package com.livingagent.core.security.session;

import java.time.Instant;
import java.util.Optional;

public interface SessionManager {

    Session createSession(SessionCreateRequest request);
    
    Optional<Session> getSession(String sessionId);
    
    Optional<Session> getActiveSessionByEmployee(String employeeId);
    
    Optional<Session> getActiveSessionBySpeaker(String speakerId);
    
    Session refreshSession(String sessionId);
    
    void endSession(String sessionId, String reason);
    
    void endAllSessionsForEmployee(String employeeId, String reason);
    
    void updateActivity(String sessionId);
    
    void cleanupExpiredSessions();
    
    long getActiveSessionCount();
    
    enum AuthMethod {
        PASSWORD,
        OAUTH_DINGTALK,
        OAUTH_FEISHU,
        OAUTH_WEWORK,
        VOICEPRINT,
        SYSTEM
    }
    
    enum SessionStatus {
        ACTIVE,
        EXPIRED,
        ENDED,
        TERMINATED
    }
    
    record SessionCreateRequest(
        String employeeId,
        String speakerId,
        String identity,
        String accessLevel,
        AuthMethod authMethod,
        String ipAddress,
        String userAgent,
        String deviceInfo,
        String locationInfo,
        long ttlSeconds
    ) {}
    
    record Session(
        String sessionId,
        String employeeId,
        String speakerId,
        String identity,
        String accessLevel,
        AuthMethod authMethod,
        String ipAddress,
        String userAgent,
        String deviceInfo,
        String locationInfo,
        Instant startedAt,
        Instant lastActivityAt,
        Instant expiresAt,
        Instant endedAt,
        String endReason,
        SessionStatus status
    ) {}
}
