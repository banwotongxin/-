package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团
 */
@Data
@TableName("clubs")
public class Club {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String intro;
    private Long ownerUserId;
    private String contact;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
