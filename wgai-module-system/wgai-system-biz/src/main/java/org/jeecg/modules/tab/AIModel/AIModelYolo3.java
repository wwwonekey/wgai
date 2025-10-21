package org.jeecg.modules.tab.AIModel;

import ai.onnxruntime.*;
import com.alibaba.fastjson.JSONObject;
import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.extern.slf4j.Slf4j;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.lang3.StringUtils;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.demo.audio.entity.TabAudioDevice;
import org.jeecg.modules.demo.tab.entity.PushInfo;

import org.jeecg.modules.demo.tab.entity.TabAiModelBund;
import org.jeecg.modules.demo.video.entity.TabVideoUtil;
import org.jeecg.modules.message.websocket.WebSocket;


import org.jeecg.modules.tab.AIModel.V5.VideoReadInfoV5;
import org.jeecg.modules.tab.AIModel.V5.VideoReadV5;
import org.jeecg.modules.tab.AIModel.V5.VideoReadtestV5;
import org.jeecg.modules.tab.AIModel.V5Util.VideoReadInfoV5Util;
import org.jeecg.modules.tab.AIModel.V5Util.VideoReadV5Util;
import org.jeecg.modules.tab.AIModel.V5Util.VideoReadtestV5Util;
import org.jeecg.modules.tab.AIModel.pose.ActionResult;
import org.jeecg.modules.tab.AIModel.pose.FallDetectionResult;
import org.jeecg.modules.tab.util.CharRecognizer;
import org.opencv.core.*;


import org.opencv.core.Point;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.HOGDescriptor;
import org.opencv.utils.Converters;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.springframework.data.redis.core.RedisTemplate;

import java.awt.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.jeecg.modules.tab.AIModel.pose.ActionResult.recognizeAction;
import static org.jeecg.modules.tab.AIModel.pose.FallDetectionResult.detectFallOrStand;
import static org.jeecg.modules.tab.controller.AITestController.CommonColors;


/**
 * @author Wang_java
 * @text：
 * @date 2024/3/13 19:29
 */
@Slf4j
public class AIModelYolo3 {

    /**
     * 车牌识别图片
     *
     * @param weight
     * @param picUrl
     * @param saveName
     * @param uploadpath
     * @return
     * @throws Exception
     */
    public String SendPicOpencvCar(String weight, String picUrl, String saveName, String uploadpath) throws Exception {
        Long a = System.currentTimeMillis();
        Tesseract tesseract = new Tesseract();
        // 设置 Tesseract 数据路径（包含 tessdata 文件夹）
        tesseract.setDatapath("F:\\JAVAAI\\tessdata");

        log.info("picUrl地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        // 加载预训练的车牌检测级联分类器
        log.info("weight地址{}", uploadpath + File.separator + weight);
        CascadeClassifier plateDetector = new CascadeClassifier(uploadpath + File.separator + weight);
        // 检测车牌
        MatOfRect plates = new MatOfRect();
        plateDetector.detectMultiScale(image, plates);
        log.info("plates.toArray()地址{}" + plates.toArray());
        // 绘制检测到的车牌
        for (Rect rect : plates.toArray()) {
            // 提取车牌区域
            Mat plate = new Mat(image, rect);

            // 转换为灰度图像
            Imgproc.cvtColor(plate, plate, Imgproc.COLOR_BGR2GRAY);

            // 保存车牌区域以便 OCR 处理
            // 保存结果图像
            String savepath = uploadpath + File.separator + "temp" + File.separator;

            if (StringUtils.isNotBlank(saveName)) {
                savepath += saveName + ".jpg";
            } else {
                saveName = System.currentTimeMillis() + "";
                savepath += saveName + ".jpg";
            }
            log.info("保存路径: " + savepath);
            Imgcodecs.imwrite(savepath, plate);

            try {

                // 识别车牌中的字符
                String result = tesseract.doOCR(new File(savepath));
                log.info("识别内容: " + result);

                // 在原始图像上绘制边界框和识别结果
                Imgproc.rectangle(image, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 255, 0), 2);
                //       Imgproc.putText(image, classNames.get(ab), new Point(box.x, box.y - 5), Core.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 1);
                Imgproc.putText(image, result, new Point(rect.x, rect.y - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 255, 0), 2);
            } catch (TesseractException e) {
                e.printStackTrace();
            }
        }


        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }
        Imgcodecs.imwrite(savepath, image);
        Long b = System.currentTimeMillis();
        log.info("消耗时间：" + (b - a));
        return saveName + ".jpg";
    }


    /**
     * 车牌识别图片
     *
     * @param weight
     * @param picUrl
     * @param saveName
     * @param uploadpath
     * @return
     * @throws Exception
     */
    public Map<String, Object> SendPicOpencvCarV5(String weight, String picUrl, String saveName, String uploadpath) throws Exception {
        Map<String, Object> map = new HashMap<>();
        log.info(uploadpath);
        Long a = System.currentTimeMillis();
        // 加载类别名称
//        List<String> classes = Files.readAllLines(Paths.get(uploadpath+ File.separator +names));
        // 加载YOLOv5模型

        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromONNX(uploadpath + File.separator + weight);
        //  net.setPreferableBackend(Dnn.DNN_BACKEND_CUDA);
        net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        net.setPreferableTarget(Dnn.DNN_TARGET_CPU);
        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("图片地址{}", image);

        Mat blob = Dnn.blobFromImage(image, 1 / 255.0, new Size(640, 640), new Scalar(0), true, false);
        net.setInput(blob);

        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = getOutputNames(net);
        net.forward(result, outBlobNames);
        log.info("output{}", Arrays.asList(outBlobNames));
        if (result.isEmpty()) {
            System.err.println("Failed to get output from the model.");
            map.put("isOk", false);
            return map;
        }


        float confThreshold = 0.3f;
        float nmsThreshold = 0.4f;

        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            int index = (int) output.size(0);
            int rows = (int) output.size(1);
            int cols = (int) output.size(2);
            // Dims: 3, Rows: 25200, Cols: 8 row,Mat [ 1*25200*8*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x28dce2da990, dataAddr=0x28dd0ebc640 ]index:1
            System.out.println("Dims: " + dims + ", Rows: " + rows + ", Cols: " + cols + " row," + output.row(0) + "index:" + index);
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

// 应用非极大值抑制
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            System.out.println("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }
        int c = 0;
        int[] indices_arr = indices.toArray();
        String plateNumber = "";
        String plateColor = "";
        for (int idx : indices_arr) {
            Rect2d box = boxes2d.get(idx);

            int classId = classIds.get(idx);
            float conf = confidences.get(idx);
            double x = box.x;
            double y = box.y;
            double width = box.width * ((double) image.cols() / 640);
            double height = box.height * ((double) image.rows() / 640);
            double xzb = x * ((double) image.cols() / 640);
            double yzb = y * ((double) image.rows() / 640);
            System.out.println("绘制1" + "x:" + x + "y:" + y + "");
            System.out.println("绘制1" + "width:" + width + "height:" + height + "");
            System.out.println(" image.cols()" + Double.valueOf((double) image.cols() / 640));
            System.out.println(" image.rows()" + Double.valueOf((double) image.rows() / 640));

            Rect rect = new Rect((int) Math.round(xzb), (int) Math.round(yzb), (int) Math.round(width), (int) Math.round(height));
            Mat plaateimage = Imgcodecs.imread(uploadpath + File.separator + picUrl);
            Mat plateMat = new Mat(plaateimage, rect);


            plateNumber += carStr(plateMat, uploadpath) + ";";

            plateColor += carColorStr(plateMat) + ";";

            System.out.println("车牌号码: " + plateNumber);
            System.out.println("车牌颜色: " + plateColor);

            // 在原图上绘制检测结果
            Imgproc.rectangle(image, rect, new Scalar(0, 255, 0), 2);
            Imgproc.putText(image, "plateNumber", new Point(rect.x, rect.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 255, 0), 2);


            c++;
        }
        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }
        log.info(savepath);
        Imgcodecs.imwrite(savepath, image);
        Long b = System.currentTimeMillis();
        log.info("消耗时间：" + (b - a));
        map.put("url", saveName + ".jpg");
        map.put("color", plateColor);
        map.put("plate", plateNumber);
        map.put("isOk", true);
        log.info("车牌号码: " + plateNumber);
        log.info("车牌颜色: " + plateColor);
        return map;
    }

    /***
     * 返回车牌识别文字内容
     * @param url
     * @return
     */
    public static String carStr(Mat url, String uploadpath) {
        String savepath = uploadpath + File.separator + "temp" + File.separator;
        savepath += System.currentTimeMillis() + ".jpg";
        Imgcodecs.imwrite(savepath, url);
        InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
        OcrResult ocrResult = engine.runOcr(savepath);
        return ocrResult.getStrRes().trim().replaceAll(" ", "");
    }


    /***
     * 返回图片识别文字内容
     * @param url
     * @return
     */
    public static String imageStr(String url, String path) {

        InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
        OcrResult ocrResult = engine.runOcr(path + File.separator + url);
        return ocrResult.getStrRes().trim().replaceAll(" ", "");
    }

    /***
     * 返回语音识别文字内容
     * @param url
     * @return
     */
    public static String audioStr(String url, String path) {

        InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
        OcrResult ocrResult = engine.runOcr(path + File.separator + url);
        return ocrResult.getStrRes().trim().replaceAll(" ", "");
    }

    /***
     * 返回车牌颜色
     * @param
     * @return
     */
    public static String carColorStr(Mat image) {
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        Mat plateRegion = image.clone();
        // 将车牌区域转换为 HSV 颜色空间
        Mat hsv = new Mat();
        Imgproc.cvtColor(plateRegion, hsv, Imgproc.COLOR_BGR2HSV);
        // 定义颜色范围（HSV）
        Scalar lowerBlue = new Scalar(100, 150, 150);
        Scalar upperBlue = new Scalar(140, 255, 255);

        Scalar lowerGreen = new Scalar(35, 100, 100);
        Scalar upperGreen = new Scalar(85, 255, 255);

        Scalar lowerWhite = new Scalar(0, 0, 200);
        Scalar upperWhite = new Scalar(180, 50, 255);

        Scalar lowerYellow = new Scalar(20, 100, 100);
        Scalar upperYellow = new Scalar(30, 255, 255);
        // 创建掩模
        Mat maskBlue = new Mat();
        Core.inRange(hsv, lowerBlue, upperBlue, maskBlue);

        Mat maskGreen = new Mat();
        Core.inRange(hsv, lowerGreen, upperGreen, maskGreen);

        Mat maskWhite = new Mat();
        Core.inRange(hsv, lowerWhite, upperWhite, maskWhite);

        Mat maskYellow = new Mat();
        Core.inRange(hsv, lowerYellow, upperYellow, maskYellow);

        // 计算掩模区域的面积
        double blueArea = Core.countNonZero(maskBlue);
        double greenArea = Core.countNonZero(maskGreen);
        double whiteArea = Core.countNonZero(maskWhite);
        double yellowArea = Core.countNonZero(maskYellow);

        // 判断车牌颜色
        String colorName = getColorName(blueArea, greenArea, whiteArea, yellowArea);
        return colorName;
    }

    /**
     * 车牌识别内容
     *
     * @param picUrl
     * @return
     * @throws Exception
     */
    public static String SendPicOpencvCarStr(String picUrl) throws Exception {


        Mat plate = Imgcodecs.imread(picUrl);

        // 转换为灰度图像
        Imgproc.cvtColor(plate, plate, Imgproc.COLOR_BGR2GRAY);
//        // 二值化处理
        Imgproc.threshold(plate, plate, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
//          //去噪声
//        Imgproc.medianBlur(plate, plate, 3);
//        //. 形态学操作
//        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(3, 3));
//        Imgproc.dilate(plate, plate, kernel);
        // 图像增强：调整对比度和亮度
        //    plate.convertTo(plate, -1, 1.2, 30); // 增强对比度和亮度

        // 保存车牌区域以便 OCR 处理
        // 保存结果图像
        String savepath = "F:\\JAVAAI\\tessdata\\";


        String saveName = System.currentTimeMillis() + "";
        savepath += saveName + ".jpg";

        log.info("保存路径: " + savepath);
        Imgcodecs.imwrite(savepath, plate);
        // 识别车牌字符
        String result = "";
        try {
            //  result = carStr(savepath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        //tesseract.doOCR(new File(picUrl));
        System.out.println("识别结果: " + result);
        return result;
    }

    /**
     * 根据掩模区域的面积判断颜色
     *
     * @param blueArea   蓝色掩模区域的面积
     * @param greenArea  绿色掩模区域的面积
     * @param whiteArea  白色掩模区域的面积
     * @param yellowArea 黄色掩模区域的面积
     * @return 车牌颜色名称
     */
    public static String getColorName(double blueArea, double greenArea, double whiteArea, double yellowArea) {
        double maxArea = Math.max(Math.max(blueArea, greenArea), Math.max(whiteArea, yellowArea));

        if (maxArea == blueArea) {
            return "蓝色";
        } else if (maxArea == greenArea) {
            return "绿色";
        } else if (maxArea == whiteArea) {
            return "白色";
        } else if (maxArea == yellowArea) {
            return "黄色";
        } else {
            return "未知颜色";
        }
    }

    // 锐化图像
    private static void sharpenImage(Mat input, Mat output) {
        Mat kernel = new Mat(3, 3, CvType.CV_32F);
        kernel.put(0, 0, 0, -1, 0);
        kernel.put(1, 0, -1, 5, -1);
        kernel.put(2, 0, 0, -1, 0);
        Imgproc.filter2D(input, output, -1, kernel);
    }

    public static void main(String[] args) throws Exception {
        System.load("F:\\JAVAAI\\opencv481\\opencv\\build\\java\\x64\\opencv_java481.dll");
// 读取图片
        Mat img = Imgcodecs.imread("F:\\logs\\e09875acd34dc8540d67396b890b327.png");

        // 初始化 HOGDescriptor
        HOGDescriptor hog = new HOGDescriptor();
        hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector());

        // 检测
        MatOfRect foundLocations = new MatOfRect();
        MatOfDouble foundWeights = new MatOfDouble();
        hog.detectMultiScale(img, foundLocations, foundWeights);

        // 判断是否有人
        Rect[] detections = foundLocations.toArray();
        if (detections.length > 0) {
            System.out.println("✅ 检测到 " + detections.length + " 人");
            // 画框
            for (Rect r : detections) {
                Imgproc.rectangle(img, r, new Scalar(0, 255, 0), 2);
            }
            Imgcodecs.imwrite("F:\\logs\\result.jpg", img);
        } else {
            System.out.println("❌ 没有人");
        }


        // 读取图像
//        Mat src = Imgcodecs.imread("F:\\home\\1740119768303.png");
////
////        String starstr=imageStr("1740110094401.jpg","F:\\home");
////        System.out.println("starstr"+starstr);
//        // 转换为灰度图像
//        // 1. 图像预处理：灰度化、二值化
//        // 1. 图像预处理：灰度化、二值化
//        Mat gray = new Mat();
//        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
//
//        Mat binary = new Mat();
//        Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
//
//        // 2. 放大图像减少极坐标转换中的锯齿
//        Mat enlargedImage = new Mat();
//        Imgproc.resize(binary, enlargedImage, new Size(binary.cols() * 2, binary.rows() * 2), 0, 0, Imgproc.INTER_CUBIC);
//
//        // 3. 添加字符间的空白（膨胀操作）
//        Mat dilatedImage = new Mat();
//        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 1));  // 使用一个2x2的矩阵作为膨胀核
//        Imgproc.dilate(enlargedImage, dilatedImage, kernel);
//
//        // 4. 检测轮廓
//        List<MatOfPoint> contours = new ArrayList<>();
//        Mat hierarchy = new Mat();
//        Imgproc.findContours(dilatedImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//
//        // 5. 使用 RotatedRect 检测弧形文字区域
//        for (MatOfPoint contour : contours) {
//            RotatedRect rotatedRect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));
//
//            // 获取旋转矩形的角点坐标
//            Point[] vertices = new Point[4];
//            rotatedRect.points(vertices);
//
//            // 在原图上绘制矩形
//            for (int j = 0; j < 4; j++) {
//                Imgproc.line(src, vertices[j], vertices[(j + 1) % 4], new Scalar(0, 255, 0), 2);
//            }
//
//            // 6. 极坐标变换矫正弧形文字（使用更高阶的插值方法）
//            Mat polarImg = new Mat();
//            Point center = rotatedRect.center;  // 弧形文字的中心
//            double maxRadius = rotatedRect.size.height / 2.0; // 以高度为半径
//            Imgproc.linearPolar(dilatedImage, polarImg, center, maxRadius, Imgproc.INTER_CUBIC + Imgproc.WARP_FILL_OUTLIERS);
//
//            // 7. 锐化极坐标变换后的图像
//            Mat sharpenKernel = new Mat(3, 3, CvType.CV_32F);
//            float[] kernelData = {
//                    0, -1, 0,
//                    -1, 5, -1,
//                    0, -1, 0
//            };
//            sharpenKernel.put(0, 0, kernelData);
//
//            Mat sharpenedImage = new Mat();
//            Imgproc.filter2D(polarImg, sharpenedImage, -1, sharpenKernel);
//
////            // 8. 细化字体边缘（腐蚀操作）
////            Mat erodedImage = new Mat();
////            Mat erosionKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 1));  // 细化字体边缘
////            Imgproc.erode(sharpenedImage, erodedImage, erosionKernel);
//
//            // 保存锐化和放大后的图像
//            String outputPolarImagePath = "F:\\home\\path_to_output_polar_image.jpg";
//            Imgcodecs.imwrite(outputPolarImagePath, sharpenedImage);
//
//            System.out.println("极坐标变换后的清晰图像已保存: " + outputPolarImagePath);
//
//            // 后续可以将 polarImg 图像传递给 OCR 工具进行识别
//            // 使用如 RapidOCR 等工具来识别展开的直线文字
//        }
//
//        // 保存标注了旋转矩形的原图像
//        String outputImagePath = "F:\\home\\orrected_image.jpg";
//        Imgcodecs.imwrite(outputImagePath, src);
//        // 保存结果
////
//        String endstr=imageStr("path_to_output_polar_image.jpg","F:\\home");
//        System.out.println("endstr"+endstr);


//        SendPicOpencvCarStr("F:\\JAVAAI\\tessdata\\1722241390893.png");

//            InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
//            OcrResult ocrResult = engine.runOcr("F:\\JAVAAI\\tessdata\\ws.jpg");
//            log.info("当前结果为："+ocrResult.getStrRes().trim().replaceAll(" ",""));
        //   System.out.println( ocrResult.getStrRes().replace("\n", ""));
        // System.load("F:\\JAVAAI\\opencv\\build\\java\\x64\\opencv_java3416.dll");

//        //   SendPicYoloV3("yolov3.weights","yolov3.cfg","coco.names","car.jpg","test","F:\\JAVAAI\\yolo3\\yuanshi");
//            SendPicYoloV5("NBplate.onnx","coco.names","writecat.jpg","","F:\\JAVAAI\\yolov5");
//    //    SendPicYoloV5Car("NBplate.onnx","coco.names","bluecar.jpg","","F:\\JAVAAI\\yolov5");
//        String rtspUrl="rtsp://admin:ch255899@192.168.0.200/Streaming/Channels/102";
//
////        String rtspUrl = "rtsp://[用户名]:[密码]@[IP地址]:[端口]/[码流类型]";
//
//        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl);
//        OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
//        VideoWriter videoWriter = new VideoWriter();
//        try {
//            grabber.setOption("rtsp_transport", "tcp"); // 使用TCP而不是UDP
//            grabber.start();
//            System.out.println("连接到RTSP流成功");
//            Java2DFrameConverter converter = new Java2DFrameConverter();
//            Frame frame;
//            int a=0;
//
//            FrameRecorder recorder = FrameRecorder.createDefault("F:\\JAVAAI\\model\\test3\\test1.mp4", grabber.getImageWidth(), grabber.getImageHeight());
//            recorder.setVideoCodec(grabber.getVideoCodec());
//            recorder.setFormat("mp4");
//            recorder.start();
//
//            while ((frame = grabber.grab()) != null) {
//
//                // 将Frame转换为JavaCV的Mat
//                int width=frame.imageWidth;
//                int height=frame.imageHeight;
//                System.out.println("成功获取帧宽度"+width+"高度:"+height);
//                Mat opencvMat=bufferedImageToMat(   converter.getBufferedImage(frame));
//                if(a<=200){
//                    // 录制帧 b释放才会保存
//                    Frame processedFrame = converterToFrame(opencvMat);
//                    recorder.record(processedFrame);
////                    Imgcodecs.imwrite("F:\\JAVAAI\\model\\test3\\test"+a+".jpg", opencvMat);
////                    saveVideo( opencvMat, frame, videoWriter,"F:\\JAVAAI\\model\\test3\\test1.mp4");
//
//                    // 将处理后的帧发送给所有连接的客户端
//          //          broadcastFrame(processedFrame);
//                }else {
//                    break;
//                }
//                a++;
//
//
//                // 显示帧（如果您想要显示视频）
//             //   canvasFrame.showImage(frame);
//
//                // 在这里可以对帧进行处理
//                // 例如：保存帧、分析帧内容等
//            }
//
//            grabber.stop();
//            grabber.release();
//
//            recorder.stop();
//            recorder.release();
//       //     canvasFrame.dispose();
//        } catch (Exception e) {
//            System.err.println("发生错误: " + e.getMessage());
//            e.printStackTrace();
//        }


    }

    public static Frame converterToFrame(Mat mat) {
        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
        return matConverter.convert(mat);
    }

    public static void saveVideo(Mat mat, Frame frame, VideoWriter videoWriter, String outputVideoPath) {
        // 获取视频帧的宽度和高度
        int frameWidth = frame.imageWidth;
        int frameHeight = frame.imageHeight;
        Size frameSize = new Size(frameWidth, frameHeight);


        int fourcc = videoWriter.fourcc('M', 'J', 'P', 'G');
        videoWriter.open(outputVideoPath, fourcc, 30, frameSize, true);
        videoWriter.write(mat);
    }

    private static int max(float[] arr) {
        int maxIdx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[maxIdx]) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    /***
     * AI模型嵌套模型
     * 需要绝对路径
     * 输入图片
     */
    public static String SendPicYoloV8(String weight, String names, String picUrl, String saveName, String uploadpath) throws Exception {
        log.info(uploadpath);
        Long a = System.currentTimeMillis();
        // 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        // 加载YOLOv8模型

        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromONNX(uploadpath + File.separator + weight);
        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("图片地址{}", image);

        Mat blob = Dnn.blobFromImage(image, 1 / 255.0, new Size(640, 640), new Scalar(0), true, false);
        net.setInput(blob);

        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = net.getUnconnectedOutLayersNames();
        net.forward(result, outBlobNames);
        System.out.println(Arrays.asList(outBlobNames));
        if (result.isEmpty()) {
            System.err.println("Failed to get output from the model.");
            return "error";
        }


        float confThreshold = 0.3f;
        float nmsThreshold = 0.4f;

        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            int index = (int) output.size(0);
            int rows = (int) output.size(1);
            int cols = (int) output.size(2);
            // Dims: 3, Rows: 25200, Cols: 8 row,Mat [ 1*25200*8*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x28dce2da990, dataAddr=0x28dd0ebc640 ]index:1
            System.out.println("Dims: " + dims + ", Rows: " + rows + ", Cols: " + cols + " row," + output.row(0) + "index:" + index);
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

// 应用非极大值抑制
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            System.out.println("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }
        int c = 0;
        int[] indices_arr = indices.toArray();
        for (int idx : indices_arr) {
            Rect2d box = boxes2d.get(idx);
            int classId = classIds.get(idx);
            float conf = confidences.get(idx);
            double x = box.x;
            double y = box.y;
            double width = box.width * ((double) image.cols() / 640);
            double height = box.height * ((double) image.rows() / 640);
            double xzb = x * ((double) image.cols() / 640);
            double yzb = y * ((double) image.rows() / 640);
            System.out.println("绘制1" + "x:" + x + "y:" + y + "");
            System.out.println("绘制1" + "width:" + width + "height:" + height + "");
            System.out.println(" image.cols()" + Double.valueOf((double) image.cols() / 640));
            System.out.println(" image.rows()" + Double.valueOf((double) image.rows() / 640));

            Imgproc.rectangle(image,
                    new Point(xzb, yzb),
                    new Point(xzb + width, yzb + height),
                    CommonColors(c), 2);
            String label = classes.get(classId) + ": " + String.format("%.2f", conf);
            Imgproc.putText(image, label, new Point(xzb, yzb - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 2);
            c++;
        }
        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }
        log.info(savepath);
        Imgcodecs.imwrite(savepath, image);
        Long b = System.currentTimeMillis();
        log.info("消耗时间：" + (b - a));
        return saveName + ".jpg";
    }


    /***
     * AI模型嵌套模型
     * 需要绝对路径
     * 输入图片
     */
    public static String SendPicYoloV5(String weight, String names, String picUrl, String saveName, String uploadpath) throws Exception {
        log.info(uploadpath);
        Long a = System.currentTimeMillis();
        // 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        // 加载YOLOv5模型

        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromONNX(uploadpath + File.separator + weight);
        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("图片地址{}", image);
// 2
        Mat blob = Dnn.blobFromImage(image, 1 / 255.0, new Size(640, 640), new Scalar(0), true, false);
        net.setInput(blob);

        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = getOutputNames(net);
        net.forward(result, outBlobNames);
        System.out.println(Arrays.asList(outBlobNames));
        if (result.isEmpty()) {
            System.err.println("Failed to get output from the model.");
            return "error";
        }


        float confThreshold = 0.45f;
        float nmsThreshold = 0.4f;

        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            int index = (int) output.size(0);
            int rows = (int) output.size(1);
            int cols = (int) output.size(2);
            // Dims: 3, Rows: 25200, Cols: 8 row,Mat [ 1*25200*8*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x28dce2da990, dataAddr=0x28dd0ebc640 ]index:1
            System.out.println("Dims: " + dims + ", Rows: " + rows + ", Cols: " + cols + " row," + output.row(0) + "index:" + index);
            Mat detectionMat = output.reshape(1, output.size(1));

            for (int i = 0; i < detectionMat.rows(); i++) {
                Mat detection = detectionMat.row(i);
                Mat scores = detection.colRange(5, cols);
                Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                float confidence = (float) detection.get(0, 4)[0];
                //   log.info("当前的信任阈值{}",Math.round(confidence));
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
                    log.info("识别到了");
                }
            }
        }

// 应用非极大值抑制
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            System.out.println("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }
        //// 转换为 MatOfRect2d 和 MatOfFloat
//        MatOfRect2d boxesNMS = new MatOfRect2d();
//        boxesNMS.fromList(boxes);
//
//        MatOfFloat confidencesNMS = new MatOfFloat();
//        confidencesNMS.fromList(confidences.stream().map(Float::valueOf).collect(Collectors.toList()));
//
//// 应用非最大抑制
//        MatOfInt indicesNMS = new MatOfInt();
//        Dnn.NMSBoxes(boxesNMS, confidencesNMS, CONFIDENCE_THRESHOLD, NMS_THRESHOLD, indicesNMS);
// 绘制结果
        //    List<String> classes = Arrays.asList("person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush");
        int c = 0;
        int[] indices_arr = indices.toArray();
        for (int idx : indices_arr) {
            log.info("idx{}-{} i", idx, indices_arr.length);
            Rect2d box = boxes2d.get(idx);
            int classId = classIds.get(idx);
            float conf = confidences.get(idx);
            double x = box.x;
            double y = box.y;
            double width = box.width * ((double) image.cols() / 640);
            double height = box.height * ((double) image.rows() / 640);
            double xzb = x * ((double) image.cols() / 640);
            double yzb = y * ((double) image.rows() / 640);
            System.out.println("绘制1" + "x:" + x + "y:" + y + "");
            System.out.println("绘制1" + "width:" + width + "height:" + height + "");
            System.out.println(image.cols() + " image.cols()" + Double.valueOf((double) image.cols() / 640));
            System.out.println(image.rows() + " image.rows()" + Double.valueOf((double) image.rows() / 640));

            Imgproc.rectangle(image,
                    new Point(xzb, yzb),
                    new Point(xzb + width, yzb + height),
                    CommonColors(c), 2);
            String label = classes.get(classId) + ": " + String.format("%.2f", conf);
            Imgproc.putText(image, label, new Point(xzb, yzb - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 2);
            c++;
        }
        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }
        log.info(savepath);
        Imgcodecs.imwrite(savepath, image);
        Long b = System.currentTimeMillis();
        log.info("消耗时间：" + (b - a));
        return saveName + ".jpg";
    }

    /**
     * yolov11
     *
     * @param weight
     * @param names
     * @param picUrl
     * @param saveName
     * @param uploadpath
     * @return
     * @throws Exception
     */
    public static String SendPicYoloV12(String weight, String names, String picUrl, String saveName, String uploadpath) throws Exception {
        log.info(uploadpath);
        Long a = System.currentTimeMillis();
        // 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        // 加载YOLOv11模型

        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromONNX(uploadpath + File.separator + weight);
        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("图片地址{}", image);
// 2
        Mat blob = Dnn.blobFromImage(image, 1 / 255.0, new Size(640, 640), new Scalar(0), true, false);
        net.setInput(blob);

        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = getOutputNames(net);
        net.forward(result, outBlobNames);
        System.out.println(Arrays.asList(outBlobNames));
        if (result.isEmpty()) {
            System.err.println("Failed to get output from the model.");
            return "error";
        }


        float confThreshold = 0.3f;
        float nmsThreshold = 0.4f;

        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            long dim0 = output.size(0);  // batch size
            long dim1 = output.size(1);  // 通常是84 (4坐标 + 80类别)
            long dim2 = output.size(2);  // 通常是8400 (检测点数量)

            // YOLOv11: Dims: 3, dim1: 84, dim2: 8400
            // YOLOv5:  Dims: 3, dim1: 25200, dim2: 85
            System.out.println("Dims: " + dims + ", dim1: " + dim1 + ", dim2: " + dim2);

            Mat detectionMat;
            int rows, cols;


            // YOLOv11格式 [1, 84, 8400] - 需要转置
            Mat reshaped = output.reshape(1, (int) dim1);  // [84, 8400]
            Mat transposed = new Mat();
            Core.transpose(reshaped, transposed);  // [8400, 84]
            detectionMat = transposed;
            rows = (int) dim2;  // 8400
            cols = (int) dim1;  // 84


            System.out.println("处理后 - Rows: " + rows + ", Cols: " + cols);

            for (int i = 0; i < rows; i++) {
                Mat detection = detectionMat.row(i);

                float confidence;
                Mat scores;
                Point classIdPoint;


                // YOLOv11格式：[x, y, w, h, class0_conf, class1_conf, ...]
                // 前4个是坐标，后面的是各类别置信度
                scores = detection.colRange(4, cols);
                Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                confidence = (float) minMaxResult.maxVal;  // 最大置信度
                classIdPoint = minMaxResult.maxLoc;


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
                    log.info("识别到了");
                }
            }
        }

// 应用非极大值抑制
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();

        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            System.out.println("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }

// 绘制结果
        int c = 0;
        int[] indices_arr = indices.toArray();
        for (int idx : indices_arr) {
            log.info("idx{}-{} i", idx, indices_arr.length);
            Rect2d box = boxes2d.get(idx);
            int classId = classIds.get(idx);
            float conf = confidences.get(idx);
            double x = box.x;
            double y = box.y;
            double width = box.width * ((double) image.cols() / 640);
            double height = box.height * ((double) image.rows() / 640);
            double xzb = x * ((double) image.cols() / 640);
            double yzb = y * ((double) image.rows() / 640);
            System.out.println("绘制1" + "x:" + x + "y:" + y + "");
            System.out.println("绘制1" + "width:" + width + "height:" + height + "");
            System.out.println(image.cols() + " image.cols()" + Double.valueOf((double) image.cols() / 640));
            System.out.println(image.rows() + " image.rows()" + Double.valueOf((double) image.rows() / 640));

            Imgproc.rectangle(image,
                    new Point(xzb, yzb),
                    new Point(xzb + width, yzb + height),
                    CommonColors(c), 2);
            String label = classes.get(classId) + ": " + String.format("%.2f", conf);
            Imgproc.putText(image, label, new Point(xzb, yzb - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 2);
            c++;
        }
        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }
        log.info(savepath);
        Imgcodecs.imwrite(savepath, image);
        Long b = System.currentTimeMillis();
        log.info("消耗时间：" + (b - a));
        return saveName + ".jpg";
    }


    public static String SendPicYoloV11CVPose(String weight, String names, String picUrl, String saveName, String uploadpath) throws Exception {
        log.info(uploadpath);
        Long a = System.currentTimeMillis();

        // 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));

        // 加载YOLOv11 Pose模型
        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromONNX(uploadpath + File.separator + weight);

        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("原始图片尺寸: {}x{}", image.cols(), image.rows());

        // 使用letterbox预处理
        Mat processedImage = letterboxResize(image, 640, 640);

        // 创建blob
        Mat blob = Dnn.blobFromImage(processedImage, 1.0 / 255.0, new Size(640, 640), new Scalar(0, 0, 0), true, false, CvType.CV_32F);
        net.setInput(blob);

        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = getOutputNames(net);
        net.forward(result, outBlobNames);

        if (result.isEmpty()) {
            System.err.println("Failed to get output from the model.");
            return "error";
        }

        // 调整阈值
        float confThreshold = 0.35f;
        float nmsThreshold = 0.2f;

        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();
        List<float[]> keypoints = new ArrayList<>(); // 存储关键点数据

        for (Mat output : result) {
            int dims = output.dims();
            long dim0 = output.size(0);
            long dim1 = output.size(1);
            long dim2 = output.size(2);

            System.out.println("输出维度: [" + dim0 + ", " + dim1 + ", " + dim2 + "]");

            Mat detectionMat;
            int rows, cols;

            // 判断是否为YOLO11 Pose格式 [1, 56, 8400] (4 + 1 + 51关键点)
            if (dims == 3 && dim1 == 56 && dim2 > 1000) {
                // YOLOv11 Pose格式 [1, 56, 8400]
                System.out.println("检测到YOLOv11 Pose格式");
                Mat reshaped = output.reshape(1, (int) dim1);
                Mat transposed = new Mat();
                Core.transpose(reshaped, transposed);
                detectionMat = transposed;
                rows = (int) dim2;
                cols = (int) dim1;
            } else {
                // 标准格式处理
                System.out.println("检测到标准格式");
                detectionMat = output.reshape(1, (int) output.size(1));
                rows = detectionMat.rows();
                cols = detectionMat.cols();
            }

            System.out.println("处理后矩阵: " + rows + "x" + cols);

            for (int i = 0; i < rows; i++) {
                Mat detection = detectionMat.row(i);

                float confidence;

                if (cols == 56) { // YOLOv11 Pose: [x, y, w, h, conf, 17*3个关键点坐标]
                    // 获取边界框坐标
                    float centerX = (float) detection.get(0, 0)[0];
                    float centerY = (float) detection.get(0, 1)[0];
                    float width = (float) detection.get(0, 2)[0];
                    float height = (float) detection.get(0, 3)[0];
                    confidence = (float) detection.get(0, 4)[0];

                    if (confidence > confThreshold) {
                        float left = centerX - width / 2;
                        float top = centerY - height / 2;

                        // 提取关键点数据 (17个关键点，每个3个值：x, y, visibility)
                        float[] kpts = new float[51]; // 17 * 3
                        for (int j = 0; j < 51; j++) {
                            kpts[j] = (float) detection.get(0, 5 + j)[0];
                        }

                        classIds.add(0); // 人体检测通常只有一个类别
                        confidences.add(confidence);
                        boxes2d.add(new Rect2d(left, top, width, height));
                        keypoints.add(kpts);

                        log.info("检测到人体: 置信度={}, 坐标=({},{},{},{})",
                                confidence, left, top, width, height);
                    }
                }
            }
        }

        System.out.println("NMS前检测框数量: " + boxes2d.size());
        if( boxes2d.size()<=0){
            return "未检测到";
        }
        // 应用非极大值抑制
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();

        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }

        int[] indices_arr = indices.toArray();
        System.out.println("NMS后检测框数量: " + indices_arr.length);

        // 计算letterbox的缩放参数
        double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
        double dx = (640 - image.cols() * scale) / 2;
        double dy = (640 - image.rows() * scale) / 2;

        // 绘制结果
        int c = 0;
        for (int idx : indices_arr) {
            Rect2d box = boxes2d.get(idx);
            int classId = classIds.get(idx);
            float conf = confidences.get(idx);
            float[] kpts = keypoints.get(idx);

            // 还原边界框到原图坐标
            double x = (box.x - dx) / scale;
            double y = (box.y - dy) / scale;
            double width = box.width / scale;
            double height = box.height / scale;

            // 确保坐标在图像范围内
            x = Math.max(0, Math.min(x, image.cols() - 1));
            y = Math.max(0, Math.min(y, image.rows() - 1));
            width = Math.min(width, image.cols() - x);
            height = Math.min(height, image.rows() - y);

            // 执行动作识别
//            ActionResult actionResult = recognizeAction(kpts, scale, dx, dy);
//
//            System.out.println(String.format("检测到人体: 置信度=%.3f, 动作=%s (%.2f), 描述=%s",
//                    conf, actionResult.getActionName(), actionResult.getConfidence(), actionResult.getDescription()));
            FallDetectionResult fallResult = detectFallOrStand(kpts, scale, dx, dy);
            System.out.println(String.format("人体检测: 置信度=%.3f, 状态=%s (%.2f), 原因=%s, 报警=%s",
                    conf, fallResult.getStatus(), fallResult.getConfidence(), fallResult.getReason(), fallResult.isAlert()));


            // 绘制边界框
            Imgproc.rectangle(image,
                    new Point(x, y),
                    new Point(x + width, y + height),
                    CommonColors(c), 2);

            String label = "person: " + String.format("%.2f", conf);
            Imgproc.putText(image, label, new Point(x, y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 2);

            // 绘制关键点和骨骼连接
            drawPoseKeypoints(image, kpts, scale, dx, dy);

            c++;
        }

        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }

        log.info("保存路径: {}", savepath);
        Imgcodecs.imwrite(savepath, image);

        Long b = System.currentTimeMillis();
        log.info("总耗时: {}ms", (b - a));

        return saveName + ".jpg";
    }
    public static String SendPicYoloV11ONNXPose(String weight, String names, String picUrl,
                                                String saveName, String uploadpath, boolean useGpu) throws Exception {
        log.info(uploadpath);
        Long startTime = System.currentTimeMillis();

        // 1. 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));

        // 2. 读取输入图像并预处理
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("原始图片尺寸: {}x{}", image.cols(), image.rows());

        // 使用letterbox预处理
        Mat processedImage = letterboxResize(image, 640, 640);

        // 3. 转换为CHW格式的float数组
        Mat blob = new Mat();
        processedImage.convertTo(blob, CvType.CV_32F, 1.0 / 255.0);
        List<Mat> channels = new ArrayList<>();
        Core.split(blob, channels);
        float[] inputData = new float[3 * 640 * 640];
        for (int c = 0; c < 3; c++) {
            float[] data = new float[640 * 640];
            channels.get(c).get(0, 0, data);
            System.arraycopy(data, 0, inputData, c * 640 * 640, 640 * 640);
        }

        // 4. 创建 ONNX Runtime 环境
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        if (useGpu) {
            options.addCUDA();
        } else {
            options.addCPU(true);
        }

        try (OrtSession session = env.createSession(uploadpath + File.separator + weight, options)) {
            // 5. 构建输入张量
            long[] shape = new long[]{1, 3, 640, 640};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);
            Map<String, OnnxTensor> inputs = Collections.singletonMap(
                    session.getInputNames().iterator().next(), inputTensor);

            // 6. 推理并解析结果
            float confThreshold = 0.45f;
            float nmsThreshold = 0.35f;

            List<Rect2d> boxes2d = new ArrayList<>();
            List<Float> confidences = new ArrayList<>();
            List<Integer> classIds = new ArrayList<>();
            List<float[]> keypoints = new ArrayList<>();

            try (OrtSession.Result results = session.run(inputs)) {
                for (Map.Entry<String, OnnxValue> entry : results) {
                    OnnxValue value = entry.getValue();
                    if (!(value instanceof OnnxTensor)) continue;
                    OnnxTensor tensor = (OnnxTensor) value;

                    long[] tensorShape = tensor.getInfo().getShape();
                    Object rawOutput = tensor.getValue();

                    System.out.println("输出维度: [" + tensorShape[0] + ", " + tensorShape[1] + ", " + tensorShape[2] + "]");

                    if (rawOutput instanceof float[][][]) {
                        float[][][] batch = (float[][][]) rawOutput;

                        // 🔍 添加调试信息
                        System.out.println("实际数组维度: [" + batch.length + "]["
                                + batch[0].length + "][" + batch[0][0].length + "]");

                        // ✅ 根据实际维度判断数据格式
                        int dim0 = batch.length;        // 通常是 1
                        int dim1 = batch[0].length;     // 可能是 56 或 8400
                        int dim2 = batch[0][0].length;  // 可能是 8400 或 56
                        int debugCount = 0; // 用于控制调试输出数量

                        if (dim1 == 56 && dim2 > 1000) {
                            System.out.println("✅ 检测到格式: [batch][features][detections]");
                            float[][] detections = batch[0];


                            for (int i = 0; i < dim2; i++) {
                                float centerX = detections[0][i];
                                float centerY = detections[1][i];
                                float width = detections[2][i];
                                float height = detections[3][i];
                                float confidence = detections[4][i];

                                if (confidence > confThreshold) {
                                    float left = centerX - width / 2;
                                    float top = centerY - height / 2;

                                    // 提取关键点数据
                                    float[] kpts = new float[51];
                                    for (int j = 0; j < 51; j++) {
                                        kpts[j] = detections[5 + j][i];
                                    }

                                    // 🔍 调试输出
                                    if (debugCount < 3) {
                                        System.out.println(String.format("\n📊 检测框索引=%d, 置信度=%.4f", i, confidence));
                                        System.out.println("  前5个关键点数据:");
                                        for (int k = 0; k < 5; k++) {
                                            float kx = kpts[k * 3];
                                            float ky = kpts[k * 3 + 1];
                                            float visibility = kpts[k * 3 + 2];
                                            System.out.println(String.format("    关键点%d: x=%.2f, y=%.2f, vis=%.4f",
                                                    k, kx, ky, visibility));
                                        }
                                    }

                                    // ✅ 验证关键点有效性
                                    int validCoordCount = 0;
                                    int highVisibilityCount = 0;
                                    float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
                                    float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;

                                    for (int k = 0; k < 17; k++) {
                                        float kx = kpts[k * 3];
                                        float ky = kpts[k * 3 + 1];
                                        float visibility = kpts[k * 3 + 2];

                                        boolean coordsInRange = (kx >= 0 && kx <= 640 && ky >= 0 && ky <= 640);
                                        boolean notZero = (kx > 0.1 || ky > 0.1);

                                        if (coordsInRange && notZero) {
                                            validCoordCount++;

                                            if (visibility > 0.5) {
                                                highVisibilityCount++;
                                                // 只统计高可见性的关键点范围
                                                minX = Math.min(minX, kx);
                                                maxX = Math.max(maxX, kx);
                                                minY = Math.min(minY, ky);
                                                maxY = Math.max(maxY, ky);
                                            }
                                        }
                                    }

                                    // ⭐ 计算关键点分布范围
                                    float keypointWidth = (maxX == Float.MIN_VALUE) ? 0 : (maxX - minX);
                                    float keypointHeight = (maxY == Float.MIN_VALUE) ? 0 : (maxY - minY);

                                    // 计算关键点范围与边界框的比例
                                    float widthRatio = (width > 0) ? (keypointWidth / width) : 0;
                                    float heightRatio = (height > 0) ? (keypointHeight / height) : 0;

                                    if (debugCount < 3) {
                                        System.out.println(String.format("  统计: 有效坐标=%d/17, 高可见性=%d/17",
                                                validCoordCount, highVisibilityCount));
                                        System.out.println(String.format("  边界框: 宽=%.1f, 高=%.1f", width, height));
                                        System.out.println(String.format("  关键点范围: 宽=%.1f, 高=%.1f", keypointWidth, keypointHeight));
                                        System.out.println(String.format("  覆盖率: 宽度%.1f%%, 高度%.1f%%", widthRatio * 100, heightRatio * 100));
                                        debugCount++;
                                    }

                                    // ⭐⭐⭐ 关键过滤条件 ⭐⭐⭐
                                    boolean hasValidKeypoints = false;

                                    if (highVisibilityCount >= 5) {
                                        // 条件1: 关键点必须有合理的分布范围
                                        boolean hasReasonableSpread = (keypointWidth > 50 && keypointHeight > 100);

                                        // 条件2: 关键点范围应该覆盖边界框的大部分区域
                                        // 真实人体的关键点应该至少覆盖边界框50%的宽度和60%的高度
                                        boolean coversEnoughArea = (widthRatio > 0.5 && heightRatio > 0.6);

                                        hasValidKeypoints = hasReasonableSpread && coversEnoughArea;

                                        if (debugCount <= 3) {
                                            System.out.println(String.format("  验证结果: 合理分布=%s, 覆盖充分=%s, 最终=%s",
                                                    hasReasonableSpread, coversEnoughArea, hasValidKeypoints));
                                        }
                                    } else {
                                        if (debugCount <= 3) {
                                            System.out.println(String.format("  验证结果: 高可见性关键点不足(%d<5)", highVisibilityCount));
                                        }
                                    }

                                    if (hasValidKeypoints) {
                                        classIds.add(0);
                                        confidences.add(confidence);
                                        boxes2d.add(new Rect2d(left, top, width, height));
                                        keypoints.add(kpts);

                                        log.info("✅ 检测到有效人体: 置信度={}, 坐标=({},{},{},{}), 关键点覆盖={}x{}",
                                                confidence, left, top, width, height,
                                                String.format("%.0f%%", widthRatio * 100),
                                                String.format("%.0f%%", heightRatio * 100));
                                    } else {
                                        if (debugCount <= 3) {
                                            System.out.println(String.format("❌ 过滤掉检测框[%d]: 关键点分布异常", i));
                                        }
                                    }
                                }
                            }
                        } else if (dim1 > 1000 && dim2 == 56) {
                            // 格式: [1][8400][56] - 检测在前
                            System.out.println("⚠️ 检测到格式: [batch][detections][features]");

                            for (int i = 0; i < dim1; i++) {
                                float[] detection = batch[0][i];  // [56]

                                float centerX = detection[0];
                                float centerY = detection[1];
                                float width = detection[2];
                                float height = detection[3];
                                float confidence = detection[4];

                                if (confidence > confThreshold) {
                                    float left = centerX - width / 2;
                                    float top = centerY - height / 2;

                                    // 提取关键点
                                    float[] kpts = new float[51];
                                    System.arraycopy(detection, 5, kpts, 0, 51);

                                    // 🔍 验证关键点有效性
                                    boolean hasValidKeypoints = false;
                                    for (int k = 0; k < 17; k++) {
                                        float kx = kpts[k * 3];
                                        float ky = kpts[k * 3 + 1];
                                        float visibility = kpts[k * 3 + 2];
                                        if (visibility > 0.3 && kx > 0 && ky > 0) {
                                            hasValidKeypoints = true;
                                            break;
                                        }
                                    }

                                    if (hasValidKeypoints) {
                                        classIds.add(0);
                                        confidences.add(confidence);
                                        boxes2d.add(new Rect2d(left, top, width, height));
                                        keypoints.add(kpts);

                                        log.info("检测到人体: 置信度={}, 坐标=({},{},{},{})",
                                                confidence, left, top, width, height);
                                    } else {
                                        System.out.println("⚠️ 跳过无效检测 (置信度=" + confidence + "): 无有效关键点");
                                    }
                                }
                            }
                        } else {
                            System.err.println("❌ 未知的输出格式: [" + dim0 + "][" + dim1 + "][" + dim2 + "]");
                        }
                    }
                }
            }

            System.out.println("NMS前检测框数量: " + boxes2d.size());

            if (boxes2d.size() <= 0) {
                return "未检测到";
            }

            // 7. 应用非极大值抑制
            MatOfRect2d boxesMat = new MatOfRect2d();
            boxesMat.fromList(boxes2d);
            MatOfFloat confidencesMat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
            MatOfInt indices = new MatOfInt();

            if (!boxesMat.empty() && !confidencesMat.empty()) {
                Dnn.NMSBoxes(boxesMat, confidencesMat, confThreshold, nmsThreshold, indices);
            }

            int[] indicesArr = indices.toArray();
            System.out.println("NMS后检测框数量: " + indicesArr.length);

            // 8. 计算letterbox的缩放参数
            double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
            double dx = (640 - image.cols() * scale) / 2;
            double dy = (640 - image.rows() * scale) / 2;

            // 9. 绘制结果
            int colorIdx = 0;
            for (int idx : indicesArr) {
                Rect2d box = boxes2d.get(idx);
                int classId = classIds.get(idx);
                float conf = confidences.get(idx);
                float[] kpts = keypoints.get(idx);

                // 还原边界框到原图坐标
                double x = (box.x - dx) / scale;
                double y = (box.y - dy) / scale;
                double width = box.width / scale;
                double height = box.height / scale;

                // 确保坐标在图像范围内
                x = Math.max(0, Math.min(x, image.cols() - 1));
                y = Math.max(0, Math.min(y, image.rows() - 1));
                width = Math.min(width, image.cols() - x);
                height = Math.min(height, image.rows() - y);

                // 执行跌倒检测
                FallDetectionResult fallResult = detectFallOrStand(kpts, scale, dx, dy);
                System.out.println(String.format("人体检测: 置信度=%.3f, 状态=%s (%.2f), 原因=%s, 报警=%s",
                        conf, fallResult.getStatus(), fallResult.getConfidence(),
                        fallResult.getReason(), fallResult.isAlert()));

                // 绘制边界框
                Imgproc.rectangle(image,
                        new Point(x, y),
                        new Point(x + width, y + height),
                        CommonColors(colorIdx), 2);

                String label = "person: " + String.format("%.2f", conf);
                Imgproc.putText(image, label, new Point(x, y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(colorIdx), 2);

                // 绘制关键点和骨骼连接
                drawPoseKeypoints(image, kpts, scale, dx, dy);

                colorIdx++;
            }

            // 10. 保存结果
            String savepath = uploadpath + File.separator + "temp" + File.separator;
            if (StringUtils.isNotBlank(saveName)) {
                savepath += saveName + ".jpg";
            } else {
                saveName = System.currentTimeMillis() + "";
                savepath += saveName + ".jpg";
            }

            log.info("保存路径: {}", savepath);
            Imgcodecs.imwrite(savepath, image);

            Long endTime = System.currentTimeMillis();
            log.info("总耗时: {}ms", (endTime - startTime));

            return saveName + ".jpg";
        }
    }
    // 绘制姿态关键点和骨骼连接
    private static void drawPoseKeypoints(Mat image, float[] keypoints, double scale, double dx, double dy) {
        // COCO 17个关键点的定义
        String[] keypointNames = {
                "nose", "left_eye", "right_eye", "left_ear", "right_ear",
                "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
                "left_wrist", "right_wrist", "left_hip", "right_hip",
                "left_knee", "right_knee", "left_ankle", "right_ankle"
        };

        // 骨骼连接定义 (COCO格式)
        int[][] skeleton = {
                {16, 14}, {14, 12}, {17, 15}, {15, 13}, {12, 13},  // 腿部
                {6, 12}, {7, 13}, {6, 7}, {6, 8}, {7, 9},          // 躯干和手臂
                {8, 10}, {9, 11}, {2, 3}, {1, 2}, {1, 3},          // 手臂和脸部
                {2, 4}, {3, 5}, {4, 6}, {5, 7}                     // 脸部到肩膀
        };

        // 将关键点坐标还原到原图
        Point[] points = new Point[17];
        boolean[] visible = new boolean[17];

        for (int i = 0; i < 17; i++) {
            float kx = keypoints[i * 3];     // x坐标
            float ky = keypoints[i * 3 + 1]; // y坐标
            float kv = keypoints[i * 3 + 2]; // 可见性

            // 还原到原图坐标
            double px = (kx - dx) / scale;
            double py = (ky - dy) / scale;

            // 确保坐标在图像范围内
            px = Math.max(0, Math.min(px, image.cols() - 1));
            py = Math.max(0, Math.min(py, image.rows() - 1));

            points[i] = new Point(px, py);
            visible[i] = kv > 0.5; // 可见性阈值
        }

        // 绘制骨骼连接线
        Scalar lineColor = new Scalar(0, 255, 0); // 绿色
        for (int[] bone : skeleton) {
            int p1_idx = bone[0] - 1; // 转换为0-based索引
            int p2_idx = bone[1] - 1;

            if (p1_idx >= 0 && p1_idx < 17 && p2_idx >= 0 && p2_idx < 17 &&
                    visible[p1_idx] && visible[p2_idx]) {
                Imgproc.line(image, points[p1_idx], points[p2_idx], lineColor, 2);
            }
        }

        // 绘制关键点
        Scalar pointColor = new Scalar(0, 0, 255); // 红色
        for (int i = 0; i < 17; i++) {
            if (visible[i]) {
                Imgproc.circle(image, points[i], 3, pointColor, -1);

                // 绘制关键点标签
                // Imgproc.putText(image, keypointNames[i],
                //     new Point(points[i].x + 5, points[i].y - 5),
                //     Imgproc.FONT_HERSHEY_SIMPLEX, 0.3, pointColor, 1);
            }
        }
    }


    public static String SendPicOnnxYoloV11(String weight, String names, String picUrl,
                                            String saveName, String uploadpath, boolean useGpu) throws Exception {

        long startTime = System.currentTimeMillis();

        // 1. 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        int expectedClassCount = classes.size();

        // 2. 读取图像并预处理
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        Mat processedImage = letterboxResize(image, 640, 640);
        // ✅ 修复：BGR → RGB
        Imgproc.cvtColor(processedImage, processedImage, Imgproc.COLOR_BGR2RGB);

        // HWC -> CHW
        Mat blob = new Mat();
        processedImage.convertTo(blob, CvType.CV_32F, 1.0 / 255.0);
        List<Mat> channels = new ArrayList<>();
        Core.split(blob, channels);
        float[] inputData = new float[3 * 640 * 640];
        for (int c = 0; c < 3; c++) {
            float[] data = new float[640 * 640];
            channels.get(c).get(0, 0, data);
            System.arraycopy(data, 0, inputData, c * 640 * 640, 640 * 640);
        }

        // 3. 创建 ONNX Runtime 环境
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        if (useGpu) {
            options.addCUDA(); // 使用 GPU
        } else {
            log.info("使用onnx cpu");
            options.setInterOpNumThreads(4);   // 线程池并行
            options.setIntraOpNumThreads(8);   // 单算子内并行
            options.addCPU(true);  // 强制 CPU
        }

        try (OrtSession session = env.createSession(uploadpath + File.separator + weight, options)) {
            // 4. 构建输入
            long[] shape = new long[]{1, 3, 640, 640};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);
            Map<String, OnnxTensor> inputs = Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor);

            // 5. 推理
            List<Rect2d> boxes2d = new ArrayList<>();
            List<Float> confidences = new ArrayList<>();
            List<Integer> classIds = new ArrayList<>();

            try (OrtSession.Result results = session.run(inputs)) {
                for (Map.Entry<String, OnnxValue> entry : results) {
                    OnnxValue value = entry.getValue();
                    if (!(value instanceof OnnxTensor)) continue;
                    OnnxTensor tensor = (OnnxTensor) value;

                    // 获取输出张量的形状
                    long[] tensorShape = tensor.getInfo().getShape();
                    Object rawOutput = tensor.getValue();

                    if (rawOutput instanceof float[][][]) { // 常见 YOLOv5/11 输出
                        float[][][] batch = (float[][][]) rawOutput;

                        // 判断是否需要转置 (YOLOv11 格式: [1, 84, 8400])
                        boolean needTranspose = tensorShape.length == 3 &&
                                tensorShape[1] < tensorShape[2] &&
                                tensorShape[1] <= (expectedClassCount + 5);

                        for (float[][] detections : batch) {
                            if (needTranspose) {
                                // YOLOv11 转置处理: [84, 8400] -> 按列遍历
                                int numFeatures = (int) tensorShape[1]; // 84
                                int numDetections = (int) tensorShape[2]; // 8400
                                int numClasses = numFeatures - 4;

                                for (int i = 0; i < numDetections; i++) {
                                    float cx = detections[0][i];
                                    float cy = detections[1][i];
                                    float w = detections[2][i];
                                    float h = detections[3][i];

                                    // 找到最高分数的类别
                                    float maxScore = 0;
                                    int classId = 0;
                                    for (int c = 0; c < numClasses; c++) {
                                        if (detections[4 + c][i] > maxScore) {
                                            maxScore = detections[4 + c][i];
                                            classId = c;
                                        }
                                    }

                                    // 边界检查并添加检测结果
                                    if (maxScore > 0.35f && classId < expectedClassCount) {
                                        boxes2d.add(new Rect2d(cx - w / 2, cy - h / 2, w, h));
                                        confidences.add(maxScore);
                                        classIds.add(classId);
                                    }
                                }
                            } else {
                                // YOLOv5 原有处理逻辑: [25200, 85]
                                for (float[] det : detections) {
                                    int actualClassCount;
                                    boolean hasObjectness;
                                    if (det.length > 5) { // YOLOv5
                                        hasObjectness = true;
                                        actualClassCount = det.length - 5;
                                    } else { // YOLOv11 (2D输出)
                                        hasObjectness = false;
                                        actualClassCount = det.length - 4;
                                    }

                                    int classId = 0;
                                    float maxScore = 0;
                                    for (int i = hasObjectness ? 5 : 4; i < det.length; i++) {
                                        if (det[i] > maxScore) {
                                            maxScore = det[i];
                                            classId = i - (hasObjectness ? 5 : 4);
                                        }
                                    }

                                    float confidence = hasObjectness ? det[4] * maxScore : maxScore;

                                    // 边界检查并添加检测结果
                                    if (confidence > 0.35f && classId < expectedClassCount) {
                                        float cx = det[0], cy = det[1], w = det[2], h = det[3];
                                        boxes2d.add(new Rect2d(cx - w / 2, cy - h / 2, w, h));
                                        confidences.add(confidence);
                                        classIds.add(classId);
                                    }
                                }
                            }
                        }
                    } else if (rawOutput instanceof float[][]) {
                        // 可处理部分 YOLOv11 输出为 2D
                        float[][] detections = (float[][]) rawOutput;
                        for (float[] det : detections) {
                            int actualClassCount = det.length - 4;
                            float confidence = 0;
                            int classId = 0;
                            for (int i = 4; i < det.length; i++) {
                                if (det[i] > confidence) {
                                    confidence = det[i];
                                    classId = i - 4;
                                }
                            }

                            // 边界检查并添加检测结果
                            if (confidence > 0.35f && classId < expectedClassCount) {
                                float cx = det[0], cy = det[1], w = det[2], h = det[3];
                                boxes2d.add(new Rect2d(cx - w / 2, cy - h / 2, w, h));
                                confidences.add(confidence);
                                classIds.add(classId);
                            }
                        }
                    }
                }
            }

            // 6. NMS
            MatOfRect2d boxesMat = new MatOfRect2d();
            boxesMat.fromList(boxes2d);
            MatOfFloat confidencesMat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
            MatOfInt indices = new MatOfInt();
            if (!boxesMat.empty() && !confidencesMat.empty()) {
                Dnn.NMSBoxes(boxesMat, confidencesMat, 0.1f, 0.5f, indices);
            }

            int[] indicesArr = indices.toArray();

            // 7. 绘制检测框
            double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
            double dx = (640 - image.cols() * scale) / 2;
            double dy = (640 - image.rows() * scale) / 2;

            int colorIdx = 0;
            for (int idx : indicesArr) {
                Rect2d box = boxes2d.get(idx);
                int clsId = classIds.get(idx);
                float conf = confidences.get(idx);

                double x = (box.x - dx) / scale;
                double y = (box.y - dy) / scale;
                double width = box.width / scale;
                double height = box.height / scale;

                x = Math.max(0, Math.min(x, image.cols() - 1));
                y = Math.max(0, Math.min(y, image.rows() - 1));
                width = Math.min(width, image.cols() - x);
                height = Math.min(height, image.rows() - y);

                Imgproc.rectangle(image, new Point(x, y), new Point(x + width, y + height), CommonColors(colorIdx), 2);
                String label = classes.get(clsId) + ": " + String.format("%.2f", conf);
                Imgproc.putText(image, label, new Point(x, y - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(colorIdx), 2);
                colorIdx++;
            }

            // 8. 保存结果
            String savepath = uploadpath + File.separator + "temp" + File.separator;
            if (saveName != null && !saveName.isEmpty()) {
                savepath += saveName + ".jpg";
            } else {
                saveName = System.currentTimeMillis() + "";
                savepath += saveName + ".jpg";
            }
            Imgcodecs.imwrite(savepath, image);

            long endTime = System.currentTimeMillis();
            System.out.println("总耗时: " + (endTime - startTime) + "ms");
            return saveName + ".jpg";
        }
    }

    public static String SendPicYoloV11(String weight, String names, String picUrl, String saveName, String uploadpath,boolean gpuFlag) throws Exception {
        log.info(uploadpath);
        Long a = System.currentTimeMillis();

        // 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        int expectedClassCount = classes.size();
        // 加载YOLOv11模型
        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromONNX(uploadpath + File.separator + weight);

        if (gpuFlag) { //gpu
            net.setPreferableBackend(Dnn.DNN_BACKEND_CUDA);
            net.setPreferableTarget(Dnn.DNN_TARGET_CUDA);  //gpu推理
            log.info("[DNN推理规则：GPU]");
        } else {
            net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
            net.setPreferableTarget(Dnn.DNN_TARGET_CPU);  //cpu推理
            log.info("[DNN推理规则：CPU]");
        }
        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("原始图片尺寸: {}x{}", image.cols(), image.rows());

        // ==========关键修正1: 使用letterbox预处理==========
        Mat processedImage = letterboxResize(image, 640, 640);

        // 创建blob - 注意参数调整
        Mat blob = Dnn.blobFromImage(processedImage, 1.0 / 255.0, new Size(640, 640), new Scalar(0, 0, 0), true, false, CvType.CV_32F);
        net.setInput(blob);

        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = getOutputNames(net);
        net.forward(result, outBlobNames);

        if (result.isEmpty()) {
            System.err.println("Failed to get output from the model.");
            return "error";
        }

        // ==========关键修正2: 调整阈值==========
        float confThreshold = 0.3f;  // 降低置信度阈值
        float nmsThreshold = 0.2f;   // 调整NMS阈值

        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            long dim0 = output.size(0);
            long dim1 = output.size(1);
            long dim2 = output.size(2);

            log.info("========================================");
            log.info("输出维度: [" + dim0 + ", " + dim1 + ", " + dim2 + "]");

            Mat detectionMat;
            int rows, cols;
            String formatType = "UNKNOWN";

            // ========== 智能格式识别 ==========
            if (dims == 3) {
                if (dim1 < 100 && dim2 > 1000) {
                    // YOLOv11格式: [1, C, N] 其中 C=(4+类别数), N=锚点数
                    // 例如: [1, 6, 8400] 或 [1, 84, 8400]
                    formatType = "YOLOv11";
                    Mat reshaped = output.reshape(1, (int) dim1);
                    Mat transposed = new Mat();
                    Core.transpose(reshaped, transposed);
                    detectionMat = transposed;
                    rows = (int) dim2;  // 8400
                    cols = (int) dim1;  // 6 或 84
                } else if (dim1 > 1000 && dim2 < 100) {
                    // YOLOv5格式: [1, N, C] 其中 N=锚点数, C=(5+类别数)
                    // 例如: [1, 25200, 85]
                    formatType = "YOLOv5";
                    detectionMat = output.reshape(1, (int) dim1);
                    rows = (int) dim1;  // 25200
                    cols = (int) dim2;  // 85
                } else {
                    // 尝试自动推断
                    log.error("警告: 非标准维度,尝试自动处理");
                    detectionMat = output.reshape(1, (int) dim1);
                    rows = detectionMat.rows();
                    cols = detectionMat.cols();
                    formatType = "AUTO";
                }
            } else {
                log.error("不支持的维度数: " + dims);
                continue;
            }

            log.info("识别格式: " + formatType);
            log.info("处理后矩阵: " + rows + " x " + cols);

            // ========== 根据格式动态计算类别数 ==========
            int actualClassCount;
            boolean hasObjectness;

            if (formatType.equals("YOLOv11")) {
                // YOLOv11: [x, y, w, h, class_scores...]
                hasObjectness = false;
                actualClassCount = cols - 4;
            } else if (formatType.equals("YOLOv5")) {
                // YOLOv5: [x, y, w, h, objectness, class_scores...]
                hasObjectness = true;
                actualClassCount = cols - 5;
            } else {
                // 自动判断: 如果第5列数值普遍在[0,1]且较大,可能是objectness
                // 这里简化处理,假设列数>5就是YOLOv5格式
                if (cols > 5) {
                    hasObjectness = true;
                    actualClassCount = cols - 5;
                } else {
                    hasObjectness = false;
                    actualClassCount = cols - 4;
                }
            }

            log.info("检测到类别数: " + actualClassCount + " (期望: " + expectedClassCount + ")");
            log.info("是否包含objectness: " + hasObjectness);

            // 验证有效性
            if (actualClassCount < 1) {
                log.info("错误: 计算出的类别数无效 (" + actualClassCount + "),跳过此输出");
                continue;
            }


            // ========== 统一的置信度计算 ==========
            int scoresStartCol = hasObjectness ? 5 : 4;  // 类别分数起始列

            for (int i = 0; i < rows; i++) {
                Mat detection = detectionMat.row(i);

                float confidence;
                int classId;

                // 提取类别分数
                Mat scores = detection.colRange(scoresStartCol, cols);
                Core.MinMaxLocResult minMaxResult = Core.minMaxLoc(scores);
                float maxClassScore = (float) minMaxResult.maxVal;
                classId = (int) minMaxResult.maxLoc.x;

                if (hasObjectness) {
                    // YOLOv5: confidence = objectness × max_class_score
                    float objectness = (float) detection.get(0, 4)[0];
                    confidence = objectness * maxClassScore;
                } else {
                    // YOLOv11: confidence = max_class_score
                    confidence = maxClassScore;
                }

                if (confidence > confThreshold) {
                    float centerX = (float) detection.get(0, 0)[0];
                    float centerY = (float) detection.get(0, 1)[0];
                    float width = (float) detection.get(0, 2)[0];
                    float height = (float) detection.get(0, 3)[0];

                    float left = centerX - width / 2;
                    float top = centerY - height / 2;

                    // 验证类别ID有效性
                    if (classId < 0 || classId >= actualClassCount) {
                        System.err.println("警告: 无效的类别ID " + classId + ",跳过");
                        continue;
                    }

                    classIds.add(classId);
                    confidences.add(confidence);
                    boxes2d.add(new Rect2d(left, top, width, height));


                }
            }
        }

        log.info("NMS前检测框数量: " + boxes2d.size());

        // 应用非极大值抑制
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();

        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }

        int[] indices_arr = indices.toArray();
        log.info("NMS后检测框数量: " + indices_arr.length);

        // ==========关键修正4: 坐标还原==========
        // 计算letterbox的缩放参数
        double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
        double dx = (640 - image.cols() * scale) / 2;
        double dy = (640 - image.rows() * scale) / 2;

        // 绘制结果
        int c = 0;
        for (int idx : indices_arr) {
            Rect2d box = boxes2d.get(idx);
            int classId = classIds.get(idx);
            float conf = confidences.get(idx);

            // 还原到原图坐标
            double x = (box.x - dx) / scale;
            double y = (box.y - dy) / scale;
            double width = box.width / scale;
            double height = box.height / scale;

            // 确保坐标在图像范围内
            x = Math.max(0, Math.min(x, image.cols() - 1));
            y = Math.max(0, Math.min(y, image.rows() - 1));
            width = Math.min(width, image.cols() - x);
            height = Math.min(height, image.rows() - y);

            System.out.println(String.format("最终检测框: %s %.3f [%.1f,%.1f,%.1f,%.1f]",
                    classes.get(classId), conf, x, y, width, height));

            Imgproc.rectangle(image,
                    new Point(x, y),
                    new Point(x + width, y + height),
                    CommonColors(c), 2);

            String label = classes.get(classId) + ": " + String.format("%.2f", conf);
            Imgproc.putText(image, label, new Point(x, y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 2);
            c++;
        }

        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }

        log.info("保存路径: {}", savepath);
        Imgcodecs.imwrite(savepath, image);

        Long b = System.currentTimeMillis();
        log.info("总耗时: {}ms", (b - a));

        return saveName + ".jpg";
    }

    // ==========新增letterbox预处理方法==========
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

    // Sigmoid函数
    private static float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    /***
     * AI模型嵌套模型
     * 需要绝对路径
     * 输入图片
     */
    public static String SendPicYoloV5Car(String weight, String names, String picUrl, String saveName, String uploadpath) throws Exception {
        log.info(uploadpath);
        Long a = System.currentTimeMillis();
        // 加载类别名称
        List<String> classes = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        // 加载YOLOv5模型

        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromONNX(uploadpath + File.separator + weight);
        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        log.info("图片地址{}", image);

        Mat blob = Dnn.blobFromImage(image, 1 / 255.0, new Size(640, 640), new Scalar(0), true, false);
        net.setInput(blob);

        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = getOutputNames(net);
        net.forward(result, outBlobNames);
        System.out.println(Arrays.asList(outBlobNames));
        if (result.isEmpty()) {
            System.err.println("Failed to get output from the model.");
            return "error";
        }


        float confThreshold = 0.3f;
        float nmsThreshold = 0.4f;

        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        for (Mat output : result) {
            int dims = output.dims();
            int index = (int) output.size(0);
            int rows = (int) output.size(1);
            int cols = (int) output.size(2);
            // Dims: 3, Rows: 25200, Cols: 8 row,Mat [ 1*25200*8*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x28dce2da990, dataAddr=0x28dd0ebc640 ]index:1
            System.out.println("Dims: " + dims + ", Rows: " + rows + ", Cols: " + cols + " row," + output.row(0) + "index:" + index);
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

// 应用非极大值抑制
        MatOfRect2d boxes_mat = new MatOfRect2d();
        boxes_mat.fromList(boxes2d);

        MatOfFloat confidences_mat = new MatOfFloat(Converters.vector_float_to_Mat(confidences));
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        if (!boxes_mat.empty() && !confidences_mat.empty()) {
            System.out.println("不为空");
            Dnn.NMSBoxes(boxes_mat, confidences_mat, confThreshold, nmsThreshold, indices);
        }
        int c = 0;
        int[] indices_arr = indices.toArray();
        for (int idx : indices_arr) {
            Rect2d box = boxes2d.get(idx);

            int classId = classIds.get(idx);
            float conf = confidences.get(idx);
            double x = box.x;
            double y = box.y;
            double width = box.width * ((double) image.cols() / 640);
            double height = box.height * ((double) image.rows() / 640);
            double xzb = x * ((double) image.cols() / 640);
            double yzb = y * ((double) image.rows() / 640);
            System.out.println("绘制1" + "x:" + x + "y:" + y + "");
            System.out.println("绘制1" + "width:" + width + "height:" + height + "");
            System.out.println(" image.cols()" + Double.valueOf((double) image.cols() / 640));
            System.out.println(" image.rows()" + Double.valueOf((double) image.rows() / 640));

            Rect rect = new Rect((int) Math.round(xzb), (int) Math.round(yzb), (int) Math.round(width), (int) Math.round(height));
            Mat plaateimage = Imgcodecs.imread(uploadpath + File.separator + picUrl);
            Mat plateMat = new Mat(plaateimage, rect);


            String plateNumber = extractPlateNumber(plateMat);
            String plateColor = recognizePlateColor(plateMat);

            System.out.println("车牌号码: " + plateNumber);
            System.out.println("车牌颜色: " + plateColor);

            // 在原图上绘制检测结果
            Imgproc.rectangle(image, rect, new Scalar(0, 255, 0), 2);
            Imgproc.putText(image, plateNumber, new Point(rect.x, rect.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 255, 0), 2);


            c++;
        }
        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }
        log.info(savepath);
        Imgcodecs.imwrite(savepath, image);
        Long b = System.currentTimeMillis();
        log.info("消耗时间：" + (b - a));
        return saveName + ".jpg";
    }

    private static String recognizePlateColor(Mat plateMat) {
        Mat hsvMat = new Mat();
        Imgproc.cvtColor(plateMat, hsvMat, Imgproc.COLOR_BGR2HSV);

        Scalar blueMin = new Scalar(100, 100, 100);
        Scalar blueMax = new Scalar(140, 255, 255);
        Scalar yellowMin = new Scalar(20, 100, 100);
        Scalar yellowMax = new Scalar(30, 255, 255);
        Scalar greenMin = new Scalar(40, 100, 100);
        Scalar greenMax = new Scalar(80, 255, 255);
        Scalar whiteMin = new Scalar(0, 0, 200);
        Scalar whiteMax = new Scalar(180, 30, 255);

        Mat blueMask = new Mat();
        Mat yellowMask = new Mat();
        Mat greenMask = new Mat();
        Mat whiteMask = new Mat();

        Core.inRange(hsvMat, blueMin, blueMax, blueMask);
        Core.inRange(hsvMat, yellowMin, yellowMax, yellowMask);
        Core.inRange(hsvMat, greenMin, greenMax, greenMask);
        Core.inRange(hsvMat, whiteMin, whiteMax, whiteMask);

        int bluePixels = Core.countNonZero(blueMask);
        int yellowPixels = Core.countNonZero(yellowMask);
        int greenPixels = Core.countNonZero(greenMask);
        int whitePixels = Core.countNonZero(whiteMask);

        int maxPixels = Math.max(Math.max(bluePixels, yellowPixels), Math.max(greenPixels, whitePixels));

        if (maxPixels == bluePixels) return "蓝色";
        if (maxPixels == yellowPixels) return "黄色";
        if (maxPixels == greenPixels) return "绿色";
        if (maxPixels == whitePixels) return "白色";
        return "未知";
    }

    //读取文字
    private static String extractPlateNumber(Mat plateMat) {
        // 预处理
        Mat gray = new Mat();
        Mat binary = new Mat();
        Imgproc.cvtColor(plateMat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);

        // 查找轮廓
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // 筛选可能的字符轮廓
        List<Rect> charBounds = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            if (isValidCharSize(rect, binary.size())) {
                charBounds.add(rect);
            }
        }

        // 按x坐标排序
        charBounds.sort(Comparator.comparingInt(r -> r.x));
        CharRecognizer recognizer = new CharRecognizer();
        // 识别每个字符
        StringBuilder plateNumber = new StringBuilder();
        int a = 0;
        for (Rect charRect : charBounds) {
            Mat charMat = new Mat(binary, charRect);
            char recognizedChar;
            if (a == 0) {
                recognizedChar = recognizer.recognizeChineseChar(charMat);
            } else {
                recognizedChar = recognizer.recognizeChar(charMat);
            }
            a++;

            plateNumber.append(recognizedChar);
        }

        return plateNumber.toString();
    }


    private static boolean isValidCharSize(Rect rect, Size plateSize) {
        double charAspectRatio = (double) rect.height / rect.width;
        double charHeightRatio = (double) rect.height / plateSize.height;
        return charAspectRatio > 1.0 && charAspectRatio < 4.0 &&
                charHeightRatio > 0.4 && charHeightRatio < 0.9;
    }

    private static List<String> getOutputNames(Net net) {
        List<String> names = new ArrayList<>();
        List<Integer> outLayers = net.getUnconnectedOutLayers().toList();
        List<String> layersNames = net.getLayerNames();
        outLayers.forEach(i -> names.add(layersNames.get(i - 1)));
        return names;
    }

    /***
     * AI模型嵌套模型
     * 需要绝对路径
     * 输入图片
     */
    public static String SendPicYoloV3(String weight, String cfg, String names, String picUrl, String saveName, String uploadpath) throws Exception {
        log.info(uploadpath);
        Long a = System.currentTimeMillis();
        // 加载类别名称
        List<String> classNames = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        // 加载YOLOv3模型
        log.info("cfg地址{}", uploadpath + File.separator + cfg);
        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromDarknet(uploadpath + File.separator + cfg, uploadpath + File.separator + weight);
//        net.setPreferableBackend(Dnn.DNN_BACKEND_CUDA);
//        net.setPreferableBackend(Dnn.DNN_TARGET_CUDA);
        net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        net.setPreferableTarget(Dnn.DNN_TARGET_CPU);
        // 读取输入图像
        log.info("图片地址{}", uploadpath + File.separator + picUrl);
        Mat image = Imgcodecs.imread(uploadpath + File.separator + picUrl);
        // 将图像传递给模型进行目标检测
        Mat blob = Dnn.blobFromImage(image, 1.0 / 255, new Size(416, 416), new Scalar(0), true, false);

        net.setInput(blob);
        // 将图像传递给模型进行目标检测
        List<Mat> result = new ArrayList<>();
        List<String> outBlobNames = net.getUnconnectedOutLayersNames();
        net.forward(result, outBlobNames);

        // 处理检测结果
        float confThreshold = 0.5f;
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
            return "error";
        }
        int[] indicesArray = indices.toArray();
        // 获取保留的边界框

        log.info(confidences.size() + "类别下标啊" + indicesArray.length);
        // 在图像上绘制保留的边界框
        int c = 0;
        for (int idx : indicesArray) {
            Rect2d box = boundingBoxes.get(idx);

            System.out.println("绘制111111" + "x:" + box.x + "y:" + box.y + "");
            System.out.println("绘制11111111" + "width:" + box.width + "y:" + box.height + "");
            Imgproc.rectangle(image, new Point(box.x, box.y), new Point(box.x + box.width, box.y + box.height), CommonColors(c), 2);
            // 添加类别标签
            log.info("当前有多少" + confidences.get(idx));
            Integer ab = classIds.get(idx);
            log.info("类别下标" + ab);
            //  AIModelYolo3.addChineseText(image, caption,new Point(box.x, box.y - 5));
            Imgproc.putText(image, classNames.get(ab), new Point(box.x, box.y - 5), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 1);
            c++;
        }
        String savepath = uploadpath + File.separator + "temp" + File.separator;

        if (StringUtils.isNotBlank(saveName)) {
            savepath += saveName + ".jpg";
        } else {
            saveName = System.currentTimeMillis() + "";
            savepath += saveName + ".jpg";
        }
        log.info(savepath);
        Imgcodecs.imwrite(savepath, image);
        Long b = System.currentTimeMillis();
        log.info("消耗时间：" + (b - a));
        return saveName + ".jpg";
    }

    /***
     * AI识别输出图片或者视频帧
     * @param weight
     * @param cfg
     * @param names
     * @param videoUrl
     * @param uploadpath
     * @return
     * @throws Exception
     */
    public String SendVideoYoloV3(String weight, String cfg, String names, String videoUrl, String uploadpath) throws Exception {
        Long a = System.currentTimeMillis();

        // 加载类别名称
        List<String> classNames = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        // 加载YOLOv3模型
        log.info("cfg地址{}", uploadpath + File.separator + cfg);
        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromDarknet(uploadpath + File.separator + cfg, uploadpath + File.separator + weight);
        net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        net.setPreferableTarget(Dnn.DNN_TARGET_CPU);

//        net.setPreferableBackend(Dnn.DNN_BACKEND_CUDA);
//        net.setPreferableTarget(Dnn.DNN_TARGET_CUDA);

        String savepath = uploadpath + File.separator + a + File.separator;
        File file = new File(savepath);
        if (!file.exists()) {
            file.mkdirs();// 创建文件根目录
        }
        VideoCapture videoCapture = new VideoCapture(videoUrl);
        if (!videoCapture.isOpened()) {
            log.info("未能正确打开视频");
            return "eror";
        }

        // 设置输出视频文件参数
        int frameWidth = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int frameHeight = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        //   VideoWriter videoWriter = new VideoWriter("F:\\JAVAAI\\output.mp4", VideoCodec.MPEG4, 30, new Size(frameWidth, frameHeight), true);
        VideoWriter videoWriter = new VideoWriter("F:\\JAVAAI\\output.mp4", VideoWriter.fourcc('X', '2', '6', '4'), 30, new Size(frameWidth, frameHeight), true);

        Mat frame = new Mat();

        while (videoCapture.read(frame)) {
            Long b = System.currentTimeMillis();
            // 将图像传递给模型进行目标检测
            Mat blob = Dnn.blobFromImage(frame, 1.0 / 255, new Size(416, 416), new Scalar(0), true, false);
            net.setInput(blob);
            // 将图像传递给模型进行目标检测
            List<Mat> result = new ArrayList<>();
            List<String> outBlobNames = net.getUnconnectedOutLayersNames();
            net.forward(result, outBlobNames);

            // 处理检测结果
            float confThreshold = 0.5f;
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
                        double centerX = row.get(0, 0)[0] * frame.cols();
                        double centerY = row.get(0, 1)[0] * frame.rows();
                        double width = row.get(0, 2)[0] * frame.cols();
                        double height = row.get(0, 3)[0] * frame.rows();
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
                return "error";
            }
            int[] indicesArray = indices.toArray();
            // 获取保留的边界框

            log.info(confidences.size() + "类别下标啊" + indicesArray.length);
            // 在图像上绘制保留的边界框
            int c = 0;
            for (int idx : indicesArray) {
                Rect2d box = boundingBoxes.get(idx);
                Imgproc.rectangle(frame, new Point(box.x, box.y), new Point(box.x + box.width, box.y + box.height), CommonColors(c), 2);
                // 添加类别标签
                log.info("当前有多少" + confidences.get(idx));
                Integer ab = classIds.get(idx);
                log.info("类别下标" + ab);
                //  AIModelYolo3.addChineseText(image, caption,new Point(box.x, box.y - 5));
                Imgproc.putText(frame, classNames.get(ab), new Point(box.x, box.y - 5), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 1);
                c++;
                videoWriter.write(frame);
            }

            String saveName = "";
            if (StringUtils.isNotBlank(saveName)) {
                saveName = savepath + saveName + ".jpg";
            } else {
                saveName = savepath + System.currentTimeMillis() + ".jpg";
            }

            // Imgcodecs.imwrite(saveName, frame);


            //       imshow("YOLOv3 Detection", frame);
            long d = (b - a) / 1000;
            if (d > 60) {
                break;
            }
            log.info(saveName + "{}", d);
        }
        // Release resources
        videoCapture.release();
        videoWriter.release();
        return "";

    }


    /***
     * 测试合成是频liu
     *
     */
    public void SendTestVideo() {
        VideoCapture capture = new VideoCapture();
        long a = System.currentTimeMillis();
        capture.open("http://218.92.168.230:8888/LL/34020000001180000002_34020000001310000005.live.mp4");

        // Check if the capture is opened successfully
        if (!capture.isOpened()) {
            System.out.println("Error: Could not open video stream");
            return;
        }

        // Get video properties
        double frameWidth = capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        double frameHeight = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        double fps = capture.get(Videoio.CAP_PROP_FPS);

        // Create VideoWriter object to write frames to a file
        String outputFileName = "F:\\JAVAAI\\output_video.mp4"; // Change this to your desired output file name
        int fourcc = VideoWriter.fourcc('X', '2', '6', '4');
        VideoWriter writer = new VideoWriter(outputFileName, fourcc, fps, new Size((int) frameWidth, (int) frameHeight));

        // Process frames from the video stream
        Mat frame = new Mat();
        while (capture.read(frame)) {
            long b = System.currentTimeMillis();
            // Write the frame to the output file
            long c = (b - a) / 1000;
            log.info("运行中{}", c);
            writer.write(frame);
            if (c > 60) {
                break;
            }

        }

        // Release resources
        capture.release();
        writer.release();
    }

    /**
     * 获取坐标
     *
     * @return
     */
    public String SendVideoLocalhostYoloV3(String userId, String weight, String cfg, String names, String videoUrl, String uploadpath, WebSocket webSocket, RedisUtil redisUtil) throws Exception {
        Long a = System.currentTimeMillis();

        // 加载类别名称   人 1
        List<String> classNames = Files.readAllLines(Paths.get(uploadpath + File.separator + names));
        // 加载YOLOv3模型
        log.info("cfg地址{}", uploadpath + File.separator + cfg);
        log.info("weight地址{}", uploadpath + File.separator + weight);
        Net net = Dnn.readNetFromDarknet(uploadpath + File.separator + cfg, uploadpath + File.separator + weight);
        //    onnx  pt darknet
        net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        net.setPreferableTarget(Dnn.DNN_TARGET_CPU);

        VideoCapture videoCapture = new VideoCapture(videoUrl);
        if (!videoCapture.isOpened()) {
            log.info("未能正确打开视频");
            return "eror";
        }
        double fps = videoCapture.get(Videoio.CAP_PROP_FPS);
        // 计算每帧的时间消耗（单位：毫秒）
        double frameTime = 1000 / fps; // 单位为毫秒
        log.info("");


        log.info("当前视频帧数{}", fps);
        Mat frame = new Mat();
        int k = 0;
        while (videoCapture.read(frame)) {


            Long startTime = System.currentTimeMillis();
            Boolean flag = (Boolean) redisUtil.get(videoUrl + "" + userId);
            log.info("获取的当前识别信息{}{}{}", videoUrl, userId, flag);
            if (!flag) {
                videoCapture.release();
                break;
            }
            // 将图像传递给模型进行目标检测 1280*1080  720p
            Mat blob = Dnn.blobFromImage(frame, 1.0 / 255, new Size(416, 416), new Scalar(0), true, false);
            net.setInput(blob);
            // 将图像传递给模型进行目标检测
            List<Mat> result = new ArrayList<>();
            List<String> outBlobNames = net.getUnconnectedOutLayersNames();
            net.forward(result, outBlobNames);

            // 处理检测结果
            float confThreshold = 0.5f; //80 70 60%   （1000*1000） 3-5天  50%
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
                        double centerX = row.get(0, 0)[0] * frame.cols(); // xy
                        double centerY = row.get(0, 1)[0] * frame.rows();
                        double width = row.get(0, 2)[0] * frame.cols();
                        double height = row.get(0, 3)[0] * frame.rows();
                        double left = centerX - width / 2;
                        double top = centerY - height / 2;

                        // 绘制边界框
                        Rect2d rect = new Rect2d(left, top, width, height);
                        boundingBoxes.add(rect);
                        confidences.add((float) confidence);

                    }
                }
            }

            // 执行非最大抑制，消除重复的边界框 第一高度
            MatOfRect2d boxes = new MatOfRect2d(boundingBoxes.toArray(new Rect2d[0]));
            MatOfFloat confidencesMat = new MatOfFloat();
            confidencesMat.fromList(confidences);
            MatOfInt indices = new MatOfInt();
            Dnn.NMSBoxes(boxes, confidencesMat, confThreshold, 0.4f, indices);
            if (indices.empty()) {
                log.info("未识别到视频内容");
                continue;
            }
            int[] indicesArray = indices.toArray();
            //   // 获取保留的边界框

            log.info(confidences.size() + "类别下标啊" + indicesArray.length);
            // 在图像上绘制保留的边界框
            int c = 0;
            JSONObject bja = new JSONObject();
            List<JSONObject> jsonlist = new ArrayList<>();
            for (int idx : indicesArray) {
                Rect2d box = boundingBoxes.get(idx);
                //    Imgproc.rectangle(frame, new Point(box.x, box.y), new Point(box.x + box.width, box.y + box.height),CommonColors(c), 2);
                // 添加类别标签
                log.info("当前有多少" + confidences.get(idx));
                Integer ab = classIds.get(idx);
                log.info("类别下标" + ab);
                //  AIModelYolo3.addChineseText(image, caption,new Point(box.x, box.y - 5))
                log.info("Detected object at: (" + box.x + ", " + box.y + "),width: (" + box.width + ", " + box.height + ")");
                //   Imgproc.putText(frame, classNames.get(ab), new Point(box.x, box.y - 5), Core.FONT_HERSHEY_SIMPLEX, 0.5, CommonColors(c), 1);

                bja.put("cmd", "video");
                JSONObject bj = new JSONObject();
                bj.put("x", box.x);
                bj.put("y", box.y);
                bj.put("width", box.width);
                bj.put("height", box.height);
                bj.put("url", videoUrl);
                bj.put("name", classNames.get(ab));
                bj.put("color", CommonColorsVue(c));
                jsonlist.add(bj);
                bja.put("list", jsonlist);
                c++;

            }


            // 计算跳过的帧数（根据所需的时间消耗）
            webSocket.sendMessage(bja.toJSONString());

            Long b = System.currentTimeMillis();
            long consumingTime = 0;
            if (k == 0) {
                consumingTime = (b - a);
                k++;
            } else {
                consumingTime = (b - startTime);
            }
            int framesToSkip = (int) (consumingTime / frameTime);

            log.warn("耗时时间{},跳过帧数{}", b - startTime, framesToSkip);
            // 跳过计算出的帧数
//            for (int i = 0; i < framesToSkip; i++) {
//                videoCapture.grab(); // 跳过帧
//            }

            Mat newmat = new Mat();
            VideoCapture videoCapture2 = new VideoCapture(videoUrl);
            videoCapture2.read(newmat);
            videoCapture.release();
            long timestamp2 = (long) videoCapture2.get(Videoio.CAP_PROP_POS_MSEC);
            videoCapture = videoCapture2;


            long timestamp = (long) videoCapture.get(Videoio.CAP_PROP_POS_MSEC);
            log.warn("当前帧时间" + (millisecondsToHours(timestamp)));
            log.warn("最新帧时间" + (millisecondsToHours(timestamp2)));
        }

        return "";

    }

    /**
     * 多线程处理视频帧
     *
     * @param
     * @return
     */
    public String SendVideoLocalhostYoloV3Thread(String userId, String weight, String cfg, String names, String videoUrl, String uploadpath, WebSocket webSocket, RedisUtil redisUtil, RedisTemplate redisTemplate) throws Exception {
        Long a = System.currentTimeMillis();


        // 加载YOLOv3模型
        log.info("cfg地址{}", uploadpath + File.separator + cfg);
        log.info("weight地址{}", uploadpath + File.separator + weight);
        log.info("names{}", uploadpath + File.separator + names);
        // 计算每帧的时间消耗（单位：毫秒）
        int maxIdleThreads = Runtime.getRuntime().availableProcessors();
        log.info("当前主机最大空闲线程数：" + maxIdleThreads);
        // 创建线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        VideoSendReadCfg.StartTime = 0;
        log.info("videoUrl：" + videoUrl);
        // 提交多个任务到线程池
        for (int i = 0; i < 3; i++) {
            //效果延迟了三秒
            //     executor.submit(new VideoFrameReader(videoUrl,uploadpath+ File.separator +weight,uploadpath+ File.separator +cfg,uploadpath+ File.separator +names,redisUtil,webSocket,userId,i,redisTemplate));
            if (i == 0) {
                executor.submit(new VideoRead(videoUrl, redisTemplate, userId));
            } else if (i == 1) {
                executor.submit(new VideoReadInfo(videoUrl, redisTemplate, userId, uploadpath + File.separator + names, uploadpath + File.separator + cfg, uploadpath + File.separator + weight, webSocket));
            } else {
                executor.submit(new VideoReadtest(videoUrl, redisTemplate, userId));
            }
            Thread.sleep(500);
        }
        // 关闭线程池
        executor.shutdown();


        return "";

    }


    /**
     * 多线程处理视频帧
     *
     * @param
     * @return
     */
    public String SendVideoLocalhostYoloV5Thread(TabAudioDevice tabAudioDevice, TabAiModelBund tabAiModelBund, String userId, String weight, String cfg, String names, String videoUrl, String uploadpath, WebSocket webSocket, RedisUtil redisUtil, RedisTemplate redisTemplate) throws Exception {
        Long a = System.currentTimeMillis();


        // 加载v5/v8模型
        log.info("cfg地址{}", uploadpath + File.separator + cfg);
        log.info("weight地址{}", uploadpath + File.separator + weight);
        log.info("names{}", uploadpath + File.separator + names);
        // 计算每帧的时间消耗（单位：毫秒）
        int maxIdleThreads = Runtime.getRuntime().availableProcessors();
        log.info("当前主机最大空闲线程数：" + maxIdleThreads);
        // 创建线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        VideoSendReadCfg.StartTime = 0;
        log.info("videoUrl：" + videoUrl);


        // 提交多个任务到线程池
        for (int i = 0; i < 3; i++) {
            //效果延迟了三秒
            //     executor.submit(new VideoFrameReader(videoUrl,uploadpath+ File.separator +weight,uploadpath+ File.separator +cfg,uploadpath+ File.separator +names,redisUtil,webSocket,userId,i,redisTemplate));
            if (i == 0) {
                executor.submit(new VideoReadV5(videoUrl, redisTemplate, userId));
            } else if (i == 1) {
                executor.submit(new VideoReadInfoV5(tabAudioDevice, tabAiModelBund, videoUrl, redisTemplate, userId, uploadpath + File.separator + names, uploadpath + File.separator + cfg, uploadpath + File.separator + weight, webSocket));
            } else {
                executor.submit(new VideoReadtestV5(videoUrl, redisTemplate, userId));
            }
            Thread.sleep(3000);
        }
        // 关闭线程池
        // executor.shutdown();


        return "";

    }


    /**
     * 多线程处理视频帧区域入侵
     *
     * @param
     * @return
     */
    public String SendVideoLocalhostYoloV5ThreadVideoUtil(TabVideoUtil tabVideoUtil, String weight, String cfg, String names, String videoUrl, String uploadpath, WebSocket webSocket, RedisUtil redisUtil, RedisTemplate redisTemplate) throws Exception {
        Long a = System.currentTimeMillis();


        // 加载v5/v8模型
        log.info("cfg地址{}", uploadpath + File.separator + cfg);
        log.info("weight地址{}", uploadpath + File.separator + weight);
        log.info("names{}", uploadpath + File.separator + names);
        // 计算每帧的时间消耗（单位：毫秒）
        int maxIdleThreads = Runtime.getRuntime().availableProcessors();
        log.info("当前主机最大空闲线程数：" + maxIdleThreads);
        // 创建线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        VideoSendReadCfg.StartTime = 0;
        log.info("videoUrl：" + videoUrl);
        // 提交多个任务到线程池
        for (int i = 0; i < 3; i++) {
            //效果延迟了三秒
            //     executor.submit(new VideoFrameReader(videoUrl,uploadpath+ File.separator +weight,uploadpath+ File.separator +cfg,uploadpath+ File.separator +names,redisUtil,webSocket,userId,i,redisTemplate));
            if (i == 0) {
                executor.submit(new VideoReadV5Util(tabVideoUtil, videoUrl, redisTemplate));
            } else if (i == 1) {
                executor.submit(new VideoReadInfoV5Util(tabVideoUtil, videoUrl, redisTemplate, uploadpath + File.separator + names, uploadpath + File.separator + cfg, uploadpath + File.separator + weight, webSocket));
            } else {
                executor.submit(new VideoReadtestV5Util(tabVideoUtil, videoUrl, redisTemplate));
            }
            Thread.sleep(1000);
        }
        // 关闭线程池
        // executor.shutdown();


        return "";

    }

    /***
     * 带线程推送
     *
     * @return
     */
    public void SendPicThread(RedisTemplate redisTemplate, String uploadPath) {
        List<PushInfo> pushA = (List<PushInfo>) redisTemplate.opsForValue().get("sendPush");
        ExecutorService executor = Executors.newCachedThreadPool();
        for (PushInfo pushInfo : pushA) {
            log.info("当前属性内容{}", pushInfo.getName());

            executor.submit(new VideoReadPic(pushInfo, uploadPath, redisTemplate));
        }


    }


    /**
     * 图片转base64
     *
     * @param imagePath
     * @return
     */
    public static String base64Image(String imagePath) {
        try {
            // 读取图片文件
            File file = new File(imagePath);
            byte[] bytesArray = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            fis.read(bytesArray); // 读取文件内容到字节数组
            fis.close();

            // 将字节数组编码为Base64字符串
            String base64String = Base64.getEncoder().encodeToString(bytesArray);

            return base64String;
        } catch (IOException e) {
            e.printStackTrace();
            return "图片解析错误";
        }

    }

    public static String millisecondsToHours(long milliseconds) {
        // 将毫秒转换为小时、分钟和秒
        long hours = milliseconds / (1000 * 60 * 60);
        long minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = ((milliseconds % (1000 * 60 * 60)) % (1000 * 60)) / 1000;

        // 构造结果字符串
        String result = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        return result;
    }


    public static Mat addChineseText(Mat images, String text, Point position, Scalar scalar) {
        BufferedImage bufferedImage = matToBufferedImage(images);
        Graphics graphics = bufferedImage.getGraphics();

        // 设置中文文本字体
        Font chineseFont = new Font("微软雅黑", Font.PLAIN, 26);
        graphics.setFont(chineseFont);
        Color awtColor = convertScalarToColor(scalar);
        // 设置文本颜色
        graphics.setColor(awtColor);

        // 在指定位置绘制中文文本
        graphics.drawString(text, (int) position.x, (int) position.y);

        // 将修改后的图像转换回OpenCV的Mat对象
        return bufferedImageToMat(bufferedImage);
    }

    private static Color convertScalarToColor(Scalar scalarColor) {
        double[] rgb = scalarColor.val;
        int r = (int) rgb[2];
        int g = (int) rgb[1];
        int b = (int) rgb[0];
        return new Color(r, g, b);
    }

    // 将Mat对象转换为BufferedImage对象
    public static BufferedImage matToBufferedImage(Mat matrix) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (matrix.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = matrix.channels() * matrix.cols() * matrix.rows();
        byte[] buffer = new byte[bufferSize];
        matrix.get(0, 0, buffer);
        BufferedImage image = new BufferedImage(matrix.cols(), matrix.rows(), type);
        final byte[] targetPixels = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
        return image;
    }

    // 将BufferedImage对象转换为Mat对象
    public static Mat bufferedImageToMat(BufferedImage image) {
        byte[] pixels = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, pixels);
        return mat;
    }


    /***
     * 保存线上图片到本地
     * @param imageUrl
     * @return
     */
    public String SavePicInLocalhost(String imageUrl, String path) {
        System.load("F:\\JAVAAI\\opencv481\\opencv\\build\\java\\x64\\opencv_java481.dll");
        try {
            String uuid = System.currentTimeMillis() + "";
            File dir = new File(path + File.separator);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    dir.mkdirs();// 创建文件根目录
                }
            }

            // 打开连接
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            // 设置请求超时为15秒
            connection.setConnectTimeout(15 * 1000);
            // 读取数据流并保存到本地
            InputStream input = connection.getInputStream();
            byte[] datas = new byte[2048];
            int len;
            FileOutputStream output = new FileOutputStream(new File(dir, uuid + ".jpg"));
            while ((len = input.read(datas)) != -1) {
                output.write(datas, 0, len);
            }
            output.close();
            input.close();
            log.info("图片保存成功：" + dir + uuid + ".jpg");
            return uuid + ".jpg";
        } catch (IOException e) {

            log.info("图片保存失败：" + e.getMessage());
            return "error";
        }

    }

    public static Scalar CommonColors(int i) {
        Scalar[] commonColors = {
                new Scalar(255, 0, 0),     // 蓝色
                new Scalar(0, 255, 0),     // 绿色
                new Scalar(0, 0, 255),     // 红色
                new Scalar(255, 255, 0),   // 黄色
                new Scalar(0, 255, 255),   // 青色
                new Scalar(255, 0, 255),   // 粉色
                new Scalar(255, 255, 255), // 白色
                new Scalar(0, 0, 0),        // 黑色
                new Scalar(128, 128, 128), // 灰色
                new Scalar(250, 128, 114), // 三文鱼
                new Scalar(240, 128, 128), // 珊瑚
                new Scalar(255, 99, 71), // 番茄
                new Scalar(124, 252, 0), // 草坪绿
                new Scalar(72, 209, 204), // 绿松石色
                new Scalar(0, 206, 209), // 深蓝绿色
                new Scalar(65, 105, 225), // 宝蓝色
                new Scalar(255, 0, 255), // 紫红色
                new Scalar(255, 0, 0),     // 蓝色
                new Scalar(0, 255, 0),     // 绿色
                new Scalar(0, 0, 255),     // 红色
                new Scalar(255, 255, 0),   // 黄色
                new Scalar(0, 255, 255),   // 青色
                new Scalar(255, 0, 255),   // 粉色
                new Scalar(255, 255, 255), // 白色
                new Scalar(0, 0, 0),        // 黑色
                new Scalar(128, 128, 128), // 灰色
                new Scalar(250, 128, 114), // 三文鱼
                new Scalar(240, 128, 128), // 珊瑚
                new Scalar(255, 99, 71), // 番茄
                new Scalar(124, 252, 0), // 草坪绿
                new Scalar(72, 209, 204), // 绿松石色
                new Scalar(0, 206, 209), // 深蓝绿色
                new Scalar(65, 105, 225), // 宝蓝色
                new Scalar(255, 0, 255), // 紫红色
                new Scalar(255, 0, 0),     // 蓝色
                new Scalar(0, 255, 0),     // 绿色
                new Scalar(0, 0, 255),     // 红色
                new Scalar(255, 255, 0),   // 黄色
                new Scalar(0, 255, 255),   // 青色
                new Scalar(255, 0, 255),   // 粉色
                new Scalar(255, 255, 255), // 白色
                new Scalar(0, 0, 0),        // 黑色
                new Scalar(128, 128, 128), // 灰色
                new Scalar(250, 128, 114), // 三文鱼
                new Scalar(240, 128, 128), // 珊瑚
                new Scalar(255, 99, 71), // 番茄
                new Scalar(124, 252, 0), // 草坪绿
                new Scalar(72, 209, 204), // 绿松石色
                new Scalar(0, 206, 209), // 深蓝绿色
                new Scalar(65, 105, 225), // 宝蓝色
                new Scalar(255, 0, 255), // 紫红色
                // 添加更多的颜色...
        };
        if (i >= commonColors.length) {
            i = 0;
        }
        return commonColors[i];
    }

    public static String CommonColorsVue(int i) {
        String[] commonColors = {
                "#0000FF",     // 蓝色
                "#00FF00",     // 绿色
                "#FF0000",     // 红色
                "#FFFF00",   // 黄色
                "#00FFFF",   // 青色
                "#FFC0CB",   // 粉色
                "#FFFFFF", // 白色
                "#000000",        // 黑色
                "#808080", // 灰色
                "#FA8072", // 三文鱼
                "#FF7F50", // 珊瑚
                "#FF6347", // 番茄
                "#7CFC00", // 草坪绿
                "#48D1CC", // 绿松石色
                "#00CED1", // 深蓝绿色
                "#4169E1", // 宝蓝色
                "#FF00FF", // 紫红色
                "#0000FF",     // 蓝色
                "#00FF00",     // 绿色
                "#FF0000",     // 红色
                "#FFFF00",   // 黄色
                "#00FFFF",   // 青色
                "#FFC0CB",   // 粉色
                "#FFFFFF", // 白色
                "#000000",        // 黑色
                "#808080", // 灰色
                "#FA8072", // 三文鱼
                "#FF7F50", // 珊瑚
                "#FF6347", // 番茄
                "#7CFC00", // 草坪绿
                "#48D1CC", // 绿松石色
                "#00CED1", // 深蓝绿色
                "#4169E1", // 宝蓝色
                "#FF00FF", // 紫红色
                "#0000FF",     // 蓝色
                "#00FF00",     // 绿色
                "#FF0000",     // 红色
                "#FFFF00",   // 黄色
                "#00FFFF",   // 青色
                "#FFC0CB",   // 粉色
                "#FFFFFF", // 白色
                "#000000",        // 黑色
                "#808080", // 灰色
                "#FA8072", // 三文鱼
                "#FF7F50", // 珊瑚
                "#FF6347", // 番茄
                "#7CFC00", // 草坪绿
                "#48D1CC", // 绿松石色
                "#00CED1", // 深蓝绿色
                "#4169E1", // 宝蓝色
                "#FF00FF", // 紫红色
                "#0000FF",     // 蓝色
                "#00FF00",     // 绿色
                "#FF0000",     // 红色
                "#FFFF00",   // 黄色
                "#00FFFF",   // 青色
                "#FFC0CB",   // 粉色
                "#FFFFFF", // 白色
                "#000000",        // 黑色
                "#808080", // 灰色
                "#FA8072", // 三文鱼
                "#FF7F50", // 珊瑚
                "#FF6347", // 番茄
                "#7CFC00", // 草坪绿
                "#48D1CC", // 绿松石色
                "#00CED1", // 深蓝绿色
                "#4169E1", // 宝蓝色
                "#FF00FF", // 紫红色
                "#0000FF",     // 蓝色
                "#00FF00",     // 绿色
                "#FF0000",     // 红色
                "#FFFF00",   // 黄色
                "#00FFFF",   // 青色
                "#FFC0CB",   // 粉色
                "#FFFFFF", // 白色
                "#000000",        // 黑色
                "#808080", // 灰色
                "#FA8072", // 三文鱼
                "#FF7F50", // 珊瑚
                "#FF6347", // 番茄
                "#7CFC00", // 草坪绿
                "#48D1CC", // 绿松石色
                "#00CED1", // 深蓝绿色
                "#4169E1", // 宝蓝色
                "#FF00FF", // 紫红色


                // 添加更多的颜色...
        };
        if (i >= commonColors.length) {
            i = 0;
        }
        return commonColors[i];
    }

    // 生成指定数量的随机颜色
    private static Scalar[] generateRandomColors(int count) {
        Scalar[] colors = new Scalar[count];
        for (int i = 0; i < count; i++) {
            int r = (int) (Math.random() * 256);
            int g = (int) (Math.random() * 256);
            int b = (int) (Math.random() * 256);
            colors[i] = new Scalar(b, g, r);
        }
        return colors;
    }
}
