package com.livingagent.gateway.controller;

import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.OAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UnifiedAuthService unifiedAuthService;
    private final Map<String, OAuthService> oauthServices;

    public AuthController(
            UnifiedAuthService unifiedAuthService,
            List<OAuthService> oauthServiceList
    ) {
        this.unifiedAuthService = unifiedAuthService;
        this.oauthServices = new HashMap<>();
        if (oauthServiceList != null) {
            for (OAuthService service : oauthServiceList) {
                this.oauthServices.put(service.getProviderName().toLowerCase(), service);
            }
        }
    }

    @GetMapping("/oauth/{provider}/url")
    public ResponseEntity<ApiResponse<OAuthUrlResponse>> getOAuthUrl(
            @PathVariable String provider,
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state
    ) {
        log.info("Getting OAuth URL for provider: {}", provider);

        OAuthService oauthService = oauthServices.get(provider.toLowerCase());
        if (oauthService == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("unsupported_provider", "OAuth provider not supported: " + provider));
        }

        String actualState = state != null ? state : UUID.randomUUID().toString();
        String authorizationUrl = oauthService.getAuthorizationUrl(redirectUri, actualState);

        OAuthUrlResponse response = new OAuthUrlResponse(authorizationUrl, actualState);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/oauth/{provider}/callback")
    public ResponseEntity<ApiResponse<LoginResponse>> oauthCallback(
            @PathVariable String provider,
            @RequestBody OAuthCallbackRequest request
    ) {
        log.info("Processing OAuth callback for provider: {}", provider);

        AuthResult result = unifiedAuthService.authenticateByOAuth(
                provider.toLowerCase(),
                request.code(),
                request.redirectUri()
        );

        if (!result.success()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(result.error(), result.errorDescription()));
        }

        AuthContext authContext = result.authContext();
        AuthSession session = result.session();

        LoginResponse response = new LoginResponse(
                session.sessionId(),
                null,
                convertToUserInfo(authContext)
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<UserInfo>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "No valid token provided"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = unifiedAuthService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "Session has expired"));
        }

        UserInfo userInfo = convertToUserInfo(sessionOpt.get().authContext());
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> getCurrentUserAlias(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return getCurrentUser(authorization);
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> updateMe(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateUserRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "No valid token provided"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = unifiedAuthService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "Session has expired"));
        }

        log.info("Updating user info for session: {}", sessionId);

        AuthContext authContext = sessionOpt.get().authContext();
        UserInfo userInfo = new UserInfo(
                authContext.getEmployeeId(),
                request.email() != null ? request.email() : authContext.getEmail(),
                request.name() != null ? request.name() : authContext.getName(),
                request.avatar(),
                authContext.getDepartment(),
                authContext.getIdentity().name(),
                authContext.getAccessLevel().name(),
                authContext.isFounder(),
                "tenant_default",
                new ArrayList<>(authContext.getAllowedBrains()),
                new ArrayList<>(),
                new ArrayList<>()
        );

        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @RequestHeader("Authorization") String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "No valid token provided"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = unifiedAuthService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "Session has expired"));
        }

        AuthSession session = sessionOpt.get();
        TokenRefreshResponse response = new TokenRefreshResponse(
                session.sessionId(),
                3600L
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorization
    ) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String sessionId = authorization.substring(7);
            unifiedAuthService.invalidateSession(sessionId);
            log.info("User logged out, session: {}", sessionId);
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<OAuthProviderInfo>>> getProviders() {
        List<OAuthProviderInfo> providers = oauthServices.entrySet().stream()
                .map(entry -> new OAuthProviderInfo(
                        entry.getKey(),
                        entry.getValue().getProviderName(),
                        true
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(providers));
    }

    private UserInfo convertToUserInfo(AuthContext authContext) {
        return new UserInfo(
                authContext.getEmployeeId(),
                authContext.getEmail(),
                authContext.getName(),
                null,
                authContext.getDepartment(),
                authContext.getIdentity().name(),
                authContext.getAccessLevel().name(),
                authContext.isFounder(),
                "tenant_default",
                new ArrayList<>(authContext.getAllowedBrains()),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
    }

    public record OAuthUrlResponse(String redirectUrl, String state) {}

    public record OAuthCallbackRequest(String code, String redirectUri) {}

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            UserInfo user
    ) {}

    public record UserInfo(
            String id,
            String email,
            String name,
            String avatar,
            String department,
            String identity,
            String accessLevel,
            boolean founder,
            String tenantId,
            List<String> allowedBrains,
            List<String> capabilities,
            List<String> skills
    ) {}

    public record TokenRefreshResponse(String accessToken, Long expiresIn) {}

    public record OAuthProviderInfo(
            String id,
            String name,
            boolean enabled
    ) {}

    public record UpdateUserRequest(
            String name,
            String email,
            String avatar
    ) {}
}
