package com.livingagent.core.embedding.impl;

import com.livingagent.core.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LocalEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(LocalEmbeddingService.class);

    private final String modelPath;
    private final int dimension;
    private final int maxBatchSize;
    private final int maxSequenceLength;
    private final boolean useGpu;
    private final int threads;
    private final boolean enableCache;
    private final int cacheSize;

    private volatile boolean initialized = false;
    private volatile long modelHandle = 0;

    private final Map<String, float[]> embeddingCache;
    private final AtomicLong totalEmbeddings = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public LocalEmbeddingService(
            @Value("${embedding.model-path:bge-m3-Q8_0.gguf}") String modelPath,
            @Value("${embedding.dimension:1024}") int dimension,
            @Value("${embedding.max-batch-size:32}") int maxBatchSize,
            @Value("${embedding.max-sequence-length:8192}") int maxSequenceLength,
            @Value("${embedding.use-gpu:true}") boolean useGpu,
            @Value("${embedding.threads:8}") int threads,
            @Value("${embedding.enable-cache:true}") boolean enableCache,
            @Value("${embedding.cache-size:10000}") int cacheSize) {
        
        this.modelPath = modelPath;
        this.dimension = dimension;
        this.maxBatchSize = maxBatchSize;
        this.maxSequenceLength = maxSequenceLength;
        this.useGpu = useGpu;
        this.threads = threads;
        this.enableCache = enableCache;
        this.cacheSize = cacheSize;
        
        this.embeddingCache = enableCache 
            ? new ConcurrentHashMap<>(cacheSize) 
            : null;
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            Path path = resolveModelPath();
            if (!Files.exists(path)) {
                log.warn("Model file not found at: {}, using mock embeddings", path);
                initialized = true;
                return;
            }
            
            log.info("Loading embedding model from: {}", path);
            long startTime = System.currentTimeMillis();
            
            modelHandle = loadModelNative(path.toString(), dimension, threads, useGpu);
            
            if (modelHandle != 0) {
                initialized = true;
                log.info("Embedding model loaded in {}ms", System.currentTimeMillis() - startTime);
            } else {
                log.warn("Failed to load embedding model, using mock embeddings");
                initialized = true;
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize embedding service: {}", e.getMessage());
            initialized = true;
        }
    }

    private Path resolveModelPath() {
        Path path = Paths.get(modelPath);
        if (path.isAbsolute()) {
            return path;
        }
        
        Path aiModelsPath = Paths.get("f:\\SoarCloudAI\\ai-models\\bge-m3-GGUF", modelPath);
        if (Files.exists(aiModelsPath)) {
            return aiModelsPath;
        }
        
        return Paths.get("models", modelPath);
    }

    @Override
    public float[] embed(String text) {
        if (!initialized) {
            initialize();
        }
        
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }
        
        String cacheKey = enableCache ? computeCacheKey(text) : null;
        
        if (enableCache && cacheKey != null) {
            float[] cached = embeddingCache.get(cacheKey);
            if (cached != null) {
                cacheHits.incrementAndGet();
                return Arrays.copyOf(cached, cached.length);
            }
            cacheMisses.incrementAndGet();
        }
        
        long startTime = System.currentTimeMillis();
        float[] embedding;
        
        if (modelHandle != 0) {
            embedding = embedNative(modelHandle, text, maxSequenceLength);
        } else {
            embedding = generateMockEmbedding(text);
        }
        
        long latency = System.currentTimeMillis() - startTime;
        totalEmbeddings.incrementAndGet();
        totalTokens.addAndGet(estimateTokens(text));
        totalLatencyMs.addAndGet(latency);
        
        if (enableCache && cacheKey != null && embeddingCache.size() < cacheSize) {
            embeddingCache.put(cacheKey, Arrays.copyOf(embedding, embedding.length));
        }
        
        return embedding;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!initialized) {
            initialize();
        }
        
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        
        List<float[]> results = new ArrayList<>(texts.size());
        
        for (int i = 0; i < texts.size(); i += maxBatchSize) {
            int end = Math.min(i + maxBatchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            
            if (modelHandle != 0) {
                float[][] batchResults = embedBatchNative(modelHandle, batch.toArray(new String[0]), maxSequenceLength);
                for (float[] result : batchResults) {
                    results.add(result);
                }
            } else {
                for (String text : batch) {
                    results.add(generateMockEmbedding(text));
                }
            }
        }
        
        totalEmbeddings.addAndGet(texts.size());
        
        return results;
    }

    @Override
    public String getModelName() {
        return "bge-m3";
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    @Override
    public EmbeddingStats getStats() {
        long total = totalEmbeddings.get();
        double avgLatency = total > 0 ? (double) totalLatencyMs.get() / total : 0;
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0;
        
        return new EmbeddingStats(
            total,
            totalTokens.get(),
            avgLatency,
            hits,
            misses,
            hitRate
        );
    }

    @Override
    public void warmup() {
        if (!initialized) {
            initialize();
        }
        
        log.info("Warming up embedding model...");
        long startTime = System.currentTimeMillis();
        
        embed("Hello, world!");
        embedBatch(List.of("Test 1", "Test 2", "Test 3"));
        
        log.info("Warmup completed in {}ms", System.currentTimeMillis() - startTime);
    }

    @Override
    public boolean isReady() {
        return initialized;
    }

    private String computeCacheKey(String text) {
        if (text.length() > 256) {
            return text.hashCode() + "_" + text.length();
        }
        return String.valueOf(text.hashCode());
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }

    private float[] generateMockEmbedding(String text) {
        float[] embedding = new float[dimension];
        Random random = new Random(text.hashCode());
        
        for (int i = 0; i < dimension; i++) {
            embedding[i] = (float) (random.nextGaussian() * 0.1);
        }
        
        double norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                embedding[i] /= norm;
            }
        }
        
        return embedding;
    }

    private native long loadModelNative(String modelPath, int dimension, int threads, boolean useGpu);
    private native void unloadModelNative(long modelHandle);
    private native float[] embedNative(long modelHandle, String text, int maxLength);
    private native float[][] embedBatchNative(long modelHandle, String[] texts, int maxLength);

    static {
        try {
            System.loadLibrary("embedding_native");
            log.info("Loaded embedding_native library");
        } catch (UnsatisfiedLinkError e) {
            log.info("embedding_native library not found, using mock embeddings");
        }
    }
}
