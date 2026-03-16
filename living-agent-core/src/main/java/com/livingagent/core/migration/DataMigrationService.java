package com.livingagent.core.migration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DataMigrationService {

    MigrationResult migrateKnowledgeToPostgres(MigrationConfig config);

    MigrationResult migrateKnowledgeToQdrant(MigrationConfig config);

    MigrationResult migrateAll(MigrationConfig config);

    MigrationStatus getMigrationStatus(String migrationId);

    List<MigrationRecord> getMigrationHistory(int limit);

    ValidationResult validateMigration(String migrationId);

    boolean rollbackMigration(String migrationId);

    record MigrationConfig(
        String sourceType,
        String targetType,
        boolean includePrivateKnowledge,
        boolean includeDomainKnowledge,
        boolean includeSharedKnowledge,
        int batchSize,
        boolean dryRun,
        boolean continueOnError,
        Map<String, Object> options
    ) {
        public static MigrationConfig defaults() {
            return new MigrationConfig(
                "sqlite", "postgres",
                true, true, true,
                1000, false, false,
                Map.of()
            );
        }

        public static MigrationConfig toQdrant() {
            return new MigrationConfig(
                "postgres", "qdrant",
                true, true, true,
                500, false, false,
                Map.of("generateEmbeddings", true)
            );
        }

        public static MigrationConfig forDryRun() {
            return new MigrationConfig(
                "sqlite", "postgres",
                true, true, true,
                100, true, false,
                Map.of()
            );
        }
    }

    record MigrationResult(
        String migrationId,
        boolean success,
        int totalRecords,
        int migratedRecords,
        int failedRecords,
        int skippedRecords,
        long durationMs,
        List<MigrationError> errors,
        Instant startedAt,
        Instant completedAt
    ) {
        public static MigrationResult success(String id, int total, int migrated, long duration) {
            return new MigrationResult(id, true, total, migrated, 0, 0, duration, List.of(), Instant.now().minusMillis(duration), Instant.now());
        }

        public static MigrationResult partial(String id, int total, int migrated, int failed, long duration, List<MigrationError> errors) {
            return new MigrationResult(id, migrated > 0, total, migrated, failed, 0, duration, errors, Instant.now().minusMillis(duration), Instant.now());
        }

        public static MigrationResult failure(String id, List<MigrationError> errors) {
            return new MigrationResult(id, false, 0, 0, 0, 0, 0, errors, Instant.now(), Instant.now());
        }
    }

    record MigrationStatus(
        String migrationId,
        String status,
        int totalRecords,
        int processedRecords,
        int currentBatch,
        int totalBatches,
        double progress,
        Instant startedAt,
        Instant estimatedCompletion,
        String currentPhase
    ) {}

    record MigrationRecord(
        String migrationId,
        String sourceType,
        String targetType,
        int totalRecords,
        int migratedRecords,
        boolean success,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> metadata
    ) {}

    record MigrationError(
        String recordId,
        String errorType,
        String errorMessage,
        String details,
        Instant timestamp
    ) {
        public static MigrationError of(String recordId, String errorType, String message) {
            return new MigrationError(recordId, errorType, message, null, Instant.now());
        }
    }

    record ValidationResult(
        String migrationId,
        boolean valid,
        int sourceCount,
        int targetCount,
        int missingCount,
        int extraCount,
        List<String> sampleMissing,
        List<String> sampleExtra,
        Map<String, Object> statistics
    ) {
        public static ValidationResult valid(String id, int source, int target) {
            return new ValidationResult(id, source == target, source, target, 0, 0, List.of(), List.of(), Map.of());
        }

        public static ValidationResult invalid(String id, int source, int target, List<String> missing) {
            return new ValidationResult(id, false, source, target, missing.size(), 0, missing.stream().limit(10).toList(), List.of(), Map.of());
        }
    }
}
