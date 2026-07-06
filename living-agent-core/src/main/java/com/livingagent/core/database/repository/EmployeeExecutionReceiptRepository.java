package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.EmployeeExecutionReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeExecutionReceiptRepository extends JpaRepository<EmployeeExecutionReceiptEntity, Long> {

    Optional<EmployeeExecutionReceiptEntity> findByReceiptId(String receiptId);

    List<EmployeeExecutionReceiptEntity> findByExecutionId(String executionId);

    List<EmployeeExecutionReceiptEntity> findByExecutionIdAndStatus(String executionId, String status);

    List<EmployeeExecutionReceiptEntity> findByEmployeeCode(String employeeCode);

    List<EmployeeExecutionReceiptEntity> findByDispatchId(String dispatchId);

    List<EmployeeExecutionReceiptEntity> findByAssignmentId(String assignmentId);

    List<EmployeeExecutionReceiptEntity> findByDepartment(String department);

    long countByExecutionId(String executionId);

    long countByExecutionIdAndStatus(String executionId, String status);

    boolean existsByReceiptId(String receiptId);
}
