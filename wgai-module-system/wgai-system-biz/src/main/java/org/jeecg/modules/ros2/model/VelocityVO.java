package org.jeecg.modules.ros2.model;

import lombok.Data;

/**
 * 速度视图对象（返回给前端）
 */
@Data
public class VelocityVO {

    /**
     * 线速度 (m/s)
     */
    private Double linearX;

    /**
     * 角速度 (rad/s)
     */
    private Double angularZ;

    /**
     * 线速度 (km/h)
     */
    private Double linearSpeedKmh;

    /**
     * 角速度 (度/秒)
     */
    private Double angularSpeedDegrees;

    /**
     * 运动类型
     */
    private String motionType;

    /**
     * 是否在移动
     */
    private Boolean isMoving;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 从实体类转换
     */
    public static VelocityVO fromEntity(VelocityCommand entity) {
        VelocityVO vo = new VelocityVO();
        vo.setLinearX(entity.getLinearX());
        vo.setAngularZ(entity.getAngularZ());
        vo.setLinearSpeedKmh(entity.getLinearSpeedKmh());
        vo.setAngularSpeedDegrees(entity.getAngularSpeedDegrees());
        vo.setMotionType(entity.getMotionType());
        vo.setIsMoving(entity.isMoving());
        vo.setTimestamp(entity.getTimestamp());
        return vo;
    }
}
