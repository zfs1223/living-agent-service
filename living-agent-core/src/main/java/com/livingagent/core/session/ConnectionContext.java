package com.livingagent.core.session;

import java.time.Instant;
import java.util.Map;

/**
 * WebSocket 连接上下文，记录会话关联的用户/租户/任务/项目等信息。
 * 原位于 gateway/ConnectionRegistry 内部，现下沉到 core 以避免 core 反向依赖 gateway。
 */
public record ConnectionContext(
    String sessionId,
    String userId,
    String tenantId,
    String departmentCode,
    String taskKey,
    String executionId,
    String projectId,
    String projectKey,
    String conversationId,
    Instant connectedAt,
    Instant lastActivity,
    Map<String, Object> attributes
) {
    public static ConnectionContext empty(String sessionId) {
        return new ConnectionContext(
            sessionId, null, null, null, null, null, null, null, null,
            Instant.now(), Instant.now(), Map.of()
        );
    }

    public ConnectionContext withTaskKey(String taskKey) {
        return new ConnectionContext(
            sessionId, userId, tenantId, departmentCode, taskKey, executionId,
            projectId, projectKey, conversationId, connectedAt, Instant.now(), attributes
        );
    }

    public ConnectionContext withExecutionId(String executionId) {
        return new ConnectionContext(
            sessionId, userId, tenantId, departmentCode, taskKey, executionId,
            projectId, projectKey, conversationId, connectedAt, Instant.now(), attributes
        );
    }

    public ConnectionContext withProjectKey(String projectKey) {
        return new ConnectionContext(
            sessionId, userId, tenantId, departmentCode, taskKey, executionId,
            projectId, projectKey, conversationId, connectedAt, Instant.now(), attributes
        );
    }

    public ConnectionContext withProjectId(String projectId) {
        return new ConnectionContext(
            sessionId, userId, tenantId, departmentCode, taskKey, executionId,
            projectId, projectKey, conversationId, connectedAt, Instant.now(), attributes
        );
    }

    public ConnectionContext withConversationId(String conversationId) {
        return new ConnectionContext(
            sessionId, userId, tenantId, departmentCode, taskKey, executionId,
            projectId, projectKey, conversationId, connectedAt, Instant.now(), attributes
        );
    }
}
