package com.livingagent.core.proactive.llm.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.proactive.llm.LlmProactiveAdvisor;
import com.livingagent.core.proactive.predictor.PatternPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class LlmProactiveAdvisorImpl implements LlmProactiveAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LlmProactiveAdvisorImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SUGGESTION_SYSTEM_PROMPT = """
        你是企业主大脑，负责根据用户行为模式和业务上下文生成主动建议。
        
        要求：
        1. 建议必须基于具体的业务上下文，不是泛泛而谈
        2. 说明触发原因、预期收益、潜在风险
        3. 给出可执行的具体动作
        4. 判断是否需要用户确认
        5. 如果没有值得建议的内容，返回空数组
        
        输出JSON：
        {"suggestions": [{"category": "效率优化", "title": "标题", "description": "描述", "triggerReason": "触发原因", "expectedBenefit": "预期收益", "riskNote": "风险提示", "recommendedActions": ["动作1", "动作2"], "requiresUserConfirmation": true, "confidence": 0.8}]}
        """;

    private final BrainRegistry brainRegistry;
    private final PatternPredictor patternPredictor;

    public LlmProactiveAdvisorImpl(BrainRegistry brainRegistry, PatternPredictor patternPredictor) {
        this.brainRegistry = brainRegistry;
        this.patternPredictor = patternPredictor;
    }

    @Override
    public Optional<ProactiveSuggestion> generateSuggestion(String userId, Map<String, Object> context) {
        List<ProactiveSuggestion> suggestions = generateSuggestions(userId, context, 1);
        return suggestions.isEmpty() ? Optional.empty() : Optional.of(suggestions.get(0));
    }

    @Override
    public List<ProactiveSuggestion> generateSuggestions(String userId, Map<String, Object> context, int maxCount) {
        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.debug("MainBrain unavailable for proactive suggestions");
            return List.of();
        }

        try {
            String userPrompt = buildSuggestionPrompt(userId, context);
            String llmResponse = mainBrain.callLlm(SUGGESTION_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                return List.of();
            }

            return parseSuggestions(llmResponse, userId, maxCount);

        } catch (Exception e) {
            log.warn("LLM proactive suggestion generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSuggestionPrompt(String userId, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户ID: ").append(userId).append("\n");

        var patterns = patternPredictor.getUserPatterns(userId);
        if (!patterns.isEmpty()) {
            sb.append("用户行为模式:\n");
            for (var pattern : patterns) {
                sb.append("- 类型: ").append(pattern.patternType())
                  .append(", 动作: ").append(pattern.actionType())
                  .append(" (置信度: ").append(String.format("%.2f", pattern.confidence())).append(")\n");
            }
        }

        if (context != null && !context.isEmpty()) {
            sb.append("业务上下文:\n");
            context.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        sb.append("\n请生成主动建议。");
        return sb.toString();
    }

    private List<ProactiveSuggestion> parseSuggestions(String llmResponse, String userId, int maxCount) {
        String json = extractJson(llmResponse);
        if (json == null) return List.of();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            Object suggestionsObj = parsed.get("suggestions");
            if (!(suggestionsObj instanceof List<?> list)) return List.of();

            List<ProactiveSuggestion> result = new ArrayList<>();
            int count = 0;
            for (Object item : list) {
                if (count >= maxCount) break;
                if (!(item instanceof Map<?, ?> map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) map;

                result.add(new ProactiveSuggestion(
                    UUID.randomUUID().toString(),
                    userId,
                    (String) m.getOrDefault("category", "general"),
                    (String) m.getOrDefault("title", ""),
                    (String) m.getOrDefault("description", ""),
                    (String) m.getOrDefault("triggerReason", ""),
                    (String) m.getOrDefault("expectedBenefit", ""),
                    (String) m.getOrDefault("riskNote", ""),
                    m.get("recommendedActions") instanceof List<?> actions
                        ? actions.stream().map(Object::toString).toList() : List.of(),
                    Boolean.TRUE.equals(m.get("requiresUserConfirmation")),
                    m.get("confidence") instanceof Number n ? n.doubleValue() : 0.5,
                    "llm_based"
                ));
                count++;
            }
            return result;

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM suggestion response: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractJson(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) return trimmed.substring(braceStart, braceEnd + 1);
        return null;
    }
}
