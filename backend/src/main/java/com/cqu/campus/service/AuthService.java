package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.domain.entity.Role;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.entity.UserRole;
import com.cqu.campus.domain.dto.LoginRequest;
import com.cqu.campus.domain.dto.ProfileUpdateRequest;
import com.cqu.campus.domain.dto.RegisterRequest;
import com.cqu.campus.mapper.RoleMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.mapper.UserRoleMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.JwtTokenService;
import com.cqu.campus.security.UserContext;
import com.cqu.campus.domain.vo.LoginVO;
import com.cqu.campus.domain.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证服务：注册、登录、当前用户
 */
@Service
public class AuthService {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserAccountMapper userAccountMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                       PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public UserVO register(RegisterRequest request) {
        if (userAccountMapper.selectCount(new QueryWrapper<UserAccount>().eq("username", request.getUsername())) > 0) {
            throw new BizException(ErrorCode.USER_EXISTS, "账号已存在");
        }
        if (userAccountMapper.selectCount(new QueryWrapper<UserAccount>().eq("student_no", request.getStudentNo())) > 0) {
            throw new BizException(ErrorCode.USER_EXISTS, "学号已存在");
        }
        UserAccount account = new UserAccount();
        account.setUsername(request.getUsername().trim());
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setRealName(request.getRealName().trim());
        account.setStudentNo(request.getStudentNo().trim());
        account.setStatus(STATUS_ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.insert(account);

        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("code", ROLE_STUDENT));
        if (role == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "学生角色未初始化");
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(account.getId());
        userRole.setRoleId(role.getId());
        userRole.setCreatedAt(LocalDateTime.now());
        userRoleMapper.insert(userRole);

        List<String> roles = new ArrayList<String>();
        roles.add(ROLE_STUDENT);
        return toVo(account, roles);
    }

    public LoginVO login(LoginRequest request) {
        UserAccount account = userAccountMapper.selectOne(new QueryWrapper<UserAccount>()
                .eq("username", request.getUsername().trim()));
        // 统一失败提示，不暴露账号是否存在
        if (account == null || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new BizException(ErrorCode.AUTH_REQUIRED, "用户名或密码错误");
        }
        if (!STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED, "账号已冻结或停用，请联系管理员");
        }
        UpdateWrapper<UserAccount> wrapper = new UpdateWrapper<UserAccount>();
        wrapper.eq("id", account.getId()).set("last_login_at", LocalDateTime.now());
        userAccountMapper.update(null, wrapper);

        List<String> roles = loadRoles(account.getId());
        String token = jwtTokenService.createToken(account.getId(), account.getUsername(), roles);
        return LoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpireSeconds())
                .user(toVo(account, roles))
                .build();
    }

    public UserVO me() {
        CurrentUser currentUser = UserContext.require();
        UserAccount account = userAccountMapper.selectById(currentUser.getUserId());
        if (account == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return toVo(account, loadRoles(account.getId()));
    }

    /**
     * 修改当前用户个人资料
     */
    @Transactional
    public UserVO updateProfile(ProfileUpdateRequest request) {
        CurrentUser currentUser = UserContext.require();
        UserAccount account = userAccountMapper.selectById(currentUser.getUserId());
        if (account == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        String studentNo = request.getStudentNo().trim();
        Long count = userAccountMapper.selectCount(new QueryWrapper<UserAccount>()
                .eq("student_no", studentNo).ne("id", account.getId()));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.USER_EXISTS, "学号已被其他账号使用");
        }
        UpdateWrapper<UserAccount> wrapper = new UpdateWrapper<UserAccount>();
        wrapper.eq("id", account.getId())
                .set("real_name", request.getRealName().trim())
                .set("student_no", studentNo)
                .set("updated_at", LocalDateTime.now());
        userAccountMapper.update(null, wrapper);
        UserAccount updated = userAccountMapper.selectById(account.getId());
        return toVo(updated, loadRoles(account.getId()));
    }

    /**
     * 查询用户角色编码
     */
    public List<String> loadRoles(Long userId) {
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

    /**
     * 授予角色（已存在则跳过）
     */
    public void grantRole(Long userId, String roleCode) {
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("code", roleCode));
        if (role == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(new QueryWrapper<UserRole>()
                .eq("user_id", userId).eq("role_id", role.getId()));
        if (count != null && count > 0) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setCreatedAt(LocalDateTime.now());
        userRoleMapper.insert(userRole);
    }

    private UserVO toVo(UserAccount account, List<String> roles) {
        return UserVO.builder()
                .id(account.getId())
                .username(account.getUsername())
                .realName(account.getRealName())
                .studentNo(account.getStudentNo())
                .status(account.getStatus())
                .roles(roles)
                .createdAt(account.getCreatedAt())
                .build();
    }
}
