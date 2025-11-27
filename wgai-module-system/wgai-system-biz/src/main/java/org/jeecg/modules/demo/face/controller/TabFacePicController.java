package org.jeecg.modules.demo.face.controller;

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
import org.jeecg.modules.demo.face.entity.TabFacePic;
import org.jeecg.modules.demo.face.service.ITabFacePicService;

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
 * @Description: 人脸图片库
 * @Author: wggg
 * @Date:   2025-11-24
 * @Version: V1.0
 */
@Api(tags="人脸图片库")
@RestController
@RequestMapping("/face/tabFacePic")
@Slf4j
public class TabFacePicController extends JeecgController<TabFacePic, ITabFacePicService> {
	@Autowired
	private ITabFacePicService tabFacePicService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tabFacePic
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "人脸图片库-分页列表查询")
	@ApiOperation(value="人脸图片库-分页列表查询", notes="人脸图片库-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TabFacePic>> queryPageList(TabFacePic tabFacePic,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<TabFacePic> queryWrapper = QueryGenerator.initQueryWrapper(tabFacePic, req.getParameterMap());
		Page<TabFacePic> page = new Page<TabFacePic>(pageNo, pageSize);
		IPage<TabFacePic> pageList = tabFacePicService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tabFacePic
	 * @return
	 */
	@AutoLog(value = "人脸图片库-添加")
	@ApiOperation(value="人脸图片库-添加", notes="人脸图片库-添加")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TabFacePic tabFacePic) {
		tabFacePicService.save(tabFacePic);
		return Result.OK("添加成功！");
	}

	 /**
	  *   添加
	  *
	  * @param tabFacePic
	  * @return
	  */
	 @AutoLog(value = "人脸图片库-批量添加")
	 @ApiOperation(value="人脸图片库-批量添加", notes="人脸图片库-添加")
	 //@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:add")
	 @PostMapping(value = "/batchAdd")
	 public Result<String> batchAdd(@RequestBody TabFacePic tabFacePic) {
		 boolean b=tabFacePicService.saveBatchZip(tabFacePic);
		 if(b){
			 return Result.OK("添加成功！");
		 }
		 return Result.error("添加识别！");
	 }


	 /**
	  *   添加
	  *
	  * @param tabFacePic
	  * @return
	  */
	 @AutoLog(value = "人脸图片库-识别")
	 @ApiOperation(value="人脸图片库-识别", notes="人脸图片库-添加")
	 //@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:add")
	 @PostMapping(value = "/extractFaceFeature")
	 public Result<TabFacePic> extractFaceFeature(@RequestBody TabFacePic tabFacePic) throws Exception {
		 TabFacePic face=tabFacePicService.extractFace(tabFacePic);
		 return Result.ok(face);
	 }

	/**
	 *  编辑
	 *
	 * @param tabFacePic
	 * @return
	 */
	@AutoLog(value = "人脸图片库-编辑")
	@ApiOperation(value="人脸图片库-编辑", notes="人脸图片库-编辑")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TabFacePic tabFacePic) {
		tabFacePicService.updateById(tabFacePic);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "人脸图片库-通过id删除")
	@ApiOperation(value="人脸图片库-通过id删除", notes="人脸图片库-通过id删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tabFacePicService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "人脸图片库-批量删除")
	@ApiOperation(value="人脸图片库-批量删除", notes="人脸图片库-批量删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tabFacePicService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}



	 /**
	  *  批量训练
	  *
	  * @param ids
	  * @return
	  */
	 @AutoLog(value = "人脸图片库-批量训练")
	 @ApiOperation(value="人脸图片库-批量训练", notes="人脸图片库-批量训练")
	 //@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:deleteBatch")
	 @RequestMapping(value = "/trainBatch", method = {RequestMethod.PUT,RequestMethod.POST})
	 public Result<String> trainBatch(@RequestParam(name="ids",required=true) String ids) {
		 try {
			 this.tabFacePicService.Batchtrain(Arrays.asList(ids.split(",")));
		 } catch (Exception e) {
			e.printStackTrace();
			 return Result.OK("采集失败!");
		 }
		 return Result.OK("采集成功!");
	 }

	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "人脸图片库-通过id查询")
	@ApiOperation(value="人脸图片库-通过id查询", notes="人脸图片库-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TabFacePic> queryById(@RequestParam(name="id",required=true) String id) {
		TabFacePic tabFacePic = tabFacePicService.getById(id);
		if(tabFacePic==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tabFacePic);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tabFacePic
    */
    //@RequiresPermissions("org.jeecg.modules.demo:tab_face_pic:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TabFacePic tabFacePic) {
        return super.exportXls(request, tabFacePic, TabFacePic.class, "人脸图片库");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    //@RequiresPermissions("tab_face_pic:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TabFacePic.class);
    }

}
