package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LedgerService 持久化 Repository
 */
@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, Long> {

    Optional<LedgerTransactionEntity> findByTransactionId(String transactionId);

    List<LedgerTransactionEntity> findByEmployeeIdOrderByCreatedAtDesc(String employeeId);

    /**
     * 获取员工当前余额（最新的 balance_after）
     */
    @Query("SELECT t.balanceAfter FROM LedgerTransactionEntity t " +
           "WHERE t.employeeId = :employeeId AND t.status = 'RECEIVED' " +
           "ORDER BY t.createdAt DESC LIMIT 1")
    Optional<Integer> findLatestBalance(@Param("employeeId") String employeeId);

    /**
     * 计算员工总收入（所有 RECEIVED 的正数金额之和）
     */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM LedgerTransactionEntity t " +
           "WHERE t.employeeId = :employeeId AND t.status = 'RECEIVED' AND t.amountCents > 0")
    Integer getTotalEarned(@Param("employeeId") String employeeId);

    /**
     * 获取员工收入历史（指定数量）
     */
    @Query("SELECT t FROM LedgerTransactionEntity t " +
           "WHERE t.employeeId = :employeeId " +
           "ORDER BY t.createdAt DESC LIMIT :limit")
    List<LedgerTransactionEntity> findIncomeHistory(@Param("employeeId") String employeeId, @Param("limit") int limit);

    /**
     * 获取员工指定类型的记录
     */
    List<LedgerTransactionEntity> findByEmployeeIdAndSourceTypeOrderByCreatedAtDesc(
        String employeeId, String sourceType);
}