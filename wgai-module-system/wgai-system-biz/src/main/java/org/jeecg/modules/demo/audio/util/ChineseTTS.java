package org.jeecg.modules.demo.audio.util;

import com.k2fsa.sherpa.onnx.*;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.io.IOException;

/**
 * @author wggg
 * @date 2025/11/28 13:41
 */

@Slf4j
public class ChineseTTS {
    /***
     *
     * @param text
     * @param speed 语速：-10 到 10
     * @param volume 音量：0 到 100
     */
    public  void speakChinese(String text,Integer speed,Integer volume) {
        try {
            text = text.replace("\"", "\\\"");

            // 设置中文语音
            String command = "powershell -Command \"" +
                    "Add-Type -AssemblyName System.Speech; " +
                    "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "$synth.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female, " +
                    "[System.Speech.Synthesis.VoiceAge]::Adult, 0, " +
                    "[System.Globalization.CultureInfo]::GetCultureInfo('zh-CN')); " +
                    "$synth.Rate = "+speed+"; " +  // 语速：-10 到 10
                    "$synth.Volume = "+volume+"; " +  // 音量：0 到 100
                    "$synth.Speak('" + text + "');\"";

            Runtime.getRuntime().exec(command);
            log.info("播报成功{}-{}--{}",text,speed,volume);
        } catch (IOException e) {
            e.printStackTrace();
            log.warn("播报失败{}-{}--{}",text,speed,volume);
        }
    }


    public  String  streamAudioToText(String encoder,String decoder,String joiner,String tokens,String ruleFsts){


        int sampleRate = 16000;

        OnlineTransducerModelConfig transducer =
                OnlineTransducerModelConfig.builder()
                        .setEncoder(encoder)
                        .setDecoder(decoder)
                        .setJoiner(joiner)
                        .build();

        OnlineModelConfig modelConfig =
                OnlineModelConfig.builder()
                        .setTransducer(transducer)
                        .setTokens(tokens)
                        .setNumThreads(1)
                        .setDebug(true)
                        .build();

        OnlineRecognizerConfig config =
                OnlineRecognizerConfig.builder()
                        .setOnlineModelConfig(modelConfig)
                        .setDecodingMethod("greedy_search")
                        .setRuleFsts(ruleFsts)
                        .build();

        OnlineRecognizer recognizer = new OnlineRecognizer(config);
        OnlineStream stream = recognizer.createStream();

        // https://docs.oracle.com/javase/8/docs/api/javax/sound/sampled/AudioFormat.html
        // Linear PCM, 16000Hz, 16-bit, 1 channel, signed, little endian
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);

        // https://docs.oracle.com/javase/8/docs/api/javax/sound/sampled/DataLine.Info.html#Info-java.lang.Class-javax.sound.sampled.AudioFormat-int-
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine targetDataLine;
        try {

            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(format);
            targetDataLine.start();
        } catch (LineUnavailableException e) {
            log.warn("Failed to open target data line: " + e.getMessage());
            recognizer.release();
            stream.release();
            return "error";
        }

        String lastText = "";
        int segmentIndex = 0;

        // You can choose an arbitrary number
        int bufferSize = 1600; // 0.1 seconds for 16000Hz
        byte[] buffer = new byte[bufferSize * 2]; // a short has 2 bytes
        float[] samples = new float[bufferSize];

        System.out.println("Started! Please speak");
        while (targetDataLine.isOpen()) {
            int n = targetDataLine.read(buffer, 0, buffer.length);
            if (n <= 0) {
                System.out.printf("Got %d bytes. Expected %d bytes.\n", n, buffer.length);
                continue;
            }
            for (int i = 0; i != bufferSize; ++i) {
                short low = buffer[2 * i];
                short high = buffer[2 * i + 1];
                int s = (high << 8) + low;
                samples[i] = (float) s / 32768;
            }
            stream.acceptWaveform(samples, sampleRate);

            while (recognizer.isReady(stream)) {
                recognizer.decode(stream);
            }

            String text = recognizer.getResult(stream).getText();
            boolean isEndpoint = recognizer.isEndpoint(stream);
            if (!text.isEmpty() && text != " " && lastText != text) {
                lastText = text;
                System.out.printf("开始输出%d: %s\r", segmentIndex, text);
            }

            if (isEndpoint) {
                if (!text.isEmpty()) {
                    System.out.println();
                    segmentIndex += 1;
                }

                recognizer.reset(stream);
            }
        } // while (targetDataLine.isOpen())

        stream.release();
        recognizer.release();
        return "success";
    }
    public static  String  streamKeyAudioToText(String encoder,String decoder,String joiner,String tokens,String ruleFsts,String keywordsFile){


        OnlineTransducerModelConfig transducer =
                OnlineTransducerModelConfig.builder()
                        .setEncoder(encoder)
                        .setDecoder(decoder)
                        .setJoiner(joiner)
                        .build();

        OnlineModelConfig modelConfig =
                OnlineModelConfig.builder()
                        .setTransducer(transducer)
                        .setTokens(tokens)
                        .setNumThreads(1)
                        .setDebug(true)
                        .build();

        KeywordSpotterConfig config =
                KeywordSpotterConfig.builder()
                        .setOnlineModelConfig(modelConfig)
                        .setKeywordsFile(keywordsFile)
                        .build();

        KeywordSpotter kws = new KeywordSpotter(config);
        OnlineStream stream = kws.createStream();

        int sampleRate = 16000;
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine targetDataLine;
        try {
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(format);
            targetDataLine.start();
        } catch (LineUnavailableException e) {
            System.out.println("Failed to open target data line: " + e.getMessage());

            stream.release();
            return null;
        }
        String lastText = "";
        int segmentIndex = 0;

        int bufferSize = 1600; // 0.1 seconds for 16000Hz
        byte[] buffer = new byte[bufferSize * 2]; // a short has 2 bytes
        float[] samples = new float[bufferSize];
        System.out.println("Started! Please speak");

        boolean keyflag=false; //关键词唤醒
        int keyTime=5000;//唤醒5s内说话 不说话失效

        while (targetDataLine.isOpen()) {
            int n = targetDataLine.read(buffer, 0, buffer.length);
            if (n <= 0) {
                System.out.printf("Got %d bytes. Expected %d bytes.\n", n, buffer.length);
                continue;
            }
            for (int i = 0; i != bufferSize; ++i) {
                short low = buffer[2 * i];
                short high = buffer[2 * i + 1];
                int s = (high << 8) + low;
                samples[i] = (float) s / 32768;
            }
            stream.acceptWaveform(samples, sampleRate);

            while (kws.isReady(stream)) {
                kws.decode(stream);

                String keyword = kws.getResult(stream).getKeyword();
                if (!keyword.isEmpty()) {
                    // Remember to reset the stream right after detecting a keyword
                    System.out.printf("Detected keyword: %s\n", keyword);
                    kws.reset(stream);
                    keyflag=true;
                }
              //  System.out.println("每次都输出,"+keyword);
            }

            if(keyflag){
            //    System.out.println("关键词成功后, 解析说话 5s内没人说话结束监听");

            }


        }
        kws.release();

        return "success";
    }
    public static void main(String[] args) {

//        static String encoder =
//                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\encoder-epoch-12-avg-2-chunk-16-left-64.onnx";
//        static String decoder =
//                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\decoder-epoch-12-avg-2-chunk-16-left-64.onnx";
//        static String joiner =
//                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\joiner-epoch-12-avg-2-chunk-16-left-64.onnx";
//        static String tokens = "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\tokens.txt";
//
//        static String keywordsFile =
//                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\keywords.txt";
//
//
//        static String encoderStream= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\encoder-epoch-99-avg-1.int8.onnx";
//        static String decoderStream= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\decoder-epoch-99-avg-1.onnx";
//        static String joinerStream= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\joiner-epoch-99-avg-1.onnx";
//        static String tokensStream= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\tokens.txt";
//        static String ruleFstsStream= "F:\\JAVAAI\\audio\\sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\\itn_zh_number.fst";
      //  speakChinese("你好骑手，请播报取餐码；例如：美团一一零三 ",3,50);
        String encoder =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\encoder-epoch-12-avg-2-chunk-16-left-64.onnx";
        String decoder =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\decoder-epoch-12-avg-2-chunk-16-left-64.onnx";
        String joiner =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\joiner-epoch-12-avg-2-chunk-16-left-64.onnx";
        String tokens = "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\tokens.txt";

        String keywordsFile =
                "F:\\JAVAAI\\audio\\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01\\keywords.txt";
        streamKeyAudioToText(encoder,decoder,joiner,tokens,null,keywordsFile);
    }
}
