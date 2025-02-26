package org.jeecg.modules.demo.video.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.demo.video.entity.TabAiClickpicSetting;
import org.jeecg.modules.demo.video.service.ITabAiClickpicSettingService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import com.alibaba.fastjson.JSON;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.aspect.annotation.AutoLog;

 /**
 * @Description: 采集图片配置
 * @Author: jeecg-boot
 * @Date:   2025-02-25
 * @Version: V1.0
 */
@Api(tags="采集图片配置")
@RestController
@RequestMapping("/video/tabAiClickpicSetting")
@Slf4j
public class TabAiClickpicSettingController extends JeecgController<TabAiClickpicSetting, ITabAiClickpicSettingService> {
	@Autowired
	private ITabAiClickpicSettingService tabAiClickpicSettingService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tabAiClickpicSetting
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "采集图片配置-分页列表查询")
	@ApiOperation(value="采集图片配置-分页列表查询", notes="采集图片配置-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TabAiClickpicSetting>> queryPageList(TabAiClickpicSetting tabAiClickpicSetting,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<TabAiClickpicSetting> queryWrapper = QueryGenerator.initQueryWrapper(tabAiClickpicSetting, req.getParameterMap());
		Page<TabAiClickpicSetting> page = new Page<TabAiClickpicSetting>(pageNo, pageSize);
		IPage<TabAiClickpicSetting> pageList = tabAiClickpicSettingService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tabAiClickpicSetting
	 * @return
	 */
	@AutoLog(value = "采集图片配置-添加")
	@ApiOperation(value="采集图片配置-添加", notes="采集图片配置-添加")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_clickpic_setting:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TabAiClickpicSetting tabAiClickpicSetting) {
		tabAiClickpicSettingService.save(tabAiClickpicSetting);
		return Result.OK("添加成功！");
	}


	 @AutoLog(value = "采集图片配置-开始采集")
	 @ApiOperation(value="采集图片配置-开始采集", notes="采集图片配置-开始采集")
	 //@RequiresPermissions("org.jeecg.modules.demo:tab_ai_clickpic_setting:add")
	 @PostMapping(value = "/starupPic")
	 public Result<?> starupPic(@RequestBody TabAiClickpicSetting tabAiClickpicSetting) {

		 return  tabAiClickpicSettingService.startUpPic(tabAiClickpicSetting);
	 }
	
	/**
	 *  编辑
	 *
	 * @param tabAiClickpicSetting
	 * @return
	 */
	@AutoLog(value = "采集图片配置-编辑")
	@ApiOperation(value="采集图片配置-编辑", notes="采集图片配置-编辑")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_clickpic_setting:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TabAiClickpicSetting tabAiClickpicSetting) {
		tabAiClickpicSettingService.updateById(tabAiClickpicSetting);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "采集图片配置-通过id删除")
	@ApiOperation(value="采集图片配置-通过id删除", notes="采集图片配置-通过id删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_clickpic_setting:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tabAiClickpicSettingService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "采集图片配置-批量删除")
	@ApiOperation(value="采集图片配置-批量删除", notes="采集图片配置-批量删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_clickpic_setting:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tabAiClickpicSettingService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "采集图片配置-通过id查询")
	@ApiOperation(value="采集图片配置-通过id查询", notes="采集图片配置-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TabAiClickpicSetting> queryById(@RequestParam(name="id",required=true) String id) {
		TabAiClickpicSetting tabAiClickpicSetting = tabAiClickpicSettingService.getById(id);
		if(tabAiClickpicSetting==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tabAiClickpicSetting);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tabAiClickpicSetting
    */
    //@RequiresPermissions("org.jeecg.modules.demo:tab_ai_clickpic_setting:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TabAiClickpicSetting tabAiClickpicSetting) {
        return super.exportXls(request, tabAiClickpicSetting, TabAiClickpicSetting.class, "采集图片配置");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    //@RequiresPermissions("tab_ai_clickpic_setting:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TabAiClickpicSetting.class);
    }

}
