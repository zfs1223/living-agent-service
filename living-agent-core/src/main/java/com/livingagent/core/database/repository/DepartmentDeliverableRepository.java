package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.DepartmentDeliverableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentDeliverableRepository extends JpaRepository<DepartmentDeliverableEntity, Long> {

    Optional<DepartmentDeliverableEntity> findByDeliverableId(String deliverableId);
    List<DepartmentDeliverableEntity> findByDepartmentOrderByDeliveredAtDesc(String department);
    List<DepartmentDeliverableEntity> findByPlanIdOrderByDeliveredAtDesc(String planId);
}
