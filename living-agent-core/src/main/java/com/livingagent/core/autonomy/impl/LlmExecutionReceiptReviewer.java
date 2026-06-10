package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LlmExecutionReceiptReviewer implements ExecutionReceiptReviewer {

    private static final Logger log = LoggerFactory.getLogger(LlmExecutionReceiptReviewer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REVIEW_SYSTEM_PROMPT = """
        你是企业主大脑，负责审核数字员工的执行回执是否满足验收标准。
        
        你需要：
        1. 对比回执内容与验收标准
        2. 评估执行质量（0-1分）
        3. 列出未满足的验收标准
        4. 判断是否需要重试
        5. 如果需要重试，给出具体建议
        
        你必须只输出一个合法的JSON对象：
        {"accepted": true, "qualityScore": 0.9, "reviewComment": "审核意见", "unmetCriteria": [], "needsRetry": false, "retrySuggestion": null}
        """;

    private final BrainRegistry brainRegistry;
    private final DefaultExecutionReceiptReviewer fallbackReviewer;

    public LlmExecutionReceiptReviewer(BrainRegistry brainRegistry) {
        this.brainRegistry = brainRegistry;
        this.fallbackReviewer = new DefaultExecutionReceiptReviewer();
    }

    @Override
    public Optional<ReceiptReviewResult> reviewReceipt(
            EmployeeExecutionReceipt receipt,
            EmployeeWorkAssignment assignment,
            List<String> acceptanceCriteria) {

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            return fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);
        }

        try {
            String userPrompt = buildReviewPrompt(receipt, assignment, acceptanceCriteria);
            String llmResponse = mainBrain.callLlm(REVIEW_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                return fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);
            }

            return parseReviewResponse(llmResponse, receipt, assignment, acceptanceCriteria);

        } catch (Exception e) {
            log.warn("LLM receipt review failed: {}, using programmatic fallback", e.getMessage());
            return fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);
        }
    }

    private String buildReviewPrompt(
            EmployeeExecutionReceipt receipt,
            EmployeeWorkAssignment assignment,
            List<String> acceptanceCriteria) {
        StringBuilder sb = new StringBuilder();
        sb.append("员工: ").append(receipt.employeeCode()).append("\n");
        sb.append("执行角色: ").append(assignment != null ? assignment.role() : "未知").append("\n");
        sb.append("执行状态: ").append(receipt.status() != null ? receipt.status().getCode() : "null").append("\n");
        sb.append("执行摘要: ").append(receipt.summary() != null ? receipt.summary() : "无").append("\n");
        sb.append("验收标准:\n");
        for (String criteria : acceptanceCriteria) {
            sb.append("- ").append(criteria).append("\n");
        }
        sb.append("\n请审核该回执是否满足验收标准。");
        return sb.toString();
    }

    private Optional<ReceiptReviewResult> parseReviewResponse(String llmResponse, EmployeeExecutionReceipt receipt,
            EmployeeWorkAssignment assignment, List<String> acceptanceCriteria) {
        String json = extractJson(llmResponse);
        if (json == null) return fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            boolean accepted = Boolean.TRUE.equals(parsed.getOrDefault("accepted", true));
            double qualityScore = parsed.get("qualityScore") instanceof Number n ? n.doubleValue() : 0.8;
            String reviewComment = (String) parsed.getOrDefault("reviewComment", "");
            List<String> unmetCriteria = parsed.get("unmetCriteria") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();
            boolean needsRetry = Boolean.TRUE.equals(parsed.getOrDefault("needsRetry", false));
            String retrySuggestion = (String) parsed.getOrDefault("retrySuggestion", null);

            return Optional.of(new ReceiptReviewResult(
                receipt.receiptId(),
                accepted,
                qualityScore,
                reviewComment,
                unmetCriteria,
                needsRetry,
                retrySuggestion
            ));

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM review response: {}", e.getMessage());
            return fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);
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
