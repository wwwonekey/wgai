package org.jeecg.modules.ros2.model;

import lombok.Data;

/**
 * @author wggg
 * @date 2026/1/23 9:51
 */
@Data
public class VelocityCommand  {



    /**
     * 线速度 X 方向（前进/后退） m/s
     */
    private Double linearX;

    /**
     * 线速度 Y 方向（侧向移动） m/s
     */
    private Double linearY;

    /**
     * 线速度 Z 方向（垂直） m/s
     */
    private Double linearZ;

    /**
     * 角速度 X 方向（俯仰） rad/s
     */
    private Double angularX;

    /**
     * 角速度 Y 方向（横滚） rad/s
     */
    private Double angularY;

    /**
     * 角速度 Z 方向（偏航/转向） rad/s
     */
    private Double angularZ;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 运动类型描述
     */
    private String motionType;

    /**
     * 获取线速度（km/h）
     */
    public Double getLinearSpeedKmh() {
        return linearX != null ? linearX * 3.6 : 0.0;
    }

    /**
     * 获取角速度（度/秒）
     */
    public Double getAngularSpeedDegrees() {
        return angularZ != null ? Math.toDegrees(angularZ) : 0.0;
    }

    /**
     * 是否在移动
     */
    public Boolean isMoving() {
        return (linearX != null && Math.abs(linearX) > 0.01)
                || (angularZ != null && Math.abs(angularZ) > 0.01);
    }
}
