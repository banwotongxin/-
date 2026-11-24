package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 学生提交补签申请
 */
@Data
public class SigninApplyRequest {

    @NotNull(message = "补签时间不能为空")
    private LocalDateTime signedAt;

    @NotBlank(message = "补签原因不能为空")
    @Size(max = 500, message = "补签原因长度不能超过 500")
    private String reason;
}
