package com.livingagent.core.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Anthropic 协议的 Provider 实现。
 * 使用 x-api-key 认证和 /v1/messages 端点。
 */
public class AnthropicProvider implements Provider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    private final ResolvedBrainModel resolvedModel;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String providerName;

    public AnthropicProvider(ResolvedBrainModel resolvedModel) {
        this.resolvedModel = resolvedModel;
        this.objectMapper = new ObjectMapper();
        this.providerName = "anthropic:" + resolvedModel.getProviderId();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(120000);
        factory.setReadTimeout(300000);
        this.restTemplate = new RestTemplate(factory);

        log.info("AnthropicProvider created for provider={}, model={}",
            resolvedModel.getProviderId(), resolvedModel.getModelName());
    }

    @Override
    public String name() {
        return providerName;
    }

    @Override
    public ProviderCapabilities capabilities() {
        if (resolvedModel.isSupportsToolChoice()) {
            return ProviderCapabilities.withTools();
        }
        return ProviderCapabilities.basic();
    }

    @Override
    public ToolsPayload convertTools(List<ToolSchema> tools) {
        List<Map<String, Object>> anthropicTools = new ArrayList<>();

        for (ToolSchema tool : tools) {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", tool.name());
            toolDef.put("description", tool.description());

            Map<String, Object> inputSchema = new HashMap<>();
            inputSchema.put("type", "object");
            inputSchema.put("properties", tool.properties());
            if (tool.required() != null) {
                inputSchema.put("required", tool.required());
            }
            toolDef.put("input_schema", inputSchema);

            anthropicTools.add(toolDef);
        }

        return new ToolsPayload(anthropicTools, ToolsPayload.ToolsPayloadType.ANTHROPIC);
    }

    @Override
    public CompletableFuture<String> simpleChat(String message, String model, double temperature) {
        return chatWithSystem(null, message, model, temperature);
    }

    @Override
    public CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(message));

        ChatRequest request = new ChatRequest(messages, List.of(), resolvedModel.getModelName(), temperature, 4096);

        return chat(request, systemPrompt).thenApply(ChatResponse::content);
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return chat(request, null);
    }

    private CompletableFuture<ChatResponse> chat(ChatRequest request, String systemPrompt) {
        String baseUrl = resolvedModel.getBaseUrl();
        String apiKey = resolvedModel.getApiKey();

        if (baseUrl == null || baseUrl.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Provider baseUrl 未配置: providerId=" + resolvedModel.getProviderId()));
        }

        String useModel = resolvedModel.getModelName();
        String chatUrl = baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")
            ? baseUrl + "/messages"
            : baseUrl + "/v1/messages";

        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : request.messages()) {
            if ("system".equals(msg.role())) {
                systemPrompt = msg.content();
                continue;
            }

            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("role", msg.role());
            messageMap.put("content", msg.content() != null ? msg.content() : "");

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                List<Map<String, Object>> contentBlocks = new ArrayList<>();
                if (msg.content() != null && !msg.content().isEmpty()) {
                    Map<String, Object> textBlock = new HashMap<>();
                    textBlock.put("type", "text");
                    textBlock.put("text", msg.content());
                    contentBlocks.add(textBlock);
                }
                for (ToolCallData tc : msg.toolCalls()) {
                    Map<String, Object> toolUseBlock = new HashMap<>();
                    toolUseBlock.put("type", "tool_use");
                    toolUseBlock.put("id", tc.id());
                    toolUseBlock.put("name", tc.name());
                    try {
                        Map<String, Object> input = new ObjectMapper().readValue(tc.arguments(), Map.class);
                        toolUseBlock.put("input", input);
                    } catch (Exception e) {
                        Map<String, Object> input = new HashMap<>();
                        input.put("raw", tc.arguments());
                        toolUseBlock.put("input", input);
                    }
                    contentBlocks.add(toolUseBlock);
                }
                messageMap.put("content", contentBlocks);
            }

            if (msg.toolResults() != null && !msg.toolResults().isEmpty()) {
                List<Map<String, Object>> contentBlocks = new ArrayList<>();
                for (ToolResultData result : msg.toolResults()) {
                    Map<String, Object> toolResultBlock = new HashMap<>();
                    toolResultBlock.put("type", "tool_result");
                    toolResultBlock.put("tool_use_id", result.callId());
                    toolResultBlock.put("content", result.content());
                    contentBlocks.add(toolResultBlock);
                }
                messageMap.put("role", "user");
                messageMap.put("content", contentBlocks);
            }

            messages.add(messageMap);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", useModel);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", request.maxTokens());

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            requestBody.put("system", systemPrompt);
        }

        if (request.tools() != null && !request.tools().isEmpty() && resolvedModel.isSupportsToolChoice()) {
            requestBody.put("tools", convertTools(request.tools()).payload());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("x-api-key", apiKey);
                headers.set("anthropic-version", "2023-06-01");

                HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);
                String response = restTemplate.postForObject(
                    chatUrl, entity, String.class);

                Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

                List<Map<String, Object>> content = (List<Map<String, Object>>) responseMap.get("content");
                if (content == null || content.isEmpty()) {
                    throw new RuntimeException("Anthropic 返回空响应: " + resolvedModel.getProviderId());
                }

                StringBuilder textContent = new StringBuilder();
                List<ToolCallData> toolCalls = new ArrayList<>();

                for (Map<String, Object> block : content) {
                    String type = (String) block.get("type");
                    if ("text".equals(type)) {
                        textContent.append(block.get("text"));
                    } else if ("tool_use".equals(type)) {
                        Map<String, Object> input = (Map<String, Object>) block.get("input");
                        String argumentsJson = input != null ? objectMapper.writeValueAsString(input) : "{}";
                        toolCalls.add(new ToolCallData(
                            (String) block.get("id"),
                            (String) block.get("name"),
                            argumentsJson
                        ));
                    }
                }

                int inputTokens = 0;
                int outputTokens = 0;
                if (responseMap.containsKey("usage")) {
                    Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
                    inputTokens = ((Number) usage.getOrDefault("input_tokens", 0)).intValue();
                    outputTokens = ((Number) usage.getOrDefault("output_tokens", 0)).intValue();
                }

                String stopReason = (String) responseMap.getOrDefault("stop_reason", "end_turn");

                return new ChatResponse(textContent.toString(), toolCalls, inputTokens, outputTokens, stopReason);
            } catch (Exception e) {
                log.error("AnthropicProvider chat failed: providerId={}, model={}, baseUrl={}, error={}",
                    resolvedModel.getProviderId(), resolvedModel.getModelName(), baseUrl, e.getMessage());
                throw new RuntimeException("Anthropic 模型调用失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean supportsNativeTools() {
        return resolvedModel.isSupportsToolChoice();
    }

    @Override
    public boolean supportsVision() {
        return false;
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }
}
