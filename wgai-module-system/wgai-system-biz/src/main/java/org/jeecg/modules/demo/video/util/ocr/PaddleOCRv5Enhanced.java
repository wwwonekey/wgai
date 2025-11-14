package org.jeecg.modules.demo.video.util.ocr;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.List;

/**
 * PaddleOCR v5 - 支持竖向文字识别和优化识别率 (OpenCV 4.8.1)
 * @author wggg
 * @date 2025/11/13
 */
public class PaddleOCRv5Enhanced {



    private OrtEnvironment env;
    private OrtSession detectionSession;
    private OrtSession recognitionSession;
    private OrtSession angleClassifierSession;

    private int detInputHeight = 960;
    private int detInputWidth = 960;
    private float detThreshold = 0.3f;
    private float boxThreshold = 0.6f;
    private float unclipRatio = 1.5f;

    private int recInputHeight = 48;
    private int recInputWidth = 320;
    private List<String> characterDict;

    private float[] mean = {0.485f, 0.456f, 0.406f};
    private float[] std = {0.229f, 0.224f, 0.225f};

    private boolean useAngleClassifier = false;
    private boolean autoRotate = true;
    private boolean useImageEnhancement = true; // 是否启用图像增强

    public PaddleOCRv5Enhanced(String detModelPath, String recModelPath,
                               String detConfigPath, String recConfigPath) throws OrtException, IOException {
        this(detModelPath, recModelPath, null, detConfigPath, recConfigPath);
    }

    public PaddleOCRv5Enhanced(String detModelPath, String recModelPath, String clsModelPath,
                               String detConfigPath, String recConfigPath) throws OrtException, IOException {
        env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        detectionSession = env.createSession(detModelPath, sessionOptions);
        recognitionSession = env.createSession(recModelPath, sessionOptions);

        if (clsModelPath != null && new File(clsModelPath).exists()) {
            angleClassifierSession = env.createSession(clsModelPath, sessionOptions);
            useAngleClassifier = true;
            System.out.println("✓ 方向分类器已启用");
        }

        if (recConfigPath != null) {
            loadRecognitionConfig(recConfigPath);
        }
        if (detConfigPath != null) {
            loadDetectionConfig(detConfigPath);
        }

        System.out.println("=== PaddleOCR v5 增强版加载成功 ===");
        System.out.println("OpenCV版本: " + Core.VERSION);
        System.out.println("字符字典大小: " + (characterDict != null ? characterDict.size() : 0));
        System.out.println("自动旋转: " + (autoRotate ? "启用" : "禁用"));
        System.out.println("图像增强: " + (useImageEnhancement ? "启用" : "禁用"));
        System.out.println("====================================");
    }

    private void loadDetectionConfig(String configPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = new FileInputStream(configPath)) {
            Map<String, Object> config = yaml.load(is);
            if (config.containsKey("det_limit_side_len")) {
                detInputHeight = detInputWidth = ((Number) config.get("det_limit_side_len")).intValue();
            }
            if (config.containsKey("det_db_thresh")) {
                detThreshold = ((Number) config.get("det_db_thresh")).floatValue();
            }
            if (config.containsKey("det_db_box_thresh")) {
                boxThreshold = ((Number) config.get("det_db_box_thresh")).floatValue();
            }
            if (config.containsKey("det_db_unclip_ratio")) {
                unclipRatio = ((Number) config.get("det_db_unclip_ratio")).floatValue();
            }
        }
    }

    private void loadRecognitionConfig(String configPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = new FileInputStream(configPath)) {
            Map<String, Object> config = yaml.load(is);

            if (config.containsKey("PreProcess")) {
                Map<String, Object> preProcess = (Map<String, Object>) config.get("PreProcess");
                if (preProcess != null && preProcess.containsKey("transform_ops")) {
                    List<Map<String, Object>> transformOps = (List<Map<String, Object>>) preProcess.get("transform_ops");
                    for (Map<String, Object> op : transformOps) {
                        if (op.containsKey("RecResizeImg")) {
                            Map<String, Object> recResizeImg = (Map<String, Object>) op.get("RecResizeImg");
                            if (recResizeImg.containsKey("image_shape")) {
                                List<Integer> shape = (List<Integer>) recResizeImg.get("image_shape");
                                if (shape.size() >= 3) {
                                    recInputHeight = shape.get(1);
                                    recInputWidth = shape.get(2);
                                }
                            }
                            break;
                        }
                    }
                }
            }

            if (config.containsKey("PostProcess")) {
                Map<String, Object> postProcess = (Map<String, Object>) config.get("PostProcess");
                if (postProcess != null && postProcess.containsKey("character_dict")) {
                    List<String> dictList = (List<String>) postProcess.get("character_dict");
                    characterDict = new ArrayList<>();
                    characterDict.add("blank");
                    characterDict.addAll(dictList);
                }
            }
        }
    }

    public void setAutoRotate(boolean enable) {
        this.autoRotate = enable;
    }

    public void setImageEnhancement(boolean enable) {
        this.useImageEnhancement = enable;
    }

    public List<OCRResult> ocr(String imagePath) throws OrtException {
        Mat image = Imgcodecs.imread(imagePath);
        if (image.empty()) {
            throw new RuntimeException("无法读取图像: " + imagePath);
        }
        return ocr(image);
    }

    public List<OCRResult> ocr(Mat image) throws OrtException {
        List<float[][]> textBoxes = detect(image);

        List<OCRResult> results = new ArrayList<>();
        for (float[][] box : textBoxes) {
            Mat croppedImage = cropImagePerspective(image, box);

            boolean isVertical = isVerticalText(croppedImage, box);

            if (isVertical && autoRotate) {
                Core.rotate(croppedImage, croppedImage, Core.ROTATE_90_CLOCKWISE);
            }

            if (useAngleClassifier) {
                int angle = classifyAngle(croppedImage);
                if (angle == 180) {
                    Core.rotate(croppedImage, croppedImage, Core.ROTATE_180);
                }
            }

            String text = recognize(croppedImage);
            results.add(new OCRResult(box, text, 0.95f, isVertical));
        }

        return results;
    }

    private boolean isVerticalText(Mat image, float[][] box) {
        int height = image.rows();
        int width = image.cols();
        float aspectRatio = (float) height / width;

        if (aspectRatio > 1.5f) {
            return true;
        }

        float boxWidth = (float) Math.sqrt(
                Math.pow(box[1][0] - box[0][0], 2) +
                        Math.pow(box[1][1] - box[0][1], 2)
        );
        float boxHeight = (float) Math.sqrt(
                Math.pow(box[3][0] - box[0][0], 2) +
                        Math.pow(box[3][1] - box[0][1], 2)
        );

        return boxHeight > boxWidth * 1.5f;
    }

    private int classifyAngle(Mat image) throws OrtException {
        if (angleClassifierSession == null) {
            return 0;
        }

        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(192, 48), 0, 0, Imgproc.INTER_LINEAR);

        Mat rgbImage = new Mat();
        Imgproc.cvtColor(resized, rgbImage, Imgproc.COLOR_BGR2RGB);

        Mat floatImage = new Mat();
        rgbImage.convertTo(floatImage, CvType.CV_32FC3, 1.0 / 255.0);

        float[][][][] inputData = new float[1][3][48][192];
        float[] data = new float[(int) floatImage.total() * floatImage.channels()];
        floatImage.get(0, 0, data);

        for (int h = 0; h < 48; h++) {
            for (int w = 0; w < 192; w++) {
                int idx = (h * 192 + w) * 3;
                inputData[0][0][h][w] = (data[idx] - mean[0]) / std[0];
                inputData[0][1][h][w] = (data[idx + 1] - mean[1]) / std[1];
                inputData[0][2][h][w] = (data[idx + 2] - mean[2]) / std[2];
            }
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(angleClassifierSession.getInputNames().iterator().next(), inputTensor);
        OrtSession.Result output = angleClassifierSession.run(inputs);

        float[][] outputData = (float[][]) output.get(0).getValue();
        int angle = outputData[0][1] > outputData[0][0] ? 180 : 0;

        inputTensor.close();
        output.close();

        return angle;
    }

    public List<float[][]> detect(Mat image) throws OrtException {
        int origHeight = image.rows();
        int origWidth = image.cols();

        float ratio = Math.min(
                (float) detInputHeight / origHeight,
                (float) detInputWidth / origWidth
        );

        int resizeHeight = (int) (origHeight * ratio);
        int resizeWidth = (int) (origWidth * ratio);
        resizeHeight = (resizeHeight / 32) * 32;
        resizeWidth = (resizeWidth / 32) * 32;

        Mat resizedImage = new Mat();
        Imgproc.resize(image, resizedImage, new Size(resizeWidth, resizeHeight), 0, 0, Imgproc.INTER_LINEAR);

        Mat rgbImage = new Mat();
        Imgproc.cvtColor(resizedImage, rgbImage, Imgproc.COLOR_BGR2RGB);

        Mat floatImage = new Mat();
        rgbImage.convertTo(floatImage, CvType.CV_32FC3, 1.0 / 255.0);

        float[][][][] inputData = new float[1][3][resizeHeight][resizeWidth];

        float[] data = new float[(int) floatImage.total() * floatImage.channels()];
        floatImage.get(0, 0, data);

        for (int h = 0; h < resizeHeight; h++) {
            for (int w = 0; w < resizeWidth; w++) {
                int idx = (h * resizeWidth + w) * 3;
                float r = data[idx];
                float g = data[idx + 1];
                float b = data[idx + 2];

                inputData[0][0][h][w] = (r - mean[0]) / std[0];
                inputData[0][1][h][w] = (g - mean[1]) / std[1];
                inputData[0][2][h][w] = (b - mean[2]) / std[2];
            }
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(detectionSession.getInputNames().iterator().next(), inputTensor);
        OrtSession.Result output = detectionSession.run(inputs);

        Object outputObj = output.get(0).getValue();
        float[][] pred;

        if (outputObj instanceof float[][][][]) {
            pred = ((float[][][][]) outputObj)[0][0];
        } else if (outputObj instanceof float[][][]) {
            pred = ((float[][][]) outputObj)[0];
        } else {
            throw new RuntimeException("未知的检测输出格式");
        }

        float minVal = Float.MAX_VALUE, maxVal = Float.MIN_VALUE;
        for (int h = 0; h < pred.length; h++) {
            for (int w = 0; w < pred[0].length; w++) {
                minVal = Math.min(minVal, pred[h][w]);
                maxVal = Math.max(maxVal, pred[h][w]);
            }
        }

        if (maxVal > 1.0f || minVal < 0.0f) {
            for (int h = 0; h < pred.length; h++) {
                for (int w = 0; w < pred[0].length; w++) {
                    pred[h][w] = 1.0f / (1.0f + (float) Math.exp(-pred[h][w]));
                }
            }
        }

        List<float[][]> boxes = postProcessDetection(pred, resizeWidth, resizeHeight,
                origWidth, origHeight, ratio);

        inputTensor.close();
        output.close();
        return boxes;
    }

    private List<float[][]> postProcessDetection(float[][] pred, int width, int height,
                                                 int origWidth, int origHeight, float ratio) {
        List<float[][]> boxes = new ArrayList<>();

        boolean[][] bitmap = new boolean[height][width];
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                bitmap[h][w] = pred[h][w] > detThreshold;
            }
        }

        List<List<int[]>> contours = findContours(bitmap);

        for (List<int[]> contour : contours) {
            if (contour.size() < 4) continue;

            float[][] box = getMinAreaRect(contour);

            float boxWidth = Math.abs(box[1][0] - box[0][0]);
            float boxHeight = Math.abs(box[2][1] - box[1][1]);

            if (boxWidth < 3 || boxHeight < 3) continue;

            float score = calculateBoxScore(pred, box);
            if (score < boxThreshold) continue;

            box = unclipBox(box, unclipRatio);

            for (int i = 0; i < box.length; i++) {
                box[i][0] = box[i][0] / ratio;
                box[i][1] = box[i][1] / ratio;
            }

            boxes.add(box);
        }

        return boxes;
    }

    private List<List<int[]>> findContours(boolean[][] bitmap) {
        List<List<int[]>> contours = new ArrayList<>();
        int height = bitmap.length;
        int width = bitmap[0].length;
        boolean[][] visited = new boolean[height][width];

        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                if (bitmap[h][w] && !visited[h][w]) {
                    List<int[]> contour = bfs(bitmap, visited, h, w);
                    if (contour.size() > 10) {
                        contours.add(contour);
                    }
                }
            }
        }

        return contours;
    }

    private List<int[]> bfs(boolean[][] bitmap, boolean[][] visited, int startH, int startW) {
        List<int[]> contour = new ArrayList<>();
        Queue<int[]> queue = new LinkedList<>();
        queue.offer(new int[]{startH, startW});
        visited[startH][startW] = true;
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int h = pos[0];
            int w = pos[1];
            contour.add(new int[]{w, h});

            for (int[] dir : dirs) {
                int nh = h + dir[1];
                int nw = w + dir[0];
                if (nh >= 0 && nh < bitmap.length && nw >= 0 && nw < bitmap[0].length
                        && bitmap[nh][nw] && !visited[nh][nw]) {
                    visited[nh][nw] = true;
                    queue.offer(new int[]{nh, nw});
                }
            }
        }

        return contour;
    }

    private float[][] getMinAreaRect(List<int[]> contour) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (int[] point : contour) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]);
            maxY = Math.max(maxY, point[1]);
        }

        return new float[][]{
                {minX, minY},
                {maxX, minY},
                {maxX, maxY},
                {minX, maxY}
        };
    }

    private float calculateBoxScore(float[][] pred, float[][] box) {
        int minX = (int) Math.max(0, Math.min(box[0][0], Math.min(box[1][0], Math.min(box[2][0], box[3][0]))));
        int maxX = (int) Math.min(pred[0].length - 1, Math.max(box[0][0], Math.max(box[1][0], Math.max(box[2][0], box[3][0]))));
        int minY = (int) Math.max(0, Math.min(box[0][1], Math.min(box[1][1], Math.min(box[2][1], box[3][1]))));
        int maxY = (int) Math.min(pred.length - 1, Math.max(box[0][1], Math.max(box[1][1], Math.max(box[2][1], box[3][1]))));

        float score = 0;
        int count = 0;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                score += pred[y][x];
                count++;
            }
        }

        return count > 0 ? score / count : 0;
    }

    private float[][] unclipBox(float[][] box, float ratio) {
        float centerX = (box[0][0] + box[1][0] + box[2][0] + box[3][0]) / 4.0f;
        float centerY = (box[0][1] + box[1][1] + box[2][1] + box[3][1]) / 4.0f;

        float[][] newBox = new float[4][2];
        for (int i = 0; i < 4; i++) {
            float dx = box[i][0] - centerX;
            float dy = box[i][1] - centerY;
            newBox[i][0] = centerX + dx * ratio;
            newBox[i][1] = centerY + dy * ratio;
        }

        return newBox;
    }

    /**
     * 文本识别 - 优化版 (OpenCV 4.8.1兼容)
     */
    public String recognize(Mat image) throws OrtException {
        if (characterDict == null || characterDict.size() <= 1) {
            return "";
        }

        // 图像增强
        Mat enhanced = useImageEnhancement ? enhanceImage(image) : image.clone();

        // 转灰度图
        Mat grayImage = new Mat();
        if (enhanced.channels() == 3) {
            Imgproc.cvtColor(enhanced, grayImage, Imgproc.COLOR_BGR2GRAY);
        } else {
            grayImage = enhanced.clone();
        }

        // CLAHE直方图均衡化
        Mat claheImage = new Mat();
        CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        clahe.apply(grayImage, claheImage);

        // 计算缩放比例
        float ratio = (float) recInputHeight / claheImage.rows();
        int resizeWidth = (int) (claheImage.cols() * ratio);

        if (resizeWidth > recInputWidth) {
            resizeWidth = recInputWidth;
        }

        // 高质量缩放
        Mat resizedImage = new Mat();
        Imgproc.resize(claheImage, resizedImage, new Size(resizeWidth, recInputHeight), 0, 0, Imgproc.INTER_CUBIC);

        // 转RGB
        Mat rgbImage = new Mat();
        Imgproc.cvtColor(resizedImage, rgbImage, Imgproc.COLOR_GRAY2RGB);

        // 归一化
        Mat floatImage = new Mat();
        rgbImage.convertTo(floatImage, CvType.CV_32FC3, 1.0 / 255.0);

        // 转NCHW格式
        float[][][][] inputData = new float[1][3][recInputHeight][recInputWidth];

        float[] data = new float[(int) floatImage.total() * floatImage.channels()];
        floatImage.get(0, 0, data);

        for (int h = 0; h < recInputHeight; h++) {
            for (int w = 0; w < recInputWidth; w++) {
                if (w < resizeWidth) {
                    int idx = (h * resizeWidth + w) * 3;
                    float r = data[idx];
                    float g = data[idx + 1];
                    float b = data[idx + 2];

                    inputData[0][0][h][w] = (r - mean[0]) / std[0];
                    inputData[0][1][h][w] = (g - mean[1]) / std[1];
                    inputData[0][2][h][w] = (b - mean[2]) / std[2];
                } else {
                    inputData[0][0][h][w] = -mean[0] / std[0];
                    inputData[0][1][h][w] = -mean[1] / std[1];
                    inputData[0][2][h][w] = -mean[2] / std[2];
                }
            }
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(recognitionSession.getInputNames().iterator().next(), inputTensor);
        OrtSession.Result output = recognitionSession.run(inputs);

        float[][][] outputData = (float[][][]) output.get(0).getValue();
        String text = ctcDecode(outputData[0]);

        inputTensor.close();
        output.close();

        return text;
    }

    /**
     * 图像增强 - OpenCV 4.8.1兼容版本
     */
    private Mat enhanceImage(Mat image) {
        Mat enhanced = new Mat();

        // 方法1: 使用双边滤波去噪 (替代fastNlMeansDenoising)
        Imgproc.bilateralFilter(image, enhanced, 9, 75, 75);

        // 方法2: 也可以使用高斯模糊 + 锐化
        // Mat blurred = new Mat();
        // Imgproc.GaussianBlur(image, blurred, new Size(5, 5), 0);
        // Core.addWeighted(image, 1.5, blurred, -0.5, 0, enhanced);

        return enhanced;
    }

    private String ctcDecode(float[][] preds) {
        StringBuilder result = new StringBuilder();
        int prevIndex = -1;

        for (int t = 0; t < preds.length; t++) {
            float[] pred = preds[t];
            int maxIndex = 0;
            float maxProb = pred[0];

            for (int i = 1; i < pred.length; i++) {
                if (pred[i] > maxProb) {
                    maxProb = pred[i];
                    maxIndex = i;
                }
            }

            if (maxIndex != 0 && maxIndex != prevIndex) {
                if (maxIndex < characterDict.size()) {
                    result.append(characterDict.get(maxIndex));
                }
            }

            prevIndex = maxIndex;
        }

        return result.toString();
    }

    /**
     * 透视变换裁剪
     */
    private Mat cropImagePerspective(Mat image, float[][] box) {
        float width1 = (float) Math.sqrt(Math.pow(box[1][0] - box[0][0], 2) + Math.pow(box[1][1] - box[0][1], 2));
        float width2 = (float) Math.sqrt(Math.pow(box[2][0] - box[3][0], 2) + Math.pow(box[2][1] - box[3][1], 2));
        float width = Math.max(width1, width2);

        float height1 = (float) Math.sqrt(Math.pow(box[3][0] - box[0][0], 2) + Math.pow(box[3][1] - box[0][1], 2));
        float height2 = (float) Math.sqrt(Math.pow(box[2][0] - box[1][0], 2) + Math.pow(box[2][1] - box[1][1], 2));
        float height = Math.max(height1, height2);

        MatOfPoint2f srcPoints = new MatOfPoint2f(
                new Point(box[0][0], box[0][1]),
                new Point(box[1][0], box[1][1]),
                new Point(box[2][0], box[2][1]),
                new Point(box[3][0], box[3][1])
        );

        MatOfPoint2f dstPoints = new MatOfPoint2f(
                new Point(0, 0),
                new Point(width, 0),
                new Point(width, height),
                new Point(0, height)
        );

        Mat M = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
        Mat warped = new Mat();
        Imgproc.warpPerspective(image, warped, M, new Size(width, height));

        return warped;
    }

    public void close() throws OrtException {
        if (detectionSession != null) detectionSession.close();
        if (recognitionSession != null) recognitionSession.close();
        if (angleClassifierSession != null) angleClassifierSession.close();
    }

    public static class OCRResult {
        public float[][] box;
        public String text;
        public float confidence;
        public boolean isVertical;

        public OCRResult(float[][] box, String text, float confidence, boolean isVertical) {
            this.box = box;
            this.text = text;
            this.confidence = confidence;
            this.isVertical = isVertical;
        }

        public int[] getBoundingBox() {
            int minX = (int) Math.min(box[0][0], Math.min(box[1][0], Math.min(box[2][0], box[3][0])));
            int maxX = (int) Math.max(box[0][0], Math.max(box[1][0], Math.max(box[2][0], box[3][0])));
            int minY = (int) Math.min(box[0][1], Math.min(box[1][1], Math.min(box[2][1], box[3][1])));
            int maxY = (int) Math.max(box[0][1], Math.max(box[1][1], Math.max(box[2][1], box[3][1])));

            return new int[]{minX, minY, maxX - minX, maxY - minY};
        }

        @Override
        public String toString() {
            int[] bbox = getBoundingBox();
            String direction = isVertical ? "竖向" : "横向";
            return String.format("文本: %s | 方向: %s | 位置: [x=%d, y=%d, w=%d, h=%d] | 置信度: %.2f",
                    text, direction, bbox[0], bbox[1], bbox[2], bbox[3], confidence);
        }
    }

    public static void main(String[] args) {
        System.load("F:\\JAVAAI\\opencv481\\opencv\\build\\java\\x64\\opencv_java481.dll");
        try {
            String detModelPath = "F:\\JAVAAI\\OCR\\Paddle\\检测模型\\jiance\\dec.onnx";
            String recModelPath = "F:\\JAVAAI\\OCR\\Paddle\\检测模型\\vi\\inference.onnx";
            String detConfigPath = "F:\\JAVAAI\\OCR\\Paddle\\检测模型\\jiance\\inference.yml";
            String recConfigPath = "F:\\JAVAAI\\OCR\\Paddle\\检测模型\\vi\\inference.yml";

            System.out.println("初始化 PaddleOCR 增强版 (OpenCV 4.8.1)...");
            PaddleOCRv5Enhanced ocr = new PaddleOCRv5Enhanced(
                    detModelPath, recModelPath, detConfigPath, recConfigPath);

            // 启用/禁用功能
            ocr.setAutoRotate(true);           // 自动旋转竖向文字
            ocr.setImageEnhancement(true);     // 图像增强

            String imagePath = "F:\\JAVAAI\\test_image.jpg";
            System.out.println("识别图像: " + imagePath);

            List<OCRResult> results = ocr.ocr(imagePath);

            System.out.println("\n========================================");
            System.out.println("识别结果 (共 " + results.size() + " 段文本):");
            System.out.println("========================================\n");

            for (int i = 0; i < results.size(); i++) {
                System.out.println((i + 1) + ". " + results.get(i).toString());
            }

            ocr.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}