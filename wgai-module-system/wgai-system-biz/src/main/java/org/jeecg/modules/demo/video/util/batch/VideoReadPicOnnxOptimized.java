package org.jeecg.modules.demo.video.util.batch;


import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.demo.video.util.RedisCacheHolder;
import org.jeecg.modules.demo.video.util.identifyTypeNewOnnx;
import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.opencv.core.Mat;
import org.springframework.data.redis.core.RedisTemplate;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 优化版视频处理器 - 使用全局模型池
 * 关键改进:
 * 1. 使用全局模型池,多个摄像头共享模型实例
 * 2. 推理任务提交到全局线程池,统一调度
 * 3. 使用信号量控制每个模型的并发推理数
 * 4. 减少独立线程数,降低CPU压力
 *
 * @author wggg
 */
@Slf4j
public class VideoReadPicOnnxOptimized implements Runnable {

    private final TabAiSubscriptionNew tabAiSubscriptionNew;
    private final RedisTemplate redisTemplate;
    private final String streamId;

    // 使用全局模型池
    private final GlobalModelPool modelPool = GlobalModelPool.getInstance();

    // 帧转换器 - 线程本地
    private final ThreadLocal<Java2DFrameConverter> converterLocal =
            ThreadLocal.withInitial(Java2DFrameConverter::new);

    // 推理处理器 - 线程本地
    private final ThreadLocal<identifyTypeNewOnnx> identifyTypeLocal =
            ThreadLocal.withInitial(identifyTypeNewOnnx::new);

    // 轻量级执行器 - 仅用于帧抓取和预处理
    private final ScheduledExecutorService frameGrabberExecutor;

    // 停止标志
    private final AtomicBoolean forceShutdown = new AtomicBoolean(false);

    // 帧率控制
    private static final long TARGET_FRAME_INTERVAL = 1000; // 1秒1帧
    private volatile long lastFrameTime = 0;

    // 性能监控
    private final AtomicLong processedFrames = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private volatile long lastLogTime = 0;

    // Mat对象池
    private final BlockingQueue<Mat> matPool = new LinkedBlockingQueue<>(10);

    public VideoReadPicOnnxOptimized(TabAiSubscriptionNew tabAiSubscriptionNew,
                                     RedisTemplate redisTemplate) {
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.redisTemplate = redisTemplate;
        this.streamId = tabAiSubscriptionNew.getId();

        // 轻量级线程池 - 只负责帧抓取
        this.frameGrabberExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FrameGrabber-" + tabAiSubscriptionNew.getName());
            t.setDaemon(true);
            return t;
        });

        log.info("[优化版视频处理器初始化] 流: {}", tabAiSubscriptionNew.getName());
    }

    @Override
    public void run() {
        FFmpegFrameGrabber grabber = null;

        try {
            grabber = createOptimizedGrabber();
            identifyTypeNewOnnx identifyType = identifyTypeLocal.get();
            List<NetPush> netPushList = tabAiSubscriptionNew.getNetPushList();

            // 预加载模型到全局池
            preloadModelsToPool(netPushList);

            Frame frame;
            int consecutiveNullFrames = 0;

            while (!forceShutdown.get()) {
                // 检查停止标志
                if (!isStreamActive()) {
                    log.warn("[主动停止推送] {}", tabAiSubscriptionNew.getName());
                    break;
                }

                frame = grabber.grabImage();
                if (frame == null) {
                    consecutiveNullFrames++;
                    if (consecutiveNullFrames > 10) {
                        log.info("[连续空帧过多,重启视频流]");
                        grabber = restartGrabber(grabber);
                        consecutiveNullFrames = 0;
                    }
                    Thread.sleep(100);
                    continue;
                }
                consecutiveNullFrames = 0;

                // 帧率控制
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFrameTime < TARGET_FRAME_INTERVAL) {
                    frame.close();
                    continue;
                }
                lastFrameTime = currentTime;

                // 处理帧 - 提交到全局推理池
                processFrameWithGlobalPool(frame, netPushList, identifyType);

                // 性能统计
                logPerformanceStats();
            }

        } catch (Exception e) {
            log.error("[处理异常]", e);
        } finally {
            cleanup(grabber);
        }
    }

    /**
     * 预加载模型到全局池
     */
    private void preloadModelsToPool(List<NetPush> netPushList) {
        log.info("[开始预加载模型] 流: {}", tabAiSubscriptionNew.getName());

        for (NetPush netPush : netPushList) {
            try {
                String modelId = getModelId(netPush);

                // 使用全局模型池加载
                if (tabAiSubscriptionNew.getModelJmType() != null &&
                        tabAiSubscriptionNew.getModelJmType() == 20) {
                    // ONNX模型
                    modelPool.getOrLoadOnnxModel(modelId, new GlobalModelPool.ModelLoader() {
                        @Override
                        public GlobalModelPool.OnnxModelWrapper loadOnnxModel() throws Exception {
                            GlobalModelPool.OnnxModelWrapper wrapper =
                                    new GlobalModelPool.OnnxModelWrapper();
                            wrapper.setSession(netPush.getSession());
                            wrapper.setEnv(netPush.getEnv());
                            return wrapper;
                        }

                        @Override
                        public org.opencv.dnn.Net loadDnnModel() throws Exception {
                            return null;
                        }
                    });
                } else {
                    // DNN模型
                    modelPool.getOrLoadDnnModel(modelId, new GlobalModelPool.ModelLoader() {
                        @Override
                        public GlobalModelPool.OnnxModelWrapper loadOnnxModel() throws Exception {
                            return null;
                        }

                        @Override
                        public org.opencv.dnn.Net loadDnnModel() throws Exception {
                            return netPush.getNet();
                        }
                    });
                }

                log.info("[模型已加载到全局池] 模型ID: {}", modelId);

            } catch (Exception e) {
                log.error("[模型预加载失败]", e);
            }
        }
    }

    /**
     * 使用全局池处理帧
     */
    private void processFrameWithGlobalPool(Frame frame,
                                            List<NetPush> netPushList,
                                            identifyTypeNewOnnx identifyType) {
        // 克隆帧
        Frame frameClone;
        try {
            frameClone = frame.clone();
        } catch (Exception e) {
            log.error("[Frame克隆失败]", e);
            return;
        }

        // 异步预处理 + 推理
        CompletableFuture.runAsync(() -> {
            BufferedImage image = null;
            Mat matInfo = null;

            try {
                // 快速转换
                Java2DFrameConverter converter = converterLocal.get();
                image = converter.getBufferedImage(frameClone);

                if (image == null || image.getWidth() <= 0) {
                    return;
                }

                matInfo = bufferedImageToMat(image);
                if (matInfo == null || matInfo.empty()) {
                    return;
                }

                // 为每个模型创建推理任务
                final Mat finalMat = matInfo;
                List<CompletableFuture<Object>> inferenceTasks = new CopyOnWriteArrayList<>();

                for (NetPush netPush : netPushList) {
                    if (forceShutdown.get()) break;

                    // 提交到全局推理池
                    CompletableFuture<Object> inferenceTask =
                            submitInferenceTask(finalMat, netPush, identifyType);

                    inferenceTasks.add(inferenceTask);
                }

                // 等待所有推理完成(带超时)
                CompletableFuture<Void> allOf = CompletableFuture.allOf(
                        inferenceTasks.toArray(new CompletableFuture[0]));

                try {
                    allOf.get(5, TimeUnit.SECONDS); // 5秒超时
                    processedFrames.incrementAndGet();
                } catch (TimeoutException e) {
                    log.warn("[推理超时] 流: {}", tabAiSubscriptionNew.getName());
                    droppedFrames.incrementAndGet();
                }

            } catch (Exception e) {
                log.error("[帧处理异常]", e);
            } finally {
                if (matInfo != null) returnMat(matInfo);
                if (frameClone != null) frameClone.close();
            }
        }, frameGrabberExecutor);
    }

    /**
     * 提交推理任务到全局池
     */
    private CompletableFuture<Object> submitInferenceTask(Mat mat,
                                                          NetPush netPush,
                                                          identifyTypeNewOnnx identifyType) {
        String modelId = getModelId(netPush);

        return modelPool.submitInferenceTask(modelId, () -> {
            Mat matCopy = getMat();
            try {
                mat.copyTo(matCopy);

                // 执行推理
                if (netPush.getIsBefor() == 0) {
                    processWithPredecessors(matCopy, netPush, identifyType);
                } else {
                    executeDetection(matCopy, netPush, identifyType, null);
                }

                return null;
            } finally {
                returnMat(matCopy);
            }
        }).exceptionally(ex -> {
            log.error("[推理任务失败] 模型: {}", modelId, ex);
            return null;
        });
    }

    /**
     * 执行检测
     */
    private void executeDetection(Mat mat, NetPush netPush,
                                  identifyTypeNewOnnx identifyType,
                                  List<retureBoxInfo> retureBoxInfos) {
        try {
            if (forceShutdown.get()) return;

            if (netPush.getDifyType() == 2) {
                identifyType.detectObjectsDifyOnnxV5Pose(
                        tabAiSubscriptionNew, mat, netPush, redisTemplate, retureBoxInfos);
            } else {
                if (netPush.getIsBeforZoom() == 0) {
                    identifyType.detectObjectsDifyOnnxV5WithROI(
                            tabAiSubscriptionNew, mat, netPush, redisTemplate, retureBoxInfos);
                } else {
                    identifyType.detectObjectsDifyOnnxV5(
                            tabAiSubscriptionNew, mat, netPush, redisTemplate, retureBoxInfos);
                }
            }
        } catch (Exception e) {
            log.error("[执行检测异常]", e);
        }
    }

    /**
     * 处理前置模型
     */
    private void processWithPredecessors(Mat mat, NetPush netPush,
                                         identifyTypeNewOnnx identifyType) {
        List<NetPush> before = netPush.getListNetPush();
        if (before == null || before.isEmpty()) return;

        retureBoxInfo validationPassed = null;
        for (int i = 0; i < before.size(); i++) {
            if (forceShutdown.get()) break;

            NetPush beforePush = before.get(i);

            if (i == 0) {
                validationPassed = identifyType.detectObjectsV5Onnx(
                        tabAiSubscriptionNew, mat, beforePush, redisTemplate);
                if (validationPassed == null || !validationPassed.isFlag()) {
                    break;
                }
            } else {
                if (validationPassed == null ||
                        validationPassed.getInfoList().size() <= 0) {
                    break;
                }
                executeDetection(mat, beforePush, identifyType,
                        validationPassed.getInfoList());
            }
        }
    }

    /**
     * 获取模型ID
     */
    private String getModelId(NetPush netPush) {
        if (netPush.getTabAiModel() != null) {
            return netPush.getTabAiModel().getId();
        }
        return "model_" + netPush.getId();
    }

    // ==================== 辅助方法 ====================

    private Mat getMat() {
        Mat mat = matPool.poll();
        return mat != null ? mat : new Mat();
    }

    private void returnMat(Mat mat) {
        if (mat != null && !mat.empty()) {
            if (matPool.size() < 10) {
                matPool.offer(mat);
            } else {
                mat.release();
            }
        }
    }

    private boolean isStreamActive() {
        try {
            return RedisCacheHolder.get(tabAiSubscriptionNew.getId() + "newRunPush");
        } catch (Exception e) {
            return false;
        }
    }

    private FFmpegFrameGrabber restartGrabber(FFmpegFrameGrabber grabber)
            throws Exception {
        if (grabber != null) {
            grabber.stop();
            grabber.release();
        }
        return createOptimizedGrabber();
    }

    public FFmpegFrameGrabber createOptimizedGrabber() throws Exception {

        // 第一步：先探测流信息
        FFmpegFrameGrabber probe = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());
        probe.setOption("rtsp_transport", "tcp");
        probe.setOption("stimeout", "5000000");
        probe.start();
        String codecName = probe.getVideoCodecName();
        int codecId = probe.getVideoCodec();
        probe.stop();
        probe.close();
        probe.release();
        log.info(" 检测到视频编码: " + codecName + " (ID=" + codecId + ")");
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());

        // GPU设置
        if (tabAiSubscriptionNew.getEventTypes().equals("1")) {


            grabber.setOption("hwaccel", "cuda");
            grabber.setOption("hwaccel_device", "0");
            grabber.setOption("hwaccel_output_format", "cuda");
            log.info("[使用GPU_CUDA加速解码]");
        }else if(tabAiSubscriptionNew.getEventTypes().equals("4")){
            //intel 加速
            grabber.setOption("hwaccel", "qsv");          // Intel QuickSync
            //grabber.setVideoCodecName("hevc_qsv");         // H.265 QSV
            if ("h264".equalsIgnoreCase(codecName)) {
                grabber.setVideoCodecName("h264_qsv");
            } else if ("hevc".equalsIgnoreCase(codecName) || "hevc1".equalsIgnoreCase(codecName)) {
                grabber.setVideoCodecName("hevc_qsv");
            }
            log.info("[使用Intel加速解码]");
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
        grabber.setOption("buffer_size", "512000"); // 减小缓冲区
        grabber.setOption("fflags", "nobuffer+flush_packets+discardcorrupt");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("flags2", "fast");
        grabber.setOption("err_detect", "compliant");   // 严格错误检测
        grabber.setOption("framedrop", "1");

        // 严格的实时设置
        grabber.setFrameRate(2.0); // 2fps
        grabber.setOption("r", "2"); // 输入帧率限制

        grabber.start();
        return grabber;
    }


    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 30000) {
            log.info("[性能统计] 流: {}, 已处理: {}, 丢弃: {}, 全局池状态:\n{}",
                    tabAiSubscriptionNew.getName(),
                    processedFrames.get(),
                    droppedFrames.get(),
                    modelPool.getStatistics());
            lastLogTime = currentTime;
        }
    }

    private void cleanup(FFmpegFrameGrabber grabber) {
        log.info("[开始清理资源] 流: {}", tabAiSubscriptionNew.getName());

        forceShutdown.set(true);

        frameGrabberExecutor.shutdown();
        try {
            if (!frameGrabberExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                frameGrabberExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            frameGrabberExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        clearObjectPools();
        converterLocal.remove();
        identifyTypeLocal.remove();

        log.info("[资源清理完成] 流: {}", tabAiSubscriptionNew.getName());
    }

    private void clearObjectPools() {
        Mat mat;
        while ((mat = matPool.poll()) != null) {
            try {
                mat.release();
            } catch (Exception e) {
                log.debug("[Mat释放异常]", e);
            }
        }
    }

    /**
     * 外部调用的停止方法
     */
    public void forceStop() {
        log.info("[外部请求停止] 流: {}", tabAiSubscriptionNew.getName());
        forceShutdown.set(true);
    }
}