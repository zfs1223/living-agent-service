package com.livingagent.core.nativelib;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class NativeCallMetrics {

    private final String operationName;
    private final LongAdder totalCalls = new LongAdder();
    private final LongAdder successCalls = new LongAdder();
    private final LongAdder failureCalls = new LongAdder();
    private final LongAdder totalDurationMs = new LongAdder();
    private final AtomicLong minDurationMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxDurationMs = new AtomicLong(0);
    private volatile Instant lastCallTime;
    private volatile String lastError;

    public NativeCallMetrics(String operationName) {
        this.operationName = operationName;
    }

    public void recordSuccess(long durationMs) {
        totalCalls.increment();
        successCalls.increment();
        totalDurationMs.add(durationMs);
        updateMinMax(durationMs);
        lastCallTime = Instant.now();
    }

    public void recordFailure(long durationMs, String error) {
        totalCalls.increment();
        failureCalls.increment();
        totalDurationMs.add(durationMs);
        updateMinMax(durationMs);
        lastCallTime = Instant.now();
        lastError = error;
    }

    private void updateMinMax(long durationMs) {
        minDurationMs.updateAndGet(current -> Math.min(current, durationMs));
        maxDurationMs.updateAndGet(current -> Math.max(current, durationMs));
    }

    public String getOperationName() { return operationName; }
    public long getTotalCalls() { return totalCalls.sum(); }
    public long getSuccessCalls() { return successCalls.sum(); }
    public long getFailureCalls() { return failureCalls.sum(); }
    public long getTotalDurationMs() { return totalDurationMs.sum(); }
    public long getMinDurationMs() { return getTotalCalls() > 0 ? minDurationMs.get() : 0; }
    public long getMaxDurationMs() { return maxDurationMs.get(); }
    public Instant getLastCallTime() { return lastCallTime; }
    public String getLastError() { return lastError; }

    public double getSuccessRate() {
        long total = getTotalCalls();
        return total > 0 ? (double) successCalls.sum() / total : 1.0;
    }

    public double getAvgDurationMs() {
        long total = getTotalCalls();
        return total > 0 ? (double) totalDurationMs.sum() / total : 0.0;
    }

    public void reset() {
        totalCalls.reset();
        successCalls.reset();
        failureCalls.reset();
        totalDurationMs.reset();
        minDurationMs.set(Long.MAX_VALUE);
        maxDurationMs.set(0);
        lastCallTime = null;
        lastError = null;
    }

    @Override
    public String toString() {
        return String.format("NativeCallMetrics[%s: calls=%d, success=%.1f%%, avg=%.1fms, min=%dms, max=%dms]",
            operationName, getTotalCalls(), getSuccessRate() * 100, getAvgDurationMs(),
            getMinDurationMs(), getMaxDurationMs());
    }
}
