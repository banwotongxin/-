package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到
 */
@Data
@TableName("signins")
public class Signin {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long signupId;
    private Long signedBy;
    private LocalDateTime signedAt;
    /** NORMAL / MANUAL（MANUAL 表示补签） */
    private String source;
    private String remark;
    private LocalDateTime createdAt;
}
