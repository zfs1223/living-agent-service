package com.livingagent.core.compliance;

import java.time.Instant;
import java.util.*;

public class ComplianceViolation {

    private String violationId;
    private String ruleId;
    private String ruleName;
    private ComplianceRule.RuleCategory category;
    private ComplianceRule.RuleSeverity severity;
    private String employeeId;
    private String employeeName;
    private String department;
    private String resource;
    private String action;
    private String description;
    private ViolationStatus status;
    private Instant detectedAt;
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolution;
    private Map<String, Object> context;

    public enum ViolationStatus {
        DETECTED("已检测"),
        ACKNOWLEDGED("已确认"),
        IN_REVIEW("审查中"),
        RESOLVED("已解决"),
        FALSE_POSITIVE("误报"),
        ESCALATED("已升级");

        private final String displayName;

        ViolationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public ComplianceViolation() {
        this.violationId = "violation_" + UUID.randomUUID().toString().substring(0, 8);
        this.status = ViolationStatus.DETECTED;
        this.detectedAt = Instant.now();
        this.context = new HashMap<>();
    }

    public ComplianceViolation(ComplianceRule rule, String employeeId, String resource, String action) {
        this();
        this.ruleId = rule.getRuleId();
        this.ruleName = rule.getName();
        this.category = rule.getCategory();
        this.severity = rule.getSeverity();
        this.employeeId = employeeId;
        this.resource = resource;
        this.action = action;
    }

    public String getViolationId() { return violationId; }
    public void setViolationId(String violationId) { this.violationId = violationId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public ComplianceRule.RuleCategory getCategory() { return category; }
    public void setCategory(ComplianceRule.RuleCategory category) { this.category = category; }

    public ComplianceRule.RuleSeverity getSeverity() { return severity; }
    public void setSeverity(ComplianceRule.RuleSeverity severity) { this.severity = severity; }

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

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public void resolve(String resolvedBy, String resolution) {
        this.status = ViolationStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        this.resolution = resolution;
    }

    public void acknowledge() {
        this.status = ViolationStatus.ACKNOWLEDGED;
    }

    public void escalate() {
        this.status = ViolationStatus.ESCALATED;
    }

    public boolean isResolved() {
        return status == ViolationStatus.RESOLVED || status == ViolationStatus.FALSE_POSITIVE;
    }

    @Override
    public String toString() {
        return String.format("ComplianceViolation{id=%s, rule=%s, severity=%s, status=%s}",
            violationId, ruleName, severity, status);
    }
}
