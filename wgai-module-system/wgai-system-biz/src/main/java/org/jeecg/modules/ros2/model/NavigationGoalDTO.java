package org.jeecg.modules.ros2.model;


import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 导航目标点 DTO
 */
@Data
public class NavigationGoalDTO {

    /**
     * 目标 X 坐标
     */
    @NotNull(message = "X坐标不能为空")
    private Double x;

    /**
     * 目标 Y 坐标
     */
    @NotNull(message = "Y坐标不能为空")
    private Double y;

    /**
     * 目标方向角（弧度）
     */
    private Double theta = 0.0;
}
