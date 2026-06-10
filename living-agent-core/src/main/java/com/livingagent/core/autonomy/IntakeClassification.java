package com.livingagent.core.autonomy;

public record IntakeClassification(
    DialogueDecision.MessageKind kind,
    String roughIntent,
    boolean needsMainBrainPlanning,
    boolean likelyCrossDepartment,
    int roughComplexity
) {
}
