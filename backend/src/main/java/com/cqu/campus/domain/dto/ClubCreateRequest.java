package com.cqu.campus.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 社团入驻申请
 */
@Data
public class ClubCreateRequest {

    @NotBlank(message = "社团名称不能为空")
    @Size(max = 100, message = "社团名称长度不能超过 100")
    private String name;

    @NotBlank(message = "社团简介不能为空")
    @Size(max = 4000, message = "社团简介长度不能超过 4000")
    private String intro;

    @Size(max = 100, message = "联系方式长度不能超过 100")
    private String contact;
}
