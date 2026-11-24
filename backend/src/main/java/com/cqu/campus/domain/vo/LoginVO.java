package com.cqu.campus.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UserVO user;
}
