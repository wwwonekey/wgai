package org.jeecg.modules.tab.AIModel.pose;

import lombok.Data;
import org.opencv.core.Point;

/**
 * 动作识别结果类
 * @author wggg
 * @date 2025/9/26 16:45
 */
@Data
public class ActionResult {
    private String actionName; //动作名称
    private float confidence; //信任值
    private String description; //描述动作

    public ActionResult(String actionName, float confidence, String description) {
        this.actionName = actionName;
        this.confidence = confidence;
        this.description = description;
    }

    // 在原有的绘制方法后添加动作识别
    public static ActionResult recognizeAction(float[] keypoints, double scale, double dx, double dy) {
        // 将关键点坐标还原并存储
        Point[] points = new Point[17];
        boolean[] visible = new boolean[17];

        for (int i = 0; i < 17; i++) {
            float kx = keypoints[i * 3];     // x坐标
            float ky = keypoints[i * 3 + 1]; // y坐标
            float kv = keypoints[i * 3 + 2]; // 可见性

            // 还原到原图坐标
            double px = (kx - dx) / scale;
            double py = (ky - dy) / scale;

            points[i] = new Point(px, py);
            visible[i] = kv > 0.5;
        }

        // 执行动作识别
        return classifyAction(points, visible);
    }

    // 核心动作分类方法
    private static ActionResult classifyAction(Point[] points, boolean[] visible) {
        // 关键点索引定义
        final int NOSE = 0, LEFT_EYE = 1, RIGHT_EYE = 2, LEFT_EAR = 3, RIGHT_EAR = 4;
        final int LEFT_SHOULDER = 5, RIGHT_SHOULDER = 6, LEFT_ELBOW = 7, RIGHT_ELBOW = 8;
        final int LEFT_WRIST = 9, RIGHT_WRIST = 10, LEFT_HIP = 11, RIGHT_HIP = 12;
        final int LEFT_KNEE = 13, RIGHT_KNEE = 14, LEFT_ANKLE = 15, RIGHT_ANKLE = 16;

        try {
            // 1. 检测站立姿势
            ActionResult standingResult = detectStanding(points, visible, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE);
            if (standingResult != null) return standingResult;

            // 2. 检测坐着姿势
            ActionResult sittingResult = detectSitting(points, visible, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE);
            if (sittingResult != null) return sittingResult;

            // 3. 检测举手动作
            ActionResult raisingHandResult = detectRaisingHands(points, visible, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST, NOSE);
            if (raisingHandResult != null) return raisingHandResult;

            // 4. 检测运动姿势
            ActionResult exerciseResult = detectExercisePoses(points, visible, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP);
            if (exerciseResult != null) return exerciseResult;

            // 5. 检测行走/跑步
            ActionResult walkingResult = detectWalkingRunning(points, visible, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE);
            if (walkingResult != null) return walkingResult;

            // 6. 检测躺下
            ActionResult lyingResult = detectLying(points, visible, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP);
            if (lyingResult != null) return lyingResult;

            return new ActionResult("未知动作", 0.3f, "无法识别当前姿势");

        } catch (Exception e) {
            return new ActionResult("识别错误", 0.0f, "动作识别过程中出现错误");
        }
    }

    // 检测站立姿势
    private static ActionResult detectStanding(Point[] points, boolean[] visible, int leftHip, int rightHip, int leftKnee, int rightKnee, int leftAnkle, int rightAnkle) {
        if (!visible[leftHip] || !visible[rightHip] || !visible[leftKnee] || !visible[rightKnee]) {
            return null;
        }

        // 计算躯干垂直度（臀部到膝盖的角度）
        double leftLegAngle = calculateAngle(points[leftHip], points[leftKnee], new Point(points[leftKnee].x, points[leftKnee].y + 100));
        double rightLegAngle = calculateAngle(points[rightHip], points[rightKnee], new Point(points[rightKnee].x, points[rightKnee].y + 100));

        // 检查腿部是否相对垂直（角度接近180度或接近0度）
        boolean leftLegStraight = Math.abs(leftLegAngle - 180) < 30 || leftLegAngle < 30;
        boolean rightLegStraight = Math.abs(rightLegAngle - 180) < 30 || rightLegAngle < 30;

        // 检查双脚是否在合理的水平线上
        if (visible[leftAnkle] && visible[rightAnkle]) {
            double ankleHeightDiff = Math.abs(points[leftAnkle].y - points[rightAnkle].y);
            double bodyHeight = Math.abs(points[leftHip].y - points[leftAnkle].y);

            if (leftLegStraight && rightLegStraight && ankleHeightDiff < bodyHeight * 0.1) {
                return new ActionResult("站立", 0.85f, "双腿直立，身体保持垂直");
            }
        }

        return null;
    }

    // 检测坐着姿势
    private static ActionResult detectSitting(Point[] points, boolean[] visible, int leftHip, int rightHip, int leftKnee, int rightKnee) {
        if (!visible[leftHip] || !visible[rightHip] || !visible[leftKnee] || !visible[rightKnee]) {
            return null;
        }

        // 计算大腿与小腿的角度
        double leftKneeAngle = calculateAngle(points[leftHip], points[leftKnee], new Point(points[leftKnee].x + 100, points[leftKnee].y));
        double rightKneeAngle = calculateAngle(points[rightHip], points[rightKnee], new Point(points[rightKnee].x + 100, points[rightKnee].y));

        // 坐着时膝盖角度通常在60-120度之间
        boolean leftKneeBent = leftKneeAngle > 60 && leftKneeAngle < 120;
        boolean rightKneeBent = rightKneeAngle > 60 && rightKneeAngle < 120;

        // 检查臀部是否低于膝盖
        boolean hipsLowerThanKnees = points[leftHip].y > points[leftKnee].y && points[rightHip].y > points[rightKnee].y;

        if (leftKneeBent && rightKneeBent && hipsLowerThanKnees) {
            return new ActionResult("坐着", 0.80f, "膝盖弯曲，呈坐姿");
        }

        return null;
    }

    // 检测举手动作
    private static ActionResult detectRaisingHands(Point[] points, boolean[] visible, int leftShoulder, int rightShoulder, int leftElbow, int rightElbow, int leftWrist, int rightWrist, int nose) {
        if (!visible[leftShoulder] || !visible[rightShoulder] || !visible[nose]) {
            return null;
        }

        double shoulderLevel = (points[leftShoulder].y + points[rightShoulder].y) / 2;
        double headLevel = points[nose].y;

        int raisedHands = 0;
        String description = "";

        // 检查左手
        if (visible[leftWrist]) {
            if (points[leftWrist].y < headLevel) {
                raisedHands++;
                description += "左手举起 ";
            }
        }

        // 检查右手
        if (visible[rightWrist]) {
            if (points[rightWrist].y < headLevel) {
                raisedHands++;
                description += "右手举起 ";
            }
        }

        if (raisedHands == 2) {
            return new ActionResult("双手举起", 0.90f, "双手高举过头");
        } else if (raisedHands == 1) {
            return new ActionResult("单手举起", 0.85f, description.trim());
        }

        // 检查挥手动作（手在肩膀水平附近）
        if (visible[leftWrist] && visible[rightWrist]) {
            boolean leftHandAtShoulder = Math.abs(points[leftWrist].y - shoulderLevel) < 50;
            boolean rightHandAtShoulder = Math.abs(points[rightWrist].y - shoulderLevel) < 50;

            if (leftHandAtShoulder || rightHandAtShoulder) {
                return new ActionResult("挥手", 0.75f, "手部在肩膀水平位置");
            }
        }

        return null;
    }

    // 检测运动姿势
    private static ActionResult detectExercisePoses(Point[] points, boolean[] visible, int leftShoulder, int rightShoulder, int leftElbow, int rightElbow, int leftWrist, int rightWrist, int leftHip, int rightHip) {
        // 检测俯卧撑姿势
        if (visible[leftShoulder] && visible[rightShoulder] && visible[leftWrist] && visible[rightWrist]) {
            // 检查手臂是否支撑身体（手腕在肩膀下方）
            boolean handsSupporting = points[leftWrist].y > points[leftShoulder].y && points[rightWrist].y > points[rightShoulder].y;

            // 检查身体是否水平
            double shoulderHipAngle = Math.abs(points[leftShoulder].y - points[leftHip].y);
            boolean bodyHorizontal = shoulderHipAngle < 100; // 相对水平

            if (handsSupporting && bodyHorizontal) {
                return new ActionResult("俯卧撑", 0.80f, "双手支撑，身体保持水平");
            }
        }

        // 检测瑜伽/伸展姿势
        if (visible[leftElbow] && visible[rightElbow] && visible[leftWrist] && visible[rightWrist]) {
            double leftArmAngle = calculateAngle(points[leftShoulder], points[leftElbow], points[leftWrist]);
            double rightArmAngle = calculateAngle(points[rightShoulder], points[rightElbow], points[rightWrist]);

            // 检查手臂是否伸展（角度接近180度）
            boolean armsExtended = (leftArmAngle > 160 && leftArmAngle < 200) || (rightArmAngle > 160 && rightArmAngle < 200);

            if (armsExtended) {
                return new ActionResult("伸展运动", 0.75f, "手臂伸展姿势");
            }
        }

        return null;
    }

    // 检测行走/跑步
    private static ActionResult detectWalkingRunning(Point[] points, boolean[] visible, int leftHip, int rightHip, int leftKnee, int rightKnee, int leftAnkle, int rightAnkle) {
        if (!visible[leftHip] || !visible[rightHip] || !visible[leftKnee] || !visible[rightKnee]) {
            return null;
        }

        // 检查腿部是否不对称（一条腿在前，一条腿在后）
        double legPositionDiff = Math.abs(points[leftKnee].y - points[rightKnee].y);
        double hipWidth = Math.abs(points[leftHip].x - points[rightHip].x);

        // 检查膝盖弯曲程度
        double leftKneeAngle = calculateAngle(points[leftHip], points[leftKnee], new Point(points[leftKnee].x, points[leftKnee].y + 100));
        double rightKneeAngle = calculateAngle(points[rightHip], points[rightKnee], new Point(points[rightKnee].x, points[rightKnee].y + 100));

        boolean oneKneeBent = (leftKneeAngle < 170 && leftKneeAngle > 30) || (rightKneeAngle < 170 && rightKneeAngle > 30);
        boolean legsAsymmetric = legPositionDiff > hipWidth * 0.3;

        if (oneKneeBent && legsAsymmetric) {
            return new ActionResult("行走/跑步", 0.70f, "腿部呈现行走姿态");
        }

        return null;
    }

    // 检测躺下姿势
    private static ActionResult detectLying(Point[] points, boolean[] visible, int leftShoulder, int rightShoulder, int leftHip, int rightHip) {
        if (!visible[leftShoulder] || !visible[rightShoulder] || !visible[leftHip] || !visible[rightHip]) {
            return null;
        }

        // 计算身体的水平程度
        double shoulderLine = Math.abs(points[leftShoulder].y - points[rightShoulder].y);
        double hipLine = Math.abs(points[leftHip].y - points[rightHip].y);
        double bodyAngle = Math.abs((points[leftShoulder].y + points[rightShoulder].y) / 2 - (points[leftHip].y + points[rightHip].y) / 2);

        // 检查身体是否接近水平
        boolean bodyHorizontal = shoulderLine < 50 && hipLine < 50 && bodyAngle < 100;

        if (bodyHorizontal) {
            return new ActionResult("躺下", 0.75f, "身体呈水平姿势");
        }

        return null;
    }

    // 计算三点间的角度
    private static double calculateAngle(Point a, Point b, Point c) {
        double ab = Math.sqrt(Math.pow(b.x - a.x, 2) + Math.pow(b.y - a.y, 2));
        double bc = Math.sqrt(Math.pow(c.x - b.x, 2) + Math.pow(c.y - b.y, 2));
        double ac = Math.sqrt(Math.pow(c.x - a.x, 2) + Math.pow(c.y - a.y, 2));

        double angle = Math.acos((ab * ab + bc * bc - ac * ac) / (2 * ab * bc));
        return Math.toDegrees(angle);
    }



}
