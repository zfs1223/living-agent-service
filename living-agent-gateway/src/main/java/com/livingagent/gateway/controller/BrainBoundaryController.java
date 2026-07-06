package com.livingagent.gateway.controller;

import com.livingagent.core.brain.BrainBoundaryEnforcer;
import com.livingagent.core.brain.BrainBoundaryEnforcer.BrainBoundary;
import com.livingagent.core.brain.BrainBoundaryProperties;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/brain-boundary")
public class BrainBoundaryController {

    private final BrainBoundaryEnforcer enforcer;
    private final AccessGateService accessGateService;

    public BrainBoundaryController(BrainBoundaryEnforcer enforcer, AccessGateService accessGateService) {
        this.enforcer = enforcer;
        this.accessGateService = accessGateService;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<BrainBoundaryConfigResponse>> getConfig(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (!canAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }

        BrainBoundaryProperties props = enforcer.getProperties();
        Map<String, BrainBoundaryDto> boundaries = enforcer.getBoundaries().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> toDto(e.getValue()),
                (a, b) -> a,
                LinkedHashMap::new
            ));

        BrainBoundaryConfigResponse response = new BrainBoundaryConfigResponse(
            props.isEnabled(),
            props.getConsecutiveFailuresThreshold(),
            props.isAuditLogEnabled(),
            props.getAuditLogLevel(),
            boundaries
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/config/{brainId}")
    public ResponseEntity<ApiResponse<BrainBoundaryDto>> getBoundaryConfig(
            @PathVariable String brainId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (!canAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }

        Optional<BrainBoundary> boundary = enforcer.getBoundary(brainId);
        if (boundary.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Brain boundary not found: " + brainId));
        }
        return ResponseEntity.ok(ApiResponse.ok(toDto(boundary.get())));
    }

    private boolean canAccess(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) return false;
        return accessGateService.canRoute(employeeId, "brain", "AdminBrain");
    }

    private static BrainBoundaryDto toDto(BrainBoundary b) {
        return new BrainBoundaryDto(
            b.brainId(),
            b.department(),
            new ArrayList<>(b.allowedActions()),
            new ArrayList<>(b.forbiddenActions()),
            new ArrayList<>(b.escalationTriggers()),
            new ArrayList<>(b.mustEscalateScenarios())
        );
    }

    public record BrainBoundaryConfigResponse(
        boolean enabled,
        int consecutiveFailuresThreshold,
        boolean auditLogEnabled,
        String auditLogLevel,
        Map<String, BrainBoundaryDto> boundaries
    ) {}

    public record BrainBoundaryDto(
        String brainId,
        String department,
        List<String> allowedActions,
        List<String> forbiddenActions,
        List<String> escalationTriggers,
        List<String> mustEscalateScenarios
    ) {}
}
