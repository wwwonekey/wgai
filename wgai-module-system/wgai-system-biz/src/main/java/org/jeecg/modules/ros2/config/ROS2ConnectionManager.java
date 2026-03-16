package org.jeecg.modules.ros2.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ROS2 连接管理器
 * 负责心跳、重连等
 */
@Slf4j
@Component
public class ROS2ConnectionManager {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private WebSocketSession currentSession;
    private Runnable reconnectCallback;

    /**
     * 启动心跳
     */
    public void startHeartbeat(WebSocketSession session, long intervalMs) {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("心跳已在运行");
            return;
        }

        this.currentSession = session;

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (session != null && session.isOpen()) {
                    // ROS2 不需要特殊心跳，保持连接即可
                    log.debug("心跳检查 - 连接正常");
                } else {
                    log.warn("心跳检查 - 连接已断开");
                    stopHeartbeat();
                }
            } catch (Exception e) {
                log.error("心跳检查失败", e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        log.info("心跳已启动，间隔: {}ms", intervalMs);
    }

    /**
     * 停止心跳
     */
    public void stopHeartbeat() {
        isRunning.set(false);
        log.info("心跳已停止");
    }

    /**
     * 设置重连回调
     */
    public void setReconnectCallback(Runnable callback) {
        this.reconnectCallback = callback;
    }

    /**
     * 触发重连
     */
    public void triggerReconnect(long delayMs, int maxAttempts) {
        int attempts = reconnectAttempts.incrementAndGet();

        if (maxAttempts > 0 && attempts > maxAttempts) {
            log.error("已达最大重连次数: {}", maxAttempts);
            return;
        }

        log.info("计划重连 ({}/{}), {}ms 后执行",
                attempts, maxAttempts > 0 ? maxAttempts : "∞", delayMs);

        scheduler.schedule(() -> {
            if (reconnectCallback != null) {
                try {
                    reconnectCallback.run();
                } catch (Exception e) {
                    log.error("重连失败", e);
                    // 继续尝试重连
                    triggerReconnect(delayMs, maxAttempts);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 重置重连计数
     */
    public void resetReconnectAttempts() {
        reconnectAttempts.set(0);
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        stopHeartbeat();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}