package org.jeecg.modules.demo.face.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.demo.face.entity.FaceBox;
import org.jeecg.modules.demo.face.entity.TabFacePic;
import org.jeecg.modules.demo.face.mapper.TabFacePicMapper;
import org.jeecg.modules.demo.face.service.ITabFacePicService;
import org.jeecg.modules.demo.train.service.impl.TabModelTryServiceImpl;
import org.jeecg.modules.tab.AIModel.AIModelYolo3;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.jeecg.modules.tab.mapper.TabAiModelMapper;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jeecg.modules.tab.AIModel.AIModelYolo3.detectFaces;
import static org.jeecg.modules.tab.AIModel.AIModelYolo3.extractFaceFeature;

/**
 * @Description: 人脸图片库
 * @Author: wggg
 * @Date:   2025-11-24
 * @Version: V1.0
 */
@Slf4j
@Service
public class TabFacePicServiceImpl extends ServiceImpl<TabFacePicMapper, TabFacePic> implements ITabFacePicService {


    @Autowired
    TabAiModelMapper tabAiModelMapper;

    @Value("${jeecg.path.upload}")
    private String upLoadPath;
    @Override
    public boolean saveBatchZip(TabFacePic tabFacePic) {
        try {

            Map<String, Object> map =TabModelTryServiceImpl.unzipFiles(upLoadPath+"/"+tabFacePic.getFacePic(),upLoadPath);
            List<String> list = (List<String>) map.get("list");
            List<String> changeFile = TabModelTryServiceImpl.changeFileName(list, false, upLoadPath + "/" + tabFacePic.getFaceName(), upLoadPath,null,false);
            List<TabFacePic> tabFacePicList=new ArrayList<>();
            for (int i = 0; i < changeFile.size(); i++) {
                TabFacePic tabFacePic1=new TabFacePic();
                tabFacePic1.setModelId(tabFacePic.getModelId());
                tabFacePic1.setFaceName(tabFacePic.getFaceName());
                tabFacePic1.setFacePic(tabFacePic.getFaceName()+"/"+changeFile.get(i));
                tabFacePic1.setRemake(tabFacePic.getRemake());
                tabFacePicList.add(tabFacePic1);
            }
            this.saveBatch(tabFacePicList);

        }catch (Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean train(String id) {




        return false;
    }

    @Override
    public boolean Batchtrain(List<String> id) throws Exception {

        List<TabFacePic> list=this.listByIds(id);
        TabAiModel tabAiModel=tabAiModelMapper.selectById(list.get(0).getModelId());
        boolean gpuFlag=false;
        if(tabAiModel.getModelJmType()!=null&&tabAiModel.getModelJmType()==1){
            log.info("使用 GPU");
            gpuFlag=true;
        }
        String faceDetectionModel =tabAiModel.getAiWeights();  // SCRFD-10G 人脸检测模型
        String faceRecognitionModel = tabAiModel.getEndWeights();   // InsightFace 特征提取模型
        for (TabFacePic faceList:list) {
            String picUrl=faceList.getFacePic();
            log.info("开始处理人脸图片: {}",picUrl );
            log.info("faceDetectionModel: {}",faceDetectionModel );
            log.info("faceRecognitionModel: {}",faceRecognitionModel );
            // 先确认人脸地方
            List<FaceBox> faceBoxes = detectFaces(
                    faceDetectionModel,
                    picUrl,
                    upLoadPath,
                    gpuFlag
            );

            if (faceBoxes.isEmpty()) {
                log.warn("图片中未检测到人脸: {}", picUrl);
                continue;
            }
            log.info("检测到 {} 个人脸", faceBoxes.size());
            // 步骤2: 选择最大的人脸（通常是主要人脸）
            FaceBox mainFace = selectMainFace(faceBoxes);
            log.info("选择主要人脸: 位置({}, {}), 尺寸: {}x{}, 置信度: {}",
                    mainFace.getX(), mainFace.getY(), mainFace.getWidth(), mainFace.getHeight(), mainFace.getConfidence());

            // 步骤3: 提取人脸特征向量
            float[] faceFeature = extractFaceFeature(
                    faceRecognitionModel,
                    picUrl,
                    mainFace,
                    upLoadPath,
                    false
            );
            String json=JSON.toJSONString(faceFeature);
            faceList.setFace512(json);
            faceList.setFaceOther(JSON.toJSONString(faceBoxes));
            faceList.setIsRun(0);
            //保存人脸数据
            this.updateById(faceList);
        }




        return false;
    }

    /***
     * 识别
     * @param tabFacePic
     * @return
     */
    @Override
    public TabFacePic extractFace(TabFacePic tabFacePic) throws Exception {

        TabAiModel tabAiModel=tabAiModelMapper.selectById(tabFacePic.getModelId());
        String faceDetectionModel =tabAiModel.getAiWeights();  // SCRFD-10G 人脸检测模型
        String faceRecognitionModel = tabAiModel.getEndWeights();   // InsightFace 特征提取模型
        String picUrl=tabFacePic.getFacePic();
        List<FaceBox> faces = detectFaces(faceDetectionModel, picUrl, upLoadPath, false);
        if (!faces.isEmpty()) {
            float[] feature = extractFaceFeature(faceRecognitionModel, picUrl,
                    faces.get(0), upLoadPath, false);

            // 在数据库中搜索匹配的人脸
            TabFacePic matchedFace = recognizeFace(feature, 0.5f);
            if (matchedFace != null) {
                drawFaceBoxAndLabel( picUrl,   faces.get(0), matchedFace.getFaceName(),  upLoadPath);
                System.out.println("识别成功：" + matchedFace.getFaceName());
                matchedFace.setFacePic(picUrl);
                return matchedFace;
            }
        }
        return null;
    }


    // 使用OpenCV绘制人脸边框和标注
    private void drawFaceBoxAndLabel(String picUrl, FaceBox faceBox, String name, String upLoadPath) {
        try {
            // 读取图片
            String imagePath = upLoadPath + "/"+picUrl;
            Mat image = Imgcodecs.imread(imagePath);

            if (image.empty()) {
                log.warn("无法读取图片：" + imagePath);
                return;
            }

            // 绘制矩形边框
            Point pt1 = new Point(faceBox.getX(), faceBox.getY());
            Point pt2 = new Point(faceBox.getX() + faceBox.getWidth(),
                    faceBox.getY() + faceBox.getHeight());
            Imgproc.rectangle(image, pt1, pt2, new Scalar(0, 255, 0), 2);

            // 绘制文字标注
            int baseline[] = new int[1];
            Size textSize = Imgproc.getTextSize(name, Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.8, 2, baseline);

            // 文字位置（边框上方）
            int textX = (int) faceBox.getX();
            int textY = (int) faceBox.getY() - 10;

            // 如果超出图片顶部，则显示在边框下方
            if (textY < textSize.height) {
                textY = (int) (faceBox.getY() + faceBox.getHeight() + textSize.height + 10);
            }

            // 绘制文字背景
            Point bgPt1 = new Point(textX, textY - textSize.height - 5);
            Point bgPt2 = new Point(textX + textSize.width + 10, textY + 5);
            Imgproc.rectangle(image, bgPt1, bgPt2, new Scalar(0, 255, 0), -1);

            // 绘制文字
            Imgproc.putText(image, name, new Point(textX + 5, textY),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.8,
                    new Scalar(255, 255, 255), 2);

            // 直接覆盖原图
            Imgcodecs.imwrite(imagePath, image);

            log.info("已在图片上标注人脸信息：" + name);

        } catch (Exception e) {
            e.printStackTrace();
            log.info("绘制人脸边框失败：" + e.getMessage());
        }
    }

    /**
     * 选择主要人脸（面积最大的）
     */
    public FaceBox selectMainFace(List<FaceBox> faceBoxes) {
        FaceBox mainFace = faceBoxes.get(0);
        double maxArea = mainFace.getWidth() * mainFace.getHeight();

        for (FaceBox box : faceBoxes) {
            double area = box.getWidth() * box.getHeight();
            if (area > maxArea) {
                maxArea = area;
                mainFace = box;
            }
        }

        return mainFace;
    }

    /**
     * 人脸识别：在数据库中查找最相似的人脸
     */
    public TabFacePic recognizeFace(float[] queryFeature, float threshold) {
        List<TabFacePic> allFaces = this.list(new LambdaQueryWrapper<TabFacePic>()
                .eq(TabFacePic::getIsRun, 0)
                .isNotNull(TabFacePic::getFace512));

        double maxSimilarity = 0.0;
        TabFacePic bestMatch = null;

        for (TabFacePic face : allFaces) {
            float[] feature = JSON.parseObject(face.getFace512(), float[].class);
            double similarity = computeSimilarity(queryFeature, feature);
            if (similarity > maxSimilarity && similarity > threshold) {
                maxSimilarity = similarity;
                bestMatch = face;
                bestMatch.setMaxSimilarity(similarity);
            }
        }

        if (bestMatch != null) {

            log.info("✅ 识别成功！相似度: {}, 用户: {}", maxSimilarity, bestMatch.getFaceName());
        } else {
            log.info("❌ 未找到匹配的人脸（阈值: {}）", threshold);
        }

        return bestMatch;
    }


    /**
     * 计算两个特征向量的相似度（余弦相似度）
     */
    public double computeSimilarity(float[] feature1, float[] feature2) {
        if (feature1 == null || feature2 == null || feature1.length != feature2.length) {
            throw new IllegalArgumentException("特征向量无效");
        }

        double dotProduct = 0.0;
        for (int i = 0; i < feature1.length; i++) {
            dotProduct += feature1[i] * feature2[i];
        }
        return dotProduct; // 已归一化，直接返回点积
    }
}
