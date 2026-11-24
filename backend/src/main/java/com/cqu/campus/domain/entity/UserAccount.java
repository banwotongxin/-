package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号
 */
@Data
@TableName("user_account")
public class UserAccount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String studentNo;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
