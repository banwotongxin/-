package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 社团入驻申请
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubApplicationVO {

    private Long id;
    private Long applicantId;
    private String applicantName;
    private String clubName;
    private String content;
    private String contact;
    private String status;
    private String reviewReason;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime reviewedAt;
}
