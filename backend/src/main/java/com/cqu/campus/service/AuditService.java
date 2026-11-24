package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.common.TraceIdFilter;
import com.cqu.campus.domain.entity.AuditLog;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.vo.AuditLogVO;
import com.cqu.campus.mapper.AuditLogMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务：记录关键操作的操作者、对象、前后状态与 traceId
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final AuditLogMapper auditLogMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper, UserAccountMapper userAccountMapper,
                        ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.userAccountMapper = userAccountMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 审计日志分页查询（平台管理员）
     */
    public PageResult<AuditLogVO> list(String action, String resourceType, Long resourceId,
                                       Integer pageNum, Integer pageSize) {
        CurrentUser user = UserContext.require();
        if (!user.hasRole(ROLE_PLATFORM_ADMIN)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅平台管理员可查看审计日志");
        }
        QueryWrapper<AuditLog> wrapper = new QueryWrapper<AuditLog>();
        if (action != null && !action.trim().isEmpty()) {
            wrapper.eq("action", action.trim());
        }
        if (resourceType != null && !resourceType.trim().isEmpty()) {
            wrapper.eq("resource_type", resourceType.trim());
        }
        if (resourceId != null) {
            wrapper.eq("resource_id", resourceId);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<AuditLog> page = new Page<AuditLog>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<AuditLog> result = auditLogMapper.selectPage(page, wrapper);
        Map<Long, String> operatorNames = new HashMap<Long, String>();
        List<Long> operatorIds = new ArrayList<Long>();
        for (AuditLog item : result.getRecords()) {
            if (item.getOperatorId() != null && !operatorIds.contains(item.getOperatorId())) {
                operatorIds.add(item.getOperatorId());
            }
        }
        if (!operatorIds.isEmpty()) {
            for (UserAccount account : userAccountMapper.selectBatchIds(operatorIds)) {
                operatorNames.put(account.getId(), account.getRealName());
            }
        }
        List<AuditLogVO> records = new ArrayList<AuditLogVO>();
        for (AuditLog item : result.getRecords()) {
            records.add(AuditLogVO.builder()
                    .id(item.getId())
                    .traceId(item.getTraceId())
                    .operatorId(item.getOperatorId())
                    .operatorName(operatorNames.get(item.getOperatorId()))
                    .resourceType(item.getResourceType())
                    .resourceId(item.getResourceId())
                    .action(item.getAction())
                    .beforeJson(item.getBeforeJson())
                    .afterJson(item.getAfterJson())
                    .result(item.getResultMark())
                    .createdAt(item.getCreatedAt())
                    .build());
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    public void record(String action, String resourceType, Long resourceId, Object before, Object after, String result) {
        try {
            AuditLog entity = new AuditLog();
            entity.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
            CurrentUser user = UserContext.get();
            entity.setOperatorId(user == null ? null : user.getUserId());
            entity.setAction(action);
            entity.setResourceType(resourceType);
            entity.setResourceId(resourceId);
            entity.setBeforeJson(toJson(before));
            entity.setAfterJson(toJson(after));
            entity.setResultMark(result);
            entity.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            // 审计失败不静默：记录错误日志，业务结果仍以主事实为准
            log.error("写入审计日志失败 action={} resourceType={} resourceId={}", action, resourceType, resourceId, e);
        }
    }

    public void record(String action, String resourceType, Long resourceId, String result) {
        record(action, resourceType, resourceId, null, null, result);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("审计快照序列化失败", e);
            return null;
        }
    }
}
