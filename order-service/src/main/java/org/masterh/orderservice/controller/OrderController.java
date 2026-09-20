package org.masterh.orderservice.controller;


import org.masterh.orderservice.client.UserClient;
import org.masterh.orderservice.dto.OrderResponse;
import org.masterh.orderservice.dto.UserResponse;
import org.springframework.web.bind.annotation.*;

/**
 * @ClassName OrderController
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/20 9:57
 * @Version 1.0
 **/
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final UserClient userClient;

    public OrderController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Integer id) {
        UserResponse user = userClient.findById(1);

        return new OrderResponse(
                id,
                "Spring Cloud 课程",
                199,
                user
        );
    }
}
