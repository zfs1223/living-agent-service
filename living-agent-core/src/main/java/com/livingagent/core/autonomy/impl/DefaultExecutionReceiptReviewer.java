package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.autonomy.ExecutionReceiptReviewer;
import com.livingagent.core.autonomy.ReceiptStatus;
import com.livingagent.core.autonomy.ExecutionReceiptReviewer.ReceiptReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultExecutionReceiptReviewer implements ExecutionReceiptReviewer {

    private static final Logger log = LoggerFactory.getLogger(DefaultExecutionReceiptReviewer.class);

    @Override
    public Optional<ReceiptReviewResult> reviewReceipt(
            EmployeeExecutionReceipt receipt,
            EmployeeWorkAssignment assignment,
            List<String> acceptanceCriteria) {

        List<String> unmetCriteria = new ArrayList<>();
        boolean accepted = true;
        double qualityScore = 1.0;
        boolean needsRetry = false;
        String retrySuggestion = null;

        if (receipt.status() != ReceiptStatus.COMPLETED) {
            accepted = false;
            qualityScore = 0.1;
            unmetCriteria.add("execution_status_not_completed: " + (receipt.status() != null ? receipt.status().getCode() : "null"));
            needsRetry = true;
            retrySuggestion = "执行状态为 " + (receipt.status() != null ? receipt.status().getCode() : "null") + "，建议重试";
        }

        if (receipt.summary() == null || receipt.summary().isBlank()) {
            accepted = false;
            qualityScore = 0.2;
            unmetCriteria.add("execution_summary_missing");
            needsRetry = true;
            retrySuggestion = "执行摘要为空，无法判断任务是否完成，建议重试";
        }

        if (acceptanceCriteria != null && !acceptanceCriteria.isEmpty()) {
            if (receipt.summary() != null) {
                String summaryLower = receipt.summary().toLowerCase();
                int unmetCount = 0;
                for (String criteria : acceptanceCriteria) {
                    if (!matchesCriteria(summaryLower, criteria.toLowerCase())) {
                        unmetCriteria.add("unmet_criteria: " + criteria);
                        unmetCount++;
                    }
                }
                // 降级验收：超过半数验收标准未满足则拒绝
                if (unmetCount > 0) {
                    accepted = false;
                    qualityScore = Math.max(0.1, 1.0 - (double) unmetCount / acceptanceCriteria.size());
                    if (unmetCount >= acceptanceCriteria.size()) {
                        needsRetry = true;
                        retrySuggestion = "所有验收标准均未满足，建议重试";
                    }
                }
            } else {
                // 摘要为空时所有标准都不满足
                accepted = false;
                qualityScore = 0.1;
                unmetCriteria.add("all_criteria_unmet_due_to_missing_summary");
                needsRetry = true;
                retrySuggestion = "执行摘要为空，无法验证验收标准，建议重试";
            }
        }

        if (assignment != null && hasArtifactExpectation(assignment)
                && (receipt.worktreePath() == null || receipt.worktreePath().isBlank())
                && (receipt.diffPath() == null || receipt.diffPath().isBlank())
                && (receipt.metadata() == null || !receipt.metadata().containsKey("artifactPaths"))) {
            accepted = false;
            qualityScore = Math.min(qualityScore, 0.3);
            unmetCriteria.add("expected_artifacts_missing");
            needsRetry = true;
            retrySuggestion = "任务要求生成产物但未产出任何文件，建议重试";
        }

        qualityScore = Math.round(qualityScore * 100.0) / 100.0;

        String reviewComment = accepted
            ? "程序规则验收通过（LLM 不可用时的降级验收）"
            : "程序规则验收未通过: " + String.join("; ", unmetCriteria);

        log.info("DefaultExecutionReceiptReviewer: receiptId={}, accepted={}, quality={}, unmet={}",
            receipt.receiptId(), accepted, qualityScore, unmetCriteria.size());

        return Optional.of(new ReceiptReviewResult(
            receipt.receiptId(),
            accepted,
            qualityScore,
            reviewComment,
            unmetCriteria,
            needsRetry,
            retrySuggestion
        ));
    }

    private boolean matchesCriteria(String summaryLower, String criteriaLower) {
        String[] keywords = criteriaLower.split("[,，、\\s]+");
        for (String keyword : keywords) {
            if (keyword.length() >= 2 && summaryLower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasArtifactExpectation(EmployeeWorkAssignment assignment) {
        return assignment.objective() != null
            && (assignment.objective().contains("生成") || assignment.objective().contains("创建")
                || assignment.objective().contains("开发") || assignment.objective().contains("编写"));
    }
}
