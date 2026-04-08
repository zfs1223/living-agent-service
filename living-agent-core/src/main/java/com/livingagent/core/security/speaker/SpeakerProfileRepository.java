package com.livingagent.core.security.speaker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpeakerProfileRepository extends JpaRepository<SpeakerProfile, String> {

    Optional<SpeakerProfile> findByName(String name);

    Optional<SpeakerProfile> findByEmployeeId(String employeeId);

    List<SpeakerProfile> findByActiveTrue();

    @Query("SELECT s FROM SpeakerProfile s WHERE s.employeeId = :empId AND s.active = true")
    Optional<SpeakerProfile> findActiveByEmployeeId(@Param("empId") String employeeId);

    boolean existsByEmployeeId(String employeeId);

    @Query("SELECT COUNT(s) FROM SpeakerProfile s WHERE s.active = true")
    int countActiveProfiles();
}
