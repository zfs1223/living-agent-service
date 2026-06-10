package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

/**
 * 主脑最终总结服务接口
 * 用于执行类任务的最终回复由主脑基于完整上下文进行组织级收口
 */
public interface MainBrainFinalSummaryService {

    /**
     * 最终总结结果
     */
    record FinalSummaryResult(
        String status,
        String userMessage,
        List<Map<String, Object>> deliverables,
        String acceptanceConclusion,
        List<String> risks,
        List<String> nextActions,
        boolean requiresHumanReview,
        String summarySource
    ) {}

    /**
     * 生成最终总结
     */
    FinalSummaryResult generateSummary(
        String originalMessage,
        DialogueDecision dialogueDecision,
        MainBrainTaskPlan taskPlan,
        DepartmentExecutionResult executionResult,
        ExecutionResultAggregator.AggregationResult receiptSummary,
        List<ArtifactRecord> artifactRecords,
        String completionGateResult,
        List<String> riskAndLimitations,
        List<String> nextActionCandidates
    );
}
