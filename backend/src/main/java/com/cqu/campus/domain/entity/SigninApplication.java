package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 补签申请：学生发起，活动所属社团负责人审核
 */
@Data
@TableName("signin_applications")
public class SigninApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long signupId;
    private Long studentId;
    /** 申请的补签时间：晚于活动开始时间且早于当前时间 */
    private LocalDateTime signedAt;
    private String reason;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private Long reviewerId;
    private String reviewReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
