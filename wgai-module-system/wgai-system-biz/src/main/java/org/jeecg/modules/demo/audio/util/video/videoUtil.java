package org.jeecg.modules.demo.audio.util.video;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.jeecg.modules.demo.audio.util.video.DetectionCallback;
import org.jeecg.modules.demo.video.util.identifyTypeNewOnnx;
import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.opencv.core.Mat;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 视频流保活 + 按需检测工具类
 * @author wggg
 * @date 2025/12/2 10:40
 */
@Slf4j
public class videoUtil {

    // ========== 视频流配置 ==========
    private final String videoUrl;
    private final RedisTemplate redisTemplate;

    // ========== 帧率控制 ==========
    private static final long TARGET_FRAME_INTERVAL = 500; // 500ms一帧(2fps)
    private volatile long lastFrameTime = 0;

    // ========== 视频流保活 ==========
    private FFmpegFrameGrabber grabber = null;
    private final AtomicBoolean streamRunning = new AtomicBoolean(false);
    private Thread streamThread;

    // ========== 检测控制 ==========
    private final AtomicBoolean detectionEnabled = new AtomicBoolean(false); // 是否启用检测
    private final AtomicBoolean isDetecting = new AtomicBoolean(false); // 是否正在检测中
    private volatile NetPush currentNetPush = null; // 当前检测参数
    private volatile DetectionCallback currentCallback = null; // 当前回调

    // ========== 异步检测线程池 ==========
    private final ExecutorService detectionExecutor;

    // ========== 重连控制 ==========
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    // ========== 检测控制 ==========
    private final AtomicInteger detectionAttempts = new AtomicInteger(0);
    private int maxDetectionAttempts = 100; // 最大检测次数（默认100次，约50秒）

    // ========== Frame转Mat转换器 ==========
    private final Java2DFrameConverter converter = new Java2DFrameConverter();

    // ========== 检测器 ==========
    private videoIdentifyTypeNewOnnx detector;

    // ========== 最新帧缓存（用于同步获取） ==========
    private final BlockingQueue<Mat> latestFrameQueue = new ArrayBlockingQueue<>(1);

    /**
     * 构造函数
     */
    public videoUtil(String videoUrl, RedisTemplate redisTemplate) {
        this.videoUrl = videoUrl;
        this.redisTemplate = redisTemplate;
        this.detectionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Detection-Thread-" + videoUrl.hashCode());
            t.setDaemon(true);
            return t;
        });
        this.detector = new videoIdentifyTypeNewOnnx();

        log.info("📹 [VideoUtil已创建] URL: {}", videoUrl);
    }

    /**
     * 启动视频流保活（只启动一次，之后一直保活）
     */
    public synchronized void startVideoStream() {
        if (streamRunning.compareAndSet(false, true)) {
            streamThread = new Thread(this::keepVideoStreamAlive, "VideoStream-" + videoUrl.hashCode());
            streamThread.setDaemon(true);
            streamThread.start();
            log.info("✅ [视频流保活已启动] URL: {}", videoUrl);
        } else {
            log.warn("⚠️ [视频流已在运行中]");
        }
    }

    /**
     * 停止视频流（彻底关闭）
     */
    public synchronized void stopVideoStream() {
        if (streamRunning.compareAndSet(true, false)) {
            log.info("🛑 [正在停止视频流] URL: {}", videoUrl);

            // 停止检测
            stopDetection();

            // 等待线程结束
            if (streamThread != null) {
                try {
                    streamThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 关闭grabber
            closeGrabber();

            log.info("✅ [视频流已停止]");
        }
    }

    /**
     * 开始检测（传入检测参数）
     * @param netPush 检测参数
     * @param callback 检测结果回调
     */
    public void startDetection(NetPush netPush, DetectionCallback callback) {
        startDetection(netPush, callback, 100); // 默认最多检测100次
    }

    /**
     * 开始检测（传入检测参数和最大检测次数）
     * @param netPush 检测参数
     * @param callback 检测结果回调
     * @param maxAttempts 最大检测次数
     */
    public void startDetection(NetPush netPush, DetectionCallback callback, int maxAttempts) {
        if (!streamRunning.get()) {
            log.error("❌ [视频流未启动，无法开始检测]");
            if (callback != null) {
                callback.onDetectionError(new IllegalStateException("视频流未启动"));
            }
            return;
        }

        if (detectionEnabled.compareAndSet(false, true)) {
            this.currentNetPush = netPush;
            this.currentCallback = callback;
            this.maxDetectionAttempts = maxAttempts;
            this.detectionAttempts.set(0);

            log.info("🔍 [开始检测] 目标类别: {}, 最大检测次数: {}",
                    netPush.getBeforText(), maxAttempts);
        } else {
            log.warn("⚠️ [检测已在进行中]");
        }
    }

    /**
     * 停止检测（但保持视频流）
     */
    public void stopDetection() {
        if (detectionEnabled.compareAndSet(true, false)) {
            log.info("🛑 [检测已停止] 总尝试次数: {}", detectionAttempts.get());
            this.currentNetPush = null;
            this.currentCallback = null;
            this.detectionAttempts.set(0);
        }
    }

    /**
     * 同步获取检测结果（阻塞式）
     * @param netPush 检测参数
     * @param timeoutSeconds 超时时间（秒）
     * @return 检测结果，超时返回null
     */
    public retureBoxInfo detectSync(NetPush netPush, int timeoutSeconds) {
        if (!streamRunning.get()) {
            log.error("❌ [视频流未启动，无法检测]");
            return null;
        }

        CompletableFuture<retureBoxInfo> future = new CompletableFuture<>();

        startDetection(netPush, new DetectionCallback() {
            @Override
            public void onDetectionSuccess(retureBoxInfo result) {
                future.complete(result);
            }

            @Override
            public void onDetectionTimeout(int attemptCount) {
                future.complete(null);
            }

            @Override
            public void onDetectionError(Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("⏰ [检测超时] {}秒", timeoutSeconds);
            stopDetection();
            return null;
        } catch (Exception e) {
            log.error("❌ [检测异常]", e);
            stopDetection();
            return null;
        }
    }

    /**
     * 视频流保活主循环
     */
    private void keepVideoStreamAlive() {
        log.info("🚀 [视频流保活线程启动]");

        while (streamRunning.get()) {
            try {
                // 初始化grabber
                if (grabber == null) {
                    log.info("📡 [正在连接视频流] URL: {}", videoUrl);
                    grabber = createOptimizedGrabber();
                    reconnectAttempts.set(0);
                    log.info("✅ [视频流连接成功]");
                }

                // 进入帧处理循环
                processFrameLoop();

            } catch (Exception e) {
                int attempts = reconnectAttempts.incrementAndGet();
                log.error("❌ [视频流异常，尝试第{}次重连] 错误: {}", attempts, e.getMessage());

                // 关闭当前grabber
                closeGrabber();

                // 检查重连次数
                if (attempts >= MAX_RECONNECT_ATTEMPTS) {
                    log.error("💀 [重连次数超过限制，停止重连]");
                    streamRunning.set(false);
                    break;
                }

                // 指数退避重连
                long sleepTime = Math.min(1000L * attempts, 30000L);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("🏁 [视频流保活线程结束]");
    }

    /**
     * 帧处理循环
     */
    private void processFrameLoop() throws Exception {
        Frame frame;
        int consecutiveNullFrames = 0;

        log.info("🎬 [开始读取视频帧]");

        while (streamRunning.get()) {
            // 读取帧
            frame = grabber.grabImage();

            // 空帧处理
            if (frame == null) {
                consecutiveNullFrames++;
                if (consecutiveNullFrames > 10) {
                    throw new RuntimeException("连续空帧过多");
                }
                Thread.sleep(100);
                continue;
            }
            consecutiveNullFrames = 0;

            // 帧率控制
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameTime < TARGET_FRAME_INTERVAL) {
                continue;
            }
            lastFrameTime = currentTime;
            BufferedImage image = null;
            Mat clonedMat = null;
            // 转换为Mat
            image = converter.getBufferedImage(frame);

            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return;
            }

            // 克隆Mat以保持数据
            clonedMat = bufferedImageToMat(image);
            if (clonedMat == null || clonedMat.empty()) {
                return;
            }

            // 如果检测已启用，提交检测任务
            if (detectionEnabled.get() && !isDetecting.get()) {
                submitDetectionTask(clonedMat);
            } else {
                // 不检测时，释放Mat
                clonedMat.release();
            }
        }
    }

    /**
     * 异步提交检测任务
     */
    private void submitDetectionTask(Mat image) {
        if (isDetecting.compareAndSet(false, true)) {
            detectionExecutor.submit(() -> executeDetection(image));
        } else {
            // 如果正在检测，释放当前帧
            image.release();
        }
    }

    /**
     * 执行检测任务
     */
    private void executeDetection(Mat image) {
        long startTime = System.currentTimeMillis();

        try {
            // 检查是否仍然启用检测
            if (!detectionEnabled.get()) {
                return;
            }

            // 增加检测次数
            int attempts = detectionAttempts.incrementAndGet();

            log.debug("🔍 [检测中] 第{}次尝试, 图像尺寸: {}x{}",
                    attempts, image.cols(), image.rows());

            // 执行检测
            retureBoxInfo result = detector.detectObjectsV5Onnx(
                    image, currentNetPush, redisTemplate
            );

            long elapsed = System.currentTimeMillis() - startTime;

            if (result != null && result.isFlag()) {
                // ✅ 检测到目标 - 停止检测并回调
                log.info("🎯 [检测成功] 第{}次尝试, 匹配数量: {}, 耗时: {}ms",
                        attempts,
                        result.getInfoList() != null ? result.getInfoList().size() : 0,
                        elapsed);

                // 停止检测
                detectionEnabled.set(false);

                // 回调通知
                if (currentCallback != null) {
                    currentCallback.onDetectionSuccess(result);
                }

            } else {
                // ❌ 未检测到目标
                log.debug("🔍 [未检测到目标] 第{}次尝试, 耗时: {}ms", attempts, elapsed);

                // 检查是否超过最大检测次数
                if (attempts >= maxDetectionAttempts) {
                    log.warn("⏰ [达到最大检测次数: {}，停止检测]", maxDetectionAttempts);

                    // 停止检测
                    detectionEnabled.set(false);

                    // 回调通知超时
                    if (currentCallback != null) {
                        currentCallback.onDetectionTimeout(attempts);
                    }
                }
            }

        } catch (Exception e) {
            log.error("❌ [检测异常]", e);

            // 停止检测
            detectionEnabled.set(false);

            // 回调通知异常
            if (currentCallback != null) {
                currentCallback.onDetectionError(e);
            }

        } finally {
            // 释放资源
            if (image != null) {
                image.release();
            }
            isDetecting.set(false);
        }
    }

    /**
     * 创建优化的Grabber
     */
    private FFmpegFrameGrabber createOptimizedGrabber() throws Exception {
        // 探测流信息
        FFmpegFrameGrabber probe = new FFmpegFrameGrabber(videoUrl);
        probe.setOption("rtsp_transport", "tcp");
        probe.setOption("stimeout", "5000000");
        probe.start();
        String codecName = probe.getVideoCodecName();
        int codecId = probe.getVideoCodec();
        probe.stop();
        probe.close();
        probe.release();
        log.info("📹 [检测到视频编码: {} (ID={})]", codecName, codecId);

        // 创建实际grabber
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl);

        // Intel硬件加速
        grabber.setOption("hwaccel", "qsv");
        if ("h264".equalsIgnoreCase(codecName)) {
            grabber.setVideoCodecName("h264_qsv");
            log.info("🚀 [使用Intel H264 QSV加速]");
        } else if ("hevc".equalsIgnoreCase(codecName) || "hevc1".equalsIgnoreCase(codecName)) {
            grabber.setVideoCodecName("hevc_qsv");
            log.info("🚀 [使用Intel HEVC QSV加速]");
        }

        // 基础设置
        grabber.setOption("loglevel", "-8");
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("rtsp_flags", "prefer_tcp");
        grabber.setOption("stimeout", "3000000");
        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);

        // 实时流优化
        grabber.setOption("flags", "low_delay");
        grabber.setOption("max_delay", "500000");
        grabber.setOption("buffer_size", "512000");
        grabber.setOption("fflags", "nobuffer+flush_packets+discardcorrupt");
        grabber.setOption("flags2", "fast");
        grabber.setOption("err_detect", "compliant");
        grabber.setOption("framedrop", "1");

        // 帧率设置
        grabber.setFrameRate(2.0);
        grabber.setOption("r", "2");

        grabber.start();
        return grabber;
    }

    /**
     * 关闭grabber
     */
    private void closeGrabber() {
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
                log.info("📴 [Grabber已关闭]");
            } catch (Exception e) {
                log.warn("⚠️ [关闭Grabber失败]", e);
            }
            grabber = null;
        }
    }

    /**
     * 优雅关闭
     */
    @PreDestroy
    public void shutdown() {
        log.info("🔄 [开始清理资源]");

        stopVideoStream();

        if (detectionExecutor != null && !detectionExecutor.isShutdown()) {
            detectionExecutor.shutdown();
            try {
                if (!detectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    detectionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                detectionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("✅ [资源清理完成]");
    }

    // ========== Getter/Setter ==========

    public void setDetector(videoIdentifyTypeNewOnnx detector) {
        this.detector = detector;
    }

    public boolean isStreamRunning() {
        return streamRunning.get();
    }

    public boolean isDetectionEnabled() {
        return detectionEnabled.get();
    }

    public boolean isDetecting() {
        return isDetecting.get();
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public int getDetectionAttempts() {
        return detectionAttempts.get();
    }
}