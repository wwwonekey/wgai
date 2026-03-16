package org.jeecg.modules.demo.audio.util.video;

import lombok.extern.slf4j.Slf4j;

import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频流管理器 - 保活视频流，按需检测
 * @author wggg
 * @date 2025/12/2
 */
@Component
@Slf4j
public class VideoStreamManager {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 视频流映射表
     */
    private final Map<String, videoUtil> streamMap = new ConcurrentHashMap<>();

    /**
     * 初始化视频流（启动保活）
     * @param streamId 流ID
     * @param rtspUrl RTSP地址
     */
    public void initStream(String streamId, String rtspUrl) {
        if (streamMap.containsKey(streamId)) {
            log.warn("⚠️ [视频流已存在] StreamId: {}", streamId);
            return;
        }

        videoUtil util = new videoUtil(rtspUrl, redisTemplate);
        util.startVideoStream(); // 启动保活
        streamMap.put(streamId, util);

        log.info("✅ [视频流已初始化并保活] StreamId: {}", streamId);
    }

    /**
     * 开始检测（传入参数触发检测）
     * @param streamId 流ID
     * @param netPush 检测参数
     * @param callback 检测结果回调
     */
    public void startDetection(String streamId, NetPush netPush, DetectionCallback callback) {
        videoUtil util = streamMap.get(streamId);
        if (util == null) {
            log.error("❌ [视频流不存在] StreamId: {}", streamId);
            if (callback != null) {
                callback.onDetectionError(new IllegalStateException("视频流不存在"));
            }
            return;
        }

        log.info("🔍 [触发检测] StreamId: {}, 目标: {}", streamId, netPush.getBeforText());
        util.startDetection(netPush, callback);
    }

    /**
     * 同步检测（阻塞等待结果）
     * @param streamId 流ID
     * @param netPush 检测参数
     * @param timeoutSeconds 超时时间（秒）
     * @return 检测结果
     */
    public retureBoxInfo detectSync(String streamId, NetPush netPush, int timeoutSeconds) {
        videoUtil util = streamMap.get(streamId);
        if (util == null) {
            log.error("❌ [视频流不存在] StreamId: {}", streamId);
            return null;
        }

        log.info("🔍 [同步检测] StreamId: {}, 超时: {}秒", streamId, timeoutSeconds);
        return util.detectSync(netPush, timeoutSeconds);
    }

    /**
     * 停止检测（但保持视频流）
     * @param streamId 流ID
     */
    public void stopDetection(String streamId) {
        videoUtil util = streamMap.get(streamId);
        if (util != null) {
            util.stopDetection();
            log.info("🛑 [检测已停止] StreamId: {}", streamId);
        }
    }

    /**
     * 销毁视频流（彻底关闭）
     * @param streamId 流ID
     */
    public void destroyStream(String streamId) {
        videoUtil util = streamMap.remove(streamId);
        if (util != null) {
            util.stopVideoStream();
            log.info("🛑 [视频流已销毁] StreamId: {}", streamId);
        }
    }

    /**
     * 获取视频流状态
     */
    public StreamStatus getStreamStatus(String streamId) {
        videoUtil util = streamMap.get(streamId);
        if (util == null) {
            return null;
        }

        StreamStatus status = new StreamStatus();
        status.setStreamId(streamId);
        status.setStreamRunning(util.isStreamRunning());
        status.setDetectionEnabled(util.isDetectionEnabled());
        status.setDetecting(util.isDetecting());
        status.setDetectionAttempts(util.getDetectionAttempts());
        return status;
    }

    /**
     * 停止所有视频流
     */
    @PreDestroy
    public void destroyAllStreams() {
        log.info("🔄 [正在销毁所有视频流] 总数: {}", streamMap.size());
        streamMap.forEach((id, util) -> {
            try {
                util.stopVideoStream();
                log.info("✅ [已销毁] StreamId: {}", id);
            } catch (Exception e) {
                log.error("❌ [销毁失败] StreamId: {}", id, e);
            }
        });
        streamMap.clear();
        log.info("✅ [所有视频流已销毁]");
    }

    /**
     * 视频流状态
     */
    public static class StreamStatus {
        private String streamId;
        private boolean streamRunning;
        private boolean detectionEnabled;
        private boolean detecting;
        private int detectionAttempts;

        // Getter/Setter
        public String getStreamId() { return streamId; }
        public void setStreamId(String streamId) { this.streamId = streamId; }

        public boolean isStreamRunning() { return streamRunning; }
        public void setStreamRunning(boolean streamRunning) { this.streamRunning = streamRunning; }

        public boolean isDetectionEnabled() { return detectionEnabled; }
        public void setDetectionEnabled(boolean detectionEnabled) { this.detectionEnabled = detectionEnabled; }

        public boolean isDetecting() { return detecting; }
        public void setDetecting(boolean detecting) { this.detecting = detecting; }

        public int getDetectionAttempts() { return detectionAttempts; }
        public void setDetectionAttempts(int detectionAttempts) { this.detectionAttempts = detectionAttempts; }
    }
}