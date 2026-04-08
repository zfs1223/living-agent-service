package com.livingagent.core.knowledge.impl;

import com.livingagent.core.database.entity.KnowledgeEntryEntity;
import com.livingagent.core.database.vector.QdrantVectorService;
import com.livingagent.core.embedding.EmbeddingService;
import com.livingagent.core.knowledge.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class LayeredKnowledgeBaseImpl implements LayeredKnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(LayeredKnowledgeBaseImpl.class);
    private static final String COLLECTION_NAME = "knowledge";

    private final Map<String, KnowledgeEntry> knowledgeStore = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> accessControl = new ConcurrentHashMap<>();
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    private QdrantVectorService vectorService;
    private EmbeddingService embeddingService;
    private KnowledgePersistenceService persistenceService;
    private volatile boolean vectorSearchEnabled = false;
    private volatile boolean persistenceEnabled = false;

    @Autowired(required = false)
    public void setVectorService(QdrantVectorService vectorService) {
        this.vectorService = vectorService;
        this.vectorSearchEnabled = vectorService != null;
        if (vectorSearchEnabled) {
            log.info("LayeredKnowledgeBaseImpl: Qdrant vector service enabled");
        }
    }

    @Autowired(required = false)
    public void setEmbeddingService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
        if (embeddingService != null) {
            log.info("LayeredKnowledgeBaseImpl: Embedding service enabled");
        }
    }

    @Autowired(required = false)
    public void setPersistenceService(KnowledgePersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        this.persistenceEnabled = persistenceService != null;
        if (persistenceEnabled) {
            log.info("LayeredKnowledgeBaseImpl: PostgreSQL persistence enabled");
        }
    }

    @Override
    public void store(String key, Object knowledge, KnowledgeScope scope, String scopeIdentifier, Map<String, String> metadata) {
        String namespace = scope.buildNamespace(scopeIdentifier);
        String fullKey = namespace + ":" + key;

        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKey(fullKey);
        entry.setContent(knowledge);
        entry.setScope(scope);
        entry.setScopeIdentifier(scopeIdentifier);
        entry.setMetadata(metadata != null ? metadata : new HashMap<>());
        entry.setCreatedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());
        entry.setAccessCount(0);
        entry.setRelevance(1.0);

        knowledgeStore.put(fullKey, entry);

        String accessKey = buildAccessKey(fullKey);
        accessControl.computeIfAbsent(accessKey, k -> ConcurrentHashMap.newKeySet());

        if (persistenceEnabled && persistenceService != null) {
            try {
                persistenceService.store(fullKey, knowledge, scope, scopeIdentifier, metadata);
            } catch (Exception e) {
                log.warn("Failed to persist knowledge {}: {}", fullKey, e.getMessage());
            }
        }

        if (vectorSearchEnabled && embeddingService != null) {
            try {
                String textContent = knowledge != null ? knowledge.toString() : "";
                float[] embedding = embeddingService.embed(textContent);
                embeddingCache.put(fullKey, embedding);
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("key", fullKey);
                payload.put("scope", scope.name());
                payload.put("scopeIdentifier", scopeIdentifier);
                payload.put("metadata", metadata);
                
                vectorService.upsertVector(COLLECTION_NAME, fullKey, embedding, payload);
                log.debug("Stored knowledge with vector: {}", fullKey);
            } catch (Exception e) {
                log.warn("Failed to store vector for {}: {}", fullKey, e.getMessage());
            }
        }

        log.info("Stored knowledge: {} in scope: {}", fullKey, scope);
    }

    @Override
    public Optional<Object> retrieve(String key, KnowledgeScope scope, String scopeIdentifier) {
        String namespace = scope.buildNamespace(scopeIdentifier);
        String fullKey = namespace + ":" + key;

        KnowledgeEntry entry = knowledgeStore.get(fullKey);
        if (entry != null) {
            entry.setAccessCount(entry.getAccessCount() + 1);
            entry.setLastAccessedAt(Instant.now());
            
            if (persistenceEnabled && persistenceService != null) {
                persistenceService.incrementAccessCount(fullKey);
            }
            
            return Optional.of(entry.getContent());
        }
        
        if (persistenceEnabled && persistenceService != null) {
            Optional<KnowledgeEntryEntity> entityOpt = persistenceService.findByKey(fullKey);
            if (entityOpt.isPresent()) {
                KnowledgeEntry loadedEntry = persistenceService.toKnowledgeEntry(entityOpt.get());
                knowledgeStore.put(fullKey, loadedEntry);
                persistenceService.incrementAccessCount(fullKey);
                return Optional.of(loadedEntry.getContent());
            }
        }
        
        return Optional.empty();
    }

    @Override
    public Optional<KnowledgeEntry> retrieveEntry(String key, KnowledgeScope scope, String scopeIdentifier) {
        String namespace = scope.buildNamespace(scopeIdentifier);
        String fullKey = namespace + ":" + key;
        
        KnowledgeEntry entry = knowledgeStore.get(fullKey);
        if (entry != null) {
            return Optional.of(entry);
        }
        
        if (persistenceEnabled && persistenceService != null) {
            Optional<KnowledgeEntryEntity> entityOpt = persistenceService.findByKey(fullKey);
            if (entityOpt.isPresent()) {
                KnowledgeEntry loadedEntry = persistenceService.toKnowledgeEntry(entityOpt.get());
                knowledgeStore.put(fullKey, loadedEntry);
                return Optional.of(loadedEntry);
            }
        }
        
        return Optional.empty();
    }

    @Override
    public Optional<KnowledgeEntry> retrieveEntry(String key) {
        KnowledgeEntry entry = knowledgeStore.get(key);
        if (entry != null) {
            return Optional.of(entry);
        }
        
        if (persistenceEnabled && persistenceService != null) {
            Optional<KnowledgeEntryEntity> entityOpt = persistenceService.findByKey(key);
            if (entityOpt.isPresent()) {
                KnowledgeEntry loadedEntry = persistenceService.toKnowledgeEntry(entityOpt.get());
                knowledgeStore.put(key, loadedEntry);
                return Optional.of(loadedEntry);
            }
        }
        
        return Optional.empty();
    }

    @Override
    public List<KnowledgeEntry> search(String query, KnowledgeScope scope, String scopeIdentifier) {
        String namespace = scope.buildNamespace(scopeIdentifier);

        List<KnowledgeEntry> memoryResults = knowledgeStore.values().stream()
                .filter(entry -> entry.getKey().startsWith(namespace))
                .filter(entry -> matchesQuery(entry, query))
                .sorted(Comparator.comparingDouble(KnowledgeEntry::getRelevance).reversed())
                .collect(Collectors.toList());
        
        if (persistenceEnabled && persistenceService != null) {
            List<KnowledgeEntryEntity> dbResults = persistenceService.searchByKeywordInScope(query, scope, scopeIdentifier);
            for (KnowledgeEntryEntity entity : dbResults) {
                if (memoryResults.stream().noneMatch(e -> e.getKey().equals(entity.getKey()))) {
                    memoryResults.add(persistenceService.toKnowledgeEntry(entity));
                }
            }
        }
        
        return memoryResults;
    }

    @Override
    public List<KnowledgeEntry> getByScope(KnowledgeScope scope, String scopeIdentifier) {
        String namespace = scope.buildNamespace(scopeIdentifier);

        List<KnowledgeEntry> memoryResults = knowledgeStore.values().stream()
                .filter(entry -> entry.getKey().startsWith(namespace))
                .collect(Collectors.toList());
        
        if (persistenceEnabled && persistenceService != null) {
            List<KnowledgeEntryEntity> dbResults = persistenceService.findByScope(scope, scopeIdentifier);
            for (KnowledgeEntryEntity entity : dbResults) {
                if (memoryResults.stream().noneMatch(e -> e.getKey().equals(entity.getKey()))) {
                    memoryResults.add(persistenceService.toKnowledgeEntry(entity));
                }
            }
        }
        
        return memoryResults;
    }

    @Override
    public List<KnowledgeEntry> searchAccessible(String query, String profileId, String departmentId) {
        List<KnowledgeEntry> memoryResults = knowledgeStore.values().stream()
                .filter(entry -> canAccess(profileId, departmentId, entry))
                .filter(entry -> matchesQuery(entry, query))
                .sorted(Comparator.comparingDouble(KnowledgeEntry::getRelevance).reversed())
                .collect(Collectors.toList());
        
        if (persistenceEnabled && persistenceService != null) {
            List<KnowledgeEntryEntity> dbResults = persistenceService.findAccessibleKnowledge(profileId, departmentId);
            for (KnowledgeEntryEntity entity : dbResults) {
                if (matchesQuery(persistenceService.toKnowledgeEntry(entity), query)) {
                    if (memoryResults.stream().noneMatch(e -> e.getKey().equals(entity.getKey()))) {
                        memoryResults.add(persistenceService.toKnowledgeEntry(entity));
                    }
                }
            }
        }
        
        return memoryResults;
    }

    @Override
    public boolean hasAccess(String profileId, KnowledgeScope scope, String scopeIdentifier) {
        return switch (scope) {
            case L1_PRIVATE -> scopeIdentifier != null && scopeIdentifier.equals(profileId);
            case L2_DEPARTMENT -> scopeIdentifier != null && !scopeIdentifier.isEmpty();
            case L3_SHARED -> true;
        };
    }

    @Override
    public void promoteKnowledge(String key, KnowledgeScope fromScope, String fromIdentifier,
                                  KnowledgeScope toScope, String toIdentifier) {
        String fromNamespace = fromScope.buildNamespace(fromIdentifier);
        String fullKey = fromNamespace + ":" + key;

        KnowledgeEntry entry = knowledgeStore.get(fullKey);
        if (entry == null) {
            log.warn("Knowledge not found for promotion: {}", fullKey);
            return;
        }

        String toNamespace = toScope.buildNamespace(toIdentifier);
        String newKey = toNamespace + ":" + key;

        KnowledgeEntry promotedEntry = new KnowledgeEntry();
        promotedEntry.setKey(newKey);
        promotedEntry.setContent(entry.getContent());
        promotedEntry.setScope(toScope);
        promotedEntry.setScopeIdentifier(toIdentifier);
        promotedEntry.setMetadata(entry.getMetadata());
        promotedEntry.setCreatedAt(entry.getCreatedAt());
        promotedEntry.setUpdatedAt(Instant.now());
        promotedEntry.setAccessCount(entry.getAccessCount());
        promotedEntry.setRelevance(entry.getRelevance());
        promotedEntry.setPromotedFrom(fullKey);

        knowledgeStore.put(newKey, promotedEntry);

        if (persistenceEnabled && persistenceService != null) {
            persistenceService.promoteKnowledge(fullKey, toScope, toIdentifier);
        }

        log.info("Promoted knowledge from {} to {}", fullKey, newKey);
    }

    @Override
    public void shareToDepartment(String key, String profileId, String departmentId) {
        promoteKnowledge(key, KnowledgeScope.L1_PRIVATE, profileId, KnowledgeScope.L2_DEPARTMENT, departmentId);
    }

    @Override
    public void shareToGlobal(String key, String departmentId) {
        promoteKnowledge(key, KnowledgeScope.L2_DEPARTMENT, departmentId, KnowledgeScope.L3_SHARED, "global");
    }

    @Override
    public KnowledgeStatistics getStatisticsByScope(KnowledgeScope scope, String scopeIdentifier) {
        List<KnowledgeEntry> entries = getByScope(scope, scopeIdentifier);

        int total = entries.size();
        long size = entries.stream()
                .mapToLong(e -> e.getContent() != null ? e.getContent().toString().length() : 0)
                .sum();
        int experienceCount = (int) entries.stream()
                .filter(e -> "experience".equals(e.getMetadata().get("type")))
                .count();
        int bestPracticeCount = (int) entries.stream()
                .filter(e -> "best_practice".equals(e.getMetadata().get("type")))
                .count();
        double avgRelevance = entries.stream()
                .mapToDouble(KnowledgeEntry::getRelevance)
                .average()
                .orElse(0.0);
        int accessCount = entries.stream()
                .mapToInt(KnowledgeEntry::getAccessCount)
                .sum();

        return new KnowledgeStatistics(total, size, experienceCount, bestPracticeCount, avgRelevance, accessCount);
    }

    @Override
    public List<KnowledgeEntry> getPrivateKnowledge(String profileId) {
        return getByScope(KnowledgeScope.L1_PRIVATE, profileId);
    }

    @Override
    public List<KnowledgeEntry> getDepartmentKnowledge(String departmentId) {
        return getByScope(KnowledgeScope.L2_DEPARTMENT, departmentId);
    }

    @Override
    public List<KnowledgeEntry> getSharedKnowledge() {
        return getByScope(KnowledgeScope.L3_SHARED, "global");
    }

    @Override
    public void grantAccess(String key, String profileId, KnowledgeScope scope, String scopeIdentifier) {
        String fullKey = scope.buildNamespace(scopeIdentifier) + ":" + key;
        String accessKey = buildAccessKey(fullKey);
        accessControl.computeIfAbsent(accessKey, k -> ConcurrentHashMap.newKeySet()).add(profileId);
    }

    @Override
    public void revokeAccess(String key, String profileId, KnowledgeScope scope, String scopeIdentifier) {
        String fullKey = scope.buildNamespace(scopeIdentifier) + ":" + key;
        String accessKey = buildAccessKey(fullKey);
        Set<String> profiles = accessControl.get(accessKey);
        if (profiles != null) {
            profiles.remove(profileId);
        }
    }

    @Override
    public List<String> getAccessibleProfiles(String key, KnowledgeScope scope, String scopeIdentifier) {
        String fullKey = scope.buildNamespace(scopeIdentifier) + ":" + key;
        String accessKey = buildAccessKey(fullKey);
        return new ArrayList<>(accessControl.getOrDefault(accessKey, Collections.emptySet()));
    }

    private boolean canAccess(String profileId, String departmentId, KnowledgeEntry entry) {
        return switch (entry.getScope()) {
            case L1_PRIVATE -> profileId != null && profileId.equals(entry.getScopeIdentifier());
            case L2_DEPARTMENT -> departmentId != null && departmentId.equals(entry.getScopeIdentifier());
            case L3_SHARED -> true;
        };
    }

    private boolean matchesQuery(KnowledgeEntry entry, String query) {
        if (query == null || query.isEmpty()) return true;

        String lowerQuery = query.toLowerCase();
        String content = entry.getContent() != null ? entry.getContent().toString().toLowerCase() : "";

        if (content.contains(lowerQuery)) return true;

        for (String value : entry.getMetadata().values()) {
            if (value != null && value.toLowerCase().contains(lowerQuery)) {
                return true;
            }
        }

        return entry.getKey().toLowerCase().contains(lowerQuery);
    }

    private String buildAccessKey(String knowledgeKey) {
        return "access:" + knowledgeKey;
    }

    @Override
    public void store(String key, Object knowledge, Map<String, String> metadata) {
        store(key, knowledge, KnowledgeScope.L3_SHARED, "global", metadata);
    }

    @Override
    public Optional<Object> retrieve(String key) {
        return retrieve(key, KnowledgeScope.L3_SHARED, "global");
    }

    @Override
    public List<KnowledgeEntry> search(String query) {
        return search(query, KnowledgeScope.L3_SHARED, "global");
    }

    @Override
    public List<KnowledgeEntry> getByCategory(String category) {
        return knowledgeStore.values().stream()
                .filter(entry -> category.equals(entry.getMetadata().get("category")))
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeEntry> getByTag(String tag) {
        return knowledgeStore.values().stream()
                .filter(entry -> {
                    String tags = entry.getMetadata().get("tags");
                    return tags != null && tags.contains(tag);
                })
                .collect(Collectors.toList());
    }

    @Override
    public void update(String key, Object knowledge) {
        KnowledgeEntry entry = knowledgeStore.get(key);
        if (entry != null) {
            entry.setContent(knowledge);
            entry.setUpdatedAt(Instant.now());
            
            if (persistenceEnabled && persistenceService != null) {
                persistenceService.update(key, knowledge);
            }
        } else if (persistenceEnabled && persistenceService != null) {
            persistenceService.update(key, knowledge);
        }
    }

    @Override
    public void delete(String key) {
        knowledgeStore.remove(key);
        accessControl.remove(buildAccessKey(key));
        embeddingCache.remove(key);
        
        if (persistenceEnabled && persistenceService != null) {
            persistenceService.delete(key);
        }
    }

    @Override
    public void addExperience(Experience experience) {
        String key = "exp_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "experience");
        metadata.put("context", experience.getContext());
        store(key, experience.getContent(), KnowledgeScope.L1_PRIVATE, experience.getProfileId(), metadata);
    }

    @Override
    public List<Experience> getExperiences(String context) {
        return knowledgeStore.values().stream()
                .filter(entry -> "experience".equals(entry.getMetadata().get("type")))
                .filter(entry -> context == null || context.equals(entry.getMetadata().get("context")))
                .map(entry -> new Experience(
                        entry.getScopeIdentifier(),
                        entry.getMetadata().get("context"),
                        entry.getContent(),
                        entry.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void shareKnowledge(String key, String targetAgent) {
        log.info("Sharing knowledge {} with agent {}", key, targetAgent);
    }

    @Override
    public List<BestPractice> getBestPractices(String domain) {
        return knowledgeStore.values().stream()
                .filter(entry -> "best_practice".equals(entry.getMetadata().get("type")))
                .filter(entry -> domain == null || domain.equals(entry.getMetadata().get("domain")))
                .map(entry -> new BestPractice(
                        entry.getKey(),
                        entry.getMetadata().get("domain"),
                        entry.getContent().toString(),
                        Double.parseDouble(entry.getMetadata().getOrDefault("effectiveness", "1.0"))
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void recordBestPractice(BestPractice practice) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "best_practice");
        metadata.put("domain", practice.getDomain());
        metadata.put("effectiveness", String.valueOf(practice.getEffectiveness()));
        store(practice.getId(), practice.getContent(), KnowledgeScope.L2_DEPARTMENT, "global", metadata);
    }

    @Override
    public List<KnowledgeEntry> searchSimilar(float[] vector, int limit) {
        if (vectorSearchEnabled && vectorService != null) {
            try {
                List<QdrantVectorService.SearchResult> results = vectorService.search(
                    COLLECTION_NAME, vector, limit, 0.0f
                );
                
                return results.stream()
                    .map(result -> {
                        String key = (String) result.getPayload().get("key");
                        return knowledgeStore.get(key);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Vector search failed, falling back to keyword search: {}", e.getMessage());
            }
        }
        
        return knowledgeStore.values().stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void storeWithVector(String key, Object knowledge, float[] embedding, Map<String, String> metadata) {
        store(key, knowledge, metadata);
        
        if (vectorSearchEnabled && vectorService != null && embedding != null) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("key", key);
                payload.put("metadata", metadata);
                
                vectorService.upsertVector(COLLECTION_NAME, key, embedding, payload);
                embeddingCache.put(key, embedding);
                log.debug("Stored knowledge with explicit vector: {}", key);
            } catch (Exception e) {
                log.warn("Failed to store explicit vector for {}: {}", key, e.getMessage());
            }
        }
    }

    @Override
    public List<KnowledgeEntry> hybridSearch(String query, float[] queryVector, double vectorWeight, double keywordWeight, int limit) {
        if (vectorSearchEnabled && vectorService != null && queryVector != null) {
            try {
                List<KnowledgeEntry> keywordResults = search(query);
                List<KnowledgeEntry> vectorResults = searchSimilar(queryVector, limit * 2);
                
                Map<String, Double> scores = new HashMap<>();
                
                for (int i = 0; i < keywordResults.size(); i++) {
                    KnowledgeEntry entry = keywordResults.get(i);
                    double score = keywordWeight * (1.0 / (i + 1));
                    scores.merge(entry.getKey(), score, Double::sum);
                }
                
                for (int i = 0; i < vectorResults.size(); i++) {
                    KnowledgeEntry entry = vectorResults.get(i);
                    double score = vectorWeight * (1.0 / (i + 1));
                    scores.merge(entry.getKey(), score, Double::sum);
                }
                
                return scores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(limit)
                    .map(entry -> knowledgeStore.get(entry.getKey()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Hybrid search failed, falling back to keyword search: {}", e.getMessage());
            }
        }
        
        return search(query).stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public int getKnowledgeCount() {
        return knowledgeStore.size();
    }

    @Override
    public int getExperienceCount() {
        return (int) knowledgeStore.values().stream()
                .filter(entry -> "experience".equals(entry.getMetadata().get("type")))
                .count();
    }

    @Override
    public void cleanupExpiredKnowledge(int daysOld) {
        Instant threshold = Instant.now().minusSeconds(daysOld * 86400L);
        knowledgeStore.entrySet().removeIf(entry ->
                entry.getValue().getUpdatedAt().isBefore(threshold) &&
                        entry.getValue().getAccessCount() == 0
        );
        
        if (persistenceEnabled && persistenceService != null) {
            persistenceService.cleanupExpiredKnowledge(daysOld);
        }
    }

    @Override
    public void updateKnowledgeRelevance(String key, double relevanceDelta) {
        KnowledgeEntry entry = knowledgeStore.get(key);
        if (entry == null) {
            return;
        }
        entry.setRelevance(Math.max(0, Math.min(2, entry.getRelevance() + relevanceDelta)));
        
        if (persistenceEnabled && persistenceService != null) {
            persistenceService.updateRelevance(key, relevanceDelta);
        }
    }

    @Override
    public List<KnowledgeEntry> getMostAccessed(int limit) {
        return knowledgeStore.values().stream()
                .sorted(Comparator.comparingInt(KnowledgeEntry::getAccessCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeEntry> getRecentlyUpdated(int limit) {
        return knowledgeStore.values().stream()
                .sorted(Comparator.comparing(KnowledgeEntry::getUpdatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKnowledge", getKnowledgeCount());
        stats.put("totalExperiences", getExperienceCount());
        stats.put("l1PrivateCount", getPrivateKnowledge("all").size());
        stats.put("l2DepartmentCount", getDepartmentKnowledge("all").size());
        stats.put("l3SharedCount", getSharedKnowledge().size());
        
        if (persistenceEnabled && persistenceService != null) {
            Map<String, Object> dbStats = persistenceService.getStatistics();
            stats.put("totalKnowledge", (Long) dbStats.get("totalKnowledge"));
            stats.put("l1PrivateCount", (Integer) dbStats.get("l1PrivateCount"));
            stats.put("l2DepartmentCount", (Integer) dbStats.get("l2DepartmentCount"));
            stats.put("l3SharedCount", (Integer) dbStats.get("l3SharedCount"));
        }
        
        return stats;
    }
}
