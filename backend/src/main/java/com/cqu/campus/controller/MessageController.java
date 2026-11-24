package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.service.MessageService;
import com.cqu.campus.domain.vo.MessageVO;
import com.cqu.campus.domain.vo.UnreadCountVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息接口
 */
@Validated
@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/api/messages")
    public ApiResponse<PageResult<MessageVO>> list(
            @RequestParam(value = "isRead", required = false) Boolean isRead,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(messageService.list(isRead, type, pageNum, pageSize));
    }

    @PutMapping("/api/messages/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable("id") Long id) {
        messageService.markRead(id);
        return ApiResponse.success();
    }

    @PutMapping("/api/messages/read-all")
    public ApiResponse<Integer> markAllRead() {
        return ApiResponse.success(messageService.markAllRead());
    }

    @GetMapping("/api/messages/unread-count")
    public ApiResponse<UnreadCountVO> unreadCount() {
        return ApiResponse.success(messageService.unreadCount());
    }
}
