package com.cqu.campus.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按日趋势点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointVO {

    /** yyyy-MM-dd */
    private String date;
    private long activityCount;
    private long signupCount;
    private long approvedCount;
    private long checkinCount;
}
