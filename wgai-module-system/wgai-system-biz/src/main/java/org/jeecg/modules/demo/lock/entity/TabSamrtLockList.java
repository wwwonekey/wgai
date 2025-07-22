package org.jeecg.modules.demo.lock.entity;

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
 * @Description: 智能锁列表
 * @Author: jeecg-boot
 * @Date:   2025-07-07
 * @Version: V1.0
 */
@Data
@TableName("tab_samrt_lock_list")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="tab_samrt_lock_list对象", description="智能锁列表")
public class TabSamrtLockList implements Serializable {
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
	/**廒间名称*/
	@Excel(name = "廒间名称", width = 15)
    @ApiModelProperty(value = "廒间名称")
    private java.lang.String wareId;
	/**锁具编码*/
	@Excel(name = "锁具编码", width = 15)
    @ApiModelProperty(value = "锁具编码")
    private java.lang.String lockUid;
	/**锁具IMEI*/
	@Excel(name = "锁具IMEI", width = 15)
    @ApiModelProperty(value = "锁具IMEI")
    private java.lang.String lockImei;
	/**锁具名称*/
	@Excel(name = "锁具名称", width = 15)
    @ApiModelProperty(value = "锁具名称")
    private java.lang.String lockName;
	/**锁具地址*/
	@Excel(name = "锁具地址", width = 15)
    @ApiModelProperty(value = "锁具地址")
    private java.lang.String lockAddress;
	/**锁具经度*/
	@Excel(name = "锁具经度", width = 15)
    @ApiModelProperty(value = "锁具经度")
    private java.lang.String lockLng;
	/**锁具纬度*/
	@Excel(name = "锁具纬度", width = 15)
    @ApiModelProperty(value = "锁具纬度")
    private java.lang.String lockLag;
	/**锁具协议类型*/
	@Excel(name = "锁具协议类型", width = 15)
    @ApiModelProperty(value = "锁具协议类型")
    private java.lang.String lockIotType;
	/**设备地址(ip+端口)*/
	@Excel(name = "设备地址(ip+端口)", width = 15)
    @ApiModelProperty(value = "设备地址(ip+端口)")
    private java.lang.String lockIp;
	/**用户名*/
	@Excel(name = "用户名", width = 15)
    @ApiModelProperty(value = "用户名")
    private java.lang.String lockUsername;
	/**设备密码*/
	@Excel(name = "设备密码", width = 15)
    @ApiModelProperty(value = "设备密码")
    private java.lang.String lockPassword;
	/**TOPIC*/
	@Excel(name = "TOPIC", width = 15)
    @ApiModelProperty(value = "TOPIC")
    private java.lang.String lockTopic;
}
