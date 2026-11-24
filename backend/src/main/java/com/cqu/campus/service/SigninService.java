package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.DateTimeUtils;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.entity.Activity;
import com.cqu.campus.domain.entity.Signin;
import com.cqu.campus.domain.entity.SigninApplication;
import com.cqu.campus.domain.entity.Signup;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.dto.ExportFile;
import com.cqu.campus.domain.dto.SigninCreateRequest;
import com.cqu.campus.domain.vo.SigninSummaryVO;
import com.cqu.campus.mapper.SigninApplicationMapper;
import com.cqu.campus.mapper.SigninMapper;
import com.cqu.campus.mapper.SignupMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import com.cqu.campus.domain.vo.SigninRosterVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到服务：现场签到、补签、名单与导出
 */
@Service
public class SigninService {

    private static final String SOURCE_NORMAL = "NORMAL";

    private final SigninMapper signinMapper;
    private final SignupMapper signupMapper;
    private final SigninApplicationMapper applicationMapper;
    private final UserAccountMapper userAccountMapper;
    private final ActivityService activityService;
    private final AuditService auditService;

    public SigninService(SigninMapper signinMapper, SignupMapper signupMapper, SigninApplicationMapper applicationMapper,
                         UserAccountMapper userAccountMapper, ActivityService activityService, AuditService auditService) {
        this.signinMapper = signinMapper;
        this.signupMapper = signupMapper;
        this.applicationMapper = applicationMapper;
        this.userAccountMapper = userAccountMapper;
        this.activityService = activityService;
        this.auditService = auditService;
    }

    /**
     * 登记正常签到：仅活动进行中、已通过报名可签到；补签走补签申请审核流程
     */
    @Transactional
    public SigninRosterVO signin(Long activityId, SigninCreateRequest request) {
        CurrentUser user = UserContext.require();
        Activity activity = activityService.requireManageActivity(activityId);
        Signup signup = signupMapper.selectById(request.getSignupId());
        if (signup == null || !activityId.equals(signup.getActivityId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "报名不存在");
        }
        if (!SignupService.STATUS_APPROVED.equals(signup.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅已通过审核的报名可签到");
        }
        Long exists = signinMapper.selectCount(new QueryWrapper<Signin>().eq("signup_id", request.getSignupId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已签到");
        }
        LocalDateTime now = LocalDateTime.now();
        requireSignable(activity, now);
        String remark = request.getRemark() == null ? null : request.getRemark().trim();
        Signin signin = new Signin();
        signin.setActivityId(activityId);
        signin.setSignupId(request.getSignupId());
        signin.setSignedBy(user.getUserId());
        signin.setSignedAt(now);
        signin.setSource(SOURCE_NORMAL);
        signin.setRemark(remark);
        signin.setCreatedAt(now);
        try {
            signinMapper.insert(signin);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已签到");
        }
        auditService.record("SIGNIN", "SIGNUP", signup.getId(), null,
                signinAuditAfter(activityId, signup.getId(), SOURCE_NORMAL, now), "SUCCESS");
        UserAccount student = userAccountMapper.selectById(signup.getStudentId());
        return SigninRosterVO.builder()
                .signupId(signup.getId())
                .studentNo(student == null ? null : student.getStudentNo())
                .studentName(student == null ? null : student.getRealName())
                .signupStatus(signup.getStatus())
                .signed(true)
                .source(signin.getSource())
                .remark(signin.getRemark())
                .signedAt(now)
                .build();
    }

    /**
     * 学生自助签到：仅本人已通过报名、活动进行中且未签到
     */
    @Transactional
    public SigninRosterVO signinSelf(Long signupId) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(SignupService.ROLE_STUDENT)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅学生可自助签到");
        }
        Signup signup = signupId == null ? null : signupMapper.selectById(signupId);
        if (signup == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "报名不存在");
        }
        if (!user.getUserId().equals(signup.getStudentId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅可为本人的报名签到");
        }
        if (!SignupService.STATUS_APPROVED.equals(signup.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅已通过审核的报名可签到");
        }
        Activity activity = activityService.requireActivity(signup.getActivityId());
        Long exists = signinMapper.selectCount(new QueryWrapper<Signin>().eq("signup_id", signupId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已签到");
        }
        LocalDateTime now = LocalDateTime.now();
        requireSignable(activity, now);
        Signin signin = new Signin();
        signin.setActivityId(signup.getActivityId());
        signin.setSignupId(signupId);
        signin.setSignedBy(user.getUserId());
        signin.setSignedAt(now);
        signin.setSource(SOURCE_NORMAL);
        signin.setCreatedAt(now);
        try {
            signinMapper.insert(signin);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已签到");
        }
        auditService.record("SIGNIN", "SIGNUP", signup.getId(), null,
                signinAuditAfter(signup.getActivityId(), signup.getId(), SOURCE_NORMAL, now), "SUCCESS");
        UserAccount student = userAccountMapper.selectById(signup.getStudentId());
        return SigninRosterVO.builder()
                .signupId(signup.getId())
                .studentNo(student == null ? null : student.getStudentNo())
                .studentName(student == null ? null : student.getRealName())
                .signupStatus(signup.getStatus())
                .signed(true)
                .source(signin.getSource())
                .signedAt(now)
                .build();
    }

    /**
     * 签到汇总：已通过人数、已签到人数、签到率与待审补签数
     */
    public SigninSummaryVO summary(Long activityId) {
        Activity activity = activityService.requireManageActivity(activityId);
        Long approved = signupMapper.selectCount(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("status", SignupService.STATUS_APPROVED));
        Long signed = signinMapper.selectCount(new QueryWrapper<Signin>()
                .inSql("signup_id", "SELECT id FROM signups WHERE activity_id = " + activityId
                        + " AND status = '" + SignupService.STATUS_APPROVED + "'"));
        Long pending = applicationMapper.selectCount(new QueryWrapper<SigninApplication>()
                .eq("activity_id", activityId).eq("status", SigninApplicationService.STATUS_PENDING));
        long approvedCount = approved == null ? 0L : approved;
        long signedCount = signed == null ? 0L : signed;
        double signinRate = approvedCount == 0 ? 0d
                : Math.round((double) signedCount / approvedCount * 10000d) / 100d;
        LocalDateTime now = LocalDateTime.now();
        String status = activity.getStatus();
        boolean statusOpen = ActivityService.STATUS_PUBLISHED.equals(status)
                || ActivityService.STATUS_FULL.equals(status);
        boolean windowOpen = statusOpen && activity.getStartTime() != null && activity.getEndTime() != null
                && !now.isBefore(activity.getStartTime()) && !now.isAfter(activity.getEndTime());
        return SigninSummaryVO.builder()
                .activityId(activityId)
                .activityStatus(status)
                .approvedCount(approvedCount)
                .signedCount(signedCount)
                .pendingApplicationCount(pending == null ? 0L : pending)
                .signinRate(signinRate)
                .windowOpen(windowOpen)
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .build();
    }

    /**
     * 统一签到前置条件：活动状态为 PUBLISHED/FULL 且处于活动时间窗口内
     */
    private void requireSignable(Activity activity, LocalDateTime now) {
        String status = activity.getStatus();
        if (!ActivityService.STATUS_PUBLISHED.equals(status) && !ActivityService.STATUS_FULL.equals(status)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "当前活动状态不允许签到");
        }
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动时间未配置，不能签到");
        }
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BizException(ErrorCode.SIGNIN_WINDOW_CLOSED, "不在签到窗口内，活动结束后可提交补签申请");
        }
    }

    private Map<String, Object> signinAuditAfter(Long activityId, Long signupId, String source, LocalDateTime signedAt) {
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("activityId", activityId);
        after.put("signupId", signupId);
        after.put("source", source);
        after.put("signedAt", signedAt.toString());
        return after;
    }

    /**
     * 签到名单分页：已通过报名及签到状态
     */
    public PageResult<SigninRosterVO> roster(Long activityId, Integer pageNum, Integer pageSize) {
        activityService.requireManageActivity(activityId);
        Page<Signup> page = new Page<Signup>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<Signup> result = signupMapper.selectPage(page, new QueryWrapper<Signup>()
                .eq("activity_id", activityId)
                .eq("status", SignupService.STATUS_APPROVED)
                .orderByAsc("created_at").orderByAsc("id"));
        List<Signin> signins = signinMapper.selectList(new QueryWrapper<Signin>().eq("activity_id", activityId));
        Map<Long, Signin> signinMap = new HashMap<Long, Signin>();
        for (Signin signin : signins) {
            signinMap.put(signin.getSignupId(), signin);
        }
        Map<Long, UserAccount> students = loadStudents(result.getRecords());
        Map<Long, String> supplements = loadSupplementStatuses(activityId, result.getRecords());
        List<SigninRosterVO> records = new ArrayList<SigninRosterVO>();
        for (Signup signup : result.getRecords()) {
            records.add(toVo(signup, students.get(signup.getStudentId()), signinMap.get(signup.getId()),
                    supplements.get(signup.getId())));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 导出签到名单 CSV（UTF-8 BOM）
     */
    public ExportFile export(Long activityId) {
        Activity activity = activityService.requireManageActivity(activityId);
        List<Signup> signups = signupMapper.selectList(new QueryWrapper<Signup>()
                .eq("activity_id", activityId)
                .eq("status", SignupService.STATUS_APPROVED)
                .orderByAsc("created_at").orderByAsc("id"));
        List<Signin> signins = signinMapper.selectList(new QueryWrapper<Signin>().eq("activity_id", activityId));
        Map<Long, Signin> signinMap = new HashMap<Long, Signin>();
        for (Signin signin : signins) {
            signinMap.put(signin.getSignupId(), signin);
        }
        Map<Long, UserAccount> students = loadStudents(signups);
        Map<Long, String> supplements = loadSupplementStatuses(activityId, signups);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("活动编号,学号,姓名,报名状态,签到状态,签到时间,签到方式,补签申请状态,备注\r\n");
        for (Signup signup : signups) {
            UserAccount student = students.get(signup.getStudentId());
            Signin signin = signinMap.get(signup.getId());
            builder.append(csv(String.valueOf(activityId))).append(',')
                    .append(csv(student == null ? null : student.getStudentNo())).append(',')
                    .append(csv(student == null ? null : student.getRealName())).append(',')
                    .append(csv(signup.getStatus())).append(',')
                    .append(csv(signin == null ? "未签到" : "已签到")).append(',')
                    .append(csv(signin == null ? null : DateTimeUtils.format(signin.getSignedAt()))).append(',')
                    .append(csv(signin == null ? null : signin.getSource())).append(',')
                    .append(csv(supplements.get(signup.getId()))).append(',')
                    .append(csv(signin == null ? null : signin.getRemark())).append("\r\n");
        }
        auditService.record("EXPORT_SIGNINS", "ACTIVITY", activityId, null, activity.getTitle(), "SUCCESS");
        return new ExportFile("signins-" + activityId + ".csv", builder.toString());
    }

    private Map<Long, String> loadSupplementStatuses(Long activityId, List<Signup> signups) {
        Map<Long, String> statuses = new HashMap<Long, String>();
        if (signups == null || signups.isEmpty()) {
            return statuses;
        }
        List<Long> signupIds = new ArrayList<Long>();
        for (Signup signup : signups) {
            signupIds.add(signup.getId());
        }
        List<SigninApplication> applications = applicationMapper.selectList(new QueryWrapper<SigninApplication>()
                .eq("activity_id", activityId).in("signup_id", signupIds));
        for (SigninApplication application : applications) {
            statuses.put(application.getSignupId(), application.getStatus());
        }
        return statuses;
    }

    private Map<Long, UserAccount> loadStudents(List<Signup> signups) {
        Map<Long, UserAccount> students = new HashMap<Long, UserAccount>();
        if (signups == null || signups.isEmpty()) {
            return students;
        }
        List<Long> studentIds = new ArrayList<Long>();
        for (Signup signup : signups) {
            if (!studentIds.contains(signup.getStudentId())) {
                studentIds.add(signup.getStudentId());
            }
        }
        for (UserAccount account : userAccountMapper.selectBatchIds(studentIds)) {
            students.put(account.getId(), account);
        }
        return students;
    }

    private SigninRosterVO toVo(Signup signup, UserAccount student, Signin signin, String supplementStatus) {
        return SigninRosterVO.builder()
                .signupId(signup.getId())
                .studentNo(student == null ? null : student.getStudentNo())
                .studentName(student == null ? null : student.getRealName())
                .signupStatus(signup.getStatus())
                .signed(signin != null)
                .source(signin == null ? null : signin.getSource())
                .supplementStatus(supplementStatus)
                .remark(signin == null ? null : signin.getRemark())
                .signedAt(signin == null ? null : signin.getSignedAt())
                .build();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        // 防 CSV 公式注入：以 = + - @ 或制表符/回车开头的单元格前置单引号，避免被 Excel 当作公式执行
        String safe = value;
        char first = safe.isEmpty() ? ' ' : safe.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            safe = "'" + safe;
        }
        boolean needQuote = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
        String escaped = safe.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
