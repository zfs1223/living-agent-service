package com.livingagent.core.settings.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class SettingsChangeImpactTracker {

    private static final Logger log = LoggerFactory.getLogger(SettingsChangeImpactTracker.class);
    private static final double HIGH_ROLLBACK_RATE = 0.30;

    private final CrossLoopEventBus eventBus;
    private final Map<String, SettingChangeRecord> changeMap = new ConcurrentHashMap<>();
    private final LongAdder totalChanges = new LongAdder();
    private final LongAdder totalRollbacks = new LongAdder();
    private final Set<String> lockedSettings = ConcurrentHashMap.newKeySet();

    public SettingsChangeImpactTracker(@Autowired(required = false) CrossLoopEventBus eventBus) {
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

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void checkAndLockHighRollbackSettings() {
        long changes = totalChanges.sum();
        if (changes < 5) return;
        double overallRollbackRate = (double) totalRollbacks.sum() / changes;

        // 锁定单个设置中回滚率高的
        for (Map.Entry<String, SettingChangeRecord> entry : changeMap.entrySet()) {
            String key = entry.getKey();
            SettingChangeRecord record = entry.getValue();
            long keyChanges = record.changeCount.sum();
            long keyRollbacks = record.rollbackCount.sum();
            if (keyChanges >= 3 && keyRollbacks >= 2) {
                double keyRollbackRate = (double) keyRollbacks / keyChanges;
                if (keyRollbackRate > HIGH_ROLLBACK_RATE && !lockedSettings.contains(key)) {
                    lockedSettings.add(key);
                    log.info("[闭环57] 设置{}回滚率{}%过高，已自动锁定",
                        key, String.format("%.0f", keyRollbackRate * 100));
                    if (eventBus != null) {
                        eventBus.publish(57, "setting_locked", CrossLoopEvent.EventPriority.DEGRADATION,
                            Map.of("settingKey", key, "rollbackRate", keyRollbackRate), 300);
                    }
                }
            }
        }

        if (overallRollbackRate > HIGH_ROLLBACK_RATE && eventBus != null) {
            eventBus.publish(57, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Settings rollback rate %.0f%% exceeds %.0f%%, suggest adding approval flow", overallRollbackRate * 100, HIGH_ROLLBACK_RATE * 100)));
        }
    }

    public boolean isSettingLocked(String settingKey) {
        return lockedSettings.contains(settingKey);
    }

    public Set<String> getLockedSettings() {
        return Set.copyOf(lockedSettings);
    }

    public SettingsImpactReport getReport() {
        long changes = totalChanges.sum();
        long rollbacks = totalRollbacks.sum();
        double rollbackRate = changes > 0 ? (double) rollbacks / changes : 0;

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
