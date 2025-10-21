package org.jeecg.modules.demo.video.util.batch.batch;


import ai.onnxruntime.*;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.jeecg.common.util.RestUtil;
import org.jeecg.modules.demo.tab.entity.TabAiBase;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
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
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.*;

/**
 * ONNX批量推理实现 - 核心推理引擎
 * 支持批量处理多个帧,一次性推理
 */
@Slf4j
public class BatchOnnxInference {

    /**
     * 批量推理 - 前置模型(返回ROI)
     */
    public static List<retureBoxInfo> batchInferencePreModel(
            List<Mat> mats,
            NetPush netPush,
            List<TabAiSubscriptionNew> pushInfos,
            RedisTemplate redisTemplate) {

        int batchSize = mats.size();
        List<retureBoxInfo> results = new ArrayList<>(batchSize);

        try {
            // 1. 批量预处理
            float[] batchInputData = batchPreprocess(mats);

            // 2. 批量ONNX推理
            List<DetectionResult> detectionResults = runBatchOnnxInference(
                    netPush.getSession(),
                    netPush.getEnv(),
                    batchInputData,
                    batchSize,
                    netPush.getClaseeNames().size()
            );

            // 3. 批量后处理 - 提取ROI
            for (int i = 0; i < batchSize; i++) {
                retureBoxInfo result = postProcessPreModel(
                        mats.get(i),
                        detectionResults.get(i),
                        netPush,
                        pushInfos.get(i)
                );
                results.add(result);
            }

            log.debug("[前置批量推理完成] 批次大小:{}, 平均ROI数:{}",
                    batchSize,
                    results.stream().mapToInt(r -> r.getInfoList() == null ? 0 : r.getInfoList().size()).average().orElse(0));

        } catch (Exception e) {
            log.error("[前置批量推理失败]", e);
            // 降级:单个推理
            for (int i = 0; i < batchSize; i++) {
                results.add(new retureBoxInfo());
            }
        }

        return results;
    }

    /**
     * 批量推理 - 后置模型(检测并推送)
     */
    public static List<Boolean> batchInferencePostModel(
            List<Mat> mats,
            NetPush netPush,
            List<TabAiSubscriptionNew> pushInfos,
            List<List<retureBoxInfo>> preResultsList,
            RedisTemplate redisTemplate) {

        int batchSize = mats.size();
        List<Boolean> results = new ArrayList<>(batchSize);

        try {
            // 1. 批量预处理
            float[] batchInputData = batchPreprocess(mats);

            // 2. 批量ONNX推理
            List<DetectionResult> detectionResults = runBatchOnnxInference(
                    netPush.getSession(),
                    netPush.getEnv(),
                    batchInputData,
                    batchSize,
                    netPush.getClaseeNames().size()
            );

            // 3. 批量后处理 - 检测并推送
            for (int i = 0; i < batchSize; i++) {
                boolean success = postProcessPostModel(
                        mats.get(i),
                        detectionResults.get(i),
                        netPush,
                        pushInfos.get(i),
                        preResultsList.get(i),
                        redisTemplate
                );
                results.add(success);
            }

            long successCount = results.stream().filter(b -> b).count();
            log.debug("[后置批量推理完成] 批次大小:{}, 成功:{}", batchSize, successCount);

        } catch (Exception e) {
            log.error("[后置批量推理失败]", e);
            for (int i = 0; i < batchSize; i++) {
                results.add(false);
            }
        }

        return results;
    }

    // ==================== 核心:批量预处理 ====================

    /**
     * 批量预处理:将N个Mat转为 [N, 3, 640, 640] 的float数组
     */
    private static float[] batchPreprocess(List<Mat> mats) {
        int batchSize = mats.size();
        float[] batchData = new float[batchSize * 3 * 640 * 640];

        for (int b = 0; b < batchSize; b++) {
            Mat mat = mats.get(b);

            // Letterbox resize
            Mat resized = letterboxResize(mat, 640, 640);

            // 转为float并归一化
            Mat blob = new Mat();
            resized.convertTo(blob, CvType.CV_32F, 1.0 / 255.0);

            // 分离通道 BGR -> RGB
            List<Mat> channels = new ArrayList<>();
            Core.split(blob, channels);

            // 填充到批量数组中 [N, C, H, W]
            int offset = b * 3 * 640 * 640;
            for (int c = 0; c < 3; c++) {
                float[] channelData = new float[640 * 640];
                // 注意:OpenCV是BGR,ONNX通常是RGB,这里反转
                channels.get(2 - c).get(0, 0, channelData);
                System.arraycopy(channelData, 0,
                        batchData, offset + c * 640 * 640, 640 * 640);
            }

            resized.release();
            blob.release();
            channels.forEach(Mat::release);
        }

        return batchData;
    }

    // ==================== 核心:批量ONNX推理 ====================

    /**
     * 批量ONNX推理 - 核心方法
     */
    private static List<DetectionResult> runBatchOnnxInference(
            OrtSession session,
            OrtEnvironment env,
            float[] batchInputData,
            int batchSize,
            int numClasses) throws OrtException {

        long[] shape = new long[]{batchSize, 3, 640, 640};
        List<DetectionResult> results = new ArrayList<>(batchSize);

        // 创建输入tensor
        FloatBuffer buffer = FloatBuffer.allocate(batchInputData.length);
        buffer.put(batchInputData);
        buffer.flip();

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape)) {
            Map<String, OnnxTensor> inputs = Collections.singletonMap(
                    session.getInputNames().iterator().next(),
                    inputTensor
            );

            // 执行推理
            try (OrtSession.Result onnxResults = session.run(inputs)) {
                for (Map.Entry<String, OnnxValue> entry : onnxResults) {
                    if (!(entry.getValue() instanceof OnnxTensor)) continue;

                    OnnxTensor tensor = (OnnxTensor) entry.getValue();
                    long[] tensorShape = tensor.getInfo().getShape();
                    Object rawOutput = tensor.getValue();

                    // 解析批量输出
                    results = parseBatchOutput(rawOutput, tensorShape, batchSize, numClasses);
                }
            }
        }

        return results;
    }

    // ==================== 核心:解析批量输出 ====================

    /**
     * 解析批量输出 - 支持YOLOv5/v8/v11格式
     */
    private static List<DetectionResult> parseBatchOutput(
            Object rawOutput,
            long[] tensorShape,
            int batchSize,
            int numClasses) {

        List<DetectionResult> results = new ArrayList<>(batchSize);

        if (rawOutput instanceof float[][][]) {
            float[][][] batch = (float[][][]) rawOutput;

            // 判断格式 [B, 84, 8400] 或 [B, 8400, 84]
            boolean needTranspose = tensorShape.length == 3 &&
                    tensorShape[1] < tensorShape[2] &&
                    tensorShape[1] <= (numClasses + 5);

            log.debug("[输出格式] shape:[{}, {}, {}], needTranspose:{}",
                    tensorShape[0], tensorShape[1], tensorShape[2], needTranspose);

            for (int b = 0; b < batchSize; b++) {
                DetectionResult result = new DetectionResult();

                if (needTranspose) {
                    // YOLOv11格式 [84, 8400]
                    parseTransposedDetections(batch[b], tensorShape, numClasses, result);
                } else {
                    // YOLOv5/v8格式 [8400, 84]
                    parseStandardDetections(batch[b], numClasses, result);
                }

                results.add(result);
            }
        }

        return results;
    }

    /**
     * 解析转置格式 [84, 8400] - YOLOv11
     */
    private static void parseTransposedDetections(
            float[][] detections,
            long[] tensorShape,
            int numClasses,
            DetectionResult result) {

        int numDetections = (int) tensorShape[2];
        int numFeatures = (int) tensorShape[1];
        int actualNumClasses = numFeatures - 4;

        float confThreshold = 0.45f;

        for (int i = 0; i < numDetections; i++) {
            float cx = detections[0][i];
            float cy = detections[1][i];
            float w = detections[2][i];
            float h = detections[3][i];

            // 找最高分类
            float maxScore = 0;
            int classId = 0;
            for (int c = 0; c < actualNumClasses; c++) {
                if (detections[4 + c][i] > maxScore) {
                    maxScore = detections[4 + c][i];
                    classId = c;
                }
            }

            if (maxScore > confThreshold && classId < numClasses) {
                result.addDetection(
                        cx - w / 2, cy - h / 2, w, h, maxScore, classId
                );
            }
        }
    }

    /**
     * 解析标准格式 [8400, 84] - YOLOv5/v8
     */
    private static void parseStandardDetections(
            float[][] detections,
            int numClasses,
            DetectionResult result) {

        float confThreshold = 0.45f;

        for (float[] det : detections) {
            if (det.length < 5) continue;

            boolean hasObjectness = det.length > 5;
            int startIdx = hasObjectness ? 5 : 4;

            // 找最高分类
            float maxScore = 0;
            int classId = 0;
            for (int i = startIdx; i < det.length && i < startIdx + numClasses; i++) {
                if (det[i] > maxScore) {
                    maxScore = det[i];
                    classId = i - startIdx;
                }
            }

            float confidence = hasObjectness ? det[4] * maxScore : maxScore;

            if (confidence > confThreshold && classId < numClasses) {
                float cx = det[0], cy = det[1], w = det[2], h = det[3];
                result.addDetection(
                        cx - w / 2, cy - h / 2, w, h, confidence, classId
                );
            }
        }
    }

    // ==================== 前置模型后处理 ====================

    /**
     * 前置模型后处理 - 提取ROI框
     */
    private static retureBoxInfo postProcessPreModel(
            Mat image,
            DetectionResult detectionResult,
            NetPush netPush,
            TabAiSubscriptionNew pushInfo) {

        retureBoxInfo returnBox = new retureBoxInfo();
        returnBox.setFlag(false);

        try {
            if (detectionResult.boxes2d.isEmpty()) {
                return returnBox;
            }

            // NMS去重
            int[] nmsIndices = performNMS(detectionResult, 0.4f, 0.45f);

            if (nmsIndices.length == 0 || nmsIndices.length > 50) {
                return returnBox;
            }

            // 坐标还原参数
            double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
            double dx = (640 - image.cols() * scale) / 2;
            double dy = (640 - image.rows() * scale) / 2;

            // 提取目标类别的ROI
            String targetClass = netPush.getBeforText();
            List<String> classNames = netPush.getClaseeNames();
            List<retureBoxInfo> matchedBoxes = new ArrayList<>();

            for (int idx : nmsIndices) {
                Rect2d box = detectionResult.boxes2d.get(idx);
                int classId = detectionResult.classIds.get(idx);
                String className = classNames.get(classId);

                // 只提取目标类别
                if (StringUtils.isNotEmpty(targetClass) && className.equals(targetClass)) {
                    // 坐标还原
                    double x = Math.max(0, (box.x - dx) / scale);
                    double y = Math.max(0, (box.y - dy) / scale);
                    double width = Math.min(box.width / scale, image.cols() - x);
                    double height = Math.min(box.height / scale, image.rows() - y);

                    retureBoxInfo boxInfo = new retureBoxInfo();
                    boxInfo.setX(x);
                    boxInfo.setY(y);
                    boxInfo.setWidth(width);
                    boxInfo.setHeight(height);
                    matchedBoxes.add(boxInfo);
                }
            }

            if (!matchedBoxes.isEmpty()) {
                returnBox.setFlag(true);
                returnBox.setInfoList(matchedBoxes);
                log.debug("[前置模型检测成功] 目标:{}, 数量:{}", targetClass, matchedBoxes.size());
            }

        } catch (Exception e) {
            log.error("[前置后处理失败]", e);
        }

        return returnBox;
    }

    // ==================== 后置模型后处理 ====================

    /**
     * 后置模型后处理 - 完整检测流程
     */
    private static boolean postProcessPostModel(
            Mat image,
            DetectionResult detectionResult,
            NetPush netPush,
            TabAiSubscriptionNew pushInfo,
            List<retureBoxInfo> preResults,
            RedisTemplate redisTemplate) {

        try {
            // 1. 频率控制
            long intervalTime = Long.parseLong(pushInfo.getEventNumber());
            Object lastPushTime = redisTemplate.opsForValue().get(netPush.getId());
            if (lastPushTime != null) {
                return false;
            }

            // 2. 检测结果验证
            if (detectionResult.boxes2d.isEmpty() || detectionResult.boxes2d.size() > 200) {
                return false;
            }

            // 3. NMS去重
            int[] nmsIndices = performNMS(detectionResult, 0.4f, 0.4f);
            if (nmsIndices.length == 0 || nmsIndices.length > 50) {
                return false;
            }

            // 4. 坐标还原
            double scale = Math.min(640.0 / image.cols(), 640.0 / image.rows());
            double dx = (640 - image.cols() * scale) / 2;
            double dy = (640 - image.rows() * scale) / 2;

            // 5. 绘制和统计
            DetectionStats stats = new DetectionStats();
            int validCount = 0;

            List<String> classNames = netPush.getClaseeNames();

            for (int idx : nmsIndices) {
                Rect2d box = detectionResult.boxes2d.get(idx);
                int classId = detectionResult.classIds.get(idx);
                String className = classNames.get(classId);
                float confidence = detectionResult.confidences.get(idx);

                // 坐标还原
                double x = Math.max(0, (box.x - dx) / scale);
                double y = Math.max(0, (box.y - dy) / scale);
                double width = Math.min(box.width / scale, image.cols() - x);
                double height = Math.min(box.height / scale, image.rows() - y);

                // 区域过滤
                if (!isValidDetection(netPush, preResults, x, y, width, height)) {
                    continue;
                }

                // 类别配置
                TabAiBase aiBase = VideoSendReadCfg.map.get(className);
                if (aiBase == null) {
                    aiBase = new TabAiBase();
                    aiBase.setChainName(className);
                }

                if (shouldSkipClass(aiBase)) {
                    continue;
                }

                // 累计统计
                stats.accumulate(aiBase);

                // 绘制
                Scalar color = getColor(aiBase.getRgbColor());
                drawDetection(image, x, y, width, height, aiBase.getChainName(), confidence, color);

                validCount++;
            }

            // 6. 推送结果
            if (stats.warnNumber <= 0) {
                return false;
            }

            // 设置Redis缓存
            redisTemplate.opsForValue().set(netPush.getId(), System.currentTimeMillis(),
                    intervalTime, TimeUnit.SECONDS);

            // 保存图像
            String savePath = netPush.getUploadPath() + File.separator + "push" + File.separator;
            String savedImagePath = saveDetectionImage(image, savePath);

            // 调用推送(这里需要你实现)
            isOk(pushInfo, netPush, redisTemplate, savedImagePath, netPush.getTabAiModel(),
                    stats.audioText, stats.warnNumber, stats.warnText, stats.warnName, savePath);

            log.info("[后置检测成功] 有效检测:{}/{}, 报警数:{}",
                    validCount, nmsIndices.length, stats.warnNumber);

            return true;

        } catch (Exception e) {
            log.error("[后置后处理失败]", e);
            return false;
        }
    }

    //开始录像
    public static String RecordVideo(String videoUrl, String savePath, long time, String id) {
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
    public static synchronized String analysisVideo(String recoredPath, NetPush netPush, String savePath) {
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

    public static boolean isOk(TabAiSubscriptionNew pushInfo, NetPush netPush, RedisTemplate redisTemplate, String saveName, TabAiModel tabAiModel,
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

    // ==================== 辅助方法 ====================

    /**
     * NMS非极大值抑制
     */
    private static int[] performNMS(DetectionResult result, float confThreshold, float nmsThreshold) {
        if (result.boxes2d.isEmpty()) {
            return new int[0];
        }

        MatOfRect2d boxesMat = new MatOfRect2d();
        boxesMat.fromList(result.boxes2d);

        MatOfFloat confMat = new MatOfFloat(Converters.vector_float_to_Mat(result.confidences));
        MatOfInt indices = new MatOfInt();

        Dnn.NMSBoxes(boxesMat, confMat, confThreshold, nmsThreshold, indices);

        return indices.empty() ? new int[0] : indices.toArray();
    }

    /**
     * 检测有效性验证
     */
    private static boolean isValidDetection(NetPush netPush, List<retureBoxInfo> preResults,
                                            double x, double y, double width, double height) {
        // 前置模型区域过滤
        if (netPush.getIsFollow() == 0 && preResults != null && !preResults.isEmpty()) {
            boolean inPreRegion = retureBoxInfo.getLocalhost(preResults, x, y, netPush.getFollowPosition());
            if (!inPreRegion) {
                return false;
            }
        }

        // 自定义区域过滤
        if (netPush.getIsBy() == 0 && netPush.getTabVideoUtil() != null) {
            double areaX = Double.parseDouble(netPush.getTabVideoUtil().getCanvasStartx());
            double areaY = Double.parseDouble(netPush.getTabVideoUtil().getCanvasStarty());
            double areaW = Double.parseDouble(netPush.getTabVideoUtil().getCanvasWidth());
            double areaH = Double.parseDouble(netPush.getTabVideoUtil().getCanvasHeight());

            boolean inArea = x >= areaX && x <= areaX + areaW &&
                    y >= areaY && y <= areaY + areaH;
            if (!inArea) {
                return false;
            }
        }

        return true;
    }

    private static boolean shouldSkipClass(TabAiBase aiBase) {
        return StringUtils.isNotEmpty(aiBase.getSpaceThree()) &&
                aiBase.getSpaceThree().equals("N");
    }

    private static Scalar getColor(String color) {
        if (StringUtils.isNotEmpty(color)) {
            String[] parts = color.split(",");
            if (parts.length >= 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Scalar(b, g, r); // BGR
            }
        }
        return CommonColors(1);
    }

    private static void drawDetection(Mat image, double x, double y, double width, double height,
                                      String label, float confidence, Scalar color) {
        Imgproc.rectangle(image,
                new Point(x, y),
                new Point(x + width, y + height),
                color, 2);

        addChineseText(image, label + String.format("%.2f", confidence),
                new Point(x, y), color);
    }

    private static String saveDetectionImage(Mat image, String savePath) {
        File dir = new File(savePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = savePath + System.currentTimeMillis() + ".jpg";
        Imgcodecs.imwrite(fileName, image);

        return fileName;
    }

    private static Mat letterboxResize(Mat image, int targetWidth, int targetHeight) {
        double scale = Math.min(
                (double) targetWidth / image.cols(),
                (double) targetHeight / image.rows()
        );

        int newWidth = (int) (image.cols() * scale);
        int newHeight = (int) (image.rows() * scale);

        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(newWidth, newHeight));

        Mat letterboxed = new Mat(targetHeight, targetWidth, image.type(),
                new Scalar(114, 114, 114));

        int dx = (targetWidth - newWidth) / 2;
        int dy = (targetHeight - newHeight) / 2;

        Rect roi = new Rect(dx, dy, newWidth, newHeight);
        Mat roiMat = new Mat(letterboxed, roi);
        resized.copyTo(roiMat);

        resized.release();
        return letterboxed;
    }

    // ==================== 数据结构 ====================

    static class DetectionResult {
        List<Rect2d> boxes2d = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();

        void addDetection(double x, double y, double w, double h,
                          float confidence, int classId) {
            boxes2d.add(new Rect2d(x, y, w, h));
            confidences.add(confidence);
            classIds.add(classId);
        }
    }

    static class DetectionStats {
        String audioText = "";
        Integer warnNumber = 0;
        String warnText = "";
        String warnName = "";

        void accumulate(TabAiBase aiBase) {
            audioText += aiBase.getRemark() + aiBase.getSpaceOne();
            warnNumber += aiBase.getSpaceTwo() == null ? 1 : aiBase.getSpaceTwo();
            warnText += (StringUtils.isEmpty(aiBase.getRemark()) ?
                    aiBase.getChainName() : aiBase.getRemark()) + ",";
            warnName += aiBase.getChainName() + ",";
        }
    }
}