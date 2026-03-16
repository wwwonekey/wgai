package org.jeecg.modules.demo.audio.service;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.demo.audio.entity.VoiceRecognitionProperties;
import org.jeecg.modules.demo.audio.util.VoiceRecognitionCallback;
import org.jeecg.modules.demo.audio.util.ChineseTTSCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 语音识别服务
 * @author wggg
 * @date 2025/12/01
 */
@Slf4j
@Service
public class VoiceRecognitionService {

    @Autowired
    private VoiceRecognitionProperties properties;

    private ChineseTTSCallback tts;

    private volatile boolean isRunning = false;

 //   @PostConstruct
    public void init() {
        tts = new ChineseTTSCallback();
        log.info("语音识别服务初始化完成");
        log.info("关键词模型: {}", properties.getKeywordModel().getEncoder());
        log.info("识别模型: {}", properties.getRecognitionModel().getEncoder());
        log.info("识别超时: {}ms", properties.getRecognition().getTimeout());
        log.info("自动启动: {}", properties.getRecognition().isAutoStart());

        if (properties.getRecognition().isAutoStart()) {
            log.info("自动启动取餐码识别服务...");
            startPickupCodeRecognition();
        }

        //保活视频

    }

    @PreDestroy
    public void destroy() {
        if (tts != null) {
            tts.shutdown();
            log.info("语音识别服务已关闭");
        }
    }

    /**
     * 启动取餐码识别服务
     */
    public void startPickupCodeRecognition() {
        if (isRunning) {
            log.warn("识别服务已在运行中");
            return;
        }

        VoiceRecognitionCallback callback = new VoiceRecognitionCallback() {
            private int orderCount = 0;

            @Override
            public void onKeywordDetected(String keyword) {
                orderCount++;
                log.info("【第{}单】骑手唤醒: {}", orderCount, keyword);

                // 播报提示语（播报完成后重置计时器）
//                tts.speakChinese(
//                        properties.getTts().getPrompt(),
//                        properties.getTts().getSpeed(),
//                        properties.getTts().getVolume(),
//                        true  // 重置计时器
//                );
            }

            @Override
            public void onRecognitionStart(int timeoutMs) {
                log.info("等待取餐码（{}秒）...", timeoutMs / 1000);
            }

            @Override
            public void onRecognizing(String text, boolean isFinal) {
                if (isFinal) {
                    log.info("识别到完整句子: {}", text);
                }
            }

            @Override
            public void onRecognitionComplete(String finalText) {
                log.info("取餐码识别完成: {}", finalText);
                handlePickupCode(finalText);
            }

            @Override
            public void onTimeout(String partialText) {
                if (partialText.isEmpty()) {
                    log.warn("识别超时，未听清内容");
                    tts.speakChinese(
                            properties.getTts().getTimeoutPrompt(),
                            properties.getTts().getSpeed(),
                            properties.getTts().getVolume(),
                            false  // 不重置计时器
                    );
                } else {
                    log.warn("识别超时，部分结果: {}", partialText);
                    handlePickupCode(partialText);
                }
                log.info("回到关键词监听，等待下一位骑手...");
            }

            @Override
            public void onError(String error, Exception exception) {
                log.error("识别错误: {}", error, exception);
                tts.speakChinese("系统错误，请稍后再试",
                        properties.getTts().getSpeed(),
                        properties.getTts().getVolume(),
                        false);
            }

            @Override
            public void onStateChanged(RecognitionState state) {
                log.debug("状态变化: {}", state.getDescription());
                if (state == RecognitionState.WAITING_KEYWORD) {
                    log.info(">>> 等待关键词唤醒...");
                }
            }
        };

        // 启动识别
        VoiceRecognitionProperties.KeywordModel kwModel = properties.getKeywordModel();
        VoiceRecognitionProperties.RecognitionModel recModel = properties.getRecognitionModel();

        tts.startRecognitionAsync(
                kwModel.getEncoder(), kwModel.getDecoder(), kwModel.getJoiner(),
                kwModel.getTokens(), kwModel.getKeywords(),
                recModel.getEncoder(), recModel.getDecoder(), recModel.getJoiner(),
                recModel.getTokens(), recModel.getRuleFsts(),
                properties.getRecognition().getTimeout(),
                callback
        );

        isRunning = true;
        log.info("取餐码识别系统已启动，支持连续多个骑手取餐");
    }

    /**
     * 停止识别服务
     */
    public void stopPickupCodeRecognition() {
        if (tts != null && isRunning) {
            tts.stopRecognition();
            isRunning = false;
            log.info("取餐码识别服务已停止");
        }
    }

    /**
     * 处理取餐码
     */
    private void handlePickupCode(String text) {
        try {
            String code = extractPickupCode(text);

            if (code != null && !code.isEmpty()) {
                log.info("✓ 提取到取餐码: {}", code);

                // 保存到数据库
                // pickupCodeService.save(code);

                // 播报确认
                String formattedCode = formatCodeForSpeech(code);
                String confirmMessage = properties.getTts().getConfirmPrefix()
                        + formattedCode
                        + properties.getTts().getConfirmSuffix();

                tts.speakChinese(
                        confirmMessage,
                        properties.getTts().getSpeed(),
                        properties.getTts().getVolume(),
                        false  // 不重置计时器
                );

                // 发送通知
                // notificationService.sendPickupNotification(code);

                log.info("取餐码处理完成，等待下一位骑手...");

            } else {
                log.warn("✗ 未能提取取餐码: {}", text);
                tts.speakChinese("未识别到取餐码，请重新播报",
                        properties.getTts().getSpeed(),
                        properties.getTts().getVolume(),
                        false);
            }
        } catch (Exception e) {
            log.error("处理取餐码失败", e);
            tts.speakChinese("处理失败，请重试",
                    properties.getTts().getSpeed(),
                    properties.getTts().getVolume(),
                    false);
        }
    }

    /**
     * 提取取餐码
     */
    private String extractPickupCode(String text) {
        // 移除所有标点和空格
        text = text.replaceAll("[\\s\\p{Punct}]", "");

        // 转换中文数字
        text = convertChineseNumbers(text);

        // 提取数字
        String code = text.replaceAll("[^0-9]", "");

        // 取餐码通常是3-6位数字
        if (code.length() >= 3 && code.length() <= 6) {
            return code;
        }

        return null;
    }

    /**
     * 转换中文数字
     */
    private String convertChineseNumbers(String text) {
        return text.replace("零", "0")
                .replace("一", "1")
                .replace("二", "2")
                .replace("三", "3")
                .replace("四", "4")
                .replace("五", "5")
                .replace("六", "6")
                .replace("七", "7")
                .replace("八", "8")
                .replace("九", "9")
                .replace("幺", "1");
    }

    /**
     * 格式化取餐码用于播报
     */
    private String formatCodeForSpeech(String code) {
        StringBuilder result = new StringBuilder();
        String[] numbers = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

        for (char c : code.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = c - '0';
                result.append(numbers[digit]);
            }
        }

        return result.toString();
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 播报语音
     */
    public void speak(String text) {
        speak(text, properties.getTts().getSpeed(), properties.getTts().getVolume());
    }

    /**
     * 播报语音（自定义参数）
     */
    public void speak(String text, int speed, int volume) {
        if (tts != null) {
            tts.speakChinese(text, speed, volume, false);
        }
    }
}