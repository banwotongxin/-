package com.cqu.campus.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cqu.campus.domain.entity.Activity;
import com.cqu.campus.domain.entity.Club;
import com.cqu.campus.domain.entity.ClubApplication;
import com.cqu.campus.domain.entity.ClubMember;
import com.cqu.campus.domain.entity.Role;
import com.cqu.campus.domain.entity.Signin;
import com.cqu.campus.domain.entity.Signup;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.entity.UserRole;
import com.cqu.campus.mapper.ActivityMapper;
import com.cqu.campus.mapper.ClubApplicationMapper;
import com.cqu.campus.mapper.ClubMapper;
import com.cqu.campus.mapper.ClubMemberMapper;
import com.cqu.campus.mapper.RoleMapper;
import com.cqu.campus.mapper.SigninMapper;
import com.cqu.campus.mapper.SignupMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 启动初始化：demo profile 或用户表为空时创建角色、演示账号、示例社团与活动
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_PASSWORD = "123456";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final RoleMapper roleMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final ClubMapper clubMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final ClubApplicationMapper clubApplicationMapper;
    private final ActivityMapper activityMapper;
    private final SignupMapper signupMapper;
    private final SigninMapper signinMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DataInitializer(RoleMapper roleMapper, UserAccountMapper userAccountMapper, UserRoleMapper userRoleMapper,
                           ClubMapper clubMapper, ClubMemberMapper clubMemberMapper,
                           ClubApplicationMapper clubApplicationMapper,
                           ActivityMapper activityMapper, SignupMapper signupMapper, SigninMapper signinMapper,
                           PasswordEncoder passwordEncoder, Environment environment) {
        this.roleMapper = roleMapper;
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
        this.clubMapper = clubMapper;
        this.clubMemberMapper = clubMemberMapper;
        this.clubApplicationMapper = clubApplicationMapper;
        this.activityMapper = activityMapper;
        this.signupMapper = signupMapper;
        this.signinMapper = signinMapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(String... args) {
        boolean demo = isDemoProfile();
        Long existingUsers = userAccountMapper.selectCount(null);
        if (!demo && existingUsers != null && existingUsers > 0) {
            log.info("非 demo 环境且用户表已有数据，跳过演示数据初始化");
            return;
        }
        Long studentRoleId = ensureRole("STUDENT", "学生");
        Long clubAdminRoleId = ensureRole("CLUB_ADMIN", "社团管理员");
        Long platformAdminRoleId = ensureRole("PLATFORM_ADMIN", "平台管理员");

        UserAccount admin = ensureUser("admin", "平台管理员", "ADMIN001", platformAdminRoleId);
        UserAccount clubAdmin = ensureUser("clubadmin", "篮球社负责人", "2023081002", clubAdminRoleId);
        UserAccount student = ensureUser("student", "演示学生一", "2023081003", studentRoleId);
        UserAccount student2 = ensureUser("student2", "演示学生二", "2023081004", studentRoleId);

        Club club = ensureClub("篮球社", "校园篮球训练、比赛与交流社团", clubAdmin.getId(), "clubadmin@cqu.edu.cn");
        ensureClubMember(club.getId(), clubAdmin.getId());

        LocalDateTime now = LocalDateTime.now();
        Activity first = ensureActivity(club.getId(), "校园篮球友谊赛",
                "面向全校的篮球友谊赛，现场分组对抗。", "东区篮球场",
                now.minusDays(1), now.plusDays(3), now.plusDays(5), now.plusDays(5).plusHours(2),
                50, "clubadmin@cqu.edu.cn", "PUBLISHED", now);
        ensureActivity(club.getId(), "篮球基础训练营",
                "篮球基础动作训练与战术讲解。", "东区篮球场",
                now.minusDays(1), now.plusDays(4), now.plusDays(6), now.plusDays(6).plusHours(2),
                80, "clubadmin@cqu.edu.cn", "PUBLISHED", now);
        Activity closed = ensureActivity(club.getId(), "上学期篮球赛回顾",
                "已结束的院内篮球赛，含合影与赛事总结。", "东区篮球场",
                now.minusDays(20), now.minusDays(12), now.minusDays(11), now.minusDays(11).plusHours(2),
                30, "clubadmin@cqu.edu.cn", "CLOSED", now);

        ensurePendingSignup(first.getId(), student.getId());
        ensureApprovedSignup(closed.getId(), student.getId(), clubAdmin.getId());
        ensureApplication(student2.getId(), "羽毛球社", "申请成立羽毛球社，定期组织训练与院际友谊赛。");
        log.info("初始化完成：平台管理员 admin / 社团管理员 clubadmin / 学生 student、student2，初始密码 123456；平台管理员 {}",
                admin.getUsername());
    }

    private boolean isDemoProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("demo".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private Long ensureRole(String code, String name) {
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("code", code));
        if (role != null) {
            return role.getId();
        }
        Role created = new Role();
        created.setCode(code);
        created.setName(name);
        created.setCreatedAt(LocalDateTime.now());
        roleMapper.insert(created);
        return created.getId();
    }

    private UserAccount ensureUser(String username, String realName, String studentNo, Long roleId) {
        UserAccount account = userAccountMapper.selectOne(new QueryWrapper<UserAccount>().eq("username", username));
        if (account == null) {
            account = new UserAccount();
            account.setUsername(username);
            account.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            account.setRealName(realName);
            account.setStudentNo(studentNo);
            account.setStatus(STATUS_ACTIVE);
            account.setCreatedAt(LocalDateTime.now());
            account.setUpdatedAt(LocalDateTime.now());
            userAccountMapper.insert(account);
            log.info("创建演示账号 {}，初始密码 {}", username, DEFAULT_PASSWORD);
        }
        Long roleCount = userRoleMapper.selectCount(new QueryWrapper<UserRole>()
                .eq("user_id", account.getId()).eq("role_id", roleId));
        if (roleCount == null || roleCount == 0) {
            UserRole userRole = new UserRole();
            userRole.setUserId(account.getId());
            userRole.setRoleId(roleId);
            userRole.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(userRole);
        }
        return account;
    }

    private Club ensureClub(String name, String intro, Long ownerUserId, String contact) {
        Club club = clubMapper.selectOne(new QueryWrapper<Club>().eq("name", name));
        if (club != null) {
            return club;
        }
        Club created = new Club();
        created.setName(name);
        created.setIntro(intro);
        created.setOwnerUserId(ownerUserId);
        created.setContact(contact);
        created.setStatus(STATUS_ACTIVE);
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        clubMapper.insert(created);
        return created;
    }

    private void ensureClubMember(Long clubId, Long userId) {
        Long count = clubMemberMapper.selectCount(new QueryWrapper<ClubMember>()
                .eq("club_id", clubId).eq("user_id", userId));
        if (count != null && count > 0) {
            return;
        }
        ClubMember member = new ClubMember();
        member.setClubId(clubId);
        member.setUserId(userId);
        member.setMemberRole("OWNER");
        member.setStatus(STATUS_ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        clubMemberMapper.insert(member);
    }

    private Activity ensureActivity(Long clubId, String title, String summary, String location,
                                    LocalDateTime signupStart, LocalDateTime signupEnd,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    int capacity, String contact, String status, LocalDateTime now) {
        Activity activity = activityMapper.selectOne(new QueryWrapper<Activity>()
                .eq("club_id", clubId).eq("title", title));
        if (activity != null) {
            return activity;
        }
        activity = new Activity();
        activity.setClubId(clubId);
        activity.setTitle(title);
        activity.setSummary(summary);
        activity.setLocation(location);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setSignupStart(signupStart);
        activity.setSignupEnd(signupEnd);
        activity.setCapacity(capacity);
        activity.setSignedCount(0);
        activity.setStatus(status);
        activity.setContact(contact);
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        activityMapper.insert(activity);
        return activity;
    }

    private void ensurePendingSignup(Long activityId, Long studentId) {
        Long count = signupMapper.selectCount(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("student_id", studentId));
        if (count != null && count > 0) {
            return;
        }
        Signup signup = new Signup();
        signup.setActivityId(activityId);
        signup.setStudentId(studentId);
        signup.setRemark("希望参加现场交流");
        signup.setStatus("PENDING");
        signup.setCreatedAt(LocalDateTime.now());
        signup.setUpdatedAt(LocalDateTime.now());
        signupMapper.insert(signup);
    }

    private void ensureApprovedSignup(Long activityId, Long studentId, Long signedBy) {
        Signup signup = signupMapper.selectOne(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("student_id", studentId));
        if (signup == null) {
            signup = new Signup();
            signup.setActivityId(activityId);
            signup.setStudentId(studentId);
            signup.setRemark("已参加并完成签到");
            signup.setStatus("APPROVED");
            signup.setReviewedAt(LocalDateTime.now());
            signup.setCreatedAt(LocalDateTime.now());
            signup.setUpdatedAt(LocalDateTime.now());
            signupMapper.insert(signup);

            UpdateWrapper<Activity> update = new UpdateWrapper<Activity>();
            update.eq("id", activityId).set("signed_count", 1);
            activityMapper.update(null, update);
        }
        ensureSignin(activityId, signup.getId(), signedBy);
    }

    private void ensureSignin(Long activityId, Long signupId, Long signedBy) {
        Long count = signinMapper.selectCount(new QueryWrapper<Signin>().eq("signup_id", signupId));
        if (count != null && count > 0) {
            return;
        }
        Signin signin = new Signin();
        signin.setActivityId(activityId);
        signin.setSignupId(signupId);
        signin.setSignedBy(signedBy);
        signin.setSignedAt(LocalDateTime.now().minusDays(11));
        signin.setSource("NORMAL");
        signin.setCreatedAt(LocalDateTime.now());
        signinMapper.insert(signin);
    }

    private void ensureApplication(Long applicantId, String clubName, String content) {
        Long count = clubApplicationMapper.selectCount(new QueryWrapper<ClubApplication>()
                .eq("applicant_id", applicantId).eq("club_name", clubName).eq("status", "PENDING"));
        if (count != null && count > 0) {
            return;
        }
        ClubApplication application = new ClubApplication();
        application.setApplicantId(applicantId);
        application.setClubName(clubName);
        application.setContent(content);
        application.setStatus("PENDING");
        application.setCreatedAt(LocalDateTime.now());
        clubApplicationMapper.insert(application);
    }
}
