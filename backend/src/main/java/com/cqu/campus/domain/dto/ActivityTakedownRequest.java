package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 活动下架请求
 */
@Data
public class ActivityTakedownRequest {

    @Size(max = 255, message = "下架原因长度不能超过 255")
    private String reason;
}
