package com.livingagent.core.nativelib;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MemoryNative {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        NativeLibrary.isLoaded();
    }
    
    public static native long createBackend(String dbPath, int maxEntries);
    
    public static native void destroyBackend(long handle);
    
    public static native boolean store(long handle, String key, String content, String category, String sessionId);
    
    public static native String recall(long handle, String query, int limit, String sessionId);
    
    public static native String get(long handle, String key);
    
    public static native boolean forget(long handle, String key);
    
    public static native int count(long handle);
    
    public static class Backend implements AutoCloseable {
        
        private final long handle;
        private volatile boolean closed = false;
        
        public Backend(String dbPath, int maxEntries) {
            this.handle = createBackend(dbPath, maxEntries);
            if (this.handle == 0) {
                throw new RuntimeException("Failed to create memory backend");
            }
        }
        
        public boolean store(String key, String content, String category, String sessionId) {
            checkClosed();
            return MemoryNative.store(handle, key, content, category, sessionId);
        }
        
        public List<Map<String, Object>> recall(String query, int limit, String sessionId) {
            checkClosed();
            String json = MemoryNative.recall(handle, query, limit, sessionId);
            if (json == null) {
                return new ArrayList<>();
            }
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = objectMapper.readValue(json, List.class);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse memory entries", e);
            }
        }
        
        public Map<String, Object> get(String key) {
            checkClosed();
            String json = MemoryNative.get(handle, key);
            if (json == null) {
                return null;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(json, Map.class);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse memory entry", e);
            }
        }
        
        public boolean forget(String key) {
            checkClosed();
            return MemoryNative.forget(handle, key);
        }
        
        public int count() {
            checkClosed();
            return MemoryNative.count(handle);
        }
        
        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Backend is closed");
            }
        }
        
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                destroyBackend(handle);
            }
        }
    }
}
