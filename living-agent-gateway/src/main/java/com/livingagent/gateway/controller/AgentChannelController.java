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
@RequestMapping("/api/agents/{agentId:.+}/channel")
public class AgentChannelController {

    private static final Logger log = LoggerFactory.getLogger(AgentChannelController.class);
    private final AccessGateService accessGateService;

    public AgentChannelController(AccessGateService accessGateService) {
        this.accessGateService = accessGateService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ChannelConfig>> getChannel(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting channel for agent: {}", agentId);

        ChannelConfig config = new ChannelConfig(
                "ch_001",
                agentId,
                "默认频道",
                "active",
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChannelConfig>> createChannel(
            @PathVariable String agentId,
            @RequestBody CreateChannelRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Creating channel for agent: {}", agentId);

        ChannelConfig config = new ChannelConfig(
                "ch_" + System.currentTimeMillis(),
                agentId,
                request.name(),
                "active",
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ChannelConfig>> updateChannel(
            @PathVariable String agentId,
            @RequestBody UpdateChannelRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating channel for agent: {}", agentId);

        ChannelConfig config = new ChannelConfig(
                request.id(),
                agentId,
                request.name(),
                request.status(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteChannel(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Deleting channel for agent: {}", agentId);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted", "agentId", agentId)));
    }

    @GetMapping("/webhook-url")
    public ResponseEntity<ApiResponse<WebhookUrl>> getWebhookUrl(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting webhook URL for agent: {}", agentId);

        WebhookUrl url = new WebhookUrl(
                "https://api.example.com/webhook/" + agentId,
                "secret_" + agentId
        );

        return ResponseEntity.ok(ApiResponse.ok(url));
    }

    public record ChannelConfig(
            String id,
            String agent_id,
            String name,
            String status,
            Instant created_at
    ) {}

    public record CreateChannelRequest(
            String name
    ) {}

    public record UpdateChannelRequest(
            String id,
            String name,
            String status
    ) {}

    public record WebhookUrl(
            String url,
            String secret
    ) {}
}
