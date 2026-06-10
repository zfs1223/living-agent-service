package com.livingagent.core.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PermissionService {

    Optional<SecurityIdentity> verifyByPhone(String phone, String verificationCode);

    Optional<SecurityIdentity> verifyByVoicePrint(String voicePrintId, float[] voiceVector);

    Optional<SecurityIdentity> verifyByOAuth(String provider, String oauthUserId, String accessToken);

    Optional<SecurityIdentity> getEmployeeById(String employeeId);

    Optional<SecurityIdentity> getEmployeeByPhone(String phone);

    Optional<SecurityIdentity> getEmployeeByVoicePrintId(String voicePrintId);

    boolean canAccessBrain(String employeeId, String brainName);

    boolean canUseModel(String employeeId, String modelName);

    boolean canExecuteTool(String employeeId, String toolName);

    Set<String> getAccessibleBrains(String employeeId);

    Set<String> getAllowedModels(String employeeId);

    AccessLevel getAccessLevel(String employeeId);

    void updateAccessLevel(String employeeId, AccessLevel newLevel);

    void recordAccess(String employeeId, String resource, String action, boolean granted);

    List<AccessAuditLog> getAccessLogs(String employeeId, int limit);

    boolean isChatOnlyUser(String employeeId);

    String getRouteTarget(String employeeId);
}
