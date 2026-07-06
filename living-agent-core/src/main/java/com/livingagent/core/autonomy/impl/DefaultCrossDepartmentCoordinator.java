package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认跨部门协调器实现。
 *
 * <p>将跨部门任务拆解为各部门子任务，并行分发，收集结果，聚合为统一响应。
 */
public class DefaultCrossDepartmentCoordinator implements CrossDepartmentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DefaultCrossDepartmentCoordinator.class);

    private final DepartmentExecutionCoordinator departmentExecutionCoordinator;
    private final AutonomyTraceService traceService;

    /** 活跃的跨部门协调会话 */
    private final Map<String, CrossDepartmentSession> activeSessions = new ConcurrentHashMap<>();

    public DefaultCrossDepartmentCoordinator(DepartmentExecutionCoordinator departmentExecutionCoordinator,
                                              AutonomyTraceService traceService) {
        this.departmentExecutionCoordinator = departmentExecutionCoordinator;
        this.traceService = traceService;
    }

    @Override
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
                DepartmentExecutionResult existingResult = departmentResults.get(dept);
                log.info("CrossDepartmentCoordinator: department={} already has result, status={}",
                    dept, existingResult.status());
                continue;
            }

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

    private record CrossDepartmentSession(
        String sessionId,
        String requestId,
        MainBrainTaskPlan taskPlan,
        CrossDepartmentResult result
    ) {}
}
