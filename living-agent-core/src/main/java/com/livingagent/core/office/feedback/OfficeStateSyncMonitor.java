package com.livingagent.core.office.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class OfficeStateSyncMonitor {

    private static final Logger log = LoggerFactory.getLogger(OfficeStateSyncMonitor.class);
    private static final long SYNC_DELAY_WARNING_MS = 30000;

    private final CrossLoopEventBus eventBus;
    private final Map<String, OfficeSyncState> officeStateMap = new ConcurrentHashMap<>();
    private final LongAdder totalSnapshots = new LongAdder();
    private final LongAdder inconsistentSnapshots = new LongAdder();

    public OfficeStateSyncMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordSnapshot(String officeId, boolean consistent, long syncDelayMs) {
        OfficeSyncState state = officeStateMap.computeIfAbsent(officeId, k -> new OfficeSyncState());
        totalSnapshots.increment();
        if (!consistent) {
            inconsistentSnapshots.increment();
            state.inconsistentCount.increment();
        }
        state.lastSyncDelayMs = syncDelayMs;

        if (syncDelayMs > SYNC_DELAY_WARNING_MS) {
            log.warn("[闭环56] 办公室同步延迟: id={}, delay={}ms", officeId, syncDelayMs);
            eventBus.publish(56, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Office %s sync delay %dms exceeds %dms threshold", officeId, syncDelayMs, SYNC_DELAY_WARNING_MS)));
        }
    }

    public OfficeSyncReport getReport(String officeId) {
        OfficeSyncState state = officeStateMap.get(officeId);
        long total = totalSnapshots.sum();
        long inconsistent = inconsistentSnapshots.sum();
        double consistencyRate = total > 0 ? 1.0 - (double) inconsistent / total : 1.0;
        long delay = state != null ? state.lastSyncDelayMs : 0;
        return new OfficeSyncReport(officeId, consistencyRate, delay, total);
    }

    public static class OfficeSyncState {
        long lastSyncDelayMs;
        LongAdder inconsistentCount = new LongAdder();
    }

    public record OfficeSyncReport(String officeId, double consistencyRate,
                                    long lastSyncDelayMs, long totalSnapshots) {}
}
