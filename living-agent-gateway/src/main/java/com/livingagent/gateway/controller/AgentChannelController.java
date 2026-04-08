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
@RequestMapping("/api/agents/{agentId}/channel")
public class AgentChannelController {

    private static final Logger log = LoggerFactory.getLogger(AgentChannelController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<ChannelConfig>> getChannel(@PathVariable String agentId) {
        log.debug("Getting channel for agent: {}", agentId);

        ChannelConfig config = new ChannelConfig(
                "ch_001",
                agentId,
                "默认频道",
                "active",
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChannelConfig>> createChannel(
            @PathVariable String agentId,
            @RequestBody CreateChannelRequest request
    ) {
        log.info("Creating channel for agent: {}", agentId);

        ChannelConfig config = new ChannelConfig(
                "ch_" + System.currentTimeMillis(),
                agentId,
                request.name(),
                "active",
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ChannelConfig>> updateChannel(
            @PathVariable String agentId,
            @RequestBody UpdateChannelRequest request
    ) {
        log.info("Updating channel for agent: {}", agentId);

        ChannelConfig config = new ChannelConfig(
                request.id(),
                agentId,
                request.name(),
                request.status(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteChannel(@PathVariable String agentId) {
        log.info("Deleting channel for agent: {}", agentId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "agentId", agentId)));
    }

    @GetMapping("/webhook-url")
    public ResponseEntity<ApiResponse<WebhookUrl>> getWebhookUrl(@PathVariable String agentId) {
        log.debug("Getting webhook URL for agent: {}", agentId);

        WebhookUrl url = new WebhookUrl(
                "https://api.example.com/webhook/" + agentId,
                "secret_" + agentId
        );

        return ResponseEntity.ok(ApiResponse.success(url));
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
