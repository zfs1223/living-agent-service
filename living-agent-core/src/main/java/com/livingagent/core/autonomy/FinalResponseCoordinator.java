package com.livingagent.core.autonomy;

public interface FinalResponseCoordinator {

    FinalResponseStrategy determineStrategy(
        String requestId,
        String department,
        DialogueDecision decision,
        BrainRoutingDecision routingDecision,
        MainBrainTaskPlan mainBrainTaskPlan,
        DepartmentExecutionResult executionResult
    );

    enum FinalResponseStrategy {
        DIRECT_ANSWER,
        ASK_CLARIFICATION,
        MAIN_BRAIN_COMPOSE,
        WAIT_FOR_RECEIPTS,
        DEPARTMENT_BRAIN_DIRECT,
        ESCALATE_TO_HUMAN,
        REQUEST_APPROVAL
    }
}
