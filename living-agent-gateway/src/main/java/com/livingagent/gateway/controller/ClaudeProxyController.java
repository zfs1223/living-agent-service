package com.livingagent.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.proxy.anthropic.AnthropicMessagesRequest;
import com.livingagent.core.proxy.anthropic.ClaudeProxyRequestContext;
import com.livingagent.core.proxy.anthropic.ClaudeProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/proxy/anthropic")
public class ClaudeProxyController {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProxyController.class);

    private final ClaudeProxyService proxyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeProxyController(ClaudeProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping(value = "/v1/messages")
    public ResponseEntity<?> createMessage(@RequestBody AnthropicMessagesRequest request,
                                    @RequestHeader(value = "x-request-id", required = false) String requestId,
                                    @RequestHeader(value = "x-session-id", required = false) String sessionId,
                                    @RequestHeader(value = "x-employee-id", required = false) String employeeId,
                                    @RequestHeader(value = "x-department-id", required = false) String departmentId,
                                    @RequestHeader(value = "x-brain-id", required = false) String brainId,
                                    @RequestHeader(value = "x-task-type", required = false) String taskType,
                                    @RequestHeader(value = "x-api-key", required = false) String apiKey,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Map<String, String> headers = new HashMap<>();
        if (requestId != null) headers.put("x-request-id", requestId);
        if (sessionId != null) headers.put("x-session-id", sessionId);
        if (employeeId != null) headers.put("x-employee-id", employeeId);
        if (departmentId != null) headers.put("x-department-id", departmentId);
        if (brainId != null) headers.put("x-brain-id", brainId);
        if (taskType != null) headers.put("x-task-type", taskType);
        if (apiKey != null) headers.put("x-api-key", apiKey);
        if (authorization != null) headers.put("authorization", authorization);

        ClaudeProxyRequestContext context = ClaudeProxyRequestContext.from(headers, Map.of());

        boolean stream = request.stream() == null || request.stream();

        if (stream) {
            SseEmitter emitter = new SseEmitter(600_000L);
            proxyService.createMessage(request, context, emitter, true);
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
        } else {
            String jsonResponse = proxyService.createNonStreamMessage(request, context);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResponse);
        }
    }

    @PostMapping("/v1/messages/count_tokens")
    public ResponseEntity<Map<String, Object>> countTokens(@RequestBody Object requestBody) {
        return ResponseEntity.ok(Map.of("input_tokens", 0, "cache_creation_input_tokens", 0, "cache_read_input_tokens", 0));
    }

    @GetMapping("/v1/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        return ResponseEntity.ok(Map.of(
            "data", new Object[]{
                Map.of(
                    "id", "claude-sonnet-4-20250514",
                    "object", "model",
                    "display_name", "Claude Sonnet 4",
                    "created", System.currentTimeMillis() / 1000
                ),
                Map.of(
                    "id", "claude-opus-4-20250514",
                    "object", "model",
                    "display_name", "Claude Opus 4",
                    "created", System.currentTimeMillis() / 1000
                ),
                Map.of(
                    "id", "claude-haiku-4-20250514",
                    "object", "model",
                    "display_name", "Claude Haiku 4",
                    "created", System.currentTimeMillis() / 1000
                )
            },
            "has_more", false,
            "first_id", "claude-sonnet-4-20250514",
            "last_id", "claude-haiku-4-20250514"
        ));
    }

    @GetMapping("/v1/models/{modelId}")
    public ResponseEntity<Map<String, Object>> getModel(@PathVariable String modelId) {
        return ResponseEntity.ok(Map.of(
            "id", modelId,
            "object", "model",
            "display_name", modelId,
            "created", System.currentTimeMillis() / 1000
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "service", "claude-proxy",
            "version", "1.0.0"
        ));
    }
}
