package com.livingagent.core.security.session.impl;

import com.livingagent.core.security.session.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SessionManagerImpl implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerImpl.class);
    private static final long DEFAULT_TTL_SECONDS = 3600 * 8;

    private final SessionRepository sessionRepository;

    public SessionManagerImpl(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Session createSession(SessionCreateRequest request) {
        String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(sessionId);
        entity.setEmployeeId(request.employeeId());
        entity.setSpeakerId(request.speakerId());
        entity.setIdentity(request.identity());
        entity.setAccessLevel(request.accessLevel());
        entity.setAuthMethod(request.authMethod() != null ? request.authMethod().name() : null);
        entity.setIpAddress(request.ipAddress());
        entity.setUserAgent(request.userAgent());
        entity.setDeviceInfo(request.deviceInfo());
        entity.setLocationInfo(request.locationInfo());
        entity.setStartedAt(Instant.now());
        entity.setLastActivityAt(Instant.now());
        
        long ttl = request.ttlSeconds() > 0 ? request.ttlSeconds() : DEFAULT_TTL_SECONDS;
        entity.setExpiresAt(Instant.now().plusSeconds(ttl));
        entity.setActive(true);
        
        sessionRepository.save(entity);
        
        log.info("Created session {} for employee: {}, speaker: {}", sessionId, request.employeeId(), request.speakerId());
        
        return toSession(entity);
    }

    @Override
    public Optional<Session> getSession(String sessionId) {
        return sessionRepository.findBySessionIdAndActiveTrue(sessionId)
                .map(this::toSession);
    }

    @Override
    public Optional<Session> getActiveSessionByEmployee(String employeeId) {
        return sessionRepository.findByEmployeeIdAndActiveTrue(employeeId)
                .map(this::toSession);
    }

    @Override
    public Optional<Session> getActiveSessionBySpeaker(String speakerId) {
        return sessionRepository.findBySpeakerIdAndActiveTrue(speakerId)
                .map(this::toSession);
    }

    @Override
    public Session refreshSession(String sessionId) {
        SessionEntity entity = sessionRepository.findBySessionIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now())) {
            entity.setActive(false);
            entity.setEndedAt(Instant.now());
            entity.setEndReason(SessionStatus.EXPIRED.name());
            sessionRepository.save(entity);
            throw new IllegalStateException("Session expired: " + sessionId);
        }
        
        entity.setLastActivityAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(DEFAULT_TTL_SECONDS));
        sessionRepository.save(entity);
        
        log.debug("Refreshed session: {}", sessionId);
        return toSession(entity);
    }

    @Override
    public void endSession(String sessionId, String reason) {
        sessionRepository.findById(sessionId).ifPresent(entity -> {
            if (entity.isActive()) {
                entity.setActive(false);
                entity.setEndedAt(Instant.now());
                entity.setEndReason(reason != null ? reason : SessionStatus.ENDED.name());
                sessionRepository.save(entity);
                log.info("Ended session: {} with reason: {}", sessionId, reason);
            }
        });
    }

    @Override
    public void endAllSessionsForEmployee(String employeeId, String reason) {
        sessionRepository.findByEmployeeIdAndActiveTrueOrderByStartedAtDesc(employeeId)
                .forEach(entity -> {
                    entity.setActive(false);
                    entity.setEndedAt(Instant.now());
                    entity.setEndReason(reason != null ? reason : SessionStatus.TERMINATED.name());
                    sessionRepository.save(entity);
                });
        log.info("Ended all sessions for employee: {} with reason: {}", employeeId, reason);
    }

    @Override
    public void updateActivity(String sessionId) {
        sessionRepository.findBySessionIdAndActiveTrue(sessionId).ifPresent(entity -> {
            entity.setLastActivityAt(Instant.now());
            sessionRepository.save(entity);
        });
    }

    @Override
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessionRepository.findByExpiresAtBeforeAndActiveTrue(now)
                .forEach(entity -> {
                    entity.setActive(false);
                    entity.setEndedAt(now);
                    entity.setEndReason(SessionStatus.EXPIRED.name());
                    sessionRepository.save(entity);
                    log.debug("Cleaned up expired session: {}", entity.getSessionId());
                });
    }

    @Override
    public long getActiveSessionCount() {
        return sessionRepository.countByActiveTrue();
    }

    private Session toSession(SessionEntity entity) {
        return new Session(
            entity.getSessionId(),
            entity.getEmployeeId(),
            entity.getSpeakerId(),
            entity.getIdentity(),
            entity.getAccessLevel(),
            entity.getAuthMethod() != null ? AuthMethod.valueOf(entity.getAuthMethod()) : null,
            entity.getIpAddress(),
            entity.getUserAgent(),
            entity.getDeviceInfo(),
            entity.getLocationInfo(),
            entity.getStartedAt(),
            entity.getLastActivityAt(),
            entity.getExpiresAt(),
            entity.getEndedAt(),
            entity.getEndReason(),
            entity.isActive() ? SessionStatus.ACTIVE : 
                (entity.getEndReason() != null ? SessionStatus.valueOf(entity.getEndReason()) : SessionStatus.ENDED)
        );
    }
}
