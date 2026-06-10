package com.livingagent.core.autonomy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EmployeeWorkAssignment(
    String assignmentId,
    String department,
    String employeeCode,
    String employeeNeuronId,
    String employeeName,
    String role,
    String objective,
    String instruction,
    List<String> expectedDeliverables,
    List<String> allowedTools,
    Map<String, Object> context,
    String worktreePath,
    String diffPath
) {
    /** 兼容旧构造：无 worktree/diff 路径时使用 */
    public EmployeeWorkAssignment(String assignmentId, String department, String employeeCode,
                                   String employeeNeuronId, String employeeName, String role,
                                   String objective, String instruction,
                                   List<String> expectedDeliverables, List<String> allowedTools,
                                   Map<String, Object> context) {
        this(assignmentId, department, employeeCode, employeeNeuronId, employeeName, role,
             objective, instruction, expectedDeliverables, allowedTools, context, null, null);
    }

    /**
     * 添加上下文信息，返回新的 EmployeeWorkAssignment
     */
    public EmployeeWorkAssignment addContext(String key, Object value) {
        Map<String, Object> newContext = context != null ? new HashMap<>(context) : new HashMap<>();
        newContext.put(key, value);
        return new EmployeeWorkAssignment(
            assignmentId, department, employeeCode, employeeNeuronId, employeeName, role,
            objective, instruction, expectedDeliverables, allowedTools, newContext,
            worktreePath, diffPath
        );
    }
}
