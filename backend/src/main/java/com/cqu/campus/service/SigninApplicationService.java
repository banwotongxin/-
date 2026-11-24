package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.dto.SigninApplicationReviewRequest;
import com.cqu.campus.domain.dto.SigninApplyRequest;
import com.cqu.campus.domain.entity.Activity;
import com.cqu.campus.domain.entity.Signin;
import com.cqu.campus.domain.entity.SigninApplication;
import com.cqu.campus.domain.entity.Signup;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.vo.SigninApplicationVO;
import com.cqu.campus.mapper.ActivityMapper;
import com.cqu.campus.mapper.SigninApplicationMapper;
import com.cqu.campus.mapper.SigninMapper;
import com.cqu.campus.mapper.SignupMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 补签申请服务：学生发起，活动所属社团负责人审核，通过后生成补签记录
 */
@Service
public class SigninApplicationService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SigninApplicationMapper applicationMapper;
    private final SigninMapper signinMapper;
    private final SignupMapper signupMapper;
    private final ActivityMapper activityMapper;
    private final UserAccountMapper userAccountMapper;
    private final ActivityService activityService;
    private final ClubService clubService;
    private final MessageService messageService;
    private final AuditService auditService;

    public SigninApplicationService(SigninApplicationMapper applicationMapper, SigninMapper signinMapper,
                                    SignupMapper signupMapper, ActivityMapper activityMapper,
                                    UserAccountMapper userAccountMapper, ActivityService activityService,
                                    ClubService clubService, MessageService messageService, AuditService auditService) {
        this.applicationMapper = applicationMapper;
        this.signinMapper = signinMapper;
        this.signupMapper = signupMapper;
        this.activityMapper = activityMapper;
        this.userAccountMapper = userAccountMapper;
        this.activityService = activityService;
        this.clubService = clubService;
        this.messageService = messageService;
        this.auditService = auditService;
    }

    /**
     * 学生提交补签申请：仅本人已通过报名、活动已结束、未签到且无有效申请
     */
    @Transactional
    public SigninApplicationVO apply(Long activityId, SigninApplyRequest request) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(SignupService.ROLE_STUDENT)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅学生可申请补签");
        }
        Activity activity = activityService.requireActivity(activityId);
        if ("DRAFT".equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动未发布，不能申请补签");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动时间未配置，不能申请补签");
        }
        if (!now.isAfter(activity.getEndTime())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动结束后才能申请补签");
        }
        validateSignedAt(activity, request.getSignedAt(), now);

        Signup signup = signupMapper.selectOne(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("student_id", user.getUserId()));
        if (signup == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "报名不存在");
        }
        if (!SignupService.STATUS_APPROVED.equals(signup.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅已通过审核的报名可申请补签");
        }
        Long signed = signinMapper.selectCount(new QueryWrapper<Signin>().eq("signup_id", signup.getId()));
        if (signed != null && signed > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已签到，无需补签");
        }

        SigninApplication application = applicationMapper.selectOne(new QueryWrapper<SigninApplication>()
                .eq("signup_id", signup.getId()));
        if (application == null) {
            application = new SigninApplication();
            application.setActivityId(activityId);
            application.setSignupId(signup.getId());
            application.setStudentId(user.getUserId());
            application.setSignedAt(request.getSignedAt());
            application.setReason(request.getReason().trim());
            application.setStatus(STATUS_PENDING);
            application.setCreatedAt(now);
            try {
                applicationMapper.insert(application);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "已有待审核的补签申请");
            }
        } else {
            if (STATUS_PENDING.equals(application.getStatus())) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "已有待审核的补签申请");
            }
            if (STATUS_APPROVED.equals(application.getStatus())) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "补签申请已通过");
            }
            application.setSignedAt(request.getSignedAt());
            application.setReason(request.getReason().trim());
            application.setStatus(STATUS_PENDING);
            application.setReviewerId(null);
            application.setReviewReason(null);
            application.setReviewedAt(null);
            application.setCreatedAt(now);
            applicationMapper.updateById(application);
        }

        Map<String, Object> after = new HashMap<String, Object>();
        after.put("activityId", activityId);
        after.put("signupId", signup.getId());
        after.put("signedAt", request.getSignedAt().toString());
        after.put("reason", application.getReason());
        auditService.record("APPLY_SIGNIN", "SIGNIN_APPLICATION", application.getId(), null, after, "SUCCESS");
        return toVo(application, activity.getTitle(), userAccountMapper.selectById(user.getUserId()));
    }

    /**
     * 社团管理员查看本社团活动的补签申请
     */
    public PageResult<SigninApplicationVO> list(Long activityId, String status, Integer pageNum, Integer pageSize) {
        Activity activity = activityService.requireManageActivity(activityId);
        QueryWrapper<SigninApplication> wrapper = new QueryWrapper<SigninApplication>();
        wrapper.eq("activity_id", activityId);
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<SigninApplication> page = new Page<SigninApplication>(
                PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<SigninApplication> result = applicationMapper.selectPage(page, wrapper);
        Map<Long, UserAccount> students = loadStudents(result.getRecords());
        List<SigninApplicationVO> records = new ArrayList<SigninApplicationVO>();
        for (SigninApplication item : result.getRecords()) {
            records.add(toVo(item, activity.getTitle(), students.get(item.getStudentId())));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 学生查看自己的补签申请
     */
    public PageResult<SigninApplicationVO> my(Integer pageNum, Integer pageSize) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(SignupService.ROLE_STUDENT)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅学生可查看补签申请");
        }
        QueryWrapper<SigninApplication> wrapper = new QueryWrapper<SigninApplication>();
        wrapper.eq("student_id", user.getUserId()).orderByDesc("created_at").orderByDesc("id");
        Page<SigninApplication> page = new Page<SigninApplication>(
                PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<SigninApplication> result = applicationMapper.selectPage(page, wrapper);
        Map<Long, String> titles = loadActivityTitles(result.getRecords());
        UserAccount student = userAccountMapper.selectById(user.getUserId());
        List<SigninApplicationVO> records = new ArrayList<SigninApplicationVO>();
        for (SigninApplication item : result.getRecords()) {
            records.add(toVo(item, titles.get(item.getActivityId()), student));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 负责人审核补签申请：通过时复核时间规则并生成补签记录
     */
    @Transactional
    public SigninApplicationVO review(Long id, SigninApplicationReviewRequest request) {
        CurrentUser user = UserContext.require();
        SigninApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "补签申请不存在");
        }
        Activity activity = activityService.requireActivity(application.getActivityId());
        clubService.requireManage(activity.getClubId());
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该补签申请已处理");
        }
        String decision = request.getDecision().trim().toUpperCase();
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (STATUS_REJECTED.equals(decision) && (reason == null || reason.isEmpty())) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "驳回时必须填写原因");
        }
        Signup signup = signupMapper.selectById(application.getSignupId());
        if (signup == null || !SignupService.STATUS_APPROVED.equals(signup.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "报名状态异常，无法审核补签");
        }
        LocalDateTime now = LocalDateTime.now();
        if (STATUS_APPROVED.equals(decision)) {
            if (activity.getEndTime() == null || !now.isAfter(activity.getEndTime())) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "活动结束后才能通过补签申请");
            }
            validateSignedAt(activity, application.getSignedAt(), now);
            Long signed = signinMapper.selectCount(new QueryWrapper<Signin>().eq("signup_id", signup.getId()));
            if (signed != null && signed > 0) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已签到");
            }
        }

        int rows = applicationMapper.update(null, new UpdateWrapper<SigninApplication>()
                .eq("id", id).eq("status", STATUS_PENDING)
                .set("status", decision)
                .set("reviewer_id", user.getUserId())
                .set("review_reason", reason)
                .set("reviewed_at", now));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该补签申请已被处理");
        }

        if (STATUS_APPROVED.equals(decision)) {
            Signin signin = new Signin();
            signin.setActivityId(application.getActivityId());
            signin.setSignupId(application.getSignupId());
            signin.setSignedBy(user.getUserId());
            signin.setSignedAt(application.getSignedAt());
            signin.setSource(SOURCE_MANUAL);
            signin.setRemark(application.getReason());
            signin.setCreatedAt(now);
            try {
                signinMapper.insert(signin);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已签到");
            }
        }

        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", STATUS_PENDING);
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", decision);
        after.put("reviewReason", reason);
        after.put("signedAt", application.getSignedAt().toString());
        auditService.record("REVIEW_SIGNIN", "SIGNIN_APPLICATION", id, before, after, "SUCCESS");

        final SigninApplication saved = applicationMapper.selectById(id);
        final String activityTitle = activity.getTitle();
        final String finalDecision = decision;
        final String finalReason = reason;
        sendAfterCommit(new Runnable() {
            @Override
            public void run() {
                String title = STATUS_APPROVED.equals(finalDecision) ? "补签申请已通过" : "补签申请已驳回";
                StringBuilder content = new StringBuilder("《").append(activityTitle).append("》的补签申请")
                        .append(STATUS_APPROVED.equals(finalDecision) ? "已通过，补签时间：" : "已驳回");
                if (STATUS_APPROVED.equals(finalDecision)) {
                    content.append(saved.getSignedAt().format(MESSAGE_TIME));
                } else if (finalReason != null && !finalReason.isEmpty()) {
                    content.append("，原因：").append(finalReason);
                }
                messageService.send(saved.getStudentId(), "SIGNIN_RESULT", title, content.toString(),
                        "signin-review:" + saved.getId() + ":" + finalDecision);
            }
        });
        return toVo(saved, activityTitle, userAccountMapper.selectById(saved.getStudentId()));
    }

    private void validateSignedAt(Activity activity, LocalDateTime signedAt, LocalDateTime now) {
        if (activity.getStartTime() != null && !signedAt.isAfter(activity.getStartTime())) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "补签时间必须晚于活动开始时间");
        }
        if (activity.getEndTime() != null && signedAt.isAfter(activity.getEndTime())) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "补签时间不能晚于活动结束时间");
        }
        if (!signedAt.isBefore(now)) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "补签时间必须早于当前时间");
        }
    }

    private Map<Long, UserAccount> loadStudents(List<SigninApplication> applications) {
        Set<Long> ids = new HashSet<Long>();
        for (SigninApplication item : applications) {
            ids.add(item.getStudentId());
        }
        Map<Long, UserAccount> users = new HashMap<Long, UserAccount>();
        if (ids.isEmpty()) {
            return users;
        }
        for (UserAccount account : userAccountMapper.selectBatchIds(ids)) {
            users.put(account.getId(), account);
        }
        return users;
    }

    private Map<Long, String> loadActivityTitles(List<SigninApplication> applications) {
        Set<Long> ids = new HashSet<Long>();
        for (SigninApplication item : applications) {
            ids.add(item.getActivityId());
        }
        Map<Long, String> titles = new HashMap<Long, String>();
        if (ids.isEmpty()) {
            return titles;
        }
        for (Activity activity : activityMapper.selectBatchIds(ids)) {
            titles.put(activity.getId(), activity.getTitle());
        }
        return titles;
    }

    private void sendAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private SigninApplicationVO toVo(SigninApplication application, String activityTitle, UserAccount student) {
        return SigninApplicationVO.builder()
                .id(application.getId())
                .activityId(application.getActivityId())
                .activityTitle(activityTitle)
                .signupId(application.getSignupId())
                .studentId(application.getStudentId())
                .studentNo(student == null ? null : student.getStudentNo())
                .studentName(student == null ? null : student.getRealName())
                .signedAt(application.getSignedAt())
                .reason(application.getReason())
                .status(application.getStatus())
                .reviewReason(application.getReviewReason())
                .reviewedAt(application.getReviewedAt())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
