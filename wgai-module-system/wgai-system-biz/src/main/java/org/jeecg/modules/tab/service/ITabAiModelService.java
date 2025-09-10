package org.jeecg.modules.tab.service;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.tab.entity.TabAiModel;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: AI模型
 * @Author: WGAI
 * @Date:   2024-03-13
 * @Version: V1.0
 */
public interface ITabAiModelService extends IService<TabAiModel> {

    /***
     * 下发模型
     * @param tabAiModel
     * @return
     */
    Result<?> nextModel(TabAiModel tabAiModel);

    Result<?> nextModelFile(TabAiModel tabAiModel);

    /***
     * 接收模型
     * @param tabAiModel
     * @return
     */
    Result<?> receiveModel(TabAiModel tabAiModel);
    public Result<?> receiveModelFile(TabAiModel tabAiModel, MultipartFile aiWeightsFile,
                                  MultipartFile aiConfigFile, MultipartFile aiNameNameFile);
}
