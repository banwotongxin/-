package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报名信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupVO {

    private Long id;
    private Long activityId;
    private String activityTitle;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String remark;
    private String status;
    private String reviewReason;
    private String reviewerName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime reviewedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelledAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime activityStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime activityEndTime;

    private String activityStatus;
    private Boolean signed;
    private String supplementStatus;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime signedAt;

    private String signinSource;
}
