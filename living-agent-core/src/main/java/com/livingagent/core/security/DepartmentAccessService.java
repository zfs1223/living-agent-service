package com.livingagent.core.security;

import org.springframework.stereotype.Service;

/**
 * 统一的部门访问权限判断服务。
 * 合并了原先分散在 WebSocketHandler、ChatService、ApiController、Interceptor 中的 hasDepartmentAccess 逻辑。
 */
@Service
public class DepartmentAccessService {

    /**
     * 判断用户是否有权访问指定部门。
     *
     * 规则：
     * 1. ctx 为 null -> 无权限
     * 2. public 部门 -> 任何人可访问
     * 3. enterprise 部门 -> 需要 FULL 权限或 founder
     * 4. FULL 权限或 founder -> 可访问任何部门
     * 5. CHAT_ONLY 权限 -> 无权访问任何部门
     * 6. 其他 -> 用户所属部门与目标部门一致即可
     */
    public boolean hasDepartmentAccess(AuthContext ctx, String department) {
        if (ctx == null) return false;
        if ("public".equals(department)) return true;
        if ("enterprise".equals(department)) {
            return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
        }
        if (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder()) return true;
        if (ctx.getAccessLevel() == AccessLevel.CHAT_ONLY) return false;
        String userDept = ctx.getDepartment();
        return userDept != null && userDept.equalsIgnoreCase(department);
    }
}
