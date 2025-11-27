package org.jeecg.modules.demo.szr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.demo.easy.entity.TabEasyPic;
import org.jeecg.modules.demo.easy.service.ITabEasyPicService;
import org.jeecg.modules.demo.szr.entity.TabSzrDz;
import org.jeecg.modules.demo.szr.entity.TabSzrPython;
import org.jeecg.modules.demo.szr.entity.TabSzrVideo;
import org.jeecg.modules.demo.szr.mapper.TabSzrPythonMapper;
import org.jeecg.modules.demo.szr.service.ITabSzrDzService;
import org.jeecg.modules.demo.szr.service.ITabSzrPythonService;
import org.jeecg.modules.demo.szr.service.ITabSzrVideoService;
import org.jeecg.modules.demo.train.entity.TabTrainLog;
import org.jeecg.modules.demo.train.entity.TabTrainPython;
import org.jeecg.modules.demo.train.service.ITabModelTryService;
import org.jeecg.modules.demo.train.service.ITabTrainLogService;
import org.jeecg.modules.demo.train.service.ITabTrainResultService;
import org.jeecg.modules.tab.service.ITabAiModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.jeecg.modules.demo.train.service.impl.TabTrainPythonServiceImpl.executePythonScript;

/**
 * @Description: 数字人训练脚本
 * @Author: wggg
 * @Date:   2025-04-17
 * @Version: V1.0
 */
@Slf4j
@Service
public class TabSzrPythonServiceImpl extends ServiceImpl<TabSzrPythonMapper, TabSzrPython> implements ITabSzrPythonService {



    private ITabModelTryService tabModelTryService;
    @Autowired
    private ITabEasyPicService tabEasyPicService;

    private ITabTrainLogService tabTrainLogService ;
    private ITabTrainResultService tabTrainResultService;
    private ITabAiModelService iTabAiModelService;
    private ITabSzrDzService iTabSzrDzService;

    public static  String yolov5Path="";

    @Autowired
    public TabSzrPythonServiceImpl(ITabSzrDzService tabSzrDzService,ITabAiModelService iTabAiModelService,ITabTrainLogService tabTrainLogService,ITabModelTryService tabModelTryService,ITabTrainResultService tabTrainResultService) {
        this.tabTrainLogService = tabTrainLogService;
        this.tabModelTryService = tabModelTryService;
        this.tabTrainResultService=tabTrainResultService;
        this.iTabAiModelService=iTabAiModelService;
        this.iTabSzrDzService=tabSzrDzService;
    }


    @Autowired
    ITabSzrVideoService iTabSzrVideoService;

    private static String uploadPath;
    @Value("${jeecg.path.upload}")
    public void setUploadPath(String uploadPath) {
        this.uploadPath = uploadPath;
    }
    @Override
    public Result<String> startPy(String id, Integer sort) {
        TabSzrVideo tabSzrVideo=iTabSzrVideoService.getById(id);
        if(tabSzrVideo==null){
            return Result.error("未找到当前需要训练的数字人！");
        }
        QueryWrapper<TabSzrPython> queryWrapper=new QueryWrapper<>();
        if(sort!=null){
            queryWrapper.eq("pysort",sort);
        }
        queryWrapper.orderByAsc("pysort");
        List<TabSzrPython> list=this.list(queryWrapper);
        String audioPath="";
        for (TabSzrPython tabSzrPython:list) {
            switch (tabSzrPython.getPysort()) {
                case 1:
                    audioPath=tabSzrPython.getPyPath();
                    log.info("当前执行第一步训练内容copy文件");
                    copyFile( tabSzrPython,tabSzrVideo);
                    break;
                case 2:

                    log.info("当前执行第二步训练修改配置文件内容");
                    sendYumlCopyFile( tabSzrPython,tabSzrVideo,audioPath);
                    break;
                case 3:
                    log.info("当前执行第三开始训练");
                    starSzrTrain( tabSzrPython,tabSzrVideo);
                    break;
            }
        }


        return Result.OK("开始训练");
    }

    @Override
    public Result<String> testStar(String id, Integer sort) {

        TabSzrVideo tabSzrVideo=iTabSzrVideoService.getById(id);
        if(tabSzrVideo==null){
            return Result.error("未找到当前需要训练的数字人！");
        }
        //测试合成结果输出video
        String picPath=tabSzrVideo.getSzrPath();
        double fps=15.38461538;
        String audioPath="D:\\opt\\upFiles\\1746494451564.wav";
        String savePath="F:\\JAVAAI\\audio\\hecheng.mp4";
        String ffmpegVideo="ffmpeg -y -v warning -r "+fps+" -f image2 -i "+picPath+"/%08d.png -vcodec libx264 -vf format=rgb24,scale=out_color_matrix=bt709,format=yuv420p -crf 18 "+picPath+"/temp.mp4";
        String ffmpegAudio="ffmpeg -y -v warning -i "+audioPath+" -i "+picPath+"/temp.mp4 "+savePath+" ";


        return null;
    }

    public static void sendYumlCopyFile( TabSzrPython tabSzrPython,TabSzrVideo tabSzrVideo,String audioPathStar)  {

        // 原始文件路径
        String videoPathStar=tabSzrVideo.getSzrVideo();



        // 新文件保存路径
        String newFilePath = tabSzrPython.getPyUrl();    // 替换为你要保存的新文件路径
        String keyword = "task_0:";                             // 你要查找的关键字
        String videoPath =" video_path: data/video/"+ videoPathStar.substring(videoPathStar.lastIndexOf("/") + 1);             // 新的行内容
        String audioPath =" audio_path: data/audio/"+ audioPathStar.substring(videoPathStar.lastIndexOf("/") + 1);
        try {
            File file=new File(newFilePath,"wgai.yaml");

            // 读取文件内容
            // 使用BufferedWriter来写入数据
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(keyword);
                writer.newLine(); // 换行
                writer.write(videoPath);
                writer.newLine(); // 换行
                writer.write(audioPath);
                writer.newLine(); // 换行
                System.out.println("文件已成功写入到: " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("文件修改并保存成功！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void copyFile( TabSzrPython tabSzrPython,TabSzrVideo tabSzrVideo)  {
        String videoPath=tabSzrPython.getPyUrl()+ File.separator+"video";
        String audioPath=tabSzrPython.getPyUrl()+File.separator+"audio";
        String videoPathStar=uploadPath+File.separator+tabSzrVideo.getSzrVideo();
        String audioPathStar=uploadPath+File.separator+tabSzrPython.getPyPath();
        log.info("需要拷贝的原始video路径"+videoPathStar);
        log.info("需要拷贝的原始audio路径"+audioPathStar);
        log.info("需要拷贝到的video路径"+videoPath);
        log.info("需要拷贝到的audio路径"+audioPath);
        try {
            Path sourcePicFile = Paths.get(videoPathStar);  // 源文件路径
            Path targetPicDir = Paths.get(videoPath);  // 目标目录路径
            // 使用 Files.copy() 复制文件，覆盖目标文件
            Path targetFilePic = targetPicDir.resolve(sourcePicFile.getFileName());
            Files.copy(sourcePicFile, targetFilePic, StandardCopyOption.REPLACE_EXISTING);
            log.info("【视频源文件】 " + sourcePicFile + " 【拷贝到】 " + targetPicDir);

            Path sourceAudioFile = Paths.get(audioPathStar);  // 源文件路径
            Path targetAudioDir = Paths.get(audioPath);  // 目标目录路径
            // 使用 Files.copy() 复制文件，覆盖目标文件
            Path targetFileAudio = targetAudioDir.resolve(sourceAudioFile.getFileName());
            Files.copy(sourceAudioFile, targetFileAudio, StandardCopyOption.REPLACE_EXISTING);
            log.info("【Audio源文件】 " + sourceAudioFile + " 【拷贝到】 " + targetAudioDir);
        }catch (Exception ex){
            ex.printStackTrace();
            log.error("拷贝失败");
        }


    }
    public  void starSzrTrain(TabSzrPython tabSzrPython,TabSzrVideo tabSzrVideo){
        String path=tabSzrPython.getPyUrl();//进入数字人目录
        Thread trainingThread = new Thread(() -> {
            StringBuffer stringBuffer=new StringBuffer();
            log.info("开始执行脚本：cd {}",path);
            executePythonScript(new String[]{"/bin/bash", "-c","echo 'Hello, World!'"});
            log.info("【当你在日志中看到了hello world 说明你已经成功90%了】");
            String VideoPath="未找到";
            String PicPath="未找到";
            String Fps="未找到";

            try {
                ProcessBuilder processBuilder = new ProcessBuilder(new String[]{"/bin/bash", "-c","cd "+path+" && source myenv/bin/activate && python -m scripts.inference --inference_config configs/inference/wgai.yaml --bbox_shift -7"});
                processBuilder.redirectErrorStream(true);  // 合并错误流到标准输出流

                Process process = processBuilder.start();
                log.info("执行脚本内容");
                // 捕获标准输出流
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String line;

                while ((line = reader.readLine()) != null) {
                    log.info(line);  // 打印日志到控制台
                    stringBuffer.append(line+"\n");
                    if(VideoPath.equals("未找到")) {
                        VideoPath = getLogImportant(line);
                    }
                    if(PicPath.equals("未找到")){
                        PicPath=getPicPath(line);
                    }
                    if(Fps.equals("未找到")) {
                        Fps = getFPS(line);
                    }
                }

                // 等待进程完成
                int exitCode = process.waitFor();
                log.info("Python script executed with exit code: " + exitCode);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }finally {
                log.info("【不管结果如何都要去保存日志并修正训练标记】{}",PicPath);


                savePicList(tabSzrVideo, path+PicPath, Fps);

                log.info("训练结束日志失败也要保存一下看看原因{}",stringBuffer.length());
                TabTrainLog tabTrainLog=new TabTrainLog();
                tabTrainLog.setModelId(tabSzrPython.getId());
                tabTrainLog.setTrainLog(stringBuffer.toString());
                if(stringBuffer.length()<=3999999){//小于2G保存一下
                    tabTrainLog.setCmdText(stringBuffer.toString());
                }else{
                    tabTrainLog.setCmdText("日志文件过于庞大舍弃了");
                }
                tabTrainLog.setCmdPath(VideoPath);
                tabTrainLogService.save(tabTrainLog);

            }
        });

        trainingThread.start();

    }


    public void  savePicList(TabSzrVideo tabSzrVideo,String picPath,String fps){
        String sourceDirPath=picPath;
        String savePath=tabSzrVideo.getId()+"/pic";
        String targetDirPath=uploadPath+File.separator+savePath;
        log.info("原始目录{}",sourceDirPath);
        log.info("拷贝目录{}",targetDirPath);
        List<String>  list= CopyPicPath( sourceDirPath, targetDirPath);
        if(list.size()>0){
            List<TabSzrDz> tabSzrDzsList=new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                TabSzrDz tabSzrDz=new TabSzrDz();
                tabSzrDz.setSzrId(tabSzrVideo.getId());
                tabSzrDz.setSzrTitle("star");
                tabSzrDz.setSzrName(tabSzrVideo.getSzrName());
                tabSzrDz.setSzrFps(Double.valueOf(fps));
                tabSzrDz.setSzrBq("say");
                tabSzrDz.setSzrFile(savePath+"/"+list.get(i));
                tabSzrDzsList.add(tabSzrDz);
            }

            iTabSzrDzService.saveBatch(tabSzrDzsList);

        }

    }

    public List<String>  CopyPicPath(String sourceDirPath,String targetDirPath){
        // 源目录和目标目录路径
//        String sourceDirPath = "C:/example/source";
//        String targetDirPath = "C:/example/target";
        File targetDirPathFile=new File(targetDirPath);
        if(!targetDirPathFile.exists()){
            targetDirPathFile.mkdirs();
        }
        File sourceDir = new File(sourceDirPath);
        File[] files = sourceDir.listFiles();
        List<String> list=new ArrayList<>();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isFile()) {
                    log.info("文件名：" + file.getName());
                    list.add(file.getName());
                    // 拷贝文件
                    try {
                        Path targetPath = Paths.get(targetDirPath, file.getName());
                        Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.error("拷贝文件失败: " + file.getName() + "，原因：" + e.getMessage());
                    }
                }
            }

        } else {
            log.info("目录为空或不存在：" + sourceDirPath);
        }
        return list;
    }

    /***
     * 分解训练日志内容1
     * @param cmd
     */
    public String getLogImportant(String cmd){

        String inputcmd=cmd.trim(); //先去首尾空格
        if(inputcmd.startsWith("ResultSaveTo")){
            log.info("【cmd的结果集数据results】{}",inputcmd);
            return inputcmd;
        }

        return "未找到";
    }




    /***
     * 分解训练日志内容2 获取训练的图片
     * @param cmd
     */
    public static String getPicPath(String cmd){

        String inputcmd=cmd.trim(); //先去首尾空格
        if(inputcmd.startsWith("ResultImgSavePath")){
            log.info("【cmd的结果集数据getPicPath】{}",inputcmd);
            return inputcmd.replace("ResultImgSavePath:.","");
        }

        return "未找到";
    }

    public static void main(String[] args) {
        String a="ResultImgSavePath:./results/along_16dapingjiesha";
        System.out.println(getPicPath(a));
    }


    /***
     * 分解训练日志内容2 获取FPS
     * @param cmd
     */
    public String getFPS(String cmd){

        String inputcmd=cmd.trim(); //先去首尾空格
        if(inputcmd.startsWith("FPS")){
            log.info("【cmd的结果集数据】{}",inputcmd);
            return inputcmd.replace("FPS:","");
        }

        return "未找到";
    }

}
