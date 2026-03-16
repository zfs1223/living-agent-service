package com.livingagent.core.knowledge.impl;

import com.livingagent.core.knowledge.*;
import com.livingagent.core.knowledge.native_.NativeKnowledge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class NativeKnowledgeBase implements KnowledgeBase {
    
    private static final Logger log = LoggerFactory.getLogger(NativeKnowledgeBase.class);
    
    private final NativeKnowledge nativeKnowledge;
    private final SQLiteKnowledgeBase fallbackBackend;
    private final boolean useNative;
    private final int vectorDimension;
    
    public NativeKnowledgeBase(String dbPath, int vectorDimension, int cacheSize) {
        this.vectorDimension = vectorDimension;
        this.nativeKnowledge = new NativeKnowledge();
        
        boolean nativeAvailable = NativeKnowledge.isNativeAvailable();
        boolean tempUseNative = false;
        SQLiteKnowledgeBase tempFallbackBackend = null;
        
        if (nativeAvailable) {
            try {
                nativeKnowledge.init(dbPath, vectorDimension, cacheSize);
                tempUseNative = true;
                log.info("Using native Rust knowledge backend");
            } catch (Exception e) {
                log.warn("Failed to initialize native backend, falling back to Java: {}", e.getMessage());
                tempUseNative = false;
                tempFallbackBackend = new SQLiteKnowledgeBase(dbPath, vectorDimension);
            }
        } else {
            tempUseNative = false;
            tempFallbackBackend = new SQLiteKnowledgeBase(dbPath, vectorDimension);
            log.info("Using Java SQLite knowledge backend (native not available)");
        }
        
        this.useNative = tempUseNative;
        this.fallbackBackend = tempFallbackBackend;
    }
    
    @Override
    public void store(String key, Object knowledge, Map<String, String> metadata) {
        if (useNative) {
            String content = serializeObject(knowledge);
            String type = metadata != null ? metadata.getOrDefault("type", "fact") : "fact";
            String brainDomain = metadata != null ? metadata.getOrDefault("brainDomain", "") : "";
            String importance = metadata != null ? metadata.getOrDefault("importance", "medium") : "medium";
            String validity = metadata != null ? metadata.getOrDefault("validity", "long_term") : "long_term";
            
            nativeKnowledge.store(key, content, type, brainDomain, importance, validity, null);
        } else {
            fallbackBackend.store(key, knowledge, metadata);
        }
    }
    
    @Override
    public Optional<Object> retrieve(String key) {
        if (useNative) {
            Map<String, Object> result = nativeKnowledge.retrieve(key);
            if (result != null && !result.isEmpty()) {
                KnowledgeEntry entry = mapToEntry(result);
                return Optional.ofNullable(entry.getContent());
            }
            return Optional.empty();
        } else {
            return fallbackBackend.retrieve(key);
        }
    }

    @Override
    public Optional<KnowledgeEntry> retrieveEntry(String key) {
        if (useNative) {
            Map<String, Object> result = nativeKnowledge.retrieve(key);
            if (result != null && !result.isEmpty()) {
                KnowledgeEntry entry = mapToEntry(result);
                return Optional.of(entry);
            }
            return Optional.empty();
        } else {
            return fallbackBackend.retrieveEntry(key);
        }
    }
    
    @Override
    public List<KnowledgeEntry> search(String query) {
        if (useNative) {
            List<Map<String, Object>> results = nativeKnowledge.search(query, 20);
            List<KnowledgeEntry> entries = new ArrayList<>();
            for (Map<String, Object> result : results) {
                entries.add(mapToEntry(result));
            }
            return entries;
        } else {
            return fallbackBackend.search(query);
        }
    }
    
    @Override
    public List<KnowledgeEntry> getByCategory(String category) {
        if (!useNative) {
            return fallbackBackend.getByCategory(category);
        }
        return search(category);
    }
    
    @Override
    public List<KnowledgeEntry> getByTag(String tag) {
        if (!useNative) {
            return fallbackBackend.getByTag(tag);
        }
        return search(tag);
    }
    
    @Override
    public void update(String key, Object knowledge) {
        if (useNative) {
            Map<String, Object> existing = nativeKnowledge.retrieve(key);
            if (existing != null) {
                String content = serializeObject(knowledge);
                String type = (String) existing.getOrDefault("type", "fact");
                String brainDomain = (String) existing.getOrDefault("brainDomain", "");
                String importance = (String) existing.getOrDefault("importance", "medium");
                String validity = (String) existing.getOrDefault("validity", "long_term");
                
                nativeKnowledge.store(key, content, type, brainDomain, importance, validity, null);
            }
        } else {
            fallbackBackend.update(key, knowledge);
        }
    }
    
    @Override
    public void delete(String key) {
        if (useNative) {
            nativeKnowledge.delete(key);
        } else {
            fallbackBackend.delete(key);
        }
    }
    
    @Override
    public void addExperience(Experience experience) {
        if (!useNative) {
            fallbackBackend.addExperience(experience);
        }
    }
    
    @Override
    public List<Experience> getExperiences(String context) {
        if (!useNative) {
            return fallbackBackend.getExperiences(context);
        }
        return new ArrayList<>();
    }
    
    @Override
    public void shareKnowledge(String key, String targetAgent) {
        if (!useNative) {
            fallbackBackend.shareKnowledge(key, targetAgent);
        }
    }
    
    @Override
    public List<BestPractice> getBestPractices(String domain) {
        if (!useNative) {
            return fallbackBackend.getBestPractices(domain);
        }
        return new ArrayList<>();
    }
    
    @Override
    public void recordBestPractice(BestPractice practice) {
        if (!useNative) {
            fallbackBackend.recordBestPractice(practice);
        }
    }
    
    @Override
    public List<KnowledgeEntry> searchSimilar(float[] vector, int limit) {
        if (useNative) {
            List<Map<String, Object>> results = nativeKnowledge.vectorSearch(vector, limit, 0.5f);
            List<KnowledgeEntry> entries = new ArrayList<>();
            for (Map<String, Object> result : results) {
                entries.add(mapToEntry(result));
            }
            return entries;
        } else {
            return fallbackBackend.searchSimilar(vector, limit);
        }
    }
    
    @Override
    public void storeWithVector(String key, Object knowledge, float[] embedding, Map<String, String> metadata) {
        if (useNative) {
            String content = serializeObject(knowledge);
            String type = metadata != null ? metadata.getOrDefault("type", "fact") : "fact";
            String brainDomain = metadata != null ? metadata.getOrDefault("brainDomain", "") : "";
            String importance = metadata != null ? metadata.getOrDefault("importance", "medium") : "medium";
            String validity = metadata != null ? metadata.getOrDefault("validity", "long_term") : "long_term";
            
            nativeKnowledge.store(key, content, type, brainDomain, importance, validity, embedding);
        } else {
            fallbackBackend.storeWithVector(key, knowledge, embedding, metadata);
        }
    }
    
    @Override
    public List<KnowledgeEntry> hybridSearch(String query, float[] queryVector, 
                                              double vectorWeight, double keywordWeight, int limit) {
        if (useNative) {
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
                .map(e -> {
                    Optional<Object> obj = retrieve(e.getKey());
                    return obj.map(o -> {
                        KnowledgeEntry entry = new KnowledgeEntry(e.getKey(), o);
                        entry.setRelevanceScore(e.getValue());
                        return entry;
                    }).orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
        } else {
            return fallbackBackend.hybridSearch(query, queryVector, vectorWeight, keywordWeight, limit);
        }
    }
    
    @Override
    public int getKnowledgeCount() {
        if (useNative) {
            return (int) nativeKnowledge.count();
        } else {
            return fallbackBackend.getKnowledgeCount();
        }
    }
    
    @Override
    public int getExperienceCount() {
        if (!useNative) {
            return fallbackBackend.getExperienceCount();
        }
        return 0;
    }
    
    @Override
    public void cleanupExpiredKnowledge(int daysOld) {
        if (useNative) {
            nativeKnowledge.cleanupExpired();
        } else {
            fallbackBackend.cleanupExpiredKnowledge(daysOld);
        }
    }
    
    @Override
    public void updateKnowledgeRelevance(String key, double relevanceDelta) {
        if (!useNative) {
            fallbackBackend.updateKnowledgeRelevance(key, relevanceDelta);
        }
    }
    
    @Override
    public List<KnowledgeEntry> getMostAccessed(int limit) {
        if (!useNative) {
            return fallbackBackend.getMostAccessed(limit);
        }
        return new ArrayList<>();
    }
    
    @Override
    public List<KnowledgeEntry> getRecentlyUpdated(int limit) {
        if (!useNative) {
            return fallbackBackend.getRecentlyUpdated(limit);
        }
        return new ArrayList<>();
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("knowledgeCount", getKnowledgeCount());
        stats.put("experienceCount", getExperienceCount());
        stats.put("useNative", useNative);
        
        if (useNative) {
            Map<String, Object> cacheStats = nativeKnowledge.cacheStats();
            stats.put("cacheStats", cacheStats);
        } else {
            stats.putAll(fallbackBackend.getStatistics());
        }
        
        return stats;
    }
    
    public float cosineSimilarity(float[] vec1, float[] vec2) {
        if (useNative) {
            return nativeKnowledge.cosineSimilarity(vec1, vec2);
        } else {
            return javaCosineSimilarity(vec1, vec2);
        }
    }
    
    public boolean isNativeEnabled() {
        return useNative;
    }
    
    private KnowledgeEntry mapToEntry(Map<String, Object> map) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setEntryId((String) map.getOrDefault("id", UUID.randomUUID().toString()));
        entry.setKey((String) map.get("key"));
        entry.setContent(map.get("content"));
        entry.setKnowledgeType(KnowledgeType.fromString((String) map.get("type")));
        entry.setImportance(Importance.fromString((String) map.get("importance")));
        entry.setBrainDomain((String) map.get("brainDomain"));
        
        Object relevanceObj = map.get("relevance_score");
        if (relevanceObj instanceof Number) {
            entry.setRelevanceScore(((Number) relevanceObj).doubleValue());
        }
        
        return entry;
    }
    
    private String serializeObject(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
    
    private float javaCosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator > 0 ? (float) (dotProduct / denominator) : 0;
    }
}
