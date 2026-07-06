package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents/{agentId:.+}/triggers")
public class AgentTriggerController {

    private static final Logger log = LoggerFactory.getLogger(AgentTriggerController.class);
    private final AccessGateService accessGateService;

    public AgentTriggerController(AccessGateService accessGateService) {
        this.accessGateService = accessGateService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TriggerInfo>>> listTriggers(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing triggers for agent: {}", agentId);

        List<TriggerInfo> triggers = new ArrayList<>();
        triggers.add(new TriggerInfo(
                "trg_001",
                agentId,
                "代码提交",
                "webhook",
                true,
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.ok(triggers));
    }

    @PatchMapping("/{triggerId}")
    public ResponseEntity<ApiResponse<TriggerInfo>> updateTrigger(
            @PathVariable String agentId,
            @PathVariable String triggerId,
            @RequestBody UpdateTriggerRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating trigger: {} for agent: {}", triggerId, agentId);

        TriggerInfo trigger = new TriggerInfo(
                triggerId,
                agentId,
                request.name(),
                request.type(),
                request.enabled(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(trigger));
    }

    @DeleteMapping("/{triggerId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteTrigger(
            @PathVariable String agentId,
            @PathVariable String triggerId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Deleting trigger: {} for agent: {}", triggerId, agentId);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted", "id", triggerId)));
    }

    public record TriggerInfo(
            String id,
            String agent_id,
            String name,
            String type,
            boolean enabled,
            Instant created_at
    ) {}

    public record UpdateTriggerRequest(
            String name,
            String type,
            Boolean enabled
    ) {}
}
