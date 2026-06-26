package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 合规违规记录持久化实体。
 * 对应 compliance_manager 的 violations 内存列表。
 */
@Entity
@Table(name = "compliance_violations", indexes = {
    @Index(name = "idx_violation_employee_id", columnList = "employee_id"),
    @Index(name = "idx_violation_rule_id", columnList = "rule_id"),
    @Index(name = "idx_violation_status", columnList = "status"),
    @Index(name = "idx_violation_detected_at", columnList = "detected_at")
})
public class ComplianceViolationEntity {

    @Id
    @Column(name = "violation_id", length = 50)
    private String violationId;

    @Column(name = "rule_id", length = 50)
    private String ruleId;

    @Column(name = "rule_name", length = 100)
    private String ruleName;

    @Column(name = "category", length = 32)
    @Enumerated(EnumType.STRING)
    private ViolationCategory category;

    @Column(name = "severity", length = 16)
    @Enumerated(EnumType.STRING)
    private ViolationSeverity severity;

    @Column(name = "employee_id", length = 255)
    private String employeeId;

    @Column(name = "employee_name", length = 255)
    private String employeeName;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "resource", length = 255)
    private String resource;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status", length = 32)
    @Enumerated(EnumType.STRING)
    private ViolationStatus status;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolution", length = 500)
    private String resolution;

    @Column(name = "context", columnDefinition = "jsonb")
    private String contextJson;

    // 枚举定义（与 ComplianceRule 的枚举对应）
    public enum ViolationCategory {
        DATA_PRIVACY, ACCESS_CONTROL, AUDIT_TRAIL, DATA_RETENTION, SECURITY_POLICY, INDUSTRY_REGULATION, INTERNAL_POLICY
    }

    public enum ViolationSeverity {
        INFO(0), LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

        private final int level;

        ViolationSeverity(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    public enum ViolationStatus {
        DETECTED, ACKNOWLEDGED, IN_REVIEW, RESOLVED, FALSE_POSITIVE, ESCALATED
    }

    public ComplianceViolationEntity() {
        this.status = ViolationStatus.DETECTED;
        this.detectedAt = Instant.now();
    }

    // === Getters & Setters ===

    public String getViolationId() { return violationId; }
    public void setViolationId(String violationId) { this.violationId = violationId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public ViolationCategory getCategory() { return category; }
    public void setCategory(ViolationCategory category) { this.category = category; }

    public ViolationSeverity getSeverity() { return severity; }
    public void setSeverity(ViolationSeverity severity) { this.severity = severity; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ViolationStatus getStatus() { return status; }
    public void setStatus(ViolationStatus status) { this.status = status; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }

    public boolean isResolved() {
        return status == ViolationStatus.RESOLVED || status == ViolationStatus.FALSE_POSITIVE;
    }

    public void resolve(String resolvedBy, String resolution) {
        this.status = ViolationStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        this.resolution = resolution;
    }

    @Override
    public String toString() {
        return String.format("ComplianceViolationEntity{id=%s, rule=%s, severity=%s, status=%s}",
            violationId, ruleName, severity, status);
    }
}