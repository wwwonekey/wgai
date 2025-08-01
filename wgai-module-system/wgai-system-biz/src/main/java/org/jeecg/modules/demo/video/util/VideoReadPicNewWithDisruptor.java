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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 安全的视频处理器 - 解决OpenCV DNN并发问题
 * 核心改进：
 * 1. 避免Mat对象的并发访问
 * 2. 每个线程独立的Mat副本
 * 3. 串行化DNN推理避免内存管理冲突
 * 4. 更好的资源生命周期管理
 */
@Slf4j
public class VideoReadPicNewWithDisruptor implements Runnable {

    private final TabAiSubscriptionNew tabAiSubscriptionNew;
    private final RedisTemplate redisTemplate;
    private final ThreadLocal<identifyTypeNew> identifyTypeLocal = ThreadLocal.withInitial(identifyTypeNew::new);
    private final ThreadLocal<Java2DFrameConverter> converterLocal = ThreadLocal.withInitial(Java2DFrameConverter::new);

    // 线程池配置
    private final ExecutorService frameProcessorPool;
    private final ExecutorService aiProcessorPool;

    // 性能监控
    private final AtomicLong processedFrames = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private volatile long lastStatsTime = 0;

    // 流状态
    private volatile boolean isRunning = true;
    private final Object dnnLock = new Object(); // DNN推理锁，避免并发问题

    public VideoReadPicNewWithDisruptor(TabAiSubscriptionNew tabAiSubscriptionNew, RedisTemplate redisTemplate) {
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.redisTemplate = redisTemplate;

        // 创建专用线程池
        int coreCount = Runtime.getRuntime().availableProcessors();

        // 帧处理线程池 - 用于Frame到Mat的转换
        this.frameProcessorPool = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                r -> new Thread(r, "FrameProcessor-" + tabAiSubscriptionNew.getName()),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        // AI处理线程池 - 串行处理避免DNN并发问题
        this.aiProcessorPool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, // 单线程处理AI推理
                new ArrayBlockingQueue<>(50),
                r -> new Thread(r, "AIProcessor-" + tabAiSubscriptionNew.getName()),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        log.info("[安全视频处理器启动] 流: {}, 处理器核心数: {}", tabAiSubscriptionNew.getName(), coreCount);
    }

    @Override
    public void run() {
        FFmpegFrameGrabber grabber = null;

        try {
            grabber = createOptimizedGrabber();
            List<NetPush> netPushList = tabAiSubscriptionNew.getNetPushList();

            log.info("[开始安全视频处理] 流: {}, 推送数量: {}", tabAiSubscriptionNew.getName(), netPushList.size());

            Frame frame;
            long lastTimestamp = 0;
            long intervalMicros = 500_000; // 0.5秒间隔
            int consecutiveFailures = 0;

            while (isStreamActive() && isRunning) {
                try {
                    frame = grabber.grabImage();

                    if (frame == null) {
                        consecutiveFailures++;
                        if (consecutiveFailures > 20) {
                            log.warn("[连续空帧，重启视频流] {}", tabAiSubscriptionNew.getName());
                            grabber = restartGrabber(grabber);
                            consecutiveFailures = 0;
                        }
                        Thread.sleep(50);
                        continue;
                    }

                    // 验证Frame
                    if (!isValidFrame(frame)) {
                        frame.close();
                        consecutiveFailures++;
                        continue;
                    }
                    consecutiveFailures = 0;

                    // 跳帧控制
                    long timestamp = grabber.getTimestamp();
                    if (timestamp - lastTimestamp < intervalMicros && lastTimestamp > 0) {
                        frame.close();
                        continue;
                    }
                    lastTimestamp = timestamp;

                    // 异步处理帧
                    final Frame frameToProcess = frame;
                    CompletableFuture.supplyAsync(() -> {
                                return processFrameToMat(frameToProcess);
                            }, frameProcessorPool)
                            .thenCompose(mat -> {
                                if (mat != null) {
                                    return processAIDetection(mat, netPushList);
                                } else {
                                    return CompletableFuture.completedFuture(null);
                                }
                            })
                            .handle((result, throwable) -> {
                                if (throwable != null) {
                                    log.error("[帧处理异常] 流: {}", tabAiSubscriptionNew.getName(), throwable);
                                    droppedFrames.incrementAndGet();
                                } else {
                                    processedFrames.incrementAndGet();
                                }
                                return null;
                            });

                    // 性能统计
                    logPerformanceStats();

                } catch (Exception e) {
                    log.error("[主循环异常] 流: {}", tabAiSubscriptionNew.getName(), e);
                    if (consecutiveFailures++ > 10) {
                        grabber = restartGrabber(grabber);
                        consecutiveFailures = 0;
                    }
                    Thread.sleep(1000);
                }
            }

        } catch (Exception e) {
            log.error("[视频处理主异常] 流: {}", tabAiSubscriptionNew.getName(), e);
        } finally {
            cleanup(grabber);
        }
    }

    /**
     * 安全地将Frame转换为Mat
     */
    private Mat processFrameToMat(Frame frame) {
        Java2DFrameConverter converter = converterLocal.get();

        try {
            if (!isValidFrame(frame)) {
                return null;
            }

            // Frame转BufferedImage
            BufferedImage image = converter.getBufferedImage(frame);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                log.debug("[图像转换失败] 流: {}", tabAiSubscriptionNew.getName());
                return null;
            }

            // BufferedImage转Mat - 创建独立副本
            Mat mat = bufferedImageToMat(image);
            if (mat == null || mat.empty()) {
                log.debug("[Mat转换失败] 流: {}", tabAiSubscriptionNew.getName());
                return null;
            }

            // 验证Mat数据
            if (mat.rows() <= 0 || mat.cols() <= 0) {
                mat.release();
                return null;
            }

            return mat;

        } catch (Exception e) {
            log.error("[Frame转Mat异常] 流: {}", tabAiSubscriptionNew.getName(),null);
            return null;
        } finally {
            // 立即释放Frame
            try {
                frame.close();
            } catch (Exception e) {
                log.debug("[Frame释放异常] {}", e.getMessage());
            }
        }
    }

    /**
     * 串行化AI检测处理 - 避免DNN并发问题
     */
    private CompletableFuture<Void> processAIDetection(Mat originalMat, List<NetPush> netPushList) {
        return CompletableFuture.runAsync(() -> {
            synchronized (dnnLock) { // 关键：串行化所有DNN操作
                identifyTypeNew identifyType = identifyTypeLocal.get();

                try {
                    for (NetPush netPush : netPushList) {
                        // 为每个NetPush创建独立的Mat副本
                        Mat matCopy = new Mat();
                        originalMat.copyTo(matCopy);

                        try {
                            processSingleNetPush(matCopy, netPush, identifyType);
                        } catch (Exception e) {
                            log.error("[NetPush处理异常] 模型: {}",
                                    netPush.getTabAiModel() != null ? netPush.getTabAiModel().getAiName() : "unknown", e);
                        } finally {
                            // 立即释放副本
                            safeReleaseMat(matCopy);
                        }
                    }
                } catch (Exception e) {
                    log.error("[AI检测异常] 流: {}", tabAiSubscriptionNew.getName(), e);
                } finally {
                    // 释放原始Mat
                    safeReleaseMat(originalMat);
                }
            }
        }, aiProcessorPool);
    }

    /**
     * 处理单个NetPush - 简化版本避免复杂的并行操作
     */
    private void processSingleNetPush(Mat mat, NetPush netPush, identifyTypeNew identifyType) {
        try {
            if (netPush.getIsBefor() == 1) { // 有前置
                List<NetPush> beforeList = netPush.getListNetPush();
                if (beforeList != null && !beforeList.isEmpty()) {

                    // 第一个模型验证
                    NetPush firstModel = beforeList.get(0);
                    boolean validated = validateWithModel(mat, firstModel, identifyType);

                    if (validated) {
                        // 执行所有模型检测
                        for (NetPush beforePush : beforeList) {
                            executeDetection(mat, beforePush, identifyType);
                        }
                    }
                }
            } else { // 无前置
                executeDetection(mat, netPush, identifyType);
            }
        } catch (Exception e) {
            log.error("[单个NetPush处理异常]", e);
        }
    }

    /**
     * 执行检测
     */
    private void executeDetection(Mat mat, NetPush netPush, identifyTypeNew identifyType) {
        try {
            if ("1".equals(netPush.getModelType())) {
                identifyType.detectObjectsDify(tabAiSubscriptionNew, mat, netPush, redisTemplate);
            } else {
                identifyType.detectObjectsDifyV5(tabAiSubscriptionNew, mat, netPush, redisTemplate);
            }
        } catch (Exception e) {
            log.error("[执行检测异常] 模型类型: {}", netPush.getModelType(), e);
        }
    }

    /**
     * 验证模型
     */
    private boolean validateWithModel(Mat mat, NetPush netPush, identifyTypeNew identifyType) {
        try {
            if ("1".equals(netPush.getModelType())) {
                return identifyType.detectObjects(tabAiSubscriptionNew, mat, netPush.getNet(),
                        netPush.getClaseeNames(), netPush);
            } else {
                return identifyType.detectObjectsV5(tabAiSubscriptionNew, mat, netPush.getNet(),
                        netPush.getClaseeNames(), netPush);
            }
        } catch (Exception e) {
            log.error("[模型验证异常]", e);
            return false;
        }
    }

    /**
     * 安全释放Mat
     */
    private void safeReleaseMat(Mat mat) {
        if (mat != null && !mat.empty()) {
            try {
                mat.release();
            } catch (Exception e) {
                log.debug("[Mat释放异常] {}", e.getMessage());
            }
        }
    }

    /**
     * 验证Frame有效性
     */
    private boolean isValidFrame(Frame frame) {
        // 检查图像有效性

        return frame != null &&
                frame.image != null &&
                frame.image.length > 0 &&
                frame.imageWidth > 0 &&
                frame.imageHeight > 0 &&
                frame.imageWidth <= 10000 &&
                frame.imageHeight <= 10000;
    }

    /**
     * 检查流状态
     */
    private boolean isStreamActive() {
        try {
            return RedisCacheHolder.get(tabAiSubscriptionNew.getId() + "newRunPush");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 性能统计
     */
    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatsTime > 30000) {
            long processed = processedFrames.get();
            long dropped = droppedFrames.get();

            log.info("[安全处理器统计] 流: {}, 处理: {}, 丢弃: {}, 成功率: {}%",
                    tabAiSubscriptionNew.getName(), processed, dropped,
                    processed + dropped > 0 ? (double) processed / (processed + dropped) * 100 : 0);

            lastStatsTime = currentTime;
        }
    }

    /**
     * 创建优化的grabber
     */
    private FFmpegFrameGrabber createOptimizedGrabber() throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());

        grabber.setOption("loglevel", "quiet"); // 减少日志

        if ("1".equals(tabAiSubscriptionNew.getEventTypes())) {
            grabber.setOption("hwaccel", "cuda");
            grabber.setOption("hwaccel_device", "0");
        }

        // 基础设置
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("stimeout", "5000000");
        grabber.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);

        // 低延迟设置
        grabber.setOption("fflags", "nobuffer");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("max_delay", "500000");
        grabber.setOption("analyzeduration", "1000000");
        grabber.setOption("probesize", "1000000");

        grabber.start();
        return grabber;
    }

    /**
     * 重启grabber
     */
    private FFmpegFrameGrabber restartGrabber(FFmpegFrameGrabber grabber) throws Exception {
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        Thread.sleep(2000); // 等待2秒
        return createOptimizedGrabber();
    }

    /**
     * 停止处理
     */
    public void stop() {
        isRunning = false;
    }

    /**
     * 清理资源
     */
    private void cleanup(FFmpegFrameGrabber grabber) {
        log.info("[开始清理安全处理器] 流: {}", tabAiSubscriptionNew.getName());

        isRunning = false;

        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        // 关闭线程池
        shutdownExecutorService(frameProcessorPool, "FrameProcessor");
        shutdownExecutorService(aiProcessorPool, "AIProcessor");

        // 清理ThreadLocal
        identifyTypeLocal.remove();
        converterLocal.remove();

        log.info("[安全处理器清理完成] 流: {}", tabAiSubscriptionNew.getName());
    }

    private void shutdownExecutorService(ExecutorService service, String name) {
        try {
            service.shutdown();
            if (!service.awaitTermination(10, TimeUnit.SECONDS)) {
                service.shutdownNow();
                log.warn("[{}线程池强制关闭] 流: {}", name, tabAiSubscriptionNew.getName());
            }
        } catch (Exception e) {
            log.error("[{}线程池关闭异常] 流: {}", name, tabAiSubscriptionNew.getName(), e);
        }
    }
}