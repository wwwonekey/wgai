package org.jeecg.modules.ros2.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.ros2.config.ROS2Config;
import org.jeecg.modules.ros2.config.ROS2WebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ROS2BridgeService {

    @Autowired
    private ROS2Config ros2Config;

    @Autowired
    private WebSocketClient webSocketClient;

    @Autowired
    private VelocityMonitorService velocityService;

    @Autowired
    private WebSocketPushService pushService; // ✅ 注入推送服务

    private WebSocketSession session;
    private final Gson gson = new Gson();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        if (ros2Config.isAutoConnect()) {
            connect();
        }
    }

    public void connect() {
        try {
            log.info("开始连接 ROS2 Bridge: {}", ros2Config.getBridgeUrl());

            // 创建消息处理器（传入 pushService）
            ROS2WebSocketHandler handler = new ROS2WebSocketHandler(
                    velocityService,
                    pushService,         // ✅ 传入推送服务
                    this::onConnected,
                    this::attemptReconnect
            );

            URI uri = new URI(ros2Config.getBridgeUrl());
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

            session = webSocketClient.doHandshake(handler, headers, uri).get();

            log.info("✅ ROS2 Bridge 连接成功，Session ID: {}", session.getId());
            reconnectAttempts.set(0);

        } catch (Exception e) {
            log.error("连接 ROS2 Bridge 失败", e);
            attemptReconnect();
        }
    }

    private void onConnected(WebSocketSession connectedSession) {
        log.info("触发连接成功回调");

        if (this.session == null) {
            this.session = connectedSession;
        }

        subscribeToTopics();
    }

    private void subscribeToTopics() {
        log.info("开始订阅 ROS2 话题");

        // 订阅速度指令
        subscribe("/cmd_vel", "geometry_msgs/Twist");

        // 订阅地图话题（2秒更新一次）
        subscribe("/map", "nav_msgs/OccupancyGrid", 2000);

        // 订阅机器人位姿
        subscribe("/amcl_pose", "geometry_msgs/PoseWithCovarianceStamped");

        // 订阅路径规划
        subscribe("/plan", "nav_msgs/Path");

        log.info("话题订阅完成");
    }

    private void subscribe(String topic, String type) {
        subscribe(topic, type, 0);
    }

    private void subscribe(String topic, String type, int throttleRate) {
        JsonObject json = new JsonObject();
        json.addProperty("op", "subscribe");
        json.addProperty("topic", topic);
        json.addProperty("type", type);
        if (throttleRate > 0) {
            json.addProperty("throttle_rate", throttleRate);
        }
        send(json.toString());
        log.info("已订阅话题: {} ({}) - 节流: {}ms", topic, type, throttleRate);
    }

    public void send(String message) {
        try {
            if (session == null || !session.isOpen()) {
                log.warn("ROS2 Bridge 未连接，无法发送消息");
                return;
            }

            session.sendMessage(new TextMessage(message));
            log.debug("发送消息: {}", message);

        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }

    public void publish(String topic, String type, JsonObject message) {
        JsonObject json = new JsonObject();
        json.addProperty("op", "publish");
        json.addProperty("topic", topic);
        json.addProperty("type", type);
        json.add("msg", message);
        send(json.toString());
    }

    private void attemptReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();

        if (ros2Config.getMaxReconnectAttempts() > 0
                && attempts > ros2Config.getMaxReconnectAttempts()) {
            log.error("达到最大重连次数，停止重连");
            return;
        }

        log.info("尝试重连 ROS2 Bridge ({}/{})",
                attempts,
                ros2Config.getMaxReconnectAttempts() > 0 ? ros2Config.getMaxReconnectAttempts() : "∞");

        try {
            Thread.sleep(ros2Config.getReconnectInterval());
            connect();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("重连线程被中断", e);
        }
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (session != null && session.isOpen()) {
                session.close();
                log.info("ROS2 Bridge 连接已关闭");
            }
        } catch (Exception e) {
            log.error("关闭连接失败", e);
        }
    }
}