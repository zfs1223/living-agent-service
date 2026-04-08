package com.livingagent.core.budget;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "budget_allocations")
public class BudgetAllocationEntity {

    @Id
    @Column(name = "allocation_id", length = 64)
    private String allocationId;

    @Column(name = "budget_type", length = 32, nullable = false)
    private String budgetType;

    @Column(name = "owner_id", length = 100)
    private String ownerId;

    @Column(name = "owner_type", length = 32)
    private String ownerType;

    @Column(name = "period", length = 16, nullable = false)
    private String period;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "allocated_amount_cents", nullable = false)
    private long allocatedAmountCents;

    @Column(name = "used_amount_cents")
    private long usedAmountCents;

    @Column(name = "reserved_amount_cents")
    private long reservedAmountCents;

    @Column(name = "alert_threshold")
    private double alertThreshold;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    public String getAllocationId() { return allocationId; }
    public void setAllocationId(String allocationId) { this.allocationId = allocationId; }

    public String getBudgetType() { return budgetType; }
    public void setBudgetType(String budgetType) { this.budgetType = budgetType; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public long getAllocatedAmountCents() { return allocatedAmountCents; }
    public void setAllocatedAmountCents(long allocatedAmountCents) { this.allocatedAmountCents = allocatedAmountCents; }

    public long getUsedAmountCents() { return usedAmountCents; }
    public void setUsedAmountCents(long usedAmountCents) { this.usedAmountCents = usedAmountCents; }

    public long getReservedAmountCents() { return reservedAmountCents; }
    public void setReservedAmountCents(long reservedAmountCents) { this.reservedAmountCents = reservedAmountCents; }

    public double getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(double alertThreshold) { this.alertThreshold = alertThreshold; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
