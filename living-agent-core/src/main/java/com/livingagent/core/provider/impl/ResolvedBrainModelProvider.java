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

public class ResolvedBrainModelProvider implements Provider {

    private static final Logger log = LoggerFactory.getLogger(ResolvedBrainModelProvider.class);

    private final ResolvedBrainModel resolvedModel;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String providerName;

    public ResolvedBrainModelProvider(ResolvedBrainModel resolvedModel) {
        this.resolvedModel = resolvedModel;
        this.objectMapper = new ObjectMapper();
        this.providerName = "brain-model:" + resolvedModel.getProviderId();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(120000);
        factory.setReadTimeout(300000);
        this.restTemplate = new RestTemplate(factory);

        log.info("ResolvedBrainModelProvider created for provider={}, model={}",
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
        List<Map<String, Object>> openAiTools = new ArrayList<>();

        for (ToolSchema tool : tools) {
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("type", "object");
            parameters.put("properties", tool.properties());
            if (tool.required() != null) {
                parameters.put("required", tool.required());
            }
            function.put("parameters", parameters);

            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("type", "function");
            toolDef.put("function", function);
            openAiTools.add(toolDef);
        }

        return new ToolsPayload(openAiTools, ToolsPayload.ToolsPayloadType.OPENAI);
    }

    @Override
    public CompletableFuture<String> simpleChat(String message, String model, double temperature) {
        return chatWithSystem(null, message, model, temperature);
    }

    @Override
    public CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        messages.add(ChatMessage.user(message));

        ChatRequest request = new ChatRequest(messages, List.of(), resolvedModel.getModelName(), temperature, 4096);

        return chat(request).thenApply(ChatResponse::content);
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        String baseUrl = resolvedModel.getBaseUrl();
        String apiKey = resolvedModel.getApiKey();

        if (baseUrl == null || baseUrl.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Provider baseUrl 未配置: providerId=" + resolvedModel.getProviderId()));
        }

        String useModel = resolvedModel.getModelName();
        boolean hasVersionSuffix = baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")
            || baseUrl.endsWith("/v4") || baseUrl.endsWith("/v4/");
        String chatUrl = hasVersionSuffix
            ? baseUrl + "/chat/completions"
            : baseUrl + "/v1/chat/completions";

        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : request.messages()) {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("role", msg.role());
            messageMap.put("content", msg.content() != null ? msg.content() : "");

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (ToolCallData tc : msg.toolCalls()) {
                    Map<String, Object> toolCall = new HashMap<>();
                    toolCall.put("id", tc.id());
                    toolCall.put("type", "function");
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", tc.name());
                    function.put("arguments", tc.arguments());
                    toolCall.put("function", function);
                    toolCalls.add(toolCall);
                }
                messageMap.put("tool_calls", toolCalls);
            }

            if (msg.toolResults() != null && !msg.toolResults().isEmpty()) {
                for (ToolResultData result : msg.toolResults()) {
                    Map<String, Object> toolMessage = new HashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", result.callId());
                    toolMessage.put("content", result.content());
                    messages.add(toolMessage);
                }
            } else {
                messages.add(messageMap);
            }
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", useModel);
        requestBody.put("messages", messages);
        requestBody.put("temperature", request.temperature());
        requestBody.put("max_tokens", request.maxTokens());

        if (request.tools() != null && !request.tools().isEmpty() && resolvedModel.isSupportsToolChoice()) {
            requestBody.put("tools", convertTools(request.tools()).payload());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (apiKey != null && !apiKey.isEmpty()) {
                    headers.setBearerAuth(apiKey);
                }

                HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);
                String response = restTemplate.postForObject(
                    chatUrl, entity, String.class);

                Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new RuntimeException("模型返回空响应: " + resolvedModel.getProviderId());
                }

                Map<String, Object> choice = choices.get(0);
                Map<String, Object> messageMap = (Map<String, Object>) choice.get("message");
                String content = (String) messageMap.get("content");

                List<ToolCallData> toolCalls = new ArrayList<>();
                if (messageMap.containsKey("tool_calls") && messageMap.get("tool_calls") != null) {
                    List<Map<String, Object>> tcList = (List<Map<String, Object>>) messageMap.get("tool_calls");
                    if (tcList != null) {
                        for (Map<String, Object> tc : tcList) {
                            Map<String, Object> func = (Map<String, Object>) tc.get("function");
                            toolCalls.add(new ToolCallData(
                                (String) tc.get("id"),
                                (String) func.get("name"),
                                (String) func.get("arguments")
                            ));
                        }
                    }
                }

                int promptTokens = 0;
                int completionTokens = 0;
                if (responseMap.containsKey("usage")) {
                    Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
                    promptTokens = ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue();
                    completionTokens = ((Number) usage.getOrDefault("completion_tokens", 0)).intValue();
                }

                String finishReason = (String) choice.getOrDefault("finish_reason", "stop");

                return new ChatResponse(content, toolCalls, promptTokens, completionTokens, finishReason);
            } catch (Exception e) {
                log.error("ResolvedBrainModelProvider chat failed: providerId={}, model={}, baseUrl={}, error={}",
                    resolvedModel.getProviderId(), resolvedModel.getModelName(), baseUrl, e.getMessage());
                throw new RuntimeException("模型调用失败: " + e.getMessage(), e);
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
