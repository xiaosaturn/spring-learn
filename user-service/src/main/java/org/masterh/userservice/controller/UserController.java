package org.masterh.userservice.controller;

import org.masterh.userservice.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ClassName UserController
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/20 9:52
 * @Version 1.0
 **/
@RestController
@RequestMapping("/users")
public class UserController {
    private final String userName;

    public UserController(@Value("${lesson.user-name}") String userName) {
        this.userName = userName;
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Integer id) {
        return new UserResponse(
                id,
                userName,
                20
        );
    }
}
