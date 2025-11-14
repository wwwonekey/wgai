package org.jeecg.modules.demo.video.util.ocr;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.*;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * PaddleOCR完整实现 - 支持中文、英文、越南语
 * 包含完整的DBNet后处理和CTC解码
 */
public class PaddleOCRCompleteV3 {

    private OrtEnvironment env;
    private OrtSession detSession;
    private OrtSession recSessionCh;
    private OrtSession recSessionVi;

    // 字符集
    private List<String> charsCh;
    private List<String> charsVi;

    // 模型文件路径
    private static final String DET_MODEL = "F:\\JAVAAI\\OCR\\Paddle\\ch_ppocr_det.onnx";
    private static final String REC_MODEL_CH = "F:\\JAVAAI\\OCR\\Paddle\\ch_ppocr_rec.onnx";
    private static final String REC_MODEL_LATIN = "F:\\JAVAAI\\OCR\\Paddle\\latin_ppocr_rec.onnx";

    // 字符集文件路径（可选）
    private static final String DICT_CH = "F:\\JAVAAI\\OCR\\Paddle\\ppocr_keys_v1.txt";
    private static final String DICT_LATIN = "F:\\JAVAAI\\OCR\\Paddle\\latin_dict.txt";

    static {
        System.load("F:\\JAVAAI\\opencv481\\opencv\\build\\java\\x64\\opencv_java481.dll");
    }

    public PaddleOCRCompleteV3() throws OrtException {
        env = OrtEnvironment.getEnvironment();

        // 加载模型
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        detSession = env.createSession(DET_MODEL, opts);
        recSessionCh = env.createSession(REC_MODEL_CH, opts);
        recSessionVi = env.createSession(REC_MODEL_LATIN, opts);

        // 加载字符集
        charsCh = loadCharacterDict(DICT_CH);
        charsVi = loadCharacterDict(DICT_LATIN);

        System.out.println("✅ 模型加载成功");
        System.out.println("   中文字符集: " + charsCh.size() + " 个字符");
        System.out.println("   拉丁字符集: " + charsVi.size() + " 个字符");
    }

    /**
     * 加载字符字典
     */
    private List<String> loadCharacterDict(String dictPath) {
        List<String> characters = new ArrayList<>();

        // 添加blank字符
        characters.add("blank");

        try {
            File file = new File(dictPath);
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)
                );

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        characters.add(line);
                    }
                }
                reader.close();
                System.out.println("✅ 加载字符集: " + dictPath);
            } else {
                System.out.println("⚠️  字符集文件不存在: " + dictPath + "，使用默认字符集");
                characters.addAll(getDefaultCharacters());
            }
        } catch (Exception e) {
            System.out.println("⚠️  加载字符集失败，使用默认字符集");
            characters.addAll(getDefaultCharacters());
        }

        return characters;
    }

    /**
     * 默认字符集（数字+字母）
     */
    private List<String> getDefaultCharacters() {
        List<String> chars = new ArrayList<>();

        // 数字
        for (char c = '0'; c <= '9'; c++) {
            chars.add(String.valueOf(c));
        }

        // 大写字母
        for (char c = 'A'; c <= 'Z'; c++) {
            chars.add(String.valueOf(c));
        }

        // 小写字母
        for (char c = 'a'; c <= 'z'; c++) {
            chars.add(String.valueOf(c));
        }

        return chars;
    }

    /**
     * 完整OCR识别
     */
    public List<OCRResult> recognize(String imagePath, String lang) {
        List<OCRResult> results = new ArrayList<>();

        try {
            Mat img = Imgcodecs.imread(imagePath);
            if (img.empty()) {
                throw new RuntimeException("无法读取图片: " + imagePath);
            }

            System.out.println("\n原始图片尺寸: " + img.width() + "x" + img.height());

            // 1. 文本检测
            List<TextBox> boxes = detectText(img);
            System.out.println("检测到 " + boxes.size() + " 个文本区域");

            // 2. 文本识别
            for (int i = 0; i < boxes.size(); i++) {
                TextBox box = boxes.get(i);

                // 裁剪文本区域
                Mat cropped = cropTextRegion(img, box.points);
                if (cropped.empty() || cropped.width() <=3 || cropped.height() <= 3) {
                    continue;
                }
                System.out.println("🟡 输入图片尺寸: " + img.width() + "x" + img.height());

                // 识别文本
                String text = recognizeText(cropped, lang);

                OCRResult result = new OCRResult();
                result.text = text;
                result.box = box.points;
                result.score = box.score;

                results.add(result);

                System.out.println((i + 1) + ". " + text + " (置信度: " +
                        String.format("%.2f%%", box.score * 100) + ")");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * 文本检测
     */
    private List<TextBox> detectText(Mat img) throws OrtException {
        // 预处理
        Mat resized = preprocessDetection(img);
        float[] inputData = matToFloatArray(resized);

        // 推理
        long[] shape = {1, 3, resized.height(), resized.width()};
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

        Map<String, OnnxTensor> inputs = Collections.singletonMap("x", tensor);
        OrtSession.Result result = detSession.run(inputs);

        // 后处理
        List<TextBox> boxes = postprocessDetection(result, img.size(), resized.size());

        tensor.close();
        result.close();

        return boxes;
    }

    /**
     * 文本识别
     */
    private String recognizeText(Mat img, String lang) throws OrtException {
        OrtSession session = lang.equals("vi") ? recSessionVi : recSessionCh;
        List<String> characters = lang.equals("vi") ? charsVi : charsCh;

        // 预处理
        Mat resized = preprocessRecognition(img);
        if (resized.width() <= 1 || resized.height() <= 1) {
            System.out.println("⚠️ 无效输入图像，跳过识别");
            return "";
        }
        float[] inputData = matToFloatArray(resized);

        // 推理
        long[] shape = {1, 3, 48, resized.width()};
        if (shape[3] <= 1) {
            System.out.println("⚠️ 非法Tensor shape: " + Arrays.toString(shape));
            return "";
        }
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

        Map<String, OnnxTensor> inputs = Collections.singletonMap("x", tensor);
        OrtSession.Result result = session.run(inputs);

        // CTC解码
        String text = ctcDecode(result, characters);

        tensor.close();
        result.close();

        return text;
    }

    /**
     * 检测预处理
     */
    private Mat preprocessDetection(Mat img) {
        Mat processed = new Mat();

        int maxSize = 960;
        double ratio = Math.min(maxSize / (double) img.width(), maxSize / (double) img.height());

        int newWidth = ((int) (img.width() * ratio) / 32) * 32;
        int newHeight = ((int) (img.height() * ratio) / 32) * 32;

        if (newWidth < 32) newWidth = 32;
        if (newHeight < 32) newHeight = 32;

        Imgproc.resize(img, processed, new Size(newWidth, newHeight));
        Imgproc.cvtColor(processed, processed, Imgproc.COLOR_BGR2RGB);
        processed.convertTo(processed, CvType.CV_32F, 1.0 / 255.0);

        Core.subtract(processed, new Scalar(0.5, 0.5, 0.5), processed);
        Core.divide(processed, new Scalar(0.5, 0.5, 0.5), processed);

        return processed;
    }

    /**
     * 识别预处理
     */
    // --- 修正版 ---
    private Mat preprocessRecognition(Mat img) {
        System.out.println("⏩ preprocessRecognition input: " + img.width() + "x" + img.height());
        Mat processed = new Mat();

        int targetHeight = 48;
        int targetWidth = (int) Math.round(img.width() * (targetHeight / (double) img.height()));

        if (targetWidth > 320) targetWidth = 320;
        if (targetWidth < 16) targetWidth = 16; // 最小宽度16，保证卷积不出错

        System.out.println("➡️ resize to: " + targetWidth + "x" + targetHeight);

        Imgproc.resize(img, processed, new Size(targetWidth, targetHeight));
        Imgproc.cvtColor(processed, processed, Imgproc.COLOR_BGR2RGB);
        processed.convertTo(processed, CvType.CV_32F, 1.0 / 255.0);

        Core.subtract(processed, new Scalar(0.5, 0.5, 0.5), processed);
        Core.divide(processed, new Scalar(0.5, 0.5, 0.5), processed);

        return processed;
    }



    /**
     * Mat转float数组（CHW格式）
     */
    private float[] matToFloatArray(Mat mat) {
        int channels = mat.channels();
        int height = mat.height();
        int width = mat.width();
        float[] data = new float[channels * height * width];

        for (int c = 0; c < channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    double[] pixel = mat.get(h, w);
                    data[c * height * width + h * width + w] = (float) pixel[c];
                }
            }
        }

        return data;
    }

    /**
     * DBNet后处理 - 完整实现
     */
    private List<TextBox> postprocessDetection(OrtSession.Result result, Size originalSize, Size modelSize) {
        List<TextBox> textBoxes = new ArrayList<>();

        try {
            // 获取输出: [1, 1, H, W]
            float[][][][] output = (float[][][][]) result.get(0).getValue();

            int height = output[0][0].length;
            int width = output[0][0][0].length;

            // 创建概率图
            Mat predMap = new Mat(height, width, CvType.CV_32F);
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    predMap.put(h, w, output[0][0][h][w]);
                }
            }

            // 二值化
            Mat binary = new Mat();
            double thresh = 0.3;
            Core.compare(predMap, new Scalar(thresh), binary, Core.CMP_GT);
            binary.convertTo(binary, CvType.CV_8U);

            // 查找轮廓
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(binary, contours, hierarchy,
                    Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

            // 处理轮廓
            double scaleX = originalSize.width / modelSize.width;
            double scaleY = originalSize.height / modelSize.height;

            for (MatOfPoint contour : contours) {
                double area = Imgproc.contourArea(contour);
                if (area < 9) continue;

                // 获取最小外接矩形
                RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));
                Point[] vertices = new Point[4];
                rect.points(vertices);

                // 计算置信度
                double score = calculateBoxScore(predMap, contour);
                if (score < 0.6) continue;

                // 转换坐标
                float[][] box = new float[4][2];
                for (int i = 0; i < 4; i++) {
                    box[i][0] = (float) (vertices[i].x * scaleX);
                    box[i][1] = (float) (vertices[i].y * scaleY);
                }

                TextBox textBox = new TextBox();
                textBox.points = box;
                textBox.score = score;
                textBoxes.add(textBox);
            }

            predMap.release();
            binary.release();
            hierarchy.release();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return textBoxes;
    }

    /**
     * 计算文本框置信度
     */
    private double calculateBoxScore(Mat predMap, MatOfPoint contour) {
        try {
            Mat mask = Mat.zeros(predMap.size(), CvType.CV_8U);
            List<MatOfPoint> contours = Arrays.asList(contour);
            Imgproc.fillPoly(mask, contours, new Scalar(255));

            Scalar meanScalar = Core.mean(predMap, mask);
            mask.release();

            return meanScalar.val[0];
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * CTC解码 - 完整实现
     */
    private String ctcDecode(OrtSession.Result result, List<String> characters) {
        try {
            // 获取输出: [batch, time_steps, num_classes]
            float[][][] output = (float[][][]) result.get(0).getValue();

            int timeSteps = output[0].length;
            int numClasses = output[0][0].length;

            // 找到每个时间步的最大概率索引
            List<Integer> indices = new ArrayList<>();
            for (int t = 0; t < timeSteps; t++) {
                int maxIndex = 0;
                float maxProb = output[0][t][0];

                for (int c = 1; c < numClasses; c++) {
                    if (output[0][t][c] > maxProb) {
                        maxProb = output[0][t][c];
                        maxIndex = c;
                    }
                }
                indices.add(maxIndex);
            }

            // CTC解码: 去除连续重复 + 去除blank(0)
            StringBuilder text = new StringBuilder();
            int prevIndex = -1;

            for (int index : indices) {
                if (index != prevIndex && index != 0) {
                    if (index < characters.size()) {
                        text.append(characters.get(index));
                    }
                }
                prevIndex = index;
            }

            return text.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 裁剪文本区域
     */
    private Mat cropTextRegion(Mat img, float[][] box) {
        try {
            // 计算边界框
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

            for (float[] point : box) {
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                maxX = Math.max(maxX, point[0]);
                maxY = Math.max(maxY, point[1]);
            }

            int x = (int) Math.max(0, minX);
            int y = (int) Math.max(0, minY);
            int width = (int) Math.min(img.width() - x, maxX - minX);
            int height = (int) Math.min(img.height() - y, maxY - minY);

            if (width <= 0 || height <= 0) {
                return new Mat();
            }

            Rect roi = new Rect(x, y, width, height);
            return new Mat(img, roi);

        } catch (Exception e) {
            e.printStackTrace();
            return new Mat();
        }
    }

    public void close() throws OrtException {
        if (detSession != null) detSession.close();
        if (recSessionCh != null) recSessionCh.close();
        if (recSessionVi != null) recSessionVi.close();
        if (env != null) env.close();
    }

    /**
     * 可视化检测结果（调试用）
     */
    public void visualizeDetection(String imagePath, String outputPath) {
        try {
            Mat img = Imgcodecs.imread(imagePath);
            if (img.empty()) return;

            // 检测文本框
            List<TextBox> boxes = detectText(img);

            // 绘制文本框
            for (TextBox box : boxes) {
                Point[] points = new Point[4];
                for (int i = 0; i < 4; i++) {
                    points[i] = new Point(box.points[i][0], box.points[i][1]);
                }

                for (int i = 0; i < 4; i++) {
                    Imgproc.line(img, points[i], points[(i + 1) % 4],
                            new Scalar(0, 255, 0), 2);
                }

                // 显示置信度
                String scoreText = String.format("%.2f", box.score);
                Imgproc.putText(img, scoreText, points[0],
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5,
                        new Scalar(0, 0, 255), 2);
            }

            // 保存结果
            Imgcodecs.imwrite(outputPath, img);
            System.out.println("✅ 可视化结果已保存: " + outputPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 文本框类
     */
    static class TextBox {
        float[][] points;
        double score;
    }

    /**
     * OCR结果类
     */
    public static class OCRResult {
        public String text;
        public float[][] box;
        public double score;

        @Override
        public String toString() {
            return String.format("Text: %s, Score: %.2f%%", text, score * 100);
        }
    }

    public static void main(String[] args) {
        try {
            PaddleOCRCompleteV3 ocr = new PaddleOCRCompleteV3();

            // 测试越南语识别
            System.out.println("\n=== 越南语OCR测试 ===");
            List<OCRResult> results = ocr.recognize("F:\\JAVAAI\\test_image.jpg", "vi");



            // 测试中文识别
 //            System.out.println("\n=== 中文OCR测试 ===");
//             List<OCRResult> results = ocr.recognize("F:\\JAVAAI\\ca6779ded8e8f77e714607e263a8e41.png", "ch");
//            System.out.println("\n最终结果:");
            for (int i = 0; i < results.size(); i++) {
                System.out.println((i + 1) + ". " + results.get(i));
            }
            ocr.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}