package org.jeecg.modules.demo.test.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author wggg
 * @date 2025/12/17 15:44
 */

@Slf4j
@Controller
public class IndexController {

    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }
}
