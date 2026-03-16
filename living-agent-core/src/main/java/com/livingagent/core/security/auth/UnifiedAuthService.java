package com.livingagent.core.security.auth;

import com.livingagent.core.security.Employee;
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

        Employee employee = oauthResult.employee();
        AuthSession session = createSession(employee, "oauth_" + provider);

        log.info("OAuth authentication successful: {} ({})", employee.getName(), provider);
        return AuthResult.success(employee, session);
    }

    public AuthResult authenticateByVoicePrint(byte[] audioData) {
        log.info("Authenticating via voice print");

        if (voicePrintService == null) {
            return AuthResult.failed("service_unavailable", "Voice print service not available");
        }

        Optional<VoicePrintService.VoicePrintMatch> match = voicePrintService.identify(audioData);
        if (match.isEmpty() || !match.get().isMatch()) {
            log.warn("Voice print authentication failed: no match found");
            return AuthResult.failed("voice_not_recognized", "Voice not recognized");
        }

        VoicePrintService.VoicePrintMatch voiceMatch = match.get();
        
        Employee employee = createEmployeeFromVoiceMatch(voiceMatch);
        AuthSession session = createSession(employee, "voice_print");

        log.info("Voice print authentication successful: {}", employee.getName());
        return AuthResult.success(employee, session);
    }

    public AuthResult authenticateByPhone(String phone, String code) {
        log.info("Authenticating via phone verification: {}", maskPhone(phone));

        if (phoneVerificationService == null) {
            return AuthResult.failed("service_unavailable", "Phone verification service not available");
        }

        PhoneVerificationService.VerifyResult result = phoneVerificationService.verifyCode(phone, code);
        if (!result.isSuccess()) {
            log.warn("Phone verification failed: {}", result.error());
            return AuthResult.failed("verification_failed", result.error());
        }

        Employee employee = createEmployeeFromPhone(phone);
        AuthSession session = createSession(employee, "phone_verification");

        log.info("Phone authentication successful: {}", maskPhone(phone));
        return AuthResult.success(employee, session);
    }

    public Optional<Employee> identifyUser(String method, Map<String, Object> credentials) {
        return switch (method.toLowerCase()) {
            case "oauth" -> {
                String provider = (String) credentials.get("provider");
                String code = (String) credentials.get("code");
                String redirectUri = (String) credentials.get("redirect_uri");
                AuthResult result = authenticateByOAuth(provider, code, redirectUri);
                yield result.success() ? Optional.of(result.employee()) : Optional.empty();
            }
            case "voice" -> {
                byte[] audioData = (byte[]) credentials.get("audio_data");
                AuthResult result = authenticateByVoicePrint(audioData);
                yield result.success() ? Optional.of(result.employee()) : Optional.empty();
            }
            case "phone" -> {
                String phone = (String) credentials.get("phone");
                String code = (String) credentials.get("code");
                AuthResult result = authenticateByPhone(phone, code);
                yield result.success() ? Optional.of(result.employee()) : Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    public Optional<AuthSession> validateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }

        AuthSession session = activeSessions.get(sessionId);
        if (session == null || session.isExpired()) {
            activeSessions.remove(sessionId);
            return Optional.empty();
        }

        session.touch();
        return Optional.of(session);
    }

    public void invalidateSession(String sessionId) {
        AuthSession session = activeSessions.remove(sessionId);
        if (session != null) {
            log.info("Session invalidated: {} for user: {}", sessionId, session.employee().getName());
        }
    }

    public void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("Cleaned up expired sessions");
    }

    private AuthSession createSession(Employee employee, String authMethod) {
        String sessionId = "sess_" + System.currentTimeMillis() + "_" + employee.getEmployeeId().hashCode();
        AuthSession session = new AuthSession(
                sessionId,
                employee,
                authMethod,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of()
        );

        activeSessions.put(sessionId, session);
        return session;
    }

    private Employee createEmployeeFromVoiceMatch(VoicePrintService.VoicePrintMatch match) {
        Employee employee = new Employee();
        employee.setEmployeeId("voice_" + match.userId());
        employee.setName(match.userName() != null ? match.userName() : match.userId());
        employee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        employee.setLastSyncTime(Instant.now());
        employee.setSyncSource("voice_print");
        return employee;
    }

    private Employee createEmployeeFromPhone(String phone) {
        Employee employee = new Employee();
        employee.setEmployeeId("phone_" + phone.hashCode());
        employee.setPhone(phone);
        employee.setName("用户" + phone.substring(phone.length() - 4));
        employee.setIdentity(UserIdentity.EXTERNAL_VISITOR);
        employee.setLastSyncTime(Instant.now());
        employee.setSyncSource("phone_verification");
        return employee;
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
            Employee employee,
            AuthSession session,
            String error,
            String errorDescription
    ) {
        public static AuthResult success(Employee employee, AuthSession session) {
            return new AuthResult(true, employee, session, null, null);
        }
        
        public static AuthResult failed(String error, String description) {
            return new AuthResult(false, null, null, error, description);
        }
    }

    public record AuthSession(
            String sessionId,
            Employee employee,
            String authMethod,
            Instant createdAt,
            Instant expiresAt,
            Map<String, Object> metadata
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public AuthSession touch() {
            return new AuthSession(sessionId, employee, authMethod, createdAt, 
                    Instant.now().plusSeconds(3600), metadata);
        }
    }
}
