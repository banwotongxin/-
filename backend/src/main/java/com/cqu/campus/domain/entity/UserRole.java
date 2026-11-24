package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联
 */
@Data
@TableName("user_roles")
public class UserRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
}
