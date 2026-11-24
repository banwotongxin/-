package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.dto.ClubApplicationReviewRequest;
import com.cqu.campus.domain.dto.ClubCreateRequest;
import com.cqu.campus.domain.dto.ClubFreezeRequest;
import com.cqu.campus.domain.dto.ClubUpdateRequest;
import com.cqu.campus.service.ClubService;
import com.cqu.campus.domain.vo.ClubApplicationVO;
import com.cqu.campus.domain.vo.ClubVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 社团接口
 */
@Validated
@RestController
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping("/api/clubs")
    public ApiResponse<PageResult<ClubVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(clubService.list(keyword, status, pageNum, pageSize));
    }

    @GetMapping("/api/clubs/managed")
    public ApiResponse<List<ClubVO>> managed() {
        return ApiResponse.success(clubService.listManaged());
    }

    @GetMapping("/api/clubs/{id}")
    public ApiResponse<ClubVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(clubService.detail(id));
    }

    @PostMapping("/api/clubs")
    public ApiResponse<ClubApplicationVO> apply(@Valid @RequestBody ClubCreateRequest request) {
        return ApiResponse.success(clubService.apply(request));
    }

    @PutMapping("/api/clubs/{id}")
    public ApiResponse<ClubVO> update(@PathVariable("id") Long id,
                                      @Valid @RequestBody ClubUpdateRequest request) {
        return ApiResponse.success(clubService.update(id, request));
    }

    @PostMapping("/api/clubs/{id}/freeze")
    public ApiResponse<ClubVO> freeze(@PathVariable("id") Long id,
                                      @RequestBody(required = false) ClubFreezeRequest request) {
        return ApiResponse.success(clubService.freeze(id, request));
    }

    @PostMapping("/api/clubs/{id}/unfreeze")
    public ApiResponse<ClubVO> unfreeze(@PathVariable("id") Long id) {
        return ApiResponse.success(clubService.unfreeze(id));
    }

    @GetMapping("/api/club-applications/my")
    public ApiResponse<PageResult<ClubApplicationVO>> myApplications(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(clubService.myApplications(status, pageNum, pageSize));
    }

    @GetMapping("/api/club-applications")
    public ApiResponse<PageResult<ClubApplicationVO>> applications(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(clubService.listApplications(status, pageNum, pageSize));
    }

    @PutMapping("/api/club-applications/{id}/review")
    public ApiResponse<ClubApplicationVO> reviewApplication(@PathVariable("id") Long id,
                                                            @Valid @RequestBody ClubApplicationReviewRequest request) {
        return ApiResponse.success(clubService.reviewApplication(id, request));
    }
}
