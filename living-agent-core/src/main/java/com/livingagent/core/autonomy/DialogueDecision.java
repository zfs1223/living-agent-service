package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DialogueDecision(
    String requestId,
    String sessionId,
    String userId,
    String originalMessage,
    MessageKind kind,
    String intent,
    String primaryDepartment,
    String primaryBrainId,
    List<String> supportingDepartments,
    boolean requiresTaskExecution,
    boolean requiresClarification,
    List<String> clarificationQuestions,  // P1-3: 改为 List<String>，与 MainBrainTaskPlan 对齐
    int complexity,
    int riskLevel,
    Map<String, Object> metadata
) {
    public enum MessageKind {
        CHAT,
        TASK,
        PROJECT,
        APPROVAL,
        CONSULTATION,
        KNOWLEDGE,
        CROSS_DEPARTMENT
    }
}
