package org.jeecg.modules.demo.video.util.batch.batch;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.demo.video.util.identifyTypeNewOnnx;
import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批量推理调度器 - 完整版(支持前置后置模型)
 * 核心思路:
 * 1. 前置模型批量推理 -> 获取ROI列表
 * 2. 后置模型基于ROI批量推理
 * 3. 按模型ID分组,减少模型切换
 */
@Slf4j
@Component
public class BatchInferenceScheduler {

    @Autowired
    private RedisTemplate redisTemplate;

    // ============ 配置参数 ============
    private static final int MAX_BATCH_SIZE = 6;           // 每批最多6帧
    private static final long BATCH_WAIT_MS = 30;          // 等待30ms凑批
    private static final int WORKER_THREADS = 4;           // 工作线程数

    // ============ 数据结构 ============

    /**
     * 完整推理任务(包含前置后置)
     */
    public static class FullInferenceTask {
        String streamId;                              // 摄像头ID
        Mat frame;                                    // 原始帧
        List<NetPush> netPushList;                    // 模型列表(前置+后置)
        TabAiSubscriptionNew pushInfo;                // 推送配置
        CompletableFuture<Boolean> future;            // 异步结果
        long submitTime;

        public FullInferenceTask(String streamId, Mat frame,
                                 List<NetPush> netPushList,
                                 TabAiSubscriptionNew pushInfo) {
            this.streamId = streamId;
            this.frame = frame;
            this.netPushList = netPushList;
            this.pushInfo = pushInfo;
            this.future = new CompletableFuture<>();
            this.submitTime = System.currentTimeMillis();
        }
    }

    /**
     * 前置模型任务
     */
    private static class PreModelTask {
        String streamId;
        Mat frame;
        NetPush netPush;
        TabAiSubscriptionNew pushInfo;
        CompletableFuture<retureBoxInfo> future;

        PreModelTask(String streamId, Mat frame, NetPush netPush,
                     TabAiSubscriptionNew pushInfo) {
            this.streamId = streamId;
            this.frame = frame;
            this.netPush = netPush;
            this.pushInfo = pushInfo;
            this.future = new CompletableFuture<>();
        }
    }

    /**
     * 后置模型任务
     */
    private static class PostModelTask {
        String streamId;
        Mat frame;
        NetPush netPush;
        TabAiSubscriptionNew pushInfo;
        List<retureBoxInfo> preResults;  // 前置模型结果
        CompletableFuture<Boolean> future;

        PostModelTask(String streamId, Mat frame, NetPush netPush,
                      TabAiSubscriptionNew pushInfo, List<retureBoxInfo> preResults) {
            this.streamId = streamId;
            this.frame = frame;
            this.netPush = netPush;
            this.pushInfo = pushInfo;
            this.preResults = preResults;
            this.future = new CompletableFuture<>();
        }
    }

    // ============ 队列 ============

    // 主任务队列
    private final BlockingQueue<FullInferenceTask> mainQueue =
            new LinkedBlockingQueue<>(100);

    // 前置模型队列(按模型ID分组)
    private final ConcurrentHashMap<String, BlockingQueue<PreModelTask>> preModelQueues =
            new ConcurrentHashMap<>();

    // 后置模型队列(按模型ID分组)
    private final ConcurrentHashMap<String, BlockingQueue<PostModelTask>> postModelQueues =
            new ConcurrentHashMap<>();

    // ============ 线程池 ============

    private final ExecutorService mainExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MainScheduler")
    );

    private final ExecutorService preExecutor = Executors.newFixedThreadPool(
            WORKER_THREADS / 2,
            r -> new Thread(r, "PreModel-Worker")
    );

    private final ExecutorService postExecutor = Executors.newFixedThreadPool(
            WORKER_THREADS / 2,
            r -> new Thread(r, "PostModel-Worker")
    );

    private volatile boolean running = true;

    // ============ 推理器池 ============

    private final ThreadLocal<identifyTypeNewOnnx> identifyTypeLocal =
            ThreadLocal.withInitial(identifyTypeNewOnnx::new);

    // ============ 统计 ============

    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);

    // ============ 初始化 ============

    @PostConstruct
    public void init() {
        log.info("[批量推理调度器启动] 批次大小:{}, 工作线程:{}",
                MAX_BATCH_SIZE, WORKER_THREADS);

        // 启动主调度线程
        mainExecutor.submit(new MainScheduler());

        // 启动前置模型工作线程
        for (int i = 0; i < WORKER_THREADS / 2; i++) {
            preExecutor.submit(new PreModelWorker());
        }

        // 启动后置模型工作线程
        for (int i = 0; i < WORKER_THREADS / 2; i++) {
            postExecutor.submit(new PostModelWorker());
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        mainExecutor.shutdown();
        preExecutor.shutdown();
        postExecutor.shutdown();
    }

    // ============ 公共接口 ============

    /**
     * 提交完整推理任务
     */
    public CompletableFuture<Boolean> submitFullInference(
            String streamId,
            Mat frame,
            List<NetPush> netPushList,
            TabAiSubscriptionNew pushInfo) {

        FullInferenceTask task = new FullInferenceTask(
                streamId, frame, netPushList, pushInfo
        );

        if (!mainQueue.offer(task)) {
            log.warn("[主队列已满,丢弃任务] 摄像头:{}", streamId);
            task.future.complete(false);
            if (frame != null) frame.release();
        }

        return task.future;
    }

    // ============ 主调度器 ============

    /**
     * 主调度器:负责任务分发
     */
    private class MainScheduler implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    FullInferenceTask task = mainQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task == null) continue;

                    processFullTask(task);

                } catch (Exception e) {
                    log.error("[主调度器异常]", e);
                }
            }
        }

        /**
         * 处理完整任务(前置 -> 后置)
         */
        private void processFullTask(FullInferenceTask task) {
            try {
                NetPush firstPush = task.netPushList.get(0);

                // 检查是否有前置模型
                if (firstPush.getIsBefor() == 0 && firstPush.getListNetPush() != null) {
                    // 有前置后置
                    processWithPrePost(task);
                } else {
                    // 无前置,直接推理
                    processDirectly(task);
                }

            } catch (Exception e) {
                log.error("[任务处理失败]", e);
                task.future.complete(false);
                if (task.frame != null) task.frame.release();
            }
        }

        /**
         * 有前置后置的处理流程
         */
        private void processWithPrePost(FullInferenceTask task) {
            List<NetPush> beforeList = task.netPushList.get(0).getListNetPush();

            if (beforeList == null || beforeList.isEmpty()) {
                task.future.complete(false);
                task.frame.release();
                return;
            }

            // 第一个是前置模型
            NetPush preModel = beforeList.get(0);

            // 提交前置模型任务
            CompletableFuture<retureBoxInfo> preFuture = submitPreModel(
                    task.streamId, task.frame, preModel, task.pushInfo
            );

            // 前置完成后,提交后置模型
            preFuture.whenComplete((preResult, ex) -> {
                if (ex != null || preResult == null || !preResult.isFlag()) {
                    log.warn("[前置模型失败,跳过后置] 摄像头:{}", task.streamId);
                    task.future.complete(false);
                    task.frame.release();
                    return;
                }

                // 提交后置模型(可能有多个)
                List<CompletableFuture<Boolean>> postFutures = new ArrayList<>();

                for (int i = 1; i < beforeList.size(); i++) {
                    NetPush postModel = beforeList.get(i);

                    CompletableFuture<Boolean> postFuture = submitPostModel(
                            task.streamId,
                            task.frame,
                            postModel,
                            task.pushInfo,
                            preResult.getInfoList()
                    );

                    postFutures.add(postFuture);
                }

                // 等待所有后置模型完成
                CompletableFuture.allOf(postFutures.toArray(new CompletableFuture[0]))
                        .whenComplete((v, e) -> {
                            boolean anySuccess = postFutures.stream()
                                    .anyMatch(f -> f.join());
                            task.future.complete(anySuccess);
                            task.frame.release();
                        });
            });
        }

        /**
         * 无前置,直接推理
         */
        private void processDirectly(FullInferenceTask task) {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            for (NetPush netPush : task.netPushList) {
                CompletableFuture<Boolean> future = submitPostModel(
                        task.streamId,
                        task.frame,
                        netPush,
                        task.pushInfo,
                        null  // 无前置结果
                );
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, e) -> {
                        boolean anySuccess = futures.stream().anyMatch(f -> f.join());
                        task.future.complete(anySuccess);
                        task.frame.release();
                    });
        }
    }

    // ============ 前置模型处理 ============

    /**
     * 提交前置模型任务
     */
    private CompletableFuture<retureBoxInfo> submitPreModel(
            String streamId, Mat frame, NetPush netPush,
            TabAiSubscriptionNew pushInfo) {

        String modelKey = getModelKey(netPush);

        BlockingQueue<PreModelTask> queue = preModelQueues.computeIfAbsent(
                modelKey,
                k -> new LinkedBlockingQueue<>(50)
        );

        Mat clonedFrame = new Mat();
        frame.copyTo(clonedFrame);

        PreModelTask task = new PreModelTask(streamId, clonedFrame, netPush, pushInfo);

        if (!queue.offer(task)) {
            log.warn("[前置队列已满] 模型:{}", modelKey);
            task.future.complete(null);
            clonedFrame.release();
        }

        return task.future;
    }

    /**
     * 前置模型工作线程
     */
    private class PreModelWorker implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    processPreModelBatches();
                    Thread.sleep(5);
                } catch (Exception e) {
                    log.error("[前置模型工作线程异常]", e);
                }
            }
        }

        private void processPreModelBatches() throws InterruptedException {
            for (Map.Entry<String, BlockingQueue<PreModelTask>> entry : preModelQueues.entrySet()) {
                String modelKey = entry.getKey();
                BlockingQueue<PreModelTask> queue = entry.getValue();

                if (queue.isEmpty()) continue;

                List<PreModelTask> batch = collectPreBatch(queue);
                if (!batch.isEmpty()) {
                    processPreBatch(modelKey, batch);
                }
            }
        }

        private List<PreModelTask> collectPreBatch(BlockingQueue<PreModelTask> queue) throws InterruptedException {
            List<PreModelTask> batch = new ArrayList<>(MAX_BATCH_SIZE);

            PreModelTask first = queue.poll();
            if (first == null) return batch;
            batch.add(first);

            long startTime = System.currentTimeMillis();
            while (batch.size() < MAX_BATCH_SIZE) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= BATCH_WAIT_MS) break;

                PreModelTask task = queue.poll(BATCH_WAIT_MS - elapsed, TimeUnit.MILLISECONDS);
                if (task == null) break;
                batch.add(task);
            }

            return batch;
        }

        private void processPreBatch(String modelKey, List<PreModelTask> batch) {
            identifyTypeNewOnnx identifier = identifyTypeLocal.get();

            for (PreModelTask task : batch) {
                try {
                    retureBoxInfo result = identifier.detectObjectsV5Onnx(
                            task.pushInfo,
                            task.frame,
                            task.netPush,
                            redisTemplate
                    );

                    task.future.complete(result);

                } catch (Exception e) {
                    log.error("[前置模型推理失败]", e);
                    task.future.complete(null);
                } finally {
                    if (task.frame != null) {
                        task.frame.release();
                    }
                }
            }

            log.debug("[前置批次完成] 模型:{}, 批次大小:{}", modelKey, batch.size());
        }
    }

    // ============ 后置模型处理 ============

    /**
     * 提交后置模型任务
     */
    private CompletableFuture<Boolean> submitPostModel(
            String streamId, Mat frame, NetPush netPush,
            TabAiSubscriptionNew pushInfo, List<retureBoxInfo> preResults) {

        String modelKey = getModelKey(netPush);

        BlockingQueue<PostModelTask> queue = postModelQueues.computeIfAbsent(
                modelKey,
                k -> new LinkedBlockingQueue<>(50)
        );

        Mat clonedFrame = new Mat();
        frame.copyTo(clonedFrame);

        PostModelTask task = new PostModelTask(
                streamId, clonedFrame, netPush, pushInfo, preResults
        );

        if (!queue.offer(task)) {
            log.warn("[后置队列已满] 模型:{}", modelKey);
            task.future.complete(false);
            clonedFrame.release();
        }

        return task.future;
    }

    /**
     * 后置模型工作线程
     */
    private class PostModelWorker implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    processPostModelBatches();
                    Thread.sleep(5);
                } catch (Exception e) {
                    log.error("[后置模型工作线程异常]", e);
                }
            }
        }

        private void processPostModelBatches() throws InterruptedException {
            for (Map.Entry<String, BlockingQueue<PostModelTask>> entry : postModelQueues.entrySet()) {
                String modelKey = entry.getKey();
                BlockingQueue<PostModelTask> queue = entry.getValue();

                if (queue.isEmpty()) continue;

                List<PostModelTask> batch = collectPostBatch(queue);
                if (!batch.isEmpty()) {
                    processPostBatch(modelKey, batch);
                }
            }
        }

        private List<PostModelTask> collectPostBatch(BlockingQueue<PostModelTask> queue) throws InterruptedException {
            List<PostModelTask> batch = new ArrayList<>(MAX_BATCH_SIZE);

            PostModelTask first = queue.poll();
            if (first == null) return batch;
            batch.add(first);

            long startTime = System.currentTimeMillis();
            while (batch.size() < MAX_BATCH_SIZE) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= BATCH_WAIT_MS) break;

                PostModelTask task = queue.poll(BATCH_WAIT_MS - elapsed, TimeUnit.MILLISECONDS);
                if (task == null) break;
                batch.add(task);
            }

            return batch;
        }

        private void processPostBatch(String modelKey, List<PostModelTask> batch) {
            identifyTypeNewOnnx identifier = identifyTypeLocal.get();

            for (PostModelTask task : batch) {
                try {
                    boolean success;

                    // 根据模型类型选择推理方法
                    if (task.netPush.getDifyType() == 2) {
                        // Pose检测
                        success = identifier.detectObjectsDifyOnnxV5Pose(
                                task.pushInfo,
                                task.frame,
                                task.netPush,
                                redisTemplate,
                                task.preResults
                        );
                    } else {
                        // 普通检测
                        if (task.netPush.getIsBeforZoom() == 0) {
                            // 开启区域放大
                            success = identifier.detectObjectsDifyOnnxV5WithROI(
                                    task.pushInfo,
                                    task.frame,
                                    task.netPush,
                                    redisTemplate,
                                    task.preResults
                            );
                        } else {
                            // 普通检测
                            success = identifier.detectObjectsDifyOnnxV5(
                                    task.pushInfo,
                                    task.frame,
                                    task.netPush,
                                    redisTemplate,
                                    task.preResults
                            );
                        }
                    }

                    task.future.complete(success);

                    if (success) {
                        totalProcessed.incrementAndGet();
                    } else {
                        totalFailed.incrementAndGet();
                    }

                } catch (Exception e) {
                    log.error("[后置模型推理失败]", e);
                    task.future.complete(false);
                    totalFailed.incrementAndGet();
                } finally {
                    if (task.frame != null) {
                        task.frame.release();
                    }
                }
            }

            log.debug("[后置批次完成] 模型:{}, 批次大小:{}", modelKey, batch.size());
        }
    }

    // ============ 辅助方法 ============

    private String getModelKey(NetPush netPush) {
        return netPush.getTabAiModel().getId() + "_" + netPush.getDifyType();
    }

    // ============ 监控统计 ============

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        int mainQueued = mainQueue.size();

        int preQueued = preModelQueues.values().stream()
                .mapToInt(BlockingQueue::size).sum();

        int postQueued = postModelQueues.values().stream()
                .mapToInt(BlockingQueue::size).sum();

        stats.put("mainQueued", mainQueued);
        stats.put("preModels", preModelQueues.size());
        stats.put("preQueued", preQueued);
        stats.put("postModels", postModelQueues.size());
        stats.put("postQueued", postQueued);
        stats.put("totalProcessed", totalProcessed.get());
        stats.put("totalFailed", totalFailed.get());

        return stats;
    }
}
