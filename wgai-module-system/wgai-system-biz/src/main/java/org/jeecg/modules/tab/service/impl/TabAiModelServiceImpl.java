package org.jeecg.modules.tab.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.RestUtil;
import org.jeecg.modules.demo.tab.entity.TabNextUrl;
import org.jeecg.modules.demo.tab.service.ITabNextUrlService;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.jeecg.modules.tab.mapper.TabAiModelMapper;
import org.jeecg.modules.tab.service.ITabAiModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @Description: AI模型
 * @Author: WGAI
 * @Date:   2024-03-13
 * @Version: V1.0
 */
@Service
public class TabAiModelServiceImpl extends ServiceImpl<TabAiModelMapper, TabAiModel> implements ITabAiModelService {

    @Autowired
    ITabNextUrlService iTabNextUrlService;

    @Value("${jeecg.path.upload}")
    public String uplpadPath;

    @Override
    public Result<?> nextModel(TabAiModel tabAiModel) {
        try {
            List<TabNextUrl> list=iTabNextUrlService.list(new LambdaQueryWrapper<TabNextUrl>().eq(TabNextUrl::getStartFlag,"Y"));
            if(list.size()>0){
                String message="";
                for (TabNextUrl next:list ) {
                    tabAiModel.setAiName(tabAiModel.getAiName()+"-平台下发");
                    if(StringUtils.isNotEmpty(tabAiModel.getAiWeights())){
                        tabAiModel.setSpareThree(tabAiModel.getAiWeights().substring(tabAiModel.getAiWeights().lastIndexOf(".")));
                        tabAiModel.setAiWeights(encodeFileToBase64(new File(uplpadPath+File.separator+tabAiModel.getAiWeights())));

                    }
                    if(StringUtils.isNotEmpty(tabAiModel.getAiConfig())){
                        tabAiModel.setSpareFour(tabAiModel.getAiConfig().substring(tabAiModel.getAiConfig().lastIndexOf(".")));
                        tabAiModel.setAiConfig(encodeFileToBase64(new File(uplpadPath+File.separator+tabAiModel.getAiConfig())));

                    }
                    if(StringUtils.isNotEmpty(tabAiModel.getAiNameName())){
                        tabAiModel.setSpareFive(tabAiModel.getAiNameName().substring(tabAiModel.getAiNameName().lastIndexOf(".")));
                        tabAiModel.setAiNameName(encodeFileToBase64(new File(uplpadPath+File.separator+tabAiModel.getAiNameName())));

                    }
                    JSONObject object= RestUtil.post(next.getNextUrl(), (JSONObject) JSONObject.toJSON(tabAiModel));
                    log.warn("返回内容："+object);
                    message+=object.getString("message")+",";
                }
                return  Result.OK("共下发"+list.size()+"个:"+message);
            }
        }catch (Exception ex){
            ex.printStackTrace();
            return Result.error("下发失败:文件不存在或文件转换失败");
        }
       //
        return Result.error("下发失败:当前下发地址为空");
    }

    @Override
    public Result<?> receiveModel(TabAiModel tabAiModel) {

        // 检查是否包含 MIME 类型前缀

        try {
            File file=new File(uplpadPath+File.separator+"next");
            if(!file.exists()){
                file.mkdirs();
            }
            if(StringUtils.isNotEmpty(tabAiModel.getAiWeights())){
                String url="next"+File.separator+System.currentTimeMillis()+tabAiModel.getSpareThree();
                saveBase64ToFile(tabAiModel.getAiWeights(),new File(uplpadPath+File.separator+url));
                tabAiModel.setAiWeights(url);
            }
            if(StringUtils.isNotEmpty(tabAiModel.getAiConfig())){
                String url="next"+File.separator+System.currentTimeMillis()+tabAiModel.getSpareFour();
                saveBase64ToFile(tabAiModel.getAiConfig(),new File(uplpadPath+File.separator+url));
                tabAiModel.setAiConfig(url);
            }
            if(StringUtils.isNotEmpty(tabAiModel.getAiNameName())){
                String url="next"+File.separator+System.currentTimeMillis()+tabAiModel.getSpareFive();
                saveBase64ToFile(tabAiModel.getAiNameName(),new File(uplpadPath+File.separator+url));
                tabAiModel.setAiNameName(url);
            }
            tabAiModel.setId("");
            tabAiModel.setCreateTime(new Date());
            this.save(tabAiModel);
            return Result.OK("下发成功");
        }catch (Exception ex){
            ex.printStackTrace();
            return Result.error("保存失败");
        }

    }

    private static String encodeFileToBase64(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return Base64.encodeBase64String(fileContent);
    }

    /**
     * 保存文件
     * @param base64Data
     * @param file
     * @throws IOException
     */
    private void saveBase64ToFile(String base64Data, File file) throws IOException {
        // 将 Base64 字符串解码为字节数组
        byte[] fileData = Base64.decodeBase64(base64Data);

        // 创建输出流并写入数据
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(fileData);
        }
    }

}
