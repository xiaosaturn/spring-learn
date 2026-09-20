package org.masterh.hellospring2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @ClassName AppProperties
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 16:08
 * @Version 1.0
 **/
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String name;
    private String welcomeMessage;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }
}
