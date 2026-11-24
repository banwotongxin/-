package com.cqu.campus.domain.vo;

import lombok.Data;

/**
 * 统计聚合查询的原始分组行：按状态、社团或日期分组时复用
 */
@Data
public class StatisticsAggregateRow {

    private Long clubId;
    /** yyyy-MM-dd */
    private String statDate;
    private String status;
    private Long activityCount;
    private Long signupCount;
    private Long approvedCount;
    private Long distinctStudentCount;
    private Long checkinCount;
    private Long normalCheckinCount;
    private Long supplementCheckinCount;
}
