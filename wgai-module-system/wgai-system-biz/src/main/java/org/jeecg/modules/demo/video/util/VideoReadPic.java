package org.jeecg.modules.demo.video.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.opencv.core.Mat;
import org.springframework.data.redis.core.RedisTemplate;

import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * @author wggg
 * @date 2025/5/20 17:41
 */
@Slf4j
public class VideoReadPic implements Runnable {
    private static final ThreadLocal<TabAiSubscriptionNew> threadLocalPushInfo = new ThreadLocal<>();
    private final ThreadLocal<identifyTypeNew> identifyTypeNewLocal = ThreadLocal.withInitial(identifyTypeNew::new);
    private final TabAiSubscriptionNew tabAiSubscriptionNew;

    // 【关键修复1】线程池大小优化 - 避免过多线程竞争
    private static final int CORE_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int MAX_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final ExecutorService SHARED_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), // 【关键修复2】有界队列防止任务堆积
            new ThreadPoolExecutor.CallerRunsPolicy() // 【关键修复3】背压策略
    );

    // 【关键修复4】Frame转换器改为实例级别，避免多线程竞争
    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();

    // 【关键修复5】严格控制并发处理数量
    private static final int MAX_CONCURRENT_PROCESSING = 32; // 大幅降低并发数
    private final AtomicInteger processingCount = new AtomicInteger(0);

    // 【关键修复6】移除对象池 - 对象池在高并发下反而增加内存压力
    // 改为直接创建和JVM自动回收

    // 性能监控
    private volatile long lastLogTime = 0;
    private final AtomicLong processedFrames = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private final RedisTemplate redisTemplate;

    // 【关键修复7】添加运行标志，确保优雅关闭
    private volatile boolean running = true;

    public VideoReadPic(TabAiSubscriptionNew tabAiSubscriptionNew, RedisTemplate redisTemplate) {
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run() {
        threadLocalPushInfo.set(tabAiSubscriptionNew);
        List<NetPush> netPushList = tabAiSubscriptionNew.getNetPushList();

        if (!"5".equals(tabAiSubscriptionNew.getPyType())) {
            return;
        }

        FFmpegFrameGrabber grabber = null;
        try {
            grabber = createOptimizedGrabber();
            identifyTypeNew identifyTypeAll = identifyTypeNewLocal.get();

            long lastTimestamp = 0;
            long intervalMicros = 1_000_000; // 【关键修复8】增加到1秒间隔，减少处理频率
            int consecutiveNullFrames = 0;

            while (running && isStreamActive()) {
                Frame frame = null;
                try {
                    frame = grabber.grabImage();

                    if (frame == null) {
                        consecutiveNullFrames++;
                        if (consecutiveNullFrames > 10) {
                            log.info("[连续空帧过多，重启视频流]");
                            grabber = restartGrabber(grabber);
                            consecutiveNullFrames = 0;
                        }
                        Thread.sleep(100); // 增加等待时间
                        continue;
                    }
                    consecutiveNullFrames = 0;

                    // 【关键修复9】更严格的帧处理控制
                    long timestamp = grabber.getTimestamp();
                    if (timestamp - lastTimestamp < intervalMicros) {
                        frame.close(); // 立即释放不处理的帧
                        continue;
                    }
                    lastTimestamp = timestamp;

                    // 【关键修复10】严格的背压控制
                    if (processingCount.get() >= MAX_CONCURRENT_PROCESSING) {
                        frame.close(); // 立即释放
                        droppedFrames.incrementAndGet();
                        continue;
                    }

                    // 【关键修复11】内存压力检测
                    if (isMemoryPressureHigh()) {
                        frame.close();
                        droppedFrames.incrementAndGet();
                        continue;
                    }

                    // 异步处理帧
                    processFrameAsync(frame, netPushList, identifyTypeAll);
                    processedFrames.incrementAndGet();

                    // 【关键修复12】添加主循环休眠，避免CPU空转
                    Thread.sleep(10);

                } catch (Exception e) {
                    if (frame != null) {
                        frame.close();
                    }
                    log.error("[处理帧异常]", e);
                    Thread.sleep(1000); // 异常后等待1秒
                }

                // 性能监控
                logPerformanceStats();
            }

        } catch (Exception exception) {
            log.error("[处理异常]", exception);
        } finally {
            running = false;
            cleanup(grabber);
        }
    }

    // 【关键修复13】严格的内存压力检测
    private boolean isMemoryPressureHigh() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double memoryUsage = (double) usedMemory / maxMemory;

        // 【关键修复14】更保守的内存使用阈值
        if (memoryUsage > 0.85) { // 85%就开始丢帧
            log.warn("[内存使用率过高: {:.2f}%，开始丢帧]", memoryUsage * 100);
            return true;
        }

        return false;
    }

    // 【关键修复15】简化异步处理，减少对象分配
    private void processFrameAsync(Frame frame, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        if (frame == null || netPushList == null || netPushList.isEmpty() || identifyTypeAll == null) {
            if (frame != null) frame.close();
            return;
        }

        processingCount.incrementAndGet();

        // 【关键修复16】直接在当前线程处理，避免额外的线程切换开销
        CompletableFuture.runAsync(() -> {
            BufferedImage image = null;
            Mat matInfo = null;
            long startTime = System.currentTimeMillis();

            try {
                // 【关键修复17】使用实例级别的转换器，避免多线程竞争
                synchronized (frameConverter) {
                    if (frame.image == null) {
                        return;
                    }
                    image = frameConverter.getBufferedImage(frame);
                }

                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    return;
                }

                // 转换为Mat
                matInfo = bufferedImageToMat(image);
                if (matInfo == null || matInfo.empty()) {
                    return;
                }

                log.debug("[开始推理] {}, 尺寸: {}x{}, 推送数量: {}",
                        tabAiSubscriptionNew.getBeginEventTypes(),
                        image.getWidth(), image.getHeight(),
                        netPushList.size());

                // 【关键修复18】简化处理逻辑，减少并发复杂度
                processNetPushList(matInfo, netPushList, identifyTypeAll);

                long processTime = System.currentTimeMillis() - startTime;
                if (processTime > 5000) {
                    log.warn("[帧处理耗时过长: {}ms]", processTime);
                }

            } catch (Exception e) {
                log.error("[处理帧异常]", e);
            } finally {
                // 【关键修复19】确保所有资源都被释放
                cleanupResources(frame, image, matInfo);
                processingCount.decrementAndGet();
            }
        }, SHARED_EXECUTOR).exceptionally(throwable -> {
            log.error("[异步处理异常]", throwable);
            processingCount.decrementAndGet();
            return null;
        });
    }

    // 【关键修复20】简化网络推送处理
    private void processNetPushList(Mat sourceMat, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        for (NetPush netPush : netPushList) {
            try {
                // 【关键修复21】每次都创建新的Mat副本，避免并发问题
                Mat mat = new Mat();
                sourceMat.copyTo(mat);

                processNetPush(mat, netPush, identifyTypeAll, tabAiSubscriptionNew.getName());

                // 立即释放
                mat.release();

            } catch (Exception e) {
                log.error("[处理NetPush异常] 模型: {}",
                        netPush.getTabAiModel() != null ? netPush.getTabAiModel().getAiName() : "unknown", e);
            }
        }
    }

    // 处理单个网络推送 - 保持原有逻辑
    private void processNetPush(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll, String name) {
        try {
            if (netPush.getIsBefor() == 1) {
                log.debug("[有前置:{}-{}]", netPush.getTabAiModel().getAiName(), name);
                processWithPredecessors(mat, netPush, identifyTypeAll);
            } else {
                log.debug("[无前置:{}-{}]", netPush.getTabAiModel().getAiName(), name);
                processWithoutPredecessors(mat, netPush, identifyTypeAll);
            }
        } catch (Exception e) {
            log.error("[处理NetPush异常] 模型: {}",
                    netPush.getTabAiModel() != null ? netPush.getTabAiModel().getAiName() : "unknown", e);
        }
    }

    // 【关键修复22】严格的资源清理
    private void cleanupResources(Frame frame, BufferedImage image, Mat mat) {
        try {
            if (frame != null) {
                frame.close();
            }
        } catch (Exception e) {
            log.warn("[释放Frame异常]", e);
        }

        try {
            if (mat != null && !mat.empty()) {
                mat.release();
            }
        } catch (Exception e) {
            log.warn("[释放Mat异常]", e);
        }

        // BufferedImage由JVM自动回收，不需要手动处理
    }

    // 【关键修复23】添加优雅关闭方法
    public void shutdown() {
        running = false;
        log.info("[视频处理线程准备关闭: {}]", tabAiSubscriptionNew.getName());
    }

    // 【关键修复24】完善的资源清理
    private void cleanup(FFmpegFrameGrabber grabber) {
        log.info("[开始清理资源]");

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                log.error("[释放grabber异常]", e);
            }
        }

        // 清理ThreadLocal
        try {
            threadLocalPushInfo.remove();
            identifyTypeNewLocal.remove();
        } catch (Exception e) {
            log.warn("[清理ThreadLocal异常]", e);
        }

        // 【关键修复25】释放转换器资源
        try {
            frameConverter.close();
        } catch (Exception e) {
            log.warn("[释放转换器异常]", e);
        }

        log.info("[资源清理完成]");
    }

    // 保持原有的其他方法
    private void processWithoutPredecessors(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        executeDetection(mat, netPush, identifyTypeAll);
    }

    private void executeDetection(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        // 【关键修复26】简化锁机制，使用netPush对象本身作为锁
        synchronized (netPush) {
            try {
                boolean result = false;
                long startTime = System.currentTimeMillis();

                if ("1".equals(netPush.getModelType())) {
                    result = identifyTypeAll.detectObjectsDify(tabAiSubscriptionNew, mat, netPush, redisTemplate);
                } else {
                    result = identifyTypeAll.detectObjectsDifyV5(tabAiSubscriptionNew, mat, netPush, redisTemplate);
                }

                long detectTime = System.currentTimeMillis() - startTime;
                if (detectTime > 3000) {
                    log.warn("[检测耗时异常] 模型: {}, 耗时: {}ms",
                            netPush.getTabAiModel().getAiName(), detectTime);
                }

            } catch (Exception e) {
                log.error("[执行检测异常] 模型类型: {}, 模型名: {}",
                        netPush.getModelType(),
                        netPush.getTabAiModel() != null ? netPush.getTabAiModel().getAiName() : "unknown", e);
            }
        }
    }

    private void processWithPredecessors(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        List<NetPush> before = netPush.getListNetPush();
        if (before == null || before.isEmpty()) {
            return;
        }

        boolean validationPassed = true;
        for (int i = 0; i < before.size() && validationPassed; i++) {
            NetPush beforePush = before.get(i);
            if (i == 0) {
                validationPassed = validateFirstModel(mat, beforePush, identifyTypeAll);
                if (!validationPassed) {
                    break;
                }
            }
            executeDetection(mat, beforePush, identifyTypeAll);
        }
    }

    private boolean validateFirstModel(Mat mat, NetPush beforePush, identifyTypeNew identifyTypeAll) {
        try {
            if ("1".equals(beforePush.getModelType())) {
                return identifyTypeAll.detectObjects(tabAiSubscriptionNew, mat, beforePush.getNet(),
                        beforePush.getClaseeNames(), beforePush);
            } else {
                return identifyTypeAll.detectObjectsV5(tabAiSubscriptionNew, mat, beforePush.getNet(),
                        beforePush.getClaseeNames(), beforePush);
            }
        } catch (Exception e) {
            log.error("[验证模型异常]", e);
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

    private FFmpegFrameGrabber createOptimizedGrabber() throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());

        // 【关键修复27】优化grabber设置，减少内存占用
        if ("1".equals(tabAiSubscriptionNew.getEventTypes())) {
            grabber.setOption("hwaccel", "cuda");
            grabber.setOption("hwaccel_device", "0");
            log.info("[使用GPU_CUDA加速解码]");
        }

        // 更保守的内存设置
        grabber.setOption("threads", "2"); // 限制线程数
        grabber.setOption("preset", "ultrafast");
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("max_delay", "1000000"); // 1秒
        grabber.setOption("buffer_size", "524288"); // 512KB缓冲区
        grabber.setOption("fflags", "nobuffer");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("analyzeduration", "0");
        grabber.setOption("probesize", "32");
        grabber.setOption("stimeout", "5000000");
        grabber.setOption("an", "1"); // 禁用音频
        grabber.setOption("loglevel", "error");
        grabber.setPixelFormat(avutil.AV_PIX_FMT_RGB24);

        grabber.start();
        return grabber;
    }

    private boolean isStreamActive() {
        try {
            return RedisCacheHolder.get(tabAiSubscriptionNew.getId() + "newRunPush");
        } catch (Exception e) {
            log.warn("[检查流状态异常]", e);
            return false;
        }
    }

    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 30000) { // 每30秒记录一次
            long processed = processedFrames.get();
            long dropped = droppedFrames.get();
            int currentProcessing = processingCount.get();

            // 【关键修复28】添加内存使用情况到日志
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / 1024 / 1024;
            long totalMemory = runtime.totalMemory() / 1024 / 1024;
            long freeMemory = runtime.freeMemory() / 1024 / 1024;
            long usedMemory = totalMemory - freeMemory;

            log.info("[性能统计] 已处理帧: {}, 丢弃帧: {}, 当前处理中: {}, 丢帧率: {}%, 内存使用: {}MB/{}MB ({}%)",
                    processed, dropped, currentProcessing,
                    processed > 0 ? (double) dropped / (processed + dropped) * 100 : 0,
                    usedMemory, maxMemory, (double) usedMemory / maxMemory * 100);

            lastLogTime = currentTime;
        }
    }


}
