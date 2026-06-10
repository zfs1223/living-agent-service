package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.EvolutionAuditLogEntity;
import com.livingagent.core.database.repository.EvolutionAuditLogRepository;
import com.livingagent.core.evolution.executor.EvolutionFeedbackService;
import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.knowledge.KnowledgeManager;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EvolutionFeedbackBridgeService {

    private final EvolutionFeedbackService feedbackService;
    private final KnowledgeManager knowledgeManager;
    private final TaskEventBridgeService taskEventBridgeService;
    private final DepartmentNotificationService departmentNotificationService;
    private final EvolutionAuditLogRepository auditLogRepository;

    public EvolutionFeedbackBridgeService(EvolutionFeedbackService feedbackService,
                                          KnowledgeManager knowledgeManager,
                                          TaskEventBridgeService taskEventBridgeService,
                                          DepartmentNotificationService departmentNotificationService,
                                          EvolutionAuditLogRepository auditLogRepository) {
        this.feedbackService = feedbackService;
        this.knowledgeManager = knowledgeManager;
        this.taskEventBridgeService = taskEventBridgeService;
        this.departmentNotificationService = departmentNotificationService;
        this.auditLogRepository = auditLogRepository;
    }

    public Map<String, Object> record(EvolutionResult result) {
        if (result == null) {
            return Map.of("ok", false, "error", "result_required");
        }

        feedbackService.record(result);
        writeAudit(result, "FEEDBACK_RECORDED", result.toMap());

        if (result.getGeneratedSkillId() != null) {
            knowledgeManager.storeShared(
                    "evolution." + result.getGeneratedSkillId(),
                    result.toMap(),
                    com.livingagent.core.knowledge.KnowledgeType.EXPERIENCE,
                    com.livingagent.core.knowledge.Importance.MEDIUM
            );
            writeAudit(result, "KNOWLEDGE_PROMOTED", Map.of(
                    "generatedSkillId", result.getGeneratedSkillId(),
                    "status", result.getStatus() != null ? result.getStatus().name() : "UNKNOWN"
            ));
        }

        if (result.getSignal() != null) {
            String department = result.getSignal().getBrainDomain() != null ? result.getSignal().getBrainDomain() : "ops";
            String employeeId = result.getSignal().getSource() != null ? result.getSignal().getSource() : "system";

            if (result.getStatus() == EvolutionResult.Status.FAILED || result.getStatus() == EvolutionResult.Status.ESCALATED) {
                departmentNotificationService.sendUrgentNotification(
                        department,
                        "进化结果需要关注",
                        "进化信号 " + result.getSignal().getSignalId() + " 执行结果为 " + result.getStatus() + "，请复核。"
                );
                writeAudit(result, "URGENT_NOTIFICATION_SENT", Map.of(
                        "department", department,
                        "signalId", result.getSignal().getSignalId(),
                        "status", result.getStatus().name()
                ));
            }

            if (result.getDecision() != null && result.getDecision().getStrategy() != null) {
                Map<String, Object> taskBridgePayload = taskEventBridgeService.onTaskReviewed(
                        department,
                        result.getSignal().getSignalId(),
                        employeeId,
                        result.getStatus() == EvolutionResult.Status.SUCCESS,
                        result.isImmediateEffective() ? 50 : 0,
                        result.getStatus() == EvolutionResult.Status.SUCCESS ? 1.0 : 0.6
                );
                writeAudit(result, "TASK_BRIDGE_TRIGGERED", taskBridgePayload);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result.toMap());
        payload.put("stats", feedbackService.statistics());
        return payload;
    }

    private void writeAudit(EvolutionResult result, String eventType, Map<String, Object> payload) {
        EvolutionAuditLogEntity log = new EvolutionAuditLogEntity();
        log.setResultId(result.getResultId());
        log.setEventType(eventType);
        log.setPayloadJson(payload != null ? payload.toString() : "{}");
        auditLogRepository.save(log);
    }
}
