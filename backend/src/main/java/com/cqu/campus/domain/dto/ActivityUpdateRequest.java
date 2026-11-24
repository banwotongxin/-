package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 编辑活动，字段为空表示保持原值
 */
@Data
public class ActivityUpdateRequest {

    @Size(max = 150, message = "活动标题长度不能超过 150")
    private String title;

    @Size(max = 4000, message = "活动简介长度不能超过 4000")
    private String summary;

    @Size(max = 200, message = "活动地点长度不能超过 200")
    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime signupStart;

    private LocalDateTime signupEnd;

    @Min(value = 1, message = "人数上限必须为正整数")
    private Integer capacity;

    @Size(max = 100, message = "联系方式长度不能超过 100")
    private String contact;

    @Size(max = 10, message = "标签数量不能超过 10")
    private List<@Size(max = 50, message = "单个标签长度不能超过 50") String> tags;
}
