package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 关闭活动
 */
@Data
public class ActivityCloseRequest {

    @Size(max = 255, message = "关闭原因长度不能超过 255")
    private String reason;
}
