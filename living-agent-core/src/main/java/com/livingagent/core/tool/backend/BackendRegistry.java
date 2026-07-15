package com.livingagent.core.tool.backend;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部工具后端注册表（64-B-1）
 * 管理所有 ExternalToolBackend 实现，提供统一查询和健康检查。
 */
public class BackendRegistry {

    private final ConcurrentHashMap<String, ExternalToolBackend> backends = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedHealth> healthCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000;

    public void register(ExternalToolBackend backend) {
        backends.put(backend.toolName(), backend);
    }

    public void unregister(String toolName) {
        backends.remove(toolName);
        healthCache.remove(toolName);
    }

    public Optional<ExternalToolBackend> getBackend(String toolName) {
        return Optional.ofNullable(backends.get(toolName));
    }

    public List<ExternalToolBackend> getAll() {
        return List.copyOf(backends.values());
    }

    /**
     * 带缓存的健康检查，30秒内不重复检查
     */
    public ExternalToolBackend.HealthStatus healthCheck(String toolName) {
        CachedHealth cached = healthCache.get(toolName);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.status;
        }
        ExternalToolBackend backend = backends.get(toolName);
        if (backend == null) {
            return ExternalToolBackend.HealthStatus.unreachable();
        }
        ExternalToolBackend.HealthStatus status = backend.healthCheck();
        healthCache.put(toolName, new CachedHealth(status, System.currentTimeMillis()));
        return status;
    }

    public boolean exists(String toolName) {
        return backends.containsKey(toolName);
    }

    public int count() {
        return backends.size();
    }

    private record CachedHealth(ExternalToolBackend.HealthStatus status, long timestamp) {}
}
