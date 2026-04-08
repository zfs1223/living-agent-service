package com.livingagent.core.knowledge.impl;

import com.livingagent.core.database.entity.KnowledgeEntryEntity;
import com.livingagent.core.database.repository.KnowledgeEntryRepository;
import com.livingagent.core.database.vector.QdrantVectorService;
import com.livingagent.core.embedding.EmbeddingService;
import com.livingagent.core.knowledge.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePersistenceService.class);
    private static final String COLLECTION_NAME = "knowledge";

    private final KnowledgeEntryRepository repository;
    private QdrantVectorService vectorService;
    private EmbeddingService embeddingService;
    private volatile boolean vectorSearchEnabled = false;

    public KnowledgePersistenceService(KnowledgeEntryRepository repository) {
        this.repository = repository;
    }

    @Autowired(required = false)
    public void setVectorService(QdrantVectorService vectorService) {
        this.vectorService = vectorService;
        this.vectorSearchEnabled = vectorService != null;
        if (vectorSearchEnabled) {
            log.info("KnowledgePersistenceService: Qdrant vector service enabled");
        }
    }

    @Autowired(required = false)
    public void setEmbeddingService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
        if (embeddingService != null) {
            log.info("KnowledgePersistenceService: Embedding service enabled");
        }
    }

    @Transactional
    public KnowledgeEntryEntity store(String key, Object content, KnowledgeScope scope, 
                                       String scopeIdentifier, Map<String, String> metadata) {
        KnowledgeEntryEntity entity = new KnowledgeEntryEntity();
        entity.setKey(key);
        entity.setContent(content != null ? content.toString() : "");
        entity.setScope(scope.name());
        entity.setScopeIdentifier(scopeIdentifier);
        
        if (metadata != null) {
            entity.getMetadata().putAll(metadata);
            if (metadata.containsKey("type")) {
                entity.setKnowledgeType(metadata.get("type"));
            }
            if (metadata.containsKey("importance")) {
                entity.setImportance(metadata.get("importance"));
            }
            if (metadata.containsKey("brainDomain")) {
                entity.setBrainDomain(metadata.get("brainDomain"));
            }
            if (metadata.containsKey("neuronId")) {
                entity.setNeuronId(metadata.get("neuronId"));
            }
        }

        entity = repository.save(entity);

        if (vectorSearchEnabled && embeddingService != null) {
            try {
                float[] embedding = embeddingService.embed(entity.getContent());
                String vectorId = entity.getEntryId();
                entity.setVectorId(vectorId);
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("key", key);
                payload.put("scope", scope.name());
                payload.put("scopeIdentifier", scopeIdentifier);
                payload.put("entryId", entity.getEntryId());
                
                vectorService.upsertVector(COLLECTION_NAME, vectorId, embedding, payload);
                entity = repository.save(entity);
                
                log.debug("Stored knowledge with vector: {}", key);
            } catch (Exception e) {
                log.warn("Failed to store vector for {}: {}", key, e.getMessage());
            }
        }

        log.debug("Stored knowledge entity: {} in scope: {}", key, scope);
        return entity;
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeEntryEntity> findByKey(String key) {
        return repository.findByKey(key);
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeEntryEntity> findByEntryId(String entryId) {
        return repository.findByEntryId(entryId);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> findByScope(KnowledgeScope scope, String scopeIdentifier) {
        if (scopeIdentifier != null) {
            return repository.findByScopeAndScopeIdentifier(scope.name(), scopeIdentifier);
        }
        return repository.findByScope(scope.name());
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> searchByKeyword(String keyword) {
        return repository.searchByKeyword(keyword);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> searchByKeywordInScope(String keyword, KnowledgeScope scope, String scopeIdentifier) {
        return repository.searchByKeywordInScope(keyword, scope.name(), scopeIdentifier);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> findAccessibleKnowledge(String profileId, String departmentId) {
        List<KnowledgeEntryEntity> results = new ArrayList<>();
        
        if (profileId != null) {
            results.addAll(repository.findPrivateKnowledge(profileId));
        }
        
        if (departmentId != null) {
            results.addAll(repository.findDepartmentKnowledge(departmentId));
        }
        
        results.addAll(repository.findSharedKnowledge());
        
        return results.stream()
            .distinct()
            .sorted(Comparator.comparing(KnowledgeEntryEntity::getRelevance, Comparator.reverseOrder()))
            .collect(Collectors.toList());
    }

    @Transactional
    public KnowledgeEntryEntity update(String key, Object content) {
        Optional<KnowledgeEntryEntity> optEntity = repository.findByKey(key);
        if (optEntity.isEmpty()) {
            log.warn("Knowledge not found for update: {}", key);
            return null;
        }
        
        KnowledgeEntryEntity entity = optEntity.get();
        entity.setContent(content != null ? content.toString() : "");
        entity.setUpdatedAt(Instant.now());
        
        if (vectorSearchEnabled && embeddingService != null && entity.getVectorId() != null) {
            try {
                float[] embedding = embeddingService.embed(entity.getContent());
                Map<String, Object> payload = new HashMap<>();
                payload.put("key", key);
                payload.put("scope", entity.getScope());
                payload.put("entryId", entity.getEntryId());
                
                vectorService.upsertVector(COLLECTION_NAME, entity.getVectorId(), embedding, payload);
            } catch (Exception e) {
                log.warn("Failed to update vector for {}: {}", key, e.getMessage());
            }
        }
        
        return repository.save(entity);
    }

    @Transactional
    public void delete(String key) {
        Optional<KnowledgeEntryEntity> optEntity = repository.findByKey(key);
        if (optEntity.isPresent()) {
            KnowledgeEntryEntity entity = optEntity.get();
            
            if (vectorSearchEnabled && entity.getVectorId() != null) {
                try {
                    vectorService.deleteVector(COLLECTION_NAME, entity.getVectorId());
                } catch (Exception e) {
                    log.warn("Failed to delete vector for {}: {}", key, e.getMessage());
                }
            }
            
            repository.delete(entity);
            log.debug("Deleted knowledge: {}", key);
        }
    }

    @Transactional
    public KnowledgeEntryEntity incrementAccessCount(String key) {
        Optional<KnowledgeEntryEntity> optEntity = repository.findByKey(key);
        if (optEntity.isPresent()) {
            KnowledgeEntryEntity entity = optEntity.get();
            entity.incrementAccessCount();
            return repository.save(entity);
        }
        return null;
    }

    @Transactional
    public void updateRelevance(String key, double relevanceDelta) {
        Optional<KnowledgeEntryEntity> optEntity = repository.findByKey(key);
        if (optEntity.isPresent()) {
            KnowledgeEntryEntity entity = optEntity.get();
            double newRelevance = Math.max(0, Math.min(2, entity.getRelevance() + relevanceDelta));
            entity.setRelevance(newRelevance);
            repository.save(entity);
        }
    }

    @Transactional
    public KnowledgeEntryEntity promoteKnowledge(String key, KnowledgeScope toScope, String toIdentifier) {
        Optional<KnowledgeEntryEntity> optEntity = repository.findByKey(key);
        if (optEntity.isEmpty()) {
            log.warn("Knowledge not found for promotion: {}", key);
            return null;
        }
        
        KnowledgeEntryEntity entity = optEntity.get();
        String oldKey = key;
        
        String newKey = toScope.buildNamespace(toIdentifier) + ":" + extractBaseKey(key);
        
        KnowledgeEntryEntity promotedEntity = new KnowledgeEntryEntity();
        promotedEntity.setKey(newKey);
        promotedEntity.setContent(entity.getContent());
        promotedEntity.setScope(toScope.name());
        promotedEntity.setScopeIdentifier(toIdentifier);
        promotedEntity.getMetadata().putAll(entity.getMetadata());
        promotedEntity.getTags().putAll(entity.getTags());
        promotedEntity.setAccessCount(entity.getAccessCount());
        promotedEntity.setRelevance(entity.getRelevance());
        promotedEntity.setPromotedFrom(oldKey);
        promotedEntity.setBrainDomain(entity.getBrainDomain());
        promotedEntity.setNeuronId(entity.getNeuronId());
        
        promotedEntity = repository.save(promotedEntity);
        
        if (vectorSearchEnabled && entity.getVectorId() != null && embeddingService != null) {
            try {
                float[] embedding = embeddingService.embed(entity.getContent());
                Map<String, Object> payload = new HashMap<>();
                payload.put("key", newKey);
                payload.put("scope", toScope.name());
                payload.put("scopeIdentifier", toIdentifier);
                payload.put("entryId", promotedEntity.getEntryId());
                
                vectorService.upsertVector(COLLECTION_NAME, promotedEntity.getEntryId(), embedding, payload);
                promotedEntity.setVectorId(promotedEntity.getEntryId());
                repository.save(promotedEntity);
            } catch (Exception e) {
                log.warn("Failed to create vector for promoted knowledge: {}", e.getMessage());
            }
        }
        
        log.info("Promoted knowledge from {} to {}", oldKey, newKey);
        return promotedEntity;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> findMostAccessed(int limit) {
        return repository.findMostAccessed(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> findRecentlyUpdated(int limit) {
        return repository.findRecentlyUpdated(PageRequest.of(0, limit));
    }

    @Transactional
    public int cleanupExpiredKnowledge(int daysOld) {
        Instant threshold = Instant.now().minusSeconds(daysOld * 86400L);
        List<KnowledgeEntryEntity> expired = repository.findUnusedBefore(threshold);
        
        for (KnowledgeEntryEntity entity : expired) {
            if (entity.getVectorId() != null && vectorSearchEnabled) {
                try {
                    vectorService.deleteVector(COLLECTION_NAME, entity.getVectorId());
                } catch (Exception e) {
                    log.warn("Failed to delete vector during cleanup: {}", e.getMessage());
                }
            }
        }
        
        repository.deleteAll(expired);
        log.info("Cleaned up {} expired knowledge entries", expired.size());
        return expired.size();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKnowledge", repository.count());
        stats.put("l1PrivateCount", repository.countByScope(KnowledgeScope.L1_PRIVATE.name()));
        stats.put("l2DepartmentCount", repository.countByScope(KnowledgeScope.L2_DEPARTMENT.name()));
        stats.put("l3SharedCount", repository.countByScope(KnowledgeScope.L3_SHARED.name()));
        stats.put("totalAccessCount", repository.getTotalAccessCount());
        return stats;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> searchSimilar(float[] vector, int limit) {
        if (!vectorSearchEnabled || vectorService == null) {
            return Collections.emptyList();
        }
        
        try {
            List<QdrantVectorService.SearchResult> results = vectorService.search(
                COLLECTION_NAME, vector, limit, 0.0f
            );
            
            List<String> entryIds = results.stream()
                .map(r -> (String) r.getPayload().get("entryId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            return entryIds.stream()
                .map(id -> repository.findByEntryId(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryEntity> hybridSearch(String query, float[] queryVector, 
                                                    double vectorWeight, int limit) {
        List<KnowledgeEntryEntity> keywordResults = searchByKeyword(query);
        List<KnowledgeEntryEntity> vectorResults = searchSimilar(queryVector, limit * 2);
        
        Map<String, Double> scores = new HashMap<>();
        double keywordWeight = 1 - vectorWeight;
        
        for (int i = 0; i < keywordResults.size(); i++) {
            KnowledgeEntryEntity entry = keywordResults.get(i);
            double score = keywordWeight * (1.0 / (i + 1));
            scores.merge(entry.getKey(), score, Double::sum);
        }
        
        for (int i = 0; i < vectorResults.size(); i++) {
            KnowledgeEntryEntity entry = vectorResults.get(i);
            double score = vectorWeight * (1.0 / (i + 1));
            scores.merge(entry.getKey(), score, Double::sum);
        }
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(e -> repository.findByKey(e.getKey()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private String extractBaseKey(String fullKey) {
        int lastColon = fullKey.lastIndexOf(':');
        if (lastColon >= 0 && lastColon < fullKey.length() - 1) {
            return fullKey.substring(lastColon + 1);
        }
        return fullKey;
    }

    public KnowledgeEntry toKnowledgeEntry(KnowledgeEntryEntity entity) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setEntryId(entity.getEntryId());
        entry.setKey(entity.getKey());
        entry.setContent(entity.getContent());
        entry.setScope(KnowledgeScope.valueOf(entity.getScope()));
        entry.setScopeIdentifier(entity.getScopeIdentifier());
        entry.setBrainDomain(entity.getBrainDomain());
        entry.setNeuronId(entity.getNeuronId());
        entry.setConfidence(entity.getConfidence() != null ? entity.getConfidence() : 0.5);
        entry.setRelevance(entity.getRelevance() != null ? entity.getRelevance() : 1.0);
        entry.setAccessCount(entity.getAccessCount() != null ? entity.getAccessCount() : 0);
        entry.setVerified(entity.getVerified() != null ? entity.getVerified() : false);
        entry.setExpiresAt(entity.getExpiresAt());
        entry.setLastAccessedAt(entity.getLastAccessedAt());
        entry.setCreatedAt(entity.getCreatedAt());
        entry.setUpdatedAt(entity.getUpdatedAt());
        entry.setPromotedFrom(entity.getPromotedFrom());
        entry.setSource(entity.getSource());
        
        if (entity.getKnowledgeType() != null) {
            try {
                entry.setKnowledgeType(KnowledgeType.valueOf(entity.getKnowledgeType()));
            } catch (IllegalArgumentException e) {
                entry.setKnowledgeType(KnowledgeType.FACT);
            }
        }
        if (entity.getImportance() != null) {
            try {
                entry.setImportance(Importance.valueOf(entity.getImportance()));
            } catch (IllegalArgumentException e) {
                entry.setImportance(Importance.MEDIUM);
            }
        }
        if (entity.getValidity() != null) {
            try {
                entry.setValidity(Validity.valueOf(entity.getValidity()));
            } catch (IllegalArgumentException e) {
                entry.setValidity(Validity.LONG_TERM);
            }
        }
        
        entry.setTags(new HashMap<>(entity.getTags()));
        entry.setMetadata(new KnowledgeMetadata());
        entry.getMetadata().putAll(entity.getMetadata());
        
        return entry;
    }
}
