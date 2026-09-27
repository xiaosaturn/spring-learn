package org.masterh.authservice.controller;

import jakarta.validation.Valid;
import org.masterh.authservice.common.ApiResponse;
import org.masterh.authservice.dto.LoginRequest;
import org.masterh.authservice.dto.RegisterRequest;
import org.masterh.authservice.service.AccountService;
import org.masterh.authservice.service.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        String token = jwtService.generateToken(authentication.getName());
        return ApiResponse.success(Map.of("token", token));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        accountService.register(request);
        return ApiResponse.success("注册成功", null);
    }
}
