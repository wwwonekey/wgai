package org.jeecg.modules.tab.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.RestUtil;
import org.jeecg.modules.demo.tab.entity.TabNextUrl;
import org.jeecg.modules.demo.tab.service.ITabNextUrlService;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.jeecg.modules.tab.mapper.TabAiModelMapper;
import org.jeecg.modules.tab.service.ITabAiModelService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

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
@Slf4j
@Service
public class TabAiModelServiceImpl extends ServiceImpl<TabAiModelMapper, TabAiModel> implements ITabAiModelService {

    @Autowired
    ITabNextUrlService iTabNextUrlService;
    @Autowired
    private RestTemplate restTemplate;
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
    public Result<?> nextModelFile(TabAiModel tabAiModel) {
        try {
            List<TabNextUrl> list=iTabNextUrlService.list(new LambdaQueryWrapper<TabNextUrl>().eq(TabNextUrl::getStartFlag,"Y"));
            if(list.size()>0){
                String message="";
                for (TabNextUrl next : list) {
                    try {
                        tabAiModel.setAiName(tabAiModel.getAiName() + "-平台下发");

                        // 使用文件上传方式发送
                        String result = sendModelWithFiles(next.getNextUrl(), tabAiModel);
                        log.info("发送到 {} 成功: {}", next.getNextUrl(), result);
                        message += "发送成功,";

                    } catch (Exception e) {

                        message += "发送失败:" + e.getMessage() + ",";
                    }
                }
                return Result.OK("共下发" + list.size() + "个:" + message);
            }
        }catch (Exception ex){
            ex.printStackTrace();
            return Result.error("下发失败:文件不存在或文件转换失败");
        }
        //
        return Result.error("下发失败:当前下发地址为空");
    }


    /**
     * 使用MultipartFile方式发送模型
     */
    private String sendModelWithFiles(String url, TabAiModel tabAiModel) throws Exception {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 准备模型基础数据（去掉文件路径，因为文件会单独上传）
        TabAiModel modelData = cloneModelWithoutFiles(tabAiModel);
        parts.add("modelData", JSONObject.toJSONString(modelData));

        // 添加文件到请求中
        addFileToRequest(parts, "aiWeights", uplpadPath+File.separator+tabAiModel.getAiWeights());
        addFileToRequest(parts, "aiConfig", uplpadPath+File.separator+tabAiModel.getAiConfig());
        addFileToRequest(parts, "aiNameName", uplpadPath+File.separator+tabAiModel.getAiNameName());

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);

        // 发送请求
        ResponseEntity<String> response = restTemplate.postForEntity(
                url , requestEntity, String.class);

        return response.getBody();
    }

    /**
     * 克隆模型对象但不包含文件路径
     */
    private TabAiModel cloneModelWithoutFiles(TabAiModel original) {
        TabAiModel clone = new TabAiModel();
        BeanUtils.copyProperties(original, clone);
        // 清空文件路径，文件将通过MultipartFile单独传输
        clone.setAiWeights(null);
        clone.setAiConfig(null);
        clone.setAiNameName(null);
        return clone;
    }

    /**
     * 将文件添加到multipart请求中
     */
    private void addFileToRequest(MultiValueMap<String, Object> parts, String paramName, String filePath) {
        if (StringUtils.isNotEmpty(filePath)) {
            try {
                File file = new File(filePath);
                if (file.exists() ) {
                    // 获取文件扩展名
                    String extension = filePath.substring(filePath.lastIndexOf("."));

                    // 创建FileSystemResource
                    FileSystemResource fileResource = new FileSystemResource(file);
                    parts.add(paramName, fileResource);

                    // 同时传递文件扩展名信息
                    parts.add(paramName + "Extension", extension);
                } else {
                    log.warn("文件不存在或过大: {}, size: {}", filePath, file.length());
                }
            } catch (Exception e) {
                log.error("添加文件到请求失败: {}", filePath, e);
            }
        }
    }

    /**
     * 接收模型数据和文件
     */
    public Result<?> receiveModel(TabAiModel tabAiModel, MultipartFile aiWeightsFile,
                                  MultipartFile aiConfigFile, MultipartFile aiNameNameFile) {
        try {
            // 创建目标目录
            File targetDir = new File(uplpadPath + File.separator + "next");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            // 保存上传的文件
            if (aiWeightsFile != null && !aiWeightsFile.isEmpty()) {
                String fileName = saveUploadedFile(aiWeightsFile, "weights");
                tabAiModel.setAiWeights(fileName);
            }

            if (aiConfigFile != null && !aiConfigFile.isEmpty()) {
                String fileName = saveUploadedFile(aiConfigFile, "config");
                tabAiModel.setAiConfig(fileName);
            }

            if (aiNameNameFile != null && !aiNameNameFile.isEmpty()) {
                String fileName = saveUploadedFile(aiNameNameFile, "namename");
                tabAiModel.setAiNameName(fileName);
            }

            // 保存模型信息到数据库
            tabAiModel.setId("");
            tabAiModel.setCreateTime(new Date());
            this.save(tabAiModel);

            return Result.OK("接收成功");

        } catch (Exception ex) {
            log.error("接收模型失败", ex);
            return Result.error("接收失败: " + ex.getMessage());
        }
    }

    /**
     * 保存上传的文件
     */
    private String saveUploadedFile(MultipartFile file, String prefix) throws IOException {
        // 获取原始文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 生成新文件名
        String fileName = "next" + File.separator + prefix + "_" + System.currentTimeMillis() + extension;
        File targetFile = new File(uplpadPath + File.separator + fileName);

        // 确保父目录存在
        targetFile.getParentFile().mkdirs();

        // 保存文件
        file.transferTo(targetFile);

        log.info("文件保存成功: {}, 大小: {} bytes", fileName, file.getSize());
        return fileName;
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

    @Override
    public Result<?> receiveModelFile(TabAiModel tabAiModel, MultipartFile aiWeightsFile, MultipartFile aiConfigFile, MultipartFile aiNameNameFile) {
        try {
            // 创建目标目录
            File targetDir = new File(uplpadPath + File.separator + "next");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            // 保存上传的文件
            if (aiWeightsFile != null && !aiWeightsFile.isEmpty()) {
                String fileName = saveUploadedFile(aiWeightsFile, "weights");
                tabAiModel.setAiWeights(fileName);
            }

            if (aiConfigFile != null && !aiConfigFile.isEmpty()) {
                String fileName = saveUploadedFile(aiConfigFile, "config");
                tabAiModel.setAiConfig(fileName);
            }

            if (aiNameNameFile != null && !aiNameNameFile.isEmpty()) {
                String fileName = saveUploadedFile(aiNameNameFile, "namename");
                tabAiModel.setAiNameName(fileName);
            }
            tabAiModel.setId("");
            // 保存模型信息到数据库
            tabAiModel.setCreateTime(new Date());
            this.save(tabAiModel);

            return Result.OK("接收成功");

        } catch (Exception ex) {
            log.error("接收模型失败", ex);
            return Result.error("接收失败: " + ex.getMessage());
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
