package com.livingagent.core.security.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    
    Optional<SessionEntity> findBySessionIdAndActiveTrue(String sessionId);
    
    Optional<SessionEntity> findByEmployeeIdAndActiveTrue(String employeeId);
    
    Optional<SessionEntity> findBySpeakerIdAndActiveTrue(String speakerId);
    
    List<SessionEntity> findByActiveTrue();
    
    List<SessionEntity> findByExpiresAtBeforeAndActiveTrue(Instant expiresAt);
    
    List<SessionEntity> findByEmployeeIdAndActiveTrueOrderByStartedAtDesc(String employeeId);
    
    long countByActiveTrue();
}
