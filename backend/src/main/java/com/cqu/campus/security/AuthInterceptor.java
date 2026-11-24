package com.cqu.campus.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.domain.entity.Role;
import com.cqu.campus.domain.entity.UserRole;
import com.cqu.campus.mapper.RoleMapper;
import com.cqu.campus.mapper.UserRoleMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证拦截器：解析 Bearer 令牌写入上下文，注册/登录与活动列表允许匿名
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public AuthInterceptor(JwtTokenService jwtTokenService, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.jwtTokenService = jwtTokenService;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        boolean anonymous = isAnonymousAllowed(request.getMethod(), request.getRequestURI());
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                try {
                    CurrentUser currentUser = jwtTokenService.parseToken(token);
                    // 角色以数据库为准，审核授权后无需重新登录即可生效
                    List<String> roles = loadRoles(currentUser.getUserId());
                    if (!roles.isEmpty()) {
                        currentUser.setRoles(roles);
                    }
                    UserContext.set(currentUser);
                } catch (BizException e) {
                    // 匿名接口允许携带失效令牌，受保护接口直接返回 401
                    if (!anonymous) {
                        throw e;
                    }
                }
            }
        }
        if (anonymous) {
            return true;
        }
        if (UserContext.get() == null) {
            throw new BizException(ErrorCode.AUTH_REQUIRED, "未登录或令牌缺失");
        }
        return true;
    }

    private boolean isAnonymousAllowed(String method, String path) {
        if ("POST".equalsIgnoreCase(method)) {
            return "/api/auth/register".equals(path) || "/api/auth/login".equals(path);
        }
        if ("GET".equalsIgnoreCase(method)) {
            return "/api/activities".equals(path);
        }
        return false;
    }

    private List<String> loadRoles(Long userId) {
        List<String> roles = new ArrayList<String>();
        List<UserRole> userRoles = userRoleMapper.selectList(new QueryWrapper<UserRole>().eq("user_id", userId));
        if (userRoles.isEmpty()) {
            return roles;
        }
        List<Long> roleIds = new ArrayList<Long>();
        for (UserRole userRole : userRoles) {
            roleIds.add(userRole.getRoleId());
        }
        for (Role role : roleMapper.selectBatchIds(roleIds)) {
            roles.add(role.getCode());
        }
        return roles;
    }
}
