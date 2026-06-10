package com.livingagent.gateway.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.livingagent.core.model.ModelSession;
import com.livingagent.core.security.AccessLevel;

public class SessionContext {

    private final String sessionId;
    private final AccessLevel accessLevel;
    private final String departmentId;
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final List<Map<String, String>> history = Collections.synchronizedList(new ArrayList<>());

    private volatile String userId;
    private volatile String coordinatorSessionId;
    private volatile ModelSession modelSession;
    private volatile CompletableFuture<Void> sessionReadyFuture;
    private volatile String taskKey;
    private volatile String executionId;

    public SessionContext(String sessionId, AccessLevel accessLevel, String departmentId) {
        this.sessionId = sessionId;
        this.accessLevel = accessLevel != null ? accessLevel : AccessLevel.CHAT_ONLY;
        this.departmentId = departmentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean hasUserIdentity() {
        return userId != null && !userId.isBlank();
    }

    public String getCoordinatorSessionId() {
        return coordinatorSessionId;
    }

    public void setCoordinatorSessionId(String coordinatorSessionId) {
        this.coordinatorSessionId = coordinatorSessionId;
    }

    public ModelSession getModelSession() {
        return modelSession;
    }

    public void setModelSession(ModelSession modelSession) {
        this.modelSession = modelSession;
    }

    public CompletableFuture<Void> getSessionReadyFuture() {
        return sessionReadyFuture;
    }

    public void setSessionReadyFuture(CompletableFuture<Void> sessionReadyFuture) {
        this.sessionReadyFuture = sessionReadyFuture;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public int incrementMessageCount() {
        return messageCount.incrementAndGet();
    }

    public int getMessageCount() {
        return messageCount.get();
    }

    public void addHistory(String role, String content) {
        if (role == null || content == null) {
            return;
        }
        history.add(Map.of("role", role, "content", content));
    }

    public List<Map<String, String>> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }
}
