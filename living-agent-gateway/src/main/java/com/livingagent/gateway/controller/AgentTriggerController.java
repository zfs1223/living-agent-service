package com.livingagent.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents/{agentId}/triggers")
public class AgentTriggerController {

    private static final Logger log = LoggerFactory.getLogger(AgentTriggerController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<List<TriggerInfo>>> listTriggers(@PathVariable String agentId) {
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

        return ResponseEntity.ok(ApiResponse.success(triggers));
    }

    @PatchMapping("/{triggerId}")
    public ResponseEntity<ApiResponse<TriggerInfo>> updateTrigger(
            @PathVariable String agentId,
            @PathVariable String triggerId,
            @RequestBody UpdateTriggerRequest request
    ) {
        log.info("Updating trigger: {} for agent: {}", triggerId, agentId);

        TriggerInfo trigger = new TriggerInfo(
                triggerId,
                agentId,
                request.name(),
                request.type(),
                request.enabled(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(trigger));
    }

    @DeleteMapping("/{triggerId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteTrigger(
            @PathVariable String agentId,
            @PathVariable String triggerId
    ) {
        log.info("Deleting trigger: {} for agent: {}", triggerId, agentId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "id", triggerId)));
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
