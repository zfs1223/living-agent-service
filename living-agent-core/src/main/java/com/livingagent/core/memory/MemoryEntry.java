package com.livingagent.core.memory;

import java.time.Instant;
import java.util.UUID;

public record MemoryEntry(
    String id,
    String key,
    String content,
    MemoryCategory category,
    Instant timestamp,
    String sessionId,
    Double score
) {
    public static MemoryEntry of(String key, String content, MemoryCategory category, String sessionId) {
        return new MemoryEntry(
            UUID.randomUUID().toString(),
            key,
            content,
            category,
            Instant.now(),
            sessionId,
            null
        );
    }
    
    public static MemoryEntry core(String key, String content) {
        return of(key, content, MemoryCategory.CORE, null);
    }
    
    public static MemoryEntry daily(String key, String content, String sessionId) {
        return of(key, content, MemoryCategory.DAILY, sessionId);
    }
    
    public static MemoryEntry conversation(String key, String content, String sessionId) {
        return of(key, content, MemoryCategory.CONVERSATION, sessionId);
    }
    
    public MemoryEntry withScore(double score) {
        return new MemoryEntry(id, key, content, category, timestamp, sessionId, score);
    }
    
    public boolean hasScore() {
        return score != null && score > 0;
    }
}
