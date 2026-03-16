package org.jeecg.modules.demo.audio.util.video;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 视频检测业务服务
 * @author wggg
 * @date 2025/12/2
 */
@Service
@Slf4j
public class VideoDetectionService {





    @Autowired
    private VideoStreamManager videoStreamManager;

    /**
     * 初始化摄像头（启动视频流保活）
     * @param cameraId 摄像头ID
     * @param rtspUrl RTSP地址
     */
    public void initCamera(String cameraId, String rtspUrl) {
        log.info("📹 [初始化摄像头] CameraId: {}", cameraId);
        videoStreamManager.initStream(cameraId, rtspUrl);
    }

    /**
     * 触发检测（异步）
     * @param cameraId 摄像头ID
     * @param netPush 检测参数
     * @param callback 回调
     */
    public void triggerDetection(String cameraId, NetPush netPush, DetectionCallback callback) {
        log.info("🔍 [触发检测] CameraId: {}, 目标: {}", cameraId, netPush.getBeforText());
        videoStreamManager.startDetection(cameraId, netPush, callback);
    }

    /**
     * 触发检测（同步，阻塞等待结果）
     * @param cameraId 摄像头ID
     * @param netPush 检测参数
     * @param timeoutSeconds 超时时间（秒）
     * @return 检测结果
     */
    public retureBoxInfo triggerDetectionSync(String cameraId, NetPush netPush, int timeoutSeconds) {
        log.info("🔍 [同步检测] CameraId: {}, 目标: {}", cameraId, netPush.getBeforText());
        return videoStreamManager.detectSync(cameraId, netPush, timeoutSeconds);
    }

    /**
     * 停止检测
     * @param cameraId 摄像头ID
     */
    public void stopDetection(String cameraId) {
        log.info("🛑 [停止检测] CameraId: {}", cameraId);
        videoStreamManager.stopDetection(cameraId);
    }

    /**
     * 销毁摄像头（彻底关闭视频流）
     * @param cameraId 摄像头ID
     */
    public void destroyCamera(String cameraId) {
        log.info("🛑 [销毁摄像头] CameraId: {}", cameraId);
        videoStreamManager.destroyStream(cameraId);
    }

    /**
     * 获取摄像头状态
     */
    public VideoStreamManager.StreamStatus getCameraStatus(String cameraId) {
        return videoStreamManager.getStreamStatus(cameraId);
    }
}