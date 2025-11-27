package org.jeecg.modules.demo.szr.service;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.demo.szr.entity.TabSzrPython;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 数字人训练脚本
 * @Author: wggg
 * @Date:   2025-04-17
 * @Version: V1.0
 */
public interface ITabSzrPythonService extends IService<TabSzrPython> {
    public Result<String> startPy(String id, Integer sort);
    public Result<String> testStar(String id, Integer sort);
}
