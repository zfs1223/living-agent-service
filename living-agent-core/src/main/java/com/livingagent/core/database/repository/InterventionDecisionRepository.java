package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.InterventionDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface InterventionDecisionRepository extends JpaRepository<InterventionDecisionEntity, Long> {

    InterventionDecisionEntity findByDecisionId(String decisionId);

    List<InterventionDecisionEntity> findByStatusIn(List<String> statuses);

    List<InterventionDecisionEntity> findByDepartment(String department);

    List<InterventionDecisionEntity> findByStatusInAndDepartment(List<String> statuses, String department);

    List<InterventionDecisionEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since);
}
