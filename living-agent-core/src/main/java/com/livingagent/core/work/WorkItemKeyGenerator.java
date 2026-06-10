package com.livingagent.core.work;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class WorkItemKeyGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String generateTaskKey(String tenantId, String userId, String taskType) {
        String timestamp = Instant.now().toString().replace(":", "").replace("-", "").replace(".", "");
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("task://%s/%s/%s/%s-%s", 
            safe(tenantId), safe(userId), safe(taskType), timestamp, shortUuid);
    }

    public String generateProjectKey(String tenantId, String userId, String projectName) {
        String timestamp = Instant.now().toString().replace(":", "").replace("-", "").replace(".", "").substring(0, 14);
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        String safeName = sanitizeName(projectName);
        return String.format("project://%s/%s/%s-%s", 
            safe(tenantId), safe(userId), safeName, timestamp);
    }

    public String generateExecutionId(String taskKey) {
        String timestamp = Instant.now().toString().replace(":", "").replace("-", "").replace(".", "");
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("exec://%s/%s-%s", safe(taskKey), timestamp, shortUuid);
    }

    public String generateDataNamespace(String tenantId, String userId, String taskKey, String executionId) {
        return String.format("data/conversations/%s/%s/%s/%s", 
            safe(tenantId), safe(userId), safe(taskKey), safe(executionId));
    }

    public String generateProjectDataNamespace(String tenantId, String projectId) {
        return String.format("data/projects/%s/%s", safe(tenantId), safe(projectId));
    }

    public String generateDocumentNamespace(String departmentCode, String category) {
        return String.format("documents/department/%s/%s", safe(departmentCode), safe(category));
    }

    public String extractTaskKeyFromExecutionId(String executionId) {
        if (executionId == null || !executionId.startsWith("exec://")) {
            return null;
        }
        String withoutPrefix = executionId.substring(7);
        int slashIndex = withoutPrefix.indexOf('/');
        if (slashIndex > 0) {
            return withoutPrefix.substring(0, slashIndex);
        }
        return null;
    }

    public String generateShortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public String generateDeterministicTaskKey(String tenantId, String userId, String taskType, String seed) {
        int hash = Math.abs((tenantId + userId + taskType + seed).hashCode());
        return String.format("task://%s/%s/%s/%08x", 
            safe(tenantId), safe(userId), safe(taskType), hash);
    }

    private String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "_";
        }
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private String sanitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed";
        }
        String sanitized = name.toLowerCase()
            .replaceAll("[^a-z0-9\\-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (sanitized.isEmpty()) {
            return "unnamed";
        }
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        return sanitized;
    }
}
