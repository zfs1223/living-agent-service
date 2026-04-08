package com.livingagent.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reception")
public class ReceptionController {

    private static final Logger log = LoggerFactory.getLogger(ReceptionController.class);
    
    private static final String RECEPTIONIST_NAME = "前台小助手";

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ReceptionStatus>> getStatus() {
        ReceptionStatus status = new ReceptionStatus(
            true,
            "前台接待员在线，欢迎咨询",
            RECEPTIONIST_NAME
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("invalid_message", "消息不能为空"));
        }

        String sessionId = visitorId != null ? visitorId : "visitor_" + UUID.randomUUID().toString().substring(0, 8);
        
        try {
            log.info("[Reception] Visitor {} sent message: {}", sessionId, request.message());
            
            String response = processReceptionChat(sessionId, request.message());
            
            ChatResponse chatResponse = new ChatResponse(
                sessionId,
                response,
                System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(ApiResponse.success(chatResponse));
            
        } catch (Exception e) {
            log.error("[Reception] Chat processing failed", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("processing_error", "处理消息时发生错误"));
        }
    }

    @PostMapping("/chat/stream")
    public ResponseEntity<ApiResponse<ChatResponse>> chatWithStream(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return chat(request, visitorId);
    }

    @GetMapping("/visitors")
    public ResponseEntity<ApiResponse<List<VisitorInfo>>> getVisitors() {
        log.debug("Getting visitor list");
        
        List<VisitorInfo> visitors = List.of(
                new VisitorInfo("vst_001", "张三", "拜访技术部", Instant.now(), "checked_in"),
                new VisitorInfo("vst_002", "李四", "商务洽谈", Instant.now(), "waiting")
        );
        
        return ResponseEntity.ok(ApiResponse.success(visitors));
    }

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<VisitorInfo>> checkIn(@RequestBody CheckInRequest request) {
        log.info("Visitor check-in: {}", request.name());
        
        VisitorInfo visitor = new VisitorInfo(
                "vst_" + System.currentTimeMillis(),
                request.name(),
                request.purpose(),
                Instant.now(),
                "checked_in"
        );
        
        return ResponseEntity.ok(ApiResponse.success(visitor));
    }

    private String processReceptionChat(String sessionId, String message) {
        return "您好！我是前台小助手，很高兴为您服务。您有什么问题可以问我，我会尽力帮助您。";
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

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
    }

    public record ReceptionStatus(
            boolean available,
            String message,
            String receptionistName
    ) {}

    public record ChatRequest(
            String message,
            String context
    ) {}

    public record ChatResponse(
            String sessionId,
            String response,
            long timestamp
    ) {}

    public record VisitorInfo(
            String id,
            String name,
            String purpose,
            Instant checkInTime,
            String status
    ) {}

    public record CheckInRequest(
            String name,
            String purpose,
            String contact
    ) {}
}
