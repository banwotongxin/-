package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建活动：默认创建草稿，publish=true 时直接发布
 */
@Data
public class ActivityCreateRequest {

    @NotNull(message = "所属社团不能为空")
    private Long clubId;

    @NotBlank(message = "活动标题不能为空")
    @Size(max = 150, message = "活动标题长度不能超过 150")
    private String title;

    @NotBlank(message = "活动简介不能为空")
    @Size(max = 4000, message = "活动简介长度不能超过 4000")
    private String summary;

    @Size(max = 200, message = "活动地点长度不能超过 200")
    private String location;

    @NotNull(message = "活动开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "活动结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "报名开始时间不能为空")
    private LocalDateTime signupStart;

    @NotNull(message = "报名截止时间不能为空")
    private LocalDateTime signupEnd;

    @NotNull(message = "人数上限不能为空")
    @Min(value = 1, message = "人数上限必须为正整数")
    private Integer capacity;

    @Size(max = 100, message = "联系方式长度不能超过 100")
    private String contact;

    @Size(max = 10, message = "标签数量不能超过 10")
    private List<@Size(max = 50, message = "单个标签长度不能超过 50") String> tags;

    /** 是否直接发布，默认保存为草稿 */
    private Boolean publish;
}
