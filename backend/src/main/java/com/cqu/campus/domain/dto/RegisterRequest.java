package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 注册请求
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 50, message = "账号长度需在 3-50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @NotBlank(message = "学号不能为空")
    @Size(max = 32, message = "学号长度不能超过 32")
    private String studentNo;
}
