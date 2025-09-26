package org.jeecg.modules.demo.video.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.jeecg.common.util.RestUtil;
import org.jeecg.modules.demo.easy.entity.TabEasyPic;
import org.jeecg.modules.demo.tab.entity.PushInfo;
import org.jeecg.modules.demo.tab.entity.TabAiBase;
import org.jeecg.modules.demo.train.util.picXml;
import org.jeecg.modules.demo.video.entity.TabAiModelNew;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.demo.video.entity.TabVideoUtil;
import org.jeecg.modules.demo.video.util.reture.retureBoxInfo;
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
import java.net.InetAddress;
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
    public retureBoxInfo detectObjects(TabAiSubscriptionNew tabAiSubscriptionNew, Mat image, Net net, List<String> classNames, NetPush netPush) {

        retureBoxInfo retureBoxInfo=new retureBoxInfo();
        retureBoxInfo.setFlag(false);
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
                return retureBoxInfo;
            }

            int[] indicesArray = indices.toArray();
            // 获取保留的边界框
            if(indicesArray.length>50){
                log.error("怎么可能类别太大 20就是上限");
                return retureBoxInfo;
            }
            log.info(confidences.size() + "类别下标啊" + indicesArray.length);
            // 在图像上绘制保留的边界框

            List<retureBoxInfo> list=new ArrayList<>();
            for (int idx : indicesArray) {

                retureBoxInfo returnBox2 = new retureBoxInfo();
                // 添加类别标签
                Integer ab = classIds.get(idx);
                String name = classNames.get(ab);
                Rect2d box = boundingBoxes.get(idx);
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
                log.info("当前类别{}验证内容：{}", name, netPush.getBeforText());
                if (aiBase.getChainName().equals(netPush.getBeforText())) {
                    log.warn("验证通过{},{}：", name, netPush.getBeforText());
                    returnBox2.setX(xzb);
                    returnBox2.setY(yzb);
                    returnBox2.setWidth(width);
                    returnBox2.setHeight(height);
                    retureBoxInfo.setFlag(true);
                    list.add(returnBox2);
                }
            }

        } catch (Exception ex) {

            return retureBoxInfo;
        }


        return retureBoxInfo;
    }

    /***
     * 自动标注
     * @param image
     * @param net
     * @param classNames
     * @return
     */
    public List<picXml>  AutoDetectObjectsV5(TabEasyPic pic, Mat image, Net net, List<String> classNames) {

        List<picXml> picXmls=new ArrayList<>();
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

                return null;
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
                return picXmls;
            }
            //     log.info(confidences.size() + "类别下标啊" + indicesArray.length);
            // 在图像上绘制保留的边界框
            int c = 0;

            for (int idx : indicesArray) {
                // 添加类别标签
                picXml picXml=new picXml();
                Rect2d box = boxes2d.get(idx);
                Integer ab = classIds.get(idx);
                String name = classNames.get(ab);
                float conf = confidences.get(idx);
                double x = box.x;
                double y = box.y;
                double width = box.width * ((double) 700 / 640);
                double height = box.height * ((double) 700 / 640);
                double xzb = x * ((double) 700 / 640);
                double yzb = y * ((double) 700 / 640);
                if(xzb<0){
                    xzb=0;
                }
                if(yzb<0){
                    yzb=0;
                }
                picXml.setName(name);
                picXml.setPicId(pic.getId());
                picXml.setXmin(xzb+"");
                picXml.setXmax(xzb+width+"");
                picXml.setYmin(yzb+"");
                picXml.setYmax(yzb+height+"");
                picXml.setModelId(pic.getModelId());
                picXml.setCanvaswidth(width);
                picXml.setCanvasheight(height);
                picXml.setYwidth(Double.parseDouble(String.valueOf(image.width())));
                picXml.setYheight(Double.parseDouble(String.valueOf(image.width())));
                picXmls.add(picXml);
            }
        } catch (Exception ex) {
            return picXmls;
        }
        return picXmls;
    }

    //v5 v8 V10 验证是否通过
    public retureBoxInfo detectObjectsV5(TabAiSubscriptionNew tabAiSubscriptionNew, Mat image, Net net, List<String> classNames, NetPush netPush) {
        retureBoxInfo returnBox=new retureBoxInfo();
        returnBox.setFlag(false);

        try {


            // 读取输入图像
            Long a = System.currentTimeMillis();

            Mat processedImage = letterboxResize(image, 640, 640);
            // 将图像传递给模型进行目标检测
            Mat blob = Dnn.blobFromImage(processedImage, 1.0 / 255, new Size(640, 640), new Scalar(0), true, false);
            net.setInput(blob);
            // 将图像传递给模型进行目标检测
            List<Mat> result = new ArrayList<>();
            List<String> outBlobNames = net.getUnconnectedOutLayersNames();
            net.forward(result, outBlobNames);

            // 处理检测结果
            float confThreshold = 0.35f;
            float nmsThreshold = 0.3f;
            List<Rect2d> boxes2d = new ArrayList<>();
            List<Float> confidences = new ArrayList<>();
            List<Integer> classIds = new ArrayList<>();

            for (Mat output : result) {
                int dims = output.dims();
                long dim0 = output.size(0);
                long dim1 = output.size(1);
                long dim2 = output.size(2);

                System.out.println("输出维度: [" + dim0 + ", " + dim1 + ", " + dim2 + "]");

                Mat detectionMat;
                int rows, cols;

                // 判断是YOLOv11还是YOLOv5格式
                if (dims == 3 && dim1 < 100 && dim2 > 1000) {
                    // YOLOv11格式 [1, 84, 8400]
                    log.info("检测到YOLOv11格式");
                    Mat reshaped = output.reshape(1, (int) dim1);
                    Mat transposed = new Mat();
                    Core.transpose(reshaped, transposed);
                    detectionMat = transposed;
                    rows = (int) dim2;
                    cols = (int) dim1;
                } else {
                    // YOLOv5格式
                    log.info("检测到YOLOv5格式");
                    detectionMat = output.reshape(1, (int) output.size(1));
                    rows = detectionMat.rows();
                    cols = detectionMat.cols();
                }

                log.info("处理后矩阵: " + rows + "x" + cols);

                for (int i = 0; i < rows; i++) {
                    Mat detection = detectionMat.row(i);

                    float confidence;
                    Mat scores;
                    Point classIdPoint;

                    if (cols == 84) {
                        // ==========关键修正3: YOLOv11置信度计算==========
                        // YOLOv11格式：[x, y, w, h, class0_conf, class1_conf, ...]
                        scores = detection.colRange(4, cols);
                        Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                        confidence = (float) minMaxResult.maxVal;
                        classIdPoint = minMaxResult.maxLoc;
                    } else {
                        // YOLOv5格式
                        confidence = (float) detection.get(0, 4)[0];
                        scores = detection.colRange(5, cols);
                        Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                        classIdPoint = minMaxResult.maxLoc;
                        confidence *= (float) minMaxResult.maxVal;
                    }

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

//                        log.info("检测到目标: 类别={}, 置信度={}, 坐标=({},{},{},{})",
//                                (int)classIdPoint.x, confidence, left, top, width, height);
                    }
                }
            }


            if (confidences.size() <= 0||confidences.size()>200) {
                log.warn(tabAiSubscriptionNew.getName() + ":当前未检测到内容");
                return returnBox;
            }
            // 执行非最大抑制，消除重复的边界框
            MatOfRect2d boxes_mat = new MatOfRect2d();
            boxes_mat.fromList(boxes2d);
            log.info("confidences.size{}", confidences.size());
            MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
            MatOfInt indices = new MatOfInt();
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
            if (!boxes_mat.empty() && !confidences_mat.empty()) {
                log.info("不为空");
                Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
            }

            int[] indicesArray = indices.toArray();
            // 获取保留的边界框
            if(indicesArray.length>50){
                log.error("怎么可能类别太大 20就是上限");
                return returnBox;
            }
            //     log.info(confidences.size() + "类别下标啊" + indicesArray.length);

            // 计算letterbox的缩放参数
            double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
            double dx = (640 - image.cols() * scale) / 2;
            double dy = (640 - image.rows() * scale) / 2;
            List<retureBoxInfo> list=new ArrayList<>();
            for (int idx : indicesArray) {

                retureBoxInfo returnBox2=new retureBoxInfo();
                // 添加类别标签
                Integer ab = classIds.get(idx);
                String name = classNames.get(ab);
                Rect2d box = boxes2d.get(idx);
                // 还原到原图坐标
                double xzb = (box.x - dx) / scale;
                double yzb = (box.y - dy) / scale;
                double width = box.width / scale;
                double height = box.height / scale;

                // 确保坐标在图像范围内
                xzb = Math.max(0, Math.min(xzb, image.cols() - 1));
                yzb = Math.max(0, Math.min(yzb, image.rows() - 1));
                width = Math.min(width, image.cols() - xzb);
                height = Math.min(height, image.rows() - yzb);

                TabAiBase aiBase = VideoSendReadCfg.map.get(name);
                if (aiBase == null) {
                    aiBase = new TabAiBase();
                    aiBase.setChainName(name);

                }
                log.info("当前类别{}验证内容：{}", name, netPush.getBeforText());
                if (aiBase.getChainName().equals(netPush.getBeforText())) {
                    log.warn("验证通过{},{}：", name, netPush.getBeforText());
                    returnBox2.setX(xzb);
                    returnBox2.setY(yzb);
                    returnBox2.setWidth(width);
                    returnBox2.setHeight(height);
                    returnBox.setFlag(true);
                    list.add(returnBox2);
                }

            }
            returnBox.setInfoList(list);

        } catch (Exception ex) {
            return returnBox;
        }finally {
            if(returnBox.isFlag()==false){
                setBeforeImg(image,"before");
            }
        }
        return returnBox;
    }


    public boolean detectObjectsDify(TabAiSubscriptionNew pushInfo, Mat image, NetPush netPush, RedisTemplate redisTemplate,List<retureBoxInfo> retureBoxInfos) {

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


    public boolean detectObjectsDifyV11(TabAiSubscriptionNew pushInfo, Mat image, NetPush netPush, RedisTemplate redisTemplate,List<retureBoxInfo> retureBoxInfos) {

        long time = Long.parseLong(pushInfo.getEventNumber());
        Object beforTime = redisTemplate.opsForValue().get(netPush.getId());
        if (beforTime == null) {
            log.info("当前间隔消失可以推送了-间隔时间{}-当前可以推送的是{},当前数据：{}", time, pushInfo.getName(),JSON.toJSONString(retureBoxInfos));
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
        Mat blob = Dnn.blobFromImage(image, 1.0 / 255.0, new Size(640, 640), new Scalar(0, 0, 0), true, false, CvType.CV_32F);
        net.setInput(blob);
        // 将图像传递给模型进行目标检测
        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = net.getUnconnectedOutLayersNames();
        net.forward(result, outBlobNames);

        // 处理检测结果
        float confThreshold = 0.45f;
        float nmsThreshold = 0.4f;
        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            long dim0 = output.size(0);
            long dim1 = output.size(1);
            long dim2 = output.size(2);

            log.info("输出维度: [" + dim0 + ", " + dim1 + ", " + dim2 + "]");

            Mat detectionMat;
            int rows, cols;

            // 判断是YOLOv11还是YOLOv5格式
            if (dims == 3 && dim1 < 100 && dim2 > 1000) {
                // YOLOv11格式 [1, 84, 8400]
                log.info("检测到YOLOv11格式");
                Mat reshaped = output.reshape(1, (int) dim1);
                Mat transposed = new Mat();
                Core.transpose(reshaped, transposed);
                detectionMat = transposed;
                rows = (int) dim2;
                cols = (int) dim1;
            } else {
                // YOLOv5格式
                log.info("检测到YOLOv5格式");
                detectionMat = output.reshape(1, (int) output.size(1));
                rows = detectionMat.rows();
                cols = detectionMat.cols();
            }

            log.info("处理后矩阵: " + rows + "x" + cols);

            for (int i = 0; i < rows; i++) {
                Mat detection = detectionMat.row(i);

                float confidence;
                Mat scores;
                Point classIdPoint;

                if (cols == 84) {
                    // ==========关键修正3: YOLOv11置信度计算==========
                    // YOLOv11格式：[x, y, w, h, class0_conf, class1_conf, ...]
                    scores = detection.colRange(4, cols);
                    Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                    confidence = (float) minMaxResult.maxVal;
                    classIdPoint = minMaxResult.maxLoc;
                } else {
                    // YOLOv5 v8格式
                    confidence = (float) detection.get(0, 4)[0];
                    scores = detection.colRange(5, cols);
                    Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                    classIdPoint = minMaxResult.maxLoc;
                    confidence *= (float) minMaxResult.maxVal;
                }

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

                    log.info("检测到目标: 类别={}, 置信度={}, 坐标=({},{},{},{})",
                            (int)classIdPoint.x, confidence, left, top, width, height);
                }
            }
        }

        boolean flag;
        String savepath = uploadpath + File.separator + "push" + File.separator;
        if (confidences.size() <= 0||confidences.size()>200) {
            log.warn(pushInfo.getName() + ":当前未检测到内容：{}-{}",netPush.getTabAiModel().getAiName(),confidences.size());
            //setBeforeImg(image,"end");
            if(netPush.getWarinngMethod()==1){//0 是识别到报警  1 是未识别到报警
                log.info("[未识别到推送数据]:{}",netPush.getIsFollow());
                String   saveName = savepath + System.currentTimeMillis() + ".jpg";
                Imgcodecs.imwrite(saveName, image);
                isOk(pushInfo,netPush,redisTemplate,saveName,tabAiModel,netPush.getNoDifText(),1,netPush.getNoDifText(),netPush.getNoDifText(),savepath);
            }
            return false;
        }
        log.info("NMS前检测框数量: " + boxes2d.size());
        // 执行非最大抑制，消除重复的边界框
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);


        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            log.info("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }

        int[] indicesArray = indices.toArray();

        // 获取保留的边界框
        if(indicesArray.length>50){
            setErrorImg(image,"maxIndex");
            log.warn("最大消除后最大识别数量50 不可能大于50 "+indicesArray.length);
            return false;
        }
        log.info(confidences.size() + "类别下标啊-NMS前检测框数量" + indicesArray.length);
        // 在图像上绘制保留的边界框
        int c = 0;
        String audioText = "";
        Integer warnNumber = 0;
        String warnText = "";
        String warnName = "";
        //保存识别前的图片
        setBeforeImg(image,"end");
        // 计算letterbox的缩放参数
        double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
        double dx = (640 - image.cols() * scale) / 2;
        double dy = (640 - image.rows() * scale) / 2;

        for (int idx : indicesArray) {
            // 添加类别标签
            Rect2d box = boxes2d.get(idx);
            Integer ab = classIds.get(idx);
            String name = classNames.get(ab);
            float conf = confidences.get(idx);

            // 还原到原图坐标
            double xzb = (box.x - dx) / scale;
            double yzb = (box.y - dy) / scale;
            double width = box.width / scale;
            double height = box.height / scale;
            // 确保坐标在图像范围内
            xzb = Math.max(0, Math.min(xzb, image.cols() - 1));
            yzb = Math.max(0, Math.min(yzb, image.rows() - 1));
            width = Math.min(width, image.cols() - xzb);
            height = Math.min(height, image.rows() - yzb);

            if(netPush.getIsFollow()==0){
                log.info("[只要识别前置模型内的内容x:{},y:{},JSON:{}] ",xzb,yzb, JSON.toJSONString(retureBoxInfos));
                boolean followFlag= retureBoxInfo.getLocalhost(retureBoxInfos,xzb,yzb,netPush.getFollowPosition());
                if(!followFlag){
                    log.info("[不在范围内到直接跳过]");
                    continue;
                }else{
                    log.info("[在范围内开始推送： ]");
                }
            }

            if(pushInfo.getIsBy()==0){//开启了 0开启 1未开启
                boolean isPointFlag= isPointInArea(box.x, box.y,  Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasStartx()), Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasStarty()),  Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasWidth()), Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasHeight()));
                log.info("[是否在区域内]:"+isPointFlag);
                if(isPointFlag){
                    log.info("[在区域内]");
                }else{
                    log.info("[不在区域内]");
                    continue;
                }
            }

            Scalar color=CommonColors(c);
            TabAiBase aiBase = VideoSendReadCfg.map.get(name);
            if (aiBase == null) {
                aiBase = new TabAiBase();
                aiBase.setChainName(name);

            }else{
                if(StringUtils.isNotEmpty(aiBase.getSpaceThree())&&aiBase.getSpaceThree().equals("N")){
                    log.warn("【当前不推送：{}】",name);
                    continue;
                }

                color=getColor(aiBase.getRgbColor());
            }
            log.error("【当前推送：{}】",name);


            audioText += aiBase.getRemark() + aiBase.getSpaceOne();
            warnNumber += aiBase.getSpaceTwo() == null ? 1 : aiBase.getSpaceTwo();
            warnText = setNmsName(warnText,StringUtils.isEmpty(aiBase.getRemark()) == true ? aiBase.getChainName() : aiBase.getRemark());
            warnName = setNmsName(warnName,aiBase.getChainName());
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
            log.info("识别消耗时间V5-v11：" + (b - a) + "ms");
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

    public boolean detectObjectsDifyV5(TabAiSubscriptionNew pushInfo, Mat image, NetPush netPush, RedisTemplate redisTemplate,List<retureBoxInfo> retureBoxInfos) {

        long time = Long.parseLong(pushInfo.getEventNumber());
        Object beforTime = redisTemplate.opsForValue().get(netPush.getId());
        if (beforTime == null) {
            log.info("当前间隔消失可以推送了-间隔时间{}-当前可以推送的是{},当前数据：{}", time, pushInfo.getName(),JSON.toJSONString(retureBoxInfos));
//             if(printAverageRGB(image)){
//                setErrorImg(image,"huidutu");
//                log.info("当前是灰度图片");
//                return false;
//             };

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
        // ==========关键修正1: 使用letterbox预处理 以前不必须纠正 后续v8 v10 v11需要纠正==========
        Mat processedImage = letterboxResize(image, 640, 640);

        // 创建blob - 注意参数调整
        Mat blob = Dnn.blobFromImage(processedImage, 1.0 / 255.0, new Size(640, 640), new Scalar(0, 0, 0), true, false, CvType.CV_32F);
        net.setInput(blob);;
        // 将图像传递给模型进行目标检测
        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = net.getUnconnectedOutLayersNames();
        net.forward(result, outBlobNames);

        // 处理检测结果
        float confThreshold = 0.4f;
        float nmsThreshold = 0.4f;
        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            long dim0 = output.size(0);
            long dim1 = output.size(1);
            long dim2 = output.size(2);

            log.info("输出维度: [" + dim0 + ", " + dim1 + ", " + dim2 + "]");

            Mat detectionMat;
            int rows, cols;

            // 判断是YOLOv11还是YOLOv5格式
            if (dims == 3 && dim1 < 100 && dim2 > 1000) {
                // YOLOv11格式 [1, 84, 8400]
                log.info("检测到YOLOv11格式");
                Mat reshaped = output.reshape(1, (int) dim1);
                Mat transposed = new Mat();
                Core.transpose(reshaped, transposed);
                detectionMat = transposed;
                rows = (int) dim2;
                cols = (int) dim1;
            } else {
                // YOLOv5格式
                log.info("检测到YOLOv5格式");
                detectionMat = output.reshape(1, (int) output.size(1));
                rows = detectionMat.rows();
                cols = detectionMat.cols();
            }

            log.info("处理后矩阵: " + rows + "x" + cols);

            for (int i = 0; i < rows; i++) {
                Mat detection = detectionMat.row(i);

                float confidence;
                Mat scores;
                Point classIdPoint;

                if (cols == 84) {
                    // ==========关键修正3: YOLOv11置信度计算==========
                    // YOLOv11格式：[x, y, w, h, class0_conf, class1_conf, ...]
                    scores = detection.colRange(4, cols);
                    Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                    confidence = (float) minMaxResult.maxVal;
                    classIdPoint = minMaxResult.maxLoc;
                } else {
                    // YOLOv5 v8格式
                    confidence = (float) detection.get(0, 4)[0];
                    scores = detection.colRange(5, cols);
                    Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                    classIdPoint = minMaxResult.maxLoc;
                    confidence *= (float) minMaxResult.maxVal;
                }

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

//                    log.info("检测到目标: 类别={}, 置信度={}, 坐标=({},{},{},{})",
//                            (int)classIdPoint.x, confidence, left, top, width, height);
                }
            }
        }
        boolean flag;
        String savepath = uploadpath + File.separator + "push" + File.separator;
        if (confidences.size() <= 0||confidences.size()>200) {
            log.warn(pushInfo.getName() + ":当前未检测到内容：{}-{}",netPush.getTabAiModel().getAiName(),confidences.size());
            //setBeforeImg(image,"end");

            if(netPush.getWarinngMethod()==1){//0 是识别到报警  1 是未识别到报警
                log.info("[未识别到推送数据]:{}",netPush.getIsFollow());
                String   saveName = savepath + System.currentTimeMillis() + ".jpg";
                Imgcodecs.imwrite(saveName, image);
                isOk(pushInfo,netPush,redisTemplate,saveName,tabAiModel,netPush.getNoDifText(),1,netPush.getNoDifText(),netPush.getNoDifText(),savepath);
            }
            return false;
        }
        log.info("NMS前检测框数量: " + boxes2d.size());
        // 执行非最大抑制，消除重复的边界框
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            log.info("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }

        int[] indicesArray = indices.toArray();

        // 获取保留的边界框
        if(indicesArray.length>50){

            setErrorImg(image,"maxIndex");
            log.info("最大消除后最大识别数量50 不可能大于50 "+indicesArray.length);
            return false;
        }
        log.info(confidences.size() + "类别下标-NMS后检测框数量" + indicesArray.length);
        // 在图像上绘制保留的边界框
        int c = 0;
        String audioText = "";
        Integer warnNumber = 0;
        String warnText = "";
        String warnName = "";
        //保存识别前的图片
        setBeforeImg(image,"end");
        // 计算letterbox的缩放参数
        double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
        double dx = (640 - image.cols() * scale) / 2;
        double dy = (640 - image.rows() * scale) / 2;

        for (int idx : indicesArray) {
            // 添加类别标签
            Rect2d box = boxes2d.get(idx);
            Integer ab = classIds.get(idx);
            String name = classNames.get(ab);
            float conf = confidences.get(idx);
            // 还原到原图坐标
            double xzb = (box.x - dx) / scale;
            double yzb = (box.y - dy) / scale;
            double width = box.width / scale;
            double height = box.height / scale;
            // 确保坐标在图像范围内
            xzb = Math.max(0, Math.min(xzb, image.cols() - 1));
            yzb = Math.max(0, Math.min(yzb, image.rows() - 1));
            width = Math.min(width, image.cols() - xzb);
            height = Math.min(height, image.rows() - yzb);

            if(netPush.getIsFollow()==0){
                log.info("[只要识别前置模型内的内容x:{},y:{},JSON:{}] ",xzb,yzb, JSON.toJSONString(retureBoxInfos));
                boolean followFlag= retureBoxInfo.getLocalhost(retureBoxInfos,xzb,yzb,netPush.getFollowPosition());
                if(!followFlag){
                    log.info("[不在范围内到直接跳过]");
                    continue;
                }else{
                    log.info("[在范围内开始推送： ]");
                }
            }

            if(pushInfo.getIsBy()==0){//开启了 0开启 1未开启
                boolean isPointFlag= isPointInArea(box.x, box.y,  Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasStartx()), Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasStarty()),  Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasWidth()), Double.parseDouble(pushInfo.getTabVideoUtil().getCanvasHeight()));
                log.info("[是否在区域内]:"+isPointFlag);
                if(isPointFlag){
                    log.info("[在区域内]");
                }else{
                    log.info("[不在区域内]");
                    continue;
                }
            }

            Scalar color=CommonColors(c);
            TabAiBase aiBase = VideoSendReadCfg.map.get(name);
            if (aiBase == null) {
                aiBase = new TabAiBase();
                aiBase.setChainName(name);

            }else{
                if(StringUtils.isNotEmpty(aiBase.getSpaceThree())&&aiBase.getSpaceThree().equals("N")){
                    log.warn("【当前不推送：{}】",name);
                    continue;
                }

                color=getColor(aiBase.getRgbColor());
            }
            log.error("【当前推送：{}】",name);


            audioText += aiBase.getRemark() + aiBase.getSpaceOne();
            warnNumber += aiBase.getSpaceTwo() == null ? 1 : aiBase.getSpaceTwo();
            warnText = setNmsName(warnText,StringUtils.isEmpty(aiBase.getRemark()) == true ? aiBase.getChainName() : aiBase.getRemark());
            warnName = setNmsName(warnName,aiBase.getChainName());
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
            log.info("识别消耗时间V5-v11：" + (b - a) + "ms");
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

    private static Mat letterboxResize(Mat image, int targetWidth, int targetHeight) {
        int originalWidth = image.cols();
        int originalHeight = image.rows();

        // 计算缩放比例
        double scale = Math.min((double) targetWidth / originalWidth, (double) targetHeight / originalHeight);

        // 计算新的尺寸
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        // 缩放图像
        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(newWidth, newHeight));

        // 创建目标尺寸的画布（灰色填充）
        Mat letterboxed = new Mat(targetHeight, targetWidth, image.type(), new Scalar(114, 114, 114));

        // 计算居中位置
        int dx = (targetWidth - newWidth) / 2;
        int dy = (targetHeight - newHeight) / 2;

        // 将缩放后的图像复制到画布中心
        Rect roi = new Rect(dx, dy, newWidth, newHeight);
        Mat roiMat = new Mat(letterboxed, roi);
        resized.copyTo(roiMat);

        return letterboxed;
    }

    public static boolean isPointInArea(double px, double py, double x, double y, double width, double height) {
        double x2 = x + width;
        double y2 = y + height;

        // 检查点是否在区域内
        return px >= x && px <= x2 && py >= y && py <= y2;
    }


    public String setNmsName(String WareText,String name){

        if (WareText == null || WareText.isEmpty()) {
            // WareText为空时，直接返回name
            return name;
        }
        log.info("[当前内容{}:替换内容{}]",WareText,name);
        if (WareText.contains(name)) {
        // 已经包含，不拼接
            return WareText;
        }
        return WareText + "," + name;

    }

    public void setErrorImg(Mat image,String txt){
        String saveName="D://error";
        try {
            log.info("错误存储地址{}", saveName);
            File imageFile = new File(saveName);
            if (!imageFile.exists()) {
                imageFile.mkdirs();
            }
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

            Imgcodecs.imwrite(saveName+"/"+txt+System.currentTimeMillis()+".jpg", image);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }

    public static void setTestImg(Mat image,String txt){
        String saveName="D://error/test";
        try {
            log.info("错误存储地址{}", saveName);
            File imageFile = new File(saveName);
            if (!imageFile.exists()) {
                imageFile.mkdirs();
            }
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

            Imgcodecs.imwrite(saveName+"/"+txt+System.currentTimeMillis()+".jpg", image);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }
    //不通过的图片
    public void setBeforeImg(Mat image,String txt){
        String saveName="D://error//"+txt;
        try {
            log.info("错误存储地址{}", saveName);
            File imageFile = new File(saveName);
            if (!imageFile.exists()) {
                imageFile.mkdirs();
            }
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
                    }
                } else {
                    log.info("[未开启录像]");
                }

                if (pushInfo.getPushStatic() == 0) {// 0 开启 1未开启
                    log.info("[推送第三方结果]：");
                    if(!pushInfo.getEventUrl().equals("localhost")){ //不进行推送
                        if(StringUtils.isNotEmpty(recordVideo)){
                            String base64Mp4 = base64Image(recordVideo);
                            push.setVideo(base64Mp4);
                        }
                        JSONObject ob = RestUtil.post(pushInfo.getEventUrl(), (JSONObject) JSONObject.toJSON(push));
                        log.info("返回内容：" + ob);
                    }
                } else {
                    log.info("[当前设置为：不推送第三方]");
                }

                if(pushInfo.getSaveLocalhost()==0){ //保存到本地
                    log.info("[本地也保存]");
                    push.setAlarmPicData(saveName);
                    push.setVideo(recordVideo);
                    // 获取 pushInfo 的路径部分

                    JSONObject ob = RestUtil.post("http://127.0.0.1:9998/jeecg-boot/video/tabAiWarning/addPush", (JSONObject) JSONObject.toJSON(push));

                    log.info("返回内容：" + ob);
                }

                if (pushInfo.getSaveRecord() != 0 && StringUtils.isNotEmpty(recordVideo)) {  //不保存本地录像
                    File imageFile = new File(recordVideo);
                    if (imageFile.exists()) {
                        imageFile.delete();
                    }
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
                    log.info("打开成功，使用 codec：" + codec);
                    break;
                } else {
                    log.info("打开失败 codec：" + codec);
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
                            //  log.info("识别到了");
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
                    log.info("不为空");
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
