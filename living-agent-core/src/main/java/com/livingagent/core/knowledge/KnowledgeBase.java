package com.livingagent.core.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeBase {
    
    void store(String key, Object knowledge, Map<String, String> metadata);
    
    Optional<Object> retrieve(String key);
    
    Optional<KnowledgeEntry> retrieveEntry(String key);
    
    List<KnowledgeEntry> search(String query);
    
    List<KnowledgeEntry> getByCategory(String category);
    
    List<KnowledgeEntry> getByTag(String tag);
    
    void update(String key, Object knowledge);
    
    void delete(String key);
    
    void addExperience(Experience experience);
    
    List<Experience> getExperiences(String context);
    
    void shareKnowledge(String key, String targetAgent);
    
    List<BestPractice> getBestPractices(String domain);
    
    void recordBestPractice(BestPractice practice);
    
    List<KnowledgeEntry> searchSimilar(float[] vector, int limit);
    
    void storeWithVector(String key, Object knowledge, float[] embedding, Map<String, String> metadata);
    
    List<KnowledgeEntry> hybridSearch(String query, float[] queryVector, double vectorWeight, double keywordWeight, int limit);
    
    int getKnowledgeCount();
    
    int getExperienceCount();
    
    void cleanupExpiredKnowledge(int daysOld);
    
    void updateKnowledgeRelevance(String key, double relevanceDelta);
    
    List<KnowledgeEntry> getMostAccessed(int limit);
    
    List<KnowledgeEntry> getRecentlyUpdated(int limit);
    
    Map<String, Object> getStatistics();
}
