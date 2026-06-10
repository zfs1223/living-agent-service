package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.ArtifactRecordService;
import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.autonomy.TaskMetadataKeys;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodeArtifactMetadataBinder {

    private final ArtifactRecordService artifactRecordService;
    private final CodeReviewWorkflowService codeReviewWorkflowService;

    public CodeArtifactMetadataBinder(ArtifactRecordService artifactRecordService,
                                      CodeReviewWorkflowService codeReviewWorkflowService) {
        this.artifactRecordService = artifactRecordService;
        this.codeReviewWorkflowService = codeReviewWorkflowService;
    }

    public ArtifactRecord registerWorktree(String executionId,
                                           String department,
                                           String employeeCode,
                                           String employeeNeuronId,
                                           String taskId,
                                           String projectId,
                                           String worktreePath,
                                           String branchName,
                                           Map<String, Object> metadata) {
        Map<String, Object> merged = merge(metadata,
            TaskMetadataKeys.ARTIFACT_CATEGORY, "worktree",
            TaskMetadataKeys.WORKTREE_PATH, worktreePath,
            TaskMetadataKeys.BRANCH_NAME, branchName,
            TaskMetadataKeys.TASK_ID, taskId,
            TaskMetadataKeys.PROJECT_ID, projectId,
            TaskMetadataKeys.TASK_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_TYPE) : null,
            TaskMetadataKeys.TASK_SCOPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_SCOPE) : null,
            TaskMetadataKeys.WORKFLOW_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.WORKFLOW_TYPE) : null);
        ArtifactRecord record = ArtifactRecord.of(executionId, department, employeeCode, employeeNeuronId,
            "CODE_WORKTREE", worktreePath, branchName != null ? branchName : worktreePath, "代码工作树");
        record = new ArtifactRecord(record.artifactId(), record.executionId(), record.department(),
            record.ownerEmployeeCode(), record.ownerEmployeeNeuronId(), record.type(), record.path(), record.name(),
            record.summary(), record.sizeBytes(), record.sha256(), taskId, projectId, List.of(), record.createdAt(), merged);
        return artifactRecordService != null ? artifactRecordService.recordArtifact(record) : record;
    }

    public ArtifactRecord registerDiff(String executionId,
                                       String department,
                                       String employeeCode,
                                       String employeeNeuronId,
                                       String taskId,
                                       String projectId,
                                       String diffPath,
                                       String worktreePath,
                                       Map<String, Object> metadata) {
        Map<String, Object> merged = merge(metadata,
            TaskMetadataKeys.ARTIFACT_CATEGORY, "diff",
            TaskMetadataKeys.WORKTREE_PATH, worktreePath,
            TaskMetadataKeys.DIFF_PATH, diffPath,
            TaskMetadataKeys.TASK_ID, taskId,
            TaskMetadataKeys.PROJECT_ID, projectId,
            TaskMetadataKeys.TASK_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_TYPE) : null,
            TaskMetadataKeys.TASK_SCOPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_SCOPE) : null,
            TaskMetadataKeys.WORKFLOW_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.WORKFLOW_TYPE) : null);
        ArtifactRecord record = ArtifactRecord.of(executionId, department, employeeCode, employeeNeuronId,
            "CODE_DIFF", diffPath, diffPath, "代码差异补丁");
        record = new ArtifactRecord(record.artifactId(), record.executionId(), record.department(),
            record.ownerEmployeeCode(), record.ownerEmployeeNeuronId(), record.type(), record.path(), record.name(),
            record.summary(), record.sizeBytes(), record.sha256(), taskId, projectId, List.of(), record.createdAt(), merged);
        return artifactRecordService != null ? artifactRecordService.recordArtifact(record) : record;
    }

    public ArtifactRecord registerReviewReport(String executionId,
                                               String department,
                                               String employeeCode,
                                               String employeeNeuronId,
                                               String taskId,
                                               String projectId,
                                               String reviewReportPath,
                                               String diffPath,
                                               String worktreePath,
                                               Map<String, Object> metadata) {
        Map<String, Object> merged = merge(metadata,
            TaskMetadataKeys.ARTIFACT_CATEGORY, "review_report",
            TaskMetadataKeys.WORKTREE_PATH, worktreePath,
            TaskMetadataKeys.DIFF_PATH, diffPath,
            TaskMetadataKeys.REVIEW_REPORT_PATH, reviewReportPath,
            TaskMetadataKeys.TASK_ID, taskId,
            TaskMetadataKeys.PROJECT_ID, projectId,
            TaskMetadataKeys.TASK_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_TYPE) : null,
            TaskMetadataKeys.TASK_SCOPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_SCOPE) : null,
            TaskMetadataKeys.WORKFLOW_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.WORKFLOW_TYPE) : null);
        ArtifactRecord record = ArtifactRecord.of(executionId, department, employeeCode, employeeNeuronId,
            "CODE_REVIEW_REPORT", reviewReportPath, reviewReportPath, "代码审查报告");
        record = new ArtifactRecord(record.artifactId(), record.executionId(), record.department(),
            record.ownerEmployeeCode(), record.ownerEmployeeNeuronId(), record.type(), record.path(), record.name(),
            record.summary(), record.sizeBytes(), record.sha256(), taskId, projectId, List.of(), record.createdAt(), merged);
        if (codeReviewWorkflowService != null) {
            codeReviewWorkflowService.registerReviewReportArtifact(record);
        }
        return artifactRecordService != null ? artifactRecordService.recordArtifact(record) : record;
    }

    public ArtifactRecord registerFinalSummary(String executionId,
                                               String department,
                                               String employeeCode,
                                               String employeeNeuronId,
                                               String taskId,
                                               String projectId,
                                               String summaryPath,
                                               String diffPath,
                                               String worktreePath,
                                               String reviewReportPath,
                                               Map<String, Object> metadata) {
        Map<String, Object> merged = merge(metadata,
            TaskMetadataKeys.ARTIFACT_CATEGORY, "final_summary",
            TaskMetadataKeys.WORKTREE_PATH, worktreePath,
            TaskMetadataKeys.DIFF_PATH, diffPath,
            TaskMetadataKeys.REVIEW_REPORT_PATH, reviewReportPath,
            TaskMetadataKeys.FINAL_SUMMARY_PATH, summaryPath,
            TaskMetadataKeys.TASK_ID, taskId,
            TaskMetadataKeys.PROJECT_ID, projectId,
            TaskMetadataKeys.TASK_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_TYPE) : null,
            TaskMetadataKeys.TASK_SCOPE, metadata != null ? metadata.get(TaskMetadataKeys.TASK_SCOPE) : null,
            TaskMetadataKeys.WORKFLOW_TYPE, metadata != null ? metadata.get(TaskMetadataKeys.WORKFLOW_TYPE) : null);
        ArtifactRecord record = ArtifactRecord.of(executionId, department, employeeCode, employeeNeuronId,
            "CODE_FINAL_SUMMARY", summaryPath, summaryPath, "最终交付摘要");
        record = new ArtifactRecord(record.artifactId(), record.executionId(), record.department(),
            record.ownerEmployeeCode(), record.ownerEmployeeNeuronId(), record.type(), record.path(), record.name(),
            record.summary(), record.sizeBytes(), record.sha256(), taskId, projectId, List.of(), record.createdAt(), merged);
        if (codeReviewWorkflowService != null) {
            codeReviewWorkflowService.registerFinalSummaryArtifact(record);
        }
        return artifactRecordService != null ? artifactRecordService.recordArtifact(record) : record;
    }

    private Map<String, Object> merge(Map<String, Object> metadata, Object... pairs) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            merged.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return merged;
    }
}
