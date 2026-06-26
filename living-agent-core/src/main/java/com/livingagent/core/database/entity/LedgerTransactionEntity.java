package com.livingagent.core.database.entity;

import com.livingagent.core.autonomous.bounty.LedgerService;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * LedgerService 持久化实体
 * 用于存储员工余额、收入记录、奖励记录
 */
@Entity
@Table(name = "ledger_transaction", indexes = {
    @Index(name = "idx_ledger_employee", columnList = "employeeId,createdAt"),
    @Index(name = "idx_ledger_source", columnList = "sourceType,sourceId"),
    @Index(name = "idx_ledger_status", columnList = "status")
})
public class LedgerTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
    private String transactionId;

    @Column(name = "employee_id", nullable = false, length = 128)
    private String employeeId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;  // INCOME / REWARD / DEBIT / ACHIEVEMENT / PENDING_INCOME

    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "status", nullable = false, length = 16)
    private String status;  // RECEIVED / PENDING

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA 要求的无参构造器
    public LedgerTransactionEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // 业务构造器
    public LedgerTransactionEntity(String transactionId, String employeeId, String sourceType,
                                   String sourceId, Integer amountCents, Integer balanceAfter,
                                   String status, String description) {
        this.transactionId = transactionId;
        this.employeeId = employeeId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.amountCents = amountCents;
        this.balanceAfter = balanceAfter;
        this.status = status;
        this.description = description;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }

    public Integer getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Integer balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // 转换为 LedgerService.IncomeRecord
    public LedgerService.IncomeRecord toIncomeRecord() {
        return new LedgerService.IncomeRecord(
            transactionId,
            employeeId,
            sourceType,
            sourceId,
            amountCents,
            status,
            createdAt,
            status.equals("RECEIVED") ? createdAt : null
        );
    }
}