package com.livingagent.core.migration.impl;

import com.livingagent.core.database.vector.QdrantVectorService;
import com.livingagent.core.embedding.EmbeddingService;
import com.livingagent.core.knowledge.*;
import com.livingagent.core.migration.DataMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DataMigrationServiceImpl implements DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationServiceImpl.class);

    private final DataSource postgresDataSource;
    private final KnowledgeManager knowledgeManager;
    private final QdrantVectorService qdrantVectorService;
    private final EmbeddingService embeddingService;

    private final Map<String, MigrationStatus> activeMigrations = new ConcurrentHashMap<>();
    private final List<MigrationRecord> migrationHistory = Collections.synchronizedList(new ArrayList<>());

    public DataMigrationServiceImpl(
            DataSource postgresDataSource,
            KnowledgeManager knowledgeManager,
            QdrantVectorService qdrantVectorService,
            EmbeddingService embeddingService) {
        this.postgresDataSource = postgresDataSource;
        this.knowledgeManager = knowledgeManager;
        this.qdrantVectorService = qdrantVectorService;
        this.embeddingService = embeddingService;
    }

    @Override
    public MigrationResult migrateKnowledgeToPostgres(MigrationConfig config) {
        String migrationId = "migrate_pg_" + System.currentTimeMillis();
        log.info("Starting migration to PostgreSQL: {}", migrationId);

        long startTime = System.currentTimeMillis();
        List<MigrationError> errors = new ArrayList<>();
        AtomicInteger migrated = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        try {
            String sqlitePath = getSqlitePath();
            List<KnowledgeEntry> entries = loadFromSqlite(sqlitePath, config);
            int total = entries.size();

            if (total == 0) {
                log.info("No entries to migrate");
                return MigrationResult.success(migrationId, 0, 0, 0);
            }

            activeMigrations.put(migrationId, new MigrationStatus(
                migrationId, "RUNNING", total, 0, 0,
                (total + config.batchSize() - 1) / config.batchSize(),
                0.0, Instant.now(), null, "MIGRATING_TO_POSTGRES"
            ));

            for (int i = 0; i < entries.size(); i += config.batchSize()) {
                int end = Math.min(i + config.batchSize(), entries.size());
                List<KnowledgeEntry> batch = entries.subList(i, end);

                for (KnowledgeEntry entry : batch) {
                    try {
                        if (!config.dryRun()) {
                            insertToPostgres(entry);
                        }
                        migrated.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        errors.add(MigrationError.of(entry.getEntryId(), "INSERT_ERROR", e.getMessage()));
                        if (!config.continueOnError()) {
                            break;
                        }
                    }
                }

                updateMigrationProgress(migrationId, end, total);
            }

            long duration = System.currentTimeMillis() - startTime;
            MigrationResult result = errors.isEmpty()
                ? MigrationResult.success(migrationId, total, migrated.get(), duration)
                : MigrationResult.partial(migrationId, total, migrated.get(), failed.get(), duration, errors);

            recordMigration(migrationId, "sqlite", "postgres", total, migrated.get(), result.success(), duration);
            activeMigrations.remove(migrationId);

            log.info("Migration to PostgreSQL completed: {} records migrated, {} failed, {}ms",
                migrated.get(), failed.get(), duration);

            return result;

        } catch (Exception e) {
            log.error("Migration to PostgreSQL failed: {}", e.getMessage(), e);
            return MigrationResult.failure(migrationId, List.of(MigrationError.of("N/A", "MIGRATION_ERROR", e.getMessage())));
        }
    }

    @Override
    public MigrationResult migrateKnowledgeToQdrant(MigrationConfig config) {
        String migrationId = "migrate_qdrant_" + System.currentTimeMillis();
        log.info("Starting migration to Qdrant: {}", migrationId);

        long startTime = System.currentTimeMillis();
        List<MigrationError> errors = new ArrayList<>();
        AtomicInteger migrated = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        try {
            List<Map<String, Object>> knowledgeRecords = loadKnowledgeFromPostgres(config);
            int total = knowledgeRecords.size();

            if (total == 0) {
                log.info("No entries to migrate to Qdrant");
                return MigrationResult.success(migrationId, 0, 0, 0);
            }

            activeMigrations.put(migrationId, new MigrationStatus(
                migrationId, "RUNNING", total, 0, 0, (total + config.batchSize() - 1) / config.batchSize(),
                0.0, Instant.now(), null, "GENERATING_EMBEDDINGS"
            ));

            for (int i = 0; i < knowledgeRecords.size(); i += config.batchSize()) {
                int end = Math.min(i + config.batchSize(), knowledgeRecords.size());
                List<Map<String, Object>> batch = knowledgeRecords.subList(i, end);

                List<String> texts = batch.stream()
                    .map(r -> String.valueOf(r.get("content")))
                    .toList();

                List<float[]> embeddings = embeddingService.embedBatch(texts);

                for (int j = 0; j < batch.size(); j++) {
                    try {
                        Map<String, Object> record = batch.get(j);
                        String id = (String) record.get("id");
                        float[] vector = embeddings.get(j);

                        if (!config.dryRun()) {
                            qdrantVectorService.upsertVector(
                                "knowledge",
                                id,
                                vector,
                                Map.of(
                                    "layer", String.valueOf(record.get("layer")),
                                    "brain_domain", String.valueOf(record.get("brain_domain")),
                                    "knowledge_type", String.valueOf(record.get("knowledge_type")),
                                    "importance", String.valueOf(record.get("importance"))
                                )
                            );
                        }
                        migrated.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        Map<String, Object> record = batch.get(j);
                        errors.add(MigrationError.of((String) record.get("id"), "QDRANT_INSERT_ERROR", e.getMessage()));
                        if (!config.continueOnError()) {
                            break;
                        }
                    }
                }

                updateMigrationProgress(migrationId, end, total);
            }

            long duration = System.currentTimeMillis() - startTime;
            MigrationResult result = errors.isEmpty()
                ? MigrationResult.success(migrationId, total, migrated.get(), duration)
                : MigrationResult.partial(migrationId, total, migrated.get(), failed.get(), duration, errors);

            recordMigration(migrationId, "postgres", "qdrant", total, migrated.get(), result.success(), duration);
            activeMigrations.remove(migrationId);

            log.info("Migration to Qdrant completed: {} records migrated, {} failed, {}ms",
                migrated.get(), failed.get(), duration);

            return result;

        } catch (Exception e) {
            log.error("Migration to Qdrant failed: {}", e.getMessage(), e);
            return MigrationResult.failure(migrationId, List.of(MigrationError.of("N/A", "MIGRATION_ERROR", e.getMessage())));
        }
    }

    @Override
    public MigrationResult migrateAll(MigrationConfig config) {
        log.info("Starting full migration pipeline");

        MigrationResult pgResult = migrateKnowledgeToPostgres(config);
        if (!pgResult.success()) {
            return pgResult;
        }

        MigrationConfig qdrantConfig = MigrationConfig.toQdrant();
        return migrateKnowledgeToQdrant(qdrantConfig);
    }

    @Override
    public MigrationStatus getMigrationStatus(String migrationId) {
        return activeMigrations.get(migrationId);
    }

    @Override
    public List<MigrationRecord> getMigrationHistory(int limit) {
        return migrationHistory.stream()
            .sorted(Comparator.comparing(MigrationRecord::startedAt).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public ValidationResult validateMigration(String migrationId) {
        Optional<MigrationRecord> record = migrationHistory.stream()
            .filter(r -> r.migrationId().equals(migrationId))
            .findFirst();

        if (record.isEmpty()) {
            return new ValidationResult(migrationId, false, 0, 0, 0, 0, List.of(), List.of(), Map.of("error", "Migration not found"));
        }

        MigrationRecord r = record.get();
        boolean valid = r.totalRecords() == r.migratedRecords();

        return new ValidationResult(
            migrationId,
            valid,
            r.totalRecords(),
            r.migratedRecords(),
            valid ? 0 : r.totalRecords() - r.migratedRecords(),
            0,
            List.of(),
            List.of(),
            Map.of("success", r.success())
        );
    }

    @Override
    public boolean rollbackMigration(String migrationId) {
        log.warn("Rollback requested for migration: {}", migrationId);
        return false;
    }

    private String getSqlitePath() {
        return "data/knowledge.db";
    }

    private List<KnowledgeEntry> loadFromSqlite(String path, MigrationConfig config) {
        List<KnowledgeEntry> entries = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement stmt = conn.createStatement()) {

            String sql = "SELECT * FROM knowledge_entries WHERE 1=1";
            if (!config.includePrivateKnowledge()) {
                sql += " AND layer != 'PRIVATE'";
            }
            if (!config.includeDomainKnowledge()) {
                sql += " AND layer != 'DOMAIN'";
            }
            if (!config.includeSharedKnowledge()) {
                sql += " AND layer != 'SHARED'";
            }

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                entries.add(mapToEntry(rs));
            }

        } catch (Exception e) {
            log.warn("Could not load from SQLite: {}, using knowledge manager", e.getMessage());
            entries.addAll(loadFromKnowledgeManager(config));
        }

        return entries;
    }

    private List<KnowledgeEntry> loadFromKnowledgeManager(MigrationConfig config) {
        List<KnowledgeEntry> entries = new ArrayList<>();

        if (config.includePrivateKnowledge()) {
            entries.addAll(knowledgeManager.searchInLayer("", KnowledgeManager.KnowledgeLayer.PRIVATE, 10000));
        }
        if (config.includeDomainKnowledge()) {
            entries.addAll(knowledgeManager.searchInLayer("", KnowledgeManager.KnowledgeLayer.DOMAIN, 10000));
        }
        if (config.includeSharedKnowledge()) {
            entries.addAll(knowledgeManager.searchInLayer("", KnowledgeManager.KnowledgeLayer.SHARED, 10000));
        }

        return entries;
    }

    private List<Map<String, Object>> loadKnowledgeFromPostgres(MigrationConfig config) {
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);

        StringBuilder sql = new StringBuilder("SELECT id, content, layer, brain_domain, knowledge_type, importance FROM knowledge_entries WHERE 1=1");
        if (!config.includePrivateKnowledge()) {
            sql.append(" AND layer != 'PRIVATE'");
        }
        if (!config.includeDomainKnowledge()) {
            sql.append(" AND layer != 'DOMAIN'");
        }
        if (!config.includeSharedKnowledge()) {
            sql.append(" AND layer != 'SHARED'");
        }

        return jdbc.queryForList(sql.toString());
    }

    private void insertToPostgres(KnowledgeEntry entry) {
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);

        String sql = """
            INSERT INTO knowledge_entries (id, key, content, layer, brain_domain, knowledge_type, importance, metadata, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, updated_at = EXCLUDED.updated_at
            """;

        jdbc.update(sql,
            entry.getEntryId(),
            entry.getKey(),
            entry.getContent() != null ? entry.getContent().toString() : "",
            entry.getCategory() != null ? entry.getCategory() : "PRIVATE",
            entry.getBrainDomain(),
            entry.getKnowledgeType() != null ? entry.getKnowledgeType().name() : "FACT",
            entry.getImportance() != null ? entry.getImportance().name() : "MEDIUM",
            "{}",
            entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now(),
            Instant.now()
        );
    }

    private KnowledgeEntry mapToEntry(ResultSet rs) throws SQLException {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setEntryId(rs.getString("id"));
        entry.setKey(rs.getString("key"));
        entry.setContent(rs.getString("content"));
        entry.setCategory(rs.getString("layer"));
        entry.setBrainDomain(rs.getString("brain_domain"));
        
        String knowledgeType = rs.getString("knowledge_type");
        if (knowledgeType != null) {
            entry.setKnowledgeType(KnowledgeType.valueOf(knowledgeType));
        }
        
        String importance = rs.getString("importance");
        if (importance != null) {
            entry.setImportance(Importance.valueOf(importance));
        }
        
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            entry.setCreatedAt(Instant.parse(createdAt));
        }
        
        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) {
            entry.setUpdatedAt(Instant.parse(updatedAt));
        }
        
        return entry;
    }

    private void updateMigrationProgress(String migrationId, int processed, int total) {
        MigrationStatus current = activeMigrations.get(migrationId);
        if (current != null && total > 0) {
            activeMigrations.put(migrationId, new MigrationStatus(
                migrationId, "RUNNING", total, processed,
                current.currentBatch() + 1, current.totalBatches(),
                (double) processed / total * 100,
                current.startedAt(),
                Instant.now().plusMillis(estimateRemaining(current.startedAt(), processed, total)),
                current.currentPhase()
            ));
        }
    }

    private long estimateRemaining(Instant started, int processed, int total) {
        if (processed == 0) return 0;
        long elapsed = System.currentTimeMillis() - started.toEpochMilli();
        return (elapsed / processed) * (total - processed);
    }

    private void recordMigration(String id, String source, String target, int total, int migrated, boolean success, long duration) {
        migrationHistory.add(new MigrationRecord(
            id, source, target, total, migrated, success,
            Instant.now().minusMillis(duration), Instant.now(),
            Map.of("durationMs", duration)
        ));
    }
}
