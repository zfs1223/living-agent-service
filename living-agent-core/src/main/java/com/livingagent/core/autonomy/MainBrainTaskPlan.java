package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MainBrainTaskPlan(
    // 核心标识
    String planId,
    String requestId,
    String conversationId,
    String sourceMessageId,
    // 需求状态
    RequirementStatus requirementStatus,
    int requirementVersion,
    String requirementSummary,
    // 任务信息
    String taskType,
    String intent,
    String businessDomain,
    String goal,
    int roughComplexity,
    int riskLevel,
    // 执行能力归一
    ExecutionCapability executionCapability,
    ArtifactType artifactType,
    ExecutionMode executionMode,
    // 部门信息
    String primaryDepartment,
    List<String> supportingDepartments,
    // 交付物
    List<String> deliverables,
    List<String> acceptanceCriteria,
    List<String> requiredSkills,
    List<String> requiredTools,
    // 人员分派
    List<DepartmentTaskPlan> departmentPlans,
    List<String> assignedEmployees,
    String assignmentBatchId,
    // 澄清信息
    List<String> clarificationQuestions,
    Long clarificationAnsweredAt,
    // 元数据
    Map<String, Object> metadata,
    // 时间戳
    Instant createdAt,
    Instant confirmedAt,
    Instant plannedAt,
    Instant assignedAt,
    Instant completedAt
) {
    /**
     * 兼容旧构造的静态工厂方法（不含新增字段）。
     */
    public static MainBrainTaskPlan of(String planId, String requestId, String taskType, String goal,
            String primaryDepartment, List<String> supportingDepartments, int complexity, int riskLevel,
            List<String> deliverables, List<String> acceptanceCriteria,
            List<DepartmentTaskPlan> departmentPlans, Map<String, Object> metadata) {
        return new MainBrainTaskPlan(
            planId, requestId, null, null,
            RequirementStatus.PLANNED, 1, goal,
            taskType, null, null, goal, complexity, riskLevel,
            null, null, null,
            primaryDepartment, supportingDepartments,
            deliverables, acceptanceCriteria, List.of(), List.of(),
            departmentPlans, List.of(), null,
            List.of(), null,
            metadata,
            Instant.now(), null, Instant.now(), null, null
        );
    }

    /**
     * 创建需求澄清状态的计划
     */
    public static MainBrainTaskPlan clarification(String requestId, String requirementSummary,
            List<String> clarificationQuestions, String primaryDepartment) {
        return new MainBrainTaskPlan(
            requestId + "-plan", requestId, null, null,
            RequirementStatus.NEEDS_CLARIFICATION, 1, requirementSummary,
            null, null, null, requirementSummary, 0, 0,
            null, null, null,
            primaryDepartment, List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), null,
            clarificationQuestions, null,
            Map.of(),
            Instant.now(), null, null, null, null
        );
    }

    /**
     * 检查是否允许分派员工
     */
    public boolean allowsAssignment() {
        return requirementStatus != null && requirementStatus.allowsAssignment();
    }

    /**
     * 检查是否需要澄清
     */
    public boolean needsClarification() {
        return requirementStatus != null && requirementStatus.needsClarification();
    }

    /**
     * P2-4: 检查需求是否已冻结（执行中不允许修改需求）
     * 当状态 >= PLANNED 时，需求视为冻结，不允许重新规划
     */
    public boolean isRequirementFrozen() {
        return requirementStatus != null
            && requirementStatus != RequirementStatus.DRAFT
            && requirementStatus != RequirementStatus.NEEDS_CLARIFICATION
            && requirementStatus != RequirementStatus.CLARIFICATION_PENDING;
    }

    /**
     * P2-4: 带状态转换校验的需求状态变更
     * 仅允许合法的状态转换，防止需求漂移
     *
     * @param newStatus 目标状态
     * @return 新的 MainBrainTaskPlan 实例（record 不可变）
     * @throws IllegalStateException 如果状态转换不合法
     */
    public MainBrainTaskPlan withRequirementStatus(RequirementStatus newStatus) {
        if (requirementStatus != null && !RequirementStatus.canTransition(requirementStatus, newStatus)) {
            throw new IllegalStateException(
                "非法需求状态转换: " + requirementStatus + " → " + newStatus
                + " (planId=" + planId + ", requestId=" + requestId + ")");
        }
        return new MainBrainTaskPlan(
            planId, requestId, conversationId, sourceMessageId,
            newStatus, requirementVersion, requirementSummary,
            taskType, intent, businessDomain, goal, roughComplexity, riskLevel,
            executionCapability, artifactType, executionMode,
            primaryDepartment, supportingDepartments,
            deliverables, acceptanceCriteria, requiredSkills, requiredTools,
            departmentPlans, assignedEmployees, assignmentBatchId,
            clarificationQuestions, clarificationAnsweredAt,
            metadata,
            createdAt,
            newStatus == RequirementStatus.REQUIREMENT_CONFIRMED ? Instant.now() : confirmedAt,
            newStatus == RequirementStatus.PLANNED ? Instant.now() : plannedAt,
            newStatus == RequirementStatus.ASSIGNED ? Instant.now() : assignedAt,
            newStatus == RequirementStatus.COMPLETED || newStatus == RequirementStatus.FAILED ? Instant.now() : completedAt
        );
    }

    /**
     * P2-4: 递增需求版本号（仅在需求确认时调用）
     */
    public MainBrainTaskPlan withIncrementedVersion() {
        return new MainBrainTaskPlan(
            planId, requestId, conversationId, sourceMessageId,
            requirementStatus, requirementVersion + 1, requirementSummary,
            taskType, intent, businessDomain, goal, roughComplexity, riskLevel,
            executionCapability, artifactType, executionMode,
            primaryDepartment, supportingDepartments,
            deliverables, acceptanceCriteria, requiredSkills, requiredTools,
            departmentPlans, assignedEmployees, assignmentBatchId,
            clarificationQuestions, clarificationAnsweredAt,
            metadata,
            createdAt, confirmedAt, plannedAt, assignedAt, completedAt
        );
    }
}
