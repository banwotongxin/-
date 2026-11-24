package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团成员
 */
@Data
@TableName("club_members")
public class ClubMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clubId;
    private Long userId;
    /** OWNER / MEMBER */
    private String memberRole;
    /** ACTIVE / LEFT */
    private String status;
    private LocalDateTime joinedAt;
}
