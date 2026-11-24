package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 社团信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubVO {

    private Long id;
    private String name;
    private String intro;
    private Long ownerUserId;
    private String ownerName;
    private String contact;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
