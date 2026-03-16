package com.livingagent.core.memory.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryBackend;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.memory.MemoryEntry;

public class MemoryServiceImpl implements Memory {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryServiceImpl.class);
    
    private final MemoryBackend backend;
    private final String name;
    
    public MemoryServiceImpl(MemoryBackend backend) {
        this(backend, "default");
    }
    
    public MemoryServiceImpl(MemoryBackend backend, String name) {
        this.backend = backend;
        this.name = name;
    }
    
    @Override
    public String name() {
        return name;
    }
    
    @Override
    public CompletableFuture<Void> store(String key, String content, MemoryCategory category, String sessionId) {
        log.debug("Storing memory: key={}, category={}, session={}", key, category, sessionId);
        return backend.store(key, content, category, sessionId);
    }
    
    @Override
    public CompletableFuture<List<MemoryEntry>> recall(String query, int limit, String sessionId) {
        log.debug("Recalling memories: query={}, limit={}, session={}", query, limit, sessionId);
        return backend.recall(query, limit, sessionId);
    }
    
    @Override
    public CompletableFuture<Optional<MemoryEntry>> get(String key) {
        log.debug("Getting memory: key={}", key);
        return backend.get(key);
    }
    
    @Override
    public CompletableFuture<List<MemoryEntry>> list(MemoryCategory category, String sessionId) {
        log.debug("Listing memories: category={}, session={}", category, sessionId);
        return backend.list(category, sessionId);
    }
    
    @Override
    public CompletableFuture<Boolean> forget(String key) {
        log.debug("Forgetting memory: key={}", key);
        return backend.forget(key);
    }
    
    @Override
    public CompletableFuture<Integer> count() {
        return backend.count();
    }
    
    @Override
    public CompletableFuture<Boolean> healthCheck() {
        return backend.healthCheck();
    }
    
    public CompletableFuture<Void> initialize() {
        return backend.initialize();
    }
    
    public CompletableFuture<Void> close() {
        return backend.close();
    }
    
    public MemoryBackend getBackend() {
        return backend;
    }
}
