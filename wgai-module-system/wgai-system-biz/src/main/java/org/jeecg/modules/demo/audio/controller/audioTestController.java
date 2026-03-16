package org.jeecg.modules.demo.audio.controller;

import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.demo.audio.util.ChineseTTS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wggg
 * @date 2025/11/28 15:52
 *
 *
 */

@Slf4j
@RestController
@RequestMapping("/audio/audioTest")
public class audioTestController {


    /**
     *http://localhost:9998/wgai/audio/audioTest/palyAudio?text=测试一下播放声音是否正常！你好骑手&speed=1&volume=100
     * @param
     * @return
     */
    @AutoLog(value = "播报设备")
    @ApiOperation(value="播报设备")
    //@RequiresPermissions("org.jeecg.modules.demo:tab_audio_device:delete")
    @RequestMapping(value = "/palyAudio")
    public Result<String> palyAudio(String text,Integer speed,Integer volume) {
       log.info("语音播报{}--速度--{}--音量--{}",text,speed,volume);
        new ChineseTTS().speakChinese(text,speed,volume);
        return Result.OK("测试结果!");
    }




   // @Value("${audio.encoder}")
    String encoder;
   // @Value("${audio.decoder}")
    String decoder;
  //  @Value("${audio.joiner}")
    String joiner;
   // @Value("${audio.tokens}")
    String tokens;
  //  @Value("${audio.ruleFsts}")
    String ruleFsts;

    /**
     *http://localhost:9998/wgai/audio/audioTest/audioToText
     * @param
     * @return
     */
    @AutoLog(value = "开启语音转文本")
    @ApiOperation(value="开启语音转文本")
    //@RequiresPermissions("org.jeecg.modules.demo:tab_audio_device:delete")
    @RequestMapping(value = "/audioToText")
    public Result<String> audioToText() {

        Thread trainingThread = new Thread(() -> {
            log.info("开启语音转文本{}-{}-{}-{}-{}", encoder, decoder, joiner, tokens, ruleFsts);
            new ChineseTTS().streamAudioToText(encoder, decoder, joiner, tokens, ruleFsts);
        });
        trainingThread.start();
        return Result.OK("测试结果!");
    }



}
