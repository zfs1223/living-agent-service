package com.livingagent.core.provider.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.ToolSchema;

@Component
public class OllamaProvider implements Provider {
    
    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    
    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${ai-models.ollama.base-url}")
    private String baseUrl;
    
    @Value("${ai-models.ollama.default-model}")
    private String defaultModel;
    
    @Value("${ai-models.ollama.timeout}")
    private int timeout;
    
    @Autowired
    public OllamaProvider(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = new ObjectMapper();
    }
    
    public OllamaProvider(String baseUrl, String defaultModel, int timeout) {
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.timeout = timeout;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }
    
    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(factory);
        log.info("OllamaProvider initialized with baseUrl={}, model={}, timeout={}ms", 
            baseUrl, defaultModel, timeout);
    }
    
    @Override
    public String name() {
        return "ollama";
    }
    
    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.withTools();
    }
    
    @Override
    public ToolsPayload convertTools(List<ToolSchema> tools) {
        List<Map<String, Object>> ollamaTools = new ArrayList<>();
        
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
            ollamaTools.add(toolDef);
        }
        
        return new ToolsPayload(ollamaTools, ToolsPayload.ToolsPayloadType.OPENAI);
    }
    
    @Override
    public CompletableFuture<String> simpleChat(String message, String model, double temperature) {
        return chatWithSystem(null, message, model, temperature);
    }
    
    @Override
    public CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature) {
        String useModel = model != null ? model : defaultModel;
        
        List<Map<String, String>> messages = new ArrayList<>();
        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", message));
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", useModel);
        request.put("messages", messages);
        request.put("stream", false);
        request.put("options", Map.of("temperature", temperature));
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/chat";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
                String response = restTemplate.postForObject(url, entity, String.class);
                
                Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
                Map<String, Object> messageMap = (Map<String, Object>) responseMap.get("message");
                return (String) messageMap.get("content");
            } catch (Exception e) {
                log.error("Ollama chat failed: {}", e.getMessage());
                throw new RuntimeException("Ollama chat failed: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        String useModel = request.model() != null ? request.model() : defaultModel;
        
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
                    toolMessage.put("content", result.content());
                    messages.add(toolMessage);
                }
            } else {
                messages.add(messageMap);
            }
        }
        
        Map<String, Object> ollamaRequest = new HashMap<>();
        ollamaRequest.put("model", useModel);
        ollamaRequest.put("messages", messages);
        ollamaRequest.put("stream", false);
        
        if (request.tools() != null && !request.tools().isEmpty()) {
            ollamaRequest.put("tools", convertTools(request.tools()).payload());
        }
        
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", request.temperature());
        options.put("num_predict", request.maxTokens());
        ollamaRequest.put("options", options);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/chat";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(ollamaRequest), headers);
                String response = restTemplate.postForObject(url, entity, String.class);
                
                Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
                Map<String, Object> messageMap = (Map<String, Object>) responseMap.get("message");
                String content = (String) messageMap.get("content");
                
                List<ToolCallData> toolCalls = new ArrayList<>();
                if (messageMap.containsKey("tool_calls")) {
                    List<Map<String, Object>> tcList = (List<Map<String, Object>>) messageMap.get("tool_calls");
                    for (Map<String, Object> tc : tcList) {
                        Map<String, Object> func = (Map<String, Object>) tc.get("function");
                        toolCalls.add(new ToolCallData(
                            (String) tc.get("id"),
                            (String) func.get("name"),
                            (String) func.get("arguments")
                        ));
                    }
                }
                
                int promptTokens = 0;
                int completionTokens = 0;
                if (responseMap.containsKey("prompt_eval_count")) {
                    promptTokens = ((Number) responseMap.get("prompt_eval_count")).intValue();
                }
                if (responseMap.containsKey("eval_count")) {
                    completionTokens = ((Number) responseMap.get("eval_count")).intValue();
                }
                
                String finishReason = toolCalls.isEmpty() ? "stop" : "tool_calls";
                
                return new ChatResponse(content, toolCalls, promptTokens, completionTokens, finishReason);
            } catch (Exception e) {
                log.error("Ollama chat failed: {}", e.getMessage());
                throw new RuntimeException("Ollama chat failed: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public boolean supportsNativeTools() {
        return true;
    }
    
    @Override
    public boolean supportsVision() {
        return true;
    }
    
    @Override
    public boolean supportsStreaming() {
        return true;
    }
    
    public CompletableFuture<List<String>> listModels() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/tags";
                String response = restTemplate.getForObject(url, String.class);
                
                Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
                List<Map<String, Object>> models = (List<Map<String, Object>>) responseMap.get("models");
                
                List<String> modelNames = new ArrayList<>();
                for (Map<String, Object> model : models) {
                    modelNames.add((String) model.get("name"));
                }
                return modelNames;
            } catch (Exception e) {
                log.error("Failed to list Ollama models: {}", e.getMessage());
                throw new RuntimeException("Failed to list Ollama models: " + e.getMessage(), e);
            }
        });
    }
    
    public CompletableFuture<Boolean> checkHealth() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/tags";
                restTemplate.getForObject(url, String.class);
                return true;
            } catch (Exception e) {
                log.warn("Ollama health check failed: {}", e.getMessage());
                return false;
            }
        });
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }
}
