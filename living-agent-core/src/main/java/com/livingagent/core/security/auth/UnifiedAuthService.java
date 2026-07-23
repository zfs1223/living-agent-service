package com.livingagent.core.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.SessionContextEntity;
import com.livingagent.core.database.repository.SessionContextRepository;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.voiceprint.VoicePrintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UnifiedAuthService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedAuthService.class);

    private final Map<String, OAuthService> oauthServices;
    private final VoicePrintService voicePrintService;
    private final PhoneVerificationService phoneVerificationService;
    private final SessionContextRepository sessionContextRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, AuthSession> activeSessions = new ConcurrentHashMap<>();

    public UnifiedAuthService(
            List<OAuthService> oauthServices,
            VoicePrintService voicePrintService,
            PhoneVerificationService phoneVerificationService,
            SessionContextRepository sessionContextRepository
    ) {
        this.oauthServices = new ConcurrentHashMap<>();
        if (oauthServices != null) {
            for (OAuthService service : oauthServices) {
                this.oauthServices.put(service.getProviderName(), service);
            }
        }
        this.voicePrintService = voicePrintService;
        this.phoneVerificationService = phoneVerificationService;
        this.sessionContextRepository = sessionContextRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * B-1-10: 启动时从 DB 加载未过期会话到内存缓存。
     */
    public void loadSessionsFromDb() {
        try {
            List<SessionContextEntity> entities = sessionContextRepository.findAll();
            int loaded = 0;
            for (SessionContextEntity entity : entities) {
                AuthSession session = toAuthSession(entity);
                if (session != null && !session.isExpired()) {
                    activeSessions.put(session.sessionId(), session);
                    loaded++;
                }
            }
            log.info("B-1-10: Loaded {} active sessions from database (total entities: {})", loaded, entities.size());
        } catch (Exception e) {
            log.warn("B-1-10: Failed to load sessions from database: {}", e.getMessage());
        }
    }

    public AuthResult authenticateByOAuth(String provider, String code, String redirectUri) {
        log.info("Authenticating via OAuth provider: {}", provider);

        OAuthService oauthService = oauthServices.get(provider);
        if (oauthService == null) {
            return AuthResult.failed("unsupported_provider", "OAuth provider not supported: " + provider);
        }

        OAuthService.OAuthResult oauthResult = oauthService.authenticate(code, redirectUri);
        if (!oauthResult.success()) {
            return AuthResult.failed(oauthResult.error(), oauthResult.errorDescription());
        }

        AuthContext authContext = oauthResult.authContext();
        AuthSession session = createSession(authContext, "oauth_" + provider);

        log.info("OAuth authentication successful: {} ({})", authContext.getName(), provider);
        return AuthResult.success(authContext, session);
    }

    public AuthResult authenticateByVoicePrint(String userId, byte[] audioData) {
        log.info("Authenticating via voice print");

        if (voicePrintService == null) {
            return AuthResult.failed("voice_print_unavailable", "Voice print service not available");
        }

        boolean verified = voicePrintService.verify(userId, audioData);
        if (!verified) {
            return AuthResult.failed("voice_print_failed", "Voice verification failed");
        }

        AuthContext authContext = createAuthContextFromVoicePrint(userId);
        AuthSession session = createSession(authContext, "voice_print");

        log.info("Voice print authentication successful: {}", authContext.getName());
        return AuthResult.success(authContext, session);
    }

    public AuthResult authenticateByPhone(String phone, String code) {
        log.info("Authenticating via phone: {}", maskPhone(phone));

        if (phoneVerificationService == null) {
            return AuthResult.failed("phone_verification_unavailable", "Phone verification service not available");
        }

        PhoneVerificationService.VerifyResult result = phoneVerificationService.verifyCode(phone, code);
        if (!result.isSuccess()) {
            return AuthResult.failed("invalid_code", result.error());
        }

        AuthContext authContext = createAuthContextFromPhone(phone);
        AuthSession session = createSession(authContext, "phone");

        log.info("Phone authentication successful: {}", maskPhone(phone));
        return AuthResult.success(authContext, session);
    }

    public AuthResult createInternalSession(AuthContext authContext) {
        AuthSession session = createSession(authContext, "internal");
        log.info("Created internal session for: {}", authContext.getName());
        return AuthResult.success(authContext, session);
    }

    /**
     * B-1-10: 验证会话。优先从内存查，内存未命中时从 DB 查。
     */
    public Optional<AuthSession> validateSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        AuthSession session = activeSessions.get(sessionId);

        // B-1-10: 内存未命中时从 DB 查
        if (session == null) {
            try {
                session = sessionContextRepository.findById(sessionId)
                    .map(this::toAuthSession)
                    .orElse(null);
                if (session != null) {
                    activeSessions.put(sessionId, session);
                    log.debug("B-1-10: Session loaded from DB: {}", sessionId);
                }
            } catch (Exception e) {
                log.debug("B-1-10: Failed to query session from DB: {}", e.getMessage());
            }
        }

        if (session == null) {
            return Optional.empty();
        }

        if (session.isExpired()) {
            activeSessions.remove(sessionId);
            // B-1-10: 过期会话从 DB 删除
            try { sessionContextRepository.deleteById(sessionId); } catch (Exception ignored) {}
            log.info("Session expired: {}", sessionId);
            return Optional.empty();
        }

        if (session.isRevoked()) {
            activeSessions.remove(sessionId);
            // B-1-10: 已撤销会话从 DB 删除
            try { sessionContextRepository.deleteById(sessionId); } catch (Exception ignored) {}
            log.info("Session revoked: {}", sessionId);
            return Optional.empty();
        }

        AuthSession touchedSession = session.touch();
        activeSessions.put(sessionId, touchedSession);
        return Optional.of(touchedSession);
    }

    /**
     * 刷新会话：使旧会话立即失效，创建新会话（令牌轮换）
     * @param oldSessionId 旧会话ID
     * @return 新会话，如果旧会话无效则返回 empty
     */
    public Optional<AuthSession> refreshSession(String oldSessionId) {
        if (oldSessionId == null) {
            return Optional.empty();
        }

        AuthSession oldSession = activeSessions.get(oldSessionId);
        if (oldSession == null) {
            return Optional.empty();
        }

        if (oldSession.isExpired()) {
            activeSessions.remove(oldSessionId);
            // B-1-10: 过期会话从 DB 删除
            try { sessionContextRepository.deleteById(oldSessionId); } catch (Exception ignored) {}
            log.info("Cannot refresh expired session: {}", oldSessionId);
            return Optional.empty();
        }

        // 使旧会话立即失效
        activeSessions.remove(oldSessionId);
        // B-1-10: 旧会话从 DB 删除
        try { sessionContextRepository.deleteById(oldSessionId); } catch (Exception ignored) {}
        log.info("Old session revoked for token rotation: {}", oldSessionId);

        // 创建新会话
        AuthContext authContext = oldSession.authContext();
        AuthSession newSession = createSession(authContext, oldSession.authMethod());

        log.info("Token rotation completed: old={} -> new={} for user={}",
                oldSessionId, newSession.sessionId(), authContext.getName());
        return Optional.of(newSession);
    }

    /**
     * B-1-10: 使会话失效，同时从 DB 删除。
     */
    public void invalidateSession(String sessionId) {
        AuthSession removed = activeSessions.remove(sessionId);
        if (removed != null) {
            // B-1-10: 同时从 DB 删除
            try { sessionContextRepository.deleteById(sessionId); } catch (Exception ignored) {}
            log.info("Session invalidated: {} for user: {}", sessionId, removed.authContext().getName());
        }
    }

    /**
     * B-1-10: 使某用户所有会话失效，同时从 DB 删除。
     */
    public void invalidateAllSessionsForUser(String employeeId) {
        activeSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().authContext().getEmployeeId().equals(employeeId)) {
                // B-1-10: 同时从 DB 删除
                try { sessionContextRepository.deleteById(entry.getKey()); } catch (Exception ignored) {}
                log.info("Invalidated session {} for user: {}", entry.getKey(), employeeId);
                return true;
            }
            return false;
        });
    }

    public void updateSessionTenantId(String sessionId, String tenantId) {
        AuthSession session = activeSessions.get(sessionId);
        if (session != null) {
            AuthContext authContext = session.authContext();
            authContext.setTenantId(tenantId);
            log.info("Updated tenantId for session: {} -> {}", sessionId, tenantId);
        }
    }

    /**
     * B-1-10: 创建会话，同时写 DB。
     */
    private AuthSession createSession(AuthContext authContext, String authMethod) {
        String sessionId = "sess_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600);

        AuthSession session = new AuthSession(
            sessionId,
            authContext,
            authMethod,
            now,
            expiresAt,
            new ConcurrentHashMap<>()
        );

        activeSessions.put(sessionId, session);

        // B-1-10: 同时写 DB
        try {
            sessionContextRepository.save(toSessionContextEntity(session));
        } catch (Exception e) {
            log.warn("B-1-10: Failed to persist session to DB: {}", e.getMessage());
        }

        return session;
    }

    private AuthContext createAuthContextFromVoicePrint(String userId) {
        AuthContext authContext = new AuthContext();
        authContext.setEmployeeId(userId);
        authContext.setName("用户" + userId.substring(0, Math.min(4, userId.length())));
        authContext.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        authContext.setLastSyncTime(Instant.now());
        authContext.setSyncSource("voice_print");
        return authContext;
    }

    private AuthContext createAuthContextFromPhone(String phone) {
        AuthContext authContext = new AuthContext();
        authContext.setEmployeeId("phone_" + phone.hashCode());
        authContext.setPhone(phone);
        authContext.setName("用户" + phone.substring(phone.length() - 4));
        authContext.setIdentity(UserIdentity.EXTERNAL_VISITOR);
        authContext.setLastSyncTime(Instant.now());
        authContext.setSyncSource("phone_verification");
        return authContext;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("activeSessions", activeSessions.size());
        stats.put("oauthProviders", oauthServices.keySet());
        stats.put("voicePrintCount", voicePrintService != null ? voicePrintService.getVoicePrintCount() : 0);
        return stats;
    }

    // ===== B-1-10: DB 映射方法 =====

    /** AuthSession -> SessionContextEntity */
    private SessionContextEntity toSessionContextEntity(AuthSession session) {
        SessionContextEntity entity = new SessionContextEntity();
        entity.setSessionId(session.sessionId());
        entity.setUserId(session.authContext().getEmployeeId());
        entity.setTenantId(session.authContext().getTenantId());
        entity.setDepartmentCode(session.authContext().getDepartment());
        entity.setConnectedAt(session.createdAt());
        entity.setLastActivity(Instant.now());
        // 将 authMethod、expiresAt、accessLevel 等信息序列化到 attributesJson
        try {
            Map<String, Object> attrs = new ConcurrentHashMap<>();
            attrs.put("authMethod", session.authMethod());
            attrs.put("expiresAt", session.expiresAt().toString());
            attrs.put("name", session.authContext().getName());
            attrs.put("identity", session.authContext().getIdentity() != null ? session.authContext().getIdentity().name() : null);
            attrs.put("accessLevel", session.authContext().getAccessLevel() != null ? session.authContext().getAccessLevel().name() : null);
            attrs.put("founder", session.authContext().isFounder());
            if (session.metadata() != null && !session.metadata().isEmpty()) {
                attrs.put("metadata", session.metadata());
            }
            entity.setAttributesJson(objectMapper.writeValueAsString(attrs));
        } catch (Exception e) {
            entity.setAttributesJson("{}");
        }
        return entity;
    }

    /** SessionContextEntity -> AuthSession */
    private AuthSession toAuthSession(SessionContextEntity entity) {
        try {
            String json = entity.getAttributesJson();
            Map<String, Object> attrs = (json != null && !json.isEmpty())
                ? objectMapper.readValue(json, Map.class) : Map.of();

            AuthContext authContext = new AuthContext();
            authContext.setEmployeeId(entity.getUserId());
            authContext.setTenantId(entity.getTenantId());
            authContext.setDepartment(entity.getDepartmentCode());
            authContext.setSessionId(entity.getSessionId());
            if (attrs.containsKey("name")) authContext.setName((String) attrs.get("name"));
            if (attrs.containsKey("identity")) {
                try {
                    authContext.setIdentity(UserIdentity.valueOf((String) attrs.get("identity")));
                } catch (Exception ignored) {}
            }
            // 恢复 accessLevel 和 founder 字段
            if (attrs.containsKey("accessLevel")) {
                try {
                    authContext.setAccessLevel(AccessLevel.valueOf((String) attrs.get("accessLevel")));
                } catch (Exception ignored) {}
            }
            if (attrs.containsKey("founder")) {
                authContext.setFounder(Boolean.TRUE.equals(attrs.get("founder")));
            }

            String authMethod = (String) attrs.getOrDefault("authMethod", "unknown");
            Instant expiresAt = attrs.containsKey("expiresAt")
                ? Instant.parse((String) attrs.get("expiresAt"))
                : entity.getConnectedAt().plusSeconds(3600);

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = attrs.containsKey("metadata")
                ? (Map<String, Object>) attrs.get("metadata")
                : new ConcurrentHashMap<>();

            return new AuthSession(
                entity.getSessionId(),
                authContext,
                authMethod,
                entity.getConnectedAt(),
                expiresAt,
                metadata
            );
        } catch (Exception e) {
            log.warn("B-1-10: Failed to map SessionContextEntity to AuthSession: {}", e.getMessage());
            return null;
        }
    }

    public record AuthResult(
            boolean success,
            AuthContext authContext,
            AuthSession session,
            String error,
            String errorDescription
    ) {
        public static AuthResult success(AuthContext authContext, AuthSession session) {
            return new AuthResult(true, authContext, session, null, null);
        }
        
        public static AuthResult failed(String error, String description) {
            return new AuthResult(false, null, null, error, description);
        }
    }

    public record AuthSession(
            String sessionId,
            AuthContext authContext,
            String authMethod,
            Instant createdAt,
            Instant expiresAt,
            Map<String, Object> metadata
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public boolean isRevoked() {
            return metadata != null && Boolean.TRUE.equals(metadata.get("revoked"));
        }

        public AuthSession touch() {
            return new AuthSession(sessionId, authContext, authMethod, createdAt,
                    Instant.now().plusSeconds(3600), metadata);
        }
    }
}
