package com.livingagent.gateway.websocket;

import com.livingagent.core.database.entity.SessionContextEntity;
import com.livingagent.core.database.repository.SessionContextRepository;
import com.livingagent.core.session.ConnectionContext;
import com.livingagent.core.session.EventQueueService;
import com.livingagent.core.session.SessionPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接注册表（内存反向索引 + DB 持久化回退）。
 * B-0-2: 反向索引（taskKey/executionId/projectKey/conversationId -> sessionId）原本仅在内存中，
 * 服务重启后丢失。现保留内存作为热路径，并在内存未命中时从 session_contexts 表回退查询。
 * 通过 {@link SessionContextRepository} 查询 DB 中最新的 session，并按 lastActivity 过滤过期会话。
 */
@Component
public class InMemoryConnectionRegistry implements ConnectionRegistry, PersistentConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConnectionRegistry.class);
    /** 会话视为"活跃"的最大空闲时间，超过则视为已断开 */
    private static final Duration ACTIVE_THRESHOLD = Duration.ofMinutes(30);

    private final Map<String, ConnectionContext> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> taskKeyToSession = new ConcurrentHashMap<>();
    private final Map<String, String> executionIdToSession = new ConcurrentHashMap<>();
    private final Map<String, String> projectKeyToSession = new ConcurrentHashMap<>();
    private final Map<String, String> conversationIdToSession = new ConcurrentHashMap<>();

    private final SessionPersistenceService persistenceService;
    private final EventQueueService eventQueueService;
    private final SessionContextRepository sessionContextRepository;

    public InMemoryConnectionRegistry(SessionPersistenceService persistenceService,
                                     EventQueueService eventQueueService,
                                     SessionContextRepository sessionContextRepository) {
        this.persistenceService = persistenceService;
        this.eventQueueService = eventQueueService;
        this.sessionContextRepository = sessionContextRepository;
    }

    /**
     * B-0-2: 启动时从 DB 加载活跃 session 到内存，保证重启后反向索引可用。
     * 仅加载 lastActivity 在 ACTIVE_THRESHOLD 内的 session。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadActiveSessionsFromDb() {
        if (sessionContextRepository == null) return;
        try {
            Instant threshold = Instant.now().minus(ACTIVE_THRESHOLD);
            List<SessionContextEntity> activeSessions =
                sessionContextRepository.findByLastActivityAfterOrderByLastActivityDesc(threshold);
            for (SessionContextEntity entity : activeSessions) {
                restoreSessionFromDb(entity);
            }
            if (!activeSessions.isEmpty()) {
                log.info("B-0-2: Loaded {} active sessions from DB into memory on startup", activeSessions.size());
            }
        } catch (Exception e) {
            log.warn("B-0-2: Failed to load active sessions from DB on startup: {}", e.getMessage());
        }
    }

    @Override
    public void register(String sessionId, String userId, String tenantId, ConnectionContext context) {
        sessions.put(sessionId, context);
        if (persistenceService != null) {
            persistenceService.saveSession(sessionId, context);
        }
    }

    @Override
    public void unregister(String sessionId) {
        ConnectionContext context = sessions.remove(sessionId);
        if (context != null) {
            if (context.taskKey() != null) taskKeyToSession.remove(context.taskKey());
            if (context.executionId() != null) executionIdToSession.remove(context.executionId());
            if (context.projectKey() != null) projectKeyToSession.remove(context.projectKey());
            if (context.conversationId() != null) conversationIdToSession.remove(context.conversationId());
        }
    }

    @Override
    public Optional<ConnectionContext> getContext(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public Optional<String> getSessionIdByTaskKey(String taskKey) {
        String sessionId = taskKeyToSession.get(taskKey);
        if (sessionId != null && sessions.containsKey(sessionId)) {
            return Optional.of(sessionId);
        }
        // B-0-2: 内存未命中，从 DB 回退查询最新活跃会话
        return lookupActiveSessionByTaskKey(taskKey).map(sessionContextEntity -> {
            // 回填内存反向索引，避免下次再查 DB
            restoreSessionFromDb(sessionContextEntity);
            return sessionContextEntity.getSessionId();
        });
    }

    @Override
    public Optional<String> getSessionIdByExecutionId(String executionId) {
        String sessionId = executionIdToSession.get(executionId);
        if (sessionId != null && sessions.containsKey(sessionId)) {
            return Optional.of(sessionId);
        }
        // B-0-2: 内存未命中，从 DB 回退查询
        return lookupActiveSessionByExecutionId(executionId).map(sessionContextEntity -> {
            restoreSessionFromDb(sessionContextEntity);
            return sessionContextEntity.getSessionId();
        });
    }

    @Override
    public Optional<String> getSessionIdByProjectKey(String projectKey) {
        String sessionId = projectKeyToSession.get(projectKey);
        if (sessionId != null && sessions.containsKey(sessionId)) {
            return Optional.of(sessionId);
        }
        // B-0-2: 内存未命中，从 DB 回退查询
        return lookupActiveSessionByProjectKey(projectKey).map(sessionContextEntity -> {
            restoreSessionFromDb(sessionContextEntity);
            return sessionContextEntity.getSessionId();
        });
    }

    @Override
    public List<String> getSessionIdsByUserId(String userId) {
        return sessions.values().stream()
            .filter(ctx -> userId.equals(ctx.userId()))
            .map(ConnectionContext::sessionId)
            .toList();
    }

    @Override
    public List<String> getSessionIdsByTenantId(String tenantId) {
        return sessions.values().stream()
            .filter(ctx -> tenantId.equals(ctx.tenantId()))
            .map(ConnectionContext::sessionId)
            .toList();
    }

    @Override
    public void bindTask(String sessionId, String taskKey) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null) {
            if (context.taskKey() != null) taskKeyToSession.remove(context.taskKey());
            ConnectionContext updated = context.withTaskKey(taskKey);
            sessions.put(sessionId, updated);
            taskKeyToSession.put(taskKey, sessionId);
        }
    }

    @Override
    public void bindExecution(String sessionId, String executionId) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null) {
            if (context.executionId() != null) executionIdToSession.remove(context.executionId());
            ConnectionContext updated = context.withExecutionId(executionId);
            sessions.put(sessionId, updated);
            executionIdToSession.put(executionId, sessionId);
        }
    }

    @Override
    public void bindProject(String sessionId, String projectKey) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null) {
            if (context.projectKey() != null) projectKeyToSession.remove(context.projectKey());
            ConnectionContext updated = context.withProjectKey(projectKey);
            sessions.put(sessionId, updated);
            projectKeyToSession.put(projectKey, sessionId);
        }
    }

    @Override
    public void bindConversation(String sessionId, String conversationId) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null) {
            if (context.conversationId() != null) conversationIdToSession.remove(context.conversationId());
            ConnectionContext updated = context.withConversationId(conversationId);
            sessions.put(sessionId, updated);
            conversationIdToSession.put(conversationId, sessionId);
        }
    }

    @Override
    public void unbindTask(String sessionId) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null && context.taskKey() != null) {
            taskKeyToSession.remove(context.taskKey());
            sessions.put(sessionId, new ConnectionContext(
                sessionId, context.userId(), context.tenantId(), context.departmentCode(),
                null, context.executionId(), context.projectId(), context.projectKey(),
                context.conversationId(), context.connectedAt(), Instant.now(), context.attributes()
            ));
        }
    }

    @Override
    public void unbindExecution(String sessionId) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null && context.executionId() != null) {
            executionIdToSession.remove(context.executionId());
            sessions.put(sessionId, new ConnectionContext(
                sessionId, context.userId(), context.tenantId(), context.departmentCode(),
                context.taskKey(), null, context.projectId(), context.projectKey(),
                context.conversationId(), context.connectedAt(), Instant.now(), context.attributes()
            ));
        }
    }

    @Override
    public void unbindProject(String sessionId) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null && context.projectKey() != null) {
            projectKeyToSession.remove(context.projectKey());
            sessions.put(sessionId, context.withProjectKey(null));
        }
    }

    @Override
    public void unbindConversation(String sessionId) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null && context.conversationId() != null) {
            conversationIdToSession.remove(context.conversationId());
            sessions.put(sessionId, context.withConversationId(null));
        }
    }

    @Override
    public Optional<String> getSessionIdByConversationId(String conversationId) {
        String sessionId = conversationIdToSession.get(conversationId);
        if (sessionId != null && sessions.containsKey(sessionId)) {
            return Optional.of(sessionId);
        }
        // B-0-2: 内存未命中，从 DB 回退查询
        return lookupActiveSessionByConversationId(conversationId).map(sessionContextEntity -> {
            restoreSessionFromDb(sessionContextEntity);
            return sessionContextEntity.getSessionId();
        });
    }

    @Override
    public void updateLastActivity(String sessionId) {
        ConnectionContext context = sessions.get(sessionId);
        if (context != null) {
            sessions.put(sessionId, new ConnectionContext(
                sessionId, context.userId(), context.tenantId(), context.departmentCode(),
                context.taskKey(), context.executionId(), context.projectId(), context.projectKey(),
                context.conversationId(), context.connectedAt(), Instant.now(), context.attributes()
            ));
        }
    }

    @Override
    public Optional<Instant> getLastActivity(String sessionId) {
        return getContext(sessionId).map(ConnectionContext::lastActivity);
    }

    @Override
    public int getActiveConnectionCount() {
        return sessions.size();
    }

    @Override
    public int getActiveConnectionCountByTenant(String tenantId) {
        return (int) sessions.values().stream()
            .filter(ctx -> tenantId.equals(ctx.tenantId()))
            .count();
    }

    @Override
    public List<String> getAllSessionIds() {
        return new ArrayList<>(sessions.keySet());
    }

    @Override
    public void cleanupStaleConnections(long maxIdleMs) {
        Instant threshold = Instant.now().minusMillis(maxIdleMs);
        List<String> staleSessions = sessions.entrySet().stream()
            .filter(entry -> entry.getValue().lastActivity().isBefore(threshold))
            .map(Map.Entry::getKey)
            .toList();
        for (String sessionId : staleSessions) {
            unregister(sessionId);
        }
    }

    // === PersistentConnectionRegistry 实现 ===
    @Override
    public List<EventQueueService.PendingEvent> getPendingEvents(String sessionId) {
        if (eventQueueService != null) {
            return eventQueueService.getPendingEvents(sessionId);
        }
        return List.of();
    }

    @Override
    public void markEventSent(String sessionId, String eventId) {
        if (eventQueueService != null) {
            eventQueueService.markEventSent(sessionId, eventId);
        }
    }

    @Override
    public void clearSentEvents(String sessionId) {
        if (eventQueueService != null) {
            eventQueueService.clearSentEvents(sessionId);
        }
    }

    public Optional<ConnectionContext> findByTaskKey(String taskKey) {
        String sessionId = taskKeyToSession.get(taskKey);
        if (sessionId != null) return getContext(sessionId);
        // B-0-2: 内存未命中，从 DB 回退
        return lookupActiveSessionByTaskKey(taskKey).map(this::toContext)
            .map(ctx -> {
                sessions.put(ctx.sessionId(), ctx);
                taskKeyToSession.put(taskKey, ctx.sessionId());
                return ctx;
            });
    }

    public Optional<ConnectionContext> findByExecutionId(String executionId) {
        String sessionId = executionIdToSession.get(executionId);
        if (sessionId != null) return getContext(sessionId);
        return lookupActiveSessionByExecutionId(executionId).map(this::toContext)
            .map(ctx -> {
                sessions.put(ctx.sessionId(), ctx);
                executionIdToSession.put(executionId, ctx.sessionId());
                return ctx;
            });
    }

    public Optional<ConnectionContext> findByProjectKey(String projectKey) {
        String sessionId = projectKeyToSession.get(projectKey);
        if (sessionId != null) return getContext(sessionId);
        return lookupActiveSessionByProjectKey(projectKey).map(this::toContext)
            .map(ctx -> {
                sessions.put(ctx.sessionId(), ctx);
                projectKeyToSession.put(projectKey, ctx.sessionId());
                return ctx;
            });
    }

    // ========== B-0-2: DB 回退查询 ==========

    private Optional<SessionContextEntity> lookupActiveSessionByTaskKey(String taskKey) {
        if (taskKey == null || sessionContextRepository == null) return Optional.empty();
        try {
            return sessionContextRepository.findFirstByTaskKeyOrderByLastActivityDesc(taskKey)
                .filter(this::isSessionActive);
        } catch (Exception e) {
            log.warn("Failed to lookup session by taskKey={} from DB: {}", taskKey, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SessionContextEntity> lookupActiveSessionByExecutionId(String executionId) {
        if (executionId == null || sessionContextRepository == null) return Optional.empty();
        try {
            return sessionContextRepository.findFirstByExecutionIdOrderByLastActivityDesc(executionId)
                .filter(this::isSessionActive);
        } catch (Exception e) {
            log.warn("Failed to lookup session by executionId={} from DB: {}", executionId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SessionContextEntity> lookupActiveSessionByProjectKey(String projectKey) {
        if (projectKey == null || sessionContextRepository == null) return Optional.empty();
        try {
            return sessionContextRepository.findFirstByProjectKeyOrderByLastActivityDesc(projectKey)
                .filter(this::isSessionActive);
        } catch (Exception e) {
            log.warn("Failed to lookup session by projectKey={} from DB: {}", projectKey, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SessionContextEntity> lookupActiveSessionByConversationId(String conversationId) {
        if (conversationId == null || sessionContextRepository == null) return Optional.empty();
        try {
            return sessionContextRepository.findFirstByConversationIdOrderByLastActivityDesc(conversationId)
                .filter(this::isSessionActive);
        } catch (Exception e) {
            log.warn("Failed to lookup session by conversationId={} from DB: {}", conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isSessionActive(SessionContextEntity entity) {
        if (entity == null || entity.getLastActivity() == null) return false;
        return entity.getLastActivity().isAfter(Instant.now().minus(ACTIVE_THRESHOLD));
    }

    /**
     * 从 DB 记录回填内存反向索引（仅在内存未命中且 DB 找到时调用）。
     * 不会覆盖已有的内存映射。
     */
    private void restoreSessionFromDb(SessionContextEntity entity) {
        if (entity == null) return;
        String sessionId = entity.getSessionId();
        if (sessionId == null) return;
        // 仅在内存没有该 session 时回填
        if (!sessions.containsKey(sessionId)) {
            ConnectionContext ctx = toContext(entity);
            sessions.putIfAbsent(sessionId, ctx);
        }
        if (entity.getTaskKey() != null && !taskKeyToSession.containsKey(entity.getTaskKey())) {
            taskKeyToSession.putIfAbsent(entity.getTaskKey(), sessionId);
        }
        if (entity.getExecutionId() != null && !executionIdToSession.containsKey(entity.getExecutionId())) {
            executionIdToSession.putIfAbsent(entity.getExecutionId(), sessionId);
        }
        if (entity.getProjectKey() != null && !projectKeyToSession.containsKey(entity.getProjectKey())) {
            projectKeyToSession.putIfAbsent(entity.getProjectKey(), sessionId);
        }
        if (entity.getConversationId() != null && !conversationIdToSession.containsKey(entity.getConversationId())) {
            conversationIdToSession.putIfAbsent(entity.getConversationId(), sessionId);
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
}
