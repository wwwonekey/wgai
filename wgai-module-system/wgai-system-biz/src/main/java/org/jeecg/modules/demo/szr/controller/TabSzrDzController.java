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
import org.jeecg.modules.demo.szr.entity.TabSzrDz;
import org.jeecg.modules.demo.szr.service.ITabSzrDzService;

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
 * @Description: 数字人动作
 * @Author: wggg
 * @Date:   2025-04-30
 * @Version: V1.0
 */
@Api(tags="数字人动作")
@RestController
@RequestMapping("/szr/tabSzrDz")
@Slf4j
public class TabSzrDzController extends JeecgController<TabSzrDz, ITabSzrDzService> {
	@Autowired
	private ITabSzrDzService tabSzrDzService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tabSzrDz
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "数字人动作-分页列表查询")
	@ApiOperation(value="数字人动作-分页列表查询", notes="数字人动作-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TabSzrDz>> queryPageList(TabSzrDz tabSzrDz,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<TabSzrDz> queryWrapper = QueryGenerator.initQueryWrapper(tabSzrDz, req.getParameterMap());
		Page<TabSzrDz> page = new Page<TabSzrDz>(pageNo, pageSize);
		IPage<TabSzrDz> pageList = tabSzrDzService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tabSzrDz
	 * @return
	 */
	@AutoLog(value = "数字人动作-添加")
	@ApiOperation(value="数字人动作-添加", notes="数字人动作-添加")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_dz:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TabSzrDz tabSzrDz) {
		tabSzrDzService.save(tabSzrDz);
		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param tabSzrDz
	 * @return
	 */
	@AutoLog(value = "数字人动作-编辑")
	@ApiOperation(value="数字人动作-编辑", notes="数字人动作-编辑")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_dz:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TabSzrDz tabSzrDz) {
		tabSzrDzService.updateById(tabSzrDz);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "数字人动作-通过id删除")
	@ApiOperation(value="数字人动作-通过id删除", notes="数字人动作-通过id删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_dz:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tabSzrDzService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "数字人动作-批量删除")
	@ApiOperation(value="数字人动作-批量删除", notes="数字人动作-批量删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_szr_dz:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tabSzrDzService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "数字人动作-通过id查询")
	@ApiOperation(value="数字人动作-通过id查询", notes="数字人动作-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TabSzrDz> queryById(@RequestParam(name="id",required=true) String id) {
		TabSzrDz tabSzrDz = tabSzrDzService.getById(id);
		if(tabSzrDz==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tabSzrDz);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tabSzrDz
    */
    //@RequiresPermissions("org.jeecg.modules.demo:tab_szr_dz:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TabSzrDz tabSzrDz) {
        return super.exportXls(request, tabSzrDz, TabSzrDz.class, "数字人动作");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    //@RequiresPermissions("tab_szr_dz:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TabSzrDz.class);
    }

}
