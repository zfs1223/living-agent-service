package com.livingagent.core.knowledge.impl;

import com.livingagent.core.knowledge.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SQLiteKnowledgeBase implements KnowledgeBase {
    
    private static final Logger log = LoggerFactory.getLogger(SQLiteKnowledgeBase.class);
    
    private final String dbPath;
    private final int vectorDimension;
    private final Map<String, float[]> vectorCache;
    
    public SQLiteKnowledgeBase(String dbPath, int vectorDimension) {
        this.dbPath = dbPath;
        this.vectorDimension = vectorDimension;
        this.vectorCache = new ConcurrentHashMap<>();
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_entries (
                    entry_id TEXT PRIMARY KEY,
                    key TEXT UNIQUE NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT,
                    tags TEXT,
                    metadata TEXT,
                    created_at TEXT,
                    updated_at TEXT,
                    access_count INTEGER DEFAULT 0,
                    relevance_score REAL DEFAULT 0.0,
                    source TEXT
                )
                """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_vectors (
                    entry_id TEXT PRIMARY KEY,
                    vector BLOB NOT NULL,
                    FOREIGN KEY (entry_id) REFERENCES knowledge_entries(entry_id) ON DELETE CASCADE
                )
                """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS experiences (
                    experience_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    context TEXT,
                    steps TEXT,
                    parameters TEXT,
                    results TEXT,
                    successful INTEGER,
                    lesson_learned TEXT,
                    tags TEXT,
                    occurred_at TEXT,
                    recorded_at TEXT,
                    recorded_by TEXT,
                    usefulness_score INTEGER DEFAULT 0
                )
                """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS best_practices (
                    practice_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    description TEXT,
                    steps TEXT,
                    prerequisites TEXT,
                    expected_outcomes TEXT,
                    common_pitfalls TEXT,
                    applicability_score INTEGER DEFAULT 100,
                    success_rate INTEGER DEFAULT 0,
                    created_at TEXT,
                    last_applied TEXT,
                    application_count INTEGER DEFAULT 0,
                    author TEXT
                )
                """);
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_key ON knowledge_entries(key)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_category ON knowledge_entries(category)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_experience_context ON experiences(context)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_practice_domain ON best_practices(domain)");
            
            log.info("Knowledge base initialized at: {}", dbPath);
        } catch (SQLException e) {
            log.error("Failed to initialize knowledge base", e);
            throw new RuntimeException("Failed to initialize knowledge base", e);
        }
    }
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
    
    @Override
    public void store(String key, Object knowledge, Map<String, String> metadata) {
        String entryId = UUID.randomUUID().toString();
        String content = serializeObject(knowledge);
        String tagsJson = metadata != null ? serializeObject(metadata) : "{}";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO knowledge_entries " +
                 "(entry_id, key, content, category, tags, metadata, created_at, updated_at, source) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            ps.setString(1, entryId);
            ps.setString(2, key);
            ps.setString(3, content);
            ps.setString(4, metadata != null ? metadata.getOrDefault("category", "general") : "general");
            ps.setString(5, tagsJson);
            ps.setString(6, tagsJson);
            ps.setString(7, Instant.now().toString());
            ps.setString(8, Instant.now().toString());
            ps.setString(9, metadata != null ? metadata.getOrDefault("source", "unknown") : "unknown");
            
            ps.executeUpdate();
            log.debug("Stored knowledge: {}", key);
        } catch (SQLException e) {
            log.error("Failed to store knowledge: {}", key, e);
        }
    }
    
    @Override
    public Optional<Object> retrieve(String key) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT content, access_count FROM knowledge_entries WHERE key = ?")) {
            
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                String content = rs.getString("content");
                int accessCount = rs.getInt("access_count");
                
                try (PreparedStatement updatePs = conn.prepareStatement(
                     "UPDATE knowledge_entries SET access_count = ?, updated_at = ? WHERE key = ?")) {
                    updatePs.setInt(1, accessCount + 1);
                    updatePs.setString(2, Instant.now().toString());
                    updatePs.setString(3, key);
                    updatePs.executeUpdate();
                }
                
                return Optional.ofNullable(deserializeObject(content));
            }
        } catch (SQLException e) {
            log.error("Failed to retrieve knowledge: {}", key, e);
        }
        return Optional.empty();
    }
    
    @Override
    public List<KnowledgeEntry> search(String query) {
        List<KnowledgeEntry> results = new ArrayList<>();
        String searchPattern = "%" + query.toLowerCase() + "%";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM knowledge_entries WHERE " +
                 "LOWER(key) LIKE ? OR LOWER(content) LIKE ? OR LOWER(tags) LIKE ? " +
                 "ORDER BY relevance_score DESC, access_count DESC LIMIT 20")) {
            
            ps.setString(1, searchPattern);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapToEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to search knowledge: {}", query, e);
        }
        return results;
    }
    
    @Override
    public List<KnowledgeEntry> getByCategory(String category) {
        List<KnowledgeEntry> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM knowledge_entries WHERE category = ? ORDER BY relevance_score DESC")) {
            
            ps.setString(1, category);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapToEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get knowledge by category: {}", category, e);
        }
        return results;
    }
    
    @Override
    public List<KnowledgeEntry> getByTag(String tag) {
        List<KnowledgeEntry> results = new ArrayList<>();
        String searchPattern = "%" + tag + "%";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM knowledge_entries WHERE tags LIKE ? ORDER BY relevance_score DESC")) {
            
            ps.setString(1, searchPattern);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapToEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get knowledge by tag: {}", tag, e);
        }
        return results;
    }
    
    @Override
    public void update(String key, Object knowledge) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE knowledge_entries SET content = ?, updated_at = ? WHERE key = ?")) {
            
            ps.setString(1, serializeObject(knowledge));
            ps.setString(2, Instant.now().toString());
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update knowledge: {}", key, e);
        }
    }
    
    @Override
    public void delete(String key) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM knowledge_entries WHERE key = ?")) {
            
            ps.setString(1, key);
            ps.executeUpdate();
            vectorCache.remove(key);
        } catch (SQLException e) {
            log.error("Failed to delete knowledge: {}", key, e);
        }
    }

    @Override
    public Optional<KnowledgeEntry> retrieveEntry(String key) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM knowledge_entries WHERE key = ?")) {

            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapToEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to retrieve knowledge entry: {}", key, e);
        }
        return Optional.empty();
    }
    
    @Override
    public void addExperience(Experience experience) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO experiences (experience_id, title, description, context, steps, " +
                 "parameters, results, successful, lesson_learned, tags, occurred_at, recorded_at, " +
                 "recorded_by, usefulness_score) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            ps.setString(1, experience.getExperienceId());
            ps.setString(2, experience.getTitle());
            ps.setString(3, experience.getDescription());
            ps.setString(4, experience.getContext());
            ps.setString(5, serializeObject(experience.getSteps()));
            ps.setString(6, serializeObject(experience.getParameters()));
            ps.setString(7, serializeObject(experience.getResults()));
            ps.setInt(8, experience.isSuccessful() ? 1 : 0);
            ps.setString(9, experience.getLessonLearned());
            ps.setString(10, serializeObject(experience.getTags()));
            ps.setString(11, experience.getOccurredAt().toString());
            ps.setString(12, experience.getRecordedAt().toString());
            ps.setString(13, experience.getRecordedBy());
            ps.setInt(14, experience.getUsefulnessScore());
            
            ps.executeUpdate();
            log.info("Added experience: {}", experience.getTitle());
        } catch (SQLException e) {
            log.error("Failed to add experience", e);
        }
    }
    
    @Override
    public List<Experience> getExperiences(String context) {
        List<Experience> results = new ArrayList<>();
        String searchPattern = "%" + context + "%";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM experiences WHERE context LIKE ? OR tags LIKE ? " +
                 "ORDER BY usefulness_score DESC LIMIT 10")) {
            
            ps.setString(1, searchPattern);
            ps.setString(2, searchPattern);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                results.add(mapToExperience(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get experiences: {}", context, e);
        }
        return results;
    }
    
    @Override
    public void shareKnowledge(String key, String targetAgent) {
        log.info("Sharing knowledge {} with agent {}", key, targetAgent);
    }
    
    @Override
    public List<BestPractice> getBestPractices(String domain) {
        List<BestPractice> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM best_practices WHERE domain = ? ORDER BY success_rate DESC")) {
            
            ps.setString(1, domain);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                results.add(mapToBestPractice(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get best practices: {}", domain, e);
        }
        return results;
    }
    
    @Override
    public void recordBestPractice(BestPractice practice) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO best_practices (practice_id, title, domain, description, " +
                 "steps, prerequisites, expected_outcomes, common_pitfalls, applicability_score, " +
                 "success_rate, created_at, last_applied, application_count, author) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            ps.setString(1, practice.getPracticeId());
            ps.setString(2, practice.getTitle());
            ps.setString(3, practice.getDomain());
            ps.setString(4, practice.getDescription());
            ps.setString(5, serializeObject(practice.getSteps()));
            ps.setString(6, serializeObject(practice.getPrerequisites()));
            ps.setString(7, serializeObject(practice.getExpectedOutcomes()));
            ps.setString(8, serializeObject(practice.getCommonPitfalls()));
            ps.setInt(9, practice.getApplicabilityScore());
            ps.setInt(10, practice.getSuccessRate());
            ps.setString(11, practice.getCreatedAt().toString());
            ps.setString(12, practice.getLastApplied() != null ? practice.getLastApplied().toString() : null);
            ps.setInt(13, practice.getApplicationCount());
            ps.setString(14, practice.getAuthor());
            
            ps.executeUpdate();
            log.info("Recorded best practice: {}", practice.getTitle());
        } catch (SQLException e) {
            log.error("Failed to record best practice", e);
        }
    }
    
    @Override
    public List<KnowledgeEntry> searchSimilar(float[] vector, int limit) {
        List<KnowledgeEntry> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT entry_id, vector FROM knowledge_vectors")) {
            
            List<Map.Entry<String, Double>> similarities = new ArrayList<>();
            
            while (rs.next()) {
                String entryId = rs.getString("entry_id");
                byte[] vectorBytes = rs.getBytes("vector");
                float[] storedVector = bytesToVector(vectorBytes);
                double similarity = cosineSimilarity(vector, storedVector);
                similarities.add(Map.entry(entryId, similarity));
            }
            
            similarities.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            
            for (int i = 0; i < Math.min(limit, similarities.size()); i++) {
                String entryId = similarities.get(i).getKey();
                try (PreparedStatement entryPs = conn.prepareStatement(
                     "SELECT * FROM knowledge_entries WHERE entry_id = ?")) {
                    entryPs.setString(1, entryId);
                    ResultSet entryRs = entryPs.executeQuery();
                    if (entryRs.next()) {
                        results.add(mapToEntry(entryRs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to search similar vectors", e);
        }
        return results;
    }
    
    @Override
    public void storeWithVector(String key, Object knowledge, float[] embedding, Map<String, String> metadata) {
        store(key, knowledge, metadata);
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO knowledge_vectors (entry_id, vector) " +
                 "SELECT entry_id, ? FROM knowledge_entries WHERE key = ?")) {
            
            ps.setBytes(1, vectorToBytes(embedding));
            ps.setString(2, key);
            ps.executeUpdate();
            
            vectorCache.put(key, embedding);
        } catch (SQLException e) {
            log.error("Failed to store knowledge with vector: {}", key, e);
        }
    }
    
    @Override
    public List<KnowledgeEntry> hybridSearch(String query, float[] queryVector, 
                                              double vectorWeight, double keywordWeight, int limit) {
        List<KnowledgeEntry> keywordResults = search(query);
        List<KnowledgeEntry> vectorResults = searchSimilar(queryVector, limit * 2);
        
        Map<String, Double> combinedScores = new HashMap<>();
        
        for (int i = 0; i < keywordResults.size(); i++) {
            KnowledgeEntry entry = keywordResults.get(i);
            double score = keywordWeight * (1.0 - (double) i / keywordResults.size());
            combinedScores.merge(entry.getKey(), score, Double::sum);
        }
        
        for (int i = 0; i < vectorResults.size(); i++) {
            KnowledgeEntry entry = vectorResults.get(i);
            double score = vectorWeight * (1.0 - (double) i / vectorResults.size());
            combinedScores.merge(entry.getKey(), score, Double::sum);
        }
        
        return combinedScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(e -> retrieve(e.getKey()).map(obj -> {
                KnowledgeEntry entry = new KnowledgeEntry(e.getKey(), obj);
                entry.setRelevanceScore(e.getValue());
                return entry;
            }).orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }
    
    @Override
    public int getKnowledgeCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM knowledge_entries")) {
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }
    
    @Override
    public int getExperienceCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM experiences")) {
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }
    
    @Override
    public void cleanupExpiredKnowledge(int daysOld) {
        String cutoffDate = Instant.now().minusSeconds(daysOld * 86400L).toString();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM knowledge_entries WHERE updated_at < ? AND access_count = 0")) {
            
            ps.setString(1, cutoffDate);
            int deleted = ps.executeUpdate();
            log.info("Cleaned up {} expired knowledge entries", deleted);
        } catch (SQLException e) {
            log.error("Failed to cleanup expired knowledge", e);
        }
    }
    
    @Override
    public void updateKnowledgeRelevance(String key, double relevanceDelta) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE knowledge_entries SET relevance_score = relevance_score + ?, updated_at = ? WHERE key = ?")) {
            
            ps.setDouble(1, relevanceDelta);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update knowledge relevance: {}", key, e);
        }
    }
    
    @Override
    public List<KnowledgeEntry> getMostAccessed(int limit) {
        List<KnowledgeEntry> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM knowledge_entries ORDER BY access_count DESC LIMIT ?")) {
            
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapToEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get most accessed knowledge", e);
        }
        return results;
    }
    
    @Override
    public List<KnowledgeEntry> getRecentlyUpdated(int limit) {
        List<KnowledgeEntry> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM knowledge_entries ORDER BY updated_at DESC LIMIT ?")) {
            
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapToEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get recently updated knowledge", e);
        }
        return results;
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("knowledgeCount", getKnowledgeCount());
        stats.put("experienceCount", getExperienceCount());
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM best_practices");
            stats.put("bestPracticeCount", rs.getInt(1));
            
            rs = stmt.executeQuery("SELECT COUNT(*) FROM knowledge_vectors");
            stats.put("vectorizedCount", rs.getInt(1));
            
            rs = stmt.executeQuery("SELECT category, COUNT(*) as cnt FROM knowledge_entries GROUP BY category");
            Map<String, Integer> categoryCounts = new HashMap<>();
            while (rs.next()) {
                categoryCounts.put(rs.getString("category"), rs.getInt("cnt"));
            }
            stats.put("categoryDistribution", categoryCounts);
            
        } catch (SQLException e) {
            log.error("Failed to get statistics", e);
        }
        
        return stats;
    }
    
    private KnowledgeEntry mapToEntry(ResultSet rs) throws SQLException {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setEntryId(rs.getString("entry_id"));
        entry.setKey(rs.getString("key"));
        entry.setContent(deserializeObject(rs.getString("content")));
        entry.setCategory(rs.getString("category"));
        Map<String, Object> tagMap = deserializeMap(rs.getString("tags"));
        Map<String, String> tags = new HashMap<>();
        tagMap.forEach((k, v) -> tags.put(k, v != null ? v.toString() : null));
        entry.setTags(tags);

        Map<String, Object> metaMap = deserializeMap(rs.getString("metadata"));
        KnowledgeMetadata metadata = new KnowledgeMetadata();
        Object source = metaMap.get("source");
        if (source != null) metadata.setSource(source.toString());
        Object brainDomain = metaMap.get("brainDomain");
        if (brainDomain != null) metadata.setBrainDomain(brainDomain.toString());
        Object neuronId = metaMap.get("neuronId");
        if (neuronId != null) metadata.setNeuronId(neuronId.toString());
        Object confidence = metaMap.get("confidence");
        if (confidence instanceof Number c) {
            metadata.setConfidence(c.doubleValue());
        }
        Object accessCount = metaMap.get("accessCount");
        if (accessCount instanceof Number a) {
            metadata.setAccessCount(a.intValue());
        }
        // 其余字段放入 extra
        metaMap.forEach((k, v) -> {
            if (!"source".equals(k)
                && !"brainDomain".equals(k)
                && !"neuronId".equals(k)
                && !"confidence".equals(k)
                && !"accessCount".equals(k)) {
                metadata.addExtra(k, v);
            }
        });
        entry.setMetadata(metadata);
        entry.setCreatedAt(Instant.parse(rs.getString("created_at")));
        entry.setUpdatedAt(Instant.parse(rs.getString("updated_at")));
        entry.setAccessCount(rs.getInt("access_count"));
        entry.setRelevanceScore(rs.getDouble("relevance_score"));
        entry.setSource(rs.getString("source"));
        return entry;
    }
    
    private Experience mapToExperience(ResultSet rs) throws SQLException {
        Experience exp = new Experience();
        exp.setExperienceId(rs.getString("experience_id"));
        exp.setTitle(rs.getString("title"));
        exp.setDescription(rs.getString("description"));
        exp.setContext(rs.getString("context"));
        exp.setSteps(deserializeList(rs.getString("steps")));
        exp.setParameters(deserializeMap(rs.getString("parameters")));
        exp.setResults(deserializeMap(rs.getString("results")));
        exp.setSuccessful(rs.getInt("successful") == 1);
        exp.setLessonLearned(rs.getString("lesson_learned"));
        exp.setTags(deserializeList(rs.getString("tags")));
        exp.setOccurredAt(Instant.parse(rs.getString("occurred_at")));
        exp.setRecordedAt(Instant.parse(rs.getString("recorded_at")));
        exp.setRecordedBy(rs.getString("recorded_by"));
        exp.setUsefulnessScore(rs.getInt("usefulness_score"));
        return exp;
    }
    
    private BestPractice mapToBestPractice(ResultSet rs) throws SQLException {
        BestPractice bp = new BestPractice();
        bp.setPracticeId(rs.getString("practice_id"));
        bp.setTitle(rs.getString("title"));
        bp.setDomain(rs.getString("domain"));
        bp.setDescription(rs.getString("description"));
        bp.setSteps(deserializeList(rs.getString("steps")));
        bp.setPrerequisites(deserializeList(rs.getString("prerequisites")));
        bp.setExpectedOutcomes(deserializeList(rs.getString("expected_outcomes")));
        bp.setCommonPitfalls(deserializeList(rs.getString("common_pitfalls")));
        bp.setApplicabilityScore(rs.getInt("applicability_score"));
        bp.setSuccessRate(rs.getInt("success_rate"));
        bp.setCreatedAt(Instant.parse(rs.getString("created_at")));
        String lastApplied = rs.getString("last_applied");
        if (lastApplied != null) {
            bp.setLastApplied(Instant.parse(lastApplied));
        }
        bp.setApplicationCount(rs.getInt("application_count"));
        bp.setAuthor(rs.getString("author"));
        return bp;
    }
    
    private String serializeObject(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
    
    private Object deserializeObject(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeMap(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<String> deserializeList(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    private byte[] vectorToBytes(float[] vector) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(vector.length * 4);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }
    
    private float[] bytesToVector(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
    
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator > 0 ? dotProduct / denominator : 0;
    }
}
