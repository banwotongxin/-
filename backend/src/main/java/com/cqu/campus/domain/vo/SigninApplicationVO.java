package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 补签申请
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigninApplicationVO {

    private Long id;
    private Long activityId;
    private String activityTitle;
    private Long signupId;
    private Long studentId;
    private String studentNo;
    private String studentName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime signedAt;

    private String reason;
    private String status;
    private String reviewReason;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime reviewedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
