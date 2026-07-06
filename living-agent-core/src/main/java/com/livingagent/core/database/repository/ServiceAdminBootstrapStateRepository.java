package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ServiceAdminBootstrapStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 服务初始化状态仓库
 */
@Repository
public interface ServiceAdminBootstrapStateRepository extends JpaRepository<ServiceAdminBootstrapStateEntity, Long> {

    Optional<ServiceAdminBootstrapStateEntity> findByServiceTypeAndStepName(
        String serviceType, String stepName);

    List<ServiceAdminBootstrapStateEntity> findByServiceType(String serviceType);

    List<ServiceAdminBootstrapStateEntity> findByStatus(String status);
}
