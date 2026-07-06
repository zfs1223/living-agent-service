package com.livingagent.gateway.controller;

import com.livingagent.core.database.entity.MessageEntity;
import com.livingagent.core.database.repository.MessageRepository;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);
    private final MessageRepository messageRepository;

    public MessageController(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<MessageInfo>>> getInbox(
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        List<MessageEntity> messages = messageRepository.findByRecipientIdOrderByCreatedAtDesc(employeeId);
        if (messages.size() > limit) {
            messages = messages.subList(0, limit);
        }
        List<MessageInfo> result = messages.stream().map(this::toMessageInfo).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> getUnreadCount(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(new UnreadCount(0)));
        }
        long count = messageRepository.countByRecipientIdAndReadAtIsNull(employeeId);
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCount((int) count)));
    }

    @PutMapping("/{messageId}/read")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String messageId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }
        var message = messageRepository.findByMessageId(messageId);
        if (message.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Message not found: " + messageId));
        }
        MessageEntity entity = message.get();
        if (!entity.getRecipientId().equals(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Not your message"));
        }
        if (entity.getReadAt() == null) {
            entity.setReadAt(Instant.now());
            messageRepository.save(entity);
        }
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PutMapping("/read-all")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }
        int updated = messageRepository.markAllAsReadByRecipientId(employeeId, Instant.now());
        log.info("Marked {} messages as read for employee: {}", updated, employeeId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private MessageInfo toMessageInfo(MessageEntity entity) {
        return new MessageInfo(
                entity.getMessageId(),
                entity.getType(),
                entity.getTitle(),
                entity.getContent(),
                entity.getSenderId(),
                entity.getCreatedAt(),
                entity.isRead()
        );
    }

    public record MessageInfo(
            String id,
            String type,
            String title,
            String content,
            String sender,
            Instant created_at,
            boolean read
    ) {}

    public record UnreadCount(int unread_count) {}
}
