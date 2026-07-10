package com.livingagent.core.settings.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class SettingsChangeImpactTracker {

    private static final Logger log = LoggerFactory.getLogger(SettingsChangeImpactTracker.class);
    private static final double HIGH_ROLLBACK_RATE = 0.30;

    private final CrossLoopEventBus eventBus;
    private final Map<String, SettingChangeRecord> changeMap = new ConcurrentHashMap<>();
    private final LongAdder totalChanges = new LongAdder();
    private final LongAdder totalRollbacks = new LongAdder();

    public SettingsChangeImpactTracker(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordChange(String settingKey, String oldValue, String newValue) {
        SettingChangeRecord record = changeMap.computeIfAbsent(settingKey, k -> new SettingChangeRecord());
        record.changeCount.increment();
        totalChanges.increment();
        log.info("[闭环57] 设置变更: key={}, old={}, new={}", settingKey, oldValue, newValue);
    }

    public void recordRollback(String settingKey, String reason) {
        SettingChangeRecord record = changeMap.computeIfAbsent(settingKey, k -> new SettingChangeRecord());
        record.rollbackCount.increment();
        totalRollbacks.increment();
        log.warn("[闭环57] 设置回滚: key={}, reason={}", settingKey, reason);
    }

    public void recordSystemAnomalyAfterChange(String settingKey) {
        SettingChangeRecord record = changeMap.get(settingKey);
        if (record != null) {
            record.anomalyAfterChange.increment();
        }
    }

    public SettingsImpactReport getReport() {
        long changes = totalChanges.sum();
        long rollbacks = totalRollbacks.sum();
        double rollbackRate = changes > 0 ? (double) rollbacks / changes : 0;

        if (changes > 5 && rollbackRate > HIGH_ROLLBACK_RATE) {
            log.warn("[闭环57] 设置回滚率过高: {:.0%} > {:.0%}%", rollbackRate, HIGH_ROLLBACK_RATE);
            eventBus.publish(57, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Settings rollback rate %.0f%% exceeds %.0f%%, suggest adding approval flow", rollbackRate * 100, HIGH_ROLLBACK_RATE * 100)));
        }

        return new SettingsImpactReport(changes, rollbacks, rollbackRate, changeMap.size());
    }

    public static class SettingChangeRecord {
        LongAdder changeCount = new LongAdder();
        LongAdder rollbackCount = new LongAdder();
        LongAdder anomalyAfterChange = new LongAdder();
    }

    public record SettingsImpactReport(long totalChanges, long totalRollbacks,
                                        double rollbackRate, int trackedSettings) {}
}
