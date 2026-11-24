package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.dto.ExportFile;
import com.cqu.campus.domain.dto.SigninCreateRequest;
import com.cqu.campus.service.SigninService;
import com.cqu.campus.domain.vo.SigninRosterVO;
import com.cqu.campus.domain.vo.SigninSummaryVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;

/**
 * 签到接口
 */
@Validated
@RestController
public class SigninController {

    private final SigninService signinService;

    public SigninController(SigninService signinService) {
        this.signinService = signinService;
    }

    @PostMapping("/api/activities/{id}/signins")
    public ApiResponse<SigninRosterVO> signin(@PathVariable("id") Long id,
                                              @Valid @RequestBody SigninCreateRequest request) {
        return ApiResponse.success(signinService.signin(id, request));
    }

    @PostMapping("/api/signups/{id}/signin")
    public ApiResponse<SigninRosterVO> signinSelf(@PathVariable("id") Long id) {
        return ApiResponse.success(signinService.signinSelf(id));
    }

    @GetMapping("/api/activities/{id}/signins/summary")
    public ApiResponse<SigninSummaryVO> summary(@PathVariable("id") Long id) {
        return ApiResponse.success(signinService.summary(id));
    }

    @GetMapping("/api/activities/{id}/signins")
    public ApiResponse<PageResult<SigninRosterVO>> roster(
            @PathVariable("id") Long id,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(signinService.roster(id, pageNum, pageSize));
    }

    @GetMapping(value = "/api/activities/{id}/signins/export")
    public ResponseEntity<byte[]> export(@PathVariable("id") Long id) {
        ExportFile file = signinService.export(id);
        byte[] content = file.getContent().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"");
        return new ResponseEntity<byte[]>(content, headers, HttpStatus.OK);
    }
}
