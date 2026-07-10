package com.livingagent.gateway.controller;

import com.livingagent.core.database.entity.VisitorEntity;
import com.livingagent.core.database.repository.VisitorRepository;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.visitor.feedback.VisitorConversionTracker;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AccessGateService accessGateService;
    private final VisitorRepository visitorRepository;

    @Autowired(required = false)
    private VisitorConversionTracker visitorConversionTracker;

    public ReceptionController(AccessGateService accessGateService, VisitorRepository visitorRepository) {
        this.accessGateService = accessGateService;
        this.visitorRepository = visitorRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ReceptionStatus>> getStatus(
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        if (visitorId != null && !visitorId.isBlank() && !accessGateService.canRoute(visitorId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        long waiting = visitorRepository.countByStatus("waiting");
        long checkedIn = visitorRepository.countByStatus("checked_in");
        ReceptionStatus status = new ReceptionStatus(
            true,
            String.format("前台接待员在线，当前等候%d人，已签到%d人", waiting, checkedIn),
            RECEPTIONIST_NAME
        );
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {

        if (visitorId != null && !visitorId.isBlank() && !accessGateService.canRoute(visitorId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_message", "消息不能为空"));
        }

        String sessionId = visitorId != null ? visitorId : "visitor_" + UUID.randomUUID().toString().substring(0, 8);
        try {
            log.info("[Reception] Visitor {} sent message: {}", sessionId, request.message());

            // 闭环51: 访客聊天时记录
            if (visitorConversionTracker != null) {
                visitorConversionTracker.recordVisit(sessionId);
                visitorConversionTracker.recordChat(sessionId);
            }

            String response = processReceptionChat(sessionId, request.message());
            ChatResponse chatResponse = new ChatResponse(sessionId, response, System.currentTimeMillis());
            return ResponseEntity.ok(ApiResponse.ok(chatResponse));
        } catch (Exception e) {
            log.error("[Reception] Chat processing failed", e);
            return ResponseEntity.status(500).body(ApiResponse.err("processing_error", "处理消息时发生错误"));
        }
    }

    @PostMapping("/chat/stream")
    public ResponseEntity<ApiResponse<ChatResponse>> chatWithStream(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return chat(request, visitorId);
    }

    @GetMapping("/visitors")
    public ResponseEntity<ApiResponse<List<VisitorInfo>>> getVisitors(
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        if (visitorId != null && !visitorId.isBlank() && !accessGateService.canRoute(visitorId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        List<VisitorEntity> visitors = visitorRepository.findByOrderByCheckInTimeDesc();
        List<VisitorInfo> result = visitors.stream().map(this::toVisitorInfo).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<VisitorInfo>> checkIn(
            @RequestBody CheckInRequest request,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        if (visitorId != null && !visitorId.isBlank() && !accessGateService.canRoute(visitorId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_name", "访客姓名不能为空"));
        }
        log.info("Visitor check-in: {}", request.name());

        String vid = "vst_" + UUID.randomUUID().toString().substring(0, 8);
        VisitorEntity entity = new VisitorEntity(
                vid, request.name(), request.purpose(), request.contact(),
                null, Instant.now(), null, "checked_in"
        );
        visitorRepository.save(entity);

        // 闭环51: 访客签到(注册)时记录
        if (visitorConversionTracker != null) {
            visitorConversionTracker.recordVisit(vid);
            visitorConversionTracker.recordRegistration(vid);
        }

        return ResponseEntity.ok(ApiResponse.ok(toVisitorInfo(entity)));
    }

    private String processReceptionChat(String sessionId, String message) {
        String lower = message.toLowerCase();
        if (lower.contains("签到") || lower.contains("登记") || lower.contains("check")) {
            return "您好！请告诉我您的姓名和来访目的，我可以帮您完成签到登记。";
        }
        if (lower.contains("预约") || lower.contains("约见") || lower.contains("appoint")) {
            return "您好！请告诉我您要拜访的员工姓名，我帮您查询是否已预约。";
        }
        if (lower.contains("等") || lower.contains("多久") || lower.contains("wait")) {
            long waiting = visitorRepository.countByStatus("waiting");
            return String.format("当前等候区有%d位访客，我会尽快为您安排。", waiting);
        }
        if (lower.contains("部门") || lower.contains("在哪") || lower.contains("哪里") || lower.contains("location")) {
            return "各部门办公室分布在一至三楼，技术部在二楼，人力资源在三楼，行政部在一楼。需要我为您指引具体位置吗？";
        }
        return "您好！我是前台小助手，可以帮您签到登记、查询预约、指引方向。请问有什么需要帮助的？";
    }

    private VisitorInfo toVisitorInfo(VisitorEntity entity) {
        return new VisitorInfo(
                entity.getVisitorId(),
                entity.getName(),
                entity.getPurpose(),
                entity.getCheckInTime(),
                entity.getStatus()
        );
    }

    public record ReceptionStatus(boolean available, String message, String receptionistName) {}
    public record ChatRequest(String message, String context) {}
    public record ChatResponse(String sessionId, String response, long timestamp) {}
    public record VisitorInfo(String id, String name, String purpose, Instant checkInTime, String status) {}
    public record CheckInRequest(String name, String purpose, String contact) {}
}
