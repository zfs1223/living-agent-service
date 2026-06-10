package com.livingagent.gateway.security;

import com.livingagent.core.database.repository.TaskRepository;
import com.livingagent.core.database.repository.ProjectRepository;
import com.livingagent.core.database.repository.DepartmentConversationRepository;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.AccessLevel;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class WorkItemPermissionService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final DepartmentConversationRepository conversationRepository;

    public WorkItemPermissionService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            DepartmentConversationRepository conversationRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
    }

    public boolean canViewTask(String taskId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;

        return taskRepository.findById(taskId)
            .map(task -> {
                if (!isSameTenant(ctx, task.getTenantId())) return false;
                if (isOwner(ctx, task.getUserId())) return true;
                if (isOwner(ctx, task.getAssignedTo())) return true;
                if (isOwner(ctx, task.getReviewerId())) return true;
                return isDepartmentMember(ctx, task.getDepartmentCode());
            })
            .orElse(false);
    }

    public boolean canEditTask(String taskId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;

        return taskRepository.findById(taskId)
            .map(task -> {
                if (!isSameTenant(ctx, task.getTenantId())) return false;
                if (isOwner(ctx, task.getUserId())) return true;
                if (isOwner(ctx, task.getAssignedTo())) return true;
                return false;
            })
            .orElse(false);
    }

    public boolean canAssignTask(String taskId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;

        return taskRepository.findById(taskId)
            .map(task -> {
                if (!isSameTenant(ctx, task.getTenantId())) return false;
                if (isOwner(ctx, task.getUserId())) return true;
                return isDepartmentManager(ctx, task.getDepartmentCode());
            })
            .orElse(false);
    }

    public boolean canReviewTask(String taskId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;

        return taskRepository.findById(taskId)
            .map(task -> {
                if (!isSameTenant(ctx, task.getTenantId())) return false;
                if (isOwner(ctx, task.getReviewerId())) return true;
                return isDepartmentManager(ctx, task.getDepartmentCode());
            })
            .orElse(false);
    }

    public boolean canViewProject(String projectId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;

        return projectRepository.findById(projectId)
            .map(project -> {
                if (!isSameTenant(ctx, project.getTenantId())) return false;
                if (isOwner(ctx, project.getCreatorUserId())) return true;
                if (isOwner(ctx, project.getManagerId())) return true;
                return isDepartmentMember(ctx, project.getOwnerDepartment());
            })
            .orElse(false);
    }

    public boolean canEditProject(String projectId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;

        return projectRepository.findById(projectId)
            .map(project -> {
                if (!isSameTenant(ctx, project.getTenantId())) return false;
                if (isOwner(ctx, project.getCreatorUserId())) return true;
                if (isOwner(ctx, project.getManagerId())) return true;
                return false;
            })
            .orElse(false);
    }

    public boolean canManageProject(String projectId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;

        return projectRepository.findById(projectId)
            .map(project -> {
                if (!isSameTenant(ctx, project.getTenantId())) return false;
                if (isOwner(ctx, project.getManagerId())) return true;
                return isDepartmentManager(ctx, project.getOwnerDepartment());
            })
            .orElse(false);
    }

    public boolean canCreateTaskInProject(String projectId, AuthContext ctx) {
        return canViewProject(projectId, ctx);
    }

    public boolean canViewConversation(String conversationId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;
        if (conversationId == null) return false;

        return conversationRepository.findByConversationId(conversationId)
            .map(conv -> {
                if (!isSameTenant(ctx, conv.getTenantId())) return false;
                if (isOwner(ctx, conv.getOwnerUserId())) return true;
                return isDepartmentMember(ctx, conv.getDepartmentCode());
            })
            .orElse(false);
    }

    public boolean canEditConversation(String conversationId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;
        if (conversationId == null) return false;

        return conversationRepository.findByConversationId(conversationId)
            .map(conv -> {
                if (!isSameTenant(ctx, conv.getTenantId())) return false;
                if (isOwner(ctx, conv.getOwnerUserId())) return true;
                return isDepartmentManager(ctx, conv.getDepartmentCode());
            })
            .orElse(false);
    }

    public boolean canDeleteConversation(String conversationId, AuthContext ctx) {
        if (isAdmin(ctx)) return true;
        if (conversationId == null) return false;

        return conversationRepository.findByConversationId(conversationId)
            .map(conv -> {
                if (!isSameTenant(ctx, conv.getTenantId())) return false;
                if (isOwner(ctx, conv.getOwnerUserId())) return true;
                return false;
            })
            .orElse(false);
    }

    private boolean isAdmin(AuthContext ctx) {
        if (ctx == null) return false;
        if (ctx.getAccessLevel() == AccessLevel.FULL) return true;
        if (ctx.isFounder()) return true;
        return false;
    }

    private boolean isSameTenant(AuthContext ctx, String tenantId) {
        if (tenantId == null) return true;
        String ctxTenant = ctx != null ? ctx.getTenantId() : null;
        if (ctxTenant == null) return true;
        return ctxTenant.equals(tenantId);
    }

    private boolean isOwner(AuthContext ctx, String ownerUserId) {
        if (ownerUserId == null || ctx == null) return false;
        return ownerUserId.equals(ctx.getEmployeeId());
    }

    private boolean isDepartmentMember(AuthContext ctx, String departmentCode) {
        if (departmentCode == null || ctx == null) return false;
        return departmentCode.equalsIgnoreCase(ctx.getDepartment());
    }

    private boolean isDepartmentManager(AuthContext ctx, String departmentCode) {
        if (departmentCode == null || ctx == null) return false;
        if (isAdmin(ctx)) return true;
        if (!departmentCode.equalsIgnoreCase(ctx.getDepartment())) return false;
        String position = ctx.getPosition();
        return position != null && (position.contains("经理") || position.contains("主管") || position.contains("manager"));
    }
}
