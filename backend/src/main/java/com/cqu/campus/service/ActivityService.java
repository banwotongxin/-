package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.entity.Activity;
import com.cqu.campus.domain.entity.ActivityTag;
import com.cqu.campus.domain.entity.Club;
import com.cqu.campus.domain.entity.Signup;
import com.cqu.campus.domain.dto.ActivityCreateRequest;
import com.cqu.campus.domain.dto.ActivityUpdateRequest;
import com.cqu.campus.mapper.ActivityMapper;
import com.cqu.campus.mapper.ActivityTagMapper;
import com.cqu.campus.mapper.ClubMapper;
import com.cqu.campus.mapper.SignupMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import com.cqu.campus.domain.vo.ActivityVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 活动服务：草稿、发布、编辑、关闭与检索
 */
@Service
public class ActivityService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_FULL = "FULL";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Set<String> VALID_STATUSES = new HashSet<String>(Arrays.asList(
            STATUS_DRAFT, STATUS_PUBLISHED, STATUS_FULL, STATUS_CLOSED, STATUS_ARCHIVED));

    private final ActivityMapper activityMapper;
    private final ActivityTagMapper activityTagMapper;
    private final ClubMapper clubMapper;
    private final SignupMapper signupMapper;
    private final ClubService clubService;
    private final AuditService auditService;
    private final MessageService messageService;

    public ActivityService(ActivityMapper activityMapper, ActivityTagMapper activityTagMapper, ClubMapper clubMapper,
                           SignupMapper signupMapper, ClubService clubService, AuditService auditService,
                           MessageService messageService) {
        this.activityMapper = activityMapper;
        this.activityTagMapper = activityTagMapper;
        this.clubMapper = clubMapper;
        this.signupMapper = signupMapper;
        this.clubService = clubService;
        this.auditService = auditService;
        this.messageService = messageService;
    }

    public PageResult<ActivityVO> list(String keyword, Long clubId, String status, String tag, LocalDateTime from,
                                       LocalDateTime to, Integer pageNum, Integer pageSize) {
        CurrentUser user = UserContext.get();
        QueryWrapper<Activity> wrapper = new QueryWrapper<Activity>();
        String targetStatus = status == null || status.trim().isEmpty() ? null : status.trim().toUpperCase();
        if (targetStatus != null && !VALID_STATUSES.contains(targetStatus)) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "活动状态筛选值非法");
        }
        boolean platformAdmin = user != null && user.hasRole(ClubService.ROLE_PLATFORM_ADMIN);
        List<Long> managedIds = null;
        if (user != null && !platformAdmin && user.hasRole(ClubService.ROLE_CLUB_ADMIN)) {
            managedIds = clubService.currentManagedClubIds();
        }
        final List<Long> visibleClubIds = managedIds;
        if (targetStatus != null) {
            if (STATUS_DRAFT.equals(targetStatus)) {
                if (!platformAdmin && managedIds == null) {
                    throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权查看草稿活动");
                }
                wrapper.eq("status", STATUS_DRAFT);
                if (!platformAdmin) {
                    if (managedIds.isEmpty()) {
                        return PageResult.empty(pageNum, pageSize);
                    }
                    wrapper.in("club_id", managedIds);
                }
            } else {
                wrapper.eq("status", targetStatus);
            }
        } else if (!platformAdmin) {
            if (managedIds != null && !managedIds.isEmpty()) {
                // 社团管理员可查看本社团草稿，其他用户不可见草稿
                wrapper.and(w -> w.ne("status", STATUS_DRAFT).or().in("club_id", visibleClubIds));
            } else {
                wrapper.ne("status", STATUS_DRAFT);
            }
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like("title", trimmed).or().like("summary", trimmed));
        }
        if (clubId != null) {
            wrapper.eq("club_id", clubId);
        }
        if (tag != null && !tag.trim().isEmpty()) {
            wrapper.apply("id IN (SELECT activity_id FROM activity_tags WHERE tag = {0})", tag.trim());
        }
        if (from != null) {
            wrapper.ge("start_time", from);
        }
        if (to != null) {
            wrapper.le("start_time", to);
        }
        wrapper.orderByDesc("start_time").orderByDesc("id");
        Page<Activity> page = new Page<Activity>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<Activity> result = activityMapper.selectPage(page, wrapper);
        Map<Long, String> clubNames = loadClubNames(result.getRecords());
        Map<Long, List<String>> tags = loadTags(result.getRecords());
        List<ActivityVO> records = new ArrayList<ActivityVO>();
        for (Activity activity : result.getRecords()) {
            records.add(toVo(activity, clubNames.get(activity.getClubId()), tags.get(activity.getId())));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    public ActivityVO detail(Long id) {
        Activity activity = requireActivity(id);
        if (STATUS_DRAFT.equals(activity.getStatus()) && !clubService.canManage(activity.getClubId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权查看该活动");
        }
        return toVo(activity, clubName(activity.getClubId()), loadTags(activity.getId()));
    }

    /**
     * 创建活动：默认草稿，publish=true 时校验通过后直接发布
     */
    @Transactional
    public ActivityVO create(ActivityCreateRequest request) {
        Club club = clubService.requireManage(request.getClubId());
        validateTime(request.getSignupStart(), request.getSignupEnd(), request.getStartTime(), request.getEndTime());
        validateCapacity(request.getCapacity(), 0);
        Activity activity = new Activity();
        activity.setClubId(club.getId());
        activity.setTitle(request.getTitle().trim());
        activity.setSummary(request.getSummary());
        activity.setLocation(request.getLocation());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setSignupStart(request.getSignupStart());
        activity.setSignupEnd(request.getSignupEnd());
        activity.setCapacity(request.getCapacity());
        activity.setSignedCount(0);
        activity.setContact(request.getContact());
        activity.setStatus(STATUS_DRAFT);
        activity.setCreatedAt(LocalDateTime.now());
        activity.setUpdatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
        replaceTags(activity.getId(), request.getTags());
        if (Boolean.TRUE.equals(request.getPublish())) {
            publishInternal(activity, club);
            activity = activityMapper.selectById(activity.getId());
        }
        return toVo(activity, club.getName(), loadTags(activity.getId()));
    }

    @Transactional
    public ActivityVO update(Long id, ActivityUpdateRequest request) {
        Activity activity = requireManageActivity(id);
        if (STATUS_CLOSED.equals(activity.getStatus()) || STATUS_ARCHIVED.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "已关闭或已归档活动不可编辑");
        }
        String title = request.getTitle() == null || request.getTitle().trim().isEmpty()
                ? activity.getTitle() : request.getTitle().trim();
        String summary = request.getSummary() == null ? activity.getSummary() : request.getSummary();
        String location = request.getLocation() == null ? activity.getLocation() : request.getLocation();
        String contact = request.getContact() == null ? activity.getContact() : request.getContact();
        LocalDateTime startTime = request.getStartTime() == null ? activity.getStartTime() : request.getStartTime();
        LocalDateTime endTime = request.getEndTime() == null ? activity.getEndTime() : request.getEndTime();
        LocalDateTime signupStart = request.getSignupStart() == null ? activity.getSignupStart() : request.getSignupStart();
        LocalDateTime signupEnd = request.getSignupEnd() == null ? activity.getSignupEnd() : request.getSignupEnd();
        Integer capacity = request.getCapacity() == null ? activity.getCapacity() : request.getCapacity();
        validateTime(signupStart, signupEnd, startTime, endTime);
        Long approved = countApproved(id);
        validateCapacity(capacity, approved.intValue());

        UpdateWrapper<Activity> wrapper = new UpdateWrapper<Activity>();
        final LocalDateTime changeAt = LocalDateTime.now();
        wrapper.eq("id", id)
                .set("title", title)
                .set("summary", summary)
                .set("location", location)
                .set("start_time", startTime)
                .set("end_time", endTime)
                .set("signup_start", signupStart)
                .set("signup_end", signupEnd)
                .set("capacity", capacity)
                .set("contact", contact)
                .set("updated_at", changeAt);
        // 已满活动在容量提升后恢复为已发布
        if (STATUS_FULL.equals(activity.getStatus()) && capacity > approved.intValue()) {
            wrapper.set("status", STATUS_PUBLISHED);
        }
        activityMapper.update(null, wrapper);
        if (request.getTags() != null) {
            replaceTags(id, request.getTags());
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("title", activity.getTitle());
        before.put("capacity", activity.getCapacity());
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("title", title);
        after.put("capacity", capacity);
        auditService.record("UPDATE_ACTIVITY", "ACTIVITY", id, before, after, "SUCCESS");
        notifyActivityChanged(activity, title, location, startTime, endTime, changeAt);
        Activity updated = activityMapper.selectById(id);
        return toVo(updated, clubName(updated.getClubId()), loadTags(id));
    }

    /**
     * 已发布/已满活动的时间或地点发生变化时，事务提交后通知已通过学生
     */
    private void notifyActivityChanged(Activity activity, String title, String location,
                                       LocalDateTime startTime, LocalDateTime endTime, LocalDateTime changeAt) {
        if (!STATUS_PUBLISHED.equals(activity.getStatus()) && !STATUS_FULL.equals(activity.getStatus())) {
            return;
        }
        List<String> changes = new ArrayList<String>();
        if (!startTime.equals(activity.getStartTime()) || !endTime.equals(activity.getEndTime())) {
            changes.add("活动时间调整为 " + MESSAGE_TIME.format(startTime) + " 至 " + MESSAGE_TIME.format(endTime));
        }
        if (location != null && !location.equals(activity.getLocation())) {
            changes.add("活动地点调整为 " + location);
        }
        if (changes.isEmpty()) {
            return;
        }
        long changeStamp = changeAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        notifyApprovedSignups(activity, "活动信息已变更",
                "《" + title + "》" + String.join("；", changes) + "，请留意最新安排。", "update-" + changeStamp);
    }

    @Transactional
    public ActivityVO publish(Long id) {
        Activity activity = requireManageActivity(id);
        if (!STATUS_DRAFT.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅草稿活动可发布");
        }
        Club club = clubService.requireClub(activity.getClubId());
        publishInternal(activity, club);
        Activity updated = activityMapper.selectById(id);
        return toVo(updated, club.getName(), loadTags(id));
    }

    /**
     * 发布校验与条件更新：草稿 -> 已发布
     */
    private void publishInternal(Activity activity, Club club) {
        if (activity.getTakedownAt() != null) {
            throw new BizException(ErrorCode.ACTIVITY_OFFLINE, "活动已被平台下架，需平台管理员恢复");
        }
        if (!ClubService.STATUS_ACTIVE.equals(club.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "社团处于冻结状态，禁止发布活动");
        }
        validateTime(activity.getSignupStart(), activity.getSignupEnd(), activity.getStartTime(), activity.getEndTime());
        validateCapacity(activity.getCapacity(), 0);
        UpdateWrapper<Activity> wrapper = new UpdateWrapper<Activity>();
        wrapper.eq("id", activity.getId()).eq("status", STATUS_DRAFT)
                .set("status", STATUS_PUBLISHED)
                .set("updated_at", LocalDateTime.now());
        int rows = activityMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动状态已被他人变更");
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", STATUS_DRAFT);
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_PUBLISHED);
        auditService.record("PUBLISH_ACTIVITY", "ACTIVITY", activity.getId(), before, after, "SUCCESS");
    }

    @Transactional
    public ActivityVO close(Long id, String reason) {
        Activity activity = requireManageActivity(id);
        if (!STATUS_PUBLISHED.equals(activity.getStatus()) && !STATUS_FULL.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅已发布或已满活动可关闭");
        }
        UpdateWrapper<Activity> wrapper = new UpdateWrapper<Activity>();
        wrapper.eq("id", id).in("status", STATUS_PUBLISHED, STATUS_FULL)
                .set("status", STATUS_CLOSED)
                .set("updated_at", LocalDateTime.now());
        int rows = activityMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动状态已被他人变更");
        }
        rejectPendingSignups(activity, "活动已关闭，报名自动驳回");
        notifyApprovedSignups(activity, "活动已关闭",
                "《" + activity.getTitle() + "》已关闭"
                        + (reason == null || reason.trim().isEmpty() ? "。" : "，原因：" + reason.trim() + "。"),
                "closed");
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", activity.getStatus());
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_CLOSED);
        after.put("reason", reason);
        auditService.record("CLOSE_ACTIVITY", "ACTIVITY", id, before, after, "SUCCESS");
        Activity updated = activityMapper.selectById(id);
        return toVo(updated, clubName(updated.getClubId()), loadTags(id));
    }

    /**
     * 平台管理员下架违规活动：已发布/已满 -> 草稿，学生端不再可见
     */
    @Transactional
    public ActivityVO takedown(Long id, String reason) {
        clubService.requirePlatformAdmin();
        if (reason == null || reason.trim().isEmpty()) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "下架原因不能为空");
        }
        Activity activity = requireActivity(id);
        if (!STATUS_PUBLISHED.equals(activity.getStatus()) && !STATUS_FULL.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅已发布或已满活动可下架");
        }
        String trimmedReason = reason.trim();
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<Activity> wrapper = new UpdateWrapper<Activity>();
        wrapper.eq("id", id).in("status", STATUS_PUBLISHED, STATUS_FULL)
                .set("status", STATUS_DRAFT)
                .set("takedown_reason", trimmedReason)
                .set("takedown_at", now)
                .set("updated_at", now);
        int rows = activityMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动状态已被他人变更");
        }
        rejectPendingSignups(activity, "活动已被平台下架，报名自动驳回");
        notifyApprovedSignups(activity, "活动已下架",
                "《" + activity.getTitle() + "》已被平台下架"
                        + (reason == null || reason.trim().isEmpty() ? "。" : "，原因：" + reason.trim() + "。"),
                "takedown-" + System.currentTimeMillis());
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", activity.getStatus());
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_DRAFT);
        after.put("reason", trimmedReason);
        auditService.record("TAKEDOWN_ACTIVITY", "ACTIVITY", id, before, after, "SUCCESS");
        Activity updated = activityMapper.selectById(id);
        return toVo(updated, clubName(updated.getClubId()), loadTags(id));
    }

    /**
     * 平台管理员恢复上架：已下架草稿重新校验后恢复为已发布
     */
    @Transactional
    public ActivityVO restore(Long id) {
        clubService.requirePlatformAdmin();
        Activity activity = requireActivity(id);
        if (activity.getTakedownAt() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动未被下架，无需恢复");
        }
        if (!STATUS_DRAFT.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅已下架的草稿活动可恢复上架");
        }
        Club club = clubService.requireClub(activity.getClubId());
        if (!ClubService.STATUS_ACTIVE.equals(club.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "社团处于冻结状态，禁止恢复上架");
        }
        validateTime(activity.getSignupStart(), activity.getSignupEnd(),
                activity.getStartTime(), activity.getEndTime());
        Long approved = countApproved(id);
        validateCapacity(activity.getCapacity(), approved.intValue());
        String reason = activity.getTakedownReason();
        UpdateWrapper<Activity> wrapper = new UpdateWrapper<Activity>();
        wrapper.eq("id", id).eq("status", STATUS_DRAFT).isNotNull("takedown_at")
                .set("status", STATUS_PUBLISHED)
                .set("takedown_reason", null)
                .set("takedown_at", null)
                .set("updated_at", LocalDateTime.now());
        int rows = activityMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动状态已被他人变更");
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", STATUS_DRAFT);
        before.put("takedownReason", reason);
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_PUBLISHED);
        auditService.record("RESTORE_ACTIVITY", "ACTIVITY", id, before, after, "SUCCESS");
        Activity updated = activityMapper.selectById(id);
        return toVo(updated, club.getName(), loadTags(id));
    }

    /**
     * 删除草稿活动（社团管理员或平台管理员）
     */
    @Transactional
    public void deleteDraft(Long id) {
        Activity activity = requireManageActivity(id);
        if (!STATUS_DRAFT.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅草稿活动可删除");
        }
        Long signupCount = signupMapper.selectCount(new QueryWrapper<Signup>().eq("activity_id", id));
        if (signupCount != null && signupCount > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "活动已有报名记录，不可删除，请关闭并保留历史");
        }
        activityTagMapper.delete(new QueryWrapper<ActivityTag>().eq("activity_id", id));
        activityMapper.deleteById(id);
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("title", activity.getTitle());
        before.put("status", activity.getStatus());
        auditService.record("DELETE_ACTIVITY", "ACTIVITY", id, before, null, "SUCCESS");
    }

    /**
     * 关闭或下架活动时自动驳回待审核报名，并在事务提交后通知学生
     */
    private void rejectPendingSignups(final Activity activity, final String reason) {
        final List<Signup> pendings = signupMapper.selectList(new QueryWrapper<Signup>()
                .eq("activity_id", activity.getId()).eq("status", SignupService.STATUS_PENDING));
        if (pendings.isEmpty()) {
            return;
        }
        UpdateWrapper<Signup> wrapper = new UpdateWrapper<Signup>();
        final LocalDateTime rejectedAt = LocalDateTime.now();
        wrapper.eq("activity_id", activity.getId()).eq("status", SignupService.STATUS_PENDING)
                .set("status", SignupService.STATUS_REJECTED)
                .set("reviewer_id", UserContext.require().getUserId())
                .set("reviewed_at", rejectedAt)
                .set("review_reason", reason)
                .set("updated_at", rejectedAt);
        signupMapper.update(null, wrapper);
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("pendingCount", pendings.size());
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", SignupService.STATUS_REJECTED);
        after.put("reason", reason);
        auditService.record("AUTO_REJECT_SIGNUPS", "ACTIVITY", activity.getId(), before, after, "SUCCESS");
        for (final Signup signup : pendings) {
            runAfterCommit(new Runnable() {
                @Override
                public void run() {
                    messageService.sendSignupResult(signup.getStudentId(), signup.getId(),
                            SignupService.STATUS_REJECTED, activity.getTitle(), reason, rejectedAt);
                }
            });
        }
    }

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
     * 向活动已通过报名的学生投递变更消息，事务提交后执行
     */
    private void notifyApprovedSignups(final Activity activity, final String title, final String content,
                                       final String changeKey) {
        List<Signup> approvedSignups = signupMapper.selectList(new QueryWrapper<Signup>()
                .eq("activity_id", activity.getId()).eq("status", SignupService.STATUS_APPROVED));
        for (final Signup signup : approvedSignups) {
            runAfterCommit(new Runnable() {
                @Override
                public void run() {
                    messageService.sendActivityChange(signup.getStudentId(), activity.getId(),
                            changeKey, title, content);
                }
            });
        }
    }

    public Activity requireActivity(Long id) {
        Activity activity = id == null ? null : activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    /**
     * 校验活动管理权限并返回活动
     */
    public Activity requireManageActivity(Long id) {
        Activity activity = requireActivity(id);
        clubService.requireManage(activity.getClubId());
        return activity;
    }

    public Long countApproved(Long activityId) {
        return signupMapper.selectCount(new QueryWrapper<Signup>()
                .eq("activity_id", activityId).eq("status", "APPROVED"));
    }

    private void validateTime(LocalDateTime signupStart, LocalDateTime signupEnd,
                              LocalDateTime startTime, LocalDateTime endTime) {
        if (signupStart == null || signupEnd == null || startTime == null || endTime == null) {
            throw new BizException(ErrorCode.TIME_INVALID, "活动时间不能为空");
        }
        if (!signupStart.isBefore(signupEnd)) {
            throw new BizException(ErrorCode.TIME_INVALID, "报名开始时间必须早于报名截止时间");
        }
        if (signupEnd.isAfter(startTime)) {
            throw new BizException(ErrorCode.TIME_INVALID, "报名截止时间不能晚于活动开始时间");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BizException(ErrorCode.TIME_INVALID, "活动开始时间必须早于结束时间");
        }
    }

    private void validateCapacity(Integer capacity, int approvedCount) {
        if (capacity == null || capacity < 1) {
            throw new BizException(ErrorCode.CAPACITY_INVALID, "人数上限必须为正整数");
        }
        if (capacity < approvedCount) {
            throw new BizException(ErrorCode.CAPACITY_INVALID, "人数上限不能小于已通过人数");
        }
    }

    private void replaceTags(Long activityId, List<String> tags) {
        activityTagMapper.delete(new QueryWrapper<ActivityTag>().eq("activity_id", activityId));
        if (tags == null) {
            return;
        }
        Set<String> uniqueTags = new LinkedHashSet<String>();
        for (String tag : tags) {
            if (tag != null && !tag.trim().isEmpty()) {
                uniqueTags.add(tag.trim());
            }
        }
        for (String tag : uniqueTags) {
            ActivityTag entity = new ActivityTag();
            entity.setActivityId(activityId);
            entity.setTag(tag);
            activityTagMapper.insert(entity);
        }
    }

    private List<String> loadTags(Long activityId) {
        List<String> tags = new ArrayList<String>();
        if (activityId == null) {
            return tags;
        }
        for (ActivityTag entity : activityTagMapper.selectList(
                new QueryWrapper<ActivityTag>().eq("activity_id", activityId).orderByAsc("id"))) {
            tags.add(entity.getTag());
        }
        return tags;
    }

    private Map<Long, List<String>> loadTags(List<Activity> activities) {
        Map<Long, List<String>> result = new HashMap<Long, List<String>>();
        if (activities == null || activities.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<Long>();
        for (Activity activity : activities) {
            ids.add(activity.getId());
        }
        for (ActivityTag tag : activityTagMapper.selectList(
                new QueryWrapper<ActivityTag>().in("activity_id", ids).orderByAsc("id"))) {
            List<String> tags = result.get(tag.getActivityId());
            if (tags == null) {
                tags = new ArrayList<String>();
                result.put(tag.getActivityId(), tags);
            }
            tags.add(tag.getTag());
        }
        return result;
    }

    public ActivityVO toVo(Activity activity, String clubName, List<String> tags) {
        boolean signupOpen = STATUS_PUBLISHED.equals(activity.getStatus())
                && activity.getSignupStart() != null && activity.getSignupEnd() != null
                && !LocalDateTime.now().isBefore(activity.getSignupStart())
                && !LocalDateTime.now().isAfter(activity.getSignupEnd());
        return ActivityVO.builder()
                .id(activity.getId())
                .clubId(activity.getClubId())
                .clubName(clubName)
                .title(activity.getTitle())
                .summary(activity.getSummary())
                .location(activity.getLocation())
                .contact(activity.getContact())
                .tags(tags == null ? new ArrayList<String>() : tags)
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .signupStart(activity.getSignupStart())
                .signupEnd(activity.getSignupEnd())
                .capacity(activity.getCapacity())
                .signedCount(activity.getSignedCount())
                .status(activity.getStatus())
                .takedownReason(activity.getTakedownReason())
                .signupOpen(signupOpen)
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .build();
    }

    public String clubName(Long clubId) {
        Club club = clubId == null ? null : clubMapper.selectById(clubId);
        return club == null ? null : club.getName();
    }

    private Map<Long, String> loadClubNames(List<Activity> activities) {
        List<Long> clubIds = new ArrayList<Long>();
        for (Activity activity : activities) {
            if (activity.getClubId() != null && !clubIds.contains(activity.getClubId())) {
                clubIds.add(activity.getClubId());
            }
        }
        Map<Long, String> names = new HashMap<Long, String>();
        if (clubIds.isEmpty()) {
            return names;
        }
        for (Club club : clubMapper.selectBatchIds(clubIds)) {
            names.put(club.getId(), club.getName());
        }
        return names;
    }
}
