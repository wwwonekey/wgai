package org.jeecg.modules.demo.video.entity;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecg.common.aspect.annotation.Dict;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 采集图片配置
 * @Author: wggg
 * @Date:   2025-02-25
 * @Version: V1.0
 */
@Data
@TableName("tab_ai_clickpic_setting")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="tab_ai_clickpic_setting对象", description="采集图片配置")
public class TabAiClickpicSetting implements Serializable {
    private static final long serialVersionUID = 1L;

	/**主键*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "主键")
    private java.lang.String id;
	/**创建人*/
    @ApiModelProperty(value = "创建人")
    private java.lang.String createBy;
	/**创建日期*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建日期")
    private java.util.Date createTime;
	/**更新人*/
    @ApiModelProperty(value = "更新人")
    private java.lang.String updateBy;
	/**更新日期*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新日期")
    private java.util.Date updateTime;
	/**所属部门*/
    @ApiModelProperty(value = "所属部门")
    private java.lang.String sysOrgCode;
	/**所属模型*/
	@Excel(name = "所属模型", width = 15, dictTable = "tab_model_try", dicText = "model_title", dicCode = "id")
	@Dict(dictTable = "tab_model_try", dicText = "model_title", dicCode = "id")
    @ApiModelProperty(value = "所属模型")
    private java.lang.String modelId;
	/**视频类型*/
	@Excel(name = "视频类型", width = 15, dicCode = "video_type")
	@Dict(dicCode = "video_type")
    @ApiModelProperty(value = "视频类型")
    private java.lang.String videoType;
	/**视频地址*/
	@Excel(name = "视频地址", width = 15)
    @ApiModelProperty(value = "视频地址")
    private java.lang.String videoUrl;
	/**间隔帧*/
	@Excel(name = "间隔帧", width = 15)
    @ApiModelProperty(value = "间隔帧")
    private java.lang.Integer interFrameInterval;
	/**采集数量*/
	@Excel(name = "采集数量", width = 15)
    @ApiModelProperty(value = "采集数量")
    private java.lang.Integer picNumber;
	/**保存目录(不勾选图片模型库生效)*/
	@Excel(name = "保存目录(不勾选图片模型库生效)", width = 15)
    @ApiModelProperty(value = "保存目录(不勾选图片模型库生效)")
    private java.lang.String savePath;
	/**运行状态*/
	@Excel(name = "运行状态", width = 15)
    @ApiModelProperty(value = "运行状态")
    private java.lang.String runState;
	/**备注*/
	@Excel(name = "备注", width = 15)
    @ApiModelProperty(value = "备注")
    private java.lang.String remake;
	/**是否覆盖*/
	@Excel(name = "是否覆盖", width = 15)
    @ApiModelProperty(value = "是否覆盖")
    private java.lang.String isCover;
	/**是否放入图片模型库*/
	@Excel(name = "是否放入图片模型库", width = 15)
    @ApiModelProperty(value = "是否放入图片模型库")
    private java.lang.String picModelInster;
}
