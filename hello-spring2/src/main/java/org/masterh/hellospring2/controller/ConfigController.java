package org.masterh.hellospring2.controller;

import org.masterh.hellospring2.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ClassName ConfigController
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 16:09
 * @Version 1.0
 **/
@RestController
@RequestMapping("/config")
public class ConfigController {
    private final AppProperties appProperties;

    public ConfigController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping
    public String config() {
        return appProperties.getName()
                + "："
                + appProperties.getWelcomeMessage();
    }
}
