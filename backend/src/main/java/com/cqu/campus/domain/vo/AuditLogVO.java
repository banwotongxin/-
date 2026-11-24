package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogVO {

    private Long id;
    private String traceId;
    private Long operatorId;
    private String operatorName;
    private String resourceType;
    private Long resourceId;
    private String action;
    private String beforeJson;
    private String afterJson;
    private String result;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
