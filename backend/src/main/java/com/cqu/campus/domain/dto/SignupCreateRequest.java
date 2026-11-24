package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 提交报名
 */
@Data
public class SignupCreateRequest {

    @Size(max = 500, message = "报名备注长度不能超过 500")
    private String remark;
}
