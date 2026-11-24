package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名
 */
@Data
@TableName("signups")
public class Signup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long studentId;
    private String remark;
    /** PENDING / APPROVED / REJECTED / CANCELLED */
    private String status;
    private Long reviewerId;
    private String reviewReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
