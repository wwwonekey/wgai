package org.jeecg.modules.ros2.config;

/**
 * @author wggg
 * @date 2026/1/23 10:36
 */
public class Constants {

    /**
     * ROS2 话题
     */
    public static class ROS2Topics {
        public static final String CMD_VEL = "/cmd_vel";
        public static final String GOAL_POSE = "/goal_pose";
        public static final String AMCL_POSE = "/amcl_pose";
        public static final String MAP = "/map";
        public static final String PLAN = "/plan";
    }

    /**
     * WebSocket 目标地址
     */
    public static class WebSocketDestinations {
        public static final String VELOCITY = "/topic/velocity";
        public static final String POSITION = "/topic/robot/position";
        public static final String MAP = "/topic/map";
    }

    /**
     * 速度阈值
     */
    public static class VelocityThresholds {
        public static final double MOVING_THRESHOLD = 0.01;
        public static final double MAX_LINEAR_SPEED = 1.0;  // m/s
        public static final double MAX_ANGULAR_SPEED = 1.0; // rad/s
    }
}