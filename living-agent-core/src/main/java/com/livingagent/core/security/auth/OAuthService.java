package com.livingagent.core.security.auth;

import com.livingagent.core.security.AuthContext;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface OAuthService {

    String getProviderName();
    
    String getAuthorizationUrl(String redirectUri, String state);
    
    OAuthToken exchangeCodeForToken(String code, String redirectUri);
    
    OAuthUserInfo getUserInfo(OAuthToken token);
    
    Optional<AuthContext> findOrCreateEmployee(OAuthUserInfo userInfo);
    
    OAuthResult authenticate(String code, String redirectUri);
    
    boolean validateToken(String accessToken);
    
    void revokeToken(String accessToken);
    
    Optional<AuthContext> findByOAuthUserId(String oauthUserId);

    record OAuthToken(
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            String scope
    ) {
        public static OAuthToken create(String accessToken, String refreshToken, Instant expiresAt, String scope) {
            return new OAuthToken(accessToken, refreshToken, expiresAt, scope);
        }
    }

    record OAuthUserInfo(
            String userId,
            String name,
            String email,
            String phone,
            String department,
            String position
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                "userId", userId != null ? userId : "",
                "name", name != null ? name : "",
                "email", email != null ? email : "",
                "phone", phone != null ? phone : "",
                "department", department != null ? department : "",
                "position", position != null ? position : ""
            );
        }
        
        public String providerUserId() {
            return userId;
        }
    }

    record OAuthResult(
            boolean success,
            AuthContext authContext,
            OAuthToken token,
            String error,
            String errorDescription
    ) {
        public static OAuthResult success(AuthContext authContext, OAuthToken token) {
            return new OAuthResult(true, authContext, token, null, null);
        }
        
        public static OAuthResult failed(String error, String description) {
            return new OAuthResult(false, null, null, error, description);
        }
    }
}
