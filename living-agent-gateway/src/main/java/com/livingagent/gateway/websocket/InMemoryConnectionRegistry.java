package com.livingagent.gateway.websocket;

import com.livingagent.core.session.ConnectionContext;
import com.livingagent.core.session.EventQueueService;
import com.livingagent.core.session.SessionPersistenceService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryConnectionRegistry implements ConnectionRegistry, PersistentConnectionRegistry {

    private final Map<String, ConnectionContext> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> taskKeyToSession = new ConcurrentHashMap<>();
    private final Map<String, String> executionIdToSession = new ConcurrentHashMap<>();
    private final Map<String, String> projectKeyToSession = new ConcurrentHashMap<>();
    private final Map<String, String> conversationIdToSession = new ConcurrentHashMap<>();
    
    private final SessionPersistenceService persistenceService;
    private final EventQueueService eventQueueService;

    public InMemoryConnectionRegistry(SessionPersistenceService persistenceService, 
                                     EventQueueService eventQueueService) {
        this.persistenceService = persistenceService;
        this.eventQueueService = eventQueueService;
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
        return Optional.ofNullable(taskKeyToSession.get(taskKey));
    }

    @Override
    public Optional<String> getSessionIdByExecutionId(String executionId) {
        return Optional.ofNullable(executionIdToSession.get(executionId));
    }

    @Override
    public Optional<String> getSessionIdByProjectKey(String projectKey) {
        return Optional.ofNullable(projectKeyToSession.get(projectKey));
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
        return Optional.ofNullable(conversationIdToSession.get(conversationId));
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
        return Optional.empty();
    }

    public Optional<ConnectionContext> findByExecutionId(String executionId) {
        String sessionId = executionIdToSession.get(executionId);
        if (sessionId != null) return getContext(sessionId);
        return Optional.empty();
    }

    public Optional<ConnectionContext> findByProjectKey(String projectKey) {
        String sessionId = projectKeyToSession.get(projectKey);
        if (sessionId != null) return getContext(sessionId);
        return Optional.empty();
    }
}
