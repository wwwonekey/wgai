package org.jeecg.modules.demo.video.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jeecg.modules.demo.easy.entity.TabEasyPic;
import org.jeecg.modules.demo.easy.mapper.TabEasyPicMapper;
import org.jeecg.modules.demo.train.entity.TabModelTry;
import org.jeecg.modules.demo.train.entity.TabModelTryOrg;
import org.jeecg.modules.demo.train.mapper.TabModelTryOrgMapper;
import org.jeecg.modules.demo.video.entity.TabAiClickpicSetting;
import org.jeecg.modules.demo.video.mapper.TabAiClickpicSettingMapper;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.jeecg.modules.demo.train.service.impl.TabModelTryServiceImpl.deleteAllFilesInFolder;
import static org.jeecg.modules.demo.train.service.impl.TabModelTryServiceImpl.sendPicNameNo;
import static org.jeecg.modules.tab.AIModel.AIModelYolo3.bufferedImageToMat;

/**
 * 切割图片
 * @author wggg
 * @date 2025/2/25 20:02
 */
@Slf4j
public class CickPic implements Runnable{

    TabAiClickpicSetting tabAiClickpicSetting;
    TabModelTry tabModelTry;
    String uploadPath;

    TabEasyPicMapper tabEasyPicMapper;
    TabModelTryOrgMapper tabModelTryOrgMapper;
    TabAiClickpicSettingMapper tabAiClickpicSettingMapper;
    public CickPic(TabAiClickpicSetting tabAiClickpicSetting,TabModelTry tabModelTry,String uploadPath, TabEasyPicMapper tabEasyPicMapper,   TabModelTryOrgMapper tabModelTryOrgMapper, TabAiClickpicSettingMapper tabAiClickpicSettingMapper){
        this.tabAiClickpicSetting=tabAiClickpicSetting;
        this.tabModelTry=tabModelTry;
        this.uploadPath=uploadPath;
        this.tabEasyPicMapper=tabEasyPicMapper;
        this.tabModelTryOrgMapper=tabModelTryOrgMapper;
        this.tabAiClickpicSettingMapper=tabAiClickpicSettingMapper;
    }


    @Override
    public void run() {
        //保存地址
        String saveUrl="";
        // 放入模型库内
        if(StringUtils.isNotEmpty(tabAiClickpicSetting.getPicModelInster())&&tabAiClickpicSetting.getPicModelInster().equals("Y")){
            saveUrl=uploadPath+ File.separator+tabModelTry.getPicName()+ File.separator;
        }else{ //不放入模型库内
            saveUrl=tabAiClickpicSetting.getSavePath()+ File.separator;
        }
        File file=new File(saveUrl);
        if(!file.exists()){
            file.mkdirs();
        }
        //是否覆盖
        if(StringUtils.isNotEmpty(tabAiClickpicSetting.getIsCover())&&tabAiClickpicSetting.getIsCover().equals("Y")){
            // 覆盖就删除 如果是文件夹，递归调用删除方法
            deleteAllFilesInFolder(file);
        }



        List<String> listurl=sendSavePic( tabAiClickpicSetting.getVideoUrl(), saveUrl,tabAiClickpicSetting.getInterFrameInterval());
        if(StringUtils.isNotEmpty(tabAiClickpicSetting.getPicModelInster())&&tabAiClickpicSetting.getPicModelInster().equals("Y")){
            if(StringUtils.isNotEmpty(tabAiClickpicSetting.getIsCover())&&tabAiClickpicSetting.getIsCover().equals("Y")) {

                tabEasyPicMapper.delete(new LambdaQueryWrapper<TabEasyPic>().eq(TabEasyPic::getModelId,tabAiClickpicSetting.getModelId()));
            }
            for (String url:listurl) {
                String picurl=url.replace(uploadPath,"");
                String picid = UUID.randomUUID().toString().replace("-", "");
                TabModelTryOrg modelTryOrg = new TabModelTryOrg();
                modelTryOrg.setModelId(tabAiClickpicSetting.getModelId());
                modelTryOrg.setPicId(picid);
                TabEasyPic pic = new TabEasyPic();
                pic.setId(picid);
                pic.setModelId(tabAiClickpicSetting.getModelId());
                pic.setPicUrl(tabModelTry.getPicName()+File.separator+url);
                pic.setMarkType("N");
                pic.setPicType("1");
                pic.setPicName(url);
                pic.setRemake(tabModelTry.getPicName());
                tabEasyPicMapper.insert(pic);
                tabModelTryOrgMapper.insert(modelTryOrg);
            }
        }
        tabAiClickpicSetting.setRunState("N");
        tabAiClickpicSettingMapper.updateById(tabAiClickpicSetting);
    }

    public static List<String> sendSavePic(String videoUrl,String saveUrl,int jgz){
        List<String> listurl=new ArrayList<>();
        log.info("开始读取数据");
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl);
        try {
//                grabber.setOption("rtsp_transport", "tcp");  // 强制使用 TCP
//                grabber.setOption("max_delay", "500000");    // 设置最大延迟
//                grabber.setOption("buffer_size", "10485760"); // 设置10MB的缓冲区
            grabber.setOption("stimeout", "10000");    // 3秒超时
            grabber.start(); // 开始读取视频
            log.info("读取成功视频：{}",videoUrl);
        } catch (Exception e) {
           e.printStackTrace();
        }
        Java2DFrameConverter converter = new Java2DFrameConverter();
        try {

            Frame frames;
            int picNumber=0;
            int jiangezhen=0;
            while ((frames = grabber.grab()) != null) {
                // 将Frame转换为OpenCV的Mat对象

                if (frames.image != null) {
                    jiangezhen++;
                    if(jiangezhen==jgz){
                        log.info("读取珍：{},当前图片数：{}",jiangezhen,picNumber);
                        jiangezhen=0;
                    }else{
                        log.info("间隔帧：{}",jiangezhen);
                        continue;
                    }
                    if(picNumber>=10){
                        log.info("读取完成：{}",picNumber);
                        break;
                    }
                    String name=sendPicNameNo(saveUrl);
                    Mat opencvMat = bufferedImageToMat(converter.getBufferedImage(frames));
                    Imgcodecs.imwrite(saveUrl+name,opencvMat);
                    picNumber++;
                    log.info("保存地址{}",saveUrl+name);
                    listurl.add(name);
                }
            }
            grabber.release();
        }catch (Exception exception){
            exception.printStackTrace();
        }
        return listurl;
    }
}
