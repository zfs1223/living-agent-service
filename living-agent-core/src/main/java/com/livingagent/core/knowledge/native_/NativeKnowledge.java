package com.livingagent.core.knowledge.native_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NativeKnowledge {
    
    private static final Logger log = LoggerFactory.getLogger(NativeKnowledge.class);
    
    private static boolean libraryLoaded = false;
    
    static {
        try {
            System.loadLibrary("living_agent_native");
            libraryLoaded = true;
            log.info("Native knowledge library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            log.warn("Native knowledge library not available, falling back to Java implementation: {}", e.getMessage());
            libraryLoaded = false;
        }
    }
    
    private boolean initialized = false;
    
    public static boolean isNativeAvailable() {
        return libraryLoaded;
    }
    
    public synchronized void init(String dbPath, int vectorDimension, int cacheSize) {
        if (!libraryLoaded) {
            log.warn("Native library not loaded, skipping native initialization");
            return;
        }
        
        if (initialized) {
            log.warn("Native knowledge already initialized");
            return;
        }
        
        try {
            nativeInit(dbPath, vectorDimension, cacheSize);
            initialized = true;
            log.info("Native knowledge initialized: db={}, dim={}, cache={}", dbPath, vectorDimension, cacheSize);
        } catch (Exception e) {
            log.error("Failed to initialize native knowledge", e);
            throw new RuntimeException("Failed to initialize native knowledge", e);
        }
    }
    
    public boolean store(String key, String content, String knowledgeType, 
                         String brainDomain, String importance, String validity,
                         float[] vector) {
        if (!libraryLoaded || !initialized) {
            return false;
        }
        
        try {
            byte[] vectorBytes = vector != null ? vectorToBytes(vector) : null;
            return nativeStore(key, content, knowledgeType, brainDomain, 
                             importance, validity, vectorBytes);
        } catch (Exception e) {
            log.error("Failed to store knowledge natively: {}", key, e);
            return false;
        }
    }
    
    public Map<String, Object> retrieve(String key) {
        if (!libraryLoaded || !initialized) {
            return null;
        }
        
        try {
            return nativeRetrieve(key);
        } catch (Exception e) {
            log.error("Failed to retrieve knowledge natively: {}", key, e);
            return null;
        }
    }
    
    public List<Map<String, Object>> search(String query, int limit) {
        if (!libraryLoaded || !initialized) {
            return new ArrayList<>();
        }
        
        try {
            return nativeSearch(query, limit);
        } catch (Exception e) {
            log.error("Failed to search knowledge natively: {}", query, e);
            return new ArrayList<>();
        }
    }
    
    public List<Map<String, Object>> vectorSearch(float[] queryVector, int topK, float threshold) {
        if (!libraryLoaded || !initialized) {
            return new ArrayList<>();
        }
        
        try {
            byte[] vectorBytes = vectorToBytes(queryVector);
            return nativeVectorSearch(vectorBytes, topK, threshold);
        } catch (Exception e) {
            log.error("Failed to vector search knowledge natively", e);
            return new ArrayList<>();
        }
    }
    
    public float cosineSimilarity(float[] vec1, float[] vec2) {
        if (!libraryLoaded || !initialized) {
            return 0.0f;
        }
        
        try {
            byte[] bytes1 = vectorToBytes(vec1);
            byte[] bytes2 = vectorToBytes(vec2);
            return nativeCosineSimilarity(bytes1, bytes2);
        } catch (Exception e) {
            log.error("Failed to compute cosine similarity natively", e);
            return 0.0f;
        }
    }
    
    public boolean delete(String key) {
        if (!libraryLoaded || !initialized) {
            return false;
        }
        
        try {
            return nativeDelete(key);
        } catch (Exception e) {
            log.error("Failed to delete knowledge natively: {}", key, e);
            return false;
        }
    }
    
    public long count() {
        if (!libraryLoaded || !initialized) {
            return 0;
        }
        
        try {
            return nativeCount();
        } catch (Exception e) {
            log.error("Failed to count knowledge natively", e);
            return 0;
        }
    }
    
    public long cleanupExpired() {
        if (!libraryLoaded || !initialized) {
            return 0;
        }
        
        try {
            return nativeCleanupExpired();
        } catch (Exception e) {
            log.error("Failed to cleanup expired knowledge natively", e);
            return 0;
        }
    }
    
    public Map<String, Object> cacheStats() {
        if (!libraryLoaded || !initialized) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("hits", 0L);
            empty.put("misses", 0L);
            empty.put("hit_rate", 0.0);
            return empty;
        }
        
        try {
            return nativeCacheStats();
        } catch (Exception e) {
            log.error("Failed to get cache stats natively", e);
            return new HashMap<>();
        }
    }
    
    private byte[] vectorToBytes(float[] vector) {
        if (vector == null) return null;
        
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }
    
    private float[] bytesToVector(byte[] bytes) {
        if (bytes == null) return null;
        
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
    
    private native void nativeInit(String dbPath, int vectorDimension, int cacheSize);
    
    private native boolean nativeStore(String key, String content, String knowledgeType,
                                       String brainDomain, String importance, String validity,
                                       byte[] vector);
    
    private native Map<String, Object> nativeRetrieve(String key);
    
    private native List<Map<String, Object>> nativeSearch(String query, int limit);
    
    private native List<Map<String, Object>> nativeVectorSearch(byte[] queryVector, int topK, float threshold);
    
    private native float nativeCosineSimilarity(byte[] vec1, byte[] vec2);
    
    private native boolean nativeDelete(String key);
    
    private native long nativeCount();
    
    private native long nativeCleanupExpired();
    
    private native Map<String, Object> nativeCacheStats();
}
