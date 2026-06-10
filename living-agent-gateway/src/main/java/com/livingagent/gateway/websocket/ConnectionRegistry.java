package com.livingagent.gateway.websocket;

import com.livingagent.core.session.ConnectionContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConnectionRegistry {

    void register(String sessionId, String userId, String tenantId, ConnectionContext context);

    void unregister(String sessionId);

    Optional<ConnectionContext> getContext(String sessionId);

    Optional<String> getSessionIdByTaskKey(String taskKey);

    Optional<String> getSessionIdByExecutionId(String executionId);

    Optional<String> getSessionIdByProjectKey(String projectKey);

    List<String> getSessionIdsByUserId(String userId);

    List<String> getSessionIdsByTenantId(String tenantId);

    void bindTask(String sessionId, String taskKey);

    void bindExecution(String sessionId, String executionId);

    void bindProject(String sessionId, String projectKey);

    void bindConversation(String sessionId, String conversationId);

    void unbindTask(String sessionId);

    void unbindExecution(String sessionId);

    void unbindProject(String sessionId);

    void unbindConversation(String sessionId);

    Optional<String> getSessionIdByConversationId(String conversationId);

    void updateLastActivity(String sessionId);

    Optional<Instant> getLastActivity(String sessionId);

    int getActiveConnectionCount();

    int getActiveConnectionCountByTenant(String tenantId);

    void cleanupStaleConnections(long maxIdleMs);
}
