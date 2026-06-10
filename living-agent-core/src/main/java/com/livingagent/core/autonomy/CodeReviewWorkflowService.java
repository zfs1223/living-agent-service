package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 固定数字员工代码编写 -> 审查 -> 复审 的流程控制器。
 *
 * 目标：
 * - 将代码工作区、diff、review report、final summary 绑定到同一任务链路
 * - 提供多轮审查状态机的统一入口
 */
public interface CodeReviewWorkflowService {

    /** 最大审查轮次 */
    int MAX_REVIEW_ROUNDS = 5;

    enum ReviewStage {
        PLAN_CREATED,
        ASSIGN_DEVELOPER,
        DEVELOPER_WRITING,
        CODE_SUBMITTED,
        ASSIGN_REVIEWER,
        REVIEWING,
        REVIEW_CHANGES_REQUESTED,
        DEVELOPER_REVISING,
        CODE_RESUBMITTED,
        REVIEW_APPROVED,
        FINAL_SUMMARY,
        USER_ACCEPTED,
        USER_REJECTED,
        ESCALATED
    }

    record ReviewState(
        String taskId,
        String projectId,
        String executionId,
        ReviewStage stage,
        int reviewRound,
        String developerEmployeeCode,
        String reviewerEmployeeCode,
        String worktreePath,
        String diffPath,
        String reviewReportPath,
        String finalSummaryPath,
        List<String> reviewFindings,
        Map<String, Object> metadata
    ) {}

    ReviewState createOrUpdate(
        String taskId,
        String projectId,
        String executionId,
        ReviewStage stage,
        String developerEmployeeCode,
        String reviewerEmployeeCode,
        String worktreePath,
        String diffPath,
        String reviewReportPath,
        String finalSummaryPath,
        List<String> reviewFindings,
        Map<String, Object> metadata
    );

    Optional<ReviewState> getByTaskId(String taskId);

    Optional<ReviewState> getByExecutionId(String executionId);

    ReviewState advanceStage(String taskId, ReviewStage nextStage, Map<String, Object> metadata);

    ReviewState requestChanges(String taskId, List<String> findings, Map<String, Object> metadata);

    ReviewState approve(String taskId, Map<String, Object> metadata);

    ReviewState escalate(String taskId, String reason, Map<String, Object> metadata);

    ArtifactRecord registerWorktreeArtifact(ArtifactRecord artifact);

    ArtifactRecord registerDiffArtifact(ArtifactRecord artifact);

    ArtifactRecord registerReviewReportArtifact(ArtifactRecord artifact);

    ArtifactRecord registerFinalSummaryArtifact(ArtifactRecord artifact);

    List<ArtifactRecord> getArtifactsByTaskId(String taskId);

    /**
     * 检查两个阶段之间的转换是否合法
     */
    static boolean canTransition(ReviewStage from, ReviewStage to) {
        if (from == to) return false;
        return switch (from) {
            case PLAN_CREATED -> to == ReviewStage.ASSIGN_DEVELOPER;
            case ASSIGN_DEVELOPER -> to == ReviewStage.DEVELOPER_WRITING;
            case DEVELOPER_WRITING -> to == ReviewStage.CODE_SUBMITTED;
            case CODE_SUBMITTED -> to == ReviewStage.ASSIGN_REVIEWER;
            case ASSIGN_REVIEWER -> to == ReviewStage.REVIEWING;
            case REVIEWING -> to == ReviewStage.REVIEW_CHANGES_REQUESTED || to == ReviewStage.REVIEW_APPROVED;
            case REVIEW_CHANGES_REQUESTED -> to == ReviewStage.DEVELOPER_REVISING;
            case DEVELOPER_REVISING -> to == ReviewStage.CODE_RESUBMITTED;
            case CODE_RESUBMITTED -> to == ReviewStage.ASSIGN_REVIEWER;
            case REVIEW_APPROVED -> to == ReviewStage.FINAL_SUMMARY;
            case FINAL_SUMMARY -> to == ReviewStage.USER_ACCEPTED || to == ReviewStage.USER_REJECTED;
            case USER_ACCEPTED, USER_REJECTED -> to == ReviewStage.ESCALATED;
            case ESCALATED -> false;
        };
    }
}
