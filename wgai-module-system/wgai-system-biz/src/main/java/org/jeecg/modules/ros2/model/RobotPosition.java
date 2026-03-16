package org.jeecg.modules.ros2.model;

import lombok.Data;

/**
 * 机器人位置实体类
 */
@Data
public class RobotPosition {

    private static final long serialVersionUID = 1L;

    /**
     * X 坐标（米）
     */
    private Double x;

    /**
     * Y 坐标（米）
     */
    private Double y;

    /**
     * Z 坐标（米）
     */
    private Double z;

    /**
     * 方向角（弧度）
     */
    private Double theta;

    /**
     * 时间戳
     */
    private Long timestamp;
}
