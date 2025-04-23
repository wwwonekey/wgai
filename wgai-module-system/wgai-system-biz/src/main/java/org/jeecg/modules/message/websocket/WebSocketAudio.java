package org.jeecg.modules.message.websocket;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.base.BaseMap;
import org.jeecg.common.constant.WebsocketConst;
import org.jeecg.common.modules.redis.client.JeecgRedisClient;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.demo.tab.service.ITabAiHistoryService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author scott
 * @Date 2019/11/29 9:41
 * @Description: 此注解相当于设置访问URL
 */
@Component
@Slf4j
@ServerEndpoint("/WebSocketAudio/{userId}")
public class WebSocketAudio {
    
    /**线程安全Map*/
    private static ConcurrentHashMap<String, Session> sessionPool = new ConcurrentHashMap<>();

    /**
     * Redis触发监听名字
     */
    public static final String REDIS_TOPIC_NAME = "socketHandler";
    @Resource
    private JeecgRedisClient jeecgRedisClient;




    //==========【websocket接受、推送消息等方法 —— 具体服务节点推送ws消息】========================================================================================
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "userId") String userId) {
        try {
            sessionPool.put(userId, session);
            log.info("WebSocketAudio连接人：{}",userId);

            log.info("【系统 WebSocketAudioAudio】有新的连接，总数为:" + sessionPool.size());
        } catch (Exception e) {
        }
    }

    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        try {
            sessionPool.remove(userId);
            log.info("【系统 WebSocketAudioAudio】连接断开，总数为:" + sessionPool.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ws推送消息
     *
     * @param userId
     * @param message
     */
    public void pushMessage(String userId, String message) {
        for (Map.Entry<String, Session> item : sessionPool.entrySet()) {
            //userId key值= {用户id + "_"+ 登录token的md5串}
            //TODO vue2未改key新规则，暂时不影响逻辑
            if (item.getKey().contains(userId)) {
                Session session = item.getValue();
                try {
                    //update-begin-author:taoyan date:20211012 for: websocket报错 https://gitee.com/jeecg/jeecg-boot/issues/I4C0MU
                    synchronized (session){
                        log.info("【系统 WebSocketAudioAudio】推送单人消息:" + message);
                        session.getBasicRemote().sendText(message);
                    }
                    //update-end-author:taoyan date:20211012 for: websocket报错 https://gitee.com/jeecg/jeecg-boot/issues/I4C0MU
                } catch (Exception e) {
                    log.error(e.getMessage(),e);
                }
            }
        }
    }

    /**
     * ws遍历群发消息
     */
    public void pushMessage(String message) {
        try {
            for (Map.Entry<String, Session> item : sessionPool.entrySet()) {
                try {
                    synchronized(item){
                        item.getValue().getAsyncRemote().sendText(message);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            log.info("【系统 WebSocketAudio】群发消息:" + message);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * ws遍历群发消息
     */
    public void pushMessageByte( byte[] encodedData) {
        try {
            for (Map.Entry<String, Session> item : sessionPool.entrySet()) {
                Session session = item.getValue();
                try {
                    synchronized(session){
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(encodedData));
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            log.info("【系统 WebSocketAudio】群发消息:" + encodedData);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void broadcastFrame( ByteBuffer buffer) {
        try {
            for (Map.Entry<String, Session> item : sessionPool.entrySet()) {
                Session session = item.getValue();
                try {
                    synchronized(session){
                        session.getBasicRemote().sendBinary(buffer);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            log.info("【系统 WebSocketAudio】群发消息:" + buffer);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
    /**
     * ws接受客户端消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam(value = "userId") String userId) {
        log.info("1【系统 WebSocketAudio】收到客户端消息:" + message);


    }

    /**
     * 配置错误信息处理
     *
     * @param session
     * @param t-
     */
    @OnError
    public void onError(Session session, Throwable t) {
        log.warn("【系统 WebSocketAudio】消息出现错误");
        t.printStackTrace();

    }
    //==========【系统 WebSocketAudio接受、推送消息等方法 —— 具体服务节点推送ws消息】========================================================================================
    

    //==========【采用redis发布订阅模式——推送消息】========================================================================================
    /**
     * 后台发送消息到redis
     *
     * @param message
     */
    public void sendMessage(String message) {
        //log.info("【系统 WebSocketAudio】广播消息:" + message);
        BaseMap baseMap = new BaseMap();
        baseMap.put("userId", "");
        baseMap.put("message", message);
        jeecgRedisClient.sendMessage(REDIS_TOPIC_NAME, baseMap);
    }

    /**
     * 此为单点消息 redis
     *
     * @param userId
     * @param message
     */
    public void sendMessage(String userId, String message) {
        BaseMap baseMap = new BaseMap();
        baseMap.put("userId", userId);
        baseMap.put("message", message);
        jeecgRedisClient.sendMessage(REDIS_TOPIC_NAME, baseMap);
    }

    /**
     * 此为单点消息(多人) redis
     *
     * @param userIds
     * @param message
     */
    public void sendMessage(String[] userIds, String message) {
        for (String userId : userIds) {
            sendMessage(userId, message);
        }
    }
    //=======【采用redis发布订阅模式——推送消息】==========================================================================================
    
}