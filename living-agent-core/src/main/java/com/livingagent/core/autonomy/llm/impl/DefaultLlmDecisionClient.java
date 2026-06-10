package com.livingagent.core.autonomy.llm.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.context.DecisionContext;
import com.livingagent.core.autonomy.llm.LlmDecisionClient;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.provider.impl.ResolvedBrainModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class DefaultLlmDecisionClient implements LlmDecisionClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmDecisionClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final BrainRegistry brainRegistry;
    private final BrainModelResolver brainModelResolver;

    public DefaultLlmDecisionClient(BrainRegistry brainRegistry, BrainModelResolver brainModelResolver) {
        this.brainRegistry = brainRegistry;
        this.brainModelResolver = brainModelResolver;
    }

    @Override
    public <T> LlmDecisionResult<T> decide(LlmDecisionRequest<T> request) {
        return decideWithRetry(request, 1);
    }

    @Override
    public <T> LlmDecisionResult<T> decideWithRetry(LlmDecisionRequest<T> request, int maxRetries) {
        long startTime = System.currentTimeMillis();
        String modelUsed = null;
        String rawResponse = null;
        List<String> allErrors = new ArrayList<>();

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.warn("MainBrain not available for LLM decision");
            return executeFallback(request, "MainBrain not available");
        }

        String fullPrompt = buildFullPrompt(request);
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                rawResponse = mainBrain.callLlm(request.systemPrompt(), fullPrompt);
                
                if (rawResponse == null || rawResponse.isBlank()) {
                    allErrors.add("Empty response from LLM (attempt " + (attempt + 1) + ")");
                    continue;
                }

                String jsonStr = extractJson(rawResponse);
                if (jsonStr == null) {
                    allErrors.add("No valid JSON found in response (attempt " + (attempt + 1) + ")");
                    continue;
                }

                T parsedData = parseResponse(jsonStr, request.resultType());
                if (parsedData == null) {
                    allErrors.add("Failed to parse JSON to " + request.resultType().getSimpleName());
                    continue;
                }

                if (request.schema() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapData = parsedData instanceof Map 
                        ? (Map<String, Object>) parsedData 
                        : objectMapper.convertValue(parsedData, Map.class);
                    
                    List<String> schemaErrors = request.schema().validateWithErrors(mapData);
                    if (!schemaErrors.isEmpty()) {
                        allErrors.addAll(schemaErrors);
                        
                        if (attempt < maxRetries - 1) {
                            String fixPrompt = buildFixPrompt(jsonStr, schemaErrors);
                            rawResponse = mainBrain.callLlm(
                                "你是一个JSON修复助手。请修复以下JSON，使其符合要求。只输出修复后的JSON，不要有任何其他文字。",
                                fixPrompt
                            );
                            continue;
                        }
                    }
                }

                long latencyMs = System.currentTimeMillis() - startTime;
                log.info("LLM decision succeeded: promptVersion={}, attempt={}, latencyMs={}",
                    request.promptVersion(), attempt + 1, latencyMs);

                return new LlmDecisionResult<>(
                    true, parsedData, rawResponse, null, null, "llm_based", modelUsed,
                    0, 0, latencyMs, List.of(), null,
                    Map.of("promptVersion", request.promptVersion(), "attempts", attempt + 1)
                );

            } catch (Exception e) {
                allErrors.add("Exception: " + e.getMessage());
                log.warn("LLM decision attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        log.warn("All LLM decision attempts failed: {}", allErrors);
        return executeFallback(request, "LLM failed after " + maxRetries + " attempts: " + String.join("; ", allErrors));
    }

    private <T> String buildFullPrompt(LlmDecisionRequest<T> request) {
        StringBuilder sb = new StringBuilder();
        
        if (request.context() != null) {
            sb.append(request.context().toPromptContext());
            sb.append("\n---\n");
        }
        
        sb.append(request.userPrompt());
        
        return sb.toString();
    }

    private String buildFixPrompt(String brokenJson, List<String> errors) {
        return "以下JSON存在以下问题，请修复：\n" +
            "问题：\n" + String.join("\n", errors) + "\n\n" +
            "原始JSON：\n" + brokenJson + "\n\n" +
            "请输出修复后的完整JSON：";
    }

    private String extractJson(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        
        int bracketStart = trimmed.indexOf('[');
        int bracketEnd = trimmed.lastIndexOf(']');
        if (bracketStart >= 0 && bracketEnd > bracketStart) {
            return trimmed.substring(bracketStart, bracketEnd + 1);
        }
        
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T parseResponse(String json, Class<T> resultType) {
        try {
            if (resultType == String.class) {
                return (T) json;
            }
            
            if (resultType == Map.class) {
                return (T) objectMapper.readValue(json, Map.class);
            }
            
            return objectMapper.readValue(json, resultType);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON to {}: {}", resultType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> LlmDecisionResult<T> executeFallback(LlmDecisionRequest<T> request, String reason) {
        if (request.fallback() != null) {
            try {
                T fallbackResult = request.fallback().execute(request, reason);
                return LlmDecisionResult.fallback(fallbackResult, reason);
            } catch (Exception e) {
                log.error("Fallback execution failed: {}", e.getMessage());
                return LlmDecisionResult.error("FALLBACK_FAILED", e.getMessage());
            }
        }
        
        return LlmDecisionResult.error("NO_FALLBACK", reason);
    }

    private String callLlmDirect(ResolvedBrainModel model, String systemPrompt, String userPrompt) {
        try {
            ResolvedBrainModelProvider provider = new ResolvedBrainModelProvider(model);
            return provider.chatWithSystem(systemPrompt, userPrompt, null, 0.7)
                .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Direct LLM call failed for model {}: {}", model.getModelName(), e.getMessage());
            return null;
        }
    }
}
