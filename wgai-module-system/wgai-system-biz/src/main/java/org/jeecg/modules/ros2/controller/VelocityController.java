package org.jeecg.modules.ros2.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ros2.model.VelocityVO;

import org.jeecg.modules.ros2.service.ROS2BridgeService;
import org.jeecg.modules.ros2.service.VelocityMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 速度监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/velocity")
@Api(tags = "速度监控")
public class VelocityController {

    @Autowired
    private VelocityMonitorService velocityService;

    @Autowired
    private ROS2BridgeService ros2Bridge;

    /**
     * 获取当前速度
     */
    @GetMapping("/current")
    @ApiOperation("获取当前速度")
    public Result<VelocityVO> getCurrentVelocity() {
        VelocityVO vo = VelocityVO.fromEntity(velocityService.getCurrentVelocity());
        return Result.OK(vo);
    }

    /**
     * 获取速度历史
     */
    @GetMapping("/history")
    @ApiOperation("获取速度历史记录")
    public Result<List<VelocityVO>> getHistory() {
        List<VelocityVO> history = velocityService.getVelocityHistory().stream()
                .map(VelocityVO::fromEntity)
                .collect(Collectors.toList());
        return Result.OK(history);
    }

    /**
     * 清空历史记录
     */
    @DeleteMapping("/history")
    @ApiOperation("清空速度历史记录")
    public Result<Void> clearHistory() {
        velocityService.clearHistory();
        return Result.OK();
    }

    /**
     * 获取连接状态
     */
    @GetMapping("/status")
    @ApiOperation("获取ROS2连接状态")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", ros2Bridge.isConnected());
        status.put("currentVelocity", VelocityVO.fromEntity(velocityService.getCurrentVelocity()));
        status.put("historyCount", velocityService.getVelocityHistory().size());
        return Result.OK(status);
    }
}