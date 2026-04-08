package com.livingagent.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<MessageInfo>>> getInbox(
            @RequestParam(defaultValue = "50") int limit
    ) {
        log.debug("Getting inbox messages, limit: {}", limit);
        List<MessageInfo> messages = new ArrayList<>();
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> getUnreadCount() {
        log.debug("Getting unread count");
        return ResponseEntity.ok(ApiResponse.success(new UnreadCount(0)));
    }

    @PutMapping("/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String messageId) {
        log.info("Marking message as read: {}", messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        log.info("Marking all messages as read");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }
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
