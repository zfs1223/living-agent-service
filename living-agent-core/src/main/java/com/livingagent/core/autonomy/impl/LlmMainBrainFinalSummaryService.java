package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.ExecutionResultAggregator.AggregationResult;
import com.livingagent.core.autonomy.MainBrainFinalSummaryService.FinalSummaryResult;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 驱动的主脑最终总结服务
 * 调用 MainBrain LLM 生成结构化总结，LLM 不可用时降级到 DefaultMainBrainFinalSummaryService
 */
public class LlmMainBrainFinalSummaryService implements MainBrainFinalSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LlmMainBrainFinalSummaryService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Jackson 反序列化用的内部 record，对应 LLM 返回的 JSON 结构
     */
    private record SummaryJson(
        String status,
        String userMessage,
        List<String> deliverables,
        String acceptanceConclusion,
        List<String> risks,
        List<String> nextActions,
        boolean requiresHumanReview
    ) {}

    private final MainBrain mainBrain;
    private final MainBrainFinalSummaryService fallbackService;

    public LlmMainBrainFinalSummaryService(MainBrain mainBrain, MainBrainFinalSummaryService fallbackService) {
        this.mainBrain = mainBrain;
        this.fallbackService = fallbackService;
    }

    @Override
    public FinalSummaryResult generateSummary(
            String originalMessage,
            DialogueDecision dialogueDecision,
            MainBrainTaskPlan taskPlan,
            DepartmentExecutionResult executionResult,
            AggregationResult receiptSummary,
            List<ArtifactRecord> artifactRecords,
            String completionGateResult,
            List<String> riskAndLimitations,
            List<String> nextActionCandidates) {
        
        try {
            String prompt = buildSummaryPrompt(
                originalMessage, dialogueDecision, taskPlan, executionResult,
                receiptSummary, artifactRecords, completionGateResult,
                riskAndLimitations, nextActionCandidates
            );

            String llmResponse = mainBrain.callLlm(prompt, "main_brain_final_summary");
            
            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM returned empty response, using fallback");
                return fallbackService.generateSummary(
                    originalMessage, dialogueDecision, taskPlan, executionResult,
                    receiptSummary, artifactRecords, completionGateResult,
                    riskAndLimitations, nextActionCandidates
                );
            }

            FinalSummaryResult result = parseLlmResponse(llmResponse);
            log.info("MainBrain final summary generated from LLM: status={}, summary_source=llm_main_brain", result.status());
            
            return new FinalSummaryResult(
                result.status(),
                result.userMessage(),
                result.deliverables(),
                result.acceptanceConclusion(),
                result.risks(),
                result.nextActions(),
                result.requiresHumanReview(),
                "llm_main_brain"
            );
            
        } catch (Exception e) {
            log.warn("LLM-based summary failed, using fallback: {}", e.getMessage());
            return fallbackService.generateSummary(
                originalMessage, dialogueDecision, taskPlan, executionResult,
                receiptSummary, artifactRecords, completionGateResult,
                riskAndLimitations, nextActionCandidates
            );
        }
    }

    private String buildSummaryPrompt(
            String originalMessage,
            DialogueDecision dialogueDecision,
            MainBrainTaskPlan taskPlan,
            DepartmentExecutionResult executionResult,
            AggregationResult receiptSummary,
            List<ArtifactRecord> artifactRecords,
            String completionGateResult,
            List<String> riskAndLimitations,
            List<String> nextActionCandidates) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("作为主脑，请根据以下执行结果生成最终总结回复：\n\n");
        
        prompt.append("用户原始请求：").append(originalMessage).append("\n\n");
        
        if (taskPlan != null) {
            prompt.append("任务计划：\n");
            prompt.append("- 任务类型：").append(taskPlan.taskType()).append("\n");
            prompt.append("- 主责部门：").append(taskPlan.primaryDepartment()).append("\n");
            prompt.append("- 交付物：").append(taskPlan.deliverables()).append("\n");
            prompt.append("- 验收标准：").append(taskPlan.acceptanceCriteria()).append("\n\n");
        }
        
        if (executionResult != null) {
            prompt.append("执行结果：\n");
            prompt.append("- 执行ID：").append(executionResult.executionId()).append("\n");
            prompt.append("- 派发数量：").append(executionResult.dispatchedAssignments().size()).append("\n");
            prompt.append("- 完成回执数：").append(receiptSummary.completedCount()).append("\n");
            prompt.append("- 失败回执数：").append(receiptSummary.failedCount()).append("\n\n");
        }
        
        if (artifactRecords != null && !artifactRecords.isEmpty()) {
            prompt.append("产物记录：\n");
            artifactRecords.forEach(a -> prompt.append("- ").append(a.name()).append(" (").append(a.path()).append(")\n"));
            prompt.append("\n");
        }
        
        prompt.append("完成闸门：").append(completionGateResult).append("\n\n");
        
        if (riskAndLimitations != null && !riskAndLimitations.isEmpty()) {
            prompt.append("风险和限制：\n");
            riskAndLimitations.forEach(r -> prompt.append("- ").append(r).append("\n"));
            prompt.append("\n");
        }
        
        prompt.append("请以结构化 JSON 格式返回总结，包含以下字段：\n");
        prompt.append("{\n");
        prompt.append("  \"status\": \"COMPLETED|PARTIAL|WAITING|FAILED\",\n");
        prompt.append("  \"userMessage\": \"最终回复正文\",\n");
        prompt.append("  \"deliverables\": [产物列表],\n");
        prompt.append("  \"acceptanceConclusion\": \"验收结论\",\n");
        prompt.append("  \"risks\": [\"风险列表\"],\n");
        prompt.append("  \"nextActions\": [\"下一步建议\"],\n");
        prompt.append("  \"requiresHumanReview\": false\n");
        prompt.append("}");
        
        return prompt.toString();
    }

    private FinalSummaryResult parseLlmResponse(String llmResponse) {
        // 提取 JSON block
        String json = extractJson(llmResponse);

        // 优先使用 Jackson 解析
        try {
            SummaryJson summary = OBJECT_MAPPER.readValue(json, SummaryJson.class);
            return new FinalSummaryResult(
                summary.status() != null ? summary.status() : "COMPLETED",
                summary.userMessage() != null ? summary.userMessage() : "任务已完成",
                summary.deliverables() != null
                    ? summary.deliverables().stream().map(d -> Map.<String, Object>of("name", d)).collect(Collectors.toList())
                    : List.of(),
                summary.acceptanceConclusion() != null ? summary.acceptanceConclusion() : "验收通过",
                summary.risks() != null ? summary.risks() : List.of(),
                summary.nextActions() != null ? summary.nextActions() : List.of(),
                summary.requiresHumanReview(),
                "llm_main_brain"
            );
        } catch (Exception e) {
            log.warn("Jackson parsing failed, falling back to string matching: {}", e.getMessage());
        }

        // 降级：原有字符串匹配方式
        try {
            String status = extractField(json, "status");
            String userMessage = extractField(json, "userMessage");
            String acceptanceConclusion = extractField(json, "acceptanceConclusion");
            boolean requiresHumanReview = json.contains("\"requiresHumanReview\": true");

            return new FinalSummaryResult(
                status != null ? status : "COMPLETED",
                userMessage != null ? userMessage : "任务已完成",
                List.of(),
                acceptanceConclusion != null ? acceptanceConclusion : "验收通过",
                List.of(),
                List.of(),
                requiresHumanReview,
                "llm_main_brain"
            );
        } catch (Exception e) {
            log.warn("Fallback string matching also failed, returning default: {}", e.getMessage());
            return new FinalSummaryResult(
                "COMPLETED",
                "任务已完成",
                List.of(),
                "验收通过",
                List.of(),
                List.of(),
                false,
                "llm_main_brain"
            );
        }
    }

    private String extractJson(String response) {
        int jsonStart = response.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = jsonStart + 7;
            int jsonEnd = response.indexOf("```", contentStart);
            if (jsonEnd >= 0) {
                return response.substring(contentStart, jsonEnd).trim();
            }
        }
        return response;
    }

    private String extractField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
