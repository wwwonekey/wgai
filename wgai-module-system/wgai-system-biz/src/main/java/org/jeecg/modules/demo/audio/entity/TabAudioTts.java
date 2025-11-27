package org.jeecg.modules.demo.audio.entity;

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
 * @Description: 文本转TTS
 * @Author: wggg
 * @Date:   2025-03-20
 * @Version: V1.0
 */
@Data
@TableName("tab_audio_tts")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="tab_audio_tts对象", description="文本转TTS")
public class TabAudioTts implements Serializable {
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
	/**语音类型*/
	@Excel(name = "语音类型", width = 15, dicCode = "audio_type")
	@Dict(dicCode = "audio_type")
    @ApiModelProperty(value = "语音类型")
    private java.lang.String audioType;
	/**语音名称*/
	@Excel(name = "语音名称", width = 15)
    @ApiModelProperty(value = "语音名称")
    private java.lang.String audioName;
	/**模型文件*/
	@Excel(name = "模型文件", width = 15)
    @ApiModelProperty(value = "模型文件")
    private java.lang.String audioModel;
	/**token文件*/
	@Excel(name = "token文件", width = 15)
    @ApiModelProperty(value = "token文件")
    private java.lang.String audioToken;
	/**lexicon文件*/
	@Excel(name = "lexicon文件", width = 15)
    @ApiModelProperty(value = "lexicon文件")
    private java.lang.String audioLexicon;
	/**Dict目录*/
	@Excel(name = "Dict目录", width = 15)
    @ApiModelProperty(value = "Dict目录")
    private java.lang.String dictDir;
	/**fsts多文件地址*/
	@Excel(name = "fsts多文件地址", width = 15)
    @ApiModelProperty(value = "fsts多文件地址")
    private java.lang.String ruleFasts;
	/**线程数*/
	@Excel(name = "线程数", width = 15)
    @ApiModelProperty(value = "线程数")
    private java.lang.Integer threadNum;
	/**音色下标*/
	@Excel(name = "音色下标", width = 15)
    @ApiModelProperty(value = "音色下标")
    private java.lang.Integer audioSid;
	/**语音速度*/
	@Excel(name = "语音速度", width = 15)
    @ApiModelProperty(value = "语音速度")
    private java.lang.Double audioSpeed;
	/**保存地址*/
	@Excel(name = "保存地址", width = 15)
    @ApiModelProperty(value = "保存地址")
    private java.lang.String savePath;
	/**文本转语音内容*/
	@Excel(name = "文本转语音内容", width = 15)
    @ApiModelProperty(value = "文本转语音内容")
    private java.lang.String audioText;
}
