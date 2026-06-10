package com.livingagent.core.autonomy;

import java.time.Instant;
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
    java.util.List<String> supportingDepartments,
    boolean requiresTaskExecution,
    boolean requiresClarification,
    String clarificationQuestion,
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
