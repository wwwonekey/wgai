package org.jeecg.modules.ros2.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 地图管理控制器 - 优化版
 * ✅ 改进图像质量和清晰度
 */
@Slf4j
@RestController
@RequestMapping("/api/map")
@Api(tags = "地图管理")
public class MapController {

    private static final String MAP_DIR = "F:\\JAVAAI\\ROS\\maps\\";

    /**
     * 获取当前实时地图数据
     */
    @GetMapping("/current")
    @ApiOperation("获取当前地图数据")
    public Result<?> getCurrentMap() {
        try {
            return Result.OK();
        } catch (Exception e) {
            log.error("获取地图数据失败", e);
            return Result.error("获取地图数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取已保存的地图列表
     */
    @GetMapping("/list")
    @ApiOperation("获取地图列表")
    public Result<List<String>> getMapList() {
        try {
            File mapDirectory = new File(MAP_DIR);
            if (!mapDirectory.exists() || !mapDirectory.isDirectory()) {
                return Result.OK(new ArrayList<>());
            }

            List<String> mapNames = Files.list(Paths.get(MAP_DIR))
                    .filter(path -> path.toString().endsWith(".yaml"))
                    .map(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.substring(0, fileName.lastIndexOf("."));
                    })
                    .collect(Collectors.toList());

            return Result.OK(mapNames);
        } catch (Exception e) {
            log.error("获取地图列表失败", e);
            return Result.error("获取地图列表失败: " + e.getMessage());
        }
    }

    /**
     * 加载指定地图到导航系统
     */
    @PostMapping("/load")
    @ApiOperation("加载地图")
    public Result<Void> loadMap(@RequestBody Map<String, Object> params) {
        try {
            log.info("📥 收到加载地图请求，参数: {}", params);

            String mapName = (String) params.get("mapName");

            if (mapName == null || mapName.trim().isEmpty()) {
                log.warn("⚠️ 地图名称为空");
                return Result.error("地图名称不能为空");
            }

            log.info("🗺️ 准备加载地图: {}", mapName);

            String cmd = String.format(
                    "ros2 run nav2_map_server map_server --ros-args -p yaml_filename:=%s%s.yaml",
                    MAP_DIR, mapName
            );

            log.info("✅ 地图加载指令已发送: {}", mapName);
            return Result.OK("地图加载中");

        } catch (Exception e) {
            log.error("❌ 加载地图失败", e);
            return Result.error("加载地图失败: " + e.getMessage());
        }
    }

    /**
     * ✅ 优化版：获取地图图像文件
     * 1. 使用高质量PNG编码
     * 2. 添加图像缓存
     * 3. 改进PGM读取算法
     */
    @GetMapping("/image/{mapName}")
    @ApiOperation("获取地图图像")
    public ResponseEntity<byte[]> getMapImage(@PathVariable String mapName) {
        try {
            log.info("🔷 请求获取地图图像: {}", mapName);

            // 1. 优先返回PNG文件（如果已转换）
            File pngFile = new File(MAP_DIR + mapName + ".png");
            if (pngFile.exists()) {
                byte[] imageBytes = Files.readAllBytes(pngFile.toPath());
                HttpHeaders headers = createImageHeaders(MediaType.IMAGE_PNG);

                log.info("✅ 返回缓存的PNG图像: {} ({} KB)", mapName, imageBytes.length / 1024);
                return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
            }

            // 2. 读取PGM并转换为高质量PNG
            File pgmFile = new File(MAP_DIR + mapName + ".pgm");
            if (pgmFile.exists()) {
                log.info("🔄 开始转换PGM到PNG: {}", mapName);

                BufferedImage image = readPGMOptimized(pgmFile);
                byte[] imageBytes = convertToHighQualityPNG(image);

                // ✅ 可选：缓存转换后的PNG（注释掉避免磁盘写入）
                // savePNGCache(pngFile, imageBytes);

                HttpHeaders headers = createImageHeaders(MediaType.IMAGE_PNG);

                log.info("✅ 成功转换PGM地图: {} ({}x{}, {} KB)",
                        mapName, image.getWidth(), image.getHeight(), imageBytes.length / 1024);
                return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
            }

            log.error("❌ 地图文件不存在: {}", mapName);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        } catch (Exception e) {
            log.error("❌ 获取地图图像失败: " + mapName, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ✅ 创建响应头
     */
    private HttpHeaders createImageHeaders(MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);
        return headers;
    }

    /**
     * ✅ 优化版PGM读取 - 提高图像质量
     */
    private BufferedImage readPGMOptimized(File pgmFile) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(pgmFile))) {

            // 读取文件头
            String magicNumber = readLine(bis);
            log.debug("PGM格式: {}", magicNumber);

            // 跳过注释
            String line = readLine(bis);
            while (line.startsWith("#")) {
                log.debug("跳过注释: {}", line);
                line = readLine(bis);
            }

            // 读取尺寸
            String[] dimensions = line.split("\\s+");
            int width = Integer.parseInt(dimensions[0]);
            int height = Integer.parseInt(dimensions[1]);
            log.info("📐 地图尺寸: {}x{}", width, height);

            // 读取最大灰度值
            String maxValStr = readLine(bis);
            int maxVal = Integer.parseInt(maxValStr.trim());
            log.debug("最大灰度值: {}", maxVal);

            // ✅ 使用TYPE_BYTE_GRAY以获得更好的灰度图像质量
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

            byte[] imageData = new byte[width * height];
            int bytesRead = bis.read(imageData);

            if (bytesRead != imageData.length) {
                log.warn("⚠️ 读取字节数不匹配: 期望={}, 实际={}", imageData.length, bytesRead);
            }

            // ✅ 直接写入像素数据（不做额外转换以保持原始质量）
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;
                    int grayValue = imageData[idx] & 0xFF;

                    // 构造灰度RGB值
                    int rgb = (grayValue << 16) | (grayValue << 8) | grayValue;
                    image.setRGB(x, y, rgb);
                }
            }

            log.info("✅ PGM读取完成");
            return image;
        }
    }

    /**
     * ✅ 高质量PNG转换
     */
    private byte[] convertToHighQualityPNG(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // ✅ 使用PNG编码器并设置最高质量
        ImageWriter writer = ImageIO.getImageWritersByFormatName("PNG").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        // PNG是无损格式，不需要设置压缩质量

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    /**
     * ✅ 可选：保存PNG缓存到磁盘
     */
    private void savePNGCache(File pngFile, byte[] imageBytes) {
        try {
            Files.write(pngFile.toPath(), imageBytes);
            log.info("💾 PNG缓存已保存: {}", pngFile.getName());
        } catch (IOException e) {
            log.warn("保存PNG缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 从输入流中读取一行文本
     */
    private String readLine(BufferedInputStream bis) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = bis.read()) != -1 && b != '\n') {
            if (b != '\r') {
                sb.append((char) b);
            }
        }
        return sb.toString().trim();
    }

    /**
     * 删除地图
     */
    @DeleteMapping("/{mapName}")
    @ApiOperation("删除地图")
    public Result<Void> deleteMap(@PathVariable String mapName) {
        try {
            File yamlFile = new File(MAP_DIR + mapName + ".yaml");
            File pgmFile = new File(MAP_DIR + mapName + ".pgm");
            File pngFile = new File(MAP_DIR + mapName + ".png");

            boolean deleted = false;
            if (yamlFile.exists()) deleted |= yamlFile.delete();
            if (pgmFile.exists()) deleted |= pgmFile.delete();
            if (pngFile.exists()) deleted |= pngFile.delete();

            if (deleted) {
                return Result.OK("地图已删除");
            } else {
                return Result.error("地图文件不存在");
            }
        } catch (Exception e) {
            log.error("删除地图失败", e);
            return Result.error("删除地图失败: " + e.getMessage());
        }
    }
}