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
 * @Description: 报警信息
 * @Author: jeecg-boot
 * @Date:   2025-02-20
 * @Version: V1.0
 */
@Data
@TableName("tab_ai_warning")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="tab_ai_warning对象", description="报警信息")
public class TabAiWarning implements Serializable {
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

    @Excel(name = "预警摄像头", width = 15)
    @ApiModelProperty(value = "预警摄像头")
    private java.lang.String warningName;

	/**预警类型*/
	@Excel(name = "预警类型", width = 15)
    @ApiModelProperty(value = "预警类型")
    private java.lang.Integer warningType;
	/**预警内容*/
	@Excel(name = "预警内容", width = 15)
    @ApiModelProperty(value = "预警内容")
    private java.lang.String warningInfo;
	/**预警视频地址*/
	@Excel(name = "预警视频", width = 15)
    @ApiModelProperty(value = "预警视频")
    private java.lang.String warningCome;

    @Excel(name = "预警图片", width = 15)
    @ApiModelProperty(value = "预警图片")
    private java.lang.String warningPic;
	/**预警时间*/
	@Excel(name = "预警时间", width = 15, format = "yyyy-MM-dd")
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "预警时间")
    private java.util.Date warningTime;
	/**预警状态*/
	@Excel(name = "预警状态", width = 15)
    @ApiModelProperty(value = "预警状态")
    private java.lang.String waringState;
	/**预警算法*/
	@Excel(name = "预警算法", width = 15)
    @ApiModelProperty(value = "预警算法")
    private java.lang.String waringAi;
	/**预警消息*/
	@Excel(name = "预警消息", width = 15)
    @ApiModelProperty(value = "预警消息")
    private java.lang.String waringText;
	/**备注*/
	@Excel(name = "备注", width = 15)
    @ApiModelProperty(value = "备注")
    private java.lang.String remake;
}
