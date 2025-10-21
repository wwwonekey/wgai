package org.jeecg.modules.demo.video.util.batch;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控工具 - 实时监控系统资源使用情况
 *
 * @author wggg
 */
@Slf4j
public class PerformanceMonitor {

    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();

    private final ScheduledExecutorService monitorExecutor;
    private final ThreadMXBean threadMXBean;

    // 性能指标
    private final AtomicLong totalInferenceTasks = new AtomicLong(0);
    private final AtomicLong failedInferenceTasks = new AtomicLong(0);
    private final AtomicLong totalInferenceTimeMs = new AtomicLong(0);

    private volatile long lastReportTime = System.currentTimeMillis();
    private volatile long lastCpuTime = 0;

    private PerformanceMonitor() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PerformanceMonitor");
            t.setDaemon(true);
            return t;
        });
    }

    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * 启动监控
     */
    public void startMonitoring() {
        log.info("[性能监控已启动]");

        // 每30秒输出一次性能报告
        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                printPerformanceReport();
            } catch (Exception e) {
                log.error("[性能监控异常]", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 记录推理任务
     */
    public void recordInferenceTask(long durationMs, boolean success) {
        totalInferenceTasks.incrementAndGet();
        totalInferenceTimeMs.addAndGet(durationMs);

        if (!success) {
            failedInferenceTasks.incrementAndGet();
        }
    }

    /**
     * 输出性能报告
     */
    private void printPerformanceReport() {
        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - lastReportTime) / 1000;

        // CPU使用率
        double cpuUsage = getCpuUsage();

        // 内存使用情况
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        // 线程统计
        int threadCount = threadMXBean.getThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();

        // 推理统计
        long totalTasks = totalInferenceTasks.get();
        long failedTasks = failedInferenceTasks.get();
        long totalTime = totalInferenceTimeMs.get();

        double avgInferenceTime = totalTasks > 0 ?
                (double) totalTime / totalTasks : 0;
        double tasksPerSecond = elapsedSeconds > 0 ?
                (double) totalTasks / elapsedSeconds : 0;
        double failureRate = totalTasks > 0 ?
                (double) failedTasks / totalTasks * 100 : 0;

        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("╔════════════════════════════════════════════════════════╗\n");
        report.append("║           系统性能监控报告                                ║\n");
        report.append("╠════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ CPU使用率: %.1f%%                                        ║\n",
                cpuUsage));
        report.append(String.format("║ 内存: %dMB / %dMB (最大: %dMB)                           ║\n",
                usedMemory, totalMemory, maxMemory));
        report.append(String.format("║ 线程数: %d (峰值: %d)                                     ║\n",
                threadCount, peakThreadCount));
        report.append("╠════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ 推理任务总数: %d                                           ║\n",
                totalTasks));
        report.append(String.format("║ 失败任务数: %d (失败率: %.2f%%)                              ║\n",
                failedTasks, failureRate));
        report.append(String.format("║ 平均推理时间: %.1fms                                           ║\n",
                avgInferenceTime));
        report.append(String.format("║ 推理吞吐量: %.1f 任务/秒                                          ║\n",
                tasksPerSecond));
        report.append("╠════════════════════════════════════════════════════════╣\n");
        report.append(getRecommendations(cpuUsage, usedMemory, maxMemory, failureRate));
        report.append("╚════════════════════════════════════════════════════════╝\n");

        log.info(report.toString());

        // 重置计数器
        lastReportTime = currentTime;
    }

    /**
     * 获取CPU使用率
     */
    private double getCpuUsage() {
        long currentCpuTime = 0;
        long[] threadIds = threadMXBean.getAllThreadIds();

        for (long threadId : threadIds) {
            long time = threadMXBean.getThreadCpuTime(threadId);
            if (time != -1) {
                currentCpuTime += time;
            }
        }

        long elapsedCpuTime = currentCpuTime - lastCpuTime;
        long elapsedTime = System.currentTimeMillis() - lastReportTime;

        if (elapsedTime == 0) {
            return 0;
        }

        lastCpuTime = currentCpuTime;

        // CPU使用率 = CPU时间增量 / (经过时间 * CPU核心数 * 1000000)
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        double cpuUsage = (double) elapsedCpuTime /
                (elapsedTime * 1000000.0 * availableProcessors) * 100;

        return Math.min(cpuUsage, 100.0);
    }

    /**
     * 获取优化建议
     */
    private String getRecommendations(double cpuUsage, long usedMemory,
                                      long maxMemory, double failureRate) {
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("║ 优化建议:                                                     ║\n");

        boolean hasRecommendation = false;

        if (cpuUsage > 80) {
            recommendations.append("║ ⚠ CPU使用率过高,建议:                               ║\n");
            recommendations.append("║   - 降低帧率(改为2秒1帧)                            ║\n");
            recommendations.append("║   - 减少每个模型的并发推理数                         ║\n");
            recommendations.append("║   - 启用GPU加速                                     ║\n");
            hasRecommendation = true;
        }

        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        if (memoryUsagePercent > 80) {
            recommendations.append("║ ⚠ 内存使用率过高,建议:                               ║\n");
            recommendations.append("║   - 减小Mat对象池大小                                ║\n");
            recommendations.append("║   - 增加JVM堆内存(-Xmx)                             ║\n");
            hasRecommendation = true;
        }

        if (failureRate > 10) {
            recommendations.append("║ ⚠ 推理失败率过高,建议:                               ║\n");
            recommendations.append("║   - 检查模型文件完整性                               ║\n");
            recommendations.append("║   - 增加推理超时时间                                 ║\n");
            hasRecommendation = true;
        }

        if (!hasRecommendation) {
            recommendations.append("║ ✓ 系统运行正常,无需调整                                  ║\n");
        }

        return recommendations.toString();
    }

    /**
     * 停止监控
     */
    public void stopMonitoring() {
        monitorExecutor.shutdown();
        log.info("[性能监控已停止]");
    }

    /**
     * 获取当前性能指标
     */
    public PerformanceMetrics getCurrentMetrics() {
        PerformanceMetrics metrics = new PerformanceMetrics();

        Runtime runtime = Runtime.getRuntime();
        metrics.setUsedMemoryMB((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        metrics.setTotalMemoryMB(runtime.totalMemory() / (1024 * 1024));
        metrics.setMaxMemoryMB(runtime.maxMemory() / (1024 * 1024));

        metrics.setThreadCount(threadMXBean.getThreadCount());
        metrics.setTotalInferenceTasks(totalInferenceTasks.get());
        metrics.setFailedInferenceTasks(failedInferenceTasks.get());

        long totalTasks = totalInferenceTasks.get();
        if (totalTasks > 0) {
            metrics.setAvgInferenceTimeMs(
                    (double) totalInferenceTimeMs.get() / totalTasks);
        }

        return metrics;
    }

    @Data
    public static class PerformanceMetrics {
        private long usedMemoryMB;
        private long totalMemoryMB;
        private long maxMemoryMB;
        private int threadCount;
        private long totalInferenceTasks;
        private long failedInferenceTasks;
        private double avgInferenceTimeMs;
    }
}
