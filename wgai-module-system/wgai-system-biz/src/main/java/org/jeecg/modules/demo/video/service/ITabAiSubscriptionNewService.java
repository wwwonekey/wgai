package org.jeecg.modules.demo.video.service;

import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @Description: 多程第三方订阅
 * @Author: jeecg-boot
 * @Date:   2025-05-20
 * @Version: V1.0
 */
public interface ITabAiSubscriptionNewService extends IService<TabAiSubscriptionNew> {

    public void startAi(TabAiSubscriptionNew tabAiSubscriptionNew) throws IOException;
    public void stopAi(TabAiSubscriptionNew tabAiSubscriptionNew);
}
