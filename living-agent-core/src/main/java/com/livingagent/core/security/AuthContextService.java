package com.livingagent.core.security;

import java.util.List;
import java.util.Optional;

public interface AuthContextService {

    AuthContext createAuthContext(AuthContext authContext);

    AuthContext updateAuthContext(AuthContext authContext);

    void deleteAuthContext(String employeeId);

    Optional<AuthContext> findById(String employeeId);

    Optional<AuthContext> findByPhone(String phone);

    Optional<AuthContext> findByEmail(String email);

    Optional<AuthContext> findByVoicePrintId(String voicePrintId);

    Optional<AuthContext> findByOAuth(String provider, String oauthUserId);

    List<AuthContext> findByDepartment(String department);

    List<AuthContext> findByIdentity(UserIdentity identity);

    List<AuthContext> findAllActive();

    List<AuthContext> findAll();

    void updateAuthContextStatus(String employeeId, UserIdentity newIdentity);

    void setVoicePrintId(String employeeId, String voicePrintId);

    void linkOAuthAccount(String employeeId, String provider, String oauthUserId);

    boolean hasAnyAuthContext();

    boolean hasFounder();
}
