package org.jeecg.modules.demo.audio.service;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.demo.audio.entity.TabAudioDevice;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 播报设备
 * @Author: wggg
 * @Date:   2025-03-07
 * @Version: V1.0
 */
public interface ITabAudioDeviceService extends IService<TabAudioDevice> {



    public Result<?> sendTxtToAudio(String id,String text);
}
