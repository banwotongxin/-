package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 活动签到汇总：已通过人数、已签到人数、签到率与待审补签数量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigninSummaryVO {

    private Long activityId;
    private String activityStatus;
    private long approvedCount;
    private long signedCount;
    private double signinRate;
    private long pendingApplicationCount;
    private boolean windowOpen;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
}
