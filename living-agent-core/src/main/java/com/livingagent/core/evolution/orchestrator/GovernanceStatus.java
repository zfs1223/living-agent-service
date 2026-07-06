package com.livingagent.core.evolution.orchestrator;

/**
 * P31-A: 治理状态。
 */
public record GovernanceStatus(
    boolean enabled,
    int pendingEvents,
    int totalProcessedEvents,
    long lastOrchestrationMsAgo,
    String state
) {
    public static GovernanceStatus disabled() {
        return new GovernanceStatus(false, 0, 0, -1, "DISABLED");
    }

    public static GovernanceStatus idle(int totalProcessed) {
        return new GovernanceStatus(true, 0, totalProcessed, -1, "IDLE");
    }

    public static GovernanceStatus active(int pending, int totalProcessed) {
        return new GovernanceStatus(true, pending, totalProcessed, 0, "ACTIVE");
    }
}
