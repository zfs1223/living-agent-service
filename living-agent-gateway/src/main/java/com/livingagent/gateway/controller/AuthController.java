package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.OAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.security.client.ClientUserBindingService;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.gateway.controller.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** OAuth state 存储：state -> 过期时间戳（毫秒） */
    private final Map<String, Long> oauthStateStore = new ConcurrentHashMap<>();

    /** OAuth state 有效期：5 分钟 */
    private static final long OAUTH_STATE_TTL_MS = 5 * 60 * 1000L;

    /** state 存储最大容量，防止内存泄漏 */
    private static final int MAX_STATE_STORE_SIZE = 10000;

    /** access_token Cookie 有效期：30 分钟 */
    private static final long ACCESS_TOKEN_COOKIE_MAX_AGE_SEC = 30 * 60L;

    /** refresh_token Cookie 有效期：7 天 */
    private static final long REFRESH_TOKEN_COOKIE_MAX_AGE_SEC = 7 * 24 * 60 * 60L;

    /** Cookie 路径：access_token 覆盖所有 /api 路径 */
    private static final String ACCESS_TOKEN_COOKIE_PATH = "/api";

    /** Cookie 路径：refresh_token 仅限刷新接口 */
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth/refresh";

    private final UnifiedAuthService unifiedAuthService;
    private final Map<String, OAuthService> oauthServices;
    private final AccessGateService accessGateService;
    private final EnterpriseEmployeeService employeeService;
    private final ClientUserBindingService clientUserBindingService;
    private final TenantController tenantController;

    /** 是否启用 Cookie 的 Secure 标志（生产环境 true，开发环境 false） */
    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    public AuthController(
            UnifiedAuthService unifiedAuthService,
            List<OAuthService> oauthServiceList,
            AccessGateService accessGateService,
            EnterpriseEmployeeService employeeService,
            ClientUserBindingService clientUserBindingService,
            TenantController tenantController
    ) {
        this.unifiedAuthService = unifiedAuthService;
        this.oauthServices = new HashMap<>();
        if (oauthServiceList != null) {
            for (OAuthService service : oauthServiceList) {
                this.oauthServices.put(service.getProviderName().toLowerCase(), service);
            }
        }
        this.accessGateService = accessGateService;
        this.employeeService = employeeService;
        this.clientUserBindingService = clientUserBindingService;
        this.tenantController = tenantController;
    }

    @GetMapping("/oauth/{provider}/url")
    public ResponseEntity<ApiResponse<OAuthUrlResponse>> getOAuthUrl(
            @PathVariable String provider,
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Getting OAuth URL for provider: {}", provider);

        OAuthService oauthService = oauthServices.get(provider.toLowerCase());
        if (oauthService == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("unsupported_provider", "OAuth provider not supported: " + provider));
        }

        String actualState = state != null ? state : UUID.randomUUID().toString();
        String authorizationUrl = oauthService.getAuthorizationUrl(redirectUri, actualState);

        // 存储 state 用于回调验证
        storeOAuthState(actualState);

        OAuthUrlResponse response = new OAuthUrlResponse(authorizationUrl, actualState);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/oauth/{provider}/callback")
    public ResponseEntity<ApiResponse<LoginResponse>> oauthCallback(
            @PathVariable String provider,
            @RequestBody OAuthCallbackRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            HttpServletResponse response
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Processing OAuth callback for provider: {}", provider);

        // 验证 state 参数，防止 CSRF 攻击
        if (request.state() == null || request.state().isBlank()) {
            log.warn("OAuth callback rejected: missing state parameter for provider {}", provider);
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("invalid_state", "State parameter is required"));
        }
        if (!validateAndConsumeOAuthState(request.state())) {
            log.warn("OAuth callback rejected: invalid or expired state for provider {}", provider);
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("invalid_state", "Invalid or expired state parameter"));
        }

        AuthResult result = unifiedAuthService.authenticateByOAuth(
                provider.toLowerCase(),
                request.code(),
                request.redirectUri()
        );

        if (!result.success()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err(result.error(), result.errorDescription()));
        }

        AuthContext authContext = result.authContext();

        Optional<AuthContext> existingUser = employeeService.findByOAuth(
            authContext.getOauthProvider(),
            authContext.getOauthUserId()
        );

        if (existingUser.isPresent()) {
            AuthContext dbUser = existingUser.get();
            authContext.setTenantId(dbUser.getTenantId());
            authContext.setIdentity(dbUser.getIdentity());
            authContext.setAccessLevel(dbUser.getAccessLevel());
            authContext.setFounder(dbUser.isFounder());
            log.info("Found existing user in database: {}, tenantId: {}",
                authContext.getName(), authContext.getTenantId());
        } else {
            employeeService.createAuthContext(authContext);
            log.info("Created new user in database: {}", authContext.getName());

            // 如果有 inviteCode,自动加入公司
            if (request.inviteCode() != null && !request.inviteCode().isBlank()) {
                try {
                    // 构造 joinTenant 请求
                    TenantController.TenantJoinRequest joinRequest =
                        new TenantController.TenantJoinRequest(request.inviteCode());

                    // 调用 tenantController.joinTenant
                    ResponseEntity<com.livingagent.gateway.controller.common.ApiResponse<TenantController.TenantJoinResult>> joinResult =
                        tenantController.joinTenant(joinRequest, "Bearer " + result.session().sessionId(), null);

                    if (joinResult.getStatusCode().is2xxSuccessful() && joinResult.getBody() != null) {
                        TenantController.TenantJoinResult joinData = joinResult.getBody().data();
                        authContext.setTenantId(joinData.tenant_id());  // 注意字段名是 tenant_id
                        log.info("Auto-joined user {} to tenant {} via invite code {}",
                            authContext.getName(), joinData.tenant_id(), request.inviteCode());
                    } else {
                        log.warn("Failed to auto-join user {} with invite code {}: {}",
                            authContext.getName(), request.inviteCode(),
                            joinResult.getBody() != null ? joinResult.getBody().error() : "unknown error");
                    }
                } catch (Exception e) {
                    log.error("Error auto-joining user {} with invite code {}: {}",
                        authContext.getName(), request.inviteCode(), e.getMessage());
                }
            }
        }

        AuthSession session = result.session();

        // 设置 HttpOnly Cookie
        setTokenCookies(response, session.sessionId(), session.sessionId());

        // 绑定 clientId 与 userId（如果请求中包含 clientId）
        String clientId = request.clientId();
        if (clientId != null && !clientId.isBlank()) {
            clientUserBindingService.bindOnLogin(
                clientId,
                authContext.getEmployeeId(),
                authContext.getAccessLevel() != null ? authContext.getAccessLevel().getLevel() : 0,
                authContext.getDepartment(),
                authContext.getTenantId()
            );
        }

        LoginResponse loginResponse = new LoginResponse(
                session.sessionId(),
                null,
                convertToUserInfo(authContext)
        );

        return ResponseEntity.ok(ApiResponse.ok(loginResponse));
    }

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<UserInfo>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "No valid token provided"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = unifiedAuthService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("session_expired", "Session has expired"));
        }

        UserInfo userInfo = convertToUserInfo(sessionOpt.get().authContext());
        return ResponseEntity.ok(ApiResponse.ok(userInfo));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> getCurrentUserAlias(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        return getCurrentUser(authorization, employeeId);
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> updateMe(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateUserRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "No valid token provided"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = unifiedAuthService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("session_expired", "Session has expired"));
        }

        log.info("Updating user info for session: {}", sessionId);

        AuthContext authContext = sessionOpt.get().authContext();

        // Apply requested changes to auth context
        if (request.name() != null) {
            authContext.setName(request.name());
        }
        if (request.email() != null) {
            authContext.setEmail(request.email());
        }

        // Persist changes to database
        try {
            authContext = employeeService.updateAuthContext(authContext);
        } catch (Exception e) {
            log.warn("Failed to persist profile update for {}: {}", authContext.getEmployeeId(), e.getMessage());
        }

        String avatar = request.avatar() != null ? request.avatar() : convertToUserInfo(authContext).avatar();

        UserInfo userInfo = new UserInfo(
                authContext.getEmployeeId(),
                authContext.getEmail(),
                authContext.getName(),
                avatar,
                authContext.getDepartment(),
                authContext.getIdentity().name(),
                authContext.getAccessLevel().name(),
                "member",
                authContext.isFounder(),
                authContext.getTenantId(),
                new ArrayList<>(authContext.getAllowedBrains()),
                new ArrayList<>(),
                new ArrayList<>()
        );

        return ResponseEntity.ok(ApiResponse.ok(userInfo));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
            HttpServletResponse response
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        // 优先从 Authorization 头获取 token，其次从 Cookie 获取
        String oldSessionId = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            oldSessionId = authorization.substring(7);
        } else if (cookieRefreshToken != null && !cookieRefreshToken.isBlank()) {
            oldSessionId = cookieRefreshToken;
        }

        if (oldSessionId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "No valid token provided"));
        }

        // 令牌轮换：旧 token 立即失效，颁发新 token
        Optional<AuthSession> newSessionOpt = unifiedAuthService.refreshSession(oldSessionId);

        if (newSessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("session_expired", "Session has expired or is invalid"));
        }

        AuthSession newSession = newSessionOpt.get();

        // 更新 HttpOnly Cookie
        setTokenCookies(response, newSession.sessionId(), newSession.sessionId());

        TokenRefreshResponse refreshResponse = new TokenRefreshResponse(
                newSession.sessionId(),
                3600L
        );

        return ResponseEntity.ok(ApiResponse.ok(refreshResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            HttpServletResponse response
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String sessionId = authorization.substring(7);
            unifiedAuthService.invalidateSession(sessionId);
            log.info("User logged out, session: {}", sessionId);
        }

        // 清除 HttpOnly Cookie
        clearTokenCookies(response);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<OAuthProviderInfo>>> getProviders(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        List<OAuthProviderInfo> providers = oauthServices.entrySet().stream()
                .map(entry -> new OAuthProviderInfo(
                        entry.getKey(),
                        entry.getValue().getProviderName(),
                        true
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(providers));
    }

    /**
     * 权限检查端点 - 前端调用后端验证权限，避免前端推断
     *
     * GET /api/auth/check?resource={resource}&action={action}
     */
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<PermissionCheckResult>> checkPermission(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String resource,
            @RequestParam(defaultValue = "access") String action,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "No valid token provided"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = unifiedAuthService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("session_expired", "Session has expired"));
        }

        AuthContext authContext = sessionOpt.get().authContext();
        String accessLevel = authContext.getAccessLevel().name();
        String department = authContext.getDepartment();
        boolean allowed;

        // 基于资源和操作的后端权限验证
        allowed = switch (resource) {
            case "main_brain" -> "FULL".equals(accessLevel);
            case "department_brain" -> "FULL".equals(accessLevel) ||
                ("DEPARTMENT".equals(accessLevel) && action.equals("own_department"));
            case "admin_brain", "cs_brain" -> "FULL".equals(accessLevel) ||
                "DEPARTMENT".equals(accessLevel) ||
                "LIMITED".equals(accessLevel);
            case "chat_only" -> true;
            case "enterprise_settings", "employee_management", "evolution_control" ->
                "FULL".equals(accessLevel);
            default -> "FULL".equals(accessLevel) || "DEPARTMENT".equals(accessLevel);
        };

        PermissionCheckResult result = new PermissionCheckResult(
            allowed, accessLevel, department, resource, action
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private UserInfo convertToUserInfo(AuthContext authContext) {
        // 根据 accessLevel 推断 role
        String role = "member";
        if (authContext.getAccessLevel() == AccessLevel.FULL) {
            role = "org_admin";
        } else if (authContext.getAccessLevel() == AccessLevel.DEPARTMENT) {
            role = "agent_admin";
        }
        return new UserInfo(
                authContext.getEmployeeId(),
                authContext.getEmail(),
                authContext.getName(),
                null,
                authContext.getDepartment(),
                authContext.getIdentity().name(),
                authContext.getAccessLevel().name(),
                role,
                authContext.isFounder(),
                authContext.getTenantId(),
                new ArrayList<>(authContext.getAllowedBrains()),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public record OAuthUrlResponse(String redirectUrl, String state) {}

    public record OAuthCallbackRequest(String code, String redirectUri, String state, String clientId, String inviteCode) {}

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
            String role,
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

    public record PermissionCheckResult(
            boolean allowed,
            String accessLevel,
            String department,
            String resource,
            String action
    ) {}

    /**
     * 存储 OAuth state，设置 5 分钟过期时间
     */
    private void storeOAuthState(String state) {
        // 清理过期 state，防止内存泄漏
        if (oauthStateStore.size() > MAX_STATE_STORE_SIZE) {
            long now = System.currentTimeMillis();
            oauthStateStore.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        oauthStateStore.put(state, System.currentTimeMillis() + OAUTH_STATE_TTL_MS);
    }

    /**
     * 验证并消费 OAuth state（一次性使用）
     * @return true 如果 state 有效且未过期
     */
    private boolean validateAndConsumeOAuthState(String state) {
        Long expiresAt = oauthStateStore.remove(state);
        if (expiresAt == null) {
            return false;
        }
        return System.currentTimeMillis() <= expiresAt;
    }

    /**
     * 设置 HttpOnly Cookie：access_token 和 refresh_token
     *
     * @param response     HTTP 响应
     * @param accessToken  访问令牌（sessionId）
     * @param refreshToken 刷新令牌（sessionId，当前与 accessToken 相同）
     */
    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessTokenCookie = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(ACCESS_TOKEN_COOKIE_MAX_AGE_SEC)
                .build();

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(REFRESH_TOKEN_COOKIE_MAX_AGE_SEC)
                .build();

        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());

        log.debug("HttpOnly cookies set: access_token (path={}, maxAge={}s), refresh_token (path={}, maxAge={}s)",
                ACCESS_TOKEN_COOKIE_PATH, ACCESS_TOKEN_COOKIE_MAX_AGE_SEC,
                REFRESH_TOKEN_COOKIE_PATH, REFRESH_TOKEN_COOKIE_MAX_AGE_SEC);
    }

    /**
     * 清除 HttpOnly Cookie：将 maxAge 设为 0 立即过期
     */
    private void clearTokenCookies(HttpServletResponse response) {
        ResponseCookie accessTokenCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());

        log.debug("HttpOnly cookies cleared");
    }
}
