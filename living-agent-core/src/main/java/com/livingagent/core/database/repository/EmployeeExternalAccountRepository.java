package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.EmployeeExternalAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 员工外部账号映射仓库
 */
@Repository
public interface EmployeeExternalAccountRepository extends JpaRepository<EmployeeExternalAccountEntity, Long> {

    Optional<EmployeeExternalAccountEntity> findByEmployeeCodeAndServiceTypeAndActiveTrue(
        String employeeCode, String serviceType);

    List<EmployeeExternalAccountEntity> findByServiceTypeAndActiveTrue(String serviceType);

    List<EmployeeExternalAccountEntity> findByEmployeeCodeAndActiveTrue(String employeeCode);
}
