package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.proactive.ProactiveOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proactive/orchestrator")
public class ProactiveOrchestratorController {

    private final ProactiveOrchestrator orchestrator;
    private final AccessGateService accessGateService;

    public ProactiveOrchestratorController(ProactiveOrchestrator orchestrator, AccessGateService accessGateService) {
        this.orchestrator = orchestrator;
        this.accessGateService = accessGateService;
    }

    @GetMapping
    public ResponseEntity<?> run(
            @RequestParam String userId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        return ResponseEntity.ok(orchestrator.runForUser(userId));
    }
}
