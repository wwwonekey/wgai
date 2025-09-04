package org.jeecg.modules.demo.video.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.jeecg.modules.demo.tab.entity.PushInfo;
import org.jeecg.modules.demo.video.entity.TabAiModelNew;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.jeecg.modules.tab.AIModel.identify.identifyTypeAll;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.springframework.data.redis.core.RedisTemplate;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 线程安全的视频处理器 - 解决多视频流Mat对象混合问题
 * @author wggg
 * @date 2025/5/20 17:41
 */
@Slf4j
public class VideoReadPicNewOptimized implements Runnable {

    // 每个视频流独立的ThreadLocal
    private static final ThreadLocal<TabAiSubscriptionNew> threadLocalPushInfo = new ThreadLocal<>();
    private final ThreadLocal<identifyTypeNew> identifyTypeNewLocal = ThreadLocal.withInitial(identifyTypeNew::new);

    // 核心数据
    private final TabAiSubscriptionNew tabAiSubscriptionNew;
    private final String videoId; // 视频流唯一标识
    private final RedisTemplate redisTemplate;

    //  关键修复：每个实例独立的资源，避免跨线程共享
    private final Java2DFrameConverter instanceConverter; // 每个视频流独立的转换器
    private final ExecutorService instanceExecutor; // 独立线程池
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);
    private final AtomicBoolean cleanupCompleted = new AtomicBoolean(false);

    // 🚫 完全移除对象池 - 避免跨线程污染
    // private final BlockingQueue<Mat> matPool = new LinkedBlockingQueue<>(20); // 删除
    // private final BlockingQueue<BufferedImage> imagePool = new LinkedBlockingQueue<>(10); // 删除

    private final AtomicInteger processingCount = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_PROCESSING = 8; // 减少并发数

    // 性能监控
    private volatile long lastLogTime = 0;
    private final AtomicLong processedFrames = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);

    // 任务追踪
    private final ConcurrentHashMap<CompletableFuture<Void>, String> activeTasks = new ConcurrentHashMap<>();

    public VideoReadPicNewOptimized(TabAiSubscriptionNew tabAiSubscriptionNew, RedisTemplate redisTemplate) {
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.videoId = tabAiSubscriptionNew.getId() + "_" + System.currentTimeMillis(); // 唯一标识
        this.redisTemplate = redisTemplate;

        //  每个视频流实例独立的转换器 - 绝对不共享
        this.instanceConverter = new Java2DFrameConverter();

        // 独立线程池，使用视频ID命名
        this.instanceExecutor = new ThreadPoolExecutor(
                1, // 核心线程数减少
                4, // 最大线程数减少  
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20), // 减少队列大小
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "VideoProcessor-" + videoId + "-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("[创建线程安全视频处理器] 视频ID: {} 流名称: {}", videoId, tabAiSubscriptionNew.getName());
    }

    @Override
    public void run() {
        threadLocalPushInfo.set(tabAiSubscriptionNew);
        TabAiSubscriptionNew localSubscription = threadLocalPushInfo.get();
        List<NetPush> netPushList = localSubscription.getNetPushList();

        if (localSubscription.getPyType().equals("5")) {
            FFmpegFrameGrabber grabber = null;
            try {
                grabber = createOptimizedGrabber();
                identifyTypeNew identifyTypeAll = identifyTypeNewLocal.get();
                Frame frame;
                long lastTimestamp = 0;
                long intervalMicros = 1000_000;
                int frameSkipCounter = 0;
                int consecutiveNullFrames = 0;

                log.info("[开始视频流处理] 视频ID: {} 流名称: {}", videoId, localSubscription.getName());

                while (!stopFlag.get()) {
                    if (!isStreamActive()) {
                        log.warn("[用户停止推送] 视频ID: {} 流名称: {}", videoId, localSubscription.getName());
                        stopFlag.set(true);
                        break;
                    }

                    frame = grabber.grabImage();
                    if (frame == null) {
                        consecutiveNullFrames++;
                        if (consecutiveNullFrames > 10) {
                            log.info("[连续空帧过多，重启视频流] 视频ID: {}", videoId);
                            grabber = restartGrabber(grabber);
                            consecutiveNullFrames = 0;
                        }
                        Thread.sleep(50); // 减少等待时间提高响应
                        continue;
                    }
                    consecutiveNullFrames = 0;

                    long timestamp = grabber.getTimestamp();
                    if (!shouldProcessFrame(timestamp, lastTimestamp, intervalMicros, frameSkipCounter)) {
                        safeCloseFrame(frame);
                        frameSkipCounter++;
                        continue;
                    }
                    lastTimestamp = timestamp;
                    frameSkipCounter = 0;

                    if (processingCount.get() >= MAX_CONCURRENT_PROCESSING) {
                        safeCloseFrame(frame);
                        droppedFrames.incrementAndGet();
                        continue;
                    }

                    //  关键修复：传递视频ID确保线程安全
                    processFrameAsync(frame, netPushList, identifyTypeAll);
                    processedFrames.incrementAndGet();

                    logPerformanceStats();
                }

            } catch (Exception exception) {
                log.error("[视频流处理异常] 视频ID: {}", videoId, exception);
            } finally {
                log.info("[开始清理视频流资源] 视频ID: {}", videoId);
                cleanup(grabber);
            }
        }
    }

    /**
     *  线程安全的异步帧处理 - 完全独立的资源管理
     */
    private void processFrameAsync(Frame frame, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        if (stopFlag.get()) {
            safeCloseFrame(frame);
            return;
        }

        processingCount.incrementAndGet();
        if (frame == null) {
            log.warn("[Frame为null] 视频ID: {}", videoId);
            processingCount.decrementAndGet();
            return;
        }

        //  关键修复：每次都创建新的BufferedImage和Mat，绝不复用
        BufferedImage image = null;
        Mat matInfo = null;
        long startTime = System.currentTimeMillis();

        try {
            if (stopFlag.get()) {
                return;
            }

            // Frame有效性检查
            if (frame.image == null && frame.samples == null) {
                log.warn("[Frame内容为空] 视频ID: {}", videoId);
                return;
            }

            //  使用实例独有的转换器，避免多线程冲突
            try {
                image = instanceConverter.getBufferedImage(frame);
            } catch (Exception e) {
                log.error("[Frame转换异常] 视频ID: {} 错误: {}", videoId, e.getMessage());
                return;
            }

            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                log.warn("[图像无效] 视频ID: {} 尺寸: {}x{}",
                        videoId,
                        image != null ? image.getWidth() : 0,
                        image != null ? image.getHeight() : 0);
                return;
            }

            //  关键修复：每次创建全新的Mat对象，确保线程隔离
            matInfo = createFreshMat(image);
            if (matInfo == null || matInfo.empty()) {
                log.warn("[Mat转换失败] 视频ID: {}", videoId);
                return;
            }

            if (stopFlag.get()) {
                return;
            }

            final int netPushCount = netPushList.size();

            log.debug("[开始推理] 视频ID: {} 流: {} 尺寸: {}x{} 推送数量: {}",
                    videoId, tabAiSubscriptionNew.getBeginEventTypes(),
                    image.getWidth(), image.getHeight(), netPushCount);

            //  关键修复：每个NetPush都使用独立的Mat副本
            if (netPushCount == 1) {
                processNetPushSafely(matInfo, netPushList.get(0), identifyTypeAll);
            } else {
                processMultipleNetPushesSafely(matInfo, netPushList, identifyTypeAll);
            }

            long processTime = System.currentTimeMillis() - startTime;
            if (processTime > 2000) {
                log.warn("[帧处理耗时过长] 视频ID: {} 耗时: {}ms", videoId, processTime);
            }

        } catch (Exception e) {
            log.error("[处理帧异常] 视频ID: {}", videoId, e);
        } finally {
            //  确保资源完全释放，不回收到池中
            cleanupFrameResourcesSafely(matInfo, image, frame);
            processingCount.decrementAndGet();
        }
    }

    /**
     *  创建全新的Mat对象 - 绝不复用
     */
    private Mat createFreshMat(BufferedImage image) {
        try {
            // 每次都创建全新的Mat，确保线程安全
            Mat mat = bufferedImageToMat(image);
            if (mat != null && !mat.empty()) {
                // 创建一个完全独立的副本
                Mat independentMat = new Mat();
                mat.copyTo(independentMat);
                mat.release(); // 立即释放原始mat
                return independentMat;
            }
            return mat;
        } catch (Exception e) {
            log.error("[创建Mat异常] 视频ID: {}", videoId, e);
            return null;
        }
    }

    /**
     *  安全的单个NetPush处理
     */
    private void processNetPushSafely(Mat sourceMat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        if (stopFlag.get()) return;

        //  每次创建全新的Mat副本
        Mat workingMat = new Mat();
        try {
            sourceMat.copyTo(workingMat);
            processNetPush(workingMat, netPush, identifyTypeAll, videoId);
        } finally {
            safeReleaseMat(workingMat);
        }
    }

    /**
     *  安全的多NetPush处理 - 每个任务独立的Mat
     */
    private void processMultipleNetPushesSafely(Mat sourceMat, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(netPushList.size());
        long taskStartTime = System.currentTimeMillis();

        for (int i = 0; i < netPushList.size(); i++) {
            if (stopFlag.get()) break;

            NetPush netPush = netPushList.get(i);
            String taskName = "NetPush-" + videoId + "-" + i + "-" + netPush.getTabAiModel().getAiName();

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                if (stopFlag.get()) return;

                //  每个任务创建完全独立的Mat副本
                Mat taskMat = new Mat();
                try {
                    sourceMat.copyTo(taskMat);
                    processNetPush(taskMat, netPush, identifyTypeAll, videoId);
                } catch (Exception e) {
                    log.error("[任务处理异常] 视频ID: {} 任务: {}", videoId, taskName, e);
                } finally {
                    safeReleaseMat(taskMat);
                }
            }, instanceExecutor).handle((result, throwable) -> {
                activeTasks.remove(CompletableFuture.completedFuture(null));
                if (throwable != null) {
                    log.error("[异步任务异常] 视频ID: {} 任务: {}", videoId, taskName, throwable);
                }
                return null;
            });

            futures.add(future);
            activeTasks.put(future, taskName);
        }

        // 优化的等待和超时处理
        try {
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allTasks.get(10, TimeUnit.SECONDS); // 减少超时时间

        } catch (TimeoutException e) {
            long elapsedTime = System.currentTimeMillis() - taskStartTime;
            log.warn("[处理超时] 视频ID: {} 耗时: {}ms 强制结束剩余任务", videoId, elapsedTime);

            futures.parallelStream().forEach(f -> {
                if (!f.isDone()) {
                    f.cancel(true);
                }
            });

        } catch (Exception e) {
            log.error("[并行处理异常] 视频ID: {}", videoId, e);
            futures.forEach(f -> f.cancel(true));
        } finally {
            futures.forEach(activeTasks::remove);
        }
    }

    /**
     *  安全的Mat释放
     */
    private void safeReleaseMat(Mat mat) {
        if (mat != null && !mat.empty()) {
            try {
                mat.release();
            } catch (Exception e) {
                log.debug("[Mat释放异常] 视频ID: {}", videoId, e);
            }
        }
    }

    /**
     *  安全的Frame关闭
     */
    private void safeCloseFrame(Frame frame) {
        if (frame != null) {
            try {
                frame.close();
            } catch (Exception e) {
                log.debug("[Frame关闭异常] 视频ID: {}", videoId, e);
            }
        }
    }

    /**
     *  安全的资源清理 - 不回收，直接释放
     */
    private void cleanupFrameResourcesSafely(Mat matInfo, BufferedImage image, Frame frame) {
        try {
            // 直接释放Mat，不回收到池中
            if (matInfo != null) {
                safeReleaseMat(matInfo);
            }

            // BufferedImage交给GC处理，不回收
            image = null;

            // 关闭Frame
            safeCloseFrame(frame);

        } catch (Exception e) {
            log.debug("[资源清理异常] 视频ID: {}", videoId, e);
        }
    }

    /**
     * 强化的清理方法
     */
    private void cleanup(FFmpegFrameGrabber grabber) {
        if (cleanupCompleted.get()) {
            return;
        }

        log.info("[开始清理处理器] 视频ID: {} 流: {}", videoId, tabAiSubscriptionNew.getName());

        stopFlag.set(true);

        // 等待处理完成
        long waitStart = System.currentTimeMillis();
        while (processingCount.get() > 0 && (System.currentTimeMillis() - waitStart) < 3000) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 取消活跃任务
        log.info("[取消活跃任务数量: {}] 视频ID: {}", activeTasks.size(), videoId);
        activeTasks.forEach((future, taskName) -> {
            if (!future.isDone()) {
                future.cancel(true);
                log.debug("[强制取消任务: {}] 视频ID: {}", taskName, videoId);
            }
        });
        activeTasks.clear();

        // 关闭线程池
        shutdownInstanceExecutor();

        // 释放视频资源
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        // 清理实例转换器
        if (instanceConverter != null) {
            try {
                // Java2DFrameConverter没有显式的close方法，交给GC
                log.debug("[清理转换器] 视频ID: {}", videoId);
            } catch (Exception e) {
                log.warn("[转换器清理异常] 视频ID: {}", videoId, e);
            }
        }

        // 清理ThreadLocal
        try {
            identifyTypeNewLocal.remove();
            threadLocalPushInfo.remove();
        } catch (Exception e) {
            log.warn("[ThreadLocal清理异常] 视频ID: {}", videoId, e);
        }

        cleanupCompleted.set(true);
        log.info("[处理器清理完成] 视频ID: {} 流: {}", videoId, tabAiSubscriptionNew.getName());
    }

    private void shutdownInstanceExecutor() {
        try {
            instanceExecutor.shutdown();

            if (!instanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[线程池强制关闭] 视频ID: {}", videoId);

                List<Runnable> pendingTasks = instanceExecutor.shutdownNow();
                log.info("[强制关闭线程池，待执行任务数: {}] 视频ID: {}", pendingTasks.size(), videoId);

                if (!instanceExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.error("[线程池强制关闭失败] 视频ID: {}", videoId);
                }
            }
        } catch (Exception e) {
            log.error("[线程池关闭异常] 视频ID: {}", videoId, e);
        }
    }

    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 30000) {
            long processed = processedFrames.get();
            long dropped = droppedFrames.get();
            int currentProcessing = processingCount.get();

            log.info("[性能统计] 视频ID: {} 已处理帧: {}, 丢弃帧: {}, 当前处理中: {}, 活跃任务: {}, 丢帧率: {}%",
                    videoId, processed, dropped, currentProcessing, activeTasks.size(),
                    processed > 0 ? (double) dropped / (processed + dropped) * 100 : 0);

            lastLogTime = currentTime;
        }
    }

    private void processNetPush(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll, String videoId) {
        if (stopFlag.get()) return;

        try {
            if (netPush.getIsBefor() == 1) {
                log.debug("[有前置] 视频ID: {} 模型: {}", videoId, netPush.getTabAiModel().getAiName());
                processWithPredecessors(mat, netPush, identifyTypeAll);
            } else {
                log.debug("[无前置] 视频ID: {} 模型: {}", videoId, netPush.getTabAiModel().getAiName());
                processWithoutPredecessors(mat, netPush, identifyTypeAll);
            }
        } catch (Exception e) {
            log.error("[处理NetPush异常] 视频ID: {} 模型: {}", videoId,
                    netPush.getTabAiModel() != null ? netPush.getTabAiModel().getAiName() : "unknown", e);
        }
    }

    private void processWithoutPredecessors(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        if (stopFlag.get()) return;
        executeDetection(mat, netPush, identifyTypeAll);
    }

    private void executeDetection(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        if (stopFlag.get()) return;

        try {
            if ("1".equals(netPush.getModelType())) {
                identifyTypeAll.detectObjectsDify(tabAiSubscriptionNew, mat, netPush, redisTemplate);
            } else {
                identifyTypeAll.detectObjectsDifyV5(tabAiSubscriptionNew, mat, netPush, redisTemplate);
            }
        } catch (Exception e) {
            log.error("[执行检测异常] 视频ID: {} 模型类型: {}", videoId, netPush.getModelType(), e);
        }
    }

    private void processWithPredecessors(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        if (stopFlag.get()) return;

        List<NetPush> before = netPush.getListNetPush();
        if (before == null || before.isEmpty()) {
            log.warn("[前置条件为空] 视频ID: {}", videoId);
            return;
        }

        boolean validationPassed = true;

        for (int i = 0; i < before.size() && validationPassed && !stopFlag.get(); i++) {
            NetPush beforePush = before.get(i);

            if (i == 0) {
                validationPassed = validateFirstModel(mat, beforePush, identifyTypeAll);
                if (!validationPassed) {
                    log.debug("[前置验证不通过] 视频ID: {} 模型: {}", videoId, beforePush.getTabAiModel().getAiName());
                    break;
                }
            }

            executeDetection(mat, beforePush, identifyTypeAll);
        }
    }

    private boolean validateFirstModel(Mat mat, NetPush beforePush, identifyTypeNew identifyTypeAll) {
        if (stopFlag.get()) return false;

        try {
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
            log.error("[验证模型异常] 视频ID: {}", videoId, e);
            return false;
        }
    }

    private FFmpegFrameGrabber restartGrabber(FFmpegFrameGrabber grabber) throws Exception {
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }
        return createOptimizedGrabber();
    }

    private FFmpegFrameGrabber createOptimizedGrabber() throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());

        grabber.setOption("loglevel", "-8");

        if (tabAiSubscriptionNew.getEventTypes().equals("1")) {
            grabber.setOption("hwaccel", "cuda");
            grabber.setOption("hwaccel_device", "0");
            grabber.setOption("hwaccel_output_format", "cuda");
            log.info("[使用GPU_CUDA加速解码] 视频ID: {}", videoId);
        }

        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("stimeout", "3000000");
        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);

        grabber.setOption("colorspace", "bt709");
        grabber.setOption("color_primaries", "bt709");
        grabber.setOption("color_trc", "bt709");
        grabber.setOption("color_range", "tv");

        grabber.setOption("threads", "auto");
        grabber.setOption("preset", "ultrafast");
        grabber.setVideoOption("tune", "zerolatency");
        grabber.setOption("max_delay", "500000");
        grabber.setOption("buffer_size", "1048576");
        grabber.setOption("fflags", "nobuffer");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("framedrop", "1");
        grabber.setOption("analyzeduration", "0");
        grabber.setOption("probesize", "32");
        grabber.setOption("an", "1");
        grabber.setOption("skip_frame", "nokey");
        grabber.setOption("strict", "experimental");

        grabber.start();
        return grabber;
    }

    private boolean isStreamActive() {
        try {
            return RedisCacheHolder.get(tabAiSubscriptionNew.getId() + "newRunPush");
        } catch (Exception e) {
            log.warn("[检查流状态异常] 视频ID: {}", videoId, e);
            return false;
        }
    }

    private boolean shouldProcessFrame(long timestamp, long lastTimestamp, long intervalMicros, int frameSkipCounter) {
        if (stopFlag.get()) return false;

        if (timestamp - lastTimestamp < intervalMicros) {
            return false;
        }

        if (isSystemUnderPressure()) {
            return frameSkipCounter % 2 == 0; // 高负载时每2帧处理1帧
        }

        return true;
    }

    private boolean isSystemUnderPressure() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double memoryUsage = (double) usedMemory / maxMemory;
        int currentProcessing = processingCount.get();

        return memoryUsage > 0.80 || currentProcessing > MAX_CONCURRENT_PROCESSING * 0.8;
    }

    // 外部接口
    public void stop() {
        log.info("[外部请求停止视频处理] 视频ID: {} 流: {}", videoId, tabAiSubscriptionNew.getName());
        stopFlag.set(true);
    }

    public boolean isStopped() {
        return stopFlag.get();
    }

    public String getVideoId() {
        return videoId;
    }

    public String getProcessingStats() {
        return String.format("视频ID: %s, 已处理: %d, 丢弃: %d, 当前处理: %d, 活跃任务: %d",
                videoId, processedFrames.get(), droppedFrames.get(),
                processingCount.get(), activeTasks.size());
    }
}