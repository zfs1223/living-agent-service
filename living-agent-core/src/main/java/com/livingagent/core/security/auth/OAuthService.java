package com.livingagent.core.security.auth;

import com.livingagent.core.security.Employee;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface OAuthService {

    String getProviderName();
    
    String getAuthorizationUrl(String redirectUri, String state);
    
    OAuthToken exchangeCodeForToken(String code, String redirectUri);
    
    OAuthUserInfo getUserInfo(OAuthToken token);
    
    Optional<Employee> findOrCreateEmployee(OAuthUserInfo userInfo);
    
    OAuthResult authenticate(String code, String redirectUri);
    
    boolean validateToken(String accessToken);
    
    void revokeToken(String accessToken);

    record OAuthToken(
            String accessToken,
            String refreshToken,
            Long expiresIn,
            String tokenType,
            String scope,
            Instant createdAt
    ) {
        public boolean isExpired() {
            if (expiresIn == null || createdAt == null) {
                return true;
            }
            return Instant.now().isAfter(createdAt.plusSeconds(expiresIn));
        }
        
        public static OAuthToken create(String accessToken, String refreshToken, Long expiresIn) {
            return new OAuthToken(accessToken, refreshToken, expiresIn, "Bearer", null, Instant.now());
        }
    }

    record OAuthUserInfo(
            String providerUserId,
            String name,
            String email,
            String phone,
            String avatar,
            String department,
            String position,
            Map<String, Object> rawInfo
    ) {
        public static OAuthUserInfo of(String providerUserId, String name, String email) {
            return new OAuthUserInfo(providerUserId, name, email, null, null, null, null, Map.of());
        }
    }

    record OAuthResult(
            boolean success,
            Employee employee,
            OAuthToken token,
            String error,
            String errorDescription
    ) {
        public static OAuthResult success(Employee employee, OAuthToken token) {
            return new OAuthResult(true, employee, token, null, null);
        }
        
        public static OAuthResult failed(String error, String description) {
            return new OAuthResult(false, null, null, error, description);
        }
    }
}
