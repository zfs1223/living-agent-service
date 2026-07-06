package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.VisitorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisitorRepository extends JpaRepository<VisitorEntity, Long> {
    List<VisitorEntity> findByStatusInOrderByCheckInTimeDesc(List<String> statuses);
    List<VisitorEntity> findByOrderByCheckInTimeDesc();
    Optional<VisitorEntity> findByVisitorId(String visitorId);
    long countByStatus(String status);
}
