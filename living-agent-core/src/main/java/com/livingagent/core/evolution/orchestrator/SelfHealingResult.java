package com.livingagent.core.evolution.orchestrator;

import java.time.Instant;
import java.util.UUID;

public record SelfHealingResult(
    String resultId,
    String issueId,
    boolean success,
    String rootCause,
    String patchApplied,
    boolean verified,
    boolean experienceCaptured,
    boolean needsHumanApproval,
    long durationMs,
    Instant completedAt
) {
    public static SelfHealingResult success(String issueId, String action, Instant start) {
        return new SelfHealingResult(UUID.randomUUID().toString(), issueId, true, "auto", action,
            true, false, false, System.currentTimeMillis() - start.toEpochMilli(), Instant.now());
    }

    public static SelfHealingResult failure(String issueId, String reason, Instant start) {
        return new SelfHealingResult(UUID.randomUUID().toString(), issueId, false, reason, null,
            false, false, false, System.currentTimeMillis() - start.toEpochMilli(), Instant.now());
    }

    public static SelfHealingResult escalated(String issueId, String action, Instant start) {
        return new SelfHealingResult(UUID.randomUUID().toString(), issueId, true, "escalated", action,
            false, false, true, System.currentTimeMillis() - start.toEpochMilli(), Instant.now());
    }
}
