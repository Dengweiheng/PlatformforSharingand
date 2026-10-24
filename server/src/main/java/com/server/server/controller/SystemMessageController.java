package com.server.server.controller;

import com.server.server.common.ApiResponse;
import com.server.server.dto.MessageCreateRequest;
import com.server.server.entity.SystemMessage;
import com.server.server.service.SystemMessageService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class SystemMessageController {

    private final SystemMessageService systemMessageService;

    public SystemMessageController(SystemMessageService systemMessageService) {
        this.systemMessageService = systemMessageService;
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<SystemMessage>> listByUser(@PathVariable Long userId) {
        return ApiResponse.success(systemMessageService.listByUserId(userId));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Boolean> markRead(@PathVariable Long id) {
        boolean ok = systemMessageService.markRead(id);
        if (!ok) {
            return ApiResponse.error("消息不存在");
        }
        return ApiResponse.success("已标记已读", true);
    }

    @PostMapping
    public ApiResponse<Boolean> create(@RequestBody MessageCreateRequest request) {
        if (request == null || request.getUserId() == null || !StringUtils.hasText(request.getMsgType())
                || !StringUtils.hasText(request.getTitle()) || !StringUtils.hasText(request.getContent())) {
            return ApiResponse.error("参数不完整");
        }

        // 统一消息入口：可用于审核结果、推荐更新、评论回复三类通知。
        SystemMessage message = new SystemMessage();
        message.setUserId(request.getUserId());
        message.setMsgType(request.getMsgType());
        message.setTitle(request.getTitle());
        message.setContent(request.getContent());
        message.setIsRead(0);
        boolean ok = systemMessageService.createMessage(message);
        if (!ok) {
            return ApiResponse.error("消息发送失败");
        }
        return ApiResponse.success("消息发送成功", true);
    }
}
