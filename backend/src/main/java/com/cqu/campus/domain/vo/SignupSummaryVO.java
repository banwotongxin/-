package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 活动报名汇总：各状态数量、剩余名额与报名窗口状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupSummaryVO {

    private Long activityId;
    private String activityStatus;
    private int capacity;
    private long totalCount;
    private long pendingCount;
    private long approvedCount;
    private long rejectedCount;
    private long cancelledCount;
    private int remainingCapacity;
    private boolean signupOpen;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime signupStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime signupEnd;
}
