package com.livingagent.core.embedding;

import java.util.List;
import java.util.Map;

public interface EmbeddingService {

    float[] embed(String text);
    
    List<float[]> embedBatch(List<String> texts);
    
    String getModelName();
    
    int getDimension();
    
    int getMaxBatchSize();
    
    EmbeddingStats getStats();
    
    void warmup();
    
    boolean isReady();
    
    record EmbeddingStats(
        long totalEmbeddings,
        long totalTokens,
        double averageLatencyMs,
        long cacheHits,
        long cacheMisses,
        double cacheHitRate
    ) {}
    
    record EmbeddingResult(
        String text,
        float[] vector,
        int tokenCount,
        long latencyMs
    ) {}
    
    record EmbeddingConfig(
        String modelPath,
        int dimension,
        int maxBatchSize,
        int maxSequenceLength,
        boolean useGpu,
        int gpuLayers,
        int threads,
        boolean enableCache,
        int cacheSize
    ) {
        public static EmbeddingConfig defaultConfig() {
            return new EmbeddingConfig(
                "bge-m3-Q8_0.gguf",
                1024,
                32,
                8192,
                true,
                35,
                8,
                true,
                10000
            );
        }
    }
}
