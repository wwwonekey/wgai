package org.jeecg.modules.demo.video.util.batch;


import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.opencv.dnn.Net;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局模型池 - 解决模型重复加载问题
 * 核心思想:
 * 1. 全局单例管理所有模型实例
 * 2. 使用模型ID作为key,多个摄像头共享同一个模型实例
 * 3. 使用信号量控制并发推理数量,避免CPU过载
 *
 * @author wggg
 */
@Slf4j
public class GlobalModelPool {

    private static final GlobalModelPool INSTANCE = new GlobalModelPool();

    // 模型缓存: modelId -> ModelWrapper
    private final ConcurrentHashMap<String, ModelWrapper> modelCache = new ConcurrentHashMap<>();

    // 每个模型的推理信号量: modelId -> Semaphore (控制并发推理数)
    private final ConcurrentHashMap<String, Semaphore> modelSemaphores = new ConcurrentHashMap<>();

    // 全局推理线程池 - 所有摄像头共享
    private final ExecutorService inferenceExecutor;

    // 推理任务队列统计
    private final AtomicInteger pendingInferenceTasks = new AtomicInteger(0);

    // 配置参数
    private static final int MAX_CONCURRENT_INFERENCE_PER_MODEL = 16; // 每个模型最多4个并发推理
    private static final int GLOBAL_INFERENCE_THREADS = 48; // 全局推理线程数

    private GlobalModelPool() {
        // 创建全局推理线程池
        this.inferenceExecutor = new ThreadPoolExecutor(
                GLOBAL_INFERENCE_THREADS,
                GLOBAL_INFERENCE_THREADS,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100), // 队列大小限制
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "GlobalInference-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("[全局模型池初始化] 推理线程数: {}", GLOBAL_INFERENCE_THREADS);
    }

    public static GlobalModelPool getInstance() {
        return INSTANCE;
    }

    /**
     * 获取或加载ONNX模型
     */
    public ModelWrapper getOrLoadOnnxModel(String modelId, ModelLoader loader) {
        return modelCache.computeIfAbsent(modelId, k -> {
            try {
                log.info("[加载ONNX模型] ID: {}", modelId);
                OnnxModelWrapper onnxModel = loader.loadOnnxModel();

                // 创建模型包装器
                ModelWrapper wrapper = new ModelWrapper();
                wrapper.setModelId(modelId);
                wrapper.setModelType("ONNX");
                wrapper.setOnnxSession(onnxModel.getSession());
                wrapper.setOnnxEnv(onnxModel.getEnv());
                wrapper.setLoadTime(System.currentTimeMillis());

                // 创建推理信号量
                modelSemaphores.put(modelId, new Semaphore(MAX_CONCURRENT_INFERENCE_PER_MODEL));

                log.info("[ONNX模型加载成功] ID: {}, 并发限制: {}",
                        modelId, MAX_CONCURRENT_INFERENCE_PER_MODEL);
                return wrapper;

            } catch (Exception e) {
                log.error("[ONNX模型加载失败] ID: {}", modelId, e);
                throw new RuntimeException("模型加载失败", e);
            }
        });
    }

    /**
     * 获取或加载OpenCV DNN模型
     */
    public ModelWrapper getOrLoadDnnModel(String modelId, ModelLoader loader) {
        return modelCache.computeIfAbsent(modelId, k -> {
            try {
                log.info("[加载DNN模型] ID: {}", modelId);
                Net net = loader.loadDnnModel();

                ModelWrapper wrapper = new ModelWrapper();
                wrapper.setModelId(modelId);
                wrapper.setModelType("DNN");
                wrapper.setDnnNet(net);
                wrapper.setLoadTime(System.currentTimeMillis());

                // 创建推理信号量
                modelSemaphores.put(modelId, new Semaphore(MAX_CONCURRENT_INFERENCE_PER_MODEL));

                log.info("[DNN模型加载成功] ID: {}, 并发限制: {}",
                        modelId, MAX_CONCURRENT_INFERENCE_PER_MODEL);
                return wrapper;

            } catch (Exception e) {
                log.error("[DNN模型加载失败] ID: {}", modelId, e);
                throw new RuntimeException("模型加载失败", e);
            }
        });
    }

    /**
     * ✅ JDK 8 兼容版 failedFuture 实现
     */
    private static <U> CompletableFuture<U> failedFuture(Throwable ex) {
        CompletableFuture<U> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    /**
     * 提交推理任务 - 带信号量控制
     */
    public <T> CompletableFuture<T> submitInferenceTask(
            String modelId,
            Callable<T> inferenceTask) {

        Semaphore semaphore = modelSemaphores.get(modelId);
        if (semaphore == null) {
            return failedFuture(new IllegalStateException("模型未加载: " + modelId));
        }

        // 检查队列是否过载
        int pending = pendingInferenceTasks.get();
        if (pending > 150) {
            log.warn("[推理队列过载] 当前排队: {}, 丢弃任务", pending);
            return failedFuture(
                    new RejectedExecutionException("推理队列过载"));
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        pendingInferenceTasks.incrementAndGet();

        inferenceExecutor.submit(() -> {
            boolean acquired = false;
            try {
                // 尝试获取信号量(带超时)
                acquired = semaphore.tryAcquire(2, TimeUnit.SECONDS);
                if (!acquired) {
                    future.completeExceptionally(
                            new TimeoutException("模型繁忙,获取推理权限超时"));
                    return;
                }

                // 执行推理
                T result = inferenceTask.call();
                future.complete(result);

            } catch (Exception e) {
                log.error("[推理任务执行失败] 模型: {}", modelId, e);
                future.completeExceptionally(e);
            } finally {
                if (acquired) {
                    semaphore.release();
                }
                pendingInferenceTasks.decrementAndGet();
            }
        });

        return future;
    }

    /**
     * 获取模型统计信息
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 全局模型池统计 ==========\n");
        sb.append("已加载模型数: ").append(modelCache.size()).append("\n");
        sb.append("待处理推理任务: ").append(pendingInferenceTasks.get()).append("\n");

        modelCache.forEach((modelId, wrapper) -> {
            Semaphore semaphore = modelSemaphores.get(modelId);
            int available = semaphore != null ? semaphore.availablePermits() : 0;
            sb.append(String.format("  模型[%s]: 类型=%s, 可用槽位=%d/%d\n",
                    modelId, wrapper.getModelType(), available, MAX_CONCURRENT_INFERENCE_PER_MODEL));
        });

        sb.append("===================================\n");
        return sb.toString();
    }

    /**
     * 清理指定模型
     */
    public void releaseModel(String modelId) {
        ModelWrapper wrapper = modelCache.remove(modelId);
        if (wrapper != null) {
            try {
                if (wrapper.getOnnxSession() != null) {
                    wrapper.getOnnxSession().close();
                }
                if (wrapper.getDnnNet() != null) {
                    // DNN Net不需要显式释放
                }
                modelSemaphores.remove(modelId);
                log.info("[模型已释放] ID: {}", modelId);
            } catch (Exception e) {
                log.error("[模型释放失败] ID: {}", modelId, e);
            }
        }
    }

    /**
     * 关闭模型池
     */
    public void shutdown() {
        log.info("[开始关闭全局模型池]");

        inferenceExecutor.shutdown();
        try {
            if (!inferenceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                inferenceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            inferenceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 释放所有模型
        modelCache.keySet().forEach(this::releaseModel);

        log.info("[全局模型池已关闭]");
    }

    // ==================== 内部类 ====================

    /**
     * 模型包装器
     */
    @Data
    public static class ModelWrapper {
        private String modelId;
        private String modelType; // "ONNX" or "DNN"
        private OrtSession onnxSession;
        private OrtEnvironment onnxEnv;
        private Net dnnNet;
        private long loadTime;
        private AtomicInteger usageCount = new AtomicInteger(0);

        public void incrementUsage() {
            usageCount.incrementAndGet();
        }
    }

    /**
     * 模型加载器接口
     */
    public interface ModelLoader {
        OnnxModelWrapper loadOnnxModel() throws Exception;
        Net loadDnnModel() throws Exception;
    }

    /**
     * ONNX模型包装
     */
    @Data
    public static class OnnxModelWrapper {
        private OrtSession session;
        private OrtEnvironment env;
    }
}
