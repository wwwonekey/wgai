package org.jeecg.modules.tab.AIModel.pose;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Point;

/**
 * @author wggg
 * @date 2025/9/26 18:35
 */
@Data
@Slf4j
public class FallDetectionResult {
    private String status; // "站立" 或 "跌倒"
    private float confidence;
    private String reason;
    private boolean isAlert; // 是否需要报警

    public FallDetectionResult(String status, float confidence, String reason, boolean isAlert) {
        this.status = status;
        this.confidence = confidence;
        this.reason = reason;
        this.isAlert = isAlert;
    }




    // 核心跌倒检测算法
    public static FallDetectionResult detectFallOrStand(float[] keypoints, double scale, double dx, double dy) {
        // 关键点索引
        final int NOSE = 0, LEFT_SHOULDER = 5, RIGHT_SHOULDER = 6;
        final int LEFT_HIP = 11, RIGHT_HIP = 12, LEFT_KNEE = 13, RIGHT_KNEE = 14;
        final int LEFT_ANKLE = 15, RIGHT_ANKLE = 16;

        // 将关键点坐标还原到原图
        Point[] points = new Point[17];
        boolean[] visible = new boolean[17];

        for (int i = 0; i < 17; i++) {
            float kx = keypoints[i * 3];
            float ky = keypoints[i * 3 + 1];
            float kv = keypoints[i * 3 + 2];

            double px = (kx - dx) / scale;
            double py = (ky - dy) / scale;

            points[i] = new Point(px, py);
            visible[i] = kv > 0.5;
        }

        try {
            // 1. 身体垂直度检测（最重要指标）
            double bodyVerticalScore = calculateBodyVerticalScore(points, visible, NOSE, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP);

            // 2. 身体长宽比检测
            double bodyAspectScore = calculateBodyAspectScore(points, visible, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, LEFT_ANKLE, RIGHT_ANKLE);

            // 3. 头部高度检测
            double headHeightScore = calculateHeadHeightScore(points, visible, NOSE, LEFT_ANKLE, RIGHT_ANKLE);

            // 综合评分 (权重分配)
            double totalScore = bodyVerticalScore * 0.5 + bodyAspectScore * 0.3 + headHeightScore * 0.2;

            System.out.println(String.format("跌倒检测评分: 垂直度=%.2f, 长宽比=%.2f, 头部高度=%.2f, 总分=%.2f",
                    bodyVerticalScore, bodyAspectScore, headHeightScore, totalScore));

            // 判断阈值
            if (totalScore < 0.4) {
                return new FallDetectionResult("fall", (float) (1.0 - totalScore),
                        "身体水平，疑似跌倒", true);
            } else if (totalScore > 0.7) {
                return new FallDetectionResult("stand", (float) totalScore,
                        "身体垂直，正常站立", true);
            } else {
                return new FallDetectionResult("不确定", (float) totalScore,
                        "姿态不明确", false);
            }

        } catch (Exception e) {
            return new FallDetectionResult("检测错误", 0.0f, "无法分析姿态", false);
        }
    }

    // 计算身体垂直度评分
    private static double calculateBodyVerticalScore(Point[] points, boolean[] visible,
                                                     int nose, int leftShoulder, int rightShoulder, int leftHip, int rightHip) {

        if (!visible[leftShoulder] || !visible[rightShoulder] || !visible[leftHip] || !visible[rightHip]) {
            return 0.5; // 关键点不可见时返回中等分数
        }

        // 计算肩膀和臀部的中心点
        Point shoulderCenter = new Point(
                (points[leftShoulder].x + points[rightShoulder].x) / 2,
                (points[leftShoulder].y + points[rightShoulder].y) / 2
        );

        Point hipCenter = new Point(
                (points[leftHip].x + points[rightHip].x) / 2,
                (points[leftHip].y + points[rightHip].y) / 2
        );

        // 计算躯干的垂直度
        double torsoLength = Math.abs(shoulderCenter.y - hipCenter.y);
        double torsoWidth = Math.abs(shoulderCenter.x - hipCenter.x);

        if (torsoLength < 10) return 0.0; // 避免除零错误

        // 垂直度比值：越接近垂直，比值越大
        double verticalRatio = torsoLength / (torsoWidth + 1);

        // 转换为0-1评分
        return Math.min(1.0, verticalRatio / 3.0);
    }

    // 计算身体长宽比评分
    private static double calculateBodyAspectScore(Point[] points, boolean[] visible,
                                                   int leftShoulder, int rightShoulder, int leftHip, int rightHip, int leftAnkle, int rightAnkle) {

        if (!visible[leftShoulder] || !visible[rightShoulder]) {
            return 0.5;
        }

        // 计算身体的边界框
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;

        int[] keyPointsToCheck = {leftShoulder, rightShoulder, leftHip, rightHip, leftAnkle, rightAnkle};

        for (int idx : keyPointsToCheck) {
            if (visible[idx]) {
                minX = Math.min(minX, points[idx].x);
                maxX = Math.max(maxX, points[idx].x);
                minY = Math.min(minY, points[idx].y);
                maxY = Math.max(maxY, points[idx].y);
            }
        }

        double bodyWidth = maxX - minX;
        double bodyHeight = maxY - minY;

        if (bodyWidth < 10 || bodyHeight < 10) return 0.5;

        // 正常站立时，高度应该大于宽度
        double aspectRatio = bodyHeight / bodyWidth;

        // 站立时比值通常 > 1.5，跌倒时 < 0.8
        if (aspectRatio > 1.5) {
            return 1.0; // 明显站立
        } else if (aspectRatio < 0.8) {
            return 0.0; // 明显跌倒
        } else {
            return (aspectRatio - 0.8) / 0.7; // 中间状态线性插值
        }
    }

    // 计算头部高度评分
    private static double calculateHeadHeightScore(Point[] points, boolean[] visible,
                                                   int nose, int leftAnkle, int rightAnkle) {

        if (!visible[nose]) {
            return 0.5;
        }

        // 找到脚踝的平均高度作为地面参考
        double groundLevel = 0;
        int validAnkles = 0;

        if (visible[leftAnkle]) {
            groundLevel += points[leftAnkle].y;
            validAnkles++;
        }
        if (visible[rightAnkle]) {
            groundLevel += points[rightAnkle].y;
            validAnkles++;
        }

        if (validAnkles == 0) return 0.5;

        groundLevel /= validAnkles;

        // 计算头部相对高度
        double headHeight = groundLevel - points[nose].y; // y坐标越小越高

        if (headHeight < 50) {
            return 0.0; // 头部很低，可能跌倒
        } else if (headHeight > 200) {
            return 1.0; // 头部很高，正常站立
        } else {
            return (headHeight - 50) / 150.0; // 线性插值
        }
    }



}