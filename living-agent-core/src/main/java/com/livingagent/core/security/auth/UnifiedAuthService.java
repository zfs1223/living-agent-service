package com.livingagent.core.security.auth;

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
    private final Map<String, AuthSession> activeSessions = new ConcurrentHashMap<>();

    public UnifiedAuthService(
            List<OAuthService> oauthServices,
            VoicePrintService voicePrintService,
            PhoneVerificationService phoneVerificationService
    ) {
        this.oauthServices = new ConcurrentHashMap<>();
        if (oauthServices != null) {
            for (OAuthService service : oauthServices) {
                this.oauthServices.put(service.getProviderName(), service);
            }
        }
        this.voicePrintService = voicePrintService;
        this.phoneVerificationService = phoneVerificationService;
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

    public Optional<AuthSession> validateSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        AuthSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }

        if (session.isExpired()) {
            activeSessions.remove(sessionId);
            log.info("Session expired: {}", sessionId);
            return Optional.empty();
        }

        AuthSession touchedSession = session.touch();
        activeSessions.put(sessionId, touchedSession);
        return Optional.of(touchedSession);
    }

    public void invalidateSession(String sessionId) {
        AuthSession removed = activeSessions.remove(sessionId);
        if (removed != null) {
            log.info("Session invalidated: {} for user: {}", sessionId, removed.authContext().getName());
        }
    }

    public void invalidateAllSessionsForUser(String employeeId) {
        activeSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().authContext().getEmployeeId().equals(employeeId)) {
                log.info("Invalidated session {} for user: {}", entry.getKey(), employeeId);
                return true;
            }
            return false;
        });
    }

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
        
        public AuthSession touch() {
            return new AuthSession(sessionId, authContext, authMethod, createdAt, 
                    Instant.now().plusSeconds(3600), metadata);
        }
    }
}
