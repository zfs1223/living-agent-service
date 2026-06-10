package com.livingagent.core.autonomy;

public interface DialogueAnalyzer {
    DialogueDecision analyze(String message, String userId, String department, String sessionId);
}
