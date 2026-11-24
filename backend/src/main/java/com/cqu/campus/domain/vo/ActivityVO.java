package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityVO {

    private Long id;
    private Long clubId;
    private String clubName;
    private String title;
    private String summary;
    private String location;
    private String contact;
    private List<String> tags;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime signupStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime signupEnd;

    private Integer capacity;
    private Integer signedCount;
    private String status;
    /** 平台下架原因，非空表示活动处于下架待恢复状态 */
    private String takedownReason;
    /** 当前是否处于可报名窗口 */
    private boolean signupOpen;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
