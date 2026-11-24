package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.entity.Activity;
import com.cqu.campus.domain.entity.Signin;
import com.cqu.campus.domain.entity.SigninApplication;
import com.cqu.campus.domain.entity.Signup;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.dto.SignupCreateRequest;
import com.cqu.campus.domain.dto.SignupReviewRequest;
import com.cqu.campus.mapper.ActivityMapper;
import com.cqu.campus.mapper.SigninApplicationMapper;
import com.cqu.campus.mapper.SigninMapper;
import com.cqu.campus.mapper.SignupMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import com.cqu.campus.domain.vo.SignupSummaryVO;
import com.cqu.campus.domain.vo.SignupVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报名服务：提交、名单、审核、撤销
 */
@Service
public class SignupService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String ROLE_STUDENT = "STUDENT";
    private static final String REASON_FULL = "名额已满，系统自动驳回";
    private static final Set<String> VALID_STATUSES = new HashSet<String>(Arrays.asList(
            STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED, STATUS_CANCELLED));

    private final SignupMapper signupMapper;
    private final ActivityMapper activityMapper;
    private final SigninMapper signinMapper;
    private final SigninApplicationMapper signinApplicationMapper;
    private final UserAccountMapper userAccountMapper;
    private final ActivityService activityService;
    private final ClubService clubService;
    private final MessageService messageService;
    private final AuditService auditService;

    public SignupService(SignupMapper signupMapper, ActivityMapper activityMapper, SigninMapper signinMapper,
                         SigninApplicationMapper signinApplicationMapper, UserAccountMapper userAccountMapper,
                         ActivityService activityService, ClubService clubService, MessageService messageService,
                         AuditService auditService) {
        this.signupMapper = signupMapper;
        this.activityMapper = activityMapper;
        this.signinMapper = signinMapper;
        this.signinApplicationMapper = signinApplicationMapper;
        this.userAccountMapper = userAccountMapper;
        this.activityService = activityService;
        this.clubService = clubService;
        this.messageService = messageService;
        this.auditService = auditService;
    }

    /**
     * 提交报名：事务内锁定活动行，校验状态、窗口、角色与重复；
     * 并发重复由唯一索引兜底并转为 SIGNUP_DUPLICATE，待审核消息在提交后创建。
     */
    @Transactional
    public SignupVO signup(Long activityId, SignupCreateRequest request) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(ROLE_STUDENT)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅学生可提交报名");
        }
        Activity activity = activityMapper.selectForUpdate(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "活动不存在");
        }
        String status = activity.getStatus();
        if (ActivityService.STATUS_DRAFT.equals(status) || ActivityService.STATUS_CLOSED.equals(status)
                || ActivityService.STATUS_ARCHIVED.equals(status)) {
            throw new BizException(ErrorCode.ACTIVITY_CLOSED, "活动未开放报名");
        }
        if (ActivityService.STATUS_FULL.equals(status)) {
            throw new BizException(ErrorCode.SIGNUP_FULL, "名额已满");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getSignupStart() == null || activity.getSignupEnd() == null
                || now.isBefore(activity.getSignupStart()) || now.isAfter(activity.getSignupEnd())) {
            throw new BizException(ErrorCode.ACTIVITY_CLOSED, "不在报名窗口内");
        }
        int remaining = activity.getCapacity() - (activity.getSignedCount() == null ? 0 : activity.getSignedCount());
        Long pendingCount = signupMapper.selectCount(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("status", STATUS_PENDING));
        if (remaining <= 0 || (pendingCount != null && pendingCount >= remaining)) {
            throw new BizException(ErrorCode.SIGNUP_FULL, "待审核报名已满，请等待审核结果或稍后再试");
        }
        Signup existing = signupMapper.selectOne(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("student_id", user.getUserId()));
        if (existing != null) {
            if (STATUS_PENDING.equals(existing.getStatus()) || STATUS_APPROVED.equals(existing.getStatus())) {
                throw new BizException(ErrorCode.SIGNUP_DUPLICATE, "请勿重复报名");
            }
            resetSignup(existing.getId(), request.getRemark(), now);
            scheduleSignupCreatedMessage(user.getUserId(), existing.getId(), activity.getTitle(), now);
            return toVo(signupMapper.selectById(existing.getId()), activity.getTitle(),
                    userAccountMapper.selectById(user.getUserId()), null);
        }
        Signup signup = new Signup();
        signup.setActivityId(activityId);
        signup.setStudentId(user.getUserId());
        signup.setRemark(request.getRemark());
        signup.setStatus(STATUS_PENDING);
        signup.setCreatedAt(now);
        signup.setUpdatedAt(now);
        try {
            signupMapper.insert(signup);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.SIGNUP_DUPLICATE, "请勿重复报名");
        }
        scheduleSignupCreatedMessage(user.getUserId(), signup.getId(), activity.getTitle(), now);
        return toVo(signup, activity.getTitle(), userAccountMapper.selectById(user.getUserId()), null);
    }

    private void scheduleSignupCreatedMessage(final Long studentId, final Long signupId, final String title,
                                              final LocalDateTime round) {
        runAfterCommit(new Runnable() {
            @Override
            public void run() {
                messageService.sendSignupCreated(studentId, signupId, title, round);
            }
        });
    }

    /**
     * 事务提交后执行副作用；无事务时立即执行
     */
    private void runAfterCommit(final Runnable action) {
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

    /**
     * 历史报名记录复用：重置为待审核、刷新报名时间并清空审核/撤销痕迹
     */
    private void resetSignup(Long signupId, String remark, LocalDateTime round) {
        UpdateWrapper<Signup> wrapper = new UpdateWrapper<Signup>();
        wrapper.eq("id", signupId)
                .set("status", STATUS_PENDING)
                .set("created_at", round)
                .set("updated_at", round)
                .setSql("reviewer_id = NULL, review_reason = NULL, reviewed_at = NULL, cancelled_at = NULL");
        if (remark == null) {
            wrapper.setSql("remark = NULL");
        } else {
            wrapper.set("remark", remark);
        }
        signupMapper.update(null, wrapper);
    }

    public PageResult<SignupVO> list(Long activityId, String status, String keyword,
                                     Integer pageNum, Integer pageSize) {
        Activity activity = activityService.requireManageActivity(activityId);
        QueryWrapper<Signup> wrapper = new QueryWrapper<Signup>();
        wrapper.eq("activity_id", activityId);
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null) {
            wrapper.eq("status", normalizedStatus);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String like = "%" + keyword.trim() + "%";
            wrapper.apply("student_id IN (SELECT id FROM user_account WHERE real_name LIKE {0} "
                    + "OR student_no LIKE {1})", like, like);
        }
        wrapper.orderByAsc("created_at").orderByAsc("id");
        Page<Signup> page = new Page<Signup>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<Signup> result = signupMapper.selectPage(page, wrapper);
        Map<Long, UserAccount> students = loadStudents(result.getRecords());
        Map<Long, String> reviewers = loadReviewers(result.getRecords());
        List<SignupVO> records = new ArrayList<SignupVO>();
        for (Signup signup : result.getRecords()) {
            records.add(toVo(signup, activity.getTitle(), students.get(signup.getStudentId()),
                    reviewers.get(signup.getReviewerId())));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 报名汇总：各状态数量、剩余名额与报名窗口状态
     */
    public SignupSummaryVO summary(Long activityId) {
        Activity activity = activityService.requireManageActivity(activityId);
        long pending = countStatus(activityId, STATUS_PENDING);
        long approved = countStatus(activityId, STATUS_APPROVED);
        long rejected = countStatus(activityId, STATUS_REJECTED);
        long cancelled = countStatus(activityId, STATUS_CANCELLED);
        int capacity = activity.getCapacity() == null ? 0 : activity.getCapacity();
        LocalDateTime now = LocalDateTime.now();
        boolean signupOpen = ActivityService.STATUS_PUBLISHED.equals(activity.getStatus())
                && activity.getSignupStart() != null && activity.getSignupEnd() != null
                && !now.isBefore(activity.getSignupStart()) && !now.isAfter(activity.getSignupEnd());
        return SignupSummaryVO.builder()
                .activityId(activityId)
                .activityStatus(activity.getStatus())
                .capacity(capacity)
                .totalCount(pending + approved + rejected + cancelled)
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .cancelledCount(cancelled)
                .remainingCapacity((int) Math.max(capacity - approved, 0))
                .signupOpen(signupOpen)
                .signupStart(activity.getSignupStart())
                .signupEnd(activity.getSignupEnd())
                .build();
    }

    /**
     * 当前学生在指定活动的报名，无记录返回 null
     */
    public SignupVO mySignupForActivity(Long activityId) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(ROLE_STUDENT)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅学生可查看本人报名");
        }
        Activity activity = activityService.requireActivity(activityId);
        Signup signup = signupMapper.selectOne(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("student_id", user.getUserId()));
        if (signup == null) {
            return null;
        }
        UserAccount student = userAccountMapper.selectById(user.getUserId());
        SignupVO vo = toVo(signup, activity.getTitle(), student, reviewerName(signup.getReviewerId()));
        vo.setActivityStartTime(activity.getStartTime());
        vo.setActivityEndTime(activity.getEndTime());
        vo.setActivityStatus(activity.getStatus());
        Signin signin = signinMapper.selectOne(new QueryWrapper<Signin>().eq("signup_id", signup.getId()));
        vo.setSigned(signin != null);
        if (signin != null) {
            vo.setSignedAt(signin.getSignedAt());
            vo.setSigninSource(signin.getSource());
        }
        SigninApplication application = signinApplicationMapper.selectOne(new QueryWrapper<SigninApplication>()
                .eq("signup_id", signup.getId()));
        vo.setSupplementStatus(application == null ? null : application.getStatus());
        return vo;
    }

    /**
     * 我的报名（当前学生）
     */
    public PageResult<SignupVO> mySignups(String status, Integer pageNum, Integer pageSize) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(ROLE_STUDENT)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅学生可查看我的报名");
        }
        QueryWrapper<Signup> wrapper = new QueryWrapper<Signup>();
        wrapper.eq("student_id", user.getUserId());
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null) {
            wrapper.eq("status", normalizedStatus);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<Signup> page = new Page<Signup>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<Signup> result = signupMapper.selectPage(page, wrapper);
        Map<Long, String> titles = loadActivityTitles(result.getRecords());
        Map<Long, Activity> activities = loadActivities(result.getRecords());
        Map<Long, Signin> signins = loadSignins(result.getRecords());
        Map<Long, String> supplementStatuses = loadSupplementStatuses(result.getRecords());
        Map<Long, String> reviewers = loadReviewers(result.getRecords());
        UserAccount student = userAccountMapper.selectById(user.getUserId());
        List<SignupVO> records = new ArrayList<SignupVO>();
        for (Signup signup : result.getRecords()) {
            SignupVO vo = toVo(signup, titles.get(signup.getActivityId()), student,
                    reviewers.get(signup.getReviewerId()));
            Activity activity = activities.get(signup.getActivityId());
            if (activity != null) {
                vo.setActivityStartTime(activity.getStartTime());
                vo.setActivityEndTime(activity.getEndTime());
                vo.setActivityStatus(activity.getStatus());
            }
            Signin signin = signins.get(signup.getId());
            vo.setSigned(signin != null);
            if (signin != null) {
                vo.setSignedAt(signin.getSignedAt());
                vo.setSigninSource(signin.getSource());
            }
            vo.setSupplementStatus(supplementStatuses.get(signup.getId()));
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 审核报名：条件更新 + 容量复核 + 审计 + 结果消息
     */
    @Transactional
    public SignupVO review(Long signupId, SignupReviewRequest request) {
        CurrentUser user = UserContext.require();
        Signup signup = signupMapper.selectById(signupId);
        if (signup == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "报名不存在");
        }
        Activity activity = activityService.requireActivity(signup.getActivityId());
        clubService.requireManage(activity.getClubId());
        if (!STATUS_PENDING.equals(signup.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该报名已被处理");
        }
        if (ActivityService.STATUS_CLOSED.equals(activity.getStatus())
                || ActivityService.STATUS_ARCHIVED.equals(activity.getStatus())
                || ActivityService.STATUS_DRAFT.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "当前活动状态不可审核报名");
        }
        boolean approved = STATUS_APPROVED.equals(request.getDecision());
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (!approved && (reason == null || reason.isEmpty())) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "驳回原因必填");
        }
        if (approved) {
            Activity locked = activityMapper.selectForUpdate(activity.getId());
            if (locked.getSignedCount() >= locked.getCapacity()) {
                throw new BizException(ErrorCode.SIGNUP_FULL, "名额已满，无法通过");
            }
        }
        UpdateWrapper<Signup> wrapper = new UpdateWrapper<Signup>();
        final LocalDateTime reviewedAt = LocalDateTime.now();
        wrapper.eq("id", signupId).eq("status", STATUS_PENDING)
                .set("status", request.getDecision())
                .set("reviewer_id", user.getUserId())
                .set("reviewed_at", reviewedAt)
                .set("updated_at", reviewedAt);
        if (reason == null) {
            wrapper.setSql("review_reason = NULL");
        } else {
            wrapper.set("review_reason", reason);
        }
        int rows = signupMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "报名状态已被他人变更");
        }
        if (approved) {
            UpdateWrapper<Activity> activityWrapper = new UpdateWrapper<Activity>();
            activityWrapper.eq("id", activity.getId()).setSql("signed_count = signed_count + 1")
                    .set("updated_at", reviewedAt);
            activityMapper.update(null, activityWrapper);
            Activity refreshed = activityMapper.selectById(activity.getId());
            if (refreshed.getSignedCount() >= refreshed.getCapacity()) {
                if (ActivityService.STATUS_PUBLISHED.equals(refreshed.getStatus())) {
                    UpdateWrapper<Activity> fullWrapper = new UpdateWrapper<Activity>();
                    fullWrapper.eq("id", activity.getId()).eq("status", ActivityService.STATUS_PUBLISHED)
                            .set("status", ActivityService.STATUS_FULL)
                            .set("updated_at", reviewedAt);
                    activityMapper.update(null, fullWrapper);
                }
                // 名额审满后，其余待审报名自动驳回，避免长期停留在待审核
                autoRejectPending(activity, user.getUserId(), reviewedAt);
            }
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", STATUS_PENDING);
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", request.getDecision());
        after.put("reason", reason);
        auditService.record("REVIEW_SIGNUP", "SIGNUP", signupId, before, after, "SUCCESS");
        final Long studentId = signup.getStudentId();
        final Long currentSignupId = signupId;
        final String decision = request.getDecision();
        final String activityTitle = activity.getTitle();
        runAfterCommit(new Runnable() {
            @Override
            public void run() {
                messageService.sendSignupResult(studentId, currentSignupId, decision, activityTitle, reason, reviewedAt);
            }
        });
        return toVo(signupMapper.selectById(signupId), activity.getTitle(),
                userAccountMapper.selectById(signup.getStudentId()), reviewerName(user.getUserId()));
    }

    /**
     * 名额已满时批量驳回剩余待审报名，写审计并在提交后逐条通知
     */
    private void autoRejectPending(final Activity activity, final Long reviewerId, final LocalDateTime round) {
        final List<Signup> pendings = signupMapper.selectList(new QueryWrapper<Signup>()
                .eq("activity_id", activity.getId()).eq("status", STATUS_PENDING));
        if (pendings.isEmpty()) {
            return;
        }
        UpdateWrapper<Signup> wrapper = new UpdateWrapper<Signup>();
        wrapper.eq("activity_id", activity.getId()).eq("status", STATUS_PENDING)
                .set("status", STATUS_REJECTED)
                .set("reviewer_id", reviewerId)
                .set("reviewed_at", round)
                .set("review_reason", REASON_FULL)
                .set("updated_at", round);
        signupMapper.update(null, wrapper);
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("pendingCount", pendings.size());
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_REJECTED);
        after.put("reason", REASON_FULL);
        auditService.record("AUTO_REJECT_SIGNUPS", "ACTIVITY", activity.getId(), before, after, "SUCCESS");
        for (final Signup pending : pendings) {
            runAfterCommit(new Runnable() {
                @Override
                public void run() {
                    messageService.sendSignupResult(pending.getStudentId(), pending.getId(),
                            STATUS_REJECTED, activity.getTitle(), REASON_FULL, round);
                }
            });
        }
    }

    /**
     * 学生撤销本人报名；已通过的报名释放名额并允许活动从 FULL 回到 PUBLISHED
     */
    @Transactional
    public SignupVO cancel(Long signupId) {
        CurrentUser user = UserContext.require();
        Signup signup = signupMapper.selectById(signupId);
        if (signup == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "报名不存在");
        }
        if (!user.getUserId().equals(signup.getStudentId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "只能撤销本人报名");
        }
        if (!STATUS_PENDING.equals(signup.getStatus()) && !STATUS_APPROVED.equals(signup.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "当前报名状态不可撤销");
        }
        // 统一锁顺序（活动 -> 报名），避免与审核流程互相等待造成死锁
        Activity activity = activityMapper.selectForUpdate(signup.getActivityId());
        if (activity != null) {
            boolean closedStatus = ActivityService.STATUS_CLOSED.equals(activity.getStatus())
                    || ActivityService.STATUS_ARCHIVED.equals(activity.getStatus())
                    || ActivityService.STATUS_DRAFT.equals(activity.getStatus());
            boolean started = activity.getStartTime() != null
                    && !LocalDateTime.now().isBefore(activity.getStartTime());
            if (closedStatus || started) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "活动已开始或已结束，不可撤销报名");
            }
        }
        UpdateWrapper<Signup> wrapper = new UpdateWrapper<Signup>();
        wrapper.eq("id", signupId).in("status", STATUS_PENDING, STATUS_APPROVED)
                .set("status", STATUS_CANCELLED)
                .set("cancelled_at", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now());
        int rows = signupMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "报名状态已被他人变更");
        }
        if (activity != null && STATUS_APPROVED.equals(signup.getStatus())) {
            UpdateWrapper<Activity> activityWrapper = new UpdateWrapper<Activity>();
            activityWrapper.eq("id", activity.getId())
                    .setSql("signed_count = CASE WHEN signed_count > 0 THEN signed_count - 1 ELSE 0 END")
                    .set("updated_at", LocalDateTime.now());
            if (ActivityService.STATUS_FULL.equals(activity.getStatus())) {
                activityWrapper.set("status", ActivityService.STATUS_PUBLISHED);
            }
            activityMapper.update(null, activityWrapper);
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", signup.getStatus());
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_CANCELLED);
        auditService.record("CANCEL_SIGNUP", "SIGNUP", signupId, before, after, "SUCCESS");
        return toVo(signupMapper.selectById(signupId),
                activity == null ? null : activity.getTitle(),
                userAccountMapper.selectById(signup.getStudentId()),
                reviewerName(signup.getReviewerId()));
    }

    private Map<Long, UserAccount> loadStudents(List<Signup> signups) {
        List<Long> studentIds = new ArrayList<Long>();
        for (Signup signup : signups) {
            if (!studentIds.contains(signup.getStudentId())) {
                studentIds.add(signup.getStudentId());
            }
        }
        Map<Long, UserAccount> students = new HashMap<Long, UserAccount>();
        if (studentIds.isEmpty()) {
            return students;
        }
        for (UserAccount account : userAccountMapper.selectBatchIds(studentIds)) {
            students.put(account.getId(), account);
        }
        return students;
    }

    private Map<Long, String> loadActivityTitles(List<Signup> signups) {
        List<Long> activityIds = new ArrayList<Long>();
        for (Signup signup : signups) {
            if (!activityIds.contains(signup.getActivityId())) {
                activityIds.add(signup.getActivityId());
            }
        }
        Map<Long, String> titles = new HashMap<Long, String>();
        if (activityIds.isEmpty()) {
            return titles;
        }
        for (Activity activity : activityMapper.selectBatchIds(activityIds)) {
            titles.put(activity.getId(), activity.getTitle());
        }
        return titles;
    }

    private Map<Long, Activity> loadActivities(List<Signup> signups) {
        Set<Long> ids = new HashSet<Long>();
        for (Signup signup : signups) {
            ids.add(signup.getActivityId());
        }
        Map<Long, Activity> activities = new HashMap<Long, Activity>();
        if (ids.isEmpty()) {
            return activities;
        }
        for (Activity activity : activityMapper.selectBatchIds(ids)) {
            activities.put(activity.getId(), activity);
        }
        return activities;
    }

    private Map<Long, Signin> loadSignins(List<Signup> signups) {
        Set<Long> ids = new HashSet<Long>();
        for (Signup signup : signups) {
            ids.add(signup.getId());
        }
        Map<Long, Signin> signins = new HashMap<Long, Signin>();
        if (ids.isEmpty()) {
            return signins;
        }
        for (Signin signin : signinMapper.selectList(new QueryWrapper<Signin>().in("signup_id", ids))) {
            signins.put(signin.getSignupId(), signin);
        }
        return signins;
    }

    private Map<Long, String> loadSupplementStatuses(List<Signup> signups) {
        Set<Long> ids = new HashSet<Long>();
        for (Signup signup : signups) {
            ids.add(signup.getId());
        }
        Map<Long, String> statuses = new HashMap<Long, String>();
        if (ids.isEmpty()) {
            return statuses;
        }
        for (SigninApplication application : signinApplicationMapper.selectList(
                new QueryWrapper<SigninApplication>().in("signup_id", ids))) {
            statuses.put(application.getSignupId(), application.getStatus());
        }
        return statuses;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        String value = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(value)) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "报名状态不合法");
        }
        return value;
    }

    private long countStatus(Long activityId, String status) {
        Long count = signupMapper.selectCount(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("status", status));
        return count == null ? 0L : count;
    }

    private String reviewerName(Long reviewerId) {
        if (reviewerId == null) {
            return null;
        }
        UserAccount account = userAccountMapper.selectById(reviewerId);
        return account == null ? null : account.getRealName();
    }

    private Map<Long, String> loadReviewers(List<Signup> signups) {
        Set<Long> ids = new HashSet<Long>();
        for (Signup signup : signups) {
            if (signup.getReviewerId() != null) {
                ids.add(signup.getReviewerId());
            }
        }
        Map<Long, String> reviewers = new HashMap<Long, String>();
        if (ids.isEmpty()) {
            return reviewers;
        }
        for (UserAccount account : userAccountMapper.selectBatchIds(ids)) {
            reviewers.put(account.getId(), account.getRealName());
        }
        return reviewers;
    }

    private SignupVO toVo(Signup signup, String activityTitle, UserAccount student, String reviewerName) {
        if (signup == null) {
            return null;
        }
        return SignupVO.builder()
                .id(signup.getId())
                .activityId(signup.getActivityId())
                .activityTitle(activityTitle)
                .studentId(signup.getStudentId())
                .studentNo(student == null ? null : student.getStudentNo())
                .studentName(student == null ? null : student.getRealName())
                .remark(signup.getRemark())
                .status(signup.getStatus())
                .reviewReason(signup.getReviewReason())
                .reviewerName(reviewerName)
                .reviewedAt(signup.getReviewedAt())
                .cancelledAt(signup.getCancelledAt())
                .createdAt(signup.getCreatedAt())
                .build();
    }
}
