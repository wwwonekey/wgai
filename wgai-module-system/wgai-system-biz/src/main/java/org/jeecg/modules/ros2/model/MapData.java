package org.jeecg.modules.ros2.model;

import lombok.Data;

/**
 * @author wggg
 * @date 2026/1/23 9:52
 */

@Data
public class MapData {

    /**
     * 地图宽度（像素）
     */
    private Integer width;

    /**
     * 地图高度（像素）
     */
    private Integer height;

    /**
     * 分辨率（米/像素）
     */
    private Float resolution;

    /**
     * 地图数据（-1=未知, 0=自由空间, 100=障碍物）
     */
    private Object data;

    /**
     * 原点 X 坐标
     */
    private Double originX;

    /**
     * 原点 Y 坐标
     */
    private Double originY;
}
