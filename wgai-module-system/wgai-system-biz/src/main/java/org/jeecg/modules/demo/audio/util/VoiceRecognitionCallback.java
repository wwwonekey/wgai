package org.jeecg.modules.demo.audio.util;

/**
 * 语音识别回调接口
 * @author wggg
 * @date 2025/11/28
 */
public interface VoiceRecognitionCallback {

    /**
     * 关键词检测到
     * @param keyword 检测到的关键词
     */
    void onKeywordDetected(String keyword);

    /**
     * 开始语音识别
     * @param timeoutMs 超时时间（毫秒）
     */
    void onRecognitionStart(int timeoutMs);

    /**
     * 识别中 - 实时返回识别内容
     * @param text 当前识别到的文本
     * @param isFinal 是否是最终结果
     */
    void onRecognizing(String text, boolean isFinal);

    /**
     * 识别完成
     * @param finalText 最终识别结果
     */
    void onRecognitionComplete(String finalText);

    /**
     * 识别超时
     * @param partialText 超时前识别到的部分文本
     */
    void onTimeout(String partialText);

    /**
     * 发生错误
     * @param error 错误信息
     * @param exception 异常对象
     */
    void onError(String error, Exception exception);

    /**
     * 状态变化通知
     * @param state 当前状态
     */
    void onStateChanged(RecognitionState state);

    /**
     * 识别状态枚举
     */
    enum RecognitionState {
        WAITING_KEYWORD("等待关键词"),
        RECOGNIZING("正在识别"),
        TIMEOUT("超时"),
        COMPLETED("完成"),
        ERROR("错误");

        private final String description;

        RecognitionState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}