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

    private final KnowledgeBase privateKnowledgeBase;
    private final KnowledgeBase domainKnowledgeBase;
    private final KnowledgeBase sharedKnowledgeBase;
    private final KnowledgeEvolver knowledgeEvolver;

    private String brainDomain;
    private String neuronId;

    private final Map<String, KnowledgeLayer> keyLayerMapping = new ConcurrentHashMap<>();

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
        KnowledgeLayer layer = keyLayerMapping.get(key);
        if (layer != null) {
            return retrieveFromLayer(key, layer);
        }

        Optional<KnowledgeEntry> entry = retrieveFromLayer(key, KnowledgeLayer.PRIVATE);
        if (entry.isPresent()) return entry;

        entry = retrieveFromLayer(key, KnowledgeLayer.DOMAIN);
        if (entry.isPresent()) return entry;

        return retrieveFromLayer(key, KnowledgeLayer.SHARED);
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
        moveToLayer(key, KnowledgeLayer.DOMAIN);
    }

    @Override
    public void promoteToShared(String key) {
        moveToLayer(key, KnowledgeLayer.SHARED);
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
}
