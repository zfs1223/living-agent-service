package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.MigrationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MigrationRecordRepository extends JpaRepository<MigrationRecordEntity, Long> {

    Optional<MigrationRecordEntity> findByMigrationId(String migrationId);

    List<MigrationRecordEntity> findAllByOrderByStartedAtDesc();

    List<MigrationRecordEntity> findTop20ByOrderByStartedAtDesc();
}
