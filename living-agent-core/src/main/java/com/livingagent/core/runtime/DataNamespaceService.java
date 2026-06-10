package com.livingagent.core.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class DataNamespaceService {

    private final String baseDataDir;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DataNamespaceService() {
        this("data");
    }

    public DataNamespaceService(String baseDataDir) {
        this.baseDataDir = baseDataDir;
    }

    public String getProjectNamespace(String tenantId, String projectId) {
        return String.format("%s/projects/%s/%s", baseDataDir, safe(tenantId), safe(projectId));
    }

    public String getProjectEventsPath(String tenantId, String projectId) {
        return String.format("%s/projects/%s/%s/events.jsonl", baseDataDir, safe(tenantId), safe(projectId));
    }

    public String getProjectSummaryPath(String tenantId, String projectId) {
        return String.format("%s/projects/%s/%s/summary.json", baseDataDir, safe(tenantId), safe(projectId));
    }

    public String getProjectPhasesPath(String tenantId, String projectId) {
        return String.format("%s/projects/%s/%s/phases", baseDataDir, safe(tenantId), safe(projectId));
    }

    public String getProjectTasksPath(String tenantId, String projectId) {
        return String.format("%s/projects/%s/%s/tasks", baseDataDir, safe(tenantId), safe(projectId));
    }

    public String getProjectArtifactsPath(String tenantId, String projectId) {
        return String.format("%s/projects/%s/%s/artifacts", baseDataDir, safe(tenantId), safe(projectId));
    }

    public String getTaskNamespace(String tenantId, String taskKey) {
        return String.format("%s/tasks/%s/%s", baseDataDir, safe(tenantId), safe(taskKey));
    }

    public String getTaskEventsPath(String tenantId, String taskKey) {
        return String.format("%s/tasks/%s/%s/events.jsonl", baseDataDir, safe(tenantId), safe(taskKey));
    }

    public String getTaskSummaryPath(String tenantId, String taskKey) {
        return String.format("%s/tasks/%s/%s/summary.json", baseDataDir, safe(tenantId), safe(taskKey));
    }

    public String getConversationNamespace(String tenantId, String userId, String taskKey, String executionId) {
        return String.format("%s/conversations/%s/%s/%s/%s", 
            baseDataDir, safe(tenantId), safe(userId), safe(taskKey), safe(executionId));
    }

    public String getConversationSessionPath(String tenantId, String userId, String taskKey, String executionId) {
        return String.format("%s/conversations/%s/%s/%s/%s/session.json", 
            baseDataDir, safe(tenantId), safe(userId), safe(taskKey), safe(executionId));
    }

    public String getConversationEventsPath(String tenantId, String userId, String taskKey, String executionId) {
        return String.format("%s/conversations/%s/%s/%s/%s/events.jsonl", 
            baseDataDir, safe(tenantId), safe(userId), safe(taskKey), safe(executionId));
    }

    public String getConversationReceiptsPath(String tenantId, String userId, String taskKey, String executionId) {
        return String.format("%s/conversations/%s/%s/%s/%s/receipts", 
            baseDataDir, safe(tenantId), safe(userId), safe(taskKey), safe(executionId));
    }

    public String getConversationArtifactsPath(String tenantId, String userId, String taskKey, String executionId) {
        return String.format("%s/conversations/%s/%s/%s/%s/artifacts", 
            baseDataDir, safe(tenantId), safe(userId), safe(taskKey), safe(executionId));
    }

    public String getConversationSummaryPath(String tenantId, String userId, String taskKey, String executionId) {
        return String.format("%s/conversations/%s/%s/%s/%s/summary.json", 
            baseDataDir, safe(tenantId), safe(userId), safe(taskKey), safe(executionId));
    }

    public String getConversationIdNamespace(String tenantId, String conversationId) {
        return String.format("%s/conversations-by-id/%s/%s", baseDataDir, safe(tenantId), safe(conversationId));
    }

    public String getConversationIdEventsPath(String tenantId, String conversationId) {
        return String.format("%s/conversations-by-id/%s/%s/events.jsonl", baseDataDir, safe(tenantId), safe(conversationId));
    }

    public String getConversationIdSummaryPath(String tenantId, String conversationId) {
        return String.format("%s/conversations-by-id/%s/%s/summary.json", baseDataDir, safe(tenantId), safe(conversationId));
    }

    public String getConversationIdArtifactsPath(String tenantId, String conversationId) {
        return String.format("%s/conversations-by-id/%s/%s/artifacts", baseDataDir, safe(tenantId), safe(conversationId));
    }

    public String getUserIndexPath(String userId) {
        return String.format("%s/indexes/by-user/%s.json", baseDataDir, safe(userId));
    }

    public String getProjectIndexPath(String projectId) {
        return String.format("%s/indexes/by-project/%s.json", baseDataDir, safe(projectId));
    }

    public String getTaskIndexPath(String taskKey) {
        return String.format("%s/indexes/by-task/%s.json", baseDataDir, safe(taskKey));
    }

    public String getExecutionIndexPath(String executionId) {
        return String.format("%s/indexes/by-execution/%s.json", baseDataDir, safe(executionId));
    }

    public Path toPath(String namespace) {
        return Paths.get(namespace);
    }

    public String getArtifactsPath(String tenantId, String executionId) {
        return String.format("%s/artifacts/%s/%s", baseDataDir, safe(tenantId), safe(executionId));
    }

    public String getReceiptsPath(String tenantId, String executionId) {
        return String.format("%s/receipts/%s/%s", baseDataDir, safe(tenantId), safe(executionId));
    }

    private String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "_";
        }
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
