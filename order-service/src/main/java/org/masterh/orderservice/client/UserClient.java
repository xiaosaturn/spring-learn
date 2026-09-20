package org.masterh.orderservice.client;

import org.masterh.orderservice.dto.UserResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * @ClassName UserClient
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/20 9:56
 * @Version 1.0
 **/

@Component
public class UserClient {
    private final RestClient restClient;

    public UserClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://user-service")
                .build();
    }

    public UserResponse findById(Integer id) {
        return restClient.get()
                .uri("/users/{id}", id)
                .retrieve()
                .body(UserResponse.class);
    }
}
