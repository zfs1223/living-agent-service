package com.livingagent.core.knowledge;

import com.livingagent.core.evolution.KnowledgeEvolver;
import com.livingagent.core.evolution.KnowledgeEvolution;
import com.livingagent.core.evolution.KnowledgeMergeResult;
import com.livingagent.core.evolution.KnowledgePropagationResult;
import com.livingagent.core.evolution.KnowledgeQualityReport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeManager {

    void initialize(String brainDomain, String neuronId);

    void storePrivate(String key, Object knowledge, Map<String, String> metadata);

    void storeDomain(String key, Object knowledge, KnowledgeType type, Importance importance);

    void storeShared(String key, Object knowledge, KnowledgeType type, Importance importance);

    Optional<KnowledgeEntry> retrieve(String key);

    Optional<KnowledgeEntry> retrieveFromLayer(String key, KnowledgeLayer layer);

    List<KnowledgeEntry> search(String query, int limit);

    List<KnowledgeEntry> searchInLayer(String query, KnowledgeLayer layer, int limit);

    List<KnowledgeEntry> searchSimilar(float[] vector, int limit);

    List<KnowledgeEntry> hybridSearch(String query, float[] queryVector, double vectorWeight, int limit);

    void update(String key, Object knowledge);

    void delete(String key);

    void moveToLayer(String key, KnowledgeLayer targetLayer);

    void promoteToDomain(String key);

    void promoteToShared(String key);

    void addExperience(Experience experience, String brainDomain);

    List<Experience> getExperiences(String context, String brainDomain);

    void recordBestPractice(BestPractice practice, String brainDomain);

    List<BestPractice> getBestPractices(String domain);

    void shareKnowledge(String key, String targetBrainDomain);

    KnowledgeEvolution evolveKnowledge(String knowledgeId);

    KnowledgeMergeResult mergeKnowledge(String sourceId, String targetId);

    KnowledgePropagationResult propagateKnowledge(String knowledgeId, String targetBrainDomain);

    KnowledgeQualityReport assessQuality();

    void cleanupExpired();

    void updateRelevanceScores();

    Map<String, Object> getStatistics();

    int getPrivateKnowledgeCount();

    int getDomainKnowledgeCount();

    int getSharedKnowledgeCount();

    enum KnowledgeLayer {
        PRIVATE(1, "神经元私有知识"),
        DOMAIN(2, "大脑领域知识"),
        SHARED(3, "共享知识库");

        private final int level;
        private final String description;

        KnowledgeLayer(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDescription() { return description; }

        public boolean canAccessFrom(KnowledgeLayer accessorLayer) {
            return accessorLayer.level >= this.level;
        }
    }

    class KnowledgeQuery {
        private String query;
        private float[] vector;
        private KnowledgeLayer layer;
        private String brainDomain;
        private String neuronId;
        private KnowledgeType type;
        private Importance minImportance;
        private int limit = 10;
        private double vectorWeight = 0.7;
        private boolean includeExpired = false;

        public KnowledgeQuery(String query) {
            this.query = query;
        }

        public static KnowledgeQuery create(String query) {
            return new KnowledgeQuery(query);
        }

        public KnowledgeQuery withVector(float[] vector) {
            this.vector = vector;
            return this;
        }

        public KnowledgeQuery inLayer(KnowledgeLayer layer) {
            this.layer = layer;
            return this;
        }

        public KnowledgeQuery inBrainDomain(String brainDomain) {
            this.brainDomain = brainDomain;
            return this;
        }

        public KnowledgeQuery fromNeuron(String neuronId) {
            this.neuronId = neuronId;
            return this;
        }

        public KnowledgeQuery ofType(KnowledgeType type) {
            this.type = type;
            return this;
        }

        public KnowledgeQuery minImportance(Importance importance) {
            this.minImportance = importance;
            return this;
        }

        public KnowledgeQuery limit(int limit) {
            this.limit = limit;
            return this;
        }

        public KnowledgeQuery vectorWeight(double weight) {
            this.vectorWeight = weight;
            return this;
        }

        public KnowledgeQuery includeExpired(boolean include) {
            this.includeExpired = include;
            return this;
        }

        public String getQuery() { return query; }
        public float[] getVector() { return vector; }
        public KnowledgeLayer getLayer() { return layer; }
        public String getBrainDomain() { return brainDomain; }
        public String getNeuronId() { return neuronId; }
        public KnowledgeType getType() { return type; }
        public Importance getMinImportance() { return minImportance; }
        public int getLimit() { return limit; }
        public double getVectorWeight() { return vectorWeight; }
        public boolean isIncludeExpired() { return includeExpired; }
    }
}
