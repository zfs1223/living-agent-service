package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * R5: 数据迁移记录持久化实体。
 * 替代 DataMigrationServiceImpl 中的内存 migrationHistory 列表，重启后迁移历史不丢失。
 */
@Entity
@Table(name = "migration_records", indexes = {
    @Index(name = "idx_migration_id", columnList = "migration_id"),
    @Index(name = "idx_migration_source", columnList = "source_type"),
    @Index(name = "idx_migration_created", columnList = "started_at")
})
public class MigrationRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "migration_id", nullable = false, length = 100, unique = true)
    private String migrationId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "migrated_records", nullable = false)
    private int migratedRecords;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public MigrationRecordEntity() {}

    public Long getId() { return id; }
    public String getMigrationId() { return migrationId; }
    public void setMigrationId(String migrationId) { this.migrationId = migrationId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    public int getMigratedRecords() { return migratedRecords; }
    public void setMigratedRecords(int migratedRecords) { this.migratedRecords = migratedRecords; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
