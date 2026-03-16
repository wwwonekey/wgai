package org.jeecg.modules.demo.audio.util;

import lombok.Builder;
import lombok.Data;

/**
 * 语音识别模型配置
 * @author wggg
 * @date 2025/11/28
 */
@Data
@Builder
public class VoiceModelConfig {

    /**
     * 关键词识别模型配置
     */
    private KeywordModelConfig keywordConfig;

    /**
     * 语音识别模型配置
     */
    private RecognitionModelConfig recognitionConfig;

    /**
     * 关键词识别模型配置
     */
    @Data
    @Builder
    public static class KeywordModelConfig {
        private String encoder;
        private String decoder;
        private String joiner;
        private String tokens;
        private String keywordsFile;
    }

    /**
     * 语音识别模型配置
     */
    @Data
    @Builder
    public static class RecognitionModelConfig {
        private String encoder;
        private String decoder;
        private String joiner;
        private String tokens;
        private String ruleFsts;
    }
}