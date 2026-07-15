package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.PerformanceStatsService;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeOrigin;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmployeeCreationImprovementTracker {

    private static final Logger log = LoggerFactory.getLogger(EmployeeCreationImprovementTracker.class);
    private static final double LOW_PERFORMANCE_THRESHOLD = 0.3;
    private static final int MIN_TASKS_FOR_EVALUATION = 3;

    private final EmployeeService employeeService;
    private final CrossLoopEventBus eventBus;

    @Autowired(required = false)
    private PerformanceStatsService performanceStatsService;

    private volatile double creationQualityThreshold = LOW_PERFORMANCE_THRESHOLD;
    private final Map<String, CreatedEmployeeRecord> createdEmployeeHistory = new ConcurrentHashMap<>();

    public EmployeeCreationImprovementTracker(EmployeeService employeeService, CrossLoopEventBus eventBus) {
        this.employeeService = employeeService;
        this.eventBus = eventBus;
    }

    public void recordCreatedEmployee(String employeeId, String department, String justification) {
        createdEmployeeHistory.put(employeeId, new CreatedEmployeeRecord(
            employeeId, department, justification, System.currentTimeMillis(), 0, 0));
        log.info("[闭环35] 记录动态创建员工: employeeId={}, department={}", employeeId, department);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evaluateCreatedEmployeePerformance() {
        if (createdEmployeeHistory.isEmpty()) return;

        int totalEvaluated = 0;
        int lowPerformers = 0;

        for (Map.Entry<String, CreatedEmployeeRecord> entry : createdEmployeeHistory.entrySet()) {
            CreatedEmployeeRecord record = entry.getValue();
            if (System.currentTimeMillis() - record.createdAtMs < 30 * 60 * 1000) continue;

            if (performanceStatsService == null) continue;

            double weight = performanceStatsService.getDispatchWeight(record.employeeId);
            totalEvaluated++;

            if (weight < creationQualityThreshold) {
                lowPerformers++;
                log.info("[闭环35] 动态员工绩效偏低: employeeId={}, weight={}", record.employeeId, weight);

                createdEmployeeHistory.put(record.employeeId, record.withPerformance(weight));

                eventBus.publish(35, "created_employee_underperforming",
                    CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("employeeId", record.employeeId,
                        "department", record.department,
                        "weight", weight,
                        "action", "adjust_creation_strategy"));
            }
        }

        if (totalEvaluated > 0) {
            double underperformRate = (double) lowPerformers / totalEvaluated;
            if (underperformRate > 0.3) {
                creationQualityThreshold = Math.min(0.5, creationQualityThreshold + 0.05);
                log.info("[闭环35] 提高创建质量阈值: newThreshold={}", creationQualityThreshold);
                eventBus.publish(35, "creation_threshold_adjusted",
                    CrossLoopEvent.EventPriority.ECONOMY,
                    Map.of("newThreshold", creationQualityThreshold,
                        "underperformRate", underperformRate,
                        "action", "raise_creation_bar"));
            } else if (underperformRate < 0.1 && creationQualityThreshold > LOW_PERFORMANCE_THRESHOLD) {
                creationQualityThreshold = Math.max(LOW_PERFORMANCE_THRESHOLD, creationQualityThreshold - 0.02);
                log.info("[闭环35] 放宽创建质量阈值: newThreshold={}", creationQualityThreshold);
            }
        }
    }

    public double getCreationQualityThreshold() {
        return creationQualityThreshold;
    }

    private record CreatedEmployeeRecord(
        String employeeId, String department, String justification,
        long createdAtMs, double lastPerformance, int taskCount) {

        CreatedEmployeeRecord withPerformance(double performance) {
            return new CreatedEmployeeRecord(employeeId, department, justification,
                createdAtMs, performance, taskCount + 1);
        }
    }
}
