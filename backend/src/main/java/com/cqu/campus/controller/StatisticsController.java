package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.DateTimeUtils;
import com.cqu.campus.domain.dto.ExportFile;
import com.cqu.campus.domain.vo.StatisticsOverviewVO;
import com.cqu.campus.domain.vo.StatisticsTrendVO;
import com.cqu.campus.service.StatisticsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 统计接口
 */
@Validated
@RestController
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/api/statistics/overview")
    public ApiResponse<StatisticsOverviewVO> overview(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "clubId", required = false) Long clubId) {
        return ApiResponse.success(statisticsService.overview(
                DateTimeUtils.parse(from), DateTimeUtils.parse(to), clubId));
    }

    @GetMapping("/api/statistics/trend")
    public ApiResponse<StatisticsTrendVO> trend(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "clubId", required = false) Long clubId) {
        return ApiResponse.success(statisticsService.trend(
                DateTimeUtils.parse(from), DateTimeUtils.parse(to), clubId));
    }

    @GetMapping(value = "/api/statistics/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "clubId", required = false) Long clubId) {
        ExportFile file = statisticsService.export(
                DateTimeUtils.parse(from), DateTimeUtils.parse(to), clubId);
        byte[] content = file.getContent().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"");
        return new ResponseEntity<byte[]>(content, headers, HttpStatus.OK);
    }
}
