package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归档资料
 */
@Data
@TableName("archives")
public class Archive {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long uploaderId;
    private Long fileId;
    private String summary;
    /** PUBLIC / CLUB_ONLY / PRIVATE */
    private String visibility;
    private Integer version;
    private LocalDateTime createdAt;
}
