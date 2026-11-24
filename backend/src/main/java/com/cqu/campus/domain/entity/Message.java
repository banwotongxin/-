package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息
 */
@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipientId;
    private String msgType;
    private String title;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
