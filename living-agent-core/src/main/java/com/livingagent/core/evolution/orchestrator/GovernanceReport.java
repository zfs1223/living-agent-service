package com.livingagent.core.evolution.orchestrator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P31-A: 治理报告，记录一次协同编排周期的执行结果。
 */
public record GovernanceReport(
    String reportId,
    Instant generatedAt,
    int totalEvents,
    int executedEvents,
    int successfulEvents,
    int failedEvents,
    int escalatedEvents,
    List<EventExecution> executions,
    Map<String, Object> summary
) {
    public static GovernanceReport empty() {
        return new GovernanceReport(UUID.randomUUID().toString(), Instant.now(),
            0, 0, 0, 0, 0, List.of(), Map.of());
    }

    public record EventExecution(
        int sourceLoop,
        String eventType,
        String result,
        long durationMs
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int totalEvents;
        private int executedEvents;
        private int successfulEvents;
        private int failedEvents;
        private int escalatedEvents;
        private final List<EventExecution> executions = new ArrayList<>();

        public Builder totalEvents(int v) { totalEvents = v; return this; }
        public Builder executedEvents(int v) { executedEvents = v; return this; }
        public Builder successfulEvents(int v) { successfulEvents = v; return this; }
        public Builder failedEvents(int v) { failedEvents = v; return this; }
        public Builder escalatedEvents(int v) { escalatedEvents = v; return this; }
        public Builder addExecution(EventExecution e) { executions.add(e); return this; }

        public GovernanceReport build() {
            return new GovernanceReport(
                UUID.randomUUID().toString(), Instant.now(),
                totalEvents, executedEvents, successfulEvents, failedEvents, escalatedEvents,
                List.copyOf(executions), Map.of()
            );
        }
    }
}
