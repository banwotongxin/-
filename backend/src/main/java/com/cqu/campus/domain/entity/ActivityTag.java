package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 活动标签
 */
@Data
@TableName("activity_tags")
public class ActivityTag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private String tag;
}
