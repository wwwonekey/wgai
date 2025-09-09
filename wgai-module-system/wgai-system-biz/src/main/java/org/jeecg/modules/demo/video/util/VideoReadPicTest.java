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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 优化后的视频读取处理类
 * 主要优化：
 * 1. 解决RTSP雪花马赛克问题 - 通过帧缓冲和质量检查
 * 2. 解决延迟问题 - 通过智能跳帧和时间戳控制
 * 3. 支持1-2秒间隔读取 - 通过定时采样策略
 */
@Slf4j
public class VideoReadPicTest implements Runnable {

    private static final ThreadLocal<TabAiSubscriptionNew> threadLocalPushInfo = new ThreadLocal<>();
    ThreadLocal<identifyTypeNew> identifyTypeNewLocal = ThreadLocal.withInitial(identifyTypeNew::new);
    TabAiSubscriptionNew tabAiSubscriptionNew;

    // 共享资源
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 4)); // 限制线程数
    private static volatile Java2DFrameConverter SHARED_CONVERTER;

    // 新增：帧质量控制
    private final AtomicBoolean isProcessingFrame = new AtomicBoolean(false);
    private final AtomicLong lastProcessTime = new AtomicLong(0);
    private final AtomicInteger consecutiveBadFrames = new AtomicInteger(0);

    // 采样控制 - 支持1-2秒间隔
    private static final long SAMPLING_INTERVAL_MS = 2000; // 2秒采样间隔
    private volatile long lastSamplingTime = 0;

    // 内存池 - 减小池大小
    private final BlockingQueue<Mat> matPool = new LinkedBlockingQueue<>(10);
    private final BlockingQueue<BufferedImage> imagePool = new LinkedBlockingQueue<>(10);
    private final AtomicInteger processingCount = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_PROCESSING = 2; // 大幅减少并发数

    // 性能监控
    private volatile long lastLogTime = 0;
    private final AtomicLong processedFrames = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private final AtomicLong sampledFrames = new AtomicLong(0); // 新增：采样帧计数

    RedisTemplate redisTemplate;

    // 线程安全的获取转换器
    private static Java2DFrameConverter getConverter() {
        if (SHARED_CONVERTER == null) {
            synchronized (VideoReadPicTest.class) {
                if (SHARED_CONVERTER == null) {
                    SHARED_CONVERTER = new Java2DFrameConverter();
                }
            }
        }
        return SHARED_CONVERTER;
    }

    // 获取复用的Mat对象
    private Mat getMat() {
        Mat mat = matPool.poll();
        return mat != null ? mat : new Mat();
    }

    // 归还Mat对象到池中
    private void returnMat(Mat mat) {
        if (mat != null && !mat.empty()) {
            if (matPool.size() < 10) {
                matPool.offer(mat);
            } else {
                mat.release();
            }
        }
    }

    // 获取复用的BufferedImage对象
    private BufferedImage getBufferedImage(int width, int height, int type) {
        BufferedImage image = imagePool.poll();
        if (image != null && image.getWidth() == width &&
                image.getHeight() == height && image.getType() == type) {
            return image;
        }
        return new BufferedImage(width, height, type);
    }

    // 归还BufferedImage对象到池中
    private void returnBufferedImage(BufferedImage image) {
        if (image != null && imagePool.size() < 10) {
            imagePool.offer(image);
        }
    }

    public VideoReadPicTest(TabAiSubscriptionNew tabAiSubscriptionNew, RedisTemplate redisTemplate) {
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.redisTemplate = redisTemplate;
    }
    long startWallClock=0;
    @Override
    public void run() {
        threadLocalPushInfo.set(tabAiSubscriptionNew);
        tabAiSubscriptionNew = threadLocalPushInfo.get();

        List<NetPush> netPushList = tabAiSubscriptionNew.getNetPushList();

        if (tabAiSubscriptionNew.getPyType().equals("5")) {
            FFmpegFrameGrabber grabber = null;
            try {
                grabber = createOptimizedGrabber();
                identifyTypeNew identifyTypeAll = identifyTypeNewLocal.get();

                Frame frame;
                int consecutiveNullFrames = 0;
                long lastValidFrameTime = System.currentTimeMillis();

                log.info("[开始视频处理] 采样间隔: {}秒, 流: {}",
                        SAMPLING_INTERVAL_MS / 1000.0, tabAiSubscriptionNew.getName());
                startWallClock= System.nanoTime();
                while (true) {
                    // 检查停止标志
                    if (!isStreamActive()) {
                        log.warn("[结束推送] {}", tabAiSubscriptionNew.getName());
                        break;
                    }

                    try {
                        frame = grabber.grabImage();

                        if (frame == null) {
                            consecutiveNullFrames++;
                            if (consecutiveNullFrames > 5) { // 降低重连阈值
                                log.warn("[连续空帧过多，重启连接] 次数: {}", consecutiveNullFrames);
                                grabber = restartGrabber(grabber);
                                consecutiveNullFrames = 0;
                                continue;
                            }
                            Thread.sleep(100); // 短暂等待
                            continue;
                        }

                        consecutiveNullFrames = 0;
                        lastValidFrameTime = System.currentTimeMillis();

                        // 关键优化1：采样控制 - 只在需要时处理帧
                        if (!shouldSampleFrame()) {
                            frame.close();
                            continue;
                        }

                        // 关键优化2：帧质量检查 - 避免处理损坏的帧
                        if (!isFrameValidAndGood(frame)) {
                            frame.close();
                            droppedFrames.incrementAndGet();
                            continue;
                        }

                        // 关键优化3：背压控制 - 如果还在处理上一帧，跳过当前帧
                        if (isProcessingFrame.get() || processingCount.get() >= MAX_CONCURRENT_PROCESSING) {
                            frame.close();
                            droppedFrames.incrementAndGet();
                            continue;
                        }

                        // 处理帧
                        processFrameWithQualityControl(frame, netPushList, identifyTypeAll);
                        sampledFrames.incrementAndGet();
                        lastSamplingTime = System.currentTimeMillis();

                        // 性能监控
                        logPerformanceStats();

                    } catch (Exception e) {
                        log.error("[处理帧异常]", e);
                        Thread.sleep(1000);
                    }
                }

            } catch (Exception exception) {
                log.error("[主处理异常]", exception);
            } finally {
                cleanup(grabber);
            }
        }
    }

    /**
     * 关键优化：采样控制 - 实现1-2秒间隔采样
     */
    private boolean shouldSampleFrame() {
        long currentTime = System.currentTimeMillis();

        // 首次采样或达到采样间隔
        if (lastSamplingTime == 0 || (currentTime - lastSamplingTime) >= SAMPLING_INTERVAL_MS) {
            return true;
        }

        return false;
    }

    /**
     * 关键优化：帧质量检查 - 解决雪花马赛克问题
     */
    private boolean isFrameValidAndGood(Frame frame) {
        if (frame == null) {
            return false;
        }

        // 基本有效性检查
        if (frame.image == null || frame.image.length == 0) {
            log.debug("[帧无效] 无图像数据");
            return false;
        }

        // 尺寸检查
        if (frame.imageWidth <= 0 || frame.imageHeight <= 0) {
            log.debug("[帧无效] 尺寸异常: {}x{}", frame.imageWidth, frame.imageHeight);
            return false;
        }

        // 深度和通道检查
        if (frame.imageDepth <= 0 || frame.imageChannels <= 0) {
            log.debug("[帧无效] 参数异常: depth={}, channels={}", frame.imageDepth, frame.imageChannels);
            return false;
        }

        // 时间戳检查 - 避免处理过旧的帧
        long elapsedMicros = (System.nanoTime() - startWallClock) / 1000; // 系统流逝时间
        long frameTs = frame.timestamp; // 帧的相对时间戳 (微秒)
        long diff = elapsedMicros - frameTs;
        log.info("时间戳检查系统启动时间{}-{}-{}",elapsedMicros,frameTs,diff);

            // 如果帧超过5秒旧，认为是延迟帧
            if (diff > 5_000_000) {
                log.debug("[帧过旧] 年龄: {}秒", diff / 1_000_000.0);
                consecutiveBadFrames.incrementAndGet();
                return false;
            }


        // 重置连续坏帧计数
        consecutiveBadFrames.set(0);
        return true;
    }

    /**
     * 带质量控制的帧处理
     */
    private void processFrameWithQualityControl(Frame frame, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            // 如果已经在处理帧，跳过
            frame.close();
            return;
        }

        processingCount.incrementAndGet();
        lastProcessTime.set(System.currentTimeMillis());

        // 提交到线程池异步处理
        SHARED_EXECUTOR.submit(() -> {
            BufferedImage image = null;
            Mat matInfo = null;
            long startTime = System.currentTimeMillis();

            try {
                // 转换为图像
                Java2DFrameConverter converter = getConverter();
                if (converter == null) {
                    log.error("[转换器初始化失败]");
                    return;
                }

                // 安全转换
                try {
                    image = converter.getBufferedImage(frame);
                } catch (Exception e) {
                    log.error("[Frame转换异常]: {}", e.getMessage());
                    return;
                }

                if (image == null) {
                    log.warn("[转换结果为null]");
                    return;
                }

                // 图像质量检查
                if (!isImageQualityGood(image)) {
                    log.debug("[图像质量不佳，跳过处理]");
                    return;
                }

                // 转换为Mat
                matInfo = bufferedImageToMat(image);
                if (matInfo == null || matInfo.empty()) {
                    log.warn("[Mat转换失败]");
                    return;
                }

                // 处理推理
                final Mat sourceMat = matInfo;
                final int netPushCount = netPushList.size();

                log.info("[开始推理] 流: {}, 尺寸: {}x{}, 推送数量: {}, 采样间隔: {}s",
                        tabAiSubscriptionNew.getName(),
                        image.getWidth(), image.getHeight(),
                        netPushCount,
                        SAMPLING_INTERVAL_MS / 1000.0);

                // 简化处理逻辑 - 减少并发复杂度
                if (netPushCount > 0) {
                    // 顺序处理，避免过多并发
                    for (NetPush netPush : netPushList) {
                        Mat mat = getMat();
                        try {
                            sourceMat.copyTo(mat);
                            processNetPush(mat, netPush, identifyTypeAll, tabAiSubscriptionNew.getName());
                        } finally {
                            returnMat(mat);
                        }
                    }
                }

                processedFrames.incrementAndGet();

                // 性能监控
                long processTime = System.currentTimeMillis() - startTime;
                log.info("[处理完成] 耗时: {}ms, 总采样帧: {}", processTime, sampledFrames.get());

            } catch (Exception e) {
                log.error("[处理帧异常]", e);
            } finally {
                // 清理资源
                if (matInfo != null) {
                    returnMat(matInfo);
                }
                if (image != null) {
                    returnBufferedImage(image);
                }
                if (frame != null) {
                    frame.close();
                }
                processingCount.decrementAndGet();
                isProcessingFrame.set(false);
            }
        });
    }

    /**
     * 图像质量检查 - 避免处理损坏的图像
     */
    private boolean isImageQualityGood(BufferedImage image) {
        if (image == null) {
            return false;
        }

        // 基本尺寸检查
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= 0 || height <= 0) {
            return false;
        }

        // 合理尺寸检查
        if (width < 32 || height < 32 || width > 4096 || height > 4096) {
            log.debug("[图像尺寸异常] {}x{}", width, height);
            return false;
        }

        // 简单的内容检查 - 检测是否为全黑或全白图像（可能是损坏帧）
        try {
            // 采样检查图像中心区域的像素
            int centerX = width / 2;
            int centerY = height / 2;
            int sampleSize = Math.min(10, Math.min(width, height) / 4);

            int[] pixels = new int[sampleSize * sampleSize];
            image.getRGB(centerX - sampleSize/2, centerY - sampleSize/2, sampleSize, sampleSize, pixels, 0, sampleSize);

            // 检查是否有足够的像素变化
            int firstPixel = pixels[0] & 0x00FFFFFF; // 移除alpha通道
            int differentPixels = 0;

            for (int pixel : pixels) {
                if ((pixel & 0x00FFFFFF) != firstPixel) {
                    differentPixels++;
                }
            }

            // 如果至少有20%的像素不同，认为图像质量可接受
            boolean hasVariation = (double) differentPixels / pixels.length > 0.2;

            if (!hasVariation) {
                log.debug("[图像缺乏变化] 可能是损坏帧");
            }

            return hasVariation;

        } catch (Exception e) {
            log.debug("[图像质量检查异常]", e);
            return true; // 异常时假定图像可用
        }
    }

    /**
     * 创建优化的grabber - 专门解决雪花马赛克和延迟问题
     */
    public FFmpegFrameGrabber createOptimizedGrabber() throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());

        // 完全静默
        grabber.setOption("loglevel", "quiet");

        // *** 关键优化1：解决雪花马赛克的核心设置 ***

        // 强制TCP传输，避免UDP丢包
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("rtsp_flags", "prefer_tcp");

        // 网络稳定性设置
        grabber.setOption("stimeout", "5000000"); // 5秒连接超时
        grabber.setOption("rw_timeout", "10000000"); // 10秒读写超时
        grabber.setOption("timeout", "10000000");

        // *** 关键优化2：缓冲区和延迟控制 ***

        // 最小缓冲区设置 - 减少延迟
        grabber.setOption("buffer_size", "1024000"); // 1MB缓冲区
        grabber.setOption("max_delay", "100000"); // 100ms最大延迟

        // 实时流优化 - 关键设置
        grabber.setOption("fflags", "nobuffer+flush_packets+discardcorrupt");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("flags2", "fast");

        // *** 关键优化3：解码器设置 ***

        // 单线程解码避免帧乱序
        grabber.setOption("threads", "1");

        // 像素格式强制设置
        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);

        // *** 关键优化4：实时性优化 ***

        // 快速分析和探测
        grabber.setOption("analyzeduration", "1000000"); // 1秒
        grabber.setOption("probesize", "1048576"); // 1MB

        // 跳帧策略 - 只解码关键帧
        grabber.setOption("skip_frame", "nokey");

        // 禁用B帧预测
        grabber.setVideoOption("refs", "1");
        grabber.setVideoOption("bf", "0");

        // *** 关键优化5：错误处理和恢复 ***

        // 错误恢复设置
        grabber.setOption("err_detect", "ignore_err");
        grabber.setOption("ignore_err", "1");

        // 自动重连设置
        grabber.setOption("reconnect", "1");
        grabber.setOption("reconnect_at_eof", "1");
        grabber.setOption("reconnect_streamed", "1");
        grabber.setOption("reconnect_delay_max", "2");

        grabber.setOption("r", "1"); // 让解码器只输出 1fps
        // *** 其他设置 ***

        // 禁用音频
        grabber.setOption("an", "1");

        // GPU加速（如果可用）
        if (tabAiSubscriptionNew.getEventTypes().equals("1")) {
            try {
                grabber.setOption("hwaccel", "cuda");
                grabber.setOption("hwaccel_device", "0");
                log.info("[启用GPU加速]");
            } catch (Exception e) {
                log.debug("[GPU加速启用失败，使用CPU]");
            }
        }

        // 启动连接
        grabber.start();
        log.info("[RTSP连接成功] 流: {}", tabAiSubscriptionNew.getBeginEventTypes());

        return grabber;
    }

    // 处理单个网络推送
    private void processNetPush(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll, String name) {
        try {
            if (netPush.getIsBefor() == 1) {
                processWithPredecessors(mat, netPush, identifyTypeAll);
            } else {
                processWithoutPredecessors(mat, netPush, identifyTypeAll);
            }
        } catch (Exception e) {
            log.error("[处理NetPush异常] 模型: {}",
                    netPush.getTabAiModel() != null ? netPush.getTabAiModel().getAiName() : "unknown", e);
        }
    }

    // 无前置条件的处理
    private void processWithoutPredecessors(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        executeDetection(mat, netPush, identifyTypeAll);
    }

    // 执行检测
    private void executeDetection(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        try {
            if ("1".equals(netPush.getModelType())) {
                identifyTypeAll.detectObjectsDify(tabAiSubscriptionNew, mat, netPush, redisTemplate);
            } else {
                identifyTypeAll.detectObjectsDifyV5(tabAiSubscriptionNew, mat, netPush, redisTemplate);
            }
        } catch (Exception e) {
            log.error("[执行检测异常] 模型类型: {}", netPush.getModelType(), e);
        }
    }

    // 有前置条件的处理
    private void processWithPredecessors(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll) {
        List<NetPush> before = netPush.getListNetPush();
        if (before == null || before.isEmpty()) {
            log.warn("[前置条件为空]");
            return;
        }

        boolean validationPassed = true;

        for (int i = 0; i < before.size() && validationPassed; i++) {
            NetPush beforePush = before.get(i);

            if (i == 0) {
                validationPassed = validateFirstModel(mat, beforePush, identifyTypeAll);
                if (!validationPassed) {
                    log.debug("前置验证不通过: {}", beforePush.getTabAiModel().getAiName());
                    break;
                } else {
                    log.debug("验证通过，开始检测后续模型");
                    continue;
                }
            }

            executeDetection(mat, beforePush, identifyTypeAll);
        }
    }

    // 验证第一个模型
    private boolean validateFirstModel(Mat mat, NetPush beforePush, identifyTypeNew identifyTypeAll) {
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
            log.error("[验证模型异常]", e);
            return false;
        }
    }

    // 重启grabber
    private FFmpegFrameGrabber restartGrabber(FFmpegFrameGrabber grabber) throws Exception {
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        // 等待1秒再重连
        Thread.sleep(1000);

        return createOptimizedGrabber();
    }

    // 检查流是否活跃
    private boolean isStreamActive() {
        try {
            return RedisCacheHolder.get(tabAiSubscriptionNew.getId() + "newRunPush");
        } catch (Exception e) {
            log.warn("[检查流状态异常]", e);
            return false;
        }
    }

    // 性能统计日志
    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 30000) { // 每30秒记录一次
            long processed = processedFrames.get();
            long dropped = droppedFrames.get();
            long sampled = sampledFrames.get();
            int currentProcessing = processingCount.get();

            log.info("[性能统计] 采样帧: {}, 处理帧: {}, 丢弃帧: {}, 当前处理中: {}, 采样率: {:.2f}帧/分钟",
                    sampled, processed, dropped, currentProcessing,
                    sampled * 60.0 / ((currentTime - (lastLogTime > 0 ? lastLogTime : currentTime - 30000)) / 1000.0));

            lastLogTime = currentTime;
        }
    }

    // 清理资源
    private void cleanup(FFmpegFrameGrabber grabber) {
        log.info("[开始清理处理器] 流: {}", tabAiSubscriptionNew.getName());

        // 等待当前处理完成
        long waitStart = System.currentTimeMillis();
        while (processingCount.get() > 0 && (System.currentTimeMillis() - waitStart) < 5000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 释放视频资源
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        // 清理对象池
        clearObjectPools();

        // 清理ThreadLocal
        try {
            identifyTypeNewLocal.remove();
            threadLocalPushInfo.remove();
        } catch (Exception e) {
            log.warn("[ThreadLocal清理异常]", e);
        }

        log.info("[处理器清理完成] 流: {}, 总采样帧: {}",
                tabAiSubscriptionNew.getName(), sampledFrames.get());
    }

    // 清理对象池
    private void clearObjectPools() {
        Mat mat;
        int releasedCount = 0;
        while ((mat = matPool.poll()) != null) {
            try {
                mat.release();
                releasedCount++;
            } catch (Exception e) {
                log.debug("[Mat释放异常]", e);
            }
        }
        imagePool.clear();
        log.info("[对象池清理完成] Mat释放数量: {}", releasedCount);
    }
}