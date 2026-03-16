package org.jeecg.modules.ros2.service;


import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.message.websocket.WebSocket;
import org.jeecg.modules.ros2.model.VelocityCommand;
import org.jeecg.modules.ros2.model.VelocityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * WebSocket 推送服务
 * 适配  WebSocket
 */
@Slf4j
@Service
public class WebSocketPushService {

    @Autowired
    private WebSocket webSocket;

    /**
     * 推送速度数据到所有客户端
     */
    public void pushVelocity(VelocityCommand velocity) {
        try {
            VelocityVO vo = VelocityVO.fromEntity(velocity);

            // 构造消息对象
            JSONObject message = new JSONObject();
            message.put("type", "velocity");
            message.put("data", vo);
            message.put("timestamp", System.currentTimeMillis());

            // 使用 JeecgBoot 的 WebSocket 推送
            webSocket.sendMessage(message.toJSONString());

        } catch (Exception e) {
            log.error("推送速度数据失败", e);
        }
    }

    /**
     * 推送位置数据
     */
    public void pushPosition(Object position) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "position");
            message.put("data", position);
            message.put("timestamp", System.currentTimeMillis());

            webSocket.sendMessage(message.toJSONString());

        } catch (Exception e) {
            log.error("推送位置数据失败", e);
        }
    }

    /**
     * 推送地图数据
     */
    public void pushMap(Object mapData) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "map");
            message.put("data", mapData);
            message.put("timestamp", System.currentTimeMillis());

            webSocket.sendMessage(message.toJSONString());

        } catch (Exception e) {
            log.error("推送地图数据失败", e);
        }
    }

    /**
     * 推送路径数据
     */
    public void pushPath(Object pathData) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "path");
            message.put("data", pathData);
            message.put("timestamp", System.currentTimeMillis());

            webSocket.sendMessage(message.toJSONString());

        } catch (Exception e) {
            log.error("推送路径数据失败", e);
        }
    }

    /**
     * 推送消息给指定用户
     */
    public void pushToUser(String userId, String type, Object data) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", type);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());

            webSocket.sendMessage(userId, message.toJSONString());

        } catch (Exception e) {
            log.error("推送消息到用户失败: userId={}", userId, e);
        }
    }

    /**
     * 推送到所有客户端
     */
    public void pushToAll(String type, Object data) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", type);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());

            webSocket.sendMessage(message.toJSONString());

        } catch (Exception e) {
            log.error("推送消息失败: type={}", type, e);
        }
    }
}
