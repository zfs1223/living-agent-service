package com.livingagent.core.autonomy.llm;

import com.livingagent.core.autonomy.context.DecisionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LlmDecisionClient {
    
    <T> LlmDecisionResult<T> decide(
        LlmDecisionRequest<T> request
    );
    
    <T> LlmDecisionResult<T> decideWithRetry(
        LlmDecisionRequest<T> request,
        int maxRetries
    );
    
    record LlmDecisionRequest<T>(
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        DecisionContext context,
        JsonSchema schema,
        Class<T> resultType,
        LlmFallback<T> fallback
    ) {
        public static <T> LlmDecisionRequest<T> of(
            String systemPrompt, String userPrompt, JsonSchema schema, Class<T> resultType) {
            return new LlmDecisionRequest<>(
                "v1", systemPrompt, userPrompt, null, schema, resultType, null
            );
        }
        
        public static <T> LlmDecisionRequest<T> of(
            String promptVersion, String systemPrompt, String userPrompt,
            DecisionContext context, JsonSchema schema, Class<T> resultType, LlmFallback<T> fallback) {
            return new LlmDecisionRequest<>(promptVersion, systemPrompt, userPrompt, context, schema, resultType, fallback);
        }
    }
    
    record LlmDecisionResult<T>(
        boolean success,
        T data,
        String rawResponse,
        String error,
        String errorDescription,
        String decisionSource,
        String modelUsed,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        List<String> schemaValidationErrors,
        String fallbackReason,
        Map<String, Object> metadata
    ) {
        public static <T> LlmDecisionResult<T> ok(T data, String rawResponse, String modelUsed) {
            return new LlmDecisionResult<>(
                true, data, rawResponse, null, null, "llm_based", modelUsed,
                0, 0, 0, List.of(), null, Map.of()
            );
        }
        
        public static <T> LlmDecisionResult<T> fallback(T data, String reason) {
            return new LlmDecisionResult<>(
                true, data, null, null, null, "rule_based_fallback", null,
                0, 0, 0, List.of(), reason, Map.of()
            );
        }
        
        public static <T> LlmDecisionResult<T> error(String error, String description) {
            return new LlmDecisionResult<>(
                false, null, null, error, description, "error", null,
                0, 0, 0, List.of(), null, Map.of()
            );
        }
    }
    
    interface LlmFallback<T> {
        T execute(LlmDecisionRequest<T> request, String failureReason);
    }
    
    interface JsonSchema {
        String name();
        Map<String, Object> schema();
        List<String> requiredFields();
        boolean validate(Object data);
        List<String> validateWithErrors(Object data);
        
        static JsonSchema of(String name, Map<String, Object> schema) {
            return new SimpleJsonSchema(name, schema);
        }
    }
    
    record SimpleJsonSchema(String name, Map<String, Object> schema) implements JsonSchema {
        @Override
        public List<String> requiredFields() {
            Object required = schema.get("required");
            if (required instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
            return List.of();
        }
        
        @Override
        public boolean validate(Object data) {
            return validateWithErrors(data).isEmpty();
        }
        
        @Override
        public List<String> validateWithErrors(Object data) {
            List<String> errors = new java.util.ArrayList<>();
            if (data == null) {
                errors.add("data is null");
                return errors;
            }
            
            if (!(data instanceof Map)) {
                errors.add("data is not a map/object");
                return errors;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            
            for (String field : requiredFields()) {
                if (!map.containsKey(field)) {
                    errors.add("missing required field: " + field);
                }
            }
            
            return errors;
        }
    }
}
