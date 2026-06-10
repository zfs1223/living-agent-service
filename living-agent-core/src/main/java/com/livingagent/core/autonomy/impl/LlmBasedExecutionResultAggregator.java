package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.ExecutionReceiptReviewer.ReceiptReviewResult;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LlmBasedExecutionResultAggregator implements ExecutionResultAggregator {

    private static final Logger log = LoggerFactory.getLogger(LlmBasedExecutionResultAggregator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String AGGREGATE_SYSTEM_PROMPT = """
        你是企业主大脑，负责汇总数字员工的执行结果，生成结构化的执行摘要。
        
        你需要：
        1. 统计完成/失败/降级/部分完成的员工数
        2. 评估整体执行质量
        3. 列出产出的交付物
        4. 标记未满足的验收标准
        5. 检测不同员工结果之间的冲突
        6. 判断是否需要重试、返工或人工介入
        7. 给出整体评价和后续建议
        
        你必须只输出一个合法的JSON对象：
        {
          "overallStatus": "COMPLETED",
          "qualityAssessment": "整体质量评价",
          "artifactsProduced": ["产物1", "产物2"],
          "unmetCriteria": [],
          "conflicts": [],
          "retryAssignments": [],
          "needsHumanIntervention": false,
          "recommendation": "后续建议",
          "needsFollowUp": false
        }
        
        overallStatus 可选：COMPLETED / PARTIALLY_COMPLETED / DEGRADED / FAILED
        当存在降级(DEGRADED)回执时，overallStatus 必须为 DEGRADED 或 FAILED，不能为 COMPLETED。
        当存在未满足的验收标准时，overallStatus 不能为 COMPLETED。
        """;

    private final BrainRegistry brainRegistry;
    private final ExecutionReceiptReviewer receiptReviewer;
    private final DefaultExecutionResultAggregator fallbackAggregator;

    public LlmBasedExecutionResultAggregator(BrainRegistry brainRegistry, ExecutionReceiptReviewer receiptReviewer) {
        this.brainRegistry = brainRegistry;
        this.receiptReviewer = receiptReviewer;
        this.fallbackAggregator = new DefaultExecutionResultAggregator();
    }

    @Override
    public String aggregate(
            String executionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            String brainRawResponse) {
        AggregationResult result = aggregateStructured(executionId, department, mainBrainTaskPlan, receipts, brainRawResponse);
        return result.summaryForUser();
    }

    @Override
    public AggregationResult aggregateStructured(
            String executionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            String brainRawResponse) {

        List<ReceiptReviewResult> reviews = new ArrayList<>();
        for (EmployeeExecutionReceipt receipt : receipts) {
            receiptReviewer.reviewReceipt(receipt, null,
                mainBrainTaskPlan != null ? mainBrainTaskPlan.acceptanceCriteria() : List.of())
                .ifPresent(reviews::add);
        }

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.debug("MainBrain unavailable for result aggregation, using template fallback");
            return buildFallbackAggregationResult(executionId, department, mainBrainTaskPlan, receipts, reviews, brainRawResponse);
        }

        try {
            String userPrompt = buildAggregatePrompt(department, mainBrainTaskPlan, receipts, reviews);
            String llmResponse = mainBrain.callLlm(AGGREGATE_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                return buildFallbackAggregationResult(executionId, department, mainBrainTaskPlan, receipts, reviews, brainRawResponse);
            }

            Map<String, Object> parsed = parseJson(llmResponse);
            if (parsed == null) {
                return buildFallbackAggregationResult(executionId, department, mainBrainTaskPlan, receipts, reviews, brainRawResponse);
            }

            return buildStructuredAggregationResult(executionId, department, mainBrainTaskPlan, receipts, reviews, parsed, brainRawResponse);

        } catch (Exception e) {
            log.warn("LLM result aggregation failed: {}, using template fallback", e.getMessage());
            return buildFallbackAggregationResult(executionId, department, mainBrainTaskPlan, receipts, reviews, brainRawResponse);
        }
    }

    private AggregationResult buildStructuredAggregationResult(
            String executionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            List<ReceiptReviewResult> reviews,
            Map<String, Object> parsed,
            String brainRawResponse) {

        String llmOverallStatus = (String) parsed.getOrDefault("overallStatus", "COMPLETED");
        String qualityAssessment = (String) parsed.getOrDefault("qualityAssessment", "");
        String recommendation = (String) parsed.getOrDefault("recommendation", "");

        List<String> artifactsProduced = parsed.get("artifactsProduced") instanceof List<?> list
            ? list.stream().map(Object::toString).toList() : List.of();
        List<String> unmetCriteria = parsed.get("unmetCriteria") instanceof List<?> list
            ? list.stream().map(Object::toString).toList() : List.of();
        List<String> conflicts = parsed.get("conflicts") instanceof List<?> list
            ? list.stream().map(Object::toString).toList() : List.of();
        List<String> retryAssignments = parsed.get("retryAssignments") instanceof List<?> list
            ? list.stream().map(Object::toString).toList() : List.of();
        boolean needsHumanIntervention = Boolean.TRUE.equals(parsed.getOrDefault("needsHumanIntervention", false));

        int completedCount = (int) reviews.stream().filter(ReceiptReviewResult::accepted).count();
        int failedCount = (int) receipts.stream().filter(r -> "FAILED".equals(r.status())).count();
        int degradedCount = (int) receipts.stream().filter(r -> "DEGRADED".equals(r.status())).count();

        boolean allReviewsAccepted = reviews.stream().allMatch(ReceiptReviewResult::accepted);
        boolean hasUnmetCriteria = !unmetCriteria.isEmpty();
        boolean hasDegradedOrFailed = degradedCount > 0 || failedCount > 0;

        String overallStatus;
        if (hasDegradedOrFailed && llmOverallStatus.equals("COMPLETED")) {
            overallStatus = degradedCount > 0 ? "DEGRADED" : "FAILED";
        } else if (hasUnmetCriteria && llmOverallStatus.equals("COMPLETED")) {
            overallStatus = "PARTIALLY_COMPLETED";
        } else {
            overallStatus = llmOverallStatus;
        }

        boolean accepted = "COMPLETED".equals(overallStatus) && allReviewsAccepted && !hasUnmetCriteria;

        String summaryForUser = buildAggregatedSummary(department, mainBrainTaskPlan, receipts, reviews, parsed, brainRawResponse);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("qualityAssessment", qualityAssessment);
        metadata.put("recommendation", recommendation);
        metadata.put("avgQualityScore", reviews.stream().mapToDouble(ReceiptReviewResult::qualityScore).average().orElse(0.0));
        metadata.put("aggregationSource", "LlmBasedExecutionResultAggregator");

        return new AggregationResult(
            executionId,
            overallStatus,
            completedCount,
            failedCount,
            degradedCount,
            receipts.size(),
            artifactsProduced,
            unmetCriteria,
            conflicts,
            retryAssignments,
            needsHumanIntervention,
            accepted,
            summaryForUser,
            metadata
        );
    }

    private AggregationResult buildFallbackAggregationResult(
            String executionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            List<ReceiptReviewResult> reviews,
            String brainRawResponse) {

        String fallbackSummary = fallbackAggregator.aggregate(executionId, department, mainBrainTaskPlan, receipts, brainRawResponse);

        int completedCount = (int) reviews.stream().filter(ReceiptReviewResult::accepted).count();
        int failedCount = (int) receipts.stream().filter(r -> "FAILED".equals(r.status())).count();
        int degradedCount = (int) receipts.stream().filter(r -> "DEGRADED".equals(r.status())).count();

        boolean allReviewsAccepted = reviews.stream().allMatch(ReceiptReviewResult::accepted);
        String overallStatus = failedCount > 0 ? "FAILED"
            : degradedCount > 0 ? "DEGRADED"
            : allReviewsAccepted ? "COMPLETED"
            : "PARTIALLY_COMPLETED";

        List<String> unmetFromReviews = reviews.stream()
            .filter(r -> !r.accepted())
            .flatMap(r -> r.unmetCriteria().stream())
            .distinct()
            .toList();

        List<String> retryFromReviews = reviews.stream()
            .filter(ReceiptReviewResult::needsRetry)
            .map(r -> r.retrySuggestion() != null ? r.retrySuggestion() : "retry")
            .toList();

        boolean needsHumanIntervention = reviews.stream().anyMatch(r -> !r.accepted() && !r.needsRetry());

        return new AggregationResult(
            executionId,
            overallStatus,
            completedCount,
            failedCount,
            degradedCount,
            receipts.size(),
            List.of(),
            unmetFromReviews,
            List.of(),
            retryFromReviews,
            needsHumanIntervention,
            "COMPLETED".equals(overallStatus) && allReviewsAccepted,
            fallbackSummary,
            Map.of("aggregationSource", "DefaultExecutionResultAggregator", "fallbackReason", "MainBrain unavailable or LLM failed")
        );
    }

    private String buildAggregatePrompt(
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            List<ReceiptReviewResult> reviews) {
        StringBuilder sb = new StringBuilder();
        sb.append("部门: ").append(department).append("\n");

        if (mainBrainTaskPlan != null) {
            sb.append("任务目标: ").append(mainBrainTaskPlan.goal()).append("\n");
            sb.append("验收标准: ").append(String.join("；", mainBrainTaskPlan.acceptanceCriteria())).append("\n");
        }

        sb.append("\n执行回执:\n");
        for (int i = 0; i < receipts.size(); i++) {
            EmployeeExecutionReceipt r = receipts.get(i);
            sb.append("- ").append(r.employeeCode()).append(": 状态=").append(r.status())
              .append(", 摘要=").append(r.summary() != null ? r.summary() : "无");

            if (i < reviews.size()) {
                ReceiptReviewResult review = reviews.get(i);
                sb.append(", 审核结果=").append(review.accepted() ? "通过" : "不通过")
                  .append(", 质量分=").append(String.format("%.1f", review.qualityScore()));
                if (!review.unmetCriteria().isEmpty()) {
                    sb.append(", 未满足标准=").append(String.join("；", review.unmetCriteria()));
                }
                if (review.needsRetry()) {
                    sb.append(", 需要重试");
                }
            }
            sb.append("\n");
        }

        sb.append("\n请汇总执行结果，特别注意：如果有降级(DEGRADED)回执，overallStatus 不能为 COMPLETED。");
        return sb.toString();
    }

    private String buildAggregatedSummary(
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            List<ReceiptReviewResult> reviews,
            Map<String, Object> parsed,
            String brainRawResponse) {

        String overallStatus = (String) parsed.getOrDefault("overallStatus", "COMPLETED");
        String qualityAssessment = (String) parsed.getOrDefault("qualityAssessment", "");
        String recommendation = (String) parsed.getOrDefault("recommendation", "");

        long completedCount = reviews.stream().filter(ReceiptReviewResult::accepted).count();
        long failedCount = reviews.size() - completedCount;
        double avgQuality = reviews.stream().mapToDouble(ReceiptReviewResult::qualityScore).average().orElse(0.8);

        StringBuilder sb = new StringBuilder();
        sb.append("**执行汇总**（").append(department).append("）\n\n");

        if (mainBrainTaskPlan != null) {
            sb.append("任务: ").append(mainBrainTaskPlan.goal()).append("\n\n");
        }

        sb.append("状态: ").append(overallStatus)
          .append(" | 完成: ").append(completedCount).append("/").append(receipts.size())
          .append(" | 平均质量: ").append(String.format("%.0f%%", avgQuality * 100)).append("\n\n");

        if (!qualityAssessment.isBlank()) {
            sb.append("质量评估: ").append(qualityAssessment).append("\n\n");
        }

        for (int i = 0; i < receipts.size() && i < reviews.size(); i++) {
            EmployeeExecutionReceipt r = receipts.get(i);
            ReceiptReviewResult review = reviews.get(i);
            sb.append(review.accepted() ? "✅" : "❌").append(" ").append(r.employeeCode())
              .append(" (").append(String.format("%.0f%%", review.qualityScore() * 100)).append(")")
              .append(": ").append(r.summary()).append("\n");
            if (!review.accepted() && review.reviewComment() != null) {
                sb.append("   审核意见: ").append(review.reviewComment()).append("\n");
            }
            if (!review.unmetCriteria().isEmpty()) {
                sb.append("   未满足标准: ").append(String.join("；", review.unmetCriteria())).append("\n");
            }
        }

        @SuppressWarnings("unchecked")
        List<String> unmetCriteria = parsed.get("unmetCriteria") instanceof List<?> list
            ? list.stream().map(Object::toString).toList() : List.of();
        if (!unmetCriteria.isEmpty()) {
            sb.append("\n**未满足的验收标准**:\n");
            for (String criteria : unmetCriteria) {
                sb.append("- ").append(criteria).append("\n");
            }
        }

        if (!recommendation.isBlank()) {
            sb.append("\n建议: ").append(recommendation);
        }

        if (brainRawResponse != null && !brainRawResponse.isBlank()) {
            sb.append("\n\n---\n**大脑响应**：\n").append(brainRawResponse);
        }

        return sb.toString();
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
