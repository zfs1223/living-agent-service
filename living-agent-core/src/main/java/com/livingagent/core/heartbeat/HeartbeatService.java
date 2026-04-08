package com.livingagent.core.heartbeat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HeartbeatService {

    void enqueueWakeup(String employeeId, WakeSource source, WakeOptions options);

    void executeRun(HeartbeatRun run);

    void completeRun(String runId, RunResult result);

    Optional<HeartbeatRun> getRun(String runId);

    List<HeartbeatRun> getPendingRuns();

    List<HeartbeatRun> getRunsByEmployee(String employeeId);

    List<HeartbeatRun> getRunsByStatus(RunStatus status);

    void cancelRun(String runId);

    HeartbeatStatistics getStatistics(String employeeId);

    void schedulePeriodicWakeup(String employeeId, Duration interval);

    enum WakeSource {
        TIMER,
        TASK_ASSIGNED,
        ON_DEMAND,
        EVOLUTION,
        PROFIT_OPPORTUNITY,
        HEALTH_CHECK,
        KNOWLEDGE_UPDATE
    }

    enum RunStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    record WakeOptions(
            Duration maxDuration,
            List<String> allowedActions,
            String priority,
            String context,
            boolean requireSuccess
    ) {}

    record RunResult(
            boolean success,
            String message,
            List<String> actionsTaken,
            Duration actualDuration,
            Instant completedAt,
            String error
    ) {}

    record HeartbeatStatistics(
            int totalRuns,
            int successfulRuns,
            int failedRuns,
            Duration averageRunDuration,
            Instant lastRunAt,
            List<String> mostCommonActions
    ) {}
}
