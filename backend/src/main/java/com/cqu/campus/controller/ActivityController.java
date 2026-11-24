package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.DateTimeUtils;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.dto.ActivityCloseRequest;
import com.cqu.campus.domain.dto.ActivityCreateRequest;
import com.cqu.campus.domain.dto.ActivityTakedownRequest;
import com.cqu.campus.domain.dto.ActivityUpdateRequest;
import com.cqu.campus.service.ActivityService;
import com.cqu.campus.domain.vo.ActivityVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 活动接口
 */
@Validated
@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/api/activities")
    public ApiResponse<PageResult<ActivityVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "clubId", required = false) Long clubId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(activityService.list(keyword, clubId, status, tag,
                DateTimeUtils.parse(from), DateTimeUtils.parse(to), pageNum, pageSize));
    }

    @PostMapping("/api/activities")
    public ApiResponse<ActivityVO> create(@Valid @RequestBody ActivityCreateRequest request) {
        return ApiResponse.success(activityService.create(request));
    }

    @GetMapping("/api/activities/{id}")
    public ApiResponse<ActivityVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(activityService.detail(id));
    }

    @PutMapping("/api/activities/{id}")
    public ApiResponse<ActivityVO> update(@PathVariable("id") Long id,
                                         @Valid @RequestBody ActivityUpdateRequest request) {
        return ApiResponse.success(activityService.update(id, request));
    }

    @PostMapping("/api/activities/{id}/publish")
    public ApiResponse<ActivityVO> publish(@PathVariable("id") Long id) {
        return ApiResponse.success(activityService.publish(id));
    }

    @PostMapping("/api/activities/{id}/close")
    public ApiResponse<ActivityVO> close(@PathVariable("id") Long id,
                                         @RequestBody(required = false) ActivityCloseRequest request) {
        return ApiResponse.success(activityService.close(id, request == null ? null : request.getReason()));
    }

    @PostMapping("/api/activities/{id}/takedown")
    public ApiResponse<ActivityVO> takedown(@PathVariable("id") Long id,
                                            @RequestBody(required = false) ActivityTakedownRequest request) {
        return ApiResponse.success(activityService.takedown(id, request == null ? null : request.getReason()));
    }

    @PostMapping("/api/activities/{id}/restore")
    public ApiResponse<ActivityVO> restore(@PathVariable("id") Long id) {
        return ApiResponse.success(activityService.restore(id));
    }

    @DeleteMapping("/api/activities/{id}")
    public ApiResponse<Void> deleteDraft(@PathVariable("id") Long id) {
        activityService.deleteDraft(id);
        return ApiResponse.success(null);
    }
}
