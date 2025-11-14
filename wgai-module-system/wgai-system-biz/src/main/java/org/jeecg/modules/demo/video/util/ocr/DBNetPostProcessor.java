package org.jeecg.modules.demo.video.util.ocr;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.*;

/**
 * DBNet后处理 - 提取文本框
 */
public class DBNetPostProcessor {

    private static final double THRESH = 0.3;  // 二值化阈值
    private static final double BOX_THRESH = 0.6;  // 文本框置信度阈值
    private static final int MIN_SIZE = 3;  // 最小文本框尺寸

    /**
     * 后处理检测结果
     */
    public static List<float[][]> postprocess(OnnxValue output, Size originalSize, Size modelSize) {
        List<float[][]> boxes = new ArrayList<>();

        try {
            // 获取输出tensor
            float[][][][] outputArray = (float[][][][]) output.getValue();

            // 输出shape: [batch, 1, height, width]
            int height = outputArray[0][0].length;
            int width = outputArray[0][0][0].length;

            System.out.println("检测输出尺寸: " + width + "x" + height);

            // 创建概率图的Mat
            Mat predMap = new Mat(height, width, CvType.CV_32F);
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    predMap.put(h, w, outputArray[0][0][h][w]);
                }
            }

            // 二值化
            Mat binary = new Mat();
            Core.compare(predMap, new Scalar(THRESH), binary, Core.CMP_GT);
            binary.convertTo(binary, CvType.CV_8U);

            // 查找轮廓
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(binary, contours, hierarchy,
                    Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

            System.out.println("找到 " + contours.size() + " 个轮廓");

            // 处理每个轮廓
            double scaleX = originalSize.width / modelSize.width;
            double scaleY = originalSize.height / modelSize.height;

            for (MatOfPoint contour : contours) {
                // 计算轮廓面积
                double area = Imgproc.contourArea(contour);
                if (area < MIN_SIZE) continue;

                // 获取最小外接矩形
                RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));
                Point[] vertices = new Point[4];
                rect.points(vertices);

                // 转换坐标到原图尺寸
                float[][] box = new float[4][2];
                for (int i = 0; i < 4; i++) {
                    box[i][0] = (float) (vertices[i].x * scaleX);
                    box[i][1] = (float) (vertices[i].y * scaleY);
                }

                // 计算平均置信度
                double score = getBoxScore(predMap, contour);
                if (score >= BOX_THRESH) {
                    boxes.add(box);
                }
            }

            // 清理
            predMap.release();
            binary.release();
            hierarchy.release();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return boxes;
    }

    /**
     * 计算文本框的平均置信度
     */
    private static double getBoxScore(Mat predMap, MatOfPoint contour) {
        try {
            // 创建mask
            Mat mask = Mat.zeros(predMap.size(), CvType.CV_8U);
            List<MatOfPoint> contours = new ArrayList<>();
            contours.add(contour);
            Imgproc.fillPoly(mask, contours, new Scalar(255));

            // 计算平均值
            Scalar meanScalar = Core.mean(predMap, mask);
            mask.release();

            return meanScalar.val[0];
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 简化版后处理 - 返回整张图
     */
    public static List<float[][]> postprocessSimple(Size imgSize) {
        List<float[][]> boxes = new ArrayList<>();

        // 返回整张图作为一个文本区域
        float[][] box = new float[][] {
                {0, 0},
                {(float)imgSize.width, 0},
                {(float)imgSize.width, (float)imgSize.height},
                {0, (float)imgSize.height}
        };
        boxes.add(box);

        return boxes;
    }
}