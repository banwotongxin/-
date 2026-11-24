package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.domain.dto.LoginRequest;
import com.cqu.campus.domain.dto.ProfileUpdateRequest;
import com.cqu.campus.domain.dto.RegisterRequest;
import com.cqu.campus.service.AuthService;
import com.cqu.campus.domain.vo.LoginVO;
import com.cqu.campus.domain.vo.UserVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 认证接口
 */
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.success(authService.me());
    }

    @PutMapping("/me")
    public ApiResponse<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }
}
