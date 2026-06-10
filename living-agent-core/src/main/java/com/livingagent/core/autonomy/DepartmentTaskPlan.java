package com.livingagent.core.autonomy;

import java.util.List;

public record DepartmentTaskPlan(
    String department,
    String objective,
    List<String> suggestedRoles,
    List<String> suggestedEmployeeCodes,
    List<String> expectedDeliverables,
    List<String> acceptanceCriteria,
    // P0-5 新增：执行能力归一字段
    ExecutionCapability executionCapability,
    ArtifactType artifactType,
    ExecutionMode executionMode
) {
    /**
     * 兼容旧构造的静态工厂方法（不含新增字段）。
     */
    public static DepartmentTaskPlan of(String department, String objective,
            List<String> suggestedRoles, List<String> suggestedEmployeeCodes,
            List<String> expectedDeliverables, List<String> acceptanceCriteria) {
        return new DepartmentTaskPlan(department, objective, suggestedRoles, suggestedEmployeeCodes,
            expectedDeliverables, acceptanceCriteria, null, null, null);
    }
}
