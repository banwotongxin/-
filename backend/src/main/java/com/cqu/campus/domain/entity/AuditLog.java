package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志（追加写入）
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long operatorId;
    private String resourceType;
    private Long resourceId;
    private String action;
    private String beforeJson;
    private String afterJson;
    private String resultMark;
    private LocalDateTime createdAt;
}
