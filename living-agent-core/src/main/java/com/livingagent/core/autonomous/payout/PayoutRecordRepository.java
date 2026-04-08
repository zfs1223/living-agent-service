package com.livingagent.core.autonomous.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutRecordRepository extends JpaRepository<PayoutRecord, String> {

    List<PayoutRecord> findByOwnerId(String ownerId);

    List<PayoutRecord> findByStatus(String status);

    List<PayoutRecord> findByAccountId(String accountId);

    @Query("SELECT p FROM PayoutRecord p WHERE p.ownerId = :ownerId ORDER BY p.createdAt DESC")
    List<PayoutRecord> findByOwnerIdOrderByCreatedAtDesc(@Param("ownerId") String ownerId);

    @Query("SELECT SUM(p.amount) FROM PayoutRecord p WHERE p.ownerId = :ownerId AND p.status = :status")
    BigDecimal sumAmountByOwnerAndStatus(@Param("ownerId") String ownerId, @Param("status") String status);

    @Query("SELECT p FROM PayoutRecord p WHERE p.status = 'PENDING'")
    List<PayoutRecord> findPendingPayouts();

    @Query("SELECT COUNT(p) FROM PayoutRecord p WHERE p.ownerId = :ownerId AND p.createdAt >= :from AND p.createdAt < :to")
    int countByOwnerAndDateRange(@Param("ownerId") String ownerId, @Param("from") String from, @Param("to") String to);

    @Query("SELECT SUM(p.amount) FROM PayoutRecord p WHERE p.ownerId = :ownerId AND p.status = 'COMPLETED' AND p.completedAt >= :from AND p.completedAt < :to")
    BigDecimal sumCompletedByOwnerAndDateRange(@Param("ownerId") String ownerId, @Param("from") String from, @Param("to") String to);
}
