package org.jeecg.modules.demo.video.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.opencv.core.Mat;
import org.springframework.data.redis.core.RedisTemplate;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.demo.video.util.identifyTypeNew.setTestImg;
import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 高性能多摄像头视频处理器 - 解决帧重叠和推理慢问题
 * @author wggg
 * @date 2025/9/5
 */
@Slf4j
public class VideoReadPicNewOptimized implements Runnable {

    // ============ 全局共享资源 ============
    // 关键改进1：全局共享线程池 - 避免线程过多
    private static final ExecutorService GLOBAL_FRAME_PROCESSOR;
    private static final ExecutorService GLOBAL_INFERENCE_EXECUTOR;
    private static final ScheduledExecutorService CLEANUP_EXECUTOR;

    static {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        log.info("【当前cpu核心数{}】",cpuCores);
        // 帧处理线程池：CPU核心数的1.5倍，专门处理帧转换
        GLOBAL_FRAME_PROCESSOR = new ThreadPoolExecutor(
                cpuCores,
                (int)(cpuCores * 1.5),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                r -> new Thread(r, "GlobalFrameProcessor"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 推理线程池：CPU核心数，专门做推理计算
        GLOBAL_INFERENCE_EXECUTOR = new ThreadPoolExecutor(
                cpuCores,
                cpuCores,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                r -> new Thread(r, "GlobalInference"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 清理线程池
        CLEANUP_EXECUTOR = Executors.newScheduledThreadPool(2,
                r -> new Thread(r, "CleanupExecutor"));
    }

    // ============ 实例变量 ============
    private final String streamId; // 唯一流标识
    private final TabAiSubscriptionNew tabAiSubscriptionNew;
    private final RedisTemplate redisTemplate;
    private final AtomicBoolean forceShutdown = new AtomicBoolean(false);

    // 关键改进2：流隔离的对象池
    private final BlockingQueue<Mat> localMatPool = new LinkedBlockingQueue<>(5);
    private final BlockingQueue<BufferedImage> localImagePool = new LinkedBlockingQueue<>(5);

    // 关键改进3：流级别的性能控制
    private static final long TARGET_FRAME_INTERVAL = 500; // 2fps
    private volatile long lastFrameTime = 0;
    private volatile long lastProcessTime = System.currentTimeMillis();

    // 关键改进4：推理排队控制 - 每个流独立控制
    private final AtomicInteger pendingInferences = new AtomicInteger(0);
    private static final int MAX_PENDING_PER_STREAM = 2;

    // 性能统计
    private final AtomicLong processedFrames = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private volatile long lastLogTime = 0;

    // 线程本地变量 - 每个流独立
    private final ThreadLocal<identifyTypeNew> identifyTypeNewLocal =
            ThreadLocal.withInitial(identifyTypeNew::new);
    private final ThreadLocal<Java2DFrameConverter> converterLocal =
            ThreadLocal.withInitial(Java2DFrameConverter::new);

    public VideoReadPicNewOptimized(TabAiSubscriptionNew tabAiSubscriptionNew, RedisTemplate redisTemplate) {
        this.streamId = tabAiSubscriptionNew.getId();
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.redisTemplate = redisTemplate;

        log.info("[创建视频处理器] 流ID: {}, 流名: {}", streamId, tabAiSubscriptionNew.getName());
    }

    @Override
    public void run() {
        FFmpegFrameGrabber grabber = null;

        try {
            grabber = createOptimizedGrabber();
            identifyTypeNew identifyTypeAll = identifyTypeNewLocal.get();
            List<NetPush> netPushList = tabAiSubscriptionNew.getNetPushList();

            Frame frame;
            int consecutiveNullFrames = 0;

            while (!forceShutdown.get()) {
                if (!isStreamActive()) {
                    log.warn("[主动停止推送] 流ID: {}", streamId);
                    break;
                }

                frame = grabber.grabImage();
                if (frame == null) {
                    consecutiveNullFrames++;
                    if (consecutiveNullFrames > 10) {
                        log.info("[连续空帧过多，重启视频流] 流ID: {}", streamId);
                        grabber = restartGrabber(grabber);
                        consecutiveNullFrames = 0;
                    }
                    Thread.sleep(50);
                    continue;
                }
                consecutiveNullFrames = 0;

                // 关键改进5：严格的帧率控制
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFrameTime < TARGET_FRAME_INTERVAL) {
                    frame.close();
                    continue;
                }
                lastFrameTime = currentTime;

                // 关键改进6：推理队列长度控制 - 防止积压
                if (pendingInferences.get() >= MAX_PENDING_PER_STREAM) {
                    log.debug("[流{}丢弃帧以防止推理积压] 当前排队: {}",
                            streamId, pendingInferences.get());
                    frame.close();
                    droppedFrames.incrementAndGet();
                    continue;
                }

                // 异步处理帧
                processFrameAsync(frame, netPushList, identifyTypeAll);

                // 性能统计
                logPerformanceStats();
            }

        } catch (Exception exception) {
            log.error("[流{}处理异常]", streamId, exception);
        } finally {
            log.info("[开始清理流资源] 流ID: {}", streamId);
            cleanup(grabber);
        }
    }

    /**
     * 关键改进7：两阶段异步处理 - 分离帧转换和推理
     */
    private void processFrameAsync(Frame frame, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        if (forceShutdown.get()) {
            frame.close();
            return;
        }
        // 创建Frame的副本
        Frame frameClone = null;
        try {
            frameClone = frame.clone();
        } catch (Exception e) {
            log.error("[Frame克隆失败]: {}", e.getMessage());
            processedFrames.decrementAndGet();
            return;
        }
        final Frame finalFrame = frameClone;
        // 第一阶段：快速帧转换（在帧处理线程池）
        CompletableFuture<Mat> frameConversionFuture = CompletableFuture.supplyAsync(() -> {
            BufferedImage image = null;
            Mat mat = null;

            try {
                if (forceShutdown.get()) {
                    return null;
                }

                // 使用线程本地转换器
                Java2DFrameConverter converter = converterLocal.get();
                image = converter.getBufferedImage(finalFrame);

                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    return null;
                }

                mat = bufferedImageToMat(image);
                if (mat == null || mat.empty()) {
                    if (mat != null) mat.release();
                    return null;
                }

                // 创建独立的Mat副本 - 关键改进8：避免帧重叠
                Mat matCopy = getLocalMat();
                mat.copyTo(matCopy);
                mat.release(); // 立即释放原始Mat

                return matCopy;

            } catch (Exception e) {
                log.error("[流{}帧转换异常]", streamId, e);
                if (mat != null) mat.release();
                return null;
            } finally {
                if (image != null) returnLocalBufferedImage(image);
                if (frame != null) frame.close();
            }
        }, GLOBAL_FRAME_PROCESSOR);

        // 第二阶段：推理处理（在推理线程池）
        frameConversionFuture.thenAcceptAsync(mat -> {
            if (mat == null || forceShutdown.get()) {
                if (mat != null) returnLocalMat(mat);
                return;
            }

            pendingInferences.incrementAndGet();
            long inferenceStart = System.currentTimeMillis();

            try {
                // 处理推理 - 使用流隔离的推理
                processInferenceIsolated(mat, netPushList, identifyTypeAll);

                processedFrames.incrementAndGet();
                lastProcessTime = System.currentTimeMillis();

                long totalTime = System.currentTimeMillis() - inferenceStart;
                if (totalTime > 2000) {
                    log.warn("[流{}推理耗时过长: {}ms]", streamId, totalTime);
                }

            } catch (Exception e) {
                log.error("[流{}推理异常]", streamId, e);
            } finally {
                returnLocalMat(mat);
                pendingInferences.decrementAndGet();
            }
        }, GLOBAL_INFERENCE_EXECUTOR).exceptionally(ex -> {
            log.error("[流{}推理任务异常]", streamId, ex);
            pendingInferences.decrementAndGet();
            return null;
        });
    }

    /**
     * 关键改进9：流隔离的推理处理
     */
    private void processInferenceIsolated(Mat mat, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        try {
            for (NetPush netPush : netPushList) {
                if (forceShutdown.get()) {
                    break;
                }

                // 为每个推理创建独立的Mat副本 - 避免并发问题
                Mat inferenceMatCopy = getLocalMat();
                try {
                    mat.copyTo(inferenceMatCopy);
                    processNetPushIsolated(inferenceMatCopy, netPush, identifyTypeAll);
                } finally {
                    returnLocalMat(inferenceMatCopy);
                }
            }

            // 设置before图像
            setTestImg(mat, "cc_" + streamId);

        } catch (Exception e) {
            log.error("[流{}推理处理异常]", streamId, e);
        }
    }

    /**
     * 关键改进10：隔离的网络推理 - 避免模型冲突
     */
    private void processNetPushIsolated(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        try {
            if (forceShutdown.get() || mat.empty()) {
                return;
            }

            // 推理超时控制
            long inferenceStart = System.currentTimeMillis();

            if (netPush.getIsBefor() == 1) {
                processWithPredecessorsIsolated(mat, netPush, identifyTypeAll);
            } else {
                executeDetectionIsolated(mat, netPush, identifyTypeAll);
            }

            long inferenceTime = System.currentTimeMillis() - inferenceStart;
            if (inferenceTime > 1500) {
                log.warn("[流{}推理耗时: {}ms] 模型: {}",
                        streamId, inferenceTime);
            }

        } catch (Exception e) {
            log.error("[流{}处理NetPush异常] 模型: {}",
                    streamId, netPush.getTabAiModel() != null ? netPush.getTabAiModel().getAiName() : "unknown", e);
        }
    }

    private void executeDetectionIsolated(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        try {
            if (forceShutdown.get() || mat.empty()) {
                log.debug("[流{}检测跳过] 原因: forceShutdown={} 或 mat为空", streamId, forceShutdown.get());
                return;
            }

            if ("1".equals(netPush.getModelType())) {
                identifyTypeAll.detectObjectsDify(tabAiSubscriptionNew, mat, netPush, redisTemplate);
                log.debug("[流{}执行V1检测] 模型: {}", streamId, netPush.getTabAiModel().getAiName());
            } else {
                identifyTypeAll.detectObjectsDifyV5(tabAiSubscriptionNew, mat, netPush, redisTemplate);
                log.debug("[流{}执行V5检测] 模型: {}", streamId, netPush.getTabAiModel().getAiName());
            }

        } catch (Exception e) {
            log.error("[流{}执行检测异常] 模型类型: {}", streamId, netPush.getModelType(), e);
        }
    }

    private void processWithPredecessorsIsolated(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        List<NetPush> before = netPush.getListNetPush();
        if (before == null || before.isEmpty()) {
            return;
        }
        synchronized (netPush) {
            for (int i = 0; i < before.size(); i++) {
                if (forceShutdown.get()) {
                    break;
                }

                NetPush beforePush = before.get(i);

                if (i == 0) {
                    boolean validationPassed = validateFirstModelIsolated(mat, beforePush, identifyTypeAll);
                    if (!validationPassed) {
                        break;
                    }
                } else {
                    executeDetectionIsolated(mat, beforePush, identifyTypeAll);
                }
            }
        }
    }

    private boolean validateFirstModelIsolated(Mat mat, NetPush beforePush, identifyTypeNew identifyTypeAll) {
        try {
            if (forceShutdown.get() || mat.empty()) {
                return false;
            }

            if ("1".equals(beforePush.getModelType())) {
                return identifyTypeAll.detectObjects(
                        tabAiSubscriptionNew, mat, beforePush.getNet(),
                        beforePush.getClaseeNames(), beforePush);
            } else {
                return identifyTypeAll.detectObjectsV5(
                        tabAiSubscriptionNew, mat, beforePush.getNet(),
                        beforePush.getClaseeNames(), beforePush);
            }
        } catch (Exception e) {
            log.error("[流{}验证模型异常]", streamId, e);
            return false;
        }
    }

    // ============ 流隔离的对象池管理 ============

    private Mat getLocalMat() {
        Mat mat = localMatPool.poll();
        return mat != null ? mat : new Mat();
    }

    private void returnLocalMat(Mat mat) {
        if (mat != null && !mat.empty()) {
            if (localMatPool.size() < 5) {
                localMatPool.offer(mat);
            } else {
                mat.release();
            }
        }
    }

    private BufferedImage getLocalBufferedImage(int width, int height, int type) {
        BufferedImage image = localImagePool.poll();
        if (image != null && image.getWidth() == width &&
                image.getHeight() == height && image.getType() == type) {
            return image;
        }
        return new BufferedImage(width, height, type);
    }

    private void returnLocalBufferedImage(BufferedImage image) {
        if (image != null && localImagePool.size() < 5) {
            localImagePool.offer(image);
        }
    }

    // ============ 资源清理 ============

    private void cleanup(FFmpegFrameGrabber grabber) {
        log.info("[开始清理流资源] 流ID: {}", streamId);

        forceShutdown.set(true);

        // 等待当前推理完成
        int waitCount = 0;
        while (pendingInferences.get() > 0 && waitCount < 50) {
            try {
                Thread.sleep(100);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 清理视频资源
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        // 清理本地对象池
        clearLocalPools();

        // 清理ThreadLocal
        try {
            identifyTypeNewLocal.remove();
            converterLocal.remove();
        } catch (Exception e) {
            log.warn("[流{}ThreadLocal清理异常]", streamId, e);
        }

        log.info("[流资源清理完成] 流ID: {}", streamId);
    }

    private void clearLocalPools() {
        Mat mat;
        while ((mat = localMatPool.poll()) != null) {
            try {
                mat.release();
            } catch (Exception e) {
                log.debug("[流{}Mat释放异常]", streamId, e);
            }
        }
        localImagePool.clear();
    }

    // ============ 性能监控 ============

    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 30000) {
            long processed = processedFrames.get();
            long dropped = droppedFrames.get();
            int pending = pendingInferences.get();
            long processingDelay = currentTime - lastProcessTime;

            log.info("[流{}性能统计] 已处理: {}, 丢弃: {}, 排队中: {}, 处理延迟: {}ms, 丢帧率: {}%",
                    streamId, processed, dropped, pending, processingDelay,
                    processed > 0 ? (double) dropped / (processed + dropped) * 100 : 0);

            lastLogTime = currentTime;
        }
    }

    // ============ 其他辅助方法 ============

    private boolean isStreamActive() {
        try {
            return RedisCacheHolder.get(tabAiSubscriptionNew.getId() + "newRunPush");
        } catch (Exception e) {
            log.warn("[流{}检查流状态异常]", streamId, e);
            return false;
        }
    }

    private FFmpegFrameGrabber restartGrabber(FFmpegFrameGrabber grabber) throws Exception {
        if (grabber != null) {
            grabber.stop();
            grabber.release();
        }
        return createOptimizedGrabber();
    }

    public FFmpegFrameGrabber createOptimizedGrabber() throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());

        // GPU设置
        if (tabAiSubscriptionNew.getEventTypes().equals("1")) {
            grabber.setOption("hwaccel", "cuda");
            grabber.setOption("hwaccel_device", "0");
            grabber.setOption("hwaccel_output_format", "cuda");
            log.info("[流{}使用GPU_CUDA加速解码]", streamId);
        } else if(tabAiSubscriptionNew.getEventTypes().equals("4")) {
            grabber.setOption("hwaccel", "qsv");
            grabber.setVideoCodecName("hevc_qsv");
            log.info("[流{}使用Intel加速解码]", streamId);
        }

        // 基础设置
        grabber.setOption("loglevel", "-8");
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("rtsp_flags", "prefer_tcp");
        grabber.setOption("stimeout", "3000000");
        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);

        // 实时流优化
        grabber.setOption("fflags", "nobuffer+flush_packets+discardcorrupt");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("flags2", "fast");
        grabber.setOption("max_delay", "500000");
        grabber.setOption("buffer_size", "256000");
        grabber.setOption("err_detect", "compliant");
        grabber.setOption("framedrop", "1");

        // 严格的实时设置
        grabber.setFrameRate(2.0);
        grabber.setOption("r", "2");

        grabber.start();
        log.info("[流{}FFmpeg抓取器创建成功]", streamId);
        return grabber;
    }

    public void forceStop() {
        log.info("[外部请求强制停止] 流ID: {}", streamId);
        forceShutdown.set(true);
    }

    // ============ 静态清理方法 - 应用关闭时调用 ============

    public static void shutdownGlobalExecutors() {
        log.info("[开始关闭全局线程池]");

        GLOBAL_FRAME_PROCESSOR.shutdown();
        GLOBAL_INFERENCE_EXECUTOR.shutdown();
        CLEANUP_EXECUTOR.shutdown();

        try {
            if (!GLOBAL_FRAME_PROCESSOR.awaitTermination(5, TimeUnit.SECONDS)) {
                GLOBAL_FRAME_PROCESSOR.shutdownNow();
            }
            if (!GLOBAL_INFERENCE_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                GLOBAL_INFERENCE_EXECUTOR.shutdownNow();
            }
            if (!CLEANUP_EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                CLEANUP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            GLOBAL_FRAME_PROCESSOR.shutdownNow();
            GLOBAL_INFERENCE_EXECUTOR.shutdownNow();
            CLEANUP_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("[全局线程池关闭完成]");
    }
}