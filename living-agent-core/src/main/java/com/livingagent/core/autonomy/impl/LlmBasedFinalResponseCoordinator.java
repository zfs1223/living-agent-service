package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LlmBasedFinalResponseCoordinator implements FinalResponseCoordinator {

    private static final Logger log = LoggerFactory.getLogger(LlmBasedFinalResponseCoordinator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STRATEGY_SYSTEM_PROMPT = """
        你是企业主大脑，负责决定对用户消息的最佳回复策略。
        
        可选策略：
        - DIRECT_ANSWER: 简单咨询，部门大脑可直接回答
        - ASK_CLARIFICATION: 用户意图不明确，需要追问
        - MAIN_BRAIN_COMPOSE: 执行类任务，需要主脑汇总执行结果
        - WAIT_FOR_RECEIPTS: 任务已派发但尚未收到回执，需等待
        - DEPARTMENT_BRAIN_DIRECT: 部门内咨询，部门大脑直接回复
        - ESCALATE_TO_HUMAN: 高风险或超出系统处理能力，需人工介入
        - REQUEST_APPROVAL: 涉及审批流程，需走审批链路
        
        你必须只输出一个合法的JSON对象：
        {"strategy": "STRATEGY_NAME", "reason": "选择该策略的原因"}
        """;

    private final BrainRegistry brainRegistry;
    private final DefaultFinalResponseCoordinator fallbackCoordinator;
    private final AutonomyTraceService traceService;

    public LlmBasedFinalResponseCoordinator(
            BrainRegistry brainRegistry,
            AutonomyTraceService traceService) {
        this.brainRegistry = brainRegistry;
        this.fallbackCoordinator = new DefaultFinalResponseCoordinator();
        this.traceService = traceService;
    }

    @Override
    public FinalResponseStrategy determineStrategy(
            String requestId,
            String department,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentExecutionResult executionResult) {

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            return fallbackWithTrace(
                fallbackCoordinator.determineStrategy(requestId, department, decision, routingDecision, mainBrainTaskPlan, executionResult),
                requestId, "MainBrain unavailable, using rule-based fallback");
        }

        try {
            String userPrompt = buildStrategyPrompt(department, decision, routingDecision, mainBrainTaskPlan, executionResult);
            String llmResponse = mainBrain.callLlm(STRATEGY_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                return fallbackWithTrace(
                    fallbackCoordinator.determineStrategy(requestId, department, decision, routingDecision, mainBrainTaskPlan, executionResult),
                    requestId, "LLM returned empty, using rule-based fallback");
            }

            Map<String, Object> parsed = parseJson(llmResponse);
            if (parsed == null || parsed.get("strategy") == null) {
                return fallbackWithTrace(
                    fallbackCoordinator.determineStrategy(requestId, department, decision, routingDecision, mainBrainTaskPlan, executionResult),
                    requestId, "LLM response parse failed, using rule-based fallback");
            }

            String strategyName = (String) parsed.get("strategy");
            String reason = (String) parsed.getOrDefault("reason", "");

            try {
                FinalResponseStrategy strategy = FinalResponseStrategy.valueOf(strategyName);
                log.info("LLM determined response strategy: {} for requestId={}, reason={}", strategy, requestId, reason);
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "response_strategy_determined", "LlmBasedFinalResponseCoordinator",
                    "LLM-driven response strategy: " + strategy,
                    Map.of("strategy", strategyName, "reason", reason, "coordinator_type", "llm_based")
                ));
                return strategy;
            } catch (IllegalArgumentException e) {
                log.warn("LLM returned unknown strategy: {}, using fallback", strategyName);
                return fallbackWithTrace(
                    fallbackCoordinator.determineStrategy(requestId, department, decision, routingDecision, mainBrainTaskPlan, executionResult),
                    requestId, "LLM returned unknown strategy: " + strategyName);
            }

        } catch (Exception e) {
            log.warn("LLM response strategy determination failed: {}, using fallback", e.getMessage());
            return fallbackWithTrace(
                fallbackCoordinator.determineStrategy(requestId, department, decision, routingDecision, mainBrainTaskPlan, executionResult),
                requestId, "LLM call failed: " + e.getMessage());
        }
    }

    private String buildStrategyPrompt(
            String department,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentExecutionResult executionResult) {

        StringBuilder sb = new StringBuilder();
        sb.append("部门: ").append(department != null ? department : "未知").append("\n");
        sb.append("消息类型: ").append(decision.kind()).append("\n");
        sb.append("需要任务执行: ").append(decision.requiresTaskExecution()).append("\n");
        sb.append("风险等级: ").append(decision.riskLevel()).append("\n");

        if (routingDecision != null) {
            sb.append("路由大脑: ").append(routingDecision.primaryBrainId()).append("\n");
        }

        if (mainBrainTaskPlan != null) {
            sb.append("任务类型: ").append(mainBrainTaskPlan.taskType()).append("\n");
            sb.append("任务目标: ").append(mainBrainTaskPlan.goal()).append("\n");
        }

        if (executionResult != null) {
            sb.append("执行状态: ").append(executionResult.status()).append("\n");
            sb.append("已分配员工数: ").append(executionResult.dispatchedAssignments() != null ? executionResult.dispatchedAssignments().size() : 0).append("\n");
        }

        sb.append("\n请决定最佳回复策略。");
        return sb.toString();
    }

    private FinalResponseStrategy fallbackWithTrace(
            FinalResponseStrategy strategy, String requestId, String reason) {
        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "response_strategy_determined", "LlmBasedFinalResponseCoordinator",
            reason,
            Map.of("strategy", strategy.name(), "coordinator_type", "rule_based_fallback")
        ));
        return strategy;
    }

    private Map<String, Object> parseJson(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(
                    trimmed.substring(braceStart, braceEnd + 1), Map.class);
                return parsed;
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        return null;
    }
}
