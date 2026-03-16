package org.jeecg.modules.demo.audio.util;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.demo.audio.util.VoiceRecognitionCallback;
import org.jeecg.modules.demo.audio.util.ChineseTTSCallback;

/**
 * 循环模式示例：关键词持续监听，支持多轮对话
 * @author wggg
 * @date 2025/11/28
 */
@Slf4j
public class VoiceRecognitionLoopExample {

    public static void main(String[] args) {
        example2_MultiplePickupCodes();
    }

    /**
     * 示例1：持续监听，支持多轮对话
     * 流程：关键词监听 → 识别 → 超时回到关键词监听 → 循环
     */
    public static void example1_ContinuousListening() {
        ChineseTTSCallback tts = new ChineseTTSCallback();

        String kwEncoder = "F:\\JAVAAI\\audio\\keyword-model\\encoder.onnx";
        String kwDecoder = "F:\\JAVAAI\\audio\\keyword-model\\decoder.onnx";
        String kwJoiner = "F:\\JAVAAI\\audio\\keyword-model\\joiner.onnx";
        String kwTokens = "F:\\JAVAAI\\audio\\keyword-model\\tokens.txt";
        String keywordsFile = "F:\\JAVAAI\\audio\\keyword-model\\keywords.txt";

        String recEncoder = "F:\\JAVAAI\\audio\\asr-model\\encoder.onnx";
        String recDecoder = "F:\\JAVAAI\\audio\\asr-model\\decoder.onnx";
        String recJoiner = "F:\\JAVAAI\\audio\\asr-model\\joiner.onnx";
        String recTokens = "F:\\JAVAAI\\audio\\asr-model\\tokens.txt";

        VoiceRecognitionCallback callback = new VoiceRecognitionCallback() {
            private int roundCount = 0;

            @Override
            public void onKeywordDetected(String keyword) {
                roundCount++;

                System.out.println("  第 " + roundCount + " 轮对话");
                System.out.println("  关键词: " + keyword);

            }

            @Override
            public void onRecognitionStart(int timeoutMs) {
                System.out.println("⏰ " + (timeoutMs / 1000) + "秒内请说话...");
            }

            @Override
            public void onRecognizing(String text, boolean isFinal) {
                if (isFinal) {
                    System.out.println("✓ 句子: " + text);
                } else {
                    System.out.print("  识别中: " + text + "\r");
                }
            }

            @Override
            public void onRecognitionComplete(String finalText) {
                System.out.println("\n✅ 完成: " + finalText);
                System.out.println("↻ 回到关键词监听...\n");
            }

            @Override
            public void onTimeout(String partialText) {
                if (partialText.isEmpty()) {
                    System.out.println("\n⏱ 超时，未识别到内容");
                } else {
                    System.out.println("\n⏱ 超时: " + partialText);
                }
                System.out.println("↻ 回到关键词监听...\n");
            }

            @Override
            public void onError(String error, Exception exception) {
                System.err.println("❌ 错误: " + error);
            }

            @Override
            public void onStateChanged(RecognitionState state) {
                System.out.println("[状态] " + state.getDescription());
            }
        };

        // 启动持续监听（10秒超时）
        tts.startRecognitionAsync(
                kwEncoder, kwDecoder, kwJoiner, kwTokens, keywordsFile,
                recEncoder, recDecoder, recJoiner, recTokens, null,
                10000, callback
        );

        System.out.println("🎤 系统已启动！");
        System.out.println("📌 说出关键词进行多轮对话");
        System.out.println("📌 每次识别后自动回到关键词监听");
        System.out.println("📌 按 Ctrl+C 退出\n");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            tts.shutdown();
        }
    }

    /**
     * 示例2：取餐码多轮识别
     */
    public static void example2_MultiplePickupCodes() {
        ChineseTTSCallback tts = new ChineseTTSCallback();



         String kwEncoder =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\encoder-epoch-12-avg-2-chunk-16-left-64.onnx";
         String kwDecoder =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\decoder-epoch-12-avg-2-chunk-16-left-64.onnx";
         String kwJoiner =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\joiner-epoch-12-avg-2-chunk-16-left-64.onnx";
         String kwTokens = "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\tokens.txt";

         String keywordsFile =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\keywords.txt";


         String recEncoder= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\encoder-epoch-99-avg-1.int8.onnx";
         String recDecoder= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\decoder-epoch-99-avg-1.onnx";
         String recJoiner= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\joiner-epoch-99-avg-1.onnx";
         String recTokens= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\tokens.txt";
         String ruleFstsStream= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\replace.fst";
        VoiceRecognitionCallback callback = new VoiceRecognitionCallback() {
            @Override
            public void onKeywordDetected(String keyword) {
                log.info("骑手唤醒: {}", keyword);
                tts.speakChinese("请播报取餐码如美团1101号,饿了么1102", 2,100,true);
            }

            @Override
            public void onRecognitionStart(int timeoutMs) {
                log.info("等待取餐码（{}秒）...", timeoutMs / 1000);
            }

            @Override
            public void onRecognizing(String text, boolean isFinal) {
                if (isFinal) {
                    log.info("识别到: {}", text);
                }
            }

            @Override
            public void onRecognitionComplete(String finalText) {
                handlePickupCode(finalText, tts);
            }

            @Override
            public void onTimeout(String partialText) {
                if (partialText.isEmpty()) {
                    log.warn("超时，未听清");
                    tts.speakChinese("未听清，请再说一次!", 1,100);
                } else {
                    handlePickupCode(partialText, tts);
                }
            }

            @Override
            public void onError(String error, Exception exception) {
                log.error("错误", exception);
            }

            @Override
            public void onStateChanged(RecognitionState state) {
                log.debug("状态: {}", state.getDescription());
            }
        };

        tts.startRecognitionAsync(
                kwEncoder, kwDecoder, kwJoiner, kwTokens, keywordsFile,
                recEncoder, recDecoder, recJoiner, recTokens, ruleFstsStream,
                10000, callback
        );

        log.info("取餐码识别系统已启动，支持连续多个骑手取餐");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            tts.shutdown();
        }
    }

    /**
     * 处理取餐码
     */
    private static void handlePickupCode(String text, ChineseTTSCallback tts) {
        String code = extractPickupCode(text);
        if (code != null) {
            log.info("取餐码: {}", code);
            tts.speakChinese("收到取餐码：" + formatCode(code) + "，请取餐", 1,100);
            // saveToDatabase(code);
        } else {
            log.warn("未识别到取餐码: {}", text);
            tts.speakChinese("未识别到您的需求，请重新说", 1,100);
        }
    }

    private static String extractPickupCode(String text) {
        text = text.replaceAll("[\\s\\p{Punct}]", "");
        text = convertChineseNumbers(text);
        String code = text.replaceAll("[^0-9]", "");
        return (code.length() >= 3 && code.length() <= 6) ? code : null;
    }

    private static String convertChineseNumbers(String text) {
        return text.replace("零", "0").replace("一", "1")
                .replace("二", "2").replace("三", "3")
                .replace("四", "4").replace("五", "5")
                .replace("六", "6").replace("七", "7")
                .replace("八", "8").replace("九", "9")
                .replace("幺", "1");
    }

    private static String formatCode(String code) {
        String[] nums = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        StringBuilder result = new StringBuilder();
        for (char c : code.toCharArray()) {
            if (Character.isDigit(c)) {
                result.append(nums[c - '0']);
            }
        }
        return result.toString();
    }

    /**
     * 示例3：智能客服场景
     */
    public static void example3_CustomerService() {
        ChineseTTSCallback tts = new ChineseTTSCallback();

        String kwEncoder = "F:\\JAVAAI\\audio\\keyword-model\\encoder.onnx";
        String kwDecoder = "F:\\JAVAAI\\audio\\keyword-model\\decoder.onnx";
        String kwJoiner = "F:\\JAVAAI\\audio\\keyword-model\\joiner.onnx";
        String kwTokens = "F:\\JAVAAI\\audio\\keyword-model\\tokens.txt";
        String keywordsFile = "F:\\JAVAAI\\audio\\keyword-model\\keywords.txt";

        String recEncoder = "F:\\JAVAAI\\audio\\asr-model\\encoder.onnx";
        String recDecoder = "F:\\JAVAAI\\audio\\asr-model\\decoder.onnx";
        String recJoiner = "F:\\JAVAAI\\audio\\asr-model\\joiner.onnx";
        String recTokens = "F:\\JAVAAI\\audio\\asr-model\\tokens.txt";

        VoiceRecognitionCallback callback = new VoiceRecognitionCallback() {
            @Override
            public void onKeywordDetected(String keyword) {
                System.out.println("\n客户: " + keyword);
                tts.speakChinese("您好，请问有什么可以帮您", 1,100);
            }

            @Override
            public void onRecognitionStart(int timeoutMs) {
                System.out.println("等待客户说话...");
            }

            @Override
            public void onRecognizing(String text, boolean isFinal) {
                if (isFinal) {
                    System.out.println("客户说: " + text);
                }
            }

            @Override
            public void onRecognitionComplete(String finalText) {
                System.out.println("完整需求: " + finalText);
                processCustomerRequest(finalText, tts);
            }

            @Override
            public void onTimeout(String partialText) {
                if (partialText.isEmpty()) {
                    tts.speakChinese("没听清，请再次唤醒我", 1,100);
                } else {
                    processCustomerRequest(partialText, tts);
                }
            }

            @Override
            public void onError(String error, Exception exception) {
                System.err.println("系统错误: " + error);
            }

            @Override
            public void onStateChanged(RecognitionState state) {
                // 状态变化
            }
        };

        tts.startRecognitionAsync(
                kwEncoder, kwDecoder, kwJoiner, kwTokens, keywordsFile,
                recEncoder, recDecoder, recJoiner, recTokens, null,
                10000, callback
        );

        System.out.println("智能客服系统已启动");
        System.out.println("支持多轮对话，每次对话后自动等待下一位客户");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            tts.shutdown();
        }
    }

    private static void processCustomerRequest(String request, ChineseTTSCallback tts) {
        if (request.contains("查询") || request.contains("查一下")) {
            tts.speakChinese("正在为您查询，请稍候", 1,100);
        } else if (request.contains("投诉")) {
            tts.speakChinese("非常抱歉，正在为您转接人工客服", 1,100);
        } else if (request.contains("帮助")) {
            tts.speakChinese("好的，我可以帮您查询订单、办理业务等", 1,100);
        } else {
            tts.speakChinese("好的，已记录您的需求", 1,100);
        }
    }
}