package org.masterh.orderservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * @ClassName RestClientConfig
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/20 9:54
 * @Version 1.0
 **/
@Configuration
public class RestClientConfig {

    // 普通 HTTP 客户端，供 Eureka 使用
    @Bean
    @Primary
    public RestClient.Builder plainRestClientBuilder() {
        return RestClient.builder();
    }

    // 带服务发现和负载均衡的客户端，供 UserClient 使用
    @Bean("loadBalancedRestClientBuilder")
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
