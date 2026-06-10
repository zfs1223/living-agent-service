package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.DepartmentConversationEntity;
import com.livingagent.core.database.repository.DepartmentConversationRepository;
import com.livingagent.core.work.WorkItemContext;
import com.livingagent.core.work.WorkItemKeyGenerator;
import com.livingagent.core.runtime.DataNamespaceService;
import com.livingagent.core.security.AuthContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkItemContextService {

    private final WorkItemKeyGenerator keyGenerator;
    private final DataNamespaceService namespaceService;
    private final DepartmentConversationRepository conversationRepository;

    public WorkItemContextService(DepartmentConversationRepository conversationRepository) {
        this.keyGenerator = new WorkItemKeyGenerator();
        this.namespaceService = new DataNamespaceService();
        this.conversationRepository = conversationRepository;
    }

    public WorkItemContext fromAuthContext(AuthContext ctx) {
        if (ctx == null) {
            return WorkItemContext.empty();
        }
        return WorkItemContext.builder()
            .tenantId(ctx.getTenantId())
            .ownerUserId(ctx.getEmployeeId())
            .departmentCode(ctx.getDepartment())
            .build();
    }

    public WorkItemContext fromAuthContextWithTask(AuthContext ctx, String taskType) {
        WorkItemContext base = fromAuthContext(ctx);
        String taskKey = keyGenerator.generateTaskKey(base.tenantId(), base.ownerUserId(), taskType);
        String dataNamespace = namespaceService.getTaskNamespace(base.tenantId(), taskKey);

        return base.withTaskKey(taskKey).withDataNamespace(dataNamespace);
    }

    public WorkItemContext fromAuthContextWithProject(AuthContext ctx, String projectName) {
        WorkItemContext base = fromAuthContext(ctx);
        String projectKey = keyGenerator.generateProjectKey(base.tenantId(), base.ownerUserId(), projectName);
        String dataNamespace = namespaceService.getProjectNamespace(base.tenantId(), projectKey);

        return base.withProjectKey(projectKey).withDataNamespace(dataNamespace);
    }

    public WorkItemContext fromWebSocketSession(String sessionId, Map<String, Object> sessionAttributes) {
        String tenantId = getStringAttribute(sessionAttributes, "tenantId");
        String userId = getStringAttribute(sessionAttributes, "userId");
        String departmentCode = getStringAttribute(sessionAttributes, "departmentCode");
        String taskKey = getStringAttribute(sessionAttributes, "taskKey");
        String executionId = getStringAttribute(sessionAttributes, "executionId");
        String projectId = getStringAttribute(sessionAttributes, "projectId");
        String projectKey = getStringAttribute(sessionAttributes, "projectKey");
        String conversationId = getStringAttribute(sessionAttributes, "conversationId");

        WorkItemContext.Builder builder = WorkItemContext.builder()
            .tenantId(tenantId)
            .ownerUserId(userId)
            .departmentCode(departmentCode)
            .sourceSessionId(sessionId)
            .sourceConversationId(conversationId);

        if (taskKey != null) builder.taskKey(taskKey);
        if (executionId != null) builder.executionId(executionId);
        if (projectId != null) builder.projectId(projectId);
        if (projectKey != null) builder.projectKey(projectKey);

        WorkItemContext context = builder.build();

        if (context.taskKey() != null && context.executionId() != null) {
            String dataNamespace = namespaceService.getConversationNamespace(
                tenantId, userId, taskKey, executionId);
            return context.withDataNamespace(dataNamespace);
        }

        return context;
    }

    public WorkItemContext createForNewExecution(WorkItemContext existing, String taskType) {
        String taskKey = existing.taskKey();
        if (taskKey == null) {
            taskKey = keyGenerator.generateTaskKey(existing.tenantId(), existing.ownerUserId(), taskType);
        }

        String executionId = keyGenerator.generateExecutionId(taskKey);
        String dataNamespace = namespaceService.getConversationNamespace(
            existing.tenantId(), existing.ownerUserId(), taskKey, executionId);

        return existing.withTaskKey(taskKey)
            .withExecutionId(executionId)
            .withDataNamespace(dataNamespace);
    }

    public WorkItemContext createForNewProject(WorkItemContext existing, String projectName) {
        String projectKey = keyGenerator.generateProjectKey(existing.tenantId(), existing.ownerUserId(), projectName);
        String dataNamespace = namespaceService.getProjectNamespace(existing.tenantId(), projectKey);

        return existing.withProjectKey(projectKey).withDataNamespace(dataNamespace);
    }

    public WorkItemContext buildFromConversationId(String conversationId) {
        if (conversationId == null) return WorkItemContext.empty();
        try {
            Optional<DepartmentConversationEntity> convOpt = conversationRepository.findByConversationId(conversationId);
            if (convOpt.isEmpty()) return WorkItemContext.empty();
            DepartmentConversationEntity conv = convOpt.get();

            WorkItemContext.Builder builder = WorkItemContext.builder()
                .tenantId(conv.getTenantId())
                .ownerUserId(conv.getOwnerUserId())
                .departmentCode(conv.getDepartmentCode())
                .sourceConversationId(conversationId);

            if (conv.getActiveTaskKey() != null) builder.taskKey(conv.getActiveTaskKey());
            if (conv.getActiveExecutionId() != null) builder.executionId(conv.getActiveExecutionId());

            WorkItemContext context = builder.build();

            if (context.taskKey() != null && context.executionId() != null) {
                String dataNamespace = namespaceService.getConversationNamespace(
                    conv.getTenantId(), conv.getOwnerUserId(),
                    conv.getActiveTaskKey(), conv.getActiveExecutionId());
                return context.withDataNamespace(dataNamespace);
            }
            return context;
        } catch (Exception e) {
            return WorkItemContext.empty();
        }
    }

    public WorkItemKeyGenerator getKeyGenerator() {
        return keyGenerator;
    }

    public DataNamespaceService getNamespaceService() {
        return namespaceService;
    }

    private String getStringAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value != null ? value.toString() : null;
    }

    public Map<String, Object> toWebSocketAttributes(WorkItemContext context) {
        Map<String, Object> attributes = new HashMap<>();
        if (context.tenantId() != null) attributes.put("tenantId", context.tenantId());
        if (context.ownerUserId() != null) attributes.put("userId", context.ownerUserId());
        if (context.departmentCode() != null) attributes.put("departmentCode", context.departmentCode());
        if (context.taskKey() != null) attributes.put("taskKey", context.taskKey());
        if (context.executionId() != null) attributes.put("executionId", context.executionId());
        if (context.projectId() != null) attributes.put("projectId", context.projectId());
        if (context.projectKey() != null) attributes.put("projectKey", context.projectKey());
        if (context.sourceConversationId() != null) attributes.put("conversationId", context.sourceConversationId());
        return attributes;
    }
}
