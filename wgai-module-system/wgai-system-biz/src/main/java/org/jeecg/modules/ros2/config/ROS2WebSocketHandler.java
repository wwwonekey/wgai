package org.jeecg.modules.ros2.config;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.ros2.service.VelocityMonitorService;
import org.jeecg.modules.ros2.service.WebSocketPushService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.function.Consumer;

/**
 * ROS2 WebSocket 消息处理器
 */
@Slf4j
public class ROS2WebSocketHandler extends TextWebSocketHandler {

    private final VelocityMonitorService velocityService;
    private final WebSocketPushService pushService;
    private final Gson gson = new Gson();
    private final Consumer<WebSocketSession> onConnectCallback;
    private final Runnable onDisconnectCallback;

    public ROS2WebSocketHandler(
            VelocityMonitorService velocityService,
            WebSocketPushService pushService,
            Consumer<WebSocketSession> onConnectCallback,
            Runnable onDisconnectCallback) {
        this.velocityService = velocityService;
        this.pushService = pushService;
        this.onConnectCallback = onConnectCallback;
        this.onDisconnectCallback = onDisconnectCallback;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("✅ ROS2 Bridge 连接已建立，Session ID: {}", session.getId());

        // 设置消息大小限制
        session.setTextMessageSizeLimit(10 * 1024 * 1024); // 10MB
        session.setBinaryMessageSizeLimit(10 * 1024 * 1024);

        if (onConnectCallback != null) {
            try {
                onConnectCallback.accept(session);
            } catch (Exception e) {
                log.error("执行连接回调失败", e);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        handleRosMessage(payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.warn("❌ ROS2 Bridge 连接已关闭，状态: code={}, reason={}",
                status.getCode(), status.getReason());

        if (onDisconnectCallback != null) {
            try {
                onDisconnectCallback.run();
            } catch (Exception e) {
                log.error("执行断开回调失败", e);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("ROS2 Bridge 传输错误", exception);
    }

    /**
     * 处理 ROS 消息
     */
    private void handleRosMessage(String message) {
        try {

            JsonObject json = gson.fromJson(message, JsonObject.class);

            if (!json.has("topic")) {
                log.warn("收到非话题消息");
                return;
            }

            String topic = json.get("topic").getAsString();
            log.info("[正在接收ROS消息:topic:{}   主要数据内容:{}]",topic,json);
            if (!json.has("msg")) {
                log.warn("消息不包含 msg 字段");
                return;
            }

            JsonObject msg = json.getAsJsonObject("msg");

            // 根据话题分发消息
            switch (topic) {
                case "/cmd_vel":
                    handleCmdVel(msg);
                    break;
                case "/map":
                    handleMapUpdate(msg);
                    break;
                case "/amcl_pose":
                    handleRobotPose(msg);
                    break;
                case "/plan":
                    handlePath(msg);
                    break;
                default:
                    log.warn("收到未处理的话题: {}", topic);
            }

        } catch (Exception e) {
            log.warn("处理 ROS 消息失败", e);
        }
    }

    /**
     * 处理速度指令
     */
    private void handleCmdVel(JsonObject msg) {
        try {
            velocityService.handleVelocityMessage(msg);
        } catch (Exception e) {
            log.error("处理速度消息失败", e);
        }
    }

    /**
     * 处理地图更新
     */
    private void handleMapUpdate(JsonObject msg) {
        try {
            // 提取地图信息
            JsonObject info = msg.getAsJsonObject("info");
            if (info == null) {
                log.warn("地图消息缺少 info 字段");
                return;
            }

            int width = info.get("width").getAsInt();
            int height = info.get("height").getAsInt();
            double resolution = info.get("resolution").getAsDouble();

            // 提取地图数据
            JsonArray dataArray = msg.getAsJsonArray("data");
            if (dataArray == null) {
                log.warn("地图消息缺少 data 字段");
                return;
            }

            // 提取原点
            JsonObject origin = info.getAsJsonObject("origin");
            JsonObject position = origin.getAsJsonObject("position");

            // 构建前端消息（使用 JSONObject）
            JSONObject mapData = new JSONObject();
            mapData.put("width", width);
            mapData.put("height", height);
            mapData.put("resolution", resolution);

            // 转换数据数组
            JSONArray dataArr = new JSONArray();
            for (int i = 0; i < dataArray.size(); i++) {
                dataArr.add(dataArray.get(i).getAsInt());
            }
            mapData.put("data", dataArr);

            // 添加原点
            JSONObject originObj = new JSONObject();
            originObj.put("x", position.get("x").getAsDouble());
            originObj.put("y", position.get("y").getAsDouble());
            mapData.put("origin", originObj);

            // 通过 pushService 推送
            pushService.pushToAll("map_update", mapData);

            log.info("✅ 地图更新已推送: {}x{}, 分辨率: {}", width, height, resolution);

        } catch (Exception e) {
            log.error("处理地图消息失败", e);
        }
    }

    /**
     * 处理机器人位姿
     */
    private void handleRobotPose(JsonObject msg) {
        try {
            JsonObject poseWithCovariance = msg.getAsJsonObject("pose");
            if (poseWithCovariance == null) {
                log.warn("位姿消息缺少 pose 字段");
                return;
            }

            JsonObject pose = poseWithCovariance.getAsJsonObject("pose");
            JsonObject position = pose.getAsJsonObject("position");
            JsonObject orientation = pose.getAsJsonObject("orientation");

            // 四元数转欧拉角
            double x = orientation.get("x").getAsDouble();
            double y = orientation.get("y").getAsDouble();
            double z = orientation.get("z").getAsDouble();
            double w = orientation.get("w").getAsDouble();
            double theta = Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));

            // 构建位姿数据
            JSONObject poseData = new JSONObject();
            poseData.put("x", position.get("x").getAsDouble());
            poseData.put("y", position.get("y").getAsDouble());
            poseData.put("theta", theta);

            // 推送到前端
            pushService.pushToAll("robot_pose", poseData);

            log.debug("机器人位姿已推送: x={}, y={}, theta={}",
                    poseData.getDouble("x"),
                    poseData.getDouble("y"),
                    poseData.getDouble("theta"));

        } catch (Exception e) {
            log.error("处理机器人位姿失败", e);
        }
    }

    /**
     * 处理路径规划
     */
    private void handlePath(JsonObject msg) {
        try {
            JsonArray poses = msg.getAsJsonArray("poses");
            if (poses == null) {
                log.warn("路径消息缺少 poses 字段");
                return;
            }

            // 构建路径数据
            JSONArray posesArray = new JSONArray();

            for (int i = 0; i < poses.size(); i++) {
                JsonObject poseStamped = poses.get(i).getAsJsonObject();
                JsonObject pose = poseStamped.getAsJsonObject("pose");
                JsonObject position = pose.getAsJsonObject("position");

                JSONObject point = new JSONObject();
                point.put("x", position.get("x").getAsDouble());
                point.put("y", position.get("y").getAsDouble());
                posesArray.add(point);
            }

            JSONObject pathData = new JSONObject();
            pathData.put("poses", posesArray);

            // 推送到前端
            pushService.pushToAll("path_update", pathData);

            log.info("✅ 路径规划已推送: {} 个路径点", posesArray.size());

        } catch (Exception e) {
            log.error("处理路径消息失败", e);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return true; // 支持分片消息
    }
}