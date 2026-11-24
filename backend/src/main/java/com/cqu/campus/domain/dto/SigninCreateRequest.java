package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 登记正常签到；补签需走补签申请审核流程
 */
@Data
public class SigninCreateRequest {

    @NotNull(message = "报名 ID 不能为空")
    private Long signupId;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
