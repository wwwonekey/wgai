package org.jeecg.modules.demo.audio.util.video;

import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;

/**
 * 视频检测结果回调接口
 * @author wggg
 * @date 2025/12/2
 */
public interface DetectionCallback {

    /**
     * 检测成功回调（检测到目标）
     * @param result 检测结果
     */
    void onDetectionSuccess(retureBoxInfo result);

    /**
     * 检测超时回调（超过最大检测次数仍未检测到）
     * @param attemptCount 尝试次数
     */
    void onDetectionTimeout(int attemptCount);

    /**
     * 检测异常回调
     * @param e 异常信息
     */
    void onDetectionError(Exception e);
}