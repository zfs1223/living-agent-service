package com.livingagent.core.autonomy;

import com.livingagent.core.knowledge.KnowledgeManager;

import java.util.concurrent.CompletableFuture;

public interface MainBrainTaskDirector {

    CompletableFuture<MainBrainTaskPlan> plan(
        IntakeClassification intake,
        DialogueDecision decision,
        String userMessage,
        String userId,
        String sessionId,
        String currentDepartment
    );

    /**
     * 带知识注入的规划方法
     */
    default CompletableFuture<MainBrainTaskPlan> planWithKnowledge(
            IntakeClassification intake,
            DialogueDecision decision,
            String userMessage,
            String userId,
            String sessionId,
            String currentDepartment,
            KnowledgeManager knowledgeManager) {
        return plan(intake, decision, userMessage, userId, sessionId, currentDepartment);
    }
}
