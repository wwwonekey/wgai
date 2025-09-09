package org.jeecg.modules.demo.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.demo.tab.service.impl.TabAiBaseServiceImpl;
import org.jeecg.modules.demo.video.entity.TabAiModelNew;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.demo.video.entity.TabAiVideoSetting;
import org.jeecg.modules.demo.video.mapper.TabAiSubscriptionNewMapper;
import org.jeecg.modules.demo.video.mapper.TabAiVideoSettingMapper;
import org.jeecg.modules.demo.video.service.ITabAiSubscriptionNewService;

import org.jeecg.modules.demo.video.util.*;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.jeecg.modules.tab.mapper.TabAiModelMapper;
import org.opencv.core.Mat;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Description: 多程第三方订阅
 * @Author: jeecg-boot
 * @Date:   2025-05-20
 * @Version: V1.0
 */

@Slf4j
@Service
public class TabAiSubscriptionNewServiceImpl extends ServiceImpl<TabAiSubscriptionNewMapper, TabAiSubscriptionNew> implements ITabAiSubscriptionNewService {

    @Autowired
    TabAiVideoSettingMapper tabAiVideoSettingMapper;

    @Autowired
    TabAiModelMapper tabAiModelMapper;

    @Autowired
    TabAiBaseServiceImpl tabAiBaseService;

    @Autowired
    RedisTemplate redisTemplate;

    @Value("${jeecg.path.upload}")
    private String upLoadPath;
    @Override
    public void startAi(TabAiSubscriptionNew tabAiSubscriptionNew)  {
        try {
            log.info("[当前配置路径]{}",upLoadPath);

            //刷新缓存
            tabAiBaseService.SendRedisBase();
            //写作开关
            redisTemplate.opsForValue().set(tabAiSubscriptionNew.getId()+"newRunPush",true);
            RedisCacheHolder.put(tabAiSubscriptionNew.getId()+"newRunPush",true);
            List<NetPush> NetPushList=new ArrayList<>();
            //查询子项
            List<TabAiVideoSetting> tabAiVideoSettingList=tabAiVideoSettingMapper.selectList(new QueryWrapper<TabAiVideoSetting>().eq("sub_id",tabAiSubscriptionNew.getId()));
            if(tabAiVideoSettingList.size()>0){ //当前不为空
                for (TabAiVideoSetting tabAiVideoSetting:tabAiVideoSettingList) {
                    NetPush allPush=new NetPush();
                    TabAiModelNew tabAiModelNew=new TabAiModelNew();
                    if(tabAiVideoSetting.getIsBefor().equals("0")){ //0为有前置需求
                        List<NetPush>  BeforNetPushList =new ArrayList<>();

                        //----------------前置----------------
                        TabAiModel before=getTabAiModelInfo(tabAiModelMapper.selectById(tabAiVideoSetting.getModelId()),tabAiVideoSetting.getModelTxt());
                        Net beforeNet=getNetModel( tabAiSubscriptionNew, before);
                        NetPush beforePush=new NetPush();
                        beforePush.setId(tabAiVideoSetting.getId());
                        beforePush.setIsBefor(1);
                        beforePush.setBeforText(tabAiVideoSetting.getModelTxt());
                        beforePush.setClaseeNames(Files.readAllLines(Paths.get(before.getAiNameName())));
                        beforePush.setNet(beforeNet);
                        beforePush.setModelType(before.getSpareOne());
                        beforePush.setTabAiModel(before);
                        beforePush.setUploadPath(upLoadPath);
                        BeforNetPushList.add(beforePush);
                        //----------------后置---------------
                        TabAiModel theEnd=getTabAiModelInfo(tabAiModelMapper.selectById(tabAiVideoSetting.getNextMode()),"");
                        Net endNet=getNetModel( tabAiSubscriptionNew, theEnd);
                        NetPush endPush=new NetPush();
                        endPush.setId(tabAiVideoSetting.getId());
                        endPush.setIsBefor(1);
                        endPush.setBeforText("");
                        endPush.setClaseeNames(Files.readAllLines(Paths.get(theEnd.getAiNameName())));
                        endPush.setNet(endNet);
                        endPush.setModelType(theEnd.getSpareOne());
                        endPush.setTabAiModel(theEnd);
                        endPush.setUploadPath(upLoadPath);

                        BeforNetPushList.add(endPush);

                        allPush.setIsBefor(1);
                        allPush.setListNetPush(BeforNetPushList);
                    }else { //没有
                        log.info("[当前配置路径]{}",upLoadPath);
                        TabAiModel tabAiModel=getTabAiModelInfo(tabAiModelMapper.selectById(tabAiVideoSetting.getNextMode()),"");
                        Net endNet=getNetModel( tabAiSubscriptionNew, tabAiModel);
                        allPush.setId(tabAiVideoSetting.getId());
                        allPush.setBeforText("");
                        allPush.setClaseeNames(Files.readAllLines(Paths.get(tabAiModel.getAiNameName())));
                        allPush.setNet(endNet);
                        allPush.setTabAiModel(tabAiModel);
                        allPush.setModelType(tabAiModel.getSpareOne());
                        allPush.setUploadPath(upLoadPath);
                        allPush.setIsBefor(0);


                    }

                    NetPushList.add(allPush);

                }
            }else{
                log.error("[当前未配置不许启动]");
                return;
            }
            //   tabAiSubscriptionNew.setTabAiModelNewList(tabAiModelNewsList);
            tabAiSubscriptionNew.setRunState(1);
            this.updateById(tabAiSubscriptionNew);
            tabAiSubscriptionNew.setNetPushList(NetPushList);
            tabAiSubscriptionNew.setListSetting(tabAiVideoSettingList);

            log.info("[当前开始执行推理]{}",tabAiSubscriptionNew.getName());
            //开始取流
            ExecutorService executor = Executors.newFixedThreadPool(32);
                    //Executors.newCachedThreadPool(4);

            //判断取流方式
            executor.submit(new VideoReadPicNew(tabAiSubscriptionNew,redisTemplate));
          //  executor.submit(new VideoReadPicNewOptimized(tabAiSubscriptionNew,redisTemplate));


         //   executor.submit(new VideoReadPicNewWithDisruptor(tabAiSubscriptionNew,redisTemplate));
        }catch (IOException ex  ){
            ex.printStackTrace();
            log.error("[当前错误读取文件错误]");
        }
    }

    public TabAiModel getTabAiModelInfo(TabAiModel tabAiModel,String name){
        if(tabAiModel.getSpareOne().equals("1")){ //v3
            tabAiModel.setAiConfig(upLoadPath+ File.separator +tabAiModel.getAiConfig());
        }
        tabAiModel.setAiWeights(upLoadPath+ File.separator +tabAiModel.getAiWeights());
        tabAiModel.setAiNameName(upLoadPath+ File.separator +tabAiModel.getAiNameName());
        if(StringUtils.isNotEmpty(name)){
            tabAiModel.setAiName(name);
        }

        return  tabAiModel;
    }

    private static final ConcurrentHashMap<String, Net> GLOBAL_NET_CACHE = new ConcurrentHashMap<>();

    public Net getNetModel(TabAiSubscriptionNew tabAiSubscriptionNew,TabAiModel tabAiModel){

        Net net= GLOBAL_NET_CACHE.get(tabAiModel.getId());
        if(net==null){ //尽量减少消耗
            if (tabAiModel.getSpareOne().equals("1")) {  //v3
                net = Dnn.readNetFromDarknet(tabAiModel.getAiConfig(), tabAiModel.getAiWeights());
            } else if (tabAiModel.getSpareOne().equals("2") || tabAiModel.getSpareOne().equals("3")) { //v5 v8
                net = Dnn.readNetFromONNX( tabAiModel.getAiWeights());
            }

            if (tabAiSubscriptionNew.getEventTypes().equals("1")) { //gpu
                net.setPreferableBackend(Dnn.DNN_BACKEND_CUDA);
                net.setPreferableTarget(Dnn.DNN_TARGET_CUDA);  //gpu推理
                log.info("[DNN推理规则：GPU]");
            } else if(tabAiSubscriptionNew.getEventTypes().equals("2")){
                net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
                net.setPreferableTarget(Dnn.DNN_TARGET_CPU);  //cpu推理
                log.info("[DNN推理规则：CPU]");
            }else if(tabAiSubscriptionNew.getEventTypes().equals("3")) {
                net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
                net.setPreferableTarget(Dnn.DNN_TARGET_OPENCL);  //OpenCL推理
                log.info("[DNN推理规则：OpenCL]");
            }else if(tabAiSubscriptionNew.getEventTypes().equals("4")) {
                //net.setPreferableBackend(Dnn.DNN_BACKEND_INFERENCE_ENGINE);
                net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
                net.setPreferableTarget(Dnn.DNN_TARGET_CPU);
                log.info("[DNN推理规则：OpenVINO]");
            }
            GLOBAL_NET_CACHE.put(tabAiModel.getId(),net);
        }else{
            log.info("【已经存在net直接返回】");
        }
        return net;
    }




    @Override
    public void stopAi(TabAiSubscriptionNew tabAiSubscriptionNew) {
        redisTemplate.opsForValue().set(tabAiSubscriptionNew.getId()+"newRunPush",false);
        RedisCacheHolder.put(tabAiSubscriptionNew.getId()+"newRunPush",false);
        tabAiSubscriptionNew.setRunState(0);
        this.updateById(tabAiSubscriptionNew);

    }

    @Override
    public void setBox(TabAiSubscriptionNew tabAiSubscriptionNew) {

    }

    @Override
    public Result<String> getVideoPic(String  id) {

        String outputPath = upLoadPath+ File.separator ;
        String picName=id+"jpg";
        try {
            TabAiSubscriptionNew tabAiSubscriptionNew1=this.getById(id);
            FFmpegFrameGrabber  grabber= createOptimizedGrabber(tabAiSubscriptionNew1);
            Frame frame;
            while (true){
                frame = grabber.grabImage();
                if(frame==null){
                    continue;
                }
                if(frame.image==null){
                    continue;
                }
                if(frame!=null){
                    Java2DFrameConverter converter = new Java2DFrameConverter();
                    BufferedImage bufferedImage = converter.convert(frame);
                    // 保存为 jpg/png
                    ImageIO.write(bufferedImage, "jpg", new File(outputPath+picName));
                    break;
                }
            }
            grabber.stop();
            grabber.release();
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return Result.OK(picName);
    }



    public FFmpegFrameGrabber createOptimizedGrabber(TabAiSubscriptionNew tabAiSubscriptionNew) throws Exception {

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tabAiSubscriptionNew.getBeginEventTypes());
        // 最重要：完全静默所有FFmpeg日志输出
        grabber.setOption("loglevel", "-8");  // 完全静默，比quiet更彻底

        // GPU设置
        if (tabAiSubscriptionNew.getEventTypes().equals("1")) {
            grabber.setOption("hwaccel", "cuda");
            grabber.setOption("hwaccel_device", "0");
            grabber.setOption("hwaccel_output_format", "cuda");
            log.info("[使用GPU_CUDA加速解码]");
        }

        // 基础连接设置
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("stimeout", "3000000");

        // 解决swscaler警告的核心设置
        // 方案1：强制转换为标准yuv420p格式，避免yuvj420p的警告
        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);

        // 或者方案2：如果需要保持原格式，则完全禁用swscaler警告
        // grabber.setOption("sws_flags", "print_info+accurate_rnd+bitexact");
        // grabber.setPixelFormat(avutil.AV_PIX_FMT_YUVJ420P);

        // 颜色空间设置
        grabber.setOption("colorspace", "bt709");
        grabber.setOption("color_primaries", "bt709");
        grabber.setOption("color_trc", "bt709");
        grabber.setOption("color_range", "tv");  // 使用标准TV范围

        // 性能优化设置
        grabber.setOption("threads", "auto");
        grabber.setOption("preset", "ultrafast");
        grabber.setVideoOption("tune", "zerolatency");
        grabber.setOption("max_delay", "500000");
        grabber.setOption("buffer_size", "1048576");
        //   grabber.setOption("fflags", "nobuffer");
        //   grabber.setOption("flags", "low_delay");
        grabber.setOption("framedrop", "1");
        grabber.setOption("analyzeduration", "5000000");// 5秒分析时间
        grabber.setOption("probesize", "2097152");// 2MB探测大小
        grabber.setOption("rw_timeout", "10000000");   // 读写超时
        // 音频禁用
        grabber.setOption("an", "1");
        // 确保从关键帧开始
        grabber.setOption("flags", "+discardcorrupt+genpts");
        grabber.setOption("flags2", "+fast");           // 快速解码
        grabber.setOption("err_detect", "compliant");   // 严格错误检测
        // 禁用B帧预测（减少依赖损坏）
        grabber.setVideoOption("refs", "1");
        grabber.setVideoOption("bf", "0");

        // 关键帧解码
        grabber.setOption("skip_frame", "nokey");

        // 严格模式
        grabber.setOption("strict", "experimental");

        grabber.start();

        return grabber;
    }
}
