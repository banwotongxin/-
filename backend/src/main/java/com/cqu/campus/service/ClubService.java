package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.entity.Club;
import com.cqu.campus.domain.entity.ClubApplication;
import com.cqu.campus.domain.entity.ClubMember;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.dto.ClubApplicationReviewRequest;
import com.cqu.campus.domain.dto.ClubCreateRequest;
import com.cqu.campus.domain.dto.ClubFreezeRequest;
import com.cqu.campus.domain.dto.ClubUpdateRequest;
import com.cqu.campus.mapper.ClubApplicationMapper;
import com.cqu.campus.mapper.ClubMapper;
import com.cqu.campus.mapper.ClubMemberMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import com.cqu.campus.domain.vo.ClubApplicationVO;
import com.cqu.campus.domain.vo.ClubVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社团服务：入驻申请、资料维护、冻结、入驻审核与归属校验
 */
@Service
public class ClubService {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ROLE_CLUB_ADMIN = "CLUB_ADMIN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String MEMBER_OWNER = "OWNER";
    public static final String MEMBER_ACTIVE = "ACTIVE";

    private final ClubMapper clubMapper;
    private final ClubApplicationMapper clubApplicationMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final UserAccountMapper userAccountMapper;
    private final AuthService authService;
    private final MessageService messageService;
    private final AuditService auditService;

    public ClubService(ClubMapper clubMapper, ClubApplicationMapper clubApplicationMapper,
                       ClubMemberMapper clubMemberMapper, UserAccountMapper userAccountMapper,
                       AuthService authService, MessageService messageService, AuditService auditService) {
        this.clubMapper = clubMapper;
        this.clubApplicationMapper = clubApplicationMapper;
        this.clubMemberMapper = clubMemberMapper;
        this.userAccountMapper = userAccountMapper;
        this.authService = authService;
        this.messageService = messageService;
        this.auditService = auditService;
    }

    public PageResult<ClubVO> list(String keyword, String status, Integer pageNum, Integer pageSize) {
        QueryWrapper<Club> wrapper = new QueryWrapper<Club>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String value = keyword.trim();
            wrapper.and(w -> w.like("name", value).or().like("intro", value));
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (!UserContext.hasRole(ROLE_PLATFORM_ADMIN)) {
            wrapper.notIn("status", STATUS_PENDING, STATUS_REJECTED);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<Club> page = new Page<Club>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<Club> result = clubMapper.selectPage(page, wrapper);
        Map<Long, String> ownerNames = loadUserNames(result.getRecords());
        List<ClubVO> records = new ArrayList<ClubVO>();
        for (Club club : result.getRecords()) {
            records.add(toVo(club, ownerNames.get(club.getOwnerUserId())));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 社团详情：非平台管理员不可见待审核/已驳回社团
     */
    public ClubVO detail(Long id) {
        Club club = requireClub(id);
        if (!UserContext.hasRole(ROLE_PLATFORM_ADMIN)
                && (STATUS_PENDING.equals(club.getStatus()) || STATUS_REJECTED.equals(club.getStatus()))) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "社团不存在");
        }
        UserAccount owner = club.getOwnerUserId() == null ? null : userAccountMapper.selectById(club.getOwnerUserId());
        return toVo(club, owner == null ? null : owner.getRealName());
    }

    /**
     * 提交入驻申请（学生）
     */
    public ClubApplicationVO apply(ClubCreateRequest request) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(ROLE_STUDENT)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅学生可提交社团入驻申请");
        }
        UserAccount applicant = requireActiveAccount(user.getUserId(), "账号已冻结或停用，无法提交入驻申请");
        String clubName = request.getName().trim();
        if (clubMapper.selectCount(new QueryWrapper<Club>().eq("name", clubName)) > 0) {
            throw new BizException(ErrorCode.CLUB_EXISTS, "社团名称已存在");
        }
        if (clubApplicationMapper.selectCount(new QueryWrapper<ClubApplication>()
                .eq("club_name", clubName).eq("status", STATUS_PENDING)) > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该社团名称已有待审核申请");
        }
        ClubApplication application = new ClubApplication();
        application.setApplicantId(user.getUserId());
        application.setClubName(clubName);
        application.setContent(request.getIntro());
        application.setContact(request.getContact());
        application.setStatus(STATUS_PENDING);
        application.setCreatedAt(LocalDateTime.now());
        clubApplicationMapper.insert(application);

        Map<String, Object> after = new HashMap<String, Object>();
        after.put("clubName", clubName);
        after.put("contact", request.getContact());
        after.put("status", STATUS_PENDING);
        auditService.record("APPLY_CLUB", "CLUB_APPLICATION", application.getId(), null, after, "SUCCESS");
        return toVo(application, applicant.getRealName());
    }

    /**
     * 入驻申请列表（平台管理员）
     */
    public PageResult<ClubApplicationVO> listApplications(String status, Integer pageNum, Integer pageSize) {
        requirePlatformAdmin();
        QueryWrapper<ClubApplication> wrapper = new QueryWrapper<ClubApplication>();
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<ClubApplication> page = new Page<ClubApplication>(
                PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<ClubApplication> result = clubApplicationMapper.selectPage(page, wrapper);
        Map<Long, String> applicantNames = new HashMap<Long, String>();
        List<Long> applicantIds = new ArrayList<Long>();
        for (ClubApplication application : result.getRecords()) {
            if (application.getApplicantId() != null && !applicantIds.contains(application.getApplicantId())) {
                applicantIds.add(application.getApplicantId());
            }
        }
        if (!applicantIds.isEmpty()) {
            for (UserAccount account : userAccountMapper.selectBatchIds(applicantIds)) {
                applicantNames.put(account.getId(), account.getRealName());
            }
        }
        List<ClubApplicationVO> records = new ArrayList<ClubApplicationVO>();
        for (ClubApplication application : result.getRecords()) {
            records.add(toVo(application, applicantNames.get(application.getApplicantId())));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 当前用户提交的入驻申请
     */
    public PageResult<ClubApplicationVO> myApplications(String status, Integer pageNum, Integer pageSize) {
        CurrentUser user = UserContext.require();
        QueryWrapper<ClubApplication> wrapper = new QueryWrapper<ClubApplication>()
                .eq("applicant_id", user.getUserId());
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<ClubApplication> page = new Page<ClubApplication>(
                PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<ClubApplication> result = clubApplicationMapper.selectPage(page, wrapper);
        UserAccount applicant = userAccountMapper.selectById(user.getUserId());
        String applicantName = applicant == null ? null : applicant.getRealName();
        List<ClubApplicationVO> records = new ArrayList<ClubApplicationVO>();
        for (ClubApplication application : result.getRecords()) {
            records.add(toVo(application, applicantName));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 维护社团资料（所属社团管理员或平台管理员）
     */
    public ClubVO update(Long id, ClubUpdateRequest request) {
        Club club = requireManage(id);
        if (!STATUS_ACTIVE.equals(club.getStatus())) {
            throw new BizException(ErrorCode.CLUB_FROZEN, "社团已冻结，请先解冻后再维护资料");
        }
        UpdateWrapper<Club> wrapper = new UpdateWrapper<Club>();
        wrapper.eq("id", id);
        if (request.getName() != null && !request.getName().trim().isEmpty() && !request.getName().trim().equals(club.getName())) {
            String clubName = request.getName().trim();
            if (clubMapper.selectCount(new QueryWrapper<Club>().eq("name", clubName).ne("id", id)) > 0) {
                throw new BizException(ErrorCode.CLUB_EXISTS, "社团名称已存在");
            }
            wrapper.set("name", clubName);
        }
        if (request.getIntro() != null) {
            wrapper.set("intro", request.getIntro());
        }
        if (request.getContact() != null) {
            wrapper.set("contact", request.getContact());
        }
        wrapper.set("updated_at", LocalDateTime.now());
        clubMapper.update(null, wrapper);
        Club updated = clubMapper.selectById(id);
        auditService.record("UPDATE_CLUB", "CLUB", id, clubSnapshot(club), clubSnapshot(updated), "SUCCESS");
        UserAccount owner = updated.getOwnerUserId() == null ? null : userAccountMapper.selectById(updated.getOwnerUserId());
        return toVo(updated, owner == null ? null : owner.getRealName());
    }

    /**
     * 冻结社团（平台管理员）
     */
    public ClubVO freeze(Long id, ClubFreezeRequest request) {
        requirePlatformAdmin();
        Club club = requireClub(id);
        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "请填写冻结原因");
        }
        UpdateWrapper<Club> wrapper = new UpdateWrapper<Club>();
        wrapper.eq("id", id).eq("status", STATUS_ACTIVE)
                .set("status", STATUS_FROZEN)
                .set("updated_at", LocalDateTime.now());
        int rows = clubMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅正常状态社团可冻结");
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", club.getStatus());
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_FROZEN);
        after.put("reason", reason);
        auditService.record("FREEZE_CLUB", "CLUB", id, before, after, "SUCCESS");
        Club updated = clubMapper.selectById(id);
        UserAccount owner = updated.getOwnerUserId() == null ? null : userAccountMapper.selectById(updated.getOwnerUserId());
        return toVo(updated, owner == null ? null : owner.getRealName());
    }

    /**
     * 解冻社团（平台管理员）
     */
    public ClubVO unfreeze(Long id) {
        requirePlatformAdmin();
        Club club = requireClub(id);
        UpdateWrapper<Club> wrapper = new UpdateWrapper<Club>();
        wrapper.eq("id", id).eq("status", STATUS_FROZEN)
                .set("status", STATUS_ACTIVE)
                .set("updated_at", LocalDateTime.now());
        int rows = clubMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅冻结状态社团可解冻");
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", STATUS_FROZEN);
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", STATUS_ACTIVE);
        auditService.record("UNFREEZE_CLUB", "CLUB", id, before, after, "SUCCESS");
        Club updated = clubMapper.selectById(id);
        UserAccount owner = updated.getOwnerUserId() == null ? null : userAccountMapper.selectById(updated.getOwnerUserId());
        return toVo(updated, owner == null ? null : owner.getRealName());
    }

    /**
     * 审核入驻申请（平台管理员）：通过时创建社团并建立负责人关系
     */
    @Transactional
    public ClubApplicationVO reviewApplication(Long id, ClubApplicationReviewRequest request) {
        requirePlatformAdmin();
        ClubApplication application = clubApplicationMapper.selectById(id);
        if (application == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "申请不存在");
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该申请已被处理");
        }
        boolean approved = "APPROVED".equals(request.getDecision());
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (!approved && (reason == null || reason.isEmpty())) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "请填写驳回原因");
        }
        if (approved) {
            requireActiveAccount(application.getApplicantId(), "申请人账号不可用，无法通过入驻申请");
        }
        // 先以条件更新抢占申请状态，保证并发审批仅首个决定生效
        UpdateWrapper<ClubApplication> wrapper = new UpdateWrapper<ClubApplication>();
        wrapper.eq("id", id).eq("status", STATUS_PENDING)
                .set("status", approved ? "APPROVED" : STATUS_REJECTED)
                .set("reviewer_id", UserContext.get().getUserId())
                .set("review_reason", reason)
                .set("reviewed_at", LocalDateTime.now());
        int rows = clubApplicationMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "申请状态已被他人变更");
        }
        if (approved) {
            applyApproved(application);
        }
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("status", STATUS_PENDING);
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("status", approved ? "APPROVED" : STATUS_REJECTED);
        after.put("reason", reason);
        auditService.record("REVIEW_CLUB_APPLICATION", "CLUB_APPLICATION", id, before, after, "SUCCESS");
        final Long applicationId = id;
        final String clubName = application.getClubName();
        final Long applicantId = application.getApplicantId();
        final boolean resultApproved = approved;
        final String rejectReason = reason;
        runAfterCommit(new Runnable() {
            @Override
            public void run() {
                messageService.send(applicantId, "CLUB_APPLICATION",
                        resultApproved ? "社团入驻申请已通过" : "社团入驻申请未通过",
                        resultApproved ? "社团《" + clubName + "》入驻申请已通过，您已成为社团负责人。"
                                : "社团《" + clubName + "》入驻申请未通过，原因：" + rejectReason,
                        "club-application:" + applicationId + ":" + (resultApproved ? "APPROVED" : "REJECTED"));
            }
        });
        return toVo(clubApplicationMapper.selectById(id), null);
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

    private void applyApproved(ClubApplication application) {
        if (clubMapper.selectCount(new QueryWrapper<Club>().eq("name", application.getClubName())) > 0) {
            throw new BizException(ErrorCode.CLUB_EXISTS, "社团名称已存在，无法通过申请");
        }
        Club club = new Club();
        club.setName(application.getClubName());
        club.setIntro(application.getContent());
        club.setContact(application.getContact());
        club.setOwnerUserId(application.getApplicantId());
        club.setStatus(STATUS_ACTIVE);
        club.setCreatedAt(LocalDateTime.now());
        club.setUpdatedAt(LocalDateTime.now());
        clubMapper.insert(club);

        ClubMember member = new ClubMember();
        member.setClubId(club.getId());
        member.setUserId(application.getApplicantId());
        member.setMemberRole(MEMBER_OWNER);
        member.setStatus(MEMBER_ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        clubMemberMapper.insert(member);

        authService.grantRole(application.getApplicantId(), ROLE_CLUB_ADMIN);
    }

    public Club requireClub(Long clubId) {
        if (clubId == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "社团不存在");
        }
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "社团不存在");
        }
        return club;
    }

    /**
     * 校验对社团的管理权限：平台管理员放行，社团管理员需为负责人
     */
    public Club requireManage(Long clubId) {
        Club club = requireClub(clubId);
        if (!canManage(clubId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "只能操作本人负责的社团");
        }
        return club;
    }

    /**
     * 当前用户是否为社团成员（含负责人）
     */
    public boolean isMember(Long clubId) {
        CurrentUser user = UserContext.get();
        if (user == null || clubId == null) {
            return false;
        }
        Long count = clubMemberMapper.selectCount(new QueryWrapper<ClubMember>()
                .eq("club_id", clubId)
                .eq("user_id", user.getUserId())
                .eq("status", MEMBER_ACTIVE));
        return count != null && count > 0;
    }

    public boolean canManage(Long clubId) {
        CurrentUser user = UserContext.get();
        if (user == null) {
            return false;
        }
        if (user.hasRole(ROLE_PLATFORM_ADMIN)) {
            return true;
        }
        Club club = clubMapper.selectById(clubId);
        return club != null && isOwner(club, user.getUserId());
    }

    private boolean isOwner(Club club, Long userId) {
        if (club.getOwnerUserId() != null && club.getOwnerUserId().equals(userId)) {
            return true;
        }
        Long count = clubMemberMapper.selectCount(new QueryWrapper<ClubMember>()
                .eq("club_id", club.getId())
                .eq("user_id", userId)
                .eq("member_role", MEMBER_OWNER)
                .eq("status", MEMBER_ACTIVE));
        return count != null && count > 0;
    }

    public void requirePlatformAdmin() {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(ROLE_PLATFORM_ADMIN)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅平台管理员可执行该操作");
        }
    }

    /**
     * 当前用户可管理的社团：平台管理员为全部，社团管理员为本人负责的社团
     */
    public List<Club> listManagedClubs() {
        CurrentUser user = UserContext.require();
        if (user.hasRole(ROLE_PLATFORM_ADMIN)) {
            return clubMapper.selectList(new QueryWrapper<Club>().orderByAsc("id"));
        }
        if (!user.hasRole(ROLE_CLUB_ADMIN)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅管理员可查看统计");
        }
        List<Long> clubIds = ownedClubIds(user.getUserId());
        if (clubIds.isEmpty()) {
            return Collections.emptyList();
        }
        return clubMapper.selectList(new QueryWrapper<Club>().in("id", clubIds).orderByAsc("id"));
    }

    /**
     * 当前用户可管理的社团列表（含冻结），供后台下拉选择
     */
    public List<ClubVO> listManaged() {
        List<Club> clubs = listManagedClubs();
        Map<Long, String> ownerNames = loadUserNames(clubs);
        List<ClubVO> records = new ArrayList<ClubVO>();
        for (Club club : clubs) {
            records.add(toVo(club, ownerNames.get(club.getOwnerUserId())));
        }
        return records;
    }

    /**
     * 当前用户负责的社团 ID 集合
     */
    public List<Long> currentManagedClubIds() {
        CurrentUser user = UserContext.require();
        if (user.hasRole(ROLE_PLATFORM_ADMIN)) {
            List<Club> clubs = clubMapper.selectList(null);
            List<Long> ids = new ArrayList<Long>();
            for (Club club : clubs) {
                ids.add(club.getId());
            }
            return ids;
        }
        return ownedClubIds(user.getUserId());
    }

    private List<Long> ownedClubIds(Long userId) {
        List<Long> ids = new ArrayList<Long>();
        for (Club club : clubMapper.selectList(new QueryWrapper<Club>().eq("owner_user_id", userId))) {
            ids.add(club.getId());
        }
        List<ClubMember> members = clubMemberMapper.selectList(new QueryWrapper<ClubMember>()
                .eq("user_id", userId).eq("member_role", MEMBER_OWNER).eq("status", MEMBER_ACTIVE));
        Set<Long> unique = new LinkedHashSet<Long>(ids);
        for (ClubMember member : members) {
            unique.add(member.getClubId());
        }
        return new ArrayList<Long>(unique);
    }

    private UserAccount requireActiveAccount(Long userId, String message) {
        UserAccount account = userId == null ? null : userAccountMapper.selectById(userId);
        if (account == null || !STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED, message);
        }
        return account;
    }

    private Map<String, Object> clubSnapshot(Club club) {
        Map<String, Object> snapshot = new HashMap<String, Object>();
        if (club == null) {
            return snapshot;
        }
        snapshot.put("name", club.getName());
        snapshot.put("contact", club.getContact());
        snapshot.put("status", club.getStatus());
        snapshot.put("ownerUserId", club.getOwnerUserId());
        return snapshot;
    }

    private Map<Long, String> loadUserNames(List<Club> clubs) {
        List<Long> ownerIds = new ArrayList<Long>();
        for (Club club : clubs) {
            if (club.getOwnerUserId() != null && !ownerIds.contains(club.getOwnerUserId())) {
                ownerIds.add(club.getOwnerUserId());
            }
        }
        Map<Long, String> names = new HashMap<Long, String>();
        if (ownerIds.isEmpty()) {
            return names;
        }
        for (UserAccount account : userAccountMapper.selectBatchIds(ownerIds)) {
            names.put(account.getId(), account.getRealName());
        }
        return names;
    }

    private ClubVO toVo(Club club, String ownerName) {
        return ClubVO.builder()
                .id(club.getId())
                .name(club.getName())
                .intro(club.getIntro())
                .ownerUserId(club.getOwnerUserId())
                .ownerName(ownerName)
                .contact(club.getContact())
                .status(club.getStatus())
                .createdAt(club.getCreatedAt())
                .updatedAt(club.getUpdatedAt())
                .build();
    }

    private ClubApplicationVO toVo(ClubApplication application, String applicantName) {
        return ClubApplicationVO.builder()
                .id(application.getId())
                .applicantId(application.getApplicantId())
                .applicantName(applicantName)
                .clubName(application.getClubName())
                .content(application.getContent())
                .contact(application.getContact())
                .status(application.getStatus())
                .reviewReason(application.getReviewReason())
                .createdAt(application.getCreatedAt())
                .reviewedAt(application.getReviewedAt())
                .build();
    }
}
