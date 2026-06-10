package com.livingagent.core.autonomy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨部门协调器：处理涉及多个部门的任务分解、分发和结果聚合。
 * <p>
 * 职责：
 * 1. 将跨部门任务拆解为各部门子任务
 * 2. 并行分发到各部门大脑执行
 * 3. 收集各部门执行结果
 * 4. 聚合为统一响应
 */
public class CrossDepartmentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(CrossDepartmentCoordinator.class);

    private final DepartmentExecutionCoordinator departmentExecutionCoordinator;
    private final AutonomyTraceService traceService;

    /** 活跃的跨部门协调会话 */
    private final Map<String, CrossDepartmentSession> activeSessions = new ConcurrentHashMap<>();

    public CrossDepartmentCoordinator(DepartmentExecutionCoordinator departmentExecutionCoordinator,
                                       AutonomyTraceService traceService) {
        this.departmentExecutionCoordinator = departmentExecutionCoordinator;
        this.traceService = traceService;
    }

    /**
     * 执行跨部门任务协调
     *
     * @param requestId 请求ID
     * @param taskPlan  主脑规划的任务计划（包含 departmentPlans）
     * @param departmentResults 已完成的各部门执行结果（由 DepartmentChatService 传入）
     * @return 跨部门协调结果
     */
    public CrossDepartmentResult coordinate(String requestId, MainBrainTaskPlan taskPlan,
                                             Map<String, DepartmentExecutionResult> departmentResults) {
        String sessionId = "cross-" + UUID.randomUUID().toString().substring(0, 8);
        Instant startTime = Instant.now();

        List<DepartmentTaskPlan> departmentPlans = taskPlan.departmentPlans();
        if (departmentPlans == null || departmentPlans.isEmpty()) {
            log.warn("CrossDepartmentCoordinator: no department plans in taskPlan, falling back to primary department only");
            return CrossDepartmentResult.singleDepartment(taskPlan.primaryDepartment());
        }

        log.info("CrossDepartmentCoordinator: starting coordination sessionId={}, departments={}, primary={}",
            sessionId, departmentPlans.stream().map(DepartmentTaskPlan::department).toList(), taskPlan.primaryDepartment());

        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "cross_department_coordination_started", "CrossDepartmentCoordinator",
            "Cross-department coordination started for " + departmentPlans.size() + " departments",
            Map.of(
                "sessionId", sessionId,
                "departments", String.join(",", departmentPlans.stream().map(DepartmentTaskPlan::department).toList()),
                "primaryDepartment", taskPlan.primaryDepartment()
            )
        ));

        // 收集结果
        List<String> failedDepartments = new ArrayList<>();
        if (departmentResults == null) {
            departmentResults = new LinkedHashMap<>();
        }

        for (DepartmentTaskPlan deptPlan : departmentPlans) {
            String dept = deptPlan.department();
            if (departmentResults.containsKey(dept)) {
                // 该部门已有结果（由主流程执行），跳过
                DepartmentExecutionResult existingResult = departmentResults.get(dept);
                log.info("CrossDepartmentCoordinator: department={} already has result, status={}",
                    dept, existingResult.status());
                continue;
            }

            // 该部门尚未执行，记录为缺失
            log.warn("CrossDepartmentCoordinator: department={} has no execution result, marking as failed", dept);
            failedDepartments.add(dept);
        }

        // 聚合结果
        boolean allSucceeded = failedDepartments.isEmpty() && departmentResults.values().stream()
            .allMatch(r -> "SUCCESS".equals(r.status()) || "COMPLETED".equals(r.status()));

        CrossDepartmentResult result = new CrossDepartmentResult(
            sessionId,
            taskPlan.primaryDepartment(),
            departmentPlans.stream().map(DepartmentTaskPlan::department).toList(),
            departmentResults,
            failedDepartments,
            allSucceeded ? "COMPLETED" : (departmentResults.isEmpty() ? "FAILED" : "PARTIAL"),
            startTime,
            Instant.now()
        );

        // 记录完成
        activeSessions.put(sessionId, new CrossDepartmentSession(sessionId, requestId, taskPlan, result));

        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "cross_department_coordination_completed", "CrossDepartmentCoordinator",
            "Cross-department coordination completed: status=" + result.overallStatus()
                + ", succeeded=" + departmentResults.size() + ", failed=" + failedDepartments.size(),
            Map.of(
                "sessionId", sessionId,
                "overallStatus", result.overallStatus(),
                "succeededDepartments", String.valueOf(departmentResults.size()),
                "failedDepartments", String.valueOf(failedDepartments.size())
            )
        ));

        return result;
    }

    /**
     * 检查是否需要跨部门协调
     */
    public static boolean needsCrossDepartmentCoordination(MainBrainTaskPlan taskPlan) {
        if (taskPlan == null) return false;
        List<DepartmentTaskPlan> plans = taskPlan.departmentPlans();
        return plans != null && plans.size() > 1;
    }

    // ===== 内部数据结构 =====

    public record CrossDepartmentResult(
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

    private record CrossDepartmentSession(
        String sessionId,
        String requestId,
        MainBrainTaskPlan taskPlan,
        CrossDepartmentResult result
    ) {}
}
