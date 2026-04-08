package com.livingagent.core.compliance;

import java.time.Instant;
import java.util.*;

public class ComplianceRule {

    private String ruleId;
    private String name;
    private String description;
    private RuleCategory category;
    private RuleSeverity severity;
    private String department;
    private List<String> applicableRoles;
    private String condition;
    private String violationMessage;
    private String remediation;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public enum RuleCategory {
        DATA_PRIVACY("数据隐私"),
        ACCESS_CONTROL("访问控制"),
        AUDIT_TRAIL("审计追踪"),
        DATA_RETENTION("数据保留"),
        SECURITY_POLICY("安全策略"),
        INDUSTRY_REGULATION("行业法规"),
        INTERNAL_POLICY("内部政策");

        private final String displayName;

        RuleCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public enum RuleSeverity {
        CRITICAL(4, "严重"),
        HIGH(3, "高"),
        MEDIUM(2, "中"),
        LOW(1, "低"),
        INFO(0, "信息");

        private final int level;
        private final String displayName;

        RuleSeverity(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
    }

    public ComplianceRule() {
        this.ruleId = "rule_" + UUID.randomUUID().toString().substring(0, 8);
        this.enabled = true;
        this.applicableRoles = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ComplianceRule(String name, RuleCategory category, RuleSeverity severity) {
        this();
        this.name = name;
        this.category = category;
        this.severity = severity;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public RuleCategory getCategory() { return category; }
    public void setCategory(RuleCategory category) { this.category = category; }

    public RuleSeverity getSeverity() { return severity; }
    public void setSeverity(RuleSeverity severity) { this.severity = severity; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public List<String> getApplicableRoles() { return applicableRoles; }
    public void setApplicableRoles(List<String> applicableRoles) { this.applicableRoles = applicableRoles; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getViolationMessage() { return violationMessage; }
    public void setViolationMessage(String violationMessage) { this.violationMessage = violationMessage; }

    public String getRemediation() { return remediation; }
    public void setRemediation(String remediation) { this.remediation = remediation; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isApplicableTo(String department, String role) {
        if (!enabled) return false;
        
        if (this.department != null && !this.department.equals(department)) {
            return false;
        }
        
        if (applicableRoles != null && !applicableRoles.isEmpty()) {
            return applicableRoles.contains(role) || applicableRoles.contains("*");
        }
        
        return true;
    }

    @Override
    public String toString() {
        return String.format("ComplianceRule{id=%s, name=%s, category=%s, severity=%s}",
            ruleId, name, category, severity);
    }
}
