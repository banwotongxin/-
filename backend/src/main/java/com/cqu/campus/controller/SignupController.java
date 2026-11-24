package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.dto.SignupCreateRequest;
import com.cqu.campus.domain.dto.SignupReviewRequest;
import com.cqu.campus.service.SignupService;
import com.cqu.campus.domain.vo.SignupSummaryVO;
import com.cqu.campus.domain.vo.SignupVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 报名接口
 */
@Validated
@RestController
public class SignupController {

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @GetMapping("/api/signups/my")
    public ApiResponse<PageResult<SignupVO>> mySignups(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(signupService.mySignups(status, pageNum, pageSize));
    }

    @PostMapping("/api/activities/{id}/signups")
    public ApiResponse<SignupVO> signup(@PathVariable("id") Long id,
                                       @Valid @RequestBody(required = false) SignupCreateRequest request) {
        return ApiResponse.success(signupService.signup(id, request == null ? new SignupCreateRequest() : request));
    }

    @GetMapping("/api/activities/{id}/signups")
    public ApiResponse<PageResult<SignupVO>> list(
            @PathVariable("id") Long id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(signupService.list(id, status, keyword, pageNum, pageSize));
    }

    @GetMapping("/api/activities/{id}/signups/my")
    public ApiResponse<SignupVO> mySignup(@PathVariable("id") Long id) {
        return ApiResponse.success(signupService.mySignupForActivity(id));
    }

    @GetMapping("/api/activities/{id}/signups/summary")
    public ApiResponse<SignupSummaryVO> summary(@PathVariable("id") Long id) {
        return ApiResponse.success(signupService.summary(id));
    }

    @PutMapping("/api/signups/{id}/review")
    public ApiResponse<SignupVO> review(@PathVariable("id") Long id,
                                        @Valid @RequestBody SignupReviewRequest request) {
        return ApiResponse.success(signupService.review(id, request));
    }

    @PostMapping("/api/signups/{id}/cancel")
    public ApiResponse<SignupVO> cancel(@PathVariable("id") Long id) {
        return ApiResponse.success(signupService.cancel(id));
    }
}
