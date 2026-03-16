package org.jeecg.modules.demo.audio.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音识别配置类
 * @author wggg
 * @date 2025/12/01
 */
@Data
@Component
@ConfigurationProperties(prefix = "audio")
public class VoiceRecognitionProperties {

    /**
     * 关键词模型配置
     */
    private KeywordModel keywordModel = new KeywordModel();

    /**
     * 语音识别模型配置
     */
    private RecognitionModel recognitionModel = new RecognitionModel();

    /**
     * 识别配置
     */
    private Recognition recognition = new Recognition();

    /**
     * TTS配置
     */
    private Tts tts = new Tts();

    @Data
    public static class KeywordModel {
        private String encoder;
        private String decoder;
        private String joiner;
        private String tokens;
        private String keywords;
    }

    @Data
    public static class RecognitionModel {
        private String encoder;
        private String decoder;
        private String joiner;
        private String tokens;
        private String ruleFsts;
    }

    @Data
    public static class Recognition {
        /**
         * 识别超时时间（毫秒）
         */
        private int timeout = 10000;

        /**
         * 是否自动启动
         */
        private boolean autoStart = true;
    }

    @Data
    public static class Tts {
        /**
         * 语速 (-10 到 10)
         */
        private int speed = 2;

        /**
         * 音量 (0 到 100)
         */
        private int volume = 100;

        /**
         * 提示语
         */
        private String prompt = "请播报取餐码如美团1101号,饿了么1102";

        /**
         * 超时提示
         */
        private String timeoutPrompt = "未听清，请再说一次";

        /**
         * 确认语前缀
         */
        private String confirmPrefix = "收到取餐码：";

        /**
         * 确认语后缀
         */
        private String confirmSuffix = "，请取餐";
    }
}