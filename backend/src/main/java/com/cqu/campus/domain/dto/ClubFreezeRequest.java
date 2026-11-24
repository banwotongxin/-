package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 社团冻结请求
 */
@Data
public class ClubFreezeRequest {

    @Size(max = 255, message = "冻结原因长度不能超过 255")
    private String reason;
}
