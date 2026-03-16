package com.livingagent.core.embedding.optimization;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface VectorIndexOptimizer {

    IndexStats getIndexStats(String collectionName);
    
    Map<String, IndexStats> getAllIndexStats();
    
    void optimizeIndex(String collectionName);
    
    void rebuildIndex(String collectionName);
    
    void warmupIndex(String collectionName);
    
    BatchIndexResult batchIndex(String collectionName, List<IndexItem> items);
    
    void setIndexConfig(String collectionName, IndexConfig config);
    
    IndexConfig getIndexConfig(String collectionName);
    
    QueryPlan analyzeQuery(String collectionName, float[] queryVector, int limit);
    
    SearchResultStats executeWithStats(String collectionName, float[] queryVector, int limit);
    
    record IndexStats(
        String collectionName,
        long vectorCount,
        long indexedCount,
        double indexSizeMB,
        double fragmentationRatio,
        String indexType,
        boolean isOptimized,
        Instant lastOptimized,
        Duration averageQueryTime
    ) {}
    
    record IndexItem(
        String id,
        float[] vector,
        Map<String, Object> payload
    ) {}
    
    record BatchIndexResult(
        int successCount,
        int failureCount,
        long durationMs,
        List<String> failedIds,
        double throughputPerSecond
    ) {}
    
    record IndexConfig(
        int hnswM,
        int hnswEfConstruct,
        int quantizationBits,
        boolean useQuantization,
        int maxSegmentSize,
        int replicationFactor
    ) {
        public static IndexConfig defaultConfig() {
            return new IndexConfig(
                16,
                100,
                8,
                true,
                100000,
                2
            );
        }
        
        public static IndexConfig highPerformance() {
            return new IndexConfig(
                32,
                200,
                8,
                true,
                500000,
                3
            );
        }
        
        public static IndexConfig memoryOptimized() {
            return new IndexConfig(
                12,
                64,
                4,
                true,
                50000,
                1
            );
        }
    }
    
    record QueryPlan(
        String collectionName,
        int estimatedResults,
        Duration estimatedTime,
        List<String> optimizationSteps,
        Map<String, Object> parameters
    ) {}
    
    record SearchResultStats(
        int resultCount,
        long queryTimeMs,
        double averageScore,
        int vectorsScanned,
        double cacheHitRate,
        List<String> optimizationApplied
    ) {}
}
