package org.jeecg.modules.demo.video.service.impl;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.demo.easy.mapper.TabEasyPicMapper;
import org.jeecg.modules.demo.train.entity.TabModelTry;
import org.jeecg.modules.demo.train.mapper.TabModelTryOrgMapper;
import org.jeecg.modules.demo.train.service.ITabModelTryService;
import org.jeecg.modules.demo.video.entity.TabAiClickpicSetting;
import org.jeecg.modules.demo.video.mapper.TabAiClickpicSettingMapper;
import org.jeecg.modules.demo.video.service.ITabAiClickpicSettingService;
import org.jeecg.modules.demo.video.util.CickPic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Description: 采集图片配置
 * @Author: jeecg-boot
 * @Date:   2025-02-25
 * @Version: V1.0
 */
@Service
public class TabAiClickpicSettingServiceImpl extends ServiceImpl<TabAiClickpicSettingMapper, TabAiClickpicSetting> implements ITabAiClickpicSettingService {


    @Autowired
    ITabModelTryService iTabModelTryService;
    @Value(value = "${jeecg.path.upload}")
    String uploadPath;
    @Autowired
    TabEasyPicMapper tabEasyPicMapper;
    @Autowired
    TabModelTryOrgMapper tabModelTryOrgMapper;
    @Autowired
    TabAiClickpicSettingMapper tabAiClickpicSettingMapper;
    @Override
    public Result<?> startUpPic(TabAiClickpicSetting tabAiClickpicSetting) {
        TabModelTry tabModelTry=iTabModelTryService.getById(tabAiClickpicSetting.getModelId());
        tabAiClickpicSetting.setRunState("Y");
        this.updateById(tabAiClickpicSetting);
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(new CickPic(tabAiClickpicSetting,tabModelTry,uploadPath,tabEasyPicMapper,tabModelTryOrgMapper,tabAiClickpicSettingMapper,iTabModelTryService));

        return Result.ok("开始采集");
    }
}
