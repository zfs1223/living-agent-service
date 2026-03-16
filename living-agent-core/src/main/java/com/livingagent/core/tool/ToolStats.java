package com.livingagent.core.tool;

public record ToolStats(
    String toolName,
    long totalCalls,
    long successfulCalls,
    long failedCalls,
    double avgDurationMs,
    long lastCallTimestamp
) {
    public static ToolStats empty(String toolName) {
        return new ToolStats(toolName, 0, 0, 0, 0, 0);
    }

    public ToolStats recordCall(boolean success, long durationMs) {
        long newTotal = totalCalls + 1;
        long newSuccess = success ? successfulCalls + 1 : successfulCalls;
        long newFailed = success ? failedCalls : failedCalls + 1;
        double newAvg = ((avgDurationMs * totalCalls) + durationMs) / newTotal;
        return new ToolStats(toolName, newTotal, newSuccess, newFailed, newAvg, System.currentTimeMillis());
    }

    public double getSuccessRate() {
        return totalCalls > 0 ? (double) successfulCalls / totalCalls : 0;
    }
}
