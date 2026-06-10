package com.livingagent.gateway.controller;

import com.livingagent.core.evolution.engine.EvolutionOrchestrator;
import com.livingagent.core.evolution.executor.EvolutionFeedbackService;
import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.evolution.executor.EvolutionResultRepository;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.EvolutionFeedbackBridgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evolution")
public class EvolutionAdminController {

    private static final Logger log = LoggerFactory.getLogger(EvolutionAdminController.class);

    private final EvolutionFeedbackBridgeService bridgeService;
    private final EvolutionResultRepository resultRepository;
    private final EvolutionFeedbackService feedbackService;
    private final AccessGateService accessGateService;
    private final EvolutionOrchestrator orchestrator;

    public EvolutionAdminController(EvolutionFeedbackBridgeService bridgeService,
                                    EvolutionResultRepository resultRepository,
                                    EvolutionFeedbackService feedbackService,
                                    AccessGateService accessGateService,
                                    EvolutionOrchestrator orchestrator) {
        this.bridgeService = bridgeService;
        this.resultRepository = resultRepository;
        this.feedbackService = feedbackService;
        this.accessGateService = accessGateService;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<Object>> feedback(@RequestBody Map<String, Object> result,
                                      @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }

        EvolutionResult evolutionResult = mapResult(result);
        resultRepository.save(evolutionResult);
        return ResponseEntity.ok(ApiResponse.ok(bridgeService.record(evolutionResult)));
    }

    @GetMapping("/feedback/recent")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recent(@RequestParam(defaultValue = "20") int limit,
                                    @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }
        Map<String, Object> data = Map.of(
                "items", feedbackService.recent(limit),
                "stats", feedbackService.statistics()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/trigger-auto-adjust")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerAutoAdjust(@RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }

        log.info("Manual trigger: auto-adjust brain-models");
        try {
            Map<String, Object> adjustments = orchestrator.runAutoAdjust(null);
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("adjustments", adjustments);
            data.put("message", "自动调整已完成");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.error("Auto-adjust failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.err("auto_adjust_failed", e.getMessage()));
        }
    }

    @PostMapping("/rollback/{brainId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollbackBrain(@PathVariable String brainId,
                                           @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }

        log.info("Manual rollback: brainId={}", brainId);
        try {
            Map<String, Object> rollbackResult = orchestrator.rollbackBrain(brainId);
            String status = (String) rollbackResult.getOrDefault("status", "failed");
            
            if ("success".equals(status)) {
                return ResponseEntity.ok(ApiResponse.ok(rollbackResult));
            } else {
                return ResponseEntity.ok(ApiResponse.err("rollback_failed", 
                    String.valueOf(rollbackResult.getOrDefault("reason", "unknown"))));
            }
        } catch (Exception e) {
            log.error("Rollback failed for brainId={}: {}", brainId, e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.err("rollback_error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEvolutionHistory(@RequestParam(defaultValue = "50") int limit,
                                                  @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }

        List<EvolutionResult> history = feedbackService.recent(limit);
        Map<String, Object> stats = feedbackService.statistics();

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("items", history);
        data.put("stats", stats);
        data.put("total", history.size());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private EvolutionResult mapResult(Map<String, Object> input) {
        String status = String.valueOf(input.getOrDefault("status", "FAILED"));
        String action = String.valueOf(input.getOrDefault("action", "feedback"));
        String resultId = String.valueOf(input.getOrDefault("resultId", "evo_feedback_" + System.currentTimeMillis()));
        EvolutionResult result = EvolutionResult.skipped(null, null).withAction(action).withMetadata("sourceStatus", status);
        result.setResultId(resultId);
        if ("SUCCESS".equalsIgnoreCase(status)) {
            result.setStatus(EvolutionResult.Status.SUCCESS);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            result.setStatus(EvolutionResult.Status.FAILED);
        } else if ("SKIPPED".equalsIgnoreCase(status)) {
            result.setStatus(EvolutionResult.Status.SKIPPED);
        } else if ("DEFERRED".equalsIgnoreCase(status)) {
            result.setStatus(EvolutionResult.Status.DEFERRED);
        } else if ("ESCALATED".equalsIgnoreCase(status)) {
            result.setStatus(EvolutionResult.Status.ESCALATED);
        }
        return result;
    }
}
