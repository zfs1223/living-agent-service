package com.livingagent.core.autonomy;

public enum RequirementStatus {
    DRAFT,
    NEEDS_CLARIFICATION,
    CLARIFICATION_PENDING,
    REQUIREMENT_CONFIRMED,
    PLANNING,
    PLANNED,
    ASSIGNED,
    EXECUTING,
    COMPLETED,
    FAILED;

    public static boolean canTransition(RequirementStatus from, RequirementStatus to) {
        return switch (from) {
            case DRAFT -> to == NEEDS_CLARIFICATION || to == REQUIREMENT_CONFIRMED;
            case NEEDS_CLARIFICATION -> to == CLARIFICATION_PENDING;
            case CLARIFICATION_PENDING -> to == REQUIREMENT_CONFIRMED || to == NEEDS_CLARIFICATION;
            case REQUIREMENT_CONFIRMED -> to == PLANNING;
            case PLANNING -> to == PLANNED;
            case PLANNED -> to == ASSIGNED;
            case ASSIGNED -> to == EXECUTING;
            case EXECUTING -> to == COMPLETED || to == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }

    public boolean allowsAssignment() {
        return this == PLANNED;
    }

    public boolean allowsExecution() {
        return this == ASSIGNED || this == EXECUTING;
    }

    public boolean needsClarification() {
        return this == DRAFT || this == NEEDS_CLARIFICATION || this == CLARIFICATION_PENDING;
    }
}
