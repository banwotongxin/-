package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.domain.entity.Message;
import com.cqu.campus.domain.entity.Notification;
import com.cqu.campus.mapper.MessageMapper;
import com.cqu.campus.mapper.NotificationMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import com.cqu.campus.domain.vo.MessageVO;
import com.cqu.campus.domain.vo.UnreadCountVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 站内消息服务：按接收人查询、标记已读、未读计数、业务事件幂等通知
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private static final String TYPE_SIGNUP_PENDING = "SIGNUP_PENDING";
    private static final String TYPE_SIGNUP_RESULT = "SIGNUP_RESULT";
    private static final String TYPE_ACTIVITY_CHANGE = "ACTIVITY_CHANGE";
    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
            TYPE_SIGNUP_PENDING, TYPE_SIGNUP_RESULT, TYPE_ACTIVITY_CHANGE, "SIGNIN_RESULT", "CLUB_APPLICATION");
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final DateTimeFormatter EVENT_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MessageMapper messageMapper;
    private final NotificationMapper notificationMapper;

    public MessageService(MessageMapper messageMapper, NotificationMapper notificationMapper) {
        this.messageMapper = messageMapper;
        this.notificationMapper = notificationMapper;
    }

    public PageResult<MessageVO> list(Boolean isRead, String type, Integer pageNum, Integer pageSize) {
        CurrentUser user = UserContext.require();
        QueryWrapper<Message> wrapper = new QueryWrapper<Message>();
        wrapper.eq("recipient_id", user.getUserId());
        if (isRead != null) {
            wrapper.eq("is_read", isRead);
        }
        String targetType = normalizeType(type);
        if (targetType != null) {
            wrapper.eq("msg_type", targetType);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<Message> page = new Page<Message>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<Message> result = messageMapper.selectPage(page, wrapper);
        List<MessageVO> records = new ArrayList<MessageVO>();
        for (Message message : result.getRecords()) {
            records.add(toVo(message));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 消息类型筛选：空值表示全部，非法值拒绝
     */
    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        String value = type.trim().toUpperCase();
        if (!SUPPORTED_TYPES.contains(value)) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "不支持的消息类型");
        }
        return value;
    }

    public void markRead(Long id) {
        CurrentUser user = UserContext.require();
        Message message = messageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息不存在");
        }
        if (!user.getUserId().equals(message.getRecipientId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "只能操作本人消息");
        }
        if (Boolean.TRUE.equals(message.getIsRead())) {
            return;
        }
        UpdateWrapper<Message> wrapper = new UpdateWrapper<Message>();
        wrapper.eq("id", id).eq("recipient_id", user.getUserId()).set("is_read", true);
        messageMapper.update(null, wrapper);
    }

    public UnreadCountVO unreadCount() {
        CurrentUser user = UserContext.require();
        Long count = messageMapper.selectCount(new QueryWrapper<Message>()
                .eq("recipient_id", user.getUserId())
                .eq("is_read", false));
        return new UnreadCountVO(count == null ? 0L : count);
    }

    /**
     * 全部标记已读：仅更新本人未读消息，返回影响条数
     */
    public int markAllRead() {
        CurrentUser user = UserContext.require();
        UpdateWrapper<Message> wrapper = new UpdateWrapper<Message>();
        wrapper.eq("recipient_id", user.getUserId())
                .eq("is_read", false)
                .set("is_read", true);
        return messageMapper.update(null, wrapper);
    }

    /**
     * 提交报名后的待审核通知：event_key = "signup:{signupId}:created:{round}"
     * round 为本次报名时间，保证撤销后重新报名仍能收到通知
     */
    public void sendSignupCreated(Long recipientId, Long signupId, String activityTitle, LocalDateTime round) {
        send(recipientId, TYPE_SIGNUP_PENDING, "报名已提交，等待审核",
                "您已提交活动《" + activityTitle + "》的报名，请等待社团管理员审核。",
                "signup:" + signupId + ":created" + roundSuffix(round));
    }

    /**
     * 报名审核结果通知：event_key = "signup:{signupId}:{decision}:{round}"
     */
    public void sendSignupResult(Long recipientId, Long signupId, String decision, String activityTitle,
                                 String reason, LocalDateTime round) {
        boolean approved = "APPROVED".equals(decision);
        String content = "活动《" + activityTitle + "》的报名" + (approved
                ? "已通过审核，请按时参加。"
                : "被驳回，原因：" + (reason == null ? "未提供" : reason));
        send(recipientId, TYPE_SIGNUP_RESULT, approved ? "报名审核通过" : "报名审核未通过", content,
                "signup:" + signupId + ":" + decision + roundSuffix(round));
    }

    private String roundSuffix(LocalDateTime round) {
        return round == null ? "" : ":" + round.format(EVENT_KEY_FORMATTER);
    }

    /**
     * 活动变更通知：event_key = "activity:{activityId}:{changeKey}:{recipientId}"
     */
    public void sendActivityChange(Long recipientId, Long activityId, String changeKey, String title, String content) {
        send(recipientId, TYPE_ACTIVITY_CHANGE, title, content,
                "activity:" + activityId + ":" + changeKey + ":" + recipientId);
    }

    /**
     * 发送站内消息；eventKey 非空时按投递幂等表去重，重复键吸收
     */
    public void send(Long recipientId, String type, String title, String content, String eventKey) {
        Notification notification = null;
        if (eventKey != null) {
            notification = notificationMapper.selectOne(new QueryWrapper<Notification>().eq("event_key", eventKey));
            if (notification != null && STATUS_SENT.equals(notification.getStatus())) {
                return;
            }
            if (notification == null) {
                notification = new Notification();
                notification.setEventKey(eventKey);
                notification.setRecipientId(recipientId);
                notification.setChannel("IN_APP");
                notification.setStatus(STATUS_PENDING);
                notification.setCreatedAt(LocalDateTime.now());
                try {
                    notificationMapper.insert(notification);
                } catch (DuplicateKeyException e) {
                    notification = notificationMapper.selectOne(
                            new QueryWrapper<Notification>().eq("event_key", eventKey));
                    if (notification != null && STATUS_SENT.equals(notification.getStatus())) {
                        return;
                    }
                }
            }
        }
        try {
            Message message = new Message();
            message.setRecipientId(recipientId);
            message.setMsgType(type);
            message.setTitle(title);
            message.setContent(content);
            message.setIsRead(false);
            message.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(message);
            if (notification != null) {
                updateNotificationStatus(notification.getId(), STATUS_SENT);
            }
        } catch (RuntimeException e) {
            // 投递失败标记 FAILED，保留幂等记录，业务事件重放时可再次投递
            if (notification != null) {
                updateNotificationStatus(notification.getId(), STATUS_FAILED);
            }
            log.error("站内消息投递失败 eventKey={} recipientId={}", eventKey, recipientId, e);
        }
    }

    private void updateNotificationStatus(Long id, String status) {
        UpdateWrapper<Notification> wrapper = new UpdateWrapper<Notification>();
        wrapper.eq("id", id).set("status", status);
        notificationMapper.update(null, wrapper);
    }

    private MessageVO toVo(Message message) {
        return MessageVO.builder()
                .id(message.getId())
                .type(message.getMsgType())
                .title(message.getTitle())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
