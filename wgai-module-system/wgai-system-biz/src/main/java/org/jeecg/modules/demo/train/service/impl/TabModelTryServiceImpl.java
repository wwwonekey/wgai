package org.jeecg.modules.demo.train.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.demo.easy.entity.TabEasyPic;
import org.jeecg.modules.demo.easy.mapper.TabEasyPicMapper;
import org.jeecg.modules.demo.train.entity.TabModelTry;
import org.jeecg.modules.demo.train.entity.TabModelTryOrg;
import org.jeecg.modules.demo.train.mapper.TabModelTryMapper;
import org.jeecg.modules.demo.train.mapper.TabModelTryOrgMapper;
import org.jeecg.modules.demo.train.service.ITabModelTryService;
import org.jeecg.modules.demo.train.util.picXml;
import org.jeecg.modules.demo.video.entity.TabAiSubscriptionNew;
import org.jeecg.modules.demo.video.util.identifyTypeNew;
import org.jeecg.modules.message.websocket.WebSocket;
import org.jeecg.modules.tab.entity.TabAiModel;
import org.opencv.core.Mat;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.jeecg.modules.demo.train.util.writeXml.writeXml;

/**
 * @Description: 模型预训练
 * @Author: WGAI
 * @Date: 2024-12-17
 * @Version: V1.0
 */
@Slf4j
@Service
public class TabModelTryServiceImpl extends ServiceImpl<TabModelTryMapper, TabModelTry> implements ITabModelTryService {

    @Autowired
    TabModelTryOrgMapper tabModelTryOrgMapper;
    @Autowired
    TabEasyPicMapper tabEasyPicMapper;
    @Autowired
    TabModelTryMapper tabModelTryMapper;

    @Value("${jeecg.path.upload}")
    private String upLoadPath;

    //解压压缩包文件并保存文件
    public static Map<String, Object> unzipFiles(String zipFilePath, String destDir) {
        Map<String, Object> map = new HashMap<>();
        List<String> list = new ArrayList<>();
        File dir = new File(destDir);

        // 创建输出目录如果它不存在
        if (!dir.exists()) dir.mkdirs();

        byte[] buffer = new byte[1024];
        long totalSize = 0;  // 用于存储解压文件的总大小，单位是字节
        try {
            FileInputStream fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                String fileName = ze.getName();

                // 如果这是一个文件夹，就创建它
                if (ze.isDirectory()) {
                    File fmd = new File(destDir + "/" + fileName);
                    fmd.mkdirs();
                } else {
                    FileOutputStream fos = new FileOutputStream(destDir + "/" + fileName);
                    BufferedOutputStream stream = new BufferedOutputStream(fos);
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        stream.write(buffer, 0, len);
                    }
                    stream.close();
                    fos.close();
                    // 累加文件大小
                    totalSize += ze.getSize();
                    // 检查是否为图片文件，如果是则进行处理
                    if (fileName.endsWith(".jpg") || fileName.endsWith(".png")) {
                        // 处理图片文件的代码，例如存储到数据库或做其他操作
                        // 这里只是简单打印文件名作为示例
                        log.info("Image file stored: " + destDir + "/" + fileName);
                        list.add(fileName);
                    }
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将文件大小转换为 MB
        double totalSizeInMB = totalSize / (1024.0 * 1024.0);
        log.info("当前文件大小 " + totalSizeInMB + " MB");
        map.put("list", list);
        map.put("size", String.format("%.2f", totalSizeInMB));
        return map;
    }

    /***
     *针对文件重新编码按照顺序后续训练更便捷
     * @param path 原文件地址
     * @param flag true 覆盖 不覆盖
     * @param folder 目标文件目录
     * @return
     */
    public static synchronized List<String> changeFileName(List<String> path, boolean flag, String folder, String upLoadPath, String picfolader,boolean reZizePic) {
        if (flag) { //从0 开始
            deleteAllFilesInFolder(new File(folder));
        }
        List<String> renamePic = new ArrayList<String>();
        try {
            File newDir = new File(folder);
            if (!newDir.exists()) {
                if (newDir.mkdirs()) {
                    log.info("新目录创建成功！");
                } else {
                    log.error("新目录创建失败！");
                }
            }
            log.info("原始地址{}",folder);

            for (String pathStr : path) {
                String newFileName = sendPicNameNo(folder);
                File newFile = new File(newDir, newFileName); //移动到新目录
                File oldFile = new File(upLoadPath + File.separator + pathStr); //移动到新目录
                if (oldFile.renameTo(newFile)) {
                    log.info("文件成功移动并重命名！{}",newFile);
                } else {
                    log.info("文件移动或重命名失败！");
                }
                if(reZizePic){
                    //移动后文件
                    File Change = new File(folder + File.separator + newFileName);
                    //图片统一存储到700*700
                    BufferedImage inputImage = ImageIO.read(Change);
                    // 创建新的空白图片，大小为 800x800
                    BufferedImage resizedImage = new BufferedImage(700, 700, inputImage.getType());
                    // 使用 Graphics2D 来缩放图片
                    Graphics2D g2d = resizedImage.createGraphics();
                    g2d.drawImage(inputImage, 0, 0, 700, 700, null);
                    g2d.dispose();
                    // 保存修改后的图片
                    ImageIO.write(resizedImage, "png", Change);
                    log.info("图片已成功保存为 700*700 像素");
                }



                renamePic.add(newFileName);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return renamePic;
    }


    /***
     * 文件生成序号
     * @param
     * @param
     * @return
     */
    public static  synchronized  String sendPicNameNo(String folder) {
        int number = getFileNum(folder);  // 获取文件数作为初始值
        String paddedNumber;
        File file;

        do {
            paddedNumber = String.format("%06d", number) + ".png";
            file = new File(folder, paddedNumber);
            number++; // 如果已存在，则顺序号+1，继续尝试
        } while (file.exists());

        return paddedNumber;
    }

    // 递归删除文件夹中的所有文件和子文件夹
    public static void deleteAllFilesInFolder(File folder) {
        if (folder.isDirectory()) {
            // 获取文件夹下的所有文件和子文件夹
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    // 如果是文件，直接删除
                    if (file.isFile()) {
                        if (file.delete()) {
                            log.info("文件 " + file.getName() + " 删除成功！");
                        } else {
                            log.info("文件 " + file.getName() + " 删除失败！");
                        }
                    } else {
                        // 如果是文件夹，递归调用删除方法
                        deleteAllFilesInFolder(file);
                    }
                }
            } else {
                log.info("当前文件夹空空如也");
            }
        }
    }

    //获取文件下文件个数
    public static int getFileNum(String folderPath) {
        File folder = new File(folderPath);  // 替换为你的文件夹路径
        // 获取文件夹中所有文件和子文件夹
        File[] files = folder.listFiles();
        if (files != null) {
            int fileCount = 1;
            // 遍历文件夹中的文件和子文件夹
            for (File file : files) {
                // 只统计文件，不统计子文件夹
                if (file.isFile()) {
                    fileCount++;
                }
            }
            return fileCount;
        } else {
            return 1;
        }
    }

    public static void main(String[] args) {
//        String zipFilePath = "D:\\opt\\upFiles\\temp\\test_1734439662236.zip";
//        String destDir = "D:\\opt\\upFiles\\temp\\test";
//
//        unzipFiles(zipFilePath, destDir);
//        BigDecimal shiji = new BigDecimal("9158");
//        BigDecimal sunhao = new BigDecimal("54883").add(new BigDecimal("-45725"));
//
//        int c = shiji.compareTo(sunhao);
//        if (c <= 0) {// num1 < num2
//            System.out.println(sunhao + "num1 < num2" + shiji);
//        } else {
//            System.out.println(sunhao + " num1 > num2" + shiji);
//        }

// 从文件读取
        String result = readObjectNames("C:\\Users\\Administrator\\Downloads\\000041.xml");
        try {
            System.out.println(processXmlFile("C:\\Users\\Administrator\\Downloads\\000001.xml","1","2"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(result); // 输出: donut,apple,banana,banana,banana,banana,apple,banana

// 从第二个文件读取（空name）
        String result2 = readObjectNames("C:\\Users\\Administrator\\Downloads\\000001.xml");
        System.out.println(result2); // 输出: ""（空字符串）
    }

    @Override
    public synchronized Result<String> savePatch(TabModelTry tabModelTry) {
        try {


            String fileName = tabModelTry.getPicUrl();
            log.info("当前文件名称{}", fileName);
            if (!"zip".equals(fileName.substring(fileName.lastIndexOf(".") + 1))) { //判断后缀是否是zip
                return Result.error("当前文件压缩为不是ZIP 请上传ZIP压缩包");
            }
            //解压压缩包放置
            Map<String, Object> map = unzipFiles(upLoadPath + "/" + fileName, upLoadPath);
            List<String> list = (List<String>) map.get("list");
            Double PicSize = Double.parseDouble(map.get("size").toString());
            String id = UUID.randomUUID().toString().replace("-", "");
            if (StringUtils.isNotEmpty(tabModelTry.getId())) {
                id = tabModelTry.getId();
                if (tabModelTry.getIsInsert().equals("Y")) {//当前覆盖所有图片重新添加
                    List<TabModelTryOrg> tabModelTryOrgList = tabModelTryOrgMapper.selectList(new QueryWrapper<TabModelTryOrg>().eq("model_id", tabModelTry.getId()));
                    if (tabModelTryOrgList.size() > 0) {
                        tabModelTryOrgMapper.deleteBatchIds(tabModelTryOrgList.stream().map(TabModelTryOrg::getId).collect(Collectors.toList()));
                        tabEasyPicMapper.deleteBatchIds(tabModelTryOrgList.stream().map(TabModelTryOrg::getPicId).collect(Collectors.toList()));
                    }
                    tabModelTry.setPicNumber(list.size() + "");
                    tabModelTry.setFileSize(PicSize);
                } else {
                    tabModelTry.setPicNumber(Integer.parseInt(tabModelTry.getPicNumber()) + list.size() + "");
                    tabModelTry.setFileSize(tabModelTry.getFileSize()==null?0:tabModelTry.getFileSize() + PicSize);
                }
                this.updateById(tabModelTry);
            } else {
                tabModelTry.setId(id);
                tabModelTry.setPicNumber(list.size() + "");
                this.save(tabModelTry);
            }

            //文件处理
            List<String> changeFile = changeFileName(list, tabModelTry.getIsInsert().equals("Y"), upLoadPath + "/" + tabModelTry.getPicName(), upLoadPath, tabModelTry.getPicName(),true);
            for (String url : changeFile) {
                String picid = UUID.randomUUID().toString().replace("-", "");
                TabModelTryOrg modelTryOrg = new TabModelTryOrg();
                modelTryOrg.setModelId(id);
                modelTryOrg.setPicId(picid);
                TabEasyPic pic = new TabEasyPic();
                pic.setId(picid);
                pic.setModelId(id);
                pic.setPicUrl(tabModelTry.getPicName() + File.separator + url);
                pic.setMarkType("N");
                pic.setPicType("1");
                pic.setPicName(url);
                pic.setRemake(tabModelTry.getPicName());
                tabEasyPicMapper.insert(pic);
                tabModelTryOrgMapper.insert(modelTryOrg);

            }
            return Result.OK("插入成功");
        } catch (Exception exception) {
            exception.printStackTrace();
            return Result.error("插入失败");
        }
    }

    @Override
    public Result<String> saveMake(List<picXml> picXmll) {
        try {
            log.info("【当前标注内容大小:{}】", picXmll.size());


            TabEasyPic tabEasyPic = tabEasyPicMapper.selectById(picXmll.get(0).getPicId());  //获取图片
            //开始保存xml文件

            String xmlpath = writeXml(upLoadPath, tabEasyPic, picXmll);

            tabEasyPic.setMarkXml(xmlpath);
            tabEasyPic.setMarkType("Y");
            tabEasyPic.setMarkTitle(picXmll.stream().map(picXml::getName).collect(Collectors.joining(",")));
            if (StringUtils.isNotEmpty(picXmll.get(0).getName())) {
                tabEasyPic.setMarkFeature("标注图");
                String jsonString = JSON.toJSONString(picXmll);
                tabEasyPic.setMarkJson(jsonString);
            } else {
                tabEasyPic.setMarkFeature("背景图");
                tabEasyPic.setMarkJson("");
            }


            Integer yesMark = tabModelTryMapper.getMakeNum(tabEasyPic.getModelId(), "Y");
            Integer noMark = tabModelTryMapper.getMakeNum(tabEasyPic.getModelId(), "N");
            Integer sumPic = yesMark + noMark;
            TabModelTry tabModelTry = tabModelTryMapper.selectById(tabEasyPic.getModelId());
            tabModelTry.setMakeNumber(yesMark + "");
            tabModelTry.setPicNumber(sumPic + "");
            tabModelTryMapper.updateById(tabModelTry);

            tabEasyPic.setRemake(tabModelTry.getPicName());
            tabEasyPicMapper.updateById(tabEasyPic);
        } catch (Exception ex) {
            ex.printStackTrace();
            return Result.error("标注保存失败");
        }

        return Result.ok("标注保存成功");
    }

    @Resource
    private WebSocket webSocket;


    @Override
    public Result<String> autoSaveMake(List<TabEasyPic> tabEasyPic, TabAiModel tabAiModel) {

        //自动识别开始
        Thread trainingThread = new Thread(() -> {

            identifyTypeNew identifyTypeNew = new identifyTypeNew();
            int a=1;
            for (TabEasyPic tabEasy : tabEasyPic) {
                String picPath = upLoadPath + File.separator + tabEasy.getPicUrl();
                log.info("当前图片地址{}", picPath);
                Mat image = Imgcodecs.imread(picPath);
                List<String> classNames = null;
                try {
                    classNames = Files.readAllLines(Paths.get(upLoadPath + File.separator + tabAiModel.getAiNameName()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                List<picXml> picXmls = identifyTypeNew.AutoDetectObjectsV5(tabEasy, image, getNetModel(tabAiModel), classNames);
                if(picXmls==null||picXmls.size()<=0){
                    log.info("[当前未识别到,当作背景图]");
                    picXmls=new ArrayList<>();
                    picXml picXml=new picXml();
                    picXml.setPicId(tabEasy.getId());
                    picXmls.add(picXml);
                }
                this.saveMake(picXmls);
                JSONObject jsonObject=new JSONObject();
                jsonObject.put("cmd","auto");
                jsonObject.put("autoList",tabEasyPic.size());
                jsonObject.put("autoNumber",a);
                jsonObject.put("autoSaveMakeId",tabEasy.getModelId());
                jsonObject.put("autoName",tabAiModel.getAiName());
                webSocket.sendMessage(jsonObject.toJSONString());

                a++;
            }

        });
        trainingThread.start();

        return Result.ok("正在处理中");
    }

    private static final ConcurrentHashMap<String, Net> GLOBAL_NET_CACHE = new ConcurrentHashMap<>();

    public Net getNetModel(TabAiModel tabAiModel) {

        Net net = GLOBAL_NET_CACHE.get(tabAiModel.getId());
        if (net == null) { //尽量减少消耗
            if (tabAiModel.getSpareOne().equals("1")) {  //v3
                net = Dnn.readNetFromDarknet(upLoadPath + File.separator + tabAiModel.getAiConfig(), upLoadPath + File.separator + tabAiModel.getAiWeights());
            } else if (tabAiModel.getSpareOne().equals("2") || tabAiModel.getSpareOne().equals("3")||tabAiModel.getSpareOne().equals("11")) { //v5 v8
                net = Dnn.readNetFromONNX(upLoadPath + File.separator + tabAiModel.getAiWeights());
            }


            net.setPreferableBackend(Dnn.DNN_BACKEND_CUDA);
            net.setPreferableTarget(Dnn.DNN_TARGET_CUDA);  //gpu推理
            log.info("[DNN推理规则：GPU]");
            GLOBAL_NET_CACHE.put(tabAiModel.getId(), net);
        } else {
            log.info("【已经存在net直接返回】");
        }
        return net;
    }

    @Override
    public Result<String> saveMakeNum() {
        List<TabModelTry> tabModelTryList = this.list();
        for (TabModelTry tabmodel : tabModelTryList) {
            List<TabEasyPic> tabEasyPicListALL = tabEasyPicMapper.selectList(new LambdaQueryWrapper<TabEasyPic>().eq(TabEasyPic::getModelId, tabmodel.getId()));
            List<TabEasyPic> tabEasyPicListY = tabEasyPicMapper.selectList(new LambdaQueryWrapper<TabEasyPic>().eq(TabEasyPic::getModelId, tabmodel.getId()).eq(TabEasyPic::getMarkType, "Y"));
            tabmodel.setPicNumber(tabEasyPicListALL.size() + "");
            tabmodel.setMakeNumber(tabEasyPicListY.size() + "");
            this.updateById(tabmodel);
        }
        return Result.ok("更新图片标记数成功");
    }

    @Override
    public Result<String> getBatchPic(List<TabModelTry> tabModelTry) {
        if(tabModelTry.size()<=0){
            return Result.error("未找到当前模型");
        }
        for (TabModelTry tabmodel:tabModelTry) {

            QueryWrapper<TabEasyPic> queryWrapper=new QueryWrapper<>();
            queryWrapper.eq("model_id",tabmodel.getId());
            List<TabEasyPic> tabEasyPic=tabEasyPicMapper.selectList(queryWrapper);
            Set<String> dbFileNames = tabEasyPic.stream()
                    .map(TabEasyPic::getPicName) // 提取字段 picName
                    .collect(Collectors.toSet());

            String path=upLoadPath + File.separator+tabmodel.getPicName()+File.separator;

            List<String>  fileNames=listFileNames(path);

            String pathxml=upLoadPath + File.separator+tabmodel.getPicName()+File.separator+"xml"+File.separator;
            if(fileNames.size()>0){

                // 3. 找出目录中存在，但数据库中不存在的文件
                List<String> notInDb = fileNames.stream()
                        .filter(name -> !dbFileNames.contains(name))
                        .collect(Collectors.toList());
                for (String noName:notInDb) {

                    String picid = UUID.randomUUID().toString().replace("-", "");
                    TabModelTryOrg modelTryOrg = new TabModelTryOrg();
                    modelTryOrg.setModelId(tabmodel.getId());
                    modelTryOrg.setPicId(picid);
                    TabEasyPic pic = new TabEasyPic();
                    pic.setId(picid);
                    pic.setModelId(tabmodel.getId());
                    pic.setPicUrl(tabmodel.getPicName() + File.separator + noName);
                    pic.setMarkType("N");
                    pic.setPicType("1");
                    pic.setPicName(noName);
                    pic.setRemake(tabmodel.getPicName());
                    String xmlpath=pathxml+noName.replaceAll("\\.(png|jpg)$", ".xml");
                    File file=new File(xmlpath);
                    if(file.exists()){
                        pic.setMarkType("Y");
                        pic.setMarkXml(tabmodel.getPicName() + File.separator +"xml"+ File.separator+noName.replaceAll("\\.(png|jpg)$", ".xml"));
                        String result = readObjectNames(xmlpath);
                        if(StringUtils.isNotEmpty(result)){
                            pic.setMarkTitle(result);
                            pic.setMarkFeature("标注图");
                            try {
                                pic.setMarkJson(processXmlFile(xmlpath, tabmodel.getId(), picid));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }else{
                            pic.setMarkFeature("背景图");
                        }

                    }else{
                        log.info("xml文件不存在");

                    }
                    tabEasyPicMapper.insert(pic);

                }

            }
        }


        return Result.ok("恢复成功");
    }
    /**
     * 将XML文档转换为JSON字符串
     */
    private static String convertToJson(Document document, String modelId, String picId) throws Exception {
        JSONArray jsonArray =new JSONArray();

        // 获取图像尺寸
        Element sizeElement = (Element) document.getElementsByTagName("size").item(0);
        double imageWidth = Double.parseDouble(sizeElement.getElementsByTagName("width").item(0).getTextContent());
        double imageHeight = Double.parseDouble(sizeElement.getElementsByTagName("height").item(0).getTextContent());

        // 获取所有对象
        NodeList objects = document.getElementsByTagName("object");

        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);
            JSONObject jsonObject = createJsonObject(object, imageWidth, imageHeight,modelId,picId);
            jsonArray.add(jsonObject);
        }

        return jsonArray.toJSONString();
    }

    /**
     * 处理XML文件并保存到数据库
     */
    public static String processXmlFile(String xmlFilePath, String modelId, String picId) throws Exception {
        // 读取XML文件
        Document document = parseXmlFile(xmlFilePath);

        // 转换为JSON
        String jsonString = convertToJson(document,modelId, picId);

        return  jsonString;

    }
    /**
     * 解析XML文件
     */
    private static Document parseXmlFile(String filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new File(filePath));
    }

    /**
     * 创建单个对象的JSON节点
     */
    private static JSONObject createJsonObject(Element objectElement, double imageWidth, double imageHeight, String modelId, String picId) {
        JSONObject jsonObject = new JSONObject();

        // 获取对象名称
        String name = objectElement.getElementsByTagName("name").item(0).getTextContent();

        // 获取边界框坐标
        Element bndbox = (Element) objectElement.getElementsByTagName("bndbox").item(0);
        double xmin = Double.parseDouble(bndbox.getElementsByTagName("xmin").item(0).getTextContent());
        double ymin = Double.parseDouble(bndbox.getElementsByTagName("ymin").item(0).getTextContent());
        double xmax = Double.parseDouble(bndbox.getElementsByTagName("xmax").item(0).getTextContent());
        double ymax = Double.parseDouble(bndbox.getElementsByTagName("ymax").item(0).getTextContent());

        // 计算宽度和高度
        double canvasWidth = xmax - xmin;
        double canvasHeight = ymax - ymin;

        // 构建JSON对象
        jsonObject.put("canvasheight", canvasHeight);
        jsonObject.put("canvaswidth", canvasWidth);
        jsonObject.put("modelId", modelId);
        jsonObject.put("name", name);
        jsonObject.put("picId", picId);
        jsonObject.put("xmax", String.valueOf(xmax));
        jsonObject.put("xmin", String.valueOf(xmin));
        jsonObject.put("yheight", imageHeight);
        jsonObject.put("ymax", String.valueOf(ymax));
        jsonObject.put("ymin", String.valueOf(ymin));
        jsonObject.put("ywidth", imageWidth);

        return jsonObject;
    }

    public static List<String> listFileNames(String path) {
        List<String> list=new ArrayList<>();
        log.info("获取的目录：{}",path);
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
          log.info("目录不存在: " + path);
          return list;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                // 只取文件名，不包含路径

                if (file.isFile()) { // 只取文件，不取子目录
                    log.info("获取到的文件名称：{}",file.getName());
                    list.add(file.getName());
                }
            }
        }
        return list;
    }



    /**
     * 读取XML注解文件，提取有效的object名称
     * @param filePath XML文件路径
     * @return 用逗号分隔的名称字符串，如果没有有效名称返回空字符串
     */
    public static String readObjectNames(String filePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(filePath));

            // 获取所有object节点
            NodeList objectList = document.getElementsByTagName("object");
            List<String> validNames = new ArrayList<>();

            for (int i = 0; i < objectList.getLength(); i++) {
                Element objectElement = (Element) objectList.item(i);

                // 获取name节点的文本内容
                NodeList nameNodes = objectElement.getElementsByTagName("name");
                if (nameNodes.getLength() > 0) {
                    String name = nameNodes.item(0).getTextContent();

                    // 检查name是否有效（不为null、不为空字符串、不为空白）
                    if (name != null && !name.trim().isEmpty()) {
                        validNames.add(name.trim());
                    }
                }
            }

            // 如果没有有效名称，返回空字符串
            if (validNames.isEmpty()) {
                return "";
            }

            // 用逗号连接所有有效名称
            return String.join(",", validNames);

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 重载方法：直接传入XML字符串内容
     * @param xmlContent XML内容字符串
     * @return 用逗号分隔的名称字符串，如果没有有效名称返回空字符串
     */
    public static String readObjectNamesFromString(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            // 将字符串转换为输入流
            java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(
                    xmlContent.getBytes("UTF-8"));
            Document document = builder.parse(input);

            NodeList objectList = document.getElementsByTagName("object");
            List<String> validNames = new ArrayList<>();

            for (int i = 0; i < objectList.getLength(); i++) {
                Element objectElement = (Element) objectList.item(i);

                NodeList nameNodes = objectElement.getElementsByTagName("name");
                if (nameNodes.getLength() > 0) {
                    String name = nameNodes.item(0).getTextContent();

                    if (name != null && !name.trim().isEmpty()) {
                        validNames.add(name.trim());
                    }
                }
            }

            if (validNames.isEmpty()) {
                return "";
            }

            return String.join(",", validNames);

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

}
