package com.livingagent.gateway.controller;

import com.livingagent.core.database.entity.MessageEntity;
import com.livingagent.core.database.entity.MessageMark;
import com.livingagent.core.database.entity.UserContact;
import com.livingagent.core.database.repository.MessageRepository;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.MessageOperationService;
import com.livingagent.gateway.service.UserContactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * IM 即时通讯 API
 *
 * 路径: /api/im (与现有 /api/messages 站内信区分)
 */
@RestController
@RequestMapping("/api/im")
public class MessageApiController {

    private static final Logger log = LoggerFactory.getLogger(MessageApiController.class);

    private final UserContactService userContactService;
    private final MessageOperationService messageOperationService;
    private final MessageRepository messageRepository;

    public MessageApiController(UserContactService userContactService,
                                MessageOperationService messageOperationService,
                                MessageRepository messageRepository) {
        this.userContactService = userContactService;
        this.messageOperationService = messageOperationService;
        this.messageRepository = messageRepository;
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<List<ContactInfo>>> getContacts(
            @RequestParam(defaultValue = "false") boolean includeHidden,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }
        List<UserContact> contacts = userContactService.getContactList(employeeId, includeHidden);
        List<ContactInfo> result = contacts.stream().map(this::toContactInfo).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 设置免打扰
     */
    @PutMapping("/contacts/{contactId}/muted")
    public ResponseEntity<ApiResponse<ContactInfo>> setMuted(
            @PathVariable String contactId,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }
        Boolean muted = body.get("muted");
        if (muted == null) {
            return ResponseEntity.badRequest().body(ApiResponse.err("bad_request", "缺少 muted 参数"));
        }
        UserContact contact = userContactService.setMuted(employeeId, contactId, muted);
        if (contact == null) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "联系人不存在"));
        }
        return ResponseEntity.ok(ApiResponse.ok(toContactInfo(contact)));
    }

    /**
     * 设置置顶
     */
    @PutMapping("/contacts/{contactId}/pinned")
    public ResponseEntity<ApiResponse<ContactInfo>> setPinned(
            @PathVariable String contactId,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }
        Boolean pinned = body.get("pinned");
        if (pinned == null) {
            return ResponseEntity.badRequest().body(ApiResponse.err("bad_request", "缺少 pinned 参数"));
        }
        UserContact contact = userContactService.setPinned(employeeId, contactId, pinned);
        if (contact == null) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "联系人不存在"));
        }
        return ResponseEntity.ok(ApiResponse.ok(toContactInfo(contact)));
    }

    /**
     * 设置隐藏
     */
    @PutMapping("/contacts/{contactId}/hidden")
    public ResponseEntity<ApiResponse<ContactInfo>> setHidden(
            @PathVariable String contactId,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }
        Boolean hidden = body.get("hidden");
        if (hidden == null) {
            return ResponseEntity.badRequest().body(ApiResponse.err("bad_request", "缺少 hidden 参数"));
        }
        UserContact contact = userContactService.setHidden(employeeId, contactId, hidden);
        if (contact == null) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "联系人不存在"));
        }
        return ResponseEntity.ok(ApiResponse.ok(toContactInfo(contact)));
    }

    /**
     * 获取聊天消息历史
     */
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<MessageInfo>>> getChatMessages(
            @RequestParam String contactId,
            @RequestParam(required = false) Instant before,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }

        List<MessageEntity> messages = messageRepository.findChatMessages(employeeId, contactId);

        // before 过滤
        if (before != null) {
            messages = messages.stream()
                .filter(m -> m.getCreatedAt().isBefore(before))
                .toList();
        }

        // limit
        if (messages.size() > limit) {
            messages = messages.subList(0, limit);
        }

        List<MessageInfo> result = messages.stream().map(this::toMessageInfo).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 撤回消息
     */
    @PostMapping("/messages/{messageId}/recall")
    public ResponseEntity<ApiResponse<Void>> recallMessage(
            @PathVariable String messageId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }
        try {
            messageOperationService.recallMessage(messageId, employeeId);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(ApiResponse.err("conflict", e.getMessage()));
        }
    }

    /**
     * 标记消息
     */
    @PostMapping("/messages/{messageId}/mark")
    public ResponseEntity<ApiResponse<MarkInfo>> markMessage(
            @PathVariable String messageId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }
        String markType = body.get("markType");
        if (markType == null || markType.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.err("bad_request", "缺少 markType 参数"));
        }

        // 校验 markType 合法值
        if (!List.of("LIKE", "REPORT", "IMPORTANT").contains(markType)) {
            return ResponseEntity.badRequest().body(ApiResponse.err("bad_request", "无效的 markType: " + markType));
        }

        try {
            MessageMark mark = messageOperationService.markMessage(messageId, employeeId, markType);
            return ResponseEntity.ok(ApiResponse.ok(toMarkInfo(mark)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.err("internal_error", e.getMessage()));
        }
    }

    /**
     * 总未读数
     */
    @GetMapping("/contacts/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> getUnreadCount(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "未认证"));
        }
        long count = userContactService.getTotalUnreadCount(employeeId);
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCount((int) count)));
    }

    // ---- DTO ----

    public record ContactInfo(
        String contactId,
        String contactType,
        String roomId,
        boolean muted,
        boolean pinned,
        boolean hidden,
        boolean shield,
        String lastMessageId,
        String lastMessageContent,
        Instant lastMessageTime,
        int unreadCount
    ) {}

    public record MessageInfo(
        String messageId,
        String senderId,
        String recipientId,
        String type,
        String content,
        String replyToId,
        Integer gapCount,
        Instant createdAt,
        boolean recalled
    ) {}

    public record MarkInfo(
        String messageId,
        String userId,
        String markType,
        String status,
        Instant createdAt
    ) {}

    public record UnreadCount(int unread_count) {}

    // ---- 转换方法 ----

    private ContactInfo toContactInfo(UserContact contact) {
        return new ContactInfo(
            contact.getContactId(),
            contact.getContactType(),
            contact.getRoomId(),
            Boolean.TRUE.equals(contact.getMuted()),
            Boolean.TRUE.equals(contact.getPinned()),
            Boolean.TRUE.equals(contact.getHidden()),
            Boolean.TRUE.equals(contact.getShield()),
            contact.getLastMessageId(),
            contact.getLastMessageContent(),
            contact.getLastMessageTime(),
            contact.getUnreadCount() != null ? contact.getUnreadCount() : 0
        );
    }

    private MessageInfo toMessageInfo(MessageEntity entity) {
        return new MessageInfo(
            entity.getMessageId(),
            entity.getSenderId(),
            entity.getRecipientId(),
            entity.getType(),
            entity.getContent(),
            entity.getReplyToId(),
            entity.getGapCount(),
            entity.getCreatedAt(),
            entity.isRecalled()
        );
    }

    private MarkInfo toMarkInfo(MessageMark mark) {
        return new MarkInfo(
            mark.getMessageId(),
            mark.getUserId(),
            mark.getMarkType(),
            mark.getStatus(),
            mark.getCreatedAt()
        );
    }
}
