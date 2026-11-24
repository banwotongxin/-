package com.cqu.campus.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 社团活跃度排名项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubRankItem {

    private Long clubId;
    private String clubName;
    private long activityCount;
    private long signupCount;
    private long approvedCount;
    private long checkinCount;
    /** 社团签到率，无通过报名时为 0 */
    private double checkinRate;
    /** 活跃度 = 活动数 × 2 + 报名人次 */
    private long activityScore;
}
