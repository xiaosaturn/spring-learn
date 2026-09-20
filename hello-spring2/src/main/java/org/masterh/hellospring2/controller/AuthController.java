package org.masterh.hellospring2.controller;

import jakarta.validation.Valid;
import org.masterh.hellospring2.common.ApiResponse;
import org.masterh.hellospring2.dto.LoginRequest;
import org.masterh.hellospring2.dto.RegisterRequest;
import org.masterh.hellospring2.service.AccountService;
import org.masterh.hellospring2.service.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @ClassName AuthController
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 17:34
 * @Version 1.0
 **/
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    private final AccountService accountService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AccountService accountService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(
            @RequestBody LoginRequest request
    ) {
        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(),
                                request.getPassword()
                        )
                );

        String token = jwtService.generateToken(
                authentication.getName()
        );

        return ApiResponse.success(
                Map.of("token", token)
        );
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        accountService.register(request);

        return ApiResponse.success("注册成功", null);
    }
}
