package com.livingagent.core.proactive.llm.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.proactive.llm.LlmRiskAssessor;
import com.livingagent.core.proactive.predictor.RiskPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LlmRiskAssessorImpl implements LlmRiskAssessor {

    private static final Logger log = LoggerFactory.getLogger(LlmRiskAssessorImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String RISK_SYSTEM_PROMPT = """
        你是企业主大脑，负责根据业务指标和上下文进行风险评估。
        
        要求：
        1. 不仅关注数值指标，还要理解业务语义
        2. 评估影响范围和潜在损失
        3. 给出可执行的缓解方案
        4. 判断是否需要审批或人工介入
        5. 如果风险可控，返回LOW级别
        
        输出JSON：
        {"level": "HIGH", "evidence": "证据描述", "impactScope": "影响范围", "recommendedAction": "建议动作", "requiresApproval": false, "requiresHumanIntervention": true, "confidence": 0.85}
        
        level可选：LOW, MEDIUM, HIGH, CRITICAL
        """;

    private final BrainRegistry brainRegistry;
    private final RiskPredictor riskPredictor;

    public LlmRiskAssessorImpl(BrainRegistry brainRegistry, RiskPredictor riskPredictor) {
        this.brainRegistry = brainRegistry;
        this.riskPredictor = riskPredictor;
    }

    @Override
    public Optional<RiskAssessmentResult> assessRisk(String domain, Map<String, Object> indicators) {
        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.debug("MainBrain unavailable for risk assessment");
            return Optional.empty();
        }

        try {
            String userPrompt = buildRiskPrompt(domain, indicators);
            String llmResponse = mainBrain.callLlm(RISK_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                return Optional.empty();
            }

            return parseRiskAssessment(llmResponse, domain);

        } catch (Exception e) {
            log.warn("LLM risk assessment failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildRiskPrompt(String domain, Map<String, Object> indicators) {
        StringBuilder sb = new StringBuilder();
        sb.append("评估领域: ").append(domain != null ? domain : "综合").append("\n");

        var activeAlerts = riskPredictor.getActiveAlerts();
        if (!activeAlerts.isEmpty()) {
            sb.append("当前活跃告警:\n");
            for (var alert : activeAlerts) {
                sb.append("- ").append(alert.indicatorName())
                  .append(" (级别: ").append(alert.level()).append(")\n");
            }
        }

        if (indicators != null && !indicators.isEmpty()) {
            sb.append("指标数据:\n");
            indicators.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        sb.append("\n请进行风险评估。");
        return sb.toString();
    }

    private Optional<RiskAssessmentResult> parseRiskAssessment(String llmResponse, String domain) {
        String json = extractJson(llmResponse);
        if (json == null) return Optional.empty();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            String levelStr = (String) parsed.getOrDefault("level", "LOW");
            RiskLevel level;
            try {
                level = RiskLevel.valueOf(levelStr);
            } catch (IllegalArgumentException e) {
                level = RiskLevel.LOW;
            }

            return Optional.of(new RiskAssessmentResult(
                UUID.randomUUID().toString(),
                domain,
                level,
                (String) parsed.getOrDefault("evidence", ""),
                (String) parsed.getOrDefault("impactScope", ""),
                (String) parsed.getOrDefault("recommendedAction", ""),
                Boolean.TRUE.equals(parsed.get("requiresApproval")),
                Boolean.TRUE.equals(parsed.get("requiresHumanIntervention")),
                parsed.get("confidence") instanceof Number n ? n.doubleValue() : 0.5,
                "llm_based"
            ));

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM risk assessment: {}", e.getMessage());
            return Optional.empty();
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
