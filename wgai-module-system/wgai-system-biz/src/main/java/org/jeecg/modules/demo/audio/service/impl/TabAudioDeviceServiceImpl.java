package org.jeecg.modules.demo.audio.service.impl;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.demo.audio.entity.TabAudioDevice;
import org.jeecg.modules.demo.audio.mapper.TabAudioDeviceMapper;
import org.jeecg.modules.demo.audio.service.ITabAudioDeviceService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 播报设备
 * @Author: wggg
 * @Date:   2025-03-07
 * @Version: V1.0
 */
@Service
public class TabAudioDeviceServiceImpl extends ServiceImpl<TabAudioDeviceMapper, TabAudioDevice> implements ITabAudioDeviceService {

    @Override
    public Result<?> sendTxtToAudio(String id, String text) {
        TabAudioDevice tabAudioDevice=this.getById(id);
        if(tabAudioDevice.getDeviceFac().equals("1")){//不知名厂家
            //获取token
        }

        return null;
    }
}
