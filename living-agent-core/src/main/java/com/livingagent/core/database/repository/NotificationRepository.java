package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByDepartmentOrderByTimestampDesc(String department);

    List<NotificationEntity> findByDepartmentAndReadFalseOrderByTimestampDesc(String department);

    Optional<NotificationEntity> findByNotificationId(String notificationId);

    void deleteByDepartment(String department);

    long countByDepartmentAndReadFalse(String department);

    long countByReadFalse();
}
