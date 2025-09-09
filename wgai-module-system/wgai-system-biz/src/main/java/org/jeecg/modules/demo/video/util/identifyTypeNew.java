package org.jeecg.modules.demo.video.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.jeecg.common.util.RestUtil;
import org.jeecg.modules.demo.tab.entity.PushInfo;
import org.jeecg.modules.demo.tab.entity.TabAiBase;
import org.jeecg.modules.demo.video.entity.TabAiModelNew;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.tab.AIModel.AIModelYolo3;
import org.jeecg.modules.tab.AIModel.NetPush;
import org.jeecg.modules.tab.AIModel.VideoSendReadCfg;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.jeecg.modules.tab.entity.pushEntity;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.jeecg.modules.demo.audio.util.audioSend.getToken;
import static org.jeecg.modules.demo.audio.util.audioSend.postAudioText;
import static org.jeecg.modules.demo.video.util.frame.FrameQualityFilter.printAverageRGB;
import static org.jeecg.modules.tab.AIModel.AIModelYolo3.*;

/**
 * @author wggg
 * @date 2025/5/22 10:31
 */
@Slf4j
public class identifyTypeNew {


    //v3 验证是否通过
    public boolean detectObjects(TabAiSubscriptionNew tabAiSubscriptionNew, Mat image, Net net, List<String> classNames, NetPush netPush) {
        boolean flag = false;
        try {


            // 读取输入图像
            Long a = System.currentTimeMillis();
            // 将图像传递给模型进行目标检测
            Mat blob = Dnn.blobFromImage(image, 1.0 / 255, new Size(416, 416), new Scalar(0), true, false);
            net.setInput(blob);
            // 将图像传递给模型进行目标检测
            List<Mat> result = new ArrayList<>();
            List<String> outBlobNames = net.getUnconnectedOutLayersNames();
            net.forward(result, outBlobNames);

            // 处理检测结果
            float confThreshold = 0.56f;
            List<Rect2d> boundingBoxes = new ArrayList<>();
            List<Float> confidences = new ArrayList<>();
            List<Integer> classIds = new ArrayList<>();
            for (Mat level : result) {
                for (int i = 0; i < level.rows(); ++i) {
                    Mat row = level.row(i);
                    Mat scores = level.row(i).colRange(5, level.cols());
                    Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(scores);
                    Point classIdPoint = minMaxLocResult.maxLoc;
                    double confidence = row.get(0, 4)[0];
                    if (confidence > confThreshold) {
                        //    log.info("classIdPoint"+ classIdPoint);
                        //    log.info("classIdPointx"+ classIdPoint.x);
                        classIds.add((int) classIdPoint.x); //记录标签下标
                        double centerX = row.get(0, 0)[0] * image.cols();
                        double centerY = row.get(0, 1)[0] * image.rows();
                        double width = row.get(0, 2)[0] * image.cols();
                        double height = row.get(0, 3)[0] * image.rows();
                        double left = centerX - width / 2;
                        double top = centerY - height / 2;
                        // 绘制边界框
                        Rect2d rect = new Rect2d(left, top, width, height);
                        boundingBoxes.add(rect);
                        confidences.add((float) confidence);
                    }
                }
            }

            // 执行非最大抑制，消除重复的边界框
            MatOfRect2d boxes = new MatOfRect2d(boundingBoxes.toArray(new Rect2d[0]));
            MatOfFloat confidencesMat = new MatOfFloat();
            confidencesMat.fromList(confidences);
            MatOfInt indices = new MatOfInt();
            Dnn.NMSBoxes(boxes, confidencesMat, confThreshold, 0.4f, indices);

            if (indices.empty()) {
                log.info("类别下标啊" + "未识别到内容");
                return false;
            }

            int[] indicesArray = indices.toArray();
            // 获取保留的边界框
            if(indicesArray.length>50){
                log.error("怎么可能类别太大 20就是上限");
                return false;
            }
            log.info(confidences.size() + "类别下标啊" + indicesArray.length);
            // 在图像上绘制保留的边界框

            for (int idx : indicesArray) {
                // 添加类别标签
                log.info("当前有多少" + confidences.get(idx));
                Integer ab = classIds.get(idx);
                String name = classNames.get(ab);
                TabAiBase aiBase = VideoSendReadCfg.map.get(name);
                if (aiBase == null) {
                    aiBase.setChainName(name);
                }
                if (aiBase.getChainName().equals(netPush.getBeforText())) {
                    flag = true;
                    break;
                }
            }

        } catch (Exception ex) {

            return false;
        }


        return flag;
    }


    //v5 v8 V10 验证是否通过
    public boolean detectObjectsV5(TabAiSubscriptionNew tabAiSubscriptionNew, Mat image, Net net, List<String> classNames, NetPush netPush) {
        boolean flag = false;
        try {


            // 读取输入图像
            Long a = System.currentTimeMillis();
            // 将图像传递给模型进行目标检测
            Mat blob = Dnn.blobFromImage(image, 1.0 / 255, new Size(640, 640), new Scalar(0), true, false);
            net.setInput(blob);
            // 将图像传递给模型进行目标检测
            List<Mat> result = new ArrayList<>();
            List<String> outBlobNames = net.getUnconnectedOutLayersNames();
            net.forward(result, outBlobNames);

            // 处理检测结果
            float confThreshold = 0.42f;
            float nmsThreshold = 0.41f;
            List<Rect2d> boxes2d = new ArrayList<>();
            List<Float> confidences = new ArrayList<>();
            List<Integer> classIds = new ArrayList<>();

            for (Mat output : result) {
                int dims = output.dims();
                int index = (int) output.size(0);
                int rows = (int) output.size(1);
                int cols = (int) output.size(2);
                //
                // Dims: 3, Rows: 25200, Cols: 8 row,Mat [ 1*25200*8*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x28dce2da990, dataAddr=0x28dd0ebc640 ]index:1
                //    log.info("Dims: " + dims + ", Rows: " + rows + ", Cols: " + cols+" row,"+output.row(0)+"index:"+index);
                Mat detectionMat = output.reshape(1, output.size(1));

                for (int i = 0; i < detectionMat.rows(); i++) {
                    Mat detection = detectionMat.row(i);
                    Mat scores = detection.colRange(5, cols);
                    Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                    float confidence = (float) detection.get(0, 4)[0];
                    Point classIdPoint = minMaxResult.maxLoc;

                    if (confidence > confThreshold) {
                        float centerX = (float) detection.get(0, 0)[0];
                        float centerY = (float) detection.get(0, 1)[0];
                        float width = (float) detection.get(0, 2)[0];
                        float height = (float) detection.get(0, 3)[0];

                        float left = centerX - width / 2;
                        float top = centerY - height / 2;

                        classIds.add((int) classIdPoint.x);
                        confidences.add(confidence);
                        boxes2d.add(new Rect2d(left, top, width, height));
                        //  System.out.println("识别到了");
                    }
                }
            }

            if (confidences.size() <= 0||confidences.size()>200) {
                log.warn(tabAiSubscriptionNew.getName() + ":当前未检测到内容");
                return false;
            }
            // 执行非最大抑制，消除重复的边界框
            MatOfRect2d boxes_mat = new MatOfRect2d();
            boxes_mat.fromList(boxes2d);
            log.info("confidences.size{}", confidences.size());
            MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
            MatOfInt indices = new MatOfInt();
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
            if (!boxes_mat.empty() && !confidences_mat.empty()) {
                System.out.println("不为空");
                Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
            }

            int[] indicesArray = indices.toArray();
            // 获取保留的边界框
            if(indicesArray.length>50){
                log.error("怎么可能类别太大 20就是上限");
                return false;
            }
            //     log.info(confidences.size() + "类别下标啊" + indicesArray.length);
            // 在图像上绘制保留的边界框
            int c = 0;

            for (int idx : indicesArray) {
                // 添加类别标签
                Integer ab = classIds.get(idx);
                String name = classNames.get(ab);

                TabAiBase aiBase = VideoSendReadCfg.map.get(name);
                if (aiBase == null) {
                    aiBase = new TabAiBase();
                    aiBase.setChainName(name);

                }
                log.info("当前类别{}验证内容：{}", name, netPush.getBeforText());
                if (aiBase.getChainName().equals(netPush.getBeforText())) {
                    log.warn("验证通过{},{}：", name, netPush.getBeforText());
                    flag = true;
                    break;
                }
            }
        } catch (Exception ex) {
            return false;
        }finally {
            if(flag==false){
                setBeforeImg(image,"before");
            }
        }
        return flag;
    }


    public boolean detectObjectsDify(TabAiSubscriptionNew pushInfo, Mat image, NetPush netPush, RedisTemplate redisTemplate) {

        Net net = netPush.getNet();
        List<String> classNames = netPush.getClaseeNames();
        String uploadpath = netPush.getUploadPath();
        String settingId = netPush.getId();
        TabAiModel tabAiModel = netPush.getTabAiModel();

        // 读取输入图像
        Long a = System.currentTimeMillis();
        // 将图像传递给模型进行目标检测
        Mat blob = Dnn.blobFromImage(image, 1.0 / 255, new Size(416, 416), new Scalar(0), true, false);
        net.setInput(blob);
        // 将图像传递给模型进行目标检测
        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = net.getUnconnectedOutLayersNames();
        net.forward(result, outBlobNames);

        // 处理检测结果
        float confThreshold = 0.56f;
        List<Rect2d> boundingBoxes = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();
        for (Mat level : result) {
            for (int i = 0; i < level.rows(); ++i) {
                Mat row = level.row(i);
                Mat scores = level.row(i).colRange(5, level.cols());
                Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(scores);
                Point classIdPoint = minMaxLocResult.maxLoc;
                double confidence = row.get(0, 4)[0];
                if (confidence > confThreshold) {
                    //    log.info("classIdPoint"+ classIdPoint);
                    //    log.info("classIdPointx"+ classIdPoint.x);
                    classIds.add((int) classIdPoint.x); //记录标签下标
                    double centerX = row.get(0, 0)[0] * image.cols();
                    double centerY = row.get(0, 1)[0] * image.rows();
                    double width = row.get(0, 2)[0] * image.cols();
                    double height = row.get(0, 3)[0] * image.rows();
                    double left = centerX - width / 2;
                    double top = centerY - height / 2;
                    // 绘制边界框
                    Rect2d rect = new Rect2d(left, top, width, height);
                    boundingBoxes.add(rect);
                    confidences.add((float) confidence);
                }
            }
        }

        // 执行非最大抑制，消除重复的边界框
        MatOfRect2d boxes = new MatOfRect2d(boundingBoxes.toArray(new Rect2d[0]));
        MatOfFloat confidencesMat = new MatOfFloat();
        confidencesMat.fromList(confidences);
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes, confidencesMat, confThreshold, 0.4f, indices);

        if (indices.empty()) {
            log.info("类别下标啊" + "未识别到内容");
            return false;
        }

        int[] indicesArray = indices.toArray();
        // 获取保留的边界框
        if(indicesArray.length>50){
            log.error("怎么可能类别太大 20就是上限");
            return false;
        }
        log.info("类别下标啊" + indicesArray.length);
        // 在图像上绘制保留的边界框
        int c = 0;
        for (int idx : indicesArray) {
            // 添加类别标签
            log.info("当前有多少" + confidences.get(idx));
            Integer ab = classIds.get(idx);
            String name = classNames.get(ab);
            TabAiBase aiBase = VideoSendReadCfg.map.get(name);
            if (aiBase == null) {
                aiBase.setChainName(name);
            }
            Rect2d box = boundingBoxes.get(idx);
            Imgproc.rectangle(image, new Point(box.x, box.y), new Point(box.x + box.width, box.y + box.height), CommonColors(c), 2);

            log.info(aiBase.getChainName() + "类别下标" + ab);
            image = AIModelYolo3.addChineseText(image, aiBase.getChainName(), new Point(box.x, box.y - 5), CommonColors(c));
            //  Imgproc.putText(image, classNames.get(ab), new Point(box.x, box.y - 5), Core.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 1);
            c++;
        }

        String savepath = uploadpath + File.separator + "temp" + File.separator;

        String saveName = settingId;//tabAiModel.getId();
        if (StringUtils.isNotBlank(saveName)) {
            saveName = savepath + saveName + ".jpg";
        } else {
            saveName = savepath + System.currentTimeMillis() + ".jpg";
        }
        log.info("存储地址{}", saveName);
        File imageFile = new File(saveName);
        if (imageFile.exists()) {
            imageFile.delete();
        }
        Imgcodecs.imwrite(saveName, image);
        String base64Img = base64Image(saveName);
        //组装参数
        pushEntity push = new pushEntity();
        push.setVideo(pushInfo.getBeginEventTypes());
        push.setType("图片");
        push.setCameraUrl(pushInfo.getBeginEventTypes());
        push.setAlarmPicData(base64Img);
        push.setTime(System.currentTimeMillis() + "");
        push.setModelId(tabAiModel.getId());
        push.setIndexCode(pushInfo.getIndexCode());
        push.setModelName(tabAiModel.getAiName());
        JSONObject ob = null;
        try {
            Long b = System.currentTimeMillis();
//            int endTime= (int) ((b-LastTime)/1000);
//            if(LastTime==0L){
//                log.info("当前时间未赋值："+endTime);
//                LastTime=b;
//            }else if(endTime>=pushInfo.getTime()){
//                LastTime=b;
//                log.info("当前时间频率赋值："+endTime);
//            }else if(endTime<pushInfo.getTime()){
//                log.info("当前时间小于间隔："+endTime);
//                return  false;
//            }
            if (pushInfo.getPushStatic() == 0) {
                ob = RestUtil.post(pushInfo.getEventUrl(), (JSONObject) JSONObject.toJSON(push));
            } else {
                log.info("不推送第三方V3");
            }
            // mqtt
            log.info("消耗时间V3：" + (b - a));
            log.info("返回内容：" + ob);
            //      LastTime=b;


        } catch (Exception ex) {
            log.info("连接失败");
            return false;

        }


        return true;
    }


    public boolean detectObjectsDifyV5(TabAiSubscriptionNew pushInfo, Mat image, NetPush netPush, RedisTemplate redisTemplate) {

        long time = Long.parseLong(pushInfo.getEventNumber());
        Object beforTime = redisTemplate.opsForValue().get(netPush.getId());
        if (beforTime == null) {
            log.info("当前间隔消失可以推送了-间隔时间{}-当前可以推送的是{}", time, pushInfo.getName());
             if(printAverageRGB(image)){
                setErrorImg(image,"huidutu");
                log.info("当前是灰度图片");
                return false;
             };

        } else {
            return false;
        }
        Net net = netPush.getNet();
        List<String> classNames = netPush.getClaseeNames();
        String uploadpath = netPush.getUploadPath();
        String settingId = netPush.getId();
        TabAiModel tabAiModel = netPush.getTabAiModel();
        // 读取输入图像
        Long a = System.currentTimeMillis();
        // 将图像传递给模型进行目标检测
        Mat blob = Dnn.blobFromImage(image, 1.0 / 255, new Size(640, 640), new Scalar(0), true, false);
        net.setInput(blob);
        // 将图像传递给模型进行目标检测
        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = net.getUnconnectedOutLayersNames();
        net.forward(result, outBlobNames);

        // 处理检测结果
        float confThreshold = 0.45f;
        float nmsThreshold = 0.45f;
        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            int index = (int) output.size(0);
            int rows = (int) output.size(1);
            int cols = (int) output.size(2);
            //
            // Dims: 3, Rows: 25200, Cols: 8 row,Mat [ 1*25200*8*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x28dce2da990, dataAddr=0x28dd0ebc640 ]index:1
            //    log.info("Dims: " + dims + ", Rows: " + rows + ", Cols: " + cols+" row,"+output.row(0)+"index:"+index);
            Mat detectionMat = output.reshape(1, output.size(1));

            for (int i = 0; i < detectionMat.rows(); i++) {
                Mat detection = detectionMat.row(i);
                Mat scores = detection.colRange(5, cols);
                Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                float confidence = (float) detection.get(0, 4)[0];
                Point classIdPoint = minMaxResult.maxLoc;

                if (confidence > confThreshold) {
                    float centerX = (float) detection.get(0, 0)[0];
                    float centerY = (float) detection.get(0, 1)[0];
                    float width = (float) detection.get(0, 2)[0];
                    float height = (float) detection.get(0, 3)[0];

                    float left = centerX - width / 2;
                    float top = centerY - height / 2;

                    classIds.add((int) classIdPoint.x);
                    confidences.add(confidence);
                    boxes2d.add(new Rect2d(left, top, width, height));
                    //  System.out.println("识别到了");
                }
            }
        }
        boolean flag;
        if (confidences.size() <= 0||confidences.size()>200) {
            log.warn(pushInfo.getName() + ":当前未检测到内容：{}-{}",netPush.getTabAiModel().getAiName(),confidences.size());
            //setBeforeImg(image,"end");
            return false;
        }
        // 执行非最大抑制，消除重复的边界框
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);
        log.info("confidences.size{}", confidences.size());
        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            System.out.println("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }

        int[] indicesArray = indices.toArray();
        // 获取保留的边界框
        if(indicesArray.length>50){

            setErrorImg(image,"maxIndex");
            log.info("最大消除后最大识别数量50 不可能大于50 "+indicesArray.length);
            return false;
        }
        log.info(confidences.size() + "类别下标啊" + indicesArray.length);
        // 在图像上绘制保留的边界框
        int c = 0;
        String audioText = "";
        Integer warnNumber = 0;
        String warnText = "";
        String warnName = "";
        for (int idx : indicesArray) {
            // 添加类别标签
            Rect2d box = boxes2d.get(idx);
            Integer ab = classIds.get(idx);
            String name = classNames.get(ab);
            float conf = confidences.get(idx);
            double x = box.x;
            double y = box.y;
            double width = box.width * ((double) image.cols() / 640);
            double height = box.height * ((double) image.rows() / 640);
            double xzb = x * ((double) image.cols() / 640);
            double yzb = y * ((double) image.rows() / 640);
            Scalar color=CommonColors(c);
            TabAiBase aiBase = VideoSendReadCfg.map.get(name);
            if (aiBase == null) {
                aiBase = new TabAiBase();
                aiBase.setChainName(name);

            }
            else{
                if(StringUtils.isNotEmpty(aiBase.getSpaceThree())&&aiBase.getSpaceThree().equals("N")){
                    log.warn("【当前不推送：{}】",name);
                    continue;
                }

                color=getColor(aiBase.getRgbColor());
            }
            log.error("【当前推送：{}】",name);

            audioText += aiBase.getRemark() + aiBase.getSpaceOne();
            warnNumber += aiBase.getSpaceTwo() == null ? 1 : aiBase.getSpaceTwo();
            warnText += StringUtils.isEmpty(aiBase.getRemark()) == true ? "" : aiBase.getRemark();
            warnName += aiBase.getChainName() + ",";
            // Imgproc.rectangle(image, new Point(box.x, box.y), new Point(box.x + box.width, box.y + box.height),CommonColors(c), 2);
            Imgproc.rectangle(image,
                    new Point(xzb, yzb),
                    new Point(xzb + width, yzb + height),
                    color, 2);
            //    log.info( "类别下标"+ab);
            image = AIModelYolo3.addChineseText(image, aiBase.getChainName() + conf, new Point(xzb, yzb),color);
            //  Imgproc.putText(image, classNames.get(ab), new Point(box.x, box.y - 5), Core.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 1);
            c++;
        }
        if(warnNumber>0){
            redisTemplate.opsForValue().set(netPush.getId(), System.currentTimeMillis(), time, TimeUnit.SECONDS);
        }else{
            log.error("【当前不推送：{}】");
            return false;
        }

        String savepath = uploadpath + File.separator + "push" + File.separator;
        File file = new File(savepath);
        if (!file.exists()) {
            file.mkdirs();
        }
//        String saveName = settingId;//pushInfo.getId();
//        if (StringUtils.isNotBlank(saveName)) {
//            saveName = savepath + saveName + ".jpg";
//        } else {
        String   saveName = savepath + System.currentTimeMillis() + ".jpg";
//        }

        log.info("存储地址{}", saveName);
        File imageFile = new File(saveName);
        if (imageFile.exists()) {
            imageFile.delete();
        }
        Imgcodecs.imwrite(saveName, image);

        try {
//            while(true){
//                log.info("会一直卡在这吗?");
//            }


//            int endTime= (int) ((b-LastTime)/1000);
//            if(LastTime==0L){
//                log.info("当前时间未赋值："+endTime);
//                LastTime=b;
//            }else if(endTime>=pushInfo.getTime()){
//                LastTime=b;
//                log.info("当前时间频率赋值："+endTime);
//            }else if(endTime<pushInfo.getTime()){
//                log.info("当前时间小于间隔："+endTime);
//                return  false;
//            }
            Long b = System.currentTimeMillis();
            log.info("识别消耗时间V5：" + (b - a) + "ms");
            isOk(pushInfo, netPush, redisTemplate, saveName, tabAiModel, audioText, warnNumber, warnText, warnName, savepath);
//            if(pushInfo.getAudioStatic()==0){
//                log.info("语音播报："+audioText);
//                String token= getToken(pushInfo.getAudioId());
//                postAudioText(token,pushInfo.getAudioId(),audioText);
//            }else{
//                log.info("语音不播报：");
//            }

//            LastTime=b;


        } catch (Exception ex) {
            ex.printStackTrace();
            log.info("连接失败");
            return false;

        }


        return true;
    }

    public void setErrorImg(Mat image,String txt){
        String saveName="D://error";
        try {
            long count = Files.list(Paths.get(saveName)).filter(Files::isRegularFile).count();
            if(count>10000){
                log.info("错误文件大于5000不再存储 以免磁盘满");
                //只删 2000 张最旧的
                new Thread(() -> {
                    try (Stream<Path> paths = Files.list(Paths.get(saveName))) {
                        paths.filter(Files::isRegularFile)
                                .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                                .limit(5000)
                                .forEach(path -> {
                                    try {
                                        Files.deleteIfExists(path);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
                return;
            }
            log.info("错误存储地址{}", saveName);
            File imageFile = new File(saveName);
            if (!imageFile.exists()) {
                imageFile.mkdirs();
            }
            Imgcodecs.imwrite(saveName+"/"+txt+System.currentTimeMillis()+".jpg", image);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }

    public static void setTestImg(Mat image,String txt){
        String saveName="D://error/test";
        try {
            long count = Files.list(Paths.get(saveName)).filter(Files::isRegularFile).count();
            if(count>10000){
                log.info("错误文件大于5000不再存储 以免磁盘满");
                //只删 2000 张最旧的
                new Thread(() -> {
                    try (Stream<Path> paths = Files.list(Paths.get(saveName))) {
                        paths.filter(Files::isRegularFile)
                                .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                                .limit(5000)
                                .forEach(path -> {
                                    try {
                                        Files.deleteIfExists(path);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
                return;
            }
            log.info("错误存储地址{}", saveName);
            File imageFile = new File(saveName);
            if (!imageFile.exists()) {
                imageFile.mkdirs();
            }
            Imgcodecs.imwrite(saveName+"/"+txt+System.currentTimeMillis()+".jpg", image);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }
    //不通过的图片
    public void setBeforeImg(Mat image,String txt){
        String saveName="D://error//"+txt;
        try {
            long count = Files.list(Paths.get(saveName)).filter(Files::isRegularFile).count();
            if(count>10000){
                log.info("不通过前置图片文件大于10000不再存储 以免磁盘满");
                //删除所有重新存储
                new Thread(() -> {
                    try (Stream<Path> paths = Files.list(Paths.get(saveName))) {
                        paths.filter(Files::isRegularFile)
                                .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                                .limit(5000)
                                .forEach(path -> {
                                    try {
                                        Files.deleteIfExists(path);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
                return;
            }
            log.info("错误存储地址{}", saveName);
            File imageFile = new File(saveName);
            if (!imageFile.exists()) {
                imageFile.mkdirs();
            }
            SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyyMMddHHmmss");
            Random random = new Random();
            int number = 10000 + random.nextInt(90000); // 10000 ~ 99999
            String c=simpleDateFormat.format(new Date())+number;
            log.info("当前文件名称{}",c);
            Imgcodecs.imwrite(saveName+"/"+txt+c+".jpg", image);
        }catch (Exception exception){
            exception.printStackTrace();
            log.info("【存储错误】");
        }
    }

    /***
     * 是否推送 /并录像
     * @param pushInfo
     * @param netPush
     * @param redisTemplate
     * @param saveName
     * @param tabAiModel
     * @param audioText
     * @param warnNumber
     * @param warnText
     * @param warnName
     * @param savePath
     * @return
     */
    public boolean isOk(TabAiSubscriptionNew pushInfo, NetPush netPush, RedisTemplate redisTemplate, String saveName, TabAiModel tabAiModel,
                        String audioText,
                        Integer warnNumber,
                        String warnText,
                        String warnName,
                        String savePath
    ) {

        log.warn("model{}-{}", tabAiModel.getAiName(), warnText);
        Thread t = new Thread(() -> {
            try {


                String base64Img = base64Image(saveName);
                //组装参数
                pushEntity push = new pushEntity();
                push.setCameraName(pushInfo.getName());
                push.setType("图片");
                push.setCameraUrl(pushInfo.getBeginEventTypes());
                push.setAlarmPicData(base64Img);
                push.setTime(System.currentTimeMillis() + "");
                push.setModelId(tabAiModel.getAiName());
                push.setIndexCode(pushInfo.getIndexCode());
                push.setModelName(warnName);
                push.setAiNumber(warnNumber);
                push.setModelText(warnText);


                String recordVideo = "";
                if (pushInfo.getPushStatic() == 0) {

                    log.info("[推送第三方结果]：");
                    //是否录像
                    if (pushInfo.getIsRecording() == 0) {

                        log.info("开启录像 录像时常{}", pushInfo.getRecordTime());
                        long recordTime = pushInfo.getRecordTime();
                        recordVideo = RecordVideo(pushInfo.getBeginEventTypes(), savePath, recordTime, netPush.getId());
                        if (StringUtils.isNotEmpty(recordVideo)) {
                            log.error("录像完成:{}", recordVideo);
                            if (pushInfo.getIsBegin() == 0) {
                                //需要分析录像视频逐帧分析
                                log.error("开始分析视频");
                                recordVideo = analysisVideo(recordVideo, netPush, savePath);
                            }
                            String base64Mp4 = base64Image(recordVideo);
                            push.setVideo(base64Mp4);
                        }

                    } else {
                        log.info("未开启录像");
                    }
                    JSONObject ob = RestUtil.post(pushInfo.getEventUrl(), (JSONObject) JSONObject.toJSON(push));

                    log.info("返回内容：" + ob);
                    if (pushInfo.getSaveRecord() != 0 && StringUtils.isNotEmpty(recordVideo)) {  //不保存
                        File imageFile = new File(recordVideo);
                        if (imageFile.exists()) {
                            imageFile.delete();
                        }
                    }
                } else {
                    log.info("不推送第三方结果：");
                }


            } catch (Exception exception) {
                exception.printStackTrace();
                log.error("[推送失败：{}]", pushInfo.getId());
            }
        });
        t.start();

        log.error("推送结束-间隔时间{}-{}", pushInfo.getId());
        return true;
    }


    //开始录像
    public String RecordVideo(String videoUrl, String savePath, long time, String id) {
        String path = savePath + id + "_" + System.currentTimeMillis() + ".mp4";
        try {

            // 创建抓取器
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl);
            grabber.setOption("rtsp_transport", "tcp"); // 避免 UDP 丢包
            grabber.setOption("stimeout", "3000000");   // 设置超时时间（可选）
            grabber.start();

            // 创建录制器
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                    path,
                    grabber.getImageWidth(),
                    grabber.getImageHeight(),
                    grabber.getAudioChannels()
            );
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 25);
            recorder.setVideoBitrate(2000000); // 2Mbps，可调
            recorder.start();

            long startTime = System.currentTimeMillis();
            long recordDuration = time * 1000; // 默认ms *1000 =s

            Frame frame;
            while ((frame = grabber.grab()) != null) {
                recorder.record(frame);
                if (System.currentTimeMillis() - startTime > recordDuration) {
                    break;
                }
            }

            recorder.stop();
            recorder.release();
            grabber.stop();
            grabber.release();
            log.info("录制完成保存为{}", path);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("[出错了检查一下]");
            return "";
        }


        return path;

    }

    //分解录像
    public synchronized String analysisVideo(String recoredPath, NetPush netPush, String savePath) {
        try {
            String saveMp4Path = recoredPath.substring(0, recoredPath.lastIndexOf("."));
            //    Thread tt = new Thread(() -> {
            log.info("当前开始分解录像{}", saveMp4Path);
            File file = new File(saveMp4Path);
            if (!file.exists()) {
                file.mkdirs();
            }
            saveMp4Path = saveMp4Path + "/avi.mp4";
            VideoCapture capture = new VideoCapture(recoredPath, Videoio.CAP_ANY);
            if (!capture.isOpened()) {
                log.info("Error: Unable to open video file.");
            }
            double fps = capture.get(Videoio.CAP_PROP_FPS);
            double widthVideo = capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
            double heightVideo = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
            double frameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
            // 创建 VideoWriter 对象
            VideoWriter writer = new VideoWriter();
            int[] codecs = {
                    VideoWriter.fourcc('X', 'V', 'I', 'D'),
                    VideoWriter.fourcc('M', 'J', 'P', 'G'),
                    VideoWriter.fourcc('a', 'v', 'c', '1'),
            };
            for (int codec : codecs) {
                writer.open(saveMp4Path, codec, fps, new Size(widthVideo, heightVideo), true);
                if (writer.isOpened()) {
                    System.out.println("打开成功，使用 codec：" + codec);
                    break;
                } else {
                    System.out.println("打开失败 codec：" + codec);
                }
            }
            Mat image = new Mat();
            Net net = netPush.getNet();
            List<String> classNames = netPush.getClaseeNames();
            int a = 0;
            while (capture.read(image)) {

                log.info("当前帧:{}",a++);

                // 将图像传递给模型进行目标检测
                Mat blob = Dnn.blobFromImage(image, 1.0 / 255, new Size(640, 640), new Scalar(0), true, false);
                net.setInput(blob);
                // 将图像传递给模型进行目标检测
                List<Mat> result = new ArrayList<>();
                List<String> outBlobNames = net.getUnconnectedOutLayersNames();
                net.forward(result, outBlobNames);

                // 处理检测结果
                float confThreshold = 0.42f;
                float nmsThreshold = 0.41f;
                List<Rect2d> boxes2d = new ArrayList<>();
                List<Float> confidences = new ArrayList<>();
                List<Integer> classIds = new ArrayList<>();

                for (Mat output : result) {
                    int dims = output.dims();
                    int index = (int) output.size(0);
                    int rows = (int) output.size(1);
                    int cols = (int) output.size(2);
                    //
                    // Dims: 3, Rows: 25200, Cols: 8 row,Mat [ 1*25200*8*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x28dce2da990, dataAddr=0x28dd0ebc640 ]index:1
                    //    log.info("Dims: " + dims + ", Rows: " + rows + ", Cols: " + cols+" row,"+output.row(0)+"index:"+index);
                    Mat detectionMat = output.reshape(1, output.size(1));

                    for (int i = 0; i < detectionMat.rows(); i++) {
                        Mat detection = detectionMat.row(i);
                        Mat scores = detection.colRange(5, cols);
                        Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                        float confidence = (float) detection.get(0, 4)[0];
                        Point classIdPoint = minMaxResult.maxLoc;

                        if (confidence > confThreshold) {
                            float centerX = (float) detection.get(0, 0)[0];
                            float centerY = (float) detection.get(0, 1)[0];
                            float width = (float) detection.get(0, 2)[0];
                            float height = (float) detection.get(0, 3)[0];

                            float left = centerX - width / 2;
                            float top = centerY - height / 2;

                            classIds.add((int) classIdPoint.x);
                            confidences.add(confidence);
                            boxes2d.add(new Rect2d(left, top, width, height));
                            //  System.out.println("识别到了");
                        }
                    }
                }

                if (confidences.size() <= 0||confidences.size()>200) {
                    log.warn("录像当前未检测到内容");
                }
                // 执行非最大抑制，消除重复的边界框
                MatOfRect2d boxes_mat = new MatOfRect2d();
                boxes_mat.fromList(boxes2d);
                log.info("confidences.size{}", confidences.size());
                MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
                MatOfInt indices = new MatOfInt();
                Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
                if (!boxes_mat.empty() && !confidences_mat.empty()) {
                    System.out.println("不为空");
                    Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
                }

                int[] indicesArray = indices.toArray();
                // 获取保留的边界框

                log.info(confidences.size() + "类别下标啊" + indicesArray.length);
                if(indicesArray.length>50){
                    log.error("怎么可能类别太大 20就是上限");
                    writer.write(image);
                    continue;
                }
                // 在图像上绘制保留的边界框
                int c = 0;
                for (int idx : indicesArray) {
                    // 添加类别标签
                    Rect2d box = boxes2d.get(idx);
                    Integer ab = classIds.get(idx);
                    String name = classNames.get(ab);
                    float conf = confidences.get(idx);
                    double x = box.x;
                    double y = box.y;
                    double width = box.width * ((double) image.cols() / 640);
                    double height = box.height * ((double) image.rows() / 640);
                    double xzb = x * ((double) image.cols() / 640);
                    double yzb = y * ((double) image.rows() / 640);

                    TabAiBase aiBase = VideoSendReadCfg.map.get(name);
                    if (aiBase == null) {
                        aiBase = new TabAiBase();
                        aiBase.setChainName(name);

                    }
                    // Imgproc.rectangle(image, new Point(box.x, box.y), new Point(box.x + box.width, box.y + box.height),CommonColors(c), 2);
                    Imgproc.rectangle(image,
                            new Point(xzb, yzb),
                            new Point(xzb + width, yzb + height),
                            CommonColors(c), 2);
                    //    log.info( "类别下标"+ab);
                    image = AIModelYolo3.addChineseText(image, aiBase.getChainName() + conf, new Point(xzb, yzb), CommonColors(c));
                    //  Imgproc.putText(image, classNames.get(ab), new Point(box.x, box.y - 5), Core.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 1);
                    c++;
                }

                writer.write(image);

            }

            writer.release();
            capture.release();
            log.error("视频合成完成：");
//            });
//            tt.start();
//            tt.join();
            return saveMp4Path + "/avi.mp4";
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("录制失败");
            return "";
        }


    }


    public Scalar getColor(String color){
        if(StringUtils.isNotEmpty(color)){
            String[] parts = color.split(",");
            log.info("颜色内容{}-数组长度{}",color,parts.length);
            if(parts.length<3){
                return  CommonColors(1);
            }
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());

// 注意：OpenCV 中是 BGR 顺序
            Scalar scalar = new Scalar(b, g, r);
            return  scalar;
        }else{
            return  CommonColors(1);
        }

    }

}
