package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.vo.AuditLogVO;
import com.cqu.campus.service.AuditService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口（平台管理员）
 */
@Validated
@RestController
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/audit-logs")
    public ApiResponse<PageResult<AuditLogVO>> list(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "resourceId", required = false) Long resourceId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(auditService.list(action, resourceType, resourceId, pageNum, pageSize));
    }
}
