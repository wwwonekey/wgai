package org.jeecg.modules.demo.szr.entity;

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
 * @Description: 数字人动作
 * @Author: wggg
 * @Date:   2025-04-30
 * @Version: V1.0
 */
@Data
@TableName("tab_szr_dz")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="tab_szr_dz对象", description="数字人动作")
public class TabSzrDz implements Serializable {
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
	/**数字人id*/
	@Excel(name = "数字人id", width = 15, dictTable = "tab_szr_video", dicText = "szr_name", dicCode = "id")
	@Dict(dictTable = "tab_szr_video", dicText = "szr_name", dicCode = "id")
    @ApiModelProperty(value = "数字人id")
    private java.lang.String szrId;
	/**数字人name*/
	@Excel(name = "数字人name", width = 15)
    @ApiModelProperty(value = "数字人name")
    private java.lang.String szrName;
	/**数字人动作*/
	@Excel(name = "数字人动作", width = 15)
    @ApiModelProperty(value = "数字人动作")
    private java.lang.String szrTitle;
	/**数字人文件*/
	@Excel(name = "数字人文件", width = 15)
    @ApiModelProperty(value = "数字人文件")
    private java.lang.String szrFile;
	/**数字人帧率*/
	@Excel(name = "数字人帧率", width = 15)
    @ApiModelProperty(value = "数字人帧率")
    private java.lang.Double szrFps;
	/**背景色*/
	@Excel(name = "背景色", width = 15)
    @ApiModelProperty(value = "背景色")
    private java.lang.String szrColor;
	/**数字人标签*/
	@Excel(name = "数字人标签", width = 15)
    @ApiModelProperty(value = "数字人标签")
    private java.lang.String szrBq;
}
