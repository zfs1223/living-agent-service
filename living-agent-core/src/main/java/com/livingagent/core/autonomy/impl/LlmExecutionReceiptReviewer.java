package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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
    private final PerformanceStatsService performanceStatsService;
    private final KnowledgeBase knowledgeBase;
    private final CrossLoopEventBus crossLoopEventBus;

    public LlmExecutionReceiptReviewer(BrainRegistry brainRegistry) {
        this(brainRegistry, null, null, null);
    }

    public LlmExecutionReceiptReviewer(BrainRegistry brainRegistry, PerformanceStatsService performanceStatsService) {
        this(brainRegistry, performanceStatsService, null, null);
    }

    public LlmExecutionReceiptReviewer(BrainRegistry brainRegistry,
                                        PerformanceStatsService performanceStatsService,
                                        KnowledgeBase knowledgeBase,
                                        CrossLoopEventBus crossLoopEventBus) {
        this.brainRegistry = brainRegistry;
        this.fallbackReviewer = new DefaultExecutionReceiptReviewer();
        this.performanceStatsService = performanceStatsService;
        this.knowledgeBase = knowledgeBase;
        this.crossLoopEventBus = crossLoopEventBus;
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

        Optional<ReceiptReviewResult> result;
        if (mainBrain == null) {
            result = fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);
        } else {
            try {
                String userPrompt = buildReviewPrompt(receipt, assignment, acceptanceCriteria);
                String llmResponse = mainBrain.callLlm(REVIEW_SYSTEM_PROMPT, userPrompt);

                if (llmResponse == null || llmResponse.isBlank()) {
                    result = fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);
                } else {
                    result = parseReviewResponse(llmResponse, receipt, assignment, acceptanceCriteria);
                }
            } catch (Exception e) {
                log.warn("LLM receipt review failed: {}, using programmatic fallback", e.getMessage());
                result = fallbackReviewer.reviewReceipt(receipt, assignment, acceptanceCriteria);
            }
        }

        // P28-A: 审核结果→分派权重联动
        result.ifPresent(r -> adjustWeightFromReview(receipt, r));

        // P28-B: 审核结果→绩效经验沉淀
        result.ifPresent(r -> captureReceiptExperience(receipt, r));

        return result;
    }

    private void adjustWeightFromReview(EmployeeExecutionReceipt receipt, ReceiptReviewResult reviewResult) {
        if (performanceStatsService == null || receipt == null) return;
        String employeeCode = receipt.employeeCode();
        if (employeeCode == null) return;

        if (!reviewResult.accepted()) {
            ReceiptStatus status = receipt.status();
            if (status == ReceiptStatus.FAILED) {
                performanceStatsService.adjustWeight(employeeCode, -0.5);
                log.info("P28-A: Employee {} receipt FAILED, weight -0.5", employeeCode);
            } else if (status == ReceiptStatus.DEGRADED || status == ReceiptStatus.NEEDS_RETRY) {
                performanceStatsService.adjustWeight(employeeCode, -0.2);
                log.info("P28-A: Employee {} receipt needs rework, weight -0.2", employeeCode);
            }
        }
    }

    /**
     * P28-B: 审核结果→绩效经验沉淀。
     * 将审核结果写入KnowledgeBase（L3_SHARED），高频失败模式发布EvolutionSignal。
     */
    private void captureReceiptExperience(EmployeeExecutionReceipt receipt, ReceiptReviewResult reviewResult) {
        if (receipt == null) return;
        String employeeCode = receipt.employeeCode();

        // 经验沉淀到 KnowledgeBase
        if (knowledgeBase != null) {
            try {
                String key = "receipt-review:" + employeeCode + ":" + receipt.receiptId();
                Map<String, Object> experience = new HashMap<>();
                experience.put("employeeCode", employeeCode);
                experience.put("receiptId", receipt.receiptId());
                experience.put("accepted", reviewResult.accepted());
                experience.put("qualityScore", reviewResult.qualityScore());
                experience.put("unmetCriteria", reviewResult.unmetCriteria());
                experience.put("needsRetry", reviewResult.needsRetry());
                experience.put("timestamp", Instant.now().toString());
                experience.put("experienceType", "RECEIPT_REVIEW");

                Map<String, String> metadata = new HashMap<>();
                metadata.put("source", "LlmExecutionReceiptReviewer");
                metadata.put("category", "REVIEW_EXPERIENCE");
                metadata.put("accepted", String.valueOf(reviewResult.accepted()));

                knowledgeBase.store(key, experience, metadata);
                log.info("P28-B: Receipt review experience captured: employee={}, accepted={}", employeeCode, reviewResult.accepted());
            } catch (Exception e) {
                log.warn("P28-B: Failed to capture receipt experience: {}", e.getMessage());
            }
        }

        // 高频失败模式→发布EvolutionSignal(CAPABILITY_GAP)
        if (!reviewResult.accepted() && crossLoopEventBus != null) {
            try {
                EvolutionSignal signal = EvolutionSignal.capabilityGap(
                    "员工" + employeeCode + "回执审核未通过: " + reviewResult.reviewComment(),
                    "execution-review");
                signal.addMetadata("employeeCode", employeeCode);
                signal.addMetadata("qualityScore", reviewResult.qualityScore());
                signal.addMetadata("unmetCriteria", String.join(",", reviewResult.unmetCriteria()));

                crossLoopEventBus.publish(28, "receipt_rejected",
                    com.livingagent.core.evolution.orchestrator.CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("employeeCode", employeeCode,
                           "qualityScore", reviewResult.qualityScore(),
                           "accepted", false,
                           "unmetCriteria", String.join(",", reviewResult.unmetCriteria())),
                    300);
                log.info("P28-B: Capability gap signal published for employee={}", employeeCode);
            } catch (Exception e) {
                log.warn("P28-B: Failed to publish capability gap signal: {}", e.getMessage());
            }
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
