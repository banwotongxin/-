package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按日趋势：与概览共用同一时间范围、社团范围与口径版本
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsTrendVO {

    private String ruleVersion;
    private String scopeType;
    private List<Long> clubIds;
    private List<String> clubNames;
    private List<TrendPointVO> points;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime from;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime to;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedAt;
}
