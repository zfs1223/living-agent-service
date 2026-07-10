package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.knowledge.KnowledgeScope;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.KnowledgeGovernanceService;
import com.livingagent.gateway.service.KnowledgePromotionAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeGovernanceService governanceService;
    private final KnowledgePromotionAuditService promotionAuditService;
    private final AccessGateService accessGateService;

    public KnowledgeController(KnowledgeGovernanceService governanceService,
                                KnowledgePromotionAuditService promotionAuditService,
                                AccessGateService accessGateService) {
        this.governanceService = governanceService;
        this.promotionAuditService = promotionAuditService;
        this.accessGateService = accessGateService;
    }

    private boolean requireFullAccess(String employeeId) {
        return employeeId != null && !employeeId.isBlank() && !accessGateService.hasFullAccess(employeeId);
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (requireFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }
        return ResponseEntity.ok(governanceService.summary());
    }

    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanup(@RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (requireFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }
        governanceService.cleanup();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{key}/promote")
    public ResponseEntity<?> promote(@PathVariable String key,
                                     @RequestBody Map<String, Object> request,
                                     @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (requireFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }
        String scope = String.valueOf(request.getOrDefault("scope", "L2_DEPARTMENT"));
        String reason = String.valueOf(request.getOrDefault("reason", "manual promotion"));
        KnowledgeScope target = KnowledgeScope.valueOf(scope);
        return ResponseEntity.ok(promotionAuditService.promote(key, target, employeeId == null ? "system" : employeeId, reason));
    }

    @GetMapping("/{key}/history")
    public ResponseEntity<?> history(@PathVariable String key,
                                     @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (requireFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }
        return ResponseEntity.ok(promotionAuditService.history(key));
    }

    /**
     * P2-2: POST /api/knowledge/{id}/feedback - 知识效果反馈
     */
    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<Void>> submitFeedback(
            @PathVariable String id,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        Boolean helpful = (Boolean) request.get("helpful");
        if (helpful == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("invalid_request", "Missing 'helpful' field"));
        }
        
        // Log feedback (P2-2: knowledge effect feedback tracking)
        governanceService.recordFeedback(id, helpful, employeeId);
        
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
