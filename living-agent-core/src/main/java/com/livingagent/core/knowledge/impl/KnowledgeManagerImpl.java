package com.livingagent.core.knowledge.impl;

import com.livingagent.core.evolution.KnowledgeEvolver;
import com.livingagent.core.evolution.KnowledgeEvolution;
import com.livingagent.core.evolution.KnowledgeMergeResult;
import com.livingagent.core.evolution.KnowledgePropagationResult;
import com.livingagent.core.evolution.KnowledgeQualityReport;
import com.livingagent.core.knowledge.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class KnowledgeManagerImpl implements KnowledgeManager {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeManagerImpl.class);

    private static final int CACHE_MAX_SIZE = 512;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    private final KnowledgeBase privateKnowledgeBase;
    private final KnowledgeBase domainKnowledgeBase;
    private final KnowledgeBase sharedKnowledgeBase;
    private final KnowledgeEvolver knowledgeEvolver;

    private String brainDomain;
    private String neuronId;

    private final Map<String, KnowledgeLayer> keyLayerMapping = new ConcurrentHashMap<>();

    /** Simple TTL cache for knowledge entries: key -> CacheEntry */
    private final Map<String, CacheEntry> entryCache = new ConcurrentHashMap<>();

    private record CacheEntry(KnowledgeEntry entry, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    public KnowledgeManagerImpl(KnowledgeBase privateKnowledgeBase,
                                KnowledgeBase domainKnowledgeBase,
                                KnowledgeBase sharedKnowledgeBase,
                                KnowledgeEvolver knowledgeEvolver) {
        this.privateKnowledgeBase = privateKnowledgeBase;
        this.domainKnowledgeBase = domainKnowledgeBase;
        this.sharedKnowledgeBase = sharedKnowledgeBase;
        this.knowledgeEvolver = knowledgeEvolver;
    }

    @Override
    public void initialize(String brainDomain, String neuronId) {
        this.brainDomain = brainDomain;
        this.neuronId = neuronId;
        log.info("KnowledgeManager initialized for brain={}, neuron={}", brainDomain, neuronId);
    }

    @Override
    public void storePrivate(String key, Object knowledge, Map<String, String> metadata) {
        KnowledgeEntry entry = createEntry(key, knowledge, KnowledgeLayer.PRIVATE);
        if (metadata != null) {
            metadata.forEach((k, v) -> entry.getTags().put(k, v));
        }
        entry.setNeuronId(neuronId);
        privateKnowledgeBase.store(key, knowledge, metadata);
        keyLayerMapping.put(key, KnowledgeLayer.PRIVATE);
        log.debug("Stored private knowledge: {}", key);
    }

    @Override
    public void storeDomain(String key, Object knowledge, KnowledgeType type, Importance importance) {
        KnowledgeEntry entry = createEntry(key, knowledge, KnowledgeLayer.DOMAIN);
        entry.setKnowledgeType(type);
        entry.setImportance(importance);
        entry.setBrainDomain(brainDomain);
        domainKnowledgeBase.store(key, knowledge, createMetadata(type, importance));
        keyLayerMapping.put(key, KnowledgeLayer.DOMAIN);
        log.debug("Stored domain knowledge: {} for brain {}", key, brainDomain);
    }

    @Override
    public void storeShared(String key, Object knowledge, KnowledgeType type, Importance importance) {
        KnowledgeEntry entry = createEntry(key, knowledge, KnowledgeLayer.SHARED);
        entry.setKnowledgeType(type);
        entry.setImportance(importance);
        sharedKnowledgeBase.store(key, knowledge, createMetadata(type, importance));
        keyLayerMapping.put(key, KnowledgeLayer.SHARED);
        log.debug("Stored shared knowledge: {}", key);
    }

    @Override
    public Optional<KnowledgeEntry> retrieve(String key) {
        // Check cache first
        CacheEntry cached = entryCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return Optional.of(cached.entry());
        }
        if (cached != null) {
            entryCache.remove(key);
        }

        KnowledgeLayer layer = keyLayerMapping.get(key);
        Optional<KnowledgeEntry> result;
        if (layer != null) {
            result = retrieveFromLayer(key, layer);
        } else {
            result = retrieveFromLayer(key, KnowledgeLayer.PRIVATE);
            if (result.isEmpty()) {
                result = retrieveFromLayer(key, KnowledgeLayer.DOMAIN);
            }
            if (result.isEmpty()) {
                result = retrieveFromLayer(key, KnowledgeLayer.SHARED);
            }
        }

        result.ifPresent(entry -> putCache(key, entry));
        return result;
    }

    @Override
    public Optional<KnowledgeEntry> retrieveFromLayer(String key, KnowledgeLayer layer) {
        KnowledgeBase base = getBaseForLayer(layer);
        if (base == null) return Optional.empty();

        Optional<Object> content = base.retrieve(key);
        return content.map(c -> {
            KnowledgeEntry entry = new KnowledgeEntry(key, c);
            entry.setBrainDomain(brainDomain);
            entry.setNeuronId(neuronId);
            return entry;
        });
    }

    @Override
    public List<KnowledgeEntry> search(String query, int limit) {
        List<KnowledgeEntry> results = new ArrayList<>();

        results.addAll(searchInLayer(query, KnowledgeLayer.PRIVATE, limit));
        if (results.size() < limit) {
            results.addAll(searchInLayer(query, KnowledgeLayer.DOMAIN, limit - results.size()));
        }
        if (results.size() < limit) {
            results.addAll(searchInLayer(query, KnowledgeLayer.SHARED, limit - results.size()));
        }

        return results.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeEntry> searchInLayer(String query, KnowledgeLayer layer, int limit) {
        KnowledgeBase base = getBaseForLayer(layer);
        if (base == null) return Collections.emptyList();

        List<KnowledgeEntry> results = base.search(query);
        results.forEach(e -> {
            if (e.getBrainDomain() == null) e.setBrainDomain(brainDomain);
        });
        return results.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeEntry> searchSimilar(float[] vector, int limit) {
        List<KnowledgeEntry> results = new ArrayList<>();

        results.addAll(privateKnowledgeBase.searchSimilar(vector, limit));
        results.addAll(domainKnowledgeBase.searchSimilar(vector, limit));
        results.addAll(sharedKnowledgeBase.searchSimilar(vector, limit));

        return results.stream()
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeEntry> hybridSearch(String query, float[] queryVector, double vectorWeight, int limit) {
        List<KnowledgeEntry> results = new ArrayList<>();

        results.addAll(privateKnowledgeBase.hybridSearch(query, queryVector, vectorWeight, 1 - vectorWeight, limit));
        results.addAll(domainKnowledgeBase.hybridSearch(query, queryVector, vectorWeight, 1 - vectorWeight, limit));
        results.addAll(sharedKnowledgeBase.hybridSearch(query, queryVector, vectorWeight, 1 - vectorWeight, limit));

        return results.stream()
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public void update(String key, Object knowledge) {
        entryCache.remove(key);
        KnowledgeLayer layer = keyLayerMapping.get(key);
        if (layer == null) {
            log.warn("Cannot update knowledge: key {} not found in layer mapping", key);
            return;
        }

        KnowledgeBase base = getBaseForLayer(layer);
        if (base != null) {
            base.update(key, knowledge);
            log.debug("Updated knowledge: {} in layer {}", key, layer);
        }
    }

    @Override
    public void delete(String key) {
        entryCache.remove(key);
        KnowledgeLayer layer = keyLayerMapping.remove(key);
        if (layer == null) {
            log.warn("Cannot delete knowledge: key {} not found", key);
            return;
        }

        KnowledgeBase base = getBaseForLayer(layer);
        if (base != null) {
            base.delete(key);
            log.debug("Deleted knowledge: {} from layer {}", key, layer);
        }
    }

    @Override
    public void moveToLayer(String key, KnowledgeLayer targetLayer) {
        entryCache.remove(key);
        Optional<KnowledgeEntry> entry = retrieve(key);
        if (entry.isEmpty()) {
            log.warn("Cannot move knowledge: key {} not found", key);
            return;
        }

        KnowledgeEntry e = entry.get();
        delete(key);

        KnowledgeBase targetBase = getBaseForLayer(targetLayer);
        if (targetBase != null) {
            targetBase.store(key, e.getContent(), e.getTags());
            keyLayerMapping.put(key, targetLayer);
            log.info("Moved knowledge {} to layer {}", key, targetLayer);
        }
    }

    @Override
    public void promoteToDomain(String key) {
        if (!canPromoteToDomain(key)) {
            throw new IllegalStateException("知识 " + key + " 不满足晋升到部门层的条件：使用次数 >= 3 且有效性评分 >= 0.7");
        }
        moveToLayer(key, KnowledgeLayer.DOMAIN);
        Optional<KnowledgeEntry> entry = retrieve(key);
        entry.ifPresent(e -> e.setPromotedFrom(KnowledgeLayer.PRIVATE.name()));
        log.info("Promoted knowledge {} to DOMAIN layer", key);
    }

    @Override
    public void promoteToShared(String key) {
        if (!canPromoteToShared(key)) {
            throw new IllegalStateException("知识 " + key + " 不满足晋升到共享层的条件：使用次数 >= 10 且有效性评分 >= 0.8 且有跨部门引用");
        }
        moveToLayer(key, KnowledgeLayer.SHARED);
        Optional<KnowledgeEntry> entry = retrieve(key);
        entry.ifPresent(e -> e.setPromotedFrom(KnowledgeLayer.DOMAIN.name()));
        log.info("Promoted knowledge {} to SHARED layer", key);
    }

    @Override
    public boolean canPromoteToDomain(String key) {
        Optional<KnowledgeEntry> optEntry = retrieve(key);
        if (optEntry.isEmpty()) {
            log.warn("Cannot check promotion condition: key {} not found", key);
            return false;
        }
        KnowledgeEntry entry = optEntry.get();
        // 条件：使用次数 >= 3 且有效性评分 >= 0.7
        boolean accessCondition = entry.getAccessCount() >= 3;
        boolean validityCondition = entry.getConfidence() >= 0.7;
        boolean result = accessCondition && validityCondition;
        if (!result) {
            log.debug("Knowledge {} not eligible for DOMAIN promotion: accessCount={}, confidence={}",
                key, entry.getAccessCount(), entry.getConfidence());
        }
        return result;
    }

    @Override
    public boolean canPromoteToShared(String key) {
        Optional<KnowledgeEntry> optEntry = retrieve(key);
        if (optEntry.isEmpty()) {
            log.warn("Cannot check promotion condition: key {} not found", key);
            return false;
        }
        KnowledgeEntry entry = optEntry.get();
        // 条件：使用次数 >= 10 且有效性评分 >= 0.8 且有跨部门引用
        boolean accessCondition = entry.getAccessCount() >= 10;
        boolean validityCondition = entry.getConfidence() >= 0.8;
        boolean crossDeptCondition = hasCrossDepartmentReference(entry);
        boolean result = accessCondition && validityCondition && crossDeptCondition;
        if (!result) {
            log.debug("Knowledge {} not eligible for SHARED promotion: accessCount={}, confidence={}, crossDept={}",
                key, entry.getAccessCount(), entry.getConfidence(), crossDeptCondition);
        }
        return result;
    }

    @Override
    public void demoteToPrivate(String key) {
        KnowledgeLayer currentLayer = keyLayerMapping.get(key);
        if (currentLayer == null) {
            log.warn("Cannot demote knowledge: key {} not found in layer mapping", key);
            return;
        }
        if (currentLayer == KnowledgeLayer.PRIVATE) {
            log.warn("Knowledge {} is already in PRIVATE layer, cannot demote further", key);
            return;
        }
        moveToLayer(key, KnowledgeLayer.PRIVATE);
        log.info("Demoted knowledge {} from {} to PRIVATE layer", key, currentLayer);
    }

    @Override
    public void demoteToDepartment(String key) {
        KnowledgeLayer currentLayer = keyLayerMapping.get(key);
        if (currentLayer == null) {
            log.warn("Cannot demote knowledge: key {} not found in layer mapping", key);
            return;
        }
        if (currentLayer != KnowledgeLayer.SHARED) {
            log.warn("Knowledge {} is in {} layer, can only demote from SHARED to DOMAIN", key, currentLayer);
            return;
        }
        moveToLayer(key, KnowledgeLayer.DOMAIN);
        log.info("Demoted knowledge {} from SHARED to DOMAIN layer", key);
    }

    /**
     * 检查知识条目是否有跨部门引用
     */
    private boolean hasCrossDepartmentReference(KnowledgeEntry entry) {
        if (entry.getMetadata() == null) return false;
        Object crossDept = entry.getMetadata().get("crossDepartmentReferences");
        if (crossDept instanceof Number) {
            return ((Number) crossDept).intValue() > 0;
        }
        // 如果元数据中有其他部门标记，也视为跨部门引用
        Object deptCount = entry.getMetadata().get("referencingDepartmentCount");
        if (deptCount instanceof Number) {
            return ((Number) deptCount).intValue() > 1;
        }
        // 默认：已验证的知识视为可能有跨部门引用
        return entry.isVerified();
    }

    @Override
    public KnowledgeEntry publish(String key) {
        KnowledgeEntry entry = retrieve(key).orElse(null);
        if (entry == null) {
            throw new IllegalArgumentException("Knowledge entry not found: " + key);
        }
        if (entry.getStatus() != KnowledgeStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT entries can be published, current status: " + entry.getStatus());
        }
        entry.setStatus(KnowledgeStatus.PUBLISHED);
        entry.setVerified(true);
        entry.setUpdatedAt(java.time.Instant.now());
        update(key, entry.getContent());
        log.info("Knowledge entry published: key={}, scope={}", key, entry.getScope());
        return entry;
    }

    @Override
    public KnowledgeEntry archive(String key) {
        KnowledgeEntry entry = retrieve(key).orElse(null);
        if (entry == null) {
            throw new IllegalArgumentException("Knowledge entry not found: " + key);
        }
        if (entry.getStatus() != KnowledgeStatus.PUBLISHED && entry.getStatus() != KnowledgeStatus.DEPRECATED) {
            throw new IllegalStateException("Only PUBLISHED or DEPRECATED entries can be archived, current status: " + entry.getStatus());
        }
        entry.setStatus(KnowledgeStatus.ARCHIVED);
        entry.setUpdatedAt(java.time.Instant.now());
        update(key, entry.getContent());
        log.info("Knowledge entry archived: key={}", key);
        return entry;
    }

    @Override
    public KnowledgeEntry deprecate(String key) {
        KnowledgeEntry entry = retrieve(key).orElse(null);
        if (entry == null) {
            throw new IllegalArgumentException("Knowledge entry not found: " + key);
        }
        if (entry.getStatus() != KnowledgeStatus.PUBLISHED) {
            throw new IllegalStateException("Only PUBLISHED entries can be deprecated, current status: " + entry.getStatus());
        }
        entry.setStatus(KnowledgeStatus.DEPRECATED);
        entry.setUpdatedAt(java.time.Instant.now());
        update(key, entry.getContent());
        log.info("Knowledge entry deprecated: key={}", key);
        return entry;
    }

    @Override
    public KnowledgeEntry reactivate(String key) {
        KnowledgeEntry entry = retrieve(key).orElse(null);
        if (entry == null) {
            throw new IllegalArgumentException("Knowledge entry not found: " + key);
        }
        if (entry.getStatus() != KnowledgeStatus.ARCHIVED && entry.getStatus() != KnowledgeStatus.DEPRECATED) {
            throw new IllegalStateException("Only ARCHIVED or DEPRECATED entries can be reactivated, current status: " + entry.getStatus());
        }
        entry.setStatus(KnowledgeStatus.PUBLISHED);
        entry.setUpdatedAt(java.time.Instant.now());
        update(key, entry.getContent());
        log.info("Knowledge entry reactivated: key={}", key);
        return entry;
    }

    @Override
    public List<KnowledgeEntry> listByStatus(KnowledgeStatus status, String scope, int limit) {
        return search("", limit).stream()
            .filter(e -> e.getStatus() == status)
            .filter(e -> scope == null || scope.equals(e.getScopeIdentifier()) || scope.equals(e.getScope().name()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public void addExperience(Experience experience, String brainDomain) {
        domainKnowledgeBase.addExperience(experience);
        log.debug("Added experience for brain {}", brainDomain);
    }

    @Override
    public List<Experience> getExperiences(String context, String brainDomain) {
        return domainKnowledgeBase.getExperiences(context);
    }

    @Override
    public void recordBestPractice(BestPractice practice, String brainDomain) {
        sharedKnowledgeBase.recordBestPractice(practice);
        log.info("Recorded best practice: {} for brain {}", practice.getTitle(), brainDomain);
    }

    @Override
    public List<BestPractice> getBestPractices(String domain) {
        return sharedKnowledgeBase.getBestPractices(domain);
    }

    @Override
    public void shareKnowledge(String key, String targetBrainDomain) {
        Optional<KnowledgeEntry> entry = retrieve(key);
        if (entry.isEmpty()) {
            log.warn("Cannot share knowledge: key {} not found", key);
            return;
        }

        KnowledgeEntry e = entry.get();
        sharedKnowledgeBase.shareKnowledge(key, targetBrainDomain);
        log.info("Shared knowledge {} with brain {}", key, targetBrainDomain);
    }

    @Override
    public KnowledgeEvolution evolveKnowledge(String knowledgeId) {
        if (knowledgeEvolver == null) {
            log.warn("KnowledgeEvolver not configured");
            return null;
        }
        return knowledgeEvolver.evolveKnowledge(knowledgeId).orElse(null);
    }

    @Override
    public KnowledgeMergeResult mergeKnowledge(String sourceId, String targetId) {
        if (knowledgeEvolver == null) {
            log.warn("KnowledgeEvolver not configured");
            return null;
        }
        return knowledgeEvolver.mergeKnowledge(sourceId, targetId);
    }

    @Override
    public KnowledgePropagationResult propagateKnowledge(String knowledgeId, String targetBrainDomain) {
        if (knowledgeEvolver == null) {
            log.warn("KnowledgeEvolver not configured");
            return null;
        }
        return knowledgeEvolver.propagateKnowledge(neuronId, targetBrainDomain, knowledgeId);
    }

    @Override
    public KnowledgeQualityReport assessQuality() {
        if (knowledgeEvolver == null) {
            log.warn("KnowledgeEvolver not configured");
            return new KnowledgeQualityReport();
        }
        return knowledgeEvolver.assessQuality();
    }

    @Override
    public void cleanupExpired() {
        // Evict expired cache entries
        entryCache.entrySet().removeIf(e -> e.getValue().isExpired());

        privateKnowledgeBase.cleanupExpiredKnowledge(30);
        domainKnowledgeBase.cleanupExpiredKnowledge(90);
        sharedKnowledgeBase.cleanupExpiredKnowledge(365);
        log.info("Cleaned up expired knowledge");
    }

    @Override
    public void updateRelevanceScores() {
        updateRelevanceScoresForBase(privateKnowledgeBase);
        updateRelevanceScoresForBase(domainKnowledgeBase);
        updateRelevanceScoresForBase(sharedKnowledgeBase);
        log.debug("Updated relevance scores");
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("brainDomain", brainDomain);
        stats.put("neuronId", neuronId);
        stats.put("privateCount", getPrivateKnowledgeCount());
        stats.put("domainCount", getDomainKnowledgeCount());
        stats.put("sharedCount", getSharedKnowledgeCount());
        stats.put("totalExperiences", 
            privateKnowledgeBase.getExperienceCount() + 
            domainKnowledgeBase.getExperienceCount() + 
            sharedKnowledgeBase.getExperienceCount());
        return stats;
    }

    @Override
    public int getPrivateKnowledgeCount() {
        return privateKnowledgeBase.getKnowledgeCount();
    }

    @Override
    public int getDomainKnowledgeCount() {
        return domainKnowledgeBase.getKnowledgeCount();
    }

    @Override
    public int getSharedKnowledgeCount() {
        return sharedKnowledgeBase.getKnowledgeCount();
    }

    private KnowledgeEntry createEntry(String key, Object content, KnowledgeLayer layer) {
        KnowledgeEntry entry = new KnowledgeEntry(key, content);
        entry.setBrainDomain(brainDomain);
        entry.setNeuronId(neuronId);
        return entry;
    }

    private Map<String, String> createMetadata(KnowledgeType type, Importance importance) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", type.name());
        metadata.put("importance", importance.name());
        metadata.put("brainDomain", brainDomain);
        metadata.put("neuronId", neuronId);
        return metadata;
    }

    private KnowledgeBase getBaseForLayer(KnowledgeLayer layer) {
        switch (layer) {
            case PRIVATE: return privateKnowledgeBase;
            case DOMAIN: return domainKnowledgeBase;
            case SHARED: return sharedKnowledgeBase;
            default: return null;
        }
    }

    private void updateRelevanceScoresForBase(KnowledgeBase base) {
        base.getMostAccessed(1000).forEach(entry -> {
            double newScore = entry.calculateRelevanceScore();
            base.updateKnowledgeRelevance(entry.getKey(), newScore - entry.getRelevanceScore());
        });
    }

    private void putCache(String key, KnowledgeEntry entry) {
        if (entryCache.size() >= CACHE_MAX_SIZE) {
            // Evict expired entries first
            entryCache.entrySet().removeIf(e -> e.getValue().isExpired());
            // If still over limit, remove oldest entries (approximate LRU by removing 10%)
            if (entryCache.size() >= CACHE_MAX_SIZE) {
                int toRemove = Math.max(1, entryCache.size() / 10);
                var iterator = entryCache.keySet().iterator();
                for (int i = 0; i < toRemove && iterator.hasNext(); i++) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
        entryCache.put(key, new CacheEntry(entry, System.currentTimeMillis()));
    }
}
