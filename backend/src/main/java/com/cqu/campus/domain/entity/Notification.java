package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息投递幂等记录
 */
@Data
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventKey;
    private Long recipientId;
    private String channel;
    /** PENDING / SENT / FAILED */
    private String status;
    private LocalDateTime createdAt;
}
