package com.livingagent.gateway.controller;

import com.livingagent.core.embedding.optimization.VectorIndexOptimizer;
import com.livingagent.core.migration.DataMigrationService;
import com.livingagent.core.migration.DataMigrationService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * R5: 数据迁移与索引管理 REST API。
 * 提供迁移触发、状态查询、验证回滚和索引优化管理能力。
 */
@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    private final DataMigrationService migrationService;
    private final VectorIndexOptimizer indexOptimizer;

    public MigrationController(DataMigrationService migrationService,
                               VectorIndexOptimizer indexOptimizer) {
        this.migrationService = migrationService;
        this.indexOptimizer = indexOptimizer;
    }

    @PostMapping("/to-postgres")
    public ResponseEntity<Map<String, Object>> migrateToPostgres(
            @RequestParam(defaultValue = "true") boolean includePrivate,
            @RequestParam(defaultValue = "true") boolean includeDomain,
            @RequestParam(defaultValue = "true") boolean includeShared,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        MigrationConfig config = new MigrationConfig(
            "sqlite", "postgres", includePrivate, includeDomain, includeShared,
            1000, dryRun, true, Map.of()
        );
        MigrationResult result = migrationService.migrateKnowledgeToPostgres(config);
        return ResponseEntity.ok(Map.of(
            "migrationId", result.migrationId(),
            "success", result.success(),
            "totalRecords", result.totalRecords(),
            "migratedRecords", result.migratedRecords(),
            "failedRecords", result.failedRecords(),
            "durationMs", result.durationMs()
        ));
    }

    @PostMapping("/to-qdrant")
    public ResponseEntity<Map<String, Object>> migrateToQdrant(
            @RequestParam(defaultValue = "true") boolean includePrivate,
            @RequestParam(defaultValue = "true") boolean includeDomain,
            @RequestParam(defaultValue = "true") boolean includeShared) {
        MigrationConfig config = new MigrationConfig(
            "postgres", "qdrant", includePrivate, includeDomain, includeShared,
            500, false, true, Map.of("generateEmbeddings", true)
        );
        MigrationResult result = migrationService.migrateKnowledgeToQdrant(config);
        return ResponseEntity.ok(Map.of(
            "migrationId", result.migrationId(),
            "success", result.success(),
            "totalRecords", result.totalRecords(),
            "migratedRecords", result.migratedRecords(),
            "failedRecords", result.failedRecords(),
            "durationMs", result.durationMs()
        ));
    }

    @PostMapping("/migrate-all")
    public ResponseEntity<Map<String, Object>> migrateAll() {
        MigrationResult result = migrationService.migrateAll(MigrationConfig.defaults());
        return ResponseEntity.ok(Map.of(
            "migrationId", result.migrationId(),
            "success", result.success(),
            "totalRecords", result.totalRecords(),
            "migratedRecords", result.migratedRecords(),
            "durationMs", result.durationMs()
        ));
    }

    @GetMapping("/status/{migrationId}")
    public ResponseEntity<MigrationStatus> getStatus(@PathVariable String migrationId) {
        MigrationStatus status = migrationService.getMigrationStatus(migrationId);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(migrationService.getMigrationHistory(limit));
    }

    @PostMapping("/validate/{migrationId}")
    public ResponseEntity<ValidationResult> validate(@PathVariable String migrationId) {
        return ResponseEntity.ok(migrationService.validateMigration(migrationId));
    }

    @PostMapping("/rollback/{migrationId}")
    public ResponseEntity<Map<String, Object>> rollback(@PathVariable String migrationId) {
        boolean success = migrationService.rollbackMigration(migrationId);
        return ResponseEntity.ok(Map.of("migrationId", migrationId, "rollbackSuccess", success));
    }

    // ========== 索引管理 ==========

    @GetMapping("/index/stats")
    public ResponseEntity<Map<String, VectorIndexOptimizer.IndexStats>> getIndexStats() {
        return ResponseEntity.ok(indexOptimizer.getAllIndexStats());
    }

    @GetMapping("/index/stats/{collection}")
    public ResponseEntity<VectorIndexOptimizer.IndexStats> getCollectionIndexStats(@PathVariable String collection) {
        return ResponseEntity.ok(indexOptimizer.getIndexStats(collection));
    }

    @PostMapping("/index/optimize/{collection}")
    public ResponseEntity<Map<String, Object>> optimizeIndex(@PathVariable String collection) {
        indexOptimizer.optimizeIndex(collection);
        return ResponseEntity.ok(Map.of("collection", collection, "action", "optimize", "success", true));
    }

    @PostMapping("/index/rebuild/{collection}")
    public ResponseEntity<Map<String, Object>> rebuildIndex(@PathVariable String collection) {
        indexOptimizer.rebuildIndex(collection);
        return ResponseEntity.ok(Map.of("collection", collection, "action", "rebuild", "success", true));
    }

    @PostMapping("/index/warmup/{collection}")
    public ResponseEntity<Map<String, Object>> warmupIndex(@PathVariable String collection) {
        indexOptimizer.warmupIndex(collection);
        return ResponseEntity.ok(Map.of("collection", collection, "action", "warmup", "success", true));
    }
}
