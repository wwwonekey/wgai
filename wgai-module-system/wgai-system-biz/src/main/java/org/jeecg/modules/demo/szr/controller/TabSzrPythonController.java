package org.jeecg.modules.demo.szr.controller;

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
import org.jeecg.modules.demo.szr.entity.TabSzrPython;
import org.jeecg.modules.demo.szr.service.ITabSzrPythonService;

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
 * @Description: 数字人训练脚本
 * @Author: wggg
 * @Date:   2025-04-17
 * @Version: V1.0
 */
@Api(tags="数字人训练脚本")
@RestController
@RequestMapping("/szr/tabSzrPython")
@Slf4j
public class TabSzrPythonController extends JeecgController<TabSzrPython, ITabSzrPythonService> {
	@Autowired
	private ITabSzrPythonService tabSzrPythonService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tabSzrPython
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "数字人训练脚本-分页列表查询")
	@ApiOperation(value="数字人训练脚本-分页列表查询", notes="数字人训练脚本-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TabSzrPython>> queryPageList(TabSzrPython tabSzrPython,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<TabSzrPython> queryWrapper = QueryGenerator.initQueryWrapper(tabSzrPython, req.getParameterMap());
		Page<TabSzrPython> page = new Page<TabSzrPython>(pageNo, pageSize);
		IPage<TabSzrPython> pageList = tabSzrPythonService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	 /***
	  * 执行脚本 训练模型
	  * @param id 模型id
	  * @return
	  */
	 @AutoLog(value = "训练脚本模板-训练模型")
	 @ApiOperation(value="训练脚本模板-训练模型", notes="训练脚本模板-训练模型")
	 //@RequiresPermissions("org.jeecg.modules.demo:tab_train_python:add")
	 @GetMapping(value = "/startPy")
	 public Result<String> add(@RequestParam(name="id",required=true) String id) {

		 return  tabSzrPythonService.startPy(id,null);
	 }


	 /***
	  * 执行脚本 训练模型
	  * @param id 模型id
	  * @return
	  */
	 @AutoLog(value = "训练脚本模板-训练模型")
	 @ApiOperation(value="训练脚本模板-训练模型", notes="训练脚本模板-训练模型")
	 //@RequiresPermissions("org.jeecg.modules.demo:tab_train_python:add")
	 @GetMapping(value = "/testStar")
	 public Result<String> testStar(@RequestParam(name="id",required=true) String id) {

		 return  tabSzrPythonService.testStar(id,null);
	 }
	/**
	 *   添加
	 *
	 * @param tabSzrPython
	 * @return
	 */
	@AutoLog(value = "数字人训练脚本-添加")
	@ApiOperation(value="数字人训练脚本-添加", notes="数字人训练脚本-添加")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_python:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TabSzrPython tabSzrPython) {
		tabSzrPythonService.save(tabSzrPython);
		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param tabSzrPython
	 * @return
	 */
	@AutoLog(value = "数字人训练脚本-编辑")
	@ApiOperation(value="数字人训练脚本-编辑", notes="数字人训练脚本-编辑")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_python:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TabSzrPython tabSzrPython) {
		tabSzrPythonService.updateById(tabSzrPython);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "数字人训练脚本-通过id删除")
	@ApiOperation(value="数字人训练脚本-通过id删除", notes="数字人训练脚本-通过id删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_python:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tabSzrPythonService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "数字人训练脚本-批量删除")
	@ApiOperation(value="数字人训练脚本-批量删除", notes="数字人训练脚本-批量删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_python:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tabSzrPythonService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "数字人训练脚本-通过id查询")
	@ApiOperation(value="数字人训练脚本-通过id查询", notes="数字人训练脚本-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TabSzrPython> queryById(@RequestParam(name="id",required=true) String id) {
		TabSzrPython tabSzrPython = tabSzrPythonService.getById(id);
		if(tabSzrPython==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tabSzrPython);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tabSzrPython
    */
    //@RequiresPermissions("org.jeecg.modules.demo:tab_szr_python:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TabSzrPython tabSzrPython) {
        return super.exportXls(request, tabSzrPython, TabSzrPython.class, "数字人训练脚本");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    //@RequiresPermissions("tab_szr_python:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TabSzrPython.class);
    }

}
