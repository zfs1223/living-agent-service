package com.livingagent.gateway.websocket;

import com.livingagent.core.database.entity.SessionContextEntity;
import com.livingagent.core.database.repository.SessionContextRepository;
import com.livingagent.core.session.ConnectionContext;
import com.livingagent.core.session.EventQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R2: 以 DB 为主存储的 ConnectionRegistry 实现。
 * 适合多节点部署：所有节点共享 PostgreSQL 中的 session_contexts 表，
 * 任一节点写入后，其他节点可立即通过 DB 查询到最新连接状态。
 *
 * 仍保留内存缓存作为热路径加速（写入双写，读取先内存后DB）。
 * 在单节点部署下退化为与 InMemoryConnectionRegistry 等价行为。
 */
@Component
@ConditionalOnProperty(
    name = "living-agent.connection-registry.backend",
    havingValue = "jdbc"
)
public class JdbcConnectionRegistry implements PersistentConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(JdbcConnectionRegistry.class);
    private static final Duration ACTIVE_THRESHOLD = Duration.ofMinutes(30);

    private final SessionContextRepository repository;
    private final EventQueueService eventQueueService;

    /** 内存缓存（热路径加速，DB 为主） */
    private final Map<String, ConnectionContext> cache = new ConcurrentHashMap<>();

    public JdbcConnectionRegistry(SessionContextRepository repository,
                                   EventQueueService eventQueueService) {
        this.repository = repository;
        this.eventQueueService = eventQueueService;
    }

    @Override
    @Transactional
    public void register(String sessionId, String userId, String tenantId, ConnectionContext context) {
        cache.put(sessionId, context);
        saveToDb(context);
    }

    @Override
    @Transactional
    public void unregister(String sessionId) {
        cache.remove(sessionId);
        try {
            repository.deleteById(sessionId);
        } catch (Exception e) {
            log.warn("Failed to delete session {} from DB: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public Optional<ConnectionContext> getContext(String sessionId) {
        ConnectionContext cached = cache.get(sessionId);
        if (cached != null) return Optional.of(cached);
        // 从 DB 加载并回填缓存
        return repository.findById(sessionId).map(entity -> {
            ConnectionContext ctx = toContext(entity);
            cache.putIfAbsent(sessionId, ctx);
            return ctx;
        });
    }

    @Override
    public Optional<String> getSessionIdByTaskKey(String taskKey) {
        return findByReverseIndex(taskKey, "taskKey",
            repository::findFirstByTaskKeyOrderByLastActivityDesc);
    }

    @Override
    public Optional<String> getSessionIdByExecutionId(String executionId) {
        return findByReverseIndex(executionId, "executionId",
            repository::findFirstByExecutionIdOrderByLastActivityDesc);
    }

    @Override
    public Optional<String> getSessionIdByProjectKey(String projectKey) {
        return findByReverseIndex(projectKey, "projectKey",
            repository::findFirstByProjectKeyOrderByLastActivityDesc);
    }

    @Override
    public Optional<String> getSessionIdByConversationId(String conversationId) {
        return findByReverseIndex(conversationId, "conversationId",
            repository::findFirstByConversationIdOrderByLastActivityDesc);
    }

    @Override
    public List<String> getSessionIdsByUserId(String userId) {
        return repository.findByUserId(userId).stream()
            .filter(this::isSessionActive)
            .map(SessionContextEntity::getSessionId)
            .toList();
    }

    @Override
    public List<String> getSessionIdsByTenantId(String tenantId) {
        return repository.findByTenantIdOrderByLastActivityDesc(tenantId).stream()
            .filter(this::isSessionActive)
            .map(SessionContextEntity::getSessionId)
            .toList();
    }

    @Override
    @Transactional
    public void bindTask(String sessionId, String taskKey) {
        updateField(sessionId, entity -> entity.setTaskKey(taskKey));
    }

    @Override
    @Transactional
    public void bindExecution(String sessionId, String executionId) {
        updateField(sessionId, entity -> entity.setExecutionId(executionId));
    }

    @Override
    @Transactional
    public void bindProject(String sessionId, String projectKey) {
        updateField(sessionId, entity -> entity.setProjectKey(projectKey));
    }

    @Override
    @Transactional
    public void bindConversation(String sessionId, String conversationId) {
        updateField(sessionId, entity -> entity.setConversationId(conversationId));
    }

    @Override
    @Transactional
    public void unbindTask(String sessionId) { bindTask(sessionId, null); }

    @Override
    @Transactional
    public void unbindExecution(String sessionId) { bindExecution(sessionId, null); }

    @Override
    @Transactional
    public void unbindProject(String sessionId) { bindProject(sessionId, null); }

    @Override
    @Transactional
    public void unbindConversation(String sessionId) { bindConversation(sessionId, null); }

    @Override
    @Transactional
    public void updateLastActivity(String sessionId) {
        updateField(sessionId, entity -> entity.setLastActivity(Instant.now()));
    }

    @Override
    public Optional<Instant> getLastActivity(String sessionId) {
        return getContext(sessionId).map(ConnectionContext::lastActivity);
    }

    @Override
    public int getActiveConnectionCount() {
        Instant threshold = Instant.now().minus(ACTIVE_THRESHOLD);
        try {
            return (int) repository.findByLastActivityAfterOrderByLastActivityDesc(threshold).size();
        } catch (Exception e) {
            return cache.size();
        }
    }

    @Override
    public int getActiveConnectionCountByTenant(String tenantId) {
        return getSessionIdsByTenantId(tenantId).size();
    }

    @Override
    public List<String> getAllSessionIds() {
        Instant threshold = Instant.now().minus(ACTIVE_THRESHOLD);
        return repository.findByLastActivityAfterOrderByLastActivityDesc(threshold).stream()
            .map(SessionContextEntity::getSessionId)
            .toList();
    }

    @Override
    @Transactional
    public void cleanupStaleConnections(long maxIdleMs) {
        Instant threshold = Instant.now().minusMillis(maxIdleMs);
        repository.deleteByLastActivityBefore(threshold);
        cache.entrySet().removeIf(e -> e.getValue().lastActivity().isBefore(threshold));
    }

    // === PersistentConnectionRegistry ===

    @Override
    public List<EventQueueService.PendingEvent> getPendingEvents(String sessionId) {
        return eventQueueService != null ? eventQueueService.getPendingEvents(sessionId) : List.of();
    }

    @Override
    public List<EventQueueService.PendingEvent> getPendingEventsAfter(String sessionId, long afterTimestamp) {
        return eventQueueService != null ? eventQueueService.getPendingEventsAfter(sessionId, afterTimestamp) : List.of();
    }

    @Override
    public void markEventSent(String sessionId, String eventId) {
        if (eventQueueService != null) eventQueueService.markEventSent(sessionId, eventId);
    }

    @Override
    public void clearSentEvents(String sessionId) {
        if (eventQueueService != null) eventQueueService.clearSentEvents(sessionId);
    }

    @Override
    public Optional<Long> getLatestEventTimestamp(String sessionId) {
        return eventQueueService != null ? eventQueueService.getLatestEventTimestamp(sessionId) : Optional.empty();
    }

    // ========== 内部方法 ==========

    @FunctionalInterface
    private interface ReverseLookup {
        Optional<SessionContextEntity> lookup(String key);
    }

    private Optional<String> findByReverseIndex(String key, String fieldName, ReverseLookup lookup) {
        if (key == null) return Optional.empty();
        // 先查缓存
        Optional<String> cached = cache.values().stream()
            .filter(ctx -> key.equals(getField(ctx, fieldName)))
            .filter(ctx -> isContextActive(ctx))
            .map(ConnectionContext::sessionId)
            .findFirst();
        if (cached.isPresent()) return cached;
        // 再查 DB
        return lookup.lookup(key)
            .filter(this::isSessionActive)
            .map(entity -> {
                ConnectionContext ctx = toContext(entity);
                cache.putIfAbsent(entity.getSessionId(), ctx);
                return entity.getSessionId();
            });
    }

    private String getField(ConnectionContext ctx, String fieldName) {
        return switch (fieldName) {
            case "taskKey" -> ctx.taskKey();
            case "executionId" -> ctx.executionId();
            case "projectKey" -> ctx.projectKey();
            case "conversationId" -> ctx.conversationId();
            default -> null;
        };
    }

    private boolean isSessionActive(SessionContextEntity entity) {
        return entity != null && entity.getLastActivity() != null
            && entity.getLastActivity().isAfter(Instant.now().minus(ACTIVE_THRESHOLD));
    }

    private boolean isContextActive(ConnectionContext ctx) {
        return ctx != null && ctx.lastActivity() != null
            && ctx.lastActivity().isAfter(Instant.now().minus(ACTIVE_THRESHOLD));
    }

    private void updateField(String sessionId, java.util.function.Consumer<SessionContextEntity> updater) {
        repository.findById(sessionId).ifPresent(entity -> {
            updater.accept(entity);
            entity.setLastActivity(Instant.now());
            repository.save(entity);
            // 同步缓存
            ConnectionContext ctx = toContext(entity);
            cache.put(sessionId, ctx);
        });
    }

    private void saveToDb(ConnectionContext context) {
        try {
            SessionContextEntity entity = new SessionContextEntity();
            entity.setSessionId(context.sessionId());
            entity.setUserId(context.userId());
            entity.setTenantId(context.tenantId());
            entity.setDepartmentCode(context.departmentCode());
            entity.setTaskKey(context.taskKey());
            entity.setExecutionId(context.executionId());
            entity.setProjectId(context.projectId());
            entity.setProjectKey(context.projectKey());
            entity.setConversationId(context.conversationId());
            entity.setConnectedAt(context.connectedAt());
            entity.setLastActivity(context.lastActivity() != null ? context.lastActivity() : Instant.now());
            entity.setAttributesJson(serializeAttributes(context.attributes()));
            repository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to save session {} to DB: {}", context.sessionId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ConnectionContext toContext(SessionContextEntity entity) {
        Map<String, Object> attributes = Map.of();
        if (entity.getAttributesJson() != null) {
            try {
                attributes = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(entity.getAttributesJson(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize session attributes: {}", e.getMessage());
            }
        }
        return new ConnectionContext(
            entity.getSessionId(), entity.getUserId(), entity.getTenantId(),
            entity.getDepartmentCode(), entity.getTaskKey(), entity.getExecutionId(),
            entity.getProjectId(), entity.getProjectKey(), entity.getConversationId(),
            entity.getConnectedAt(), entity.getLastActivity(), attributes
        );
    }

    private String serializeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) return "{}";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(attributes);
        } catch (Exception e) {
            return "{}";
        }
    }
}
