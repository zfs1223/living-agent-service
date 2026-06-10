package com.livingagent.gateway.service;

import com.livingagent.core.employee.EmployeeCompensationService;
import com.livingagent.core.proactive.predictor.RiskPredictor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TaskPerformanceBridgeService {

    private final EmployeeCompensationService compensationService;
    private final RiskPredictor riskPredictor;
    private final DepartmentNotificationService departmentNotificationService;

    public TaskPerformanceBridgeService(EmployeeCompensationService compensationService,
                                        RiskPredictor riskPredictor,
                                        DepartmentNotificationService departmentNotificationService) {
        this.compensationService = compensationService;
        this.riskPredictor = riskPredictor;
        this.departmentNotificationService = departmentNotificationService;
    }

    public Map<String, Object> onTaskReview(String department, String employeeId, boolean approved, int rewardGranted, double qualityScore, String taskId) {
        if (approved) {
            compensationService.recordReward(employeeId, rewardGranted, "Task approved: " + taskId);
        } else {
            compensationService.recordPenalty(employeeId, Math.max(10, rewardGranted / 2), "Task rejected: " + taskId);
        }

        riskPredictor.updateMetric("task_completion", approved ? 1.0 : 0.0, Map.of(
                "taskId", taskId,
                "employeeId", employeeId,
                "approved", approved,
                "qualityScore", qualityScore
        ));

        if (qualityScore < 0.8 || !approved) {
            departmentNotificationService.sendTaskNotification(
                    department,
                    "任务绩效提醒",
                    "任务 " + taskId + " 的审核结果需要关注，建议复盘改进。",
                    taskId,
                    "REVIEW"
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", employeeId);
        payload.put("approved", approved);
        payload.put("rewardGranted", rewardGranted);
        payload.put("qualityScore", qualityScore);
        payload.put("balance", compensationService.getBalance(employeeId));
        payload.put("riskStats", riskPredictor.getStatistics());
        payload.put("departmentSummary", compensationService.summarizeDepartment(department));
        return payload;
    }
}
