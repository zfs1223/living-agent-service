package com.livingagent.core.memory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Memory {

    String name();

    CompletableFuture<Void> store(String key, String content, MemoryCategory category, String sessionId);

    CompletableFuture<List<MemoryEntry>> recall(String query, int limit, String sessionId);

    CompletableFuture<Optional<MemoryEntry>> get(String key);

    CompletableFuture<List<MemoryEntry>> list(MemoryCategory category, String sessionId);

    CompletableFuture<Boolean> forget(String key);

    CompletableFuture<Integer> count();

    CompletableFuture<Boolean> healthCheck();
}
