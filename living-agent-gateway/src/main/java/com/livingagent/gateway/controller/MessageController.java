package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
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
    private final AccessGateService accessGateService;

    public MessageController(AccessGateService accessGateService) {
        this.accessGateService = accessGateService;
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<MessageInfo>>> getInbox(
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>()));
        }
        log.debug("Getting inbox messages, limit: {}, employee: {}", limit, employeeId);
        List<MessageInfo> messages = new ArrayList<>();
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> getUnreadCount(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(new UnreadCount(0)));
        }
        log.debug("Getting unread count for employee: {}", employeeId);
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCount(0)));
    }

    @PutMapping("/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String messageId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }
        log.info("Marking message as read: {}", messageId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }
        log.info("Marking all messages as read");
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> err(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
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
