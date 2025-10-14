package org.jeecg.modules.tab.AIModel;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecg.modules.demo.video.entity.TabVideoUtil;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.opencv.dnn.Net;

import java.util.List;

/**
 * @author wggg
 * @date 2025/2/24 11:26
 */
@Data
public class NetPush {

    String id;

    Integer index;
    Net net;

    OrtSession session;
    OrtEnvironment env;
    String modelType;

    List<String> claseeNames;

    Integer isBefor;

    String beforText;

    List<NetPush> listNetPush;

    TabAiModel tabAiModel;

    String uploadPath;

    /**是否跟随前置坐标 0 是 1 否*/
    @Dict(dicCode = "push_static")
    @Excel(name = "是否跟随前置坐标", width = 15)
    @ApiModelProperty(value = "是否跟随前置坐标")
    private Integer isFollow;

    @Dict(dicCode = "push_static")
    @Excel(name = "是否跟随前置放大", width = 15)
    @ApiModelProperty(value = "是否跟随前置放大")
    private Integer isBeforZoom;

    @Excel(name = "跟随最大距离", width = 15)
    @ApiModelProperty(value = "跟随最大距离")
    private Integer followPosition;


    @Dict(dicCode = "push_static")
    @Excel(name = "是否识别预警 默认0 1否", width = 15)
    @ApiModelProperty(value = "是否识别预警 默认0 1否")
    private Integer warinngMethod;


    @Excel(name = "未识别到预警文本", width = 15)
    @ApiModelProperty(value = "未识别到预警文本")
    private String noDifText;


    @ApiModelProperty(value = "是否开启区域识别")
    Integer isBy;

    TabVideoUtil tabVideoUtil;
    @ApiModelProperty(value = "识别类型1.图像识别 2.姿态 3.多边形")
    Integer difyType;

}
