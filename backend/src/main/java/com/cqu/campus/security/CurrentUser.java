package com.cqu.campus.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户上下文对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    private Long userId;
    private String username;
    private List<String> roles;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public List<String> safeRoles() {
        return roles == null ? new ArrayList<String>() : roles;
    }
}
