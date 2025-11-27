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
 * @Description: 数字人
 * @Author: wggg
 * @Date:   2025-04-17
 * @Version: V1.0
 */
@Data
@TableName("tab_szr_video")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="tab_szr_video对象", description="数字人")
public class TabSzrVideo implements Serializable {
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
	/**数字人名称*/
	@Excel(name = "数字人名称", width = 15)
    @ApiModelProperty(value = "数字人名称")
    private java.lang.String szrName;
	/**数字人图片*/
	@Excel(name = "数字人图片", width = 15)
    @ApiModelProperty(value = "数字人图片")
    private java.lang.String szrIcon;
	/**数字人视频*/
	@Excel(name = "数字人视频", width = 15)
    @ApiModelProperty(value = "数字人视频")
    private java.lang.String szrVideo;
	/**数字人简介*/
	@Excel(name = "数字人简介", width = 15)
    @ApiModelProperty(value = "数字人简介")
    private java.lang.String szrText;
	/**备注*/
	@Excel(name = "备注", width = 15)
    @ApiModelProperty(value = "备注")
    private java.lang.String szrOther;
	/**数字人目录*/
	@Excel(name = "数字人目录", width = 15)
    @ApiModelProperty(value = "数字人目录")
    private java.lang.String szrPath;
	/**训练状态*/
	@Excel(name = "训练状态", width = 15)
    @ApiModelProperty(value = "训练状态")
    private java.lang.Integer starFlag;
	/**是否可用*/
	@Excel(name = "是否可用", width = 15)
    @ApiModelProperty(value = "是否可用")
    private java.lang.Integer isOk;
}
