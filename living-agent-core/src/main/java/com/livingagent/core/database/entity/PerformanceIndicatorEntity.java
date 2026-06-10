package com.livingagent.core.database.entity;

import com.livingagent.core.operation.performance.PerformanceIndicator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "performance_indicators", indexes = {
        @Index(name = "idx_performance_indicator_id", columnList = "indicator_id"),
        @Index(name = "idx_performance_indicator_category", columnList = "category"),
        @Index(name = "idx_performance_indicator_enabled", columnList = "enabled")
})
public class PerformanceIndicatorEntity {

    @jakarta.persistence.Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "indicator_id", unique = true, length = 64, nullable = false)
    private String indicatorId;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "target_value")
    private Double targetValue;

    @Column(name = "calculation_method", length = 128)
    private String calculationMethod;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public PerformanceIndicatorEntity() {
        this.id = UUID.randomUUID();
        this.enabled = true;
        this.weight = 1.0;
        this.targetValue = 0.0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getIndicatorId() { return indicatorId; }
    public void setIndicatorId(String indicatorId) { this.indicatorId = indicatorId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public Double getTargetValue() { return targetValue; }
    public void setTargetValue(Double targetValue) { this.targetValue = targetValue; }

    public String getCalculationMethod() { return calculationMethod; }
    public void setCalculationMethod(String calculationMethod) { this.calculationMethod = calculationMethod; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static PerformanceIndicatorEntity fromDefinition(String indicatorId,
                                                            String name,
                                                            String description,
                                                            PerformanceIndicator.IndicatorCategory category,
                                                            double weight,
                                                            double targetValue,
                                                            String calculationMethod) {
        PerformanceIndicatorEntity entity = new PerformanceIndicatorEntity();
        entity.setIndicatorId(indicatorId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setCategory(category != null ? category.name() : null);
        entity.setWeight(weight);
        entity.setTargetValue(targetValue);
        entity.setCalculationMethod(calculationMethod);
        return entity;
    }
}
