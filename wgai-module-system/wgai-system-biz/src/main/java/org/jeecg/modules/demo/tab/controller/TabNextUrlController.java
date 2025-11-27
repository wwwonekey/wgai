package org.jeecg.modules.demo.tab.controller;

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
import org.jeecg.modules.demo.tab.entity.TabNextUrl;
import org.jeecg.modules.demo.tab.service.ITabNextUrlService;

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
 * @Description: 模型下发列表
 * @Author: wggg
 * @Date:   2025-02-25
 * @Version: V1.0
 */
@Api(tags="模型下发列表")
@RestController
@RequestMapping("/tab/tabNextUrl")
@Slf4j
public class TabNextUrlController extends JeecgController<TabNextUrl, ITabNextUrlService> {
	@Autowired
	private ITabNextUrlService tabNextUrlService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tabNextUrl
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "模型下发列表-分页列表查询")
	@ApiOperation(value="模型下发列表-分页列表查询", notes="模型下发列表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TabNextUrl>> queryPageList(TabNextUrl tabNextUrl,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<TabNextUrl> queryWrapper = QueryGenerator.initQueryWrapper(tabNextUrl, req.getParameterMap());
		Page<TabNextUrl> page = new Page<TabNextUrl>(pageNo, pageSize);
		IPage<TabNextUrl> pageList = tabNextUrlService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tabNextUrl
	 * @return
	 */
	@AutoLog(value = "模型下发列表-添加")
	@ApiOperation(value="模型下发列表-添加", notes="模型下发列表-添加")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_next_url:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TabNextUrl tabNextUrl) {
		tabNextUrlService.save(tabNextUrl);
		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param tabNextUrl
	 * @return
	 */
	@AutoLog(value = "模型下发列表-编辑")
	@ApiOperation(value="模型下发列表-编辑", notes="模型下发列表-编辑")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_next_url:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TabNextUrl tabNextUrl) {
		tabNextUrlService.updateById(tabNextUrl);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "模型下发列表-通过id删除")
	@ApiOperation(value="模型下发列表-通过id删除", notes="模型下发列表-通过id删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_next_url:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tabNextUrlService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "模型下发列表-批量删除")
	@ApiOperation(value="模型下发列表-批量删除", notes="模型下发列表-批量删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_next_url:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tabNextUrlService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "模型下发列表-通过id查询")
	@ApiOperation(value="模型下发列表-通过id查询", notes="模型下发列表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TabNextUrl> queryById(@RequestParam(name="id",required=true) String id) {
		TabNextUrl tabNextUrl = tabNextUrlService.getById(id);
		if(tabNextUrl==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tabNextUrl);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tabNextUrl
    */
    //@RequiresPermissions("org.jeecg.modules.demo:tab_next_url:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TabNextUrl tabNextUrl) {
        return super.exportXls(request, tabNextUrl, TabNextUrl.class, "模型下发列表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    //@RequiresPermissions("tab_next_url:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TabNextUrl.class);
    }

}
