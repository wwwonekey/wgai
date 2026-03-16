package org.jeecg.modules.ros2.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 建图控制器
 */
@Slf4j
@RestController
@RequestMapping("/ros2/mapping")
@Api(tags = "建图控制")
public class MappingController {

    private static final String MAPS_DIR ="F:\\JAVAAI\\ROS\\maps\\" ;//"/home/ros/maps";
    private Process mappingProcess = null;

    /**
     * 启动建图（改进版）
     */
    @PostMapping("/start")
    @ApiOperation("启动建图")
    public Result<Void> startMapping() {
        try {
            // 检查是否已经在运行
            if (mappingProcess != null && mappingProcess.isAlive()) {
                log.warn("建图已在运行中");
                return Result.error("建图已在运行中，请先停止当前建图");
            }

            // 确保地图目录存在
            Files.createDirectories(Paths.get(MAPS_DIR));

            // 使用 ProcessBuilder 启动
            ProcessBuilder pb = new ProcessBuilder(
                    "bash", "-c",
                    "source /home/ros/install/setup.bash && " +
                            "ros2 launch slam_toolbox online_async_launch.py " +
                            "slam_params_file:=/home/ros/slam_no_odom.yaml"
            );

            // 设置工作目录
            pb.directory(new File(System.getProperty("user.home")));

            // 合并错误输出
            pb.redirectErrorStream(true);
//
//            // 启动进程
//            mappingProcess = pb.start();
//
//            // 在后台线程读取输出
//            new Thread(() -> {
//                try (BufferedReader reader = new BufferedReader(
//                        new InputStreamReader(mappingProcess.getInputStream()))) {
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        log.info("SLAM输出: {}", line);
//                    }
//                } catch (IOException e) {
//                    log.error("读取SLAM输出失败", e);
//                }
//            }).start();

            log.info("✅ 建图已启动, PID: {}", mappingProcess);
            return Result.OK("建图已启动，请稍等片刻后查看实时地图");

        } catch (IOException e) {
            log.error("启动建图失败", e);
            return Result.error("启动建图失败: " + e.getMessage());
        }
    }

    /**
     * 停止建图
     */
    @PostMapping("/stop")
    @ApiOperation("停止建图")
    public Result<Void> stopMapping() {
        try {
            if (mappingProcess != null && mappingProcess.isAlive()) {
                log.info("正在停止建图...");

                // 优雅关闭
                mappingProcess.destroy();

                // 等待3秒
                if (!mappingProcess.waitFor(3, TimeUnit.SECONDS)) {
                    log.warn("进程未响应，强制终止");
                    mappingProcess.destroyForcibly();
                }

                mappingProcess = null;
                log.info("✅ 建图已停止");
                return Result.OK("建图已停止");
            } else {
                return Result.error("建图未在运行");
            }
        } catch (Exception e) {
            log.error("停止建图失败", e);
            return Result.error("停止建图失败: " + e.getMessage());
        }
    }

    /**
     * 获取建图状态
     */
    @GetMapping("/status")
    @ApiOperation("获取建图状态")
    public Result<Map<String, Object>> getMappingStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean isRunning = mappingProcess != null && mappingProcess.isAlive();
        status.put("running", isRunning);
        if (isRunning) {
            status.put("pid", mappingProcess);
        }
        return Result.OK(status);
    }

    /**
     * 保存地图
     */
    @PostMapping("/save")
    @ApiOperation("保存地图")
    public Result<Void> saveMap(@RequestParam String filename) {
        try {
            if (filename == null || filename.trim().isEmpty()) {
                return Result.error("地图名称不能为空");
            }

            // 清理文件名
            filename = filename.replaceAll("[^a-zA-Z0-9_-]", "_");

            // 保存地图
            String mapPath = MAPS_DIR + "/" + filename;
            String cmd = String.format(
                    "source /home/ros/install/setup.bash && " +
                            "ros2 run nav2_map_server map_saver_cli -f %s --ros-args -p save_map_timeout:=10000",
                    mapPath
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("保存地图输出: {}", line);
                }
            }

            // 等待完成
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);

            if (finished && process.exitValue() == 0) {
                log.info("✅ 地图保存成功: {}", filename);
                return Result.OK("地图保存成功: " + filename);
            } else {
                log.error("❌ 地图保存失败，输出: {}", output);
                return Result.error("地图保存失败，请检查建图是否正在运行");
            }

        } catch (Exception e) {
            log.error("保存地图失败", e);
            return Result.error("保存地图失败: " + e.getMessage());
        }
    }

    /**
     * 获取地图列表
     */
    @GetMapping("/list")
    @ApiOperation("获取地图列表")
    public Result<List<Map<String, Object>>> getMapList() {
        try {
            File mapsDir = new File(MAPS_DIR);
            if (!mapsDir.exists()) {
                return Result.OK(new ArrayList<>());
            }

            File[] files = mapsDir.listFiles((dir, name) -> name.endsWith(".yaml"));
            if (files == null) {
                return Result.OK(new ArrayList<>());
            }

            List<Map<String, Object>> mapList = new ArrayList<>();
            for (File file : files) {
                String mapName = file.getName().replace(".yaml", "");
                File pgmFile = new File(MAPS_DIR + "/" + mapName + ".pgm");

                Map<String, Object> mapInfo = new HashMap<>();
                mapInfo.put("name", mapName);
                mapInfo.put("createTime", file.lastModified());
                mapInfo.put("hasImage", pgmFile.exists());
                mapList.add(mapInfo);
            }

            return Result.OK(mapList);

        } catch (Exception e) {
            log.error("获取地图列表失败", e);
            return Result.error("获取地图列表失败: " + e.getMessage());
        }
    }
}