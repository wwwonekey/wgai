package org.jeecg.modules.demo.face.service;

import org.jeecg.modules.demo.face.entity.TabFacePic;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @Description: 人脸图片库
 * @Author: wggg
 * @Date:   2025-11-24
 * @Version: V1.0
 */
public interface ITabFacePicService extends IService<TabFacePic> {

    boolean saveBatchZip(TabFacePic tabFacePic);


    boolean train(String id);

    boolean Batchtrain(List<String> id) throws Exception;


    TabFacePic extractFace(TabFacePic tabFacePic) throws Exception;
}
