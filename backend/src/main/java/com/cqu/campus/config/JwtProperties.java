package com.cqu.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥，长度至少 32 字符 */
    private String secret = "cqu-campus-activity-system-demo-secret-key-2026";

    /** 令牌有效期（秒） */
    private long expireSeconds = 7200;
}
