package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团入驻申请
 */
@Data
@TableName("club_applications")
public class ClubApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicantId;
    private String clubName;
    private String content;
    private String contact;
    private String status;
    private Long reviewerId;
    private String reviewReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
