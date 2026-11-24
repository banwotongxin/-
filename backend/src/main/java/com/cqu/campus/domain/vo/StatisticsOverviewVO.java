package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计概览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsOverviewVO {

    private long activityCount;
    /** 状态非 DRAFT 的活动数（发布口径） */
    private long publishedActivityCount;
    /** 活动状态分布：DRAFT/PUBLISHED/FULL/CLOSED/ARCHIVED */
    private Map<String, Long> activityStatusCounts;
    /** 报名人次，不含已撤销 */
    private long signupCount;
    /** 报名人数，按学生去重 */
    private long distinctStudentCount;
    private long approvedCount;
    private long checkinCount;
    /** 正常签到人数 */
    private long normalCheckinCount;
    /** 补签人数 */
    private long supplementCheckinCount;
    /** 签到率，已通过人数为 0 时返回 0 */
    private double checkinRate;
    /** 是否无基数（已通过人数为 0） */
    private boolean noBaseline;
    /** 口径说明，无基数时提示签到率按 0 返回 */
    private String note;
    /** 统计口径版本 */
    private String ruleVersion;
    /** ALL=全平台，SELECTED=指定社团，MANAGED=本人管理社团 */
    private String scopeType;
    private List<Long> clubIds;
    private List<String> clubNames;
    private List<ClubRankItem> clubRanking;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime from;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime to;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedAt;
}
