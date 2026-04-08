package com.livingagent.core.security.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, String> {

    Optional<UserProfileEntity> findByEmployeeId(String employeeId);

    Optional<UserProfileEntity> findBySpeakerId(String speakerId);

    Optional<UserProfileEntity> findByDigitalId(String digitalId);

    List<UserProfileEntity> findAll();
}
