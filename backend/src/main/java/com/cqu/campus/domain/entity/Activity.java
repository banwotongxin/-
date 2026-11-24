package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动
 */
@Data
@TableName("activities")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clubId;
    private String title;
    private String summary;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime signupStart;
    private LocalDateTime signupEnd;
    private Integer capacity;
    private Integer signedCount;
    /** DRAFT / PUBLISHED / FULL / CLOSED / ARCHIVED */
    private String status;
    private String contact;
    /** 平台下架原因，恢复上架后清空 */
    private String takedownReason;
    /** 平台下架时间，非空表示尚未恢复上架 */
    private LocalDateTime takedownAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
