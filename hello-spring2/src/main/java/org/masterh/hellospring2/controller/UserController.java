package org.masterh.hellospring2.controller;

import org.masterh.hellospring2.dto.UserCreateRequest;
import org.masterh.hellospring2.dto.UserUpdateRequest;
import org.masterh.hellospring2.dto.UserResponse;
import org.masterh.hellospring2.model.User;
import org.masterh.hellospring2.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.masterh.hellospring2.common.ApiResponse;

/**
 * @ClassName UserController
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 9:22
 * @Version 1.0
 **/
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @Value("${app.name}")
    private String appName;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> findAll() {
        return ApiResponse.success(userService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> findById(@PathVariable int id) {
        return ApiResponse.success(userService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse result = userService.create(request);

        ApiResponse<UserResponse> body =
                new ApiResponse<>(201, "创建成功", result);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(body);
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
            @PathVariable int id,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        UserResponse result = userService.update(id, request);

        return ApiResponse.success("修改成功", result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable int id) {
        userService.deleteById(id);

        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/search")
    public List<User> searchByName(@RequestParam String name) {
        return userService.findByName(name);
    }

    @GetMapping("/by-age")
    public List<User> searchByAge(@RequestParam Integer minAge) {
        return userService.findByMinAge(minAge);
    }

    @GetMapping("/sorted")
    public List<User> findAllSorted(
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return userService.findAllSorted(direction);
    }

    @GetMapping("/page")
    public Page<User> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return userService.findPage(page, size);
    }

    @GetMapping("/app-info")
    public String appInfo() {
        return appName;
    }
}
