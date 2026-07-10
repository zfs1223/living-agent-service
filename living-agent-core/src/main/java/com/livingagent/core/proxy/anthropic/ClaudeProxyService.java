package com.livingagent.core.proxy.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.model.pool.ModelHealthRegistry;
import com.livingagent.core.model.proxy.feedback.ClaudeProxyMetricsService;
import com.livingagent.core.proxy.anthropic.converter.AnthropicToOpenAiConverter;
import com.livingagent.core.proxy.anthropic.sse.AnthropicSseBuilder;
import com.livingagent.core.proxy.anthropic.sse.OpenAiStreamChunkParser;
import com.livingagent.core.sandbox.ClaudeCliProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeProxyService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProxyService.class);

    private final ClaudeProxyModelRouter modelRouter;
    private final AnthropicToOpenAiConverter converter;
    private final ClaudeProxyAuditService auditService;
    private final ClaudeCliProperties properties;
    private final ModelHealthRegistry modelHealthRegistry;
    private final ClaudeProxyMetricsService proxyMetricsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeProxyService(ClaudeProxyModelRouter modelRouter,
                              AnthropicToOpenAiConverter converter,
                              ClaudeProxyAuditService auditService,
                              ClaudeCliProperties properties,
                              ModelHealthRegistry modelHealthRegistry,
                              ClaudeProxyMetricsService proxyMetricsService) {
        this.modelRouter = modelRouter;
        this.converter = converter;
        this.auditService = auditService;
        this.properties = properties;
        this.modelHealthRegistry = modelHealthRegistry;
        this.proxyMetricsService = proxyMetricsService;
    }

    public void createMessage(AnthropicMessagesRequest request, ClaudeProxyRequestContext context, SseEmitter emitter) {
        createMessage(request, context, emitter, true);
    }

    public String createNonStreamMessage(AnthropicMessagesRequest request, ClaudeProxyRequestContext context) {
        long startTime = System.currentTimeMillis();
        String requestedModel = request.resolveModelName();

        auditService.recordRequestReceived(context.requestId(), requestedModel, context.employeeId(), context.sessionId());

        ClaudeProxyModelRouter.RoutingResult routing = modelRouter.resolve(request, context);
        if (!routing.success()) {
            return buildNonStreamError("no_provider", "No provider configured for this request");
        }

        auditService.recordModelResolved(context.requestId(), routing.requestedModel(), routing.provider().getDisplayName());

        String providerBaseUrl = routing.provider().getBaseUrl();
        if (!providerBaseUrl.endsWith("/v1") && !providerBaseUrl.endsWith("/v4")
            && !providerBaseUrl.endsWith("/v1/") && !providerBaseUrl.endsWith("/v4/")) {
            providerBaseUrl = providerBaseUrl + "/v1";
        }
        String providerUrl = providerBaseUrl + "/chat/completions";
        String apiKey = routing.provider().getApiKeyEncrypted();

        Map<String, Object> openAiRequest = converter.convert(request, routing.actualModel());
        openAiRequest.put("stream", false);

        try {
            HttpURLConnection conn = createConnection(providerUrl, apiKey);
            String requestBody = objectMapper.writeValueAsString(openAiRequest);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readStream(conn.getErrorStream());
                modelHealthRegistry.recordFailure(routing.actualModel(), routing.provider().getId(), "HTTP " + responseCode);
                auditService.recordFailed(context.requestId(), "provider_error", errorBody, System.currentTimeMillis() - startTime);
                proxyMetricsService.recordRequest(routing.provider().getDisplayName(), false, System.currentTimeMillis() - startTime);
                return buildNonStreamError("provider_error", "Provider returned " + responseCode + ": " + errorBody);
            }

            String responseBody = readStream(conn.getInputStream());
            Map<String, Object> openAiResponse = objectMapper.readValue(responseBody, Map.class);

            String content = "";
            String stopReason = "end_turn";
            int inputTokens = 0;
            int outputTokens = 0;

            try {
                Map<String, Object> usage = (Map<String, Object>) openAiResponse.get("usage");
                if (usage != null) {
                    inputTokens = ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue();
                    outputTokens = ((Number) usage.getOrDefault("completion_tokens", 0)).intValue();
                }
                List<Map<String, Object>> choices = (List<Map<String, Object>>) openAiResponse.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null) {
                        content = (String) message.getOrDefault("content", "");
                    }
                    String finishReason = (String) choice.get("finish_reason");
                    if (finishReason != null) {
                        stopReason = "stop".equals(finishReason) ? "end_turn" : finishReason;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse OpenAI response, returning raw content: {}", e.getMessage());
                content = responseBody;
            }

            auditService.recordCompleted(context.requestId(), stopReason, inputTokens, outputTokens, System.currentTimeMillis() - startTime);
            modelHealthRegistry.recordSuccess(routing.actualModel(), routing.provider().getId(), System.currentTimeMillis() - startTime);
            proxyMetricsService.recordRequest(routing.provider().getDisplayName(), true, System.currentTimeMillis() - startTime);

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("id", "msg_" + System.currentTimeMillis());
            message.put("type", "message");
            message.put("role", "assistant");
            message.put("model", routing.requestedModel());
            message.put("content", List.of(Map.of("type", "text", "text", content)));
            message.put("stop_reason", stopReason);
            message.put("stop_sequence", null);
            message.put("usage", Map.of("input_tokens", inputTokens, "output_tokens", outputTokens));

            return objectMapper.writeValueAsString(message);

        } catch (Exception e) {
            log.error("Claude proxy non-stream request failed: {}", e.getMessage());
            modelHealthRegistry.recordFailure(routing.actualModel(), routing.provider().getId(), e.getMessage());
            auditService.recordFailed(context.requestId(), "provider_error", e.getMessage(), System.currentTimeMillis() - startTime);
            proxyMetricsService.recordRequest(routing.provider().getDisplayName(), false, System.currentTimeMillis() - startTime);
            return buildNonStreamError("provider_error", e.getMessage());
        }
    }

    private String buildNonStreamError(String type, String message) {
        try {
            Map<String, Object> error = Map.of("type", "error", "error", Map.of("type", type, "message", message));
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"}}";
        }
    }

    public void createMessage(AnthropicMessagesRequest request, ClaudeProxyRequestContext context, SseEmitter emitter, boolean stream) {
        long startTime = System.currentTimeMillis();
        String requestedModel = request.resolveModelName();

        auditService.recordRequestReceived(context.requestId(), requestedModel, context.employeeId(), context.sessionId());

        ClaudeProxyModelRouter.RoutingResult routing = modelRouter.resolve(request, context);
        if (!routing.success()) {
            emitError(emitter, context, "no_provider", "No provider configured for this request", startTime);
            return;
        }

        auditService.recordModelResolved(context.requestId(), routing.requestedModel(), routing.provider().getDisplayName());

        String providerBaseUrl = routing.provider().getBaseUrl();
        if (!providerBaseUrl.endsWith("/v1") && !providerBaseUrl.endsWith("/v4")
            && !providerBaseUrl.endsWith("/v1/") && !providerBaseUrl.endsWith("/v4/")) {
            providerBaseUrl = providerBaseUrl + "/v1";
        }
        String providerUrl = providerBaseUrl + "/chat/completions";
        String apiKey = routing.provider().getApiKeyEncrypted();

        Map<String, Object> openAiRequest = converter.convert(request, routing.actualModel());

        int streamTimeoutMs = properties.getProxy().getStreamTimeoutSeconds() * 1000;
        SseEmitter timeoutEmitter = new SseEmitter((long) streamTimeoutMs);

        try {
            AnthropicSseBuilder sseBuilder = new AnthropicSseBuilder(emitter, routing.requestedModel());

            emitter.onTimeout(() -> {
                auditService.recordFailed(context.requestId(), "timeout", "Stream timeout", System.currentTimeMillis() - startTime);
            });

            emitter.onCompletion(() -> {
                long duration = System.currentTimeMillis() - startTime;
                auditService.recordCompleted(context.requestId(), "end_turn", 0, 0, duration);
            });

            HttpURLConnection conn = createConnection(providerUrl, apiKey);
            String requestBody = objectMapper.writeValueAsString(openAiRequest);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readStream(conn.getErrorStream());
                modelHealthRegistry.recordFailure(routing.actualModel(), routing.provider().getId(), "HTTP " + responseCode);
                proxyMetricsService.recordRequest(routing.provider().getDisplayName(), false, System.currentTimeMillis() - startTime);
                throw new RuntimeException("Provider returned " + responseCode + ": " + errorBody);
            }

            sseBuilder.messageStart();
            sseBuilder.contentBlockStart(0, "text", null, null);

            OpenAiStreamChunkParser parser = new OpenAiStreamChunkParser();
            boolean[] thinkingBlockStarted = {false};
            parser.parseStream(conn.getInputStream(), chunk -> {
                try {
                    switch (chunk.type()) {
                        case TEXT -> {
                            sseBuilder.contentBlockDelta(0, chunk.content());
                            auditService.recordStreamEvent(context.requestId(), "text", 0);
                        }
                        case THINKING -> {
                            if (!thinkingBlockStarted[0]) {
                                sseBuilder.contentBlockStop(0);
                                sseBuilder.contentBlockStart(1, "thinking", null, null);
                                thinkingBlockStarted[0] = true;
                            }
                            sseBuilder.thinkingDelta(1, chunk.content());
                            auditService.recordStreamEvent(context.requestId(), "thinking", 1);
                        }
                        case TOOL_USE -> {
                            if (thinkingBlockStarted[0]) {
                                sseBuilder.contentBlockStop(1);
                                thinkingBlockStarted[0] = false;
                            } else {
                                sseBuilder.contentBlockStop(0);
                            }
                            sseBuilder.contentBlockStart(2, "tool_use", chunk.toolCallId(), chunk.toolName());
                            if (chunk.toolArgs() != null && !chunk.toolArgs().isEmpty()) {
                                sseBuilder.toolUseDelta(2, chunk.toolName(), chunk.toolArgs());
                            }
                        }
                        case FINISH -> {
                            if (thinkingBlockStarted[0]) {
                                sseBuilder.contentBlockStop(1);
                            } else {
                                sseBuilder.contentBlockStop(0);
                            }
                            int outputTokens = chunk.usage() != null ? chunk.usage().getOrDefault("output_tokens", 0) : 0;
                            int inputTokens = chunk.usage() != null ? chunk.usage().getOrDefault("input_tokens", 0) : 0;
                            String stopReason = chunk.finishReason() != null ? chunk.finishReason() : "end_turn";
                            sseBuilder.messageDelta(stopReason, outputTokens);
                            sseBuilder.messageStop(stopReason);
                            modelHealthRegistry.recordSuccess(routing.actualModel(), routing.provider().getId(), System.currentTimeMillis() - startTime);
                            auditService.recordCompleted(context.requestId(), stopReason, inputTokens, outputTokens, System.currentTimeMillis() - startTime);
                            proxyMetricsService.recordRequest(routing.provider().getDisplayName(), true, System.currentTimeMillis() - startTime);
                            emitter.complete();
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to send SSE event: {}", e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Claude proxy request failed: {}", e.getMessage());
            modelHealthRegistry.recordFailure(routing.actualModel(), routing.provider().getId(), e.getMessage());
            proxyMetricsService.recordRequest(routing.provider().getDisplayName(), false, System.currentTimeMillis() - startTime);
            emitError(emitter, context, "provider_error", e.getMessage(), startTime);
        }
    }

    private HttpURLConnection createConnection(String url, String apiKey) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(properties.getProxy().getStreamTimeoutSeconds() * 1000);
        return conn;
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private void emitError(SseEmitter emitter, ClaudeProxyRequestContext context, String type, String message, long startTime) {
        try {
            AnthropicSseBuilder sseBuilder = new AnthropicSseBuilder(emitter, "unknown");
            sseBuilder.error(type, message);
            emitter.complete();
        } catch (Exception e) {
            log.error("Failed to emit error: {}", e.getMessage());
        }
        auditService.recordFailed(context.requestId(), type, message, System.currentTimeMillis() - startTime);
    }
}
