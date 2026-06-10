package com.livingagent.gateway.controller;

import java.util.Map;
import java.util.HashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.service.AgentService;
import com.livingagent.gateway.controller.common.ApiResponse;

@RestController
@RequestMapping("/api")
public class AgentController {

    private final AgentService agentService;
    private final AccessGateService accessGateService;

    public AgentController(AgentService agentService, AccessGateService accessGateService) {
        this.agentService = agentService;
        this.accessGateService = accessGateService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "权限不足，无法访问健康检查", Map.of()));
        }
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(health));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "权限不足，无法查看状态", Map.of()));
        }
        return ResponseEntity.ok(ApiResponse.ok(agentService.getStatus()));
    }

    @PostMapping("/session/{sessionId}/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startSession(
            @PathVariable String sessionId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetName,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {
        String effectiveEmployeeId = (employeeId != null && !employeeId.isBlank()) ? employeeId : headerEmployeeId;
        if (effectiveEmployeeId != null && !effectiveEmployeeId.isBlank()) {
            String resolvedType = (targetType == null || targetType.isBlank()) ? "brain" : targetType;
            String resolvedName = (targetName == null || targetName.isBlank()) ? "MainBrain" : targetName;
            boolean allowed = accessGateService.canRoute(effectiveEmployeeId, resolvedType, resolvedName);
            if (!allowed) {
                Map<String, Object> denied = new HashMap<>();
                denied.put("sessionId", sessionId);
                denied.put("employeeId", effectiveEmployeeId);
                denied.put("targetType", resolvedType);
                denied.put("targetName", resolvedName);
                denied.put("reason", "permission denied before routing");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.err("PERMISSION_DENIED", "权限不足，无法启动会话", denied));
            }
        }
        agentService.startSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "started");
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/session/{sessionId}/end")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endSession(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "权限不足，无法结束会话", Map.of()));
        }
        agentService.endSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "ended");
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessionStatus(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "权限不足，无法查看会话状态", Map.of()));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("active", agentService.isSessionActive(sessionId));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
