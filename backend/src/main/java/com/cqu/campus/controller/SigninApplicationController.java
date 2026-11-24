package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.dto.SigninApplicationReviewRequest;
import com.cqu.campus.domain.dto.SigninApplyRequest;
import com.cqu.campus.domain.vo.SigninApplicationVO;
import com.cqu.campus.service.SigninApplicationService;
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
 * 补签申请接口
 */
@Validated
@RestController
public class SigninApplicationController {

    private final SigninApplicationService signinApplicationService;

    public SigninApplicationController(SigninApplicationService signinApplicationService) {
        this.signinApplicationService = signinApplicationService;
    }

    @PostMapping("/api/activities/{id}/signin-applications")
    public ApiResponse<SigninApplicationVO> apply(@PathVariable("id") Long id,
                                                  @Valid @RequestBody SigninApplyRequest request) {
        return ApiResponse.success(signinApplicationService.apply(id, request));
    }

    @GetMapping("/api/signin-applications/my")
    public ApiResponse<PageResult<SigninApplicationVO>> my(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(signinApplicationService.my(pageNum, pageSize));
    }

    @GetMapping("/api/activities/{id}/signin-applications")
    public ApiResponse<PageResult<SigninApplicationVO>> list(
            @PathVariable("id") Long id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(signinApplicationService.list(id, status, pageNum, pageSize));
    }

    @PutMapping("/api/signin-applications/{id}/review")
    public ApiResponse<SigninApplicationVO> review(@PathVariable("id") Long id,
                                                   @Valid @RequestBody SigninApplicationReviewRequest request) {
        return ApiResponse.success(signinApplicationService.review(id, request));
    }
}
