package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 个人资料修改
 */
@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @NotBlank(message = "学号不能为空")
    @Size(max = 32, message = "学号长度不能超过 32")
    private String studentNo;
}
