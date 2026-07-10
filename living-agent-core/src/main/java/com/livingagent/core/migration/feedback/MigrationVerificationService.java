package com.livingagent.core.migration.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class MigrationVerificationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationVerificationService.class);

    private final CrossLoopEventBus eventBus;
    private final Map<String, MigrationRecord> migrationRecords = new ConcurrentHashMap<>();
    private final LongAdder totalMigrations = new LongAdder();
    private final LongAdder failedMigrations = new LongAdder();
    private final LongAdder rolledBack = new LongAdder();

    public MigrationVerificationService(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordMigrationStart(String migrationId, String description) {
        migrationRecords.computeIfAbsent(migrationId, k -> new MigrationRecord());
        totalMigrations.increment();
        log.info("[闭环62] 迁移开始: id={}, desc={}", migrationId, description);
    }

    public void recordMigrationComplete(String migrationId, boolean verified) {
        MigrationRecord record = migrationRecords.get(migrationId);
        if (record != null) {
            record.verified = verified;
            record.completed = true;
            if (!verified) {
                failedMigrations.increment();
                log.error("[闭环62] 迁移验证失败: id={}", migrationId);
                eventBus.publish(62, "auth_error", CrossLoopEvent.EventPriority.SECURITY,
                    Map.of("content", String.format("Migration %s verification failed", migrationId), "source", "migration"));
            }
        }
    }

    public void recordRollback(String migrationId, String reason) {
        rolledBack.increment();
        log.warn("[闭环62] 迁移回滚: id={}, reason={}", migrationId, reason);
    }

    public MigrationVerificationReport getReport() {
        long total = totalMigrations.sum();
        long failed = failedMigrations.sum();
        long rb = rolledBack.sum();
        double successRate = total > 0 ? 1.0 - (double) failed / total : 1.0;
        return new MigrationVerificationReport(total, failed, rb, successRate);
    }

    public static class MigrationRecord {
        boolean completed;
        boolean verified;
    }

    public record MigrationVerificationReport(long totalMigrations, long failedMigrations,
                                               long rolledBack, double successRate) {}
}
