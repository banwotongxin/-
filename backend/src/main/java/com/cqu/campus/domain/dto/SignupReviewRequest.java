package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 报名审核
 */
@Data
public class SignupReviewRequest {

    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "APPROVED|REJECTED", message = "审核结果只能是 APPROVED 或 REJECTED")
    private String decision;

    @Size(max = 500, message = "审核原因长度不能超过 500")
    private String reason;
}
