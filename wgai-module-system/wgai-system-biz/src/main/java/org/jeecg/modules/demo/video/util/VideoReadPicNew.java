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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * @author wggg
 * @date 2025/5/20 17:41
 */
@Slf4j
public class VideoReadPicNew implements Runnable {

    private static final ThreadLocal<TabAiSubscriptionNew> threadLocalPushInfo = new ThreadLocal<>();
    ThreadLocal<identifyTypeNew> identifyTypeNewLocal = ThreadLocal.withInitial(identifyTypeNew::new);
    TabAiSubscriptionNew tabAiSubscriptionNew;

    // 类级别的共享资源 - 避免重复创建
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private static volatile  Java2DFrameConverter SHARED_CONVERTER ;
    // 线程安全的获取转换器
    private static Java2DFrameConverter getConverter() {
        if (SHARED_CONVERTER == null) {
            synchronized (VideoReadPicNew.class) { // 替换为你的实际类名
                if (SHARED_CONVERTER == null) {
                    SHARED_CONVERTER = new Java2DFrameConverter();
                }
            }
        }
        return SHARED_CONVERTER;
    }
    // 内存池 - 复用对象减少GC压力
    private final BlockingQueue<Mat> matPool = new LinkedBlockingQueue<>(50);
    private final BlockingQueue<BufferedImage> imagePool = new LinkedBlockingQueue<>(50);
    private final AtomicInteger processingCount = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_PROCESSING = 32; // 限制并发处理数量

    // 性能监控
    private volatile long lastLogTime = 0;
    private final AtomicLong processedFrames = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    RedisTemplate redisTemplate;

    // 获取复用的Mat对象
    private Mat getMat() {
        Mat mat = matPool.poll();
        return mat != null ? mat : new Mat();
    }

    // 归还Mat对象到池中
    private void returnMat(Mat mat) {
        if (mat != null && !mat.empty()) {
       //     mat.setTo(new Scalar(0)); // 清空内容
            if (matPool.size() < 500) { // 限制池大小
                matPool.offer(mat);
            } else {
                mat.release(); // 池满时释放
            }
        }
    }
    // 获取复用的BufferedImage对象
    private BufferedImage getBufferedImage(int width, int height, int type) {
        BufferedImage image = imagePool.poll();
        if (image != null && image.getWidth() == width &&
                image.getHeight() == height && image.getType() == type) {
            return image; // 直接复用，不清空
        }
        return new BufferedImage(width, height, type);
    }
    // 归还BufferedImage对象到池中
    private void returnBufferedImage(BufferedImage image) {
        if (image != null && imagePool.size() < 200) { // 限制池大小
            imagePool.offer(image);// 非阻塞
        }
    }
    public VideoReadPicNew(TabAiSubscriptionNew tabAiSubscriptionNew, RedisTemplate redisTemplate) {
        this.tabAiSubscriptionNew = tabAiSubscriptionNew;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run() {

        threadLocalPushInfo.set(tabAiSubscriptionNew);
        tabAiSubscriptionNew = threadLocalPushInfo.get();

        List<NetPush> netPushList = tabAiSubscriptionNew.getNetPushList();


        if (tabAiSubscriptionNew.getPyType().equals("5")) { // pytype字典 FFMPEG的值
            FFmpegFrameGrabber grabber =null;
            try {

                grabber=createOptimizedGrabber();
                identifyTypeNew identifyTypeAll =  identifyTypeNewLocal.get(); //每个视频只创建一次
                Frame frame;
                long lastTimestamp = 0;
                long intervalMicros = 1000_000; // 减少到1秒间隔，提高响应速度
                int frameSkipCounter = 0; // 跳帧计数器
                int consecutiveNullFrames = 0; // 连续空帧计数

                while (true) {

                    // 检查停止标志
                    if (!isStreamActive()) {
                        log.warn("[结束推送]{}", tabAiSubscriptionNew.getName());
                        break;
                    }
                    frame = grabber.grabImage();
                    if (frame == null) {
                        consecutiveNullFrames++;
                        if (consecutiveNullFrames > 6) { // 连续10次空帧才重启
                            log.info("[连续空帧过多，重启视频流]");
                            grabber = restartGrabber(grabber);
                            consecutiveNullFrames = 0;
                        }
                        Thread.sleep(2000); // 短暂等待
                        continue;
                    }
                    consecutiveNullFrames = 0;

                    // 智能跳帧策略
                    long timestamp = grabber.getTimestamp();
                    if (!shouldProcessFrame(timestamp, lastTimestamp, intervalMicros, frameSkipCounter)) {
                        frame.close();
                        frameSkipCounter++;
                        continue;
                    }
                    lastTimestamp = timestamp;
                    frameSkipCounter = 0;

                    // 背压控制 - 处理队列过长时跳帧
                    if (processingCount.get() >= MAX_CONCURRENT_PROCESSING) {
                        frame.close();
                        droppedFrames.incrementAndGet();
                        continue;
                    }

                    // 异步处理帧 - 避免阻塞主循环
                    processFrameAsync(frame, netPushList, identifyTypeAll);
                    processedFrames.incrementAndGet();

                    // 性能监控日志
                    logPerformanceStats();

                }

            } catch (Exception exception) {
                log.error("[处理异常]", exception);
            } finally {
                log.info("[无论如何都要结束释放]");
                cleanup( grabber);
            }
        }
    }

    /**
     * 清理资源
     */
    private void cleanup(FFmpegFrameGrabber grabber) {


        log.info("[开始清理处理器] 流: {}", tabAiSubscriptionNew.getName());


        // 2. 等待当前处理完成，但不超过5秒
        long waitStart = System.currentTimeMillis();
        while (processingCount.get() > 0 && (System.currentTimeMillis() - waitStart) < 5000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 3. 强制取消所有活跃任务
//        log.info("[取消活跃任务数量: {}]", activeTasks.size());
//        activeTasks.forEach((future, taskName) -> {
//            if (!future.isDone()) {
//                future.cancel(true);
//                log.debug("[强制取消任务: {}]", taskName);
//            }
//        });
//        activeTasks.clear();
//
//        // 4. 关闭实例线程池
//        shutdownInstanceExecutor();

        // 5. 释放视频资源
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }

        // 6. 清理对象池 - 确保完全释放
        clearObjectPools();

        // 7. 清理ThreadLocal
        try {
            identifyTypeNewLocal.remove();
            threadLocalPushInfo.remove();
        } catch (Exception e) {
            log.warn("[ThreadLocal清理异常]", e);
        }

        // 8. 强制GC（仅调试时使用）
        if (log.isDebugEnabled()) {
            System.gc();
            log.debug("[建议系统进行垃圾回收]");
        }

        log.info("[处理器清理完成] 流: {}", tabAiSubscriptionNew.getName());
    }
    /**
     * 清理对象池并确保内存释放
     */
    private void clearObjectPools() {
        // 清理Mat池
        Mat mat;
        while ((mat = matPool.poll()) != null) {
            try {
                mat.release();
            } catch (Exception e) {
                log.debug("[Mat释放异常]", e);
            }
        }

        // 清理BufferedImage池
        imagePool.clear();

        log.info("[对象池清理完成，Mat池释放数量约: {}, Image池大小: {}]",
                20, imagePool.size());
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
    // 性能统计日志
    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 30000) { // 每30秒记录一次
            long processed = processedFrames.get();
            long dropped = droppedFrames.get();
            int currentProcessing = processingCount.get();

            log.info("[性能统计] 已处理帧: {}, 丢弃帧: {}, 当前处理中: {}, 丢帧率: {}%",
                    processed, dropped, currentProcessing,
                    processed > 0 ? (double) dropped / (processed + dropped) * 100 : 0);

            lastLogTime = currentTime;
        }
    }
    // 异步处理帧
    private void processFrameAsync(Frame frame, List<NetPush> netPushList, identifyTypeNew identifyTypeAll) {
        processingCount.incrementAndGet();
        if (frame == null) {
            log.warn("[Frame为null，跳过处理]");
            return;
        }

   //     SHARED_EXECUTOR.submit(() -> {
            BufferedImage image = null;
            Mat matInfo = null;
            long startTime = System.currentTimeMillis();
            try {
                // 安全的转换为图像 - 增强空值检查
                Java2DFrameConverter converter = getConverter();
                if (converter == null) {
                    log.error("[转换器初始化失败]");
                    return;
                }

                // Frame有效性检查
                if (frame.image == null && frame.samples == null) {
                    log.warn("[Frame内容为空，跳过处理]");
                    return;
                }

                // 尝试转换，添加异常捕获
                try {
                    image = converter.getBufferedImage(frame);
                } catch (Exception e) {
                    log.error("[Frame转换异常]: {}", e.getMessage());
                    return;
                }

                if (image == null) {
                    log.warn("[Frame转换为BufferedImage结果为null]");
                    return;
                }

                // 检查图像有效性
                if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                    log.warn("[图像尺寸无效: {}x{}]", image.getWidth(), image.getHeight());
                    return;
                }

                // 将BufferedImage转换为Mat
                matInfo = bufferedImageToMat(image);
                if (matInfo == null || matInfo.empty()) {
                    log.warn("[BufferedImage转换为Mat失败]");
                    return;
                }

                if (matInfo == null || matInfo.empty()) {
                    log.warn("[Mat转换失败或为空]");
                    return;
                }

                // 创建final变量供lambda使用
                final Mat sourceMat = matInfo;
                final int netPushCount = netPushList.size();
                log.info("[开始推理]{},尺寸: {}x{},推送数量{}]", tabAiSubscriptionNew.getBeginEventTypes(), image.getWidth(), image.getHeight(),netPushCount);
                // 批量处理 - 如果只有一个推送，直接处理避免额外开销
                if (netPushCount == 0) {
                    Mat mat = getMat();
                    try {
                        sourceMat.copyTo(mat);
                        processNetPush(mat, netPushList.get(0), identifyTypeAll,tabAiSubscriptionNew.getName());
                    } finally {
                        returnMat(mat);
                    }
                } else {
                    // 多个推送并行处理
                    List<CompletableFuture<Void>> futures = new ArrayList<>(netPushCount);

                    for (NetPush netPush : netPushList) {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            Mat mat = getMat();
                            try {
                                sourceMat.copyTo(mat);
                                processNetPush(mat, netPush, identifyTypeAll,tabAiSubscriptionNew.getName());
                            } finally {
                                returnMat(mat);
                            }
                        }, SHARED_EXECUTOR);

                        futures.add(future);
                    }

                    // 等待所有任务完成，设置超时
                    try {
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .get(20, TimeUnit.SECONDS); // 10秒超时
                    } catch (TimeoutException e) {
                        log.warn("[处理超时，取消剩余任务]");
                        futures.forEach(f -> f.cancel(true));
                    }
                }

                // 性能监控
                long processTime = System.currentTimeMillis() - startTime;
                if (processTime > 1000) { // 超过1秒记录警告
                    log.warn("[帧处理耗时过长: {}ms]", processTime);
                }

            } catch (Exception e) {
                log.error("[处理帧异常]", e);
            } finally {
                // 清理资源 - 使用对象池
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
            }
   //     });
    }

    // 处理单个网络推送
    private void processNetPush(Mat mat, NetPush netPush, identifyTypeNew identifyTypeAll,String name) {
        try {
            if (netPush.getIsBefor() == 1) { // 有前置
                log.info("[有前置:{}-{}-{}]",netPush.getListNetPush().get(1).getTabAiModel().getAiName(),netPush.getListNetPush().get(0).getBeforText(),name);
                processWithPredecessors(mat, netPush, identifyTypeAll);
            } else { // 无前置
                log.info("[无前置:{}-{}]", netPush.getTabAiModel().getAiName() ,name);
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
                // 第一个模型进行验证
                validationPassed = validateFirstModel(mat, beforePush, identifyTypeAll);
                if (!validationPassed) {
                    log.warn("前置验证不通过: {},{}", beforePush.getTabAiModel().getAiName(),before.get(1).getTabAiModel().getAiName());
                    break;
                }else{
                    log.warn("验证通过了！开始检测后续模型");
                    continue;
                }
            }

            // 执行检测
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

    // 重启grabber - 复用设置代码
    private FFmpegFrameGrabber restartGrabber(FFmpegFrameGrabber grabber) throws Exception {
        grabber.stop();
        grabber.release();
        // 重新使用相同的优化设置
        return createOptimizedGrabber();
    }

    // 创建优化的grabber
    public FFmpegFrameGrabber createOptimizedGrabber() throws Exception {

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());
        // 最重要：完全静默所有FFmpeg日志输出
        grabber.setOption("loglevel", "-8");  // 完全静默，比quiet更彻底

        // GPU设置
        if (tabAiSubscriptionNew.getEventTypes().equals("1")) {


            grabber.setOption("hwaccel", "cuda");
            grabber.setOption("hwaccel_device", "0");
            grabber.setOption("hwaccel_output_format", "cuda");
            log.info("[使用GPU_CUDA加速解码]");
        }else if(tabAiSubscriptionNew.getEventTypes().equals("4")){
            //intel 加速
            grabber.setOption("hwaccel", "qsv");          // Intel QuickSync
            grabber.setVideoCodecName("hevc_qsv");         // H.265 QSV
            log.info("[使用Intel加速解码]");
        }

        // 基础连接设置
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("rtsp_flags", "prefer_tcp");
        grabber.setOption("stimeout", "3000000");

        // 解决swscaler警告的核心设置
        // 方案1：强制转换为标准yuv420p格式，避免yuvj420p的警告
        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);

        // 或者方案2：如果需要保持原格式，则完全禁用swscaler警告
        // grabber.setOption("sws_flags", "print_info+accurate_rnd+bitexact");
        // grabber.setPixelFormat(avutil.AV_PIX_FMT_YUVJ420P);

        // 颜色空间设置
        grabber.setOption("colorspace", "bt709");
        grabber.setOption("color_primaries", "bt709");
        grabber.setOption("color_trc", "bt709");
        grabber.setOption("color_range", "tv");  // 使用标准TV范围

        // 性能优化设置
        grabber.setOption("threads", "auto");
        grabber.setOption("preset", "ultrafast");
        grabber.setVideoOption("tune", "zerolatency");
        grabber.setOption("max_delay", "500000");
        grabber.setOption("buffer_size", "1048576");
     //   grabber.setOption("fflags", "nobuffer");
     //   grabber.setOption("flags", "low_delay");
        grabber.setOption("framedrop", "1");
        grabber.setOption("analyzeduration", "5000000");// 5秒分析时间
        grabber.setOption("probesize", "2097152");// 2MB探测大小
        grabber.setOption("rw_timeout", "10000000");   // 读写超时
        // 音频禁用
        grabber.setOption("an", "1");
        // 确保从关键帧开始
        // 实时流优化 - 关键设置
        grabber.setOption("fflags", "nobuffer+flush_packets+discardcorrupt");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("flags2", "fast");
        grabber.setOption("err_detect", "compliant");   // 严格错误检测
        // 禁用B帧预测（减少依赖损坏）
        grabber.setVideoOption("refs", "1");
        grabber.setVideoOption("bf", "0");

        // 关键帧解码
        grabber.setOption("skip_frame", "nokey");

        // 严格模式
        grabber.setOption("strict", "experimental");
        // 单线程解码避免帧乱序
        grabber.setOption("threads", "1");
        // 自动重连设置
        grabber.setOption("reconnect", "1");
        grabber.setOption("reconnect_at_eof", "1");
        grabber.setOption("reconnect_streamed", "1");
        grabber.setOption("reconnect_delay_max", "2");
        grabber.setOption("r", "2"); // 让解码器只输出 2fps
        grabber.setOption("vf", "fps=2");// 2fps
        grabber.start();

        return grabber;
    }


    // 检查流是否活跃
    private boolean isStreamActive() {
        try {
        //    return (boolean) redisTemplate.opsForValue().get(tabAiSubscriptionNew.getId() + "newRunPush");
            return RedisCacheHolder.get(tabAiSubscriptionNew.getId() + "newRunPush");
        } catch (Exception e) {
            log.warn("[检查流状态异常]", e);
            return false;
        }
    }

    // 检查是否应该处理当前帧
    private boolean shouldProcessFrame(long timestamp, long lastTimestamp, long intervalMicros, int frameSkipCounter) {
        // 时间间隔控制
        if (timestamp - lastTimestamp < intervalMicros) {
            return false;
        }

        // 动态跳帧 - 根据系统负载
        if (isSystemUnderPressure()) {
            return frameSkipCounter % 5 == 0; // 高负载时每3帧处理1帧
        }

        return true;
    }

    // 系统压力检测 - 更精确的判断
    private boolean isSystemUnderPressure() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double memoryUsage = (double) usedMemory / maxMemory;
        int currentProcessing = processingCount.get();

        return memoryUsage > 0.85 || currentProcessing > MAX_CONCURRENT_PROCESSING * 0.85;
    }

}
