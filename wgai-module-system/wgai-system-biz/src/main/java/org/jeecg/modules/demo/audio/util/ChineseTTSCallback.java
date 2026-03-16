package org.jeecg.modules.demo.audio.util;

import com.k2fsa.sherpa.onnx.*;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.demo.audio.util.VoiceRecognitionCallback;
import org.jeecg.modules.demo.audio.util.VoiceRecognitionCallback.RecognitionState;
import org.jeecg.modules.demo.audio.util.VoiceModelConfig;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 最终完美版：解决TTS播报占用识别时间的问题
 * TTS播报完成后重置超时计时器，确保用户有完整的识别时间
 *
 * @author wggg
 * @date 2025/12/01
 */
@Slf4j
public class ChineseTTSCallback {

    private ExecutorService recognitionExecutor;
    private ExecutorService ttsExecutor;

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread recognitionThread;

    // TTS播放标志
    private volatile boolean isSpeaking = false;

    // 最后一次说话时间（使用AtomicLong，支持从TTS线程更新）
    private AtomicLong lastSpeechTime = new AtomicLong(0);

    public ChineseTTSCallback() {
        this.recognitionExecutor = Executors.newCachedThreadPool();
        this.ttsExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "TTS-Thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 播放提示音
     */
    private void playBeepSound() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
            log.info("播放提示音成功");
        } catch (Exception e) {
            log.warn("播放提示音失败", e);
        }
    }

    /**
     * 中文语音播报（播放完成后重置超时计时器）
     * @param text 播报文本
     * @param speed 语速：-10 到 10
     * @param volume 音量：0 到 100
     * @param resetTimer 是否在播报完成后重置超时计时器
     */
    public void speakChinese(String text, Integer speed, Integer volume, boolean resetTimer) {
        ttsExecutor.submit(() -> {
            try {
                isSpeaking = true;
                log.info("开始播报（暂停识别）: {}", text);

                long startTime = System.currentTimeMillis();

                String escapedText = text.replace("\"", "\\\"");
                String command = "powershell -Command \"" +
                        "Add-Type -AssemblyName System.Speech; " +
                        "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$synth.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female, " +
                        "[System.Speech.Synthesis.VoiceAge]::Adult, 0, " +
                        "[System.Globalization.CultureInfo]::GetCultureInfo('zh-CN')); " +
                        "$synth.Rate = " + speed + "; " +
                        "$synth.Volume = " + volume + "; " +
                        "$synth.Speak('" + escapedText + "');\"";

                Process process = Runtime.getRuntime().exec(command);
                int exitCode = process.waitFor();

                long duration = System.currentTimeMillis() - startTime;

                if (exitCode == 0) {
                    log.info("播报完成: {}，用时: {}ms", text, duration);
                } else {
                    log.warn("播报失败，退出码: {}, 文本: {}", exitCode, text);
                }

                // 等待尾音消散
                Thread.sleep(300);

                // 关键：播报完成后重置超时计时器
                if (resetTimer) {
                    lastSpeechTime.set(System.currentTimeMillis());
                    log.info("重置超时计时器，用户现在有完整的识别时间");
                }

            } catch (IOException e) {
                log.error("播报IO异常: {}", text, e);
            } catch (InterruptedException e) {
                log.warn("播报被中断: {}", text);
                Thread.currentThread().interrupt();
            } finally {
                isSpeaking = false;
                log.info("恢复识别");
            }
        });
    }

    /**
     * 重载方法：默认不重置计时器
     */
    public void speakChinese(String text, Integer speed, Integer volume) {
        speakChinese(text, speed, volume, false);
    }

    /**
     * 启动关键词唤醒+语音识别（循环模式）
     */
    public void startRecognitionAsync(
            String keywordEncoder, String keywordDecoder, String keywordJoiner,
            String keywordTokens, String keywordsFile,
            String recognitionEncoder, String recognitionDecoder, String recognitionJoiner,
            String recognitionTokens, String ruleFsts,
            int recognitionTimeoutMs, VoiceRecognitionCallback callback) {

        if (isRunning.get()) {
            log.warn("识别任务已在运行中");
            callback.onError("识别任务已在运行中", null);
            return;
        }

        recognitionThread = new Thread(() -> {
            try {
                isRunning.set(true);
                doRecognitionLoop(
                        keywordEncoder, keywordDecoder, keywordJoiner, keywordTokens, keywordsFile,
                        recognitionEncoder, recognitionDecoder, recognitionJoiner, recognitionTokens, ruleFsts,
                        recognitionTimeoutMs, callback
                );
            } finally {
                isRunning.set(false);
            }
        });

        recognitionThread.setName("VoiceRecognition-Thread");
        recognitionThread.start();
    }

    /**
     * 使用配置对象启动
     */
    public void startRecognitionAsync(VoiceModelConfig modelConfig,
                                      int recognitionTimeoutMs,
                                      VoiceRecognitionCallback callback) {
        VoiceModelConfig.KeywordModelConfig kwConfig = modelConfig.getKeywordConfig();
        VoiceModelConfig.RecognitionModelConfig recConfig = modelConfig.getRecognitionConfig();

        startRecognitionAsync(
                kwConfig.getEncoder(), kwConfig.getDecoder(), kwConfig.getJoiner(),
                kwConfig.getTokens(), kwConfig.getKeywordsFile(),
                recConfig.getEncoder(), recConfig.getDecoder(), recConfig.getJoiner(),
                recConfig.getTokens(), recConfig.getRuleFsts(),
                recognitionTimeoutMs, callback
        );
    }

    /**
     * 停止识别
     */
    public void stopRecognition() {
        if (isRunning.get() && recognitionThread != null) {
            isRunning.set(false);
            recognitionThread.interrupt();
            log.info("识别任务已停止");
        }
    }

    /**
     * 核心识别逻辑（完美版本）
     */
    private void doRecognitionLoop(
            String keywordEncoder, String keywordDecoder, String keywordJoiner,
            String keywordTokens, String keywordsFile,
            String recognitionEncoder, String recognitionDecoder, String recognitionJoiner,
            String recognitionTokens, String ruleFsts,
            int recognitionTimeoutMs, VoiceRecognitionCallback callback) {

        int sampleRate = 16000;
        TargetDataLine targetDataLine = null;
        OnlineStream kwsStream = null;
        KeywordSpotter kws = null;
        OnlineStream recognizerStream = null;
        OnlineRecognizer recognizer = null;

        try {
            // 1. 创建关键词识别器
            log.info("初始化关键词识别模型...");
            OnlineTransducerModelConfig kwTransducer = OnlineTransducerModelConfig.builder()
                    .setEncoder(keywordEncoder)
                    .setDecoder(keywordDecoder)
                    .setJoiner(keywordJoiner)
                    .build();

            OnlineModelConfig kwModelConfig = OnlineModelConfig.builder()
                    .setTransducer(kwTransducer)
                    .setTokens(keywordTokens)
                    .setNumThreads(1)
                    .setDebug(true)
                    .build();

            KeywordSpotterConfig kwsConfig = KeywordSpotterConfig.builder()
                    .setOnlineModelConfig(kwModelConfig)
                    .setKeywordsFile(keywordsFile)
                    .build();

            kws = new KeywordSpotter(kwsConfig);
            kwsStream = kws.createStream();
            log.info("关键词识别模型初始化完成");

            // 2. 创建语音识别器
            log.info("初始化语音识别模型...");
            OnlineTransducerModelConfig recTransducer = OnlineTransducerModelConfig.builder()
                    .setEncoder(recognitionEncoder)
                    .setDecoder(recognitionDecoder)
                    .setJoiner(recognitionJoiner)
                    .build();

            OnlineModelConfig recModelConfig = OnlineModelConfig.builder()
                    .setTransducer(recTransducer)
                    .setTokens(recognitionTokens)
                    .setNumThreads(1)
                    .setDebug(true)
                    .build();

            OnlineRecognizerConfig recognizerConfig = OnlineRecognizerConfig.builder()
                    .setOnlineModelConfig(recModelConfig)
                    .setDecodingMethod("greedy_search")
                    .setRuleFsts(ruleFsts)
                    .build();

            recognizer = new OnlineRecognizer(recognizerConfig);
            recognizerStream = recognizer.createStream();
            log.info("语音识别模型初始化完成");

            // 3. 设置音频格式
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(format);
            targetDataLine.start();

            // 4. 初始化状态
            RecognitionState state = RecognitionState.WAITING_KEYWORD;
            callback.onStateChanged(state);

            StringBuilder recognizedText = new StringBuilder();
            String lastText = "";
            int segmentIndex = 0;

            int bufferSize = 1600;
            byte[] buffer = new byte[bufferSize * 2];
            float[] samples = new float[bufferSize];

            log.info("系统启动，持续监听关键词...");

            // 5. 主循环
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                int n = targetDataLine.read(buffer, 0, buffer.length);
                if (n <= 0) {
                    continue;
                }

                // 如果正在播放TTS，跳过音频处理
                if (isSpeaking) {
                    continue;
                }

                // 转换音频数据
                for (int i = 0; i < bufferSize; ++i) {
                    short low = buffer[2 * i];
                    short high = buffer[2 * i + 1];
                    int s = (high << 8) + low;
                    samples[i] = (float) s / 32768;
                }

                switch (state) {
                    case WAITING_KEYWORD:
                        kwsStream.acceptWaveform(samples, sampleRate);

                        while (kws.isReady(kwsStream)) {
                            kws.decode(kwsStream);
                            String keyword = kws.getResult(kwsStream).getKeyword();

                            if (!keyword.isEmpty()) {
                                log.info("检测到关键词: {}", keyword);
                                callback.onKeywordDetected(keyword);

                                playBeepSound();

                                // 播报提示语（播报完成后重置计时器）
                             //   speakChinese("请播报取餐码", 3, 50, true);

                                kws.reset(kwsStream);

                                state = RecognitionState.RECOGNIZING;
                                // 初始设置（会被TTS播报完成后重置）
                                lastSpeechTime.set(System.currentTimeMillis());

                                recognizedText.setLength(0);
                                lastText = "";
                                segmentIndex = 0;
                                recognizer.reset(recognizerStream);

                                callback.onStateChanged(state);
                                callback.onRecognitionStart(recognitionTimeoutMs);

                                log.info("切换到语音识别模式，{}秒内请说话...", recognitionTimeoutMs / 1000);
                            }
                        }
                        break;

                    case RECOGNIZING:
                        recognizerStream.acceptWaveform(samples, sampleRate);

                        while (recognizer.isReady(recognizerStream)) {
                            recognizer.decode(recognizerStream);
                        }

                        String text = recognizer.getResult(recognizerStream).getText();
                        boolean isEndpoint = recognizer.isEndpoint(recognizerStream);

                        if (!text.isEmpty() && !text.equals(" ") && !lastText.equals(text)) {
                            lastText = text;
                            lastSpeechTime.set(System.currentTimeMillis());
                            callback.onRecognizing(text, false);
                        }

                        if (isEndpoint) {
                            if (!text.isEmpty()) {
                                log.info("识别到完整句子: {}", text);
                                recognizedText.append(text).append(" ");
                                segmentIndex++;
                                lastSpeechTime.set(System.currentTimeMillis());
                                callback.onRecognizing(text, true);
                            }
                            recognizer.reset(recognizerStream);
                        }

                        // 检查超时
                        long currentTime = System.currentTimeMillis();
                        long elapsed = currentTime - lastSpeechTime.get();

                        if (elapsed > recognitionTimeoutMs) {
                            String finalResult = recognizedText.toString().trim();

                            log.info("语音识别超时（实际等待: {}ms），结果: {}",
                                    elapsed, finalResult.isEmpty() ? "无" : finalResult);

                            if (finalResult.isEmpty()) {
                                callback.onTimeout(finalResult);
                            } else {
                                callback.onRecognitionComplete(finalResult);
                            }

                            state = RecognitionState.WAITING_KEYWORD;
                            callback.onStateChanged(state);
                            log.info("回到关键词监听模式...");
                        }
                        break;
                }
            }

        } catch (Exception e) {
            log.error("语音识别出错", e);
            callback.onError("语音识别出错: " + e.getMessage(), e);
            callback.onStateChanged(RecognitionState.ERROR);
        } finally {
            if (targetDataLine != null && targetDataLine.isOpen()) {
                targetDataLine.close();
            }
            if (kwsStream != null) kwsStream.release();
            if (kws != null) kws.release();
            if (recognizerStream != null) recognizerStream.release();
            if (recognizer != null) recognizer.release();

            log.info("资源已释放");
        }
    }

    /**
     * 检查是否正在播放
     */
    public boolean isSpeaking() {
        return isSpeaking;
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        stopRecognition();
        recognitionExecutor.shutdown();
        ttsExecutor.shutdown();
        log.info("语音识别服务和TTS服务已关闭");
    }
}