package com.livingagent.core.work;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record WorkItemContext(
    String tenantId,
    String ownerUserId,
    String departmentCode,
    String projectId,
    String projectKey,
    String taskId,
    String taskKey,
    String executionId,
    String sourceConversationId,
    String sourceSessionId,
    String dataNamespace,
    String documentNamespace,
    Instant createdAt,
    Map<String, Object> metadata
) {
    public static WorkItemContext empty() {
        return new WorkItemContext(
            null, null, null, null, null, null, null, null, null, null, null, null, Instant.now(), new HashMap<>()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public WorkItemContext withTaskId(String taskId) {
        return new WorkItemContext(
            tenantId, ownerUserId, departmentCode, projectId, projectKey,
            taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
            dataNamespace, documentNamespace, createdAt, metadata
        );
    }

    public WorkItemContext withTaskKey(String taskKey) {
        return new WorkItemContext(
            tenantId, ownerUserId, departmentCode, projectId, projectKey,
            taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
            dataNamespace, documentNamespace, createdAt, metadata
        );
    }

    public WorkItemContext withExecutionId(String executionId) {
        return new WorkItemContext(
            tenantId, ownerUserId, departmentCode, projectId, projectKey,
            taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
            dataNamespace, documentNamespace, createdAt, metadata
        );
    }

    public WorkItemContext withProjectId(String projectId) {
        return new WorkItemContext(
            tenantId, ownerUserId, departmentCode, projectId, projectKey,
            taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
            dataNamespace, documentNamespace, createdAt, metadata
        );
    }

    public WorkItemContext withProjectKey(String projectKey) {
        return new WorkItemContext(
            tenantId, ownerUserId, departmentCode, projectId, projectKey,
            taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
            dataNamespace, documentNamespace, createdAt, metadata
        );
    }

    public WorkItemContext withDataNamespace(String dataNamespace) {
        return new WorkItemContext(
            tenantId, ownerUserId, departmentCode, projectId, projectKey,
            taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
            dataNamespace, documentNamespace, createdAt, metadata
        );
    }

    public WorkItemContext withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);
        return new WorkItemContext(
            tenantId, ownerUserId, departmentCode, projectId, projectKey,
            taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
            dataNamespace, documentNamespace, createdAt, newMetadata
        );
    }

    public Optional<String> getProjectPath() {
        if (tenantId == null || projectId == null) return Optional.empty();
        return Optional.of(String.format("data/projects/%s/%s", tenantId, projectId));
    }

    public Optional<String> getConversationPath() {
        if (tenantId == null || ownerUserId == null || taskKey == null || executionId == null) {
            return Optional.empty();
        }
        return Optional.of(String.format("data/conversations/%s/%s/%s/%s", tenantId, ownerUserId, taskKey, executionId));
    }

    public Optional<String> getTaskPath() {
        if (tenantId == null || taskKey == null) return Optional.empty();
        return Optional.of(String.format("data/tasks/%s/%s", tenantId, taskKey));
    }

    public static class Builder {
        private String tenantId;
        private String ownerUserId;
        private String departmentCode;
        private String projectId;
        private String projectKey;
        private String taskId;
        private String taskKey;
        private String executionId;
        private String sourceConversationId;
        private String sourceSessionId;
        private String dataNamespace;
        private String documentNamespace;
        private Instant createdAt = Instant.now();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder ownerUserId(String ownerUserId) {
            this.ownerUserId = ownerUserId;
            return this;
        }

        public Builder departmentCode(String departmentCode) {
            this.departmentCode = departmentCode;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder projectKey(String projectKey) {
            this.projectKey = projectKey;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder taskKey(String taskKey) {
            this.taskKey = taskKey;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder sourceConversationId(String sourceConversationId) {
            this.sourceConversationId = sourceConversationId;
            return this;
        }

        public Builder sourceSessionId(String sourceSessionId) {
            this.sourceSessionId = sourceSessionId;
            return this;
        }

        public Builder dataNamespace(String dataNamespace) {
            this.dataNamespace = dataNamespace;
            return this;
        }

        public Builder documentNamespace(String documentNamespace) {
            this.documentNamespace = documentNamespace;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public WorkItemContext build() {
            return new WorkItemContext(
                tenantId, ownerUserId, departmentCode, projectId, projectKey,
                taskId, taskKey, executionId, sourceConversationId, sourceSessionId,
                dataNamespace, documentNamespace, createdAt, metadata
            );
        }
    }
}
