package org.jeecg.modules.demo.face.entity;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.*;
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
 * @Description: 人脸图片库
 * @Author: wggg
 * @Date:   2025-11-24
 * @Version: V1.0
 */
@Data
@TableName("tab_face_pic")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="tab_face_pic对象", description="人脸图片库")
public class TabFacePic implements Serializable {
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
	/**人脸名称*/
	@Excel(name = "人脸名称", width = 15)
    @ApiModelProperty(value = "人脸名称")
    private java.lang.String faceName;
	/**人脸图片*/
	@Excel(name = "人脸图片", width = 15)
    @ApiModelProperty(value = "人脸图片")
    private java.lang.String facePic;
	/**512维度数据*/
	@Excel(name = "512维度数据", width = 15)
    @ApiModelProperty(value = "512维度数据")
    private java.lang.String face512;
	/**3D维度数据*/
	@Excel(name = "3D维度数据", width = 15)
    @ApiModelProperty(value = "3D维度数据")
    private java.lang.String face3d;
	/**其他维度数据*/
	@Excel(name = "其他维度数据", width = 15)
    @ApiModelProperty(value = "其他维度数据")
    private java.lang.String faceOther;
	/**是否标注*/
	@Excel(name = "是否标注", width = 15)
    @Dict(dicCode = "push_static")
    @ApiModelProperty(value = "是否标注")
    private Integer isRun;
	/**备注*/
	@Excel(name = "备注", width = 15)
    @ApiModelProperty(value = "备注")
    private java.lang.String remake;

    /**
     * 模型文件
     */
    @Dict(dictTable = "tab_ai_model", dicCode = "id", dicText = "end_name")
    private String modelId;


    @TableField(exist = false)
    private  double maxSimilarity;
}
