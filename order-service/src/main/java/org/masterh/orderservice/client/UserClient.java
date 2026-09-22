package org.masterh.orderservice.client;

import org.masterh.orderservice.dto.UserResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClient;

/**
 * @ClassName UserClient
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/20 9:56
 * @Version 1.0
 **/

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/users/{id}")
    UserResponse findById(
            @PathVariable("id") Integer id
    );
}

//@Component
//public class UserClient {
//    private final RestClient restClient;
//
//    public UserClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
//        this.restClient = builder
//                .baseUrl("http://user-service")
//                .build();
//    }
//
//    public UserResponse findById(Integer id) {
//        return restClient.get()
//                .uri("/users/{id}", id)
//                .retrieve()
//                .body(UserResponse.class);
//    }
//}
