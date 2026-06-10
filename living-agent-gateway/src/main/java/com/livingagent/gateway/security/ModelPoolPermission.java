package com.livingagent.gateway.security;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.PermissionService;

public class ModelPoolPermission {

    public static void checkChairmanPermission(PermissionService permissionService, String userId) {
        AccessLevel level = permissionService.getAccessLevel(userId);
        if (level != AccessLevel.FULL) {
            throw new SecurityException("仅董事长可访问模型池配置");
        }
    }

    public static boolean isChairman(PermissionService permissionService, String userId) {
        return permissionService.getAccessLevel(userId) == AccessLevel.FULL;
    }

    public static void checkDepartmentAccess(PermissionService permissionService, String userId, String brainId) {
        AccessLevel level = permissionService.getAccessLevel(userId);
        if (level == AccessLevel.CHAT_ONLY || level == AccessLevel.LIMITED) {
            throw new SecurityException("当前用户无权查看大脑模型配置");
        }
    }
}
