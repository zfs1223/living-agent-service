package com.livingagent.core.embedding.optimization.impl;

import com.livingagent.core.database.vector.QdrantVectorService;
import com.livingagent.core.embedding.optimization.VectorIndexOptimizer;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * R5: VectorIndexOptimizer 基于 Qdrant 的实现。
 * 提供索引统计、优化、重建、批量索引和查询分析能力。
 */
@Service
public class QdrantVectorIndexOptimizer implements VectorIndexOptimizer {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorIndexOptimizer.class);

    private final QdrantClient qdrantClient;
    private final QdrantVectorService qdrantVectorService;
    private final String collectionPrefix;

    private final Map<String, IndexConfig> indexConfigs = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastOptimized = new ConcurrentHashMap<>();

    /** R5: 碎片化阈值，超过此值自动触发 optimizeIndex */
    private double fragmentationThreshold = 0.3;

    public QdrantVectorIndexOptimizer(QdrantClient qdrantClient,
                                       QdrantVectorService qdrantVectorService,
                                       @Value("${qdrant.collection-prefix:living_agent_}") String collectionPrefix) {
        this.qdrantClient = qdrantClient;
        this.qdrantVectorService = qdrantVectorService;
        this.collectionPrefix = collectionPrefix;
    }

    @Override
    public IndexStats getIndexStats(String collectionName) {
        String fullName = collectionPrefix + collectionName;
        try {
            CollectionInfo info = qdrantClient.getCollectionInfoAsync(fullName).get();
            long vectorCount = info.getVectorsCount();
            long indexedCount = info.getIndexedVectorsCount();
            double fragmentation = vectorCount > 0 ? 1.0 - (double) indexedCount / vectorCount : 0.0;

            return new IndexStats(
                collectionName, vectorCount, indexedCount,
                info.getPayloadSchemaCount() * 0.001,
                fragmentation, "HNSW",
                fragmentation < fragmentationThreshold,
                lastOptimized.get(collectionName),
                Duration.ofMillis(5)
            );
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Failed to get index stats for {}: {}", fullName, e.getMessage());
            Thread.currentThread().interrupt();
            return new IndexStats(collectionName, 0, 0, 0, 1.0, "UNKNOWN", false, null, Duration.ZERO);
        }
    }

    @Override
    public Map<String, IndexStats> getAllIndexStats() {
        Map<String, IndexStats> stats = new LinkedHashMap<>();
        for (String name : List.of("knowledge", "employee", "experience")) {
            stats.put(name, getIndexStats(name));
        }
        return stats;
    }

    @Override
    public void optimizeIndex(String collectionName) {
        log.info("Optimizing index for collection: {} (Qdrant auto-optimizes via HNSW)", collectionName);
        // Qdrant 使用 HNSW 索引，自动优化。这里只更新时间戳记录
        lastOptimized.put(collectionName, Instant.now());
        log.info("Index optimization timestamp updated for {}", collectionName);
    }

    @Override
    public void rebuildIndex(String collectionName) {
        log.info("Rebuilding index for collection: {} (delete + recreate + re-upsert)", collectionName);
        qdrantVectorService.deleteCollection(collectionName);
        int vectorSize = "employee".equals(collectionName) ? 192 : 1024;
        qdrantVectorService.createCollectionIfNotExists(collectionName, vectorSize);
        lastOptimized.put(collectionName, Instant.now());
        log.info("Index rebuilt for {} — vectors need to be re-upserted via migration", collectionName);
    }

    @Override
    public void warmupIndex(String collectionName) {
        log.info("Warming up index for collection: {}", collectionName);
        try {
            String fullName = collectionPrefix + collectionName;
            float[] dummy = new float["employee".equals(collectionName) ? 192 : 1024];
            Arrays.fill(dummy, 0.0f);
            qdrantVectorService.search(collectionName, dummy, 1, 0.0f);
            log.info("Index warmup completed for {}", collectionName);
        } catch (Exception e) {
            log.warn("Index warmup failed for {}: {}", collectionName, e.getMessage());
        }
    }

    @Override
    public BatchIndexResult batchIndex(String collectionName, List<IndexItem> items) {
        long start = System.currentTimeMillis();
        int success = 0;
        List<String> failedIds = new ArrayList<>();

        for (IndexItem item : items) {
            try {
                qdrantVectorService.upsertVector(collectionName, item.id(), item.vector(), item.payload());
                success++;
            } catch (Exception e) {
                failedIds.add(item.id());
                log.warn("Batch index failed for id={}: {}", item.id(), e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        double throughput = duration > 0 ? (double) success / duration * 1000 : 0;
        return new BatchIndexResult(success, failedIds.size(), duration, failedIds, throughput);
    }

    @Override
    public void setIndexConfig(String collectionName, IndexConfig config) {
        indexConfigs.put(collectionName, config);
        log.info("Index config updated for {}: hnswM={}, efConstruct={}", collectionName, config.hnswM(), config.hnswEfConstruct());
    }

    @Override
    public IndexConfig getIndexConfig(String collectionName) {
        return indexConfigs.getOrDefault(collectionName, IndexConfig.defaultConfig());
    }

    @Override
    public QueryPlan analyzeQuery(String collectionName, float[] queryVector, int limit) {
        IndexStats stats = getIndexStats(collectionName);
        List<String> steps = new ArrayList<>();
        if (stats.fragmentationRatio() > fragmentationThreshold) {
            steps.add("RECOMMEND_OPTIMIZE: fragmentation=" + String.format("%.2f", stats.fragmentationRatio()));
        }
        return new QueryPlan(collectionName, limit, Duration.ofMillis(5), steps, Map.of("vectorCount", stats.vectorCount()));
    }

    @Override
    public SearchResultStats executeWithStats(String collectionName, float[] queryVector, int limit) {
        long start = System.currentTimeMillis();
        var results = qdrantVectorService.search(collectionName, queryVector, limit, 0.0f);
        long queryTime = System.currentTimeMillis() - start;

        double avgScore = results.stream().mapToDouble(QdrantVectorService.SearchResult::getScore).average().orElse(0);
        return new SearchResultStats(results.size(), queryTime, avgScore, results.size(), 0.0, List.of());
    }

    /** R5: 定期检查碎片化，自动触发优化 */
    @Scheduled(fixedDelayString = "${vector.index.optimize-interval-ms:3600000}")
    public void scheduledOptimizeIfNeeded() {
        for (var entry : getAllIndexStats().entrySet()) {
            if (entry.getValue().fragmentationRatio() > fragmentationThreshold) {
                log.info("Auto-optimizing {} (fragmentation={})", entry.getKey(), String.format("%.2f", entry.getValue().fragmentationRatio()));
                optimizeIndex(entry.getKey());
            }
        }
    }

    public void setFragmentationThreshold(double threshold) {
        this.fragmentationThreshold = threshold;
    }
}
