package com.livingagent.gateway.service;

import com.livingagent.core.proactive.predictor.RiskPredictor;
import com.livingagent.core.proactive.suggestion.ProactiveSuggestionService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TaskEventBridgeService {

    private final RiskPredictor riskPredictor;
    private final ProactiveSuggestionService suggestionService;
    private final DepartmentNotificationService departmentNotificationService;

    public TaskEventBridgeService(
            RiskPredictor riskPredictor,
            ProactiveSuggestionService suggestionService,
            DepartmentNotificationService departmentNotificationService) {
        this.riskPredictor = riskPredictor;
        this.suggestionService = suggestionService;
        this.departmentNotificationService = departmentNotificationService;
    }

    public Map<String, Object> onTaskReviewed(String department, String taskId, String employeeId, boolean approved, int rewardGranted, double qualityScore) {
        double taskCompletionRate = approved ? 1.0 : 0.0;
        riskPredictor.updateMetric("task_completion", taskCompletionRate, Map.of(
                "taskId", taskId,
                "employeeId", employeeId,
                "approved", approved,
                "rewardGranted", rewardGranted,
                "qualityScore", qualityScore
        ));

        if (!approved || qualityScore < 0.8) {
            departmentNotificationService.sendUrgentNotification(
                    department,
                    "任务审核异常",
                    "任务 " + taskId + " 审核未通过或质量偏低，请关注。"
            );
            suggestionService.pushSuggestion(employeeId, new ProactiveSuggestionService.Suggestion(
                    "task-review-" + taskId,
                    employeeId,
                    "提升任务质量",
                    "你提交的任务质量评分偏低，建议优化执行过程和结果交付。",
                    ProactiveSuggestionService.SuggestionType.LEARNING,
                    0.88,
                    Map.of("taskId", taskId, "qualityScore", qualityScore, "approved", approved),
                    java.time.Instant.now()
            ));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("employeeId", employeeId);
        payload.put("approved", approved);
        payload.put("rewardGranted", rewardGranted);
        payload.put("qualityScore", qualityScore);
        payload.put("riskStats", riskPredictor.getStatistics());
        payload.put("suggested", !approved || qualityScore < 0.8);
        return payload;
    }
}
