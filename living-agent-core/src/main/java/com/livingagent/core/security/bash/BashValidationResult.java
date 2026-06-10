package com.livingagent.core.security.bash;

public record BashValidationResult(
    boolean isSafe,
    String threatType,
    String reason,
    BashSeverity severity
) {
    public enum BashSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public static BashValidationResult safe() {
        return new BashValidationResult(true, "SAFE", null, BashSeverity.LOW);
    }

    public static BashValidationResult threat(String threatType, String reason, BashSeverity severity) {
        return new BashValidationResult(false, threatType, reason, severity);
    }

    public boolean shouldDeny() {
        return !isSafe && (severity == BashSeverity.HIGH || severity == BashSeverity.CRITICAL);
    }

    public boolean shouldAsk() {
        return !isSafe && severity == BashSeverity.MEDIUM;
    }
}
