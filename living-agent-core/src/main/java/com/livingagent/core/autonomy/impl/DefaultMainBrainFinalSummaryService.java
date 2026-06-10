package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.ExecutionResultAggregator.AggregationResult;
import com.livingagent.core.autonomy.MainBrainFinalSummaryService.FinalSummaryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认主脑最终总结服务（fallback）
 * LLM 不可用时使用模板方式生成总结
 */
public class DefaultMainBrainFinalSummaryService implements MainBrainFinalSummaryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMainBrainFinalSummaryService.class);

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
        
        String status = determineStatus(receiptSummary, completionGateResult);
        String userMessage = buildUserMessage(originalMessage, taskPlan, executionResult, 
            receiptSummary, artifactRecords, completionGateResult);
        
        List<Map<String, Object>> deliverables = artifactRecords != null ? artifactRecords.stream()
            .map(a -> Map.<String, Object>of(
                "name", a.name(),
                "path", a.path(),
                "type", a.type(),
                "summary", a.summary() != null ? a.summary() : ""
            ))
            .collect(Collectors.toList()) : List.of();
        
        String acceptanceConclusion = completionGateResult.equals("PASSED") 
            ? "验收通过，所有交付物符合要求" 
            : "验收未完全通过，部分任务需要跟进";
        
        List<String> risks = riskAndLimitations != null ? riskAndLimitations : List.of();
        List<String> nextActions = nextActionCandidates != null ? nextActionCandidates : List.of();
        
        boolean requiresHumanReview = receiptSummary.failedCount() > 0 || 
            completionGateResult.equals("FAILED");

        log.info("MainBrain final summary generated from fallback: status={}, summary_source=fallback_composer", status);
        
        return new FinalSummaryResult(
            status,
            userMessage,
            deliverables,
            acceptanceConclusion,
            risks,
            nextActions,
            requiresHumanReview,
            "fallback_composer"
        );
    }

    private String determineStatus(AggregationResult receiptSummary, String completionGateResult) {
        if (completionGateResult.equals("FAILED")) return "FAILED";
        if (receiptSummary.completedCount() == 0) return "FAILED";
        if (receiptSummary.failedCount() > 0) return "PARTIAL";
        if (receiptSummary.totalCount() > receiptSummary.completedCount()) return "WAITING";
        return "COMPLETED";
    }

    private String buildUserMessage(String originalMessage, MainBrainTaskPlan taskPlan,
                                    DepartmentExecutionResult executionResult,
                                    AggregationResult receiptSummary,
                                    List<ArtifactRecord> artifactRecords,
                                    String completionGateResult) {
        
        StringBuilder msg = new StringBuilder();
        
        if (taskPlan != null) {
            msg.append("已根据您的请求安排任务：").append(taskPlan.taskType()).append("\n");
            msg.append("主责部门：").append(taskPlan.primaryDepartment()).append("\n\n");
        }
        
        if (executionResult != null) {
            msg.append("执行结果：\n");
            msg.append("- 派发任务数：").append(executionResult.dispatchedAssignments().size()).append("\n");
            msg.append("- 已完成：").append(receiptSummary.completedCount()).append("\n");
            msg.append("- 失败：").append(receiptSummary.failedCount()).append("\n");
            msg.append("- 验收状态：").append(completionGateResult).append("\n\n");
        }
        
        if (artifactRecords != null && !artifactRecords.isEmpty()) {
            msg.append("生成产物：\n");
            artifactRecords.forEach(a -> msg.append("- ").append(a.name()).append(": ").append(a.path()).append("\n"));
            msg.append("\n");
        }
        
        if (completionGateResult.equals("PASSED")) {
            msg.append("所有交付物已验收通过，任务完成。");
        } else {
            msg.append("任务部分完成，可能需要进一步处理。");
        }
        
        return msg.toString();
    }
}
