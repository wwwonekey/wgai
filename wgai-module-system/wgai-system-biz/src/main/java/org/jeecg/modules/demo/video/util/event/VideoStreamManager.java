//package org.jeecg.modules.demo.video.util.event;
//
//import lombok.extern.slf4j.Slf4j;
//import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
//import org.jeecg.modules.demo.video.util.VideoReadPicNew;
//import org.jeecg.modules.demo.video.util.VideoReadPicNewOptimized;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Component;
//
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
///**
// * 线程安全的视频流管理器
// */
//@Slf4j
//@Component
//public class VideoStreamManager {
////    @Autowired
////    private VideoStreamManager videoStreamManager;
////
////    // 启动视频流
////    String videoId = videoStreamManager.startVideoStream(tabAiSubscriptionNew);
////log.info("视频流已启动，ID: {}", videoId);
////
////// 停止指定视频流
////videoStreamManager.stopVideoStream(videoId);
////
////    // 查看状态
////    String status = videoStreamManager.getVideoStreamStatus(videoId);
////log.info("视频流状态: {}", status);
////
////    // 查看所有流状态
////    String allStatus = videoStreamManager.getAllVideoStreamsStatus();
////log.info("所有视频流状态:\n{}", allStatus);
//
//    // 独立的视频处理线程池
//    private final ExecutorService videoExecutor;
//
//    // 活跃的视频处理器追踪
//    private final ConcurrentHashMap<String, VideoProcessorWrapper> activeProcessors = new ConcurrentHashMap<>();
//
//    private final RedisTemplate redisTemplate;
//
//    public VideoStreamManager(RedisTemplate redisTemplate) {
//        this.redisTemplate = redisTemplate;
//
//        // 创建专用的视频处理线程池
//        this.videoExecutor = new ThreadPoolExecutor(
//                0, // 核心线程数为0，按需创建
//                50, // 最大50个视频流
//                60L, TimeUnit.SECONDS, // 空闲60秒后回收
//                new SynchronousQueue<>(), // 同步队列，确保每个任务都有专用线程
//                new ThreadFactory() {
//                    private final AtomicInteger counter = new AtomicInteger(0);
//                    @Override
//                    public Thread newThread(Runnable r) {
//                        Thread t = new Thread(r, "VideoStream-" + counter.incrementAndGet());
//                        t.setDaemon(false); // 非守护线程，确保完整执行
//                        return t;
//                    }
//                },
//                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
//        );
//    }
//
//    /**
//     * 启动视频流处理
//     */
//    public String startVideoStream(TabAiSubscriptionNew subscription) {
//        try {
//            // 创建线程安全的处理器
//            VideoReadPicNewOptimized processor = new VideoReadPicNewOptimized(subscription, redisTemplate);
//            String videoId = processor.getVideoId();
//
//            log.info("[启动视频流] ID: {} 名称: {}", videoId, subscription.getName());
//
//            // 提交到专用线程池
//            Future<?> future = videoExecutor.submit(processor);
//
//            // 包装器用于管理
//            VideoProcessorWrapper wrapper = new VideoProcessorWrapper(processor, future);
//            activeProcessors.put(videoId, wrapper);
//
//            // 定期检查状态
//            scheduleHealthCheck(videoId);
//
//            log.info("[视频流启动成功] ID: {} 当前活跃流数量: {}", videoId, activeProcessors.size());
//            return videoId;
//
//        } catch (Exception e) {
//            log.error("[启动视频流失败] 名称: {}", subscription.getName(), e);
//            throw new RuntimeException("视频流启动失败", e);
//        }
//    }
//
//    /**
//     * 停止视频流处理
//     */
//    public boolean stopVideoStream(String videoId) {
//        VideoProcessorWrapper wrapper = activeProcessors.get(videoId);
//        if (wrapper == null) {
//            log.warn("[视频流不存在] ID: {}", videoId);
//            return false;
//        }
//
//        try {
//            log.info("[停止视频流] ID: {}", videoId);
//
//            // 1. 通知处理器停止
//            wrapper.processor.stop();
//
//            // 2. 等待处理器自然停止（最多10秒）
//            long stopStart = System.currentTimeMillis();
//            while (!wrapper.processor.isStopped() &&
//                    (System.currentTimeMillis() - stopStart) < 10000) {
//                Thread.sleep(100);
//            }
//
//            // 3. 如果还没停止，强制取消Future
//            if (!wrapper.processor.isStopped()) {
//                log.warn("[强制取消视频流] ID: {}", videoId);
//                wrapper.future.cancel(true);
//            }
//
//            // 4. 从活跃列表移除
//            activeProcessors.remove(videoId);
//
//            log.info("[视频流停止成功] ID: {} 剩余活跃流数量: {}", videoId, activeProcessors.size());
//            return true;
//
//        } catch (Exception e) {
//            log.error("[停止视频流异常] ID: {}", videoId, e);
//            return false;
//        }
//    }
//
//    /**
//     * 停止所有视频流
//     */
//    public void stopAllVideoStreams() {
//        log.info("[开始停止所有视频流] 总数: {}", activeProcessors.size());
//
//        // 并行停止所有视频流
//        activeProcessors.entrySet().parallelStream().forEach(entry -> {
//            try {
//                stopVideoStream(entry.getKey());
//            } catch (Exception e) {
//                log.error("[停止视频流异常] ID: {}", entry.getKey(), e);
//            }
//        });
//
//        log.info("[所有视频流停止完成]");
//    }
//
//    /**
//     * 获取视频流状态
//     */
//    public String getVideoStreamStatus(String videoId) {
//        VideoProcessorWrapper wrapper = activeProcessors.get(videoId);
//        if (wrapper == null) {
//            return "视频流不存在";
//        }
//
//        return wrapper.processor.getProcessingStats();
//    }
//
//    /**
//     * 获取所有活跃视频流状态
//     */
//    public String getAllVideoStreamsStatus() {
//        StringBuilder status = new StringBuilder();
//        status.append("活跃视频流数量: ").append(activeProcessors.size()).append("\n");
//
//        activeProcessors.forEach((videoId, wrapper) -> {
//            status.append("- ").append(wrapper.processor.getProcessingStats()).append("\n");
//        });
//
//        return status.toString();
//    }
//
//    /**
//     * 健康检查 - 定期清理已结束的流
//     */
//    private void scheduleHealthCheck(String videoId) {
//        // 使用单独的调度器进行健康检查
//        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
//            Thread t = new Thread(r, "HealthCheck-" + videoId);
//            t.setDaemon(true);
//            return t;
//        });
//
//        scheduler.scheduleWithFixedDelay(() -> {
//            try {
//                VideoProcessorWrapper wrapper = activeProcessors.get(videoId);
//                if (wrapper != null) {
//                    // 检查Future状态
//                    if (wrapper.future.isDone() || wrapper.future.isCancelled()) {
//                        log.info("[检测到视频流已结束，自动清理] ID: {}", videoId);
//                        activeProcessors.remove(videoId);
//                        scheduler.shutdown(); // 关闭当前调度器
//                    }
//                } else {
//                    scheduler.shutdown(); // 流已被移除，关闭调度器
//                }
//            } catch (Exception e) {
//                log.error("[健康检查异常] ID: {}", videoId, e);
//            }
//        }, 30, 30, TimeUnit.SECONDS); // 每30秒检查一次
//    }
//
//    /**
//     * 优雅关闭管理器
//     */
//    public void shutdown() {
//        log.info("[开始关闭视频流管理器]");
//
//        // 停止所有视频流
//        stopAllVideoStreams();
//
//        // 关闭线程池
//        videoExecutor.shutdown();
//        try {
//            if (!videoExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
//                videoExecutor.shutdownNow();
//                log.warn("[视频线程池强制关闭]");
//            }
//        } catch (InterruptedException e) {
//            videoExecutor.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//
//        log.info("[视频流管理器关闭完成]");
//    }
//
//    /**
//     * 视频处理器包装类
//     */
//    private static class VideoProcessorWrapper {
//        final VideoReadPicNewOptimized processor;
//        final Future<?> future;
//        final long createTime;
//
//        VideoProcessorWrapper(VideoReadPicNewOptimized processor, Future<?> future) {
//            this.processor = processor;
//            this.future = future;
//            this.createTime = System.currentTimeMillis();
//        }
//    }
//
//    /**
//     * 获取系统资源使用情况
//     */
//    public String getSystemResourceStatus() {
//        Runtime runtime = Runtime.getRuntime();
//        long maxMemory = runtime.maxMemory();
//        long totalMemory = runtime.totalMemory();
//        long freeMemory = runtime.freeMemory();
//        long usedMemory = totalMemory - freeMemory;
//
//        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
//
//        return String.format(
//                "系统资源状态:\n" +
//                        "- 活跃视频流: %d\n" +
//                        "- 内存使用: %.2f%% (%dMB/%dMB)\n" +
//                        "- 可用处理器: %d\n" +
//                        "- 线程池活跃线程: %d",
//                activeProcessors.size(),
//                memoryUsagePercent,
//                usedMemory / 1024 / 1024,
//                maxMemory / 1024 / 1024,
//                Runtime.getRuntime().availableProcessors(),
//                ((ThreadPoolExecutor) videoExecutor).getActiveCount()
//        );
//    }
//}