package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.RequirementReadinessEvaluator;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.provider.impl.ResolvedBrainModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LlmRequirementReadinessEvaluator implements RequirementReadinessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmRequirementReadinessEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BrainModelResolver brainModelResolver;
    private final String brainId;

    private static final String SYSTEM_PROMPT = """
        你是一个需求就绪评估器。你的任务是判断用户的需求是否足够明确，可以进入任务规划和执行阶段。

        评估标准：
        1. SUFFICIENT：需求明确，可以直接执行。包括但不限于：
           - 明确的动作请求（开发/分析/总结/检查/修复/设计/部署等）
           - 咨询性问题（"什么是..."、"怎么理解..."）→ 也算 SUFFICIENT，可以直接回答
           - 任何有意义的用户输入，只要能理解用户意图

        2. PARTIALLY_SUFFICIENT：需求基本明确但缺少一些细节，可以边执行边澄清

        3. INSUFFICIENT：需求完全不明确，无法理解用户想要什么。只有以下情况：
           - 空消息或无意义输入
           - 完全模糊无法推断意图

        重要原则：
        - 宁可放行也不要过度要求澄清，用户可以在后续对话中补充细节
        - 大部分用户输入都是 SUFFICIENT 或 PARTIALLY_SUFFICIENT
        - 只有真正无法理解的输入才是 INSUFFICIENT

        请以JSON格式返回评估结果：
        {
          "level": "SUFFICIENT" | "PARTIALLY_SUFFICIENT" | "INSUFFICIENT",
          "confidence": 0.0-1.0,
          "missing_elements": ["缺少的要素"],
          "clarification_questions": ["建议的澄清问题"],
          "reason": "评估理由"
        }
        """;

    public LlmRequirementReadinessEvaluator(BrainModelResolver brainModelResolver, String brainId) {
        this.brainModelResolver = brainModelResolver;
        this.brainId = brainId;
    }

    @Override
    public RequirementReadinessResult evaluate(String userMessage, String department, String sessionId) {
        if (userMessage == null || userMessage.isBlank()) {
            return RequirementReadinessResult.insufficient(
                List.of("请描述您需要完成的任务"),
                "Empty message"
            );
        }

        if (userMessage.trim().length() < 3) {
            return RequirementReadinessResult.insufficient(
                List.of("请提供更详细的任务描述"),
                "Message too short"
            );
        }

        try {
            ResolvedBrainModel model = resolveModel();
            if (model == null) {
                log.warn("No model available for LLM readiness evaluation, falling back to default (SUFFICIENT)");
                return RequirementReadinessResult.sufficient(0.8, "No model available, defaulting to sufficient");
            }

            String llmResponse = callLlm(model, userMessage, department);
            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM readiness evaluation returned empty, defaulting to SUFFICIENT");
                return RequirementReadinessResult.sufficient(0.8, "LLM returned empty, defaulting to sufficient");
            }

            return parseLlmResponse(llmResponse);
        } catch (Exception e) {
            log.warn("LLM readiness evaluation failed: {}, defaulting to SUFFICIENT", e.getMessage());
            return RequirementReadinessResult.sufficient(0.8, "LLM evaluation failed, defaulting to sufficient");
        }
    }

    private ResolvedBrainModel resolveModel() {
        try {
            ResolvedBrainModel model = brainModelResolver.resolve(brainId);
            if (model != null) return model;
            return brainModelResolver.resolveDefault(brainId);
        } catch (Exception e) {
            log.warn("Failed to resolve model for readiness evaluation: {}", e.getMessage());
            return null;
        }
    }

    private String callLlm(ResolvedBrainModel model, String userMessage, String department) {
        try {
            Provider provider = new ResolvedBrainModelProvider(model);
            List<Provider.ChatMessage> messages = List.of(
                Provider.ChatMessage.system(SYSTEM_PROMPT),
                Provider.ChatMessage.user("部门: " + (department != null ? department : "未知") + "\n用户消息: " + userMessage)
            );
            Provider.ChatRequest request = new Provider.ChatRequest(messages, List.of(), model.getModelName(), 0.1, 500);
            Provider.ChatResponse response = provider.chat(request).join();
            return response.content();
        } catch (Exception e) {
            log.warn("LLM call for readiness evaluation failed: {}", e.getMessage());
            return null;
        }
    }

    private RequirementReadinessResult parseLlmResponse(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            JsonNode node = MAPPER.readTree(json);

            String levelStr = node.has("level") ? node.get("level").asText() : "SUFFICIENT";
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.8;
            List<String> missingElements = parseStringArray(node, "missing_elements");
            List<String> clarificationQuestions = parseStringArray(node, "clarification_questions");
            String reason = node.has("reason") ? node.get("reason").asText() : "LLM evaluated";

            ReadinessLevel level = switch (levelStr.toUpperCase()) {
                case "SUFFICIENT" -> ReadinessLevel.SUFFICIENT;
                case "PARTIALLY_SUFFICIENT" -> ReadinessLevel.PARTIALLY_SUFFICIENT;
                case "INSUFFICIENT" -> ReadinessLevel.INSUFFICIENT;
                default -> ReadinessLevel.SUFFICIENT;
            };

            log.info("LLM readiness evaluation: level={}, confidence={}, reason={}", level, String.format("%.2f", confidence), reason);

            return new RequirementReadinessResult(level, confidence, missingElements, clarificationQuestions, reason);
        } catch (Exception e) {
            log.warn("Failed to parse LLM readiness response: {}, defaulting to SUFFICIENT", e.getMessage());
            return RequirementReadinessResult.sufficient(0.8, "Parse failed, defaulting to sufficient");
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private List<String> parseStringArray(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || !node.get(fieldName).isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node.get(fieldName)) {
            result.add(item.asText());
        }
        return result;
    }
}
