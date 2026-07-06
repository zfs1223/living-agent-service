package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 跨部门协调器接口。
 *
 * <p>处理涉及多个部门的任务分解、分发和结果聚合。
 */
public interface CrossDepartmentCoordinator {

    /**
     * 执行跨部门任务协调
     *
     * @param requestId        请求ID
     * @param taskPlan         主脑规划的任务计划（包含 departmentPlans）
     * @param departmentResults 已完成的各部门执行结果
     * @return 跨部门协调结果
     */
    CrossDepartmentResult coordinate(String requestId, MainBrainTaskPlan taskPlan,
                                      Map<String, DepartmentExecutionResult> departmentResults);

    /**
     * 检查是否需要跨部门协调
     */
    static boolean needsCrossDepartmentCoordination(MainBrainTaskPlan taskPlan) {
        if (taskPlan == null) return false;
        List<DepartmentTaskPlan> plans = taskPlan.departmentPlans();
        return plans != null && plans.size() > 1;
    }

    /**
     * 跨部门协调结果
     */
    record CrossDepartmentResult(
        String sessionId,
        String primaryDepartment,
        List<String> coordinatedDepartments,
        Map<String, DepartmentExecutionResult> departmentResults,
        List<String> failedDepartments,
        String overallStatus,
        Instant startedAt,
        Instant completedAt
    ) {
        public static CrossDepartmentResult singleDepartment(String department) {
            return new CrossDepartmentResult(
                "single-" + UUID.randomUUID().toString().substring(0, 8),
                department, List.of(department), Map.of(), List.of(), "SINGLE_DEPARTMENT",
                Instant.now(), Instant.now()
            );
        }

        /** 收集所有部门的收据摘要 */
        public String collectSummaries() {
            StringBuilder sb = new StringBuilder();
            departmentResults.forEach((dept, result) -> {
                sb.append("【").append(dept).append("】");
                sb.append(" status=").append(result.status());
                int count = result.dispatchedAssignments() != null ? result.dispatchedAssignments().size() : 0;
                sb.append(", dispatched=").append(count);
                sb.append("\n");
            });
            if (!failedDepartments.isEmpty()) {
                sb.append("失败部门: ").append(String.join(", ", failedDepartments));
            }
            return sb.toString();
        }
    }
}
