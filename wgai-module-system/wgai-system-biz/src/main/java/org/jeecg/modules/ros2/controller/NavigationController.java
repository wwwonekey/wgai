package org.jeecg.modules.ros2.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ros2.model.NavigationGoalDTO;
import org.jeecg.modules.ros2.service.NavigationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 导航控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/navigation")
@Api(tags = "导航控制")
public class NavigationController {

    @Autowired
    private NavigationService navigationService;

    /**
     * 发送导航目标
     */
    @PostMapping("/goal")
    @ApiOperation("设置导航目标")
    public Result<Void> setGoal(@Validated @RequestBody NavigationGoalDTO goal) {
        navigationService.sendNavigationGoal(goal.getX(), goal.getY(), goal.getTheta());
        return Result.OK("导航目标已设置");
    }

    /**
     * 取消导航
     */
    @PostMapping("/cancel")
    @ApiOperation("取消导航")
    public Result<Void> cancel() {
        navigationService.cancelNavigation();
        return Result.OK("导航已取消");
    }
}
