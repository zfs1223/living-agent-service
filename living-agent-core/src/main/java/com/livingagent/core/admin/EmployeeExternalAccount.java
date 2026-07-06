package com.livingagent.core.admin;

/**
 * 员工外部账号映射值对象
 * <p>记录员工在各外部服务中的账号信息，用于运行时员工工具调用时解析凭据。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md
 */
public record EmployeeExternalAccount(
    String employeeCode,       // 员工编码（T01、H01）
    String serviceType,        // 服务类型：gitlab/openproject/jenkins
    String externalUserId,     // 外部服务中的用户ID
    String externalUsername,   // 外部服务中的用户名
    String externalToken,      // 员工访问令牌
    String externalMetadata    // 额外元数据（JSON）
) {
    public static EmployeeExternalAccount of(String employeeCode, String serviceType,
                                             String userId, String username, String token) {
        return new EmployeeExternalAccount(employeeCode, serviceType, userId, username, token, null);
    }
}
