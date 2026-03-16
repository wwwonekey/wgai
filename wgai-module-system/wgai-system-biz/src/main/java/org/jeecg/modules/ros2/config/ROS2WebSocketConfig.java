package org.jeecg.modules.ros2.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

/**
 * ROS2 WebSocket 客户端配置
 */
@Configuration
public class ROS2WebSocketConfig {

    /**
     * 创建 Spring WebSocketClient Bean
     */
    @Bean
    public WebSocketClient ros2WebSocketClient() {
        // 使用标准 JSR-356 WebSocket 实现
        StandardWebSocketClient client = new StandardWebSocketClient();

        // 配置 WebSocket 容器
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();

        // 设置缓冲区大小（处理大消息，如地图数据）
        container.setDefaultMaxTextMessageBufferSize(512 * 1024); // 512KB
        container.setDefaultMaxBinaryMessageBufferSize(512 * 1024);

        // 设置超时时间
        container.setDefaultMaxSessionIdleTimeout(0); // 0 = 无超时


        return client;
    }
}