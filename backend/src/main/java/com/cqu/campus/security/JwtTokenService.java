package com.cqu.campus.security;

import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * JWT 令牌签发与解析（含 userId、username、roles）
 */
@Service
public class JwtTokenService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ROLES = "roles";

    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + properties.getExpireSeconds() * 1000L);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, primaryRole(roles))
                .claim(CLAIM_ROLES, roles == null ? new ArrayList<String>() : roles)
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(SignatureAlgorithm.HS256, secretBytes())
                .compact();
    }

    /**
     * 主角色优先级：平台管理员 > 社团管理员 > 学生
     */
    private String primaryRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        if (roles.contains("PLATFORM_ADMIN")) {
            return "PLATFORM_ADMIN";
        }
        if (roles.contains("CLUB_ADMIN")) {
            return "CLUB_ADMIN";
        }
        return roles.get(0);
    }

    @SuppressWarnings("unchecked")
    public CurrentUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secretBytes())
                    .parseClaimsJws(token)
                    .getBody();
            Object userIdValue = claims.get(CLAIM_USER_ID);
            Long userId = userIdValue == null ? Long.valueOf(claims.getSubject()) : Long.valueOf(userIdValue.toString());
            String username = claims.get(CLAIM_USERNAME, String.class);
            List<String> roles = new ArrayList<String>();
            Object rolesValue = claims.get(CLAIM_ROLES);
            if (rolesValue instanceof List) {
                for (Object item : (List<Object>) rolesValue) {
                    roles.add(String.valueOf(item));
                }
            }
            return new CurrentUser(userId, username, roles);
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.AUTH_REQUIRED, "令牌已过期，请重新登录");
        } catch (JwtException e) {
            throw new BizException(ErrorCode.AUTH_REQUIRED, "令牌无效");
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.AUTH_REQUIRED, "令牌无效");
        }
    }

    public long getExpireSeconds() {
        return properties.getExpireSeconds();
    }

    private byte[] secretBytes() {
        return properties.getSecret().getBytes(StandardCharsets.UTF_8);
    }
}
