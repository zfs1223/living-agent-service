package com.livingagent.core.compliance;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ComplianceReport {

    private String reportId;
    private Instant generatedAt;
    private Instant periodStart;
    private Instant periodEnd;
    
    private int totalAuditLogs;
    private int totalViolations;
    private int resolvedViolations;
    private int openViolations;
    private double complianceScore;
    
    private Map<ComplianceRule.RuleSeverity, Integer> violationsBySeverity;
    private Map<ComplianceRule.RuleCategory, Integer> violationsByCategory;
    private Map<String, Integer> violationsByDepartment;
    
    private String summary;
    private String recommendation;

    public ComplianceReport() {
        this.reportId = "report_" + System.currentTimeMillis();
        this.generatedAt = Instant.now();
        this.violationsBySeverity = new HashMap<>();
        this.violationsByCategory = new HashMap<>();
        this.violationsByDepartment = new HashMap<>();
    }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    
    public void setReportPeriod(Instant start, Instant end) {
        this.periodStart = start;
        this.periodEnd = end;
    }

    public int getTotalAuditLogs() { return totalAuditLogs; }
    public void setTotalAuditLogs(int totalAuditLogs) { this.totalAuditLogs = totalAuditLogs; }

    public int getTotalViolations() { return totalViolations; }
    public void setTotalViolations(int totalViolations) { this.totalViolations = totalViolations; }

    public int getResolvedViolations() { return resolvedViolations; }
    public void setResolvedViolations(int resolvedViolations) { this.resolvedViolations = resolvedViolations; }

    public int getOpenViolations() { return openViolations; }
    public void setOpenViolations(int openViolations) { this.openViolations = openViolations; }

    public double getComplianceScore() { return complianceScore; }
    public void setComplianceScore(double complianceScore) { this.complianceScore = complianceScore; }

    public Map<ComplianceRule.RuleSeverity, Integer> getViolationsBySeverity() { return violationsBySeverity; }
    public void setViolationsBySeverity(Map<ComplianceRule.RuleSeverity, Integer> violationsBySeverity) { 
        this.violationsBySeverity = violationsBySeverity; 
    }

    public Map<ComplianceRule.RuleCategory, Integer> getViolationsByCategory() { return violationsByCategory; }
    public void setViolationsByCategory(Map<ComplianceRule.RuleCategory, Integer> violationsByCategory) { 
        this.violationsByCategory = violationsByCategory; 
    }

    public Map<String, Integer> getViolationsByDepartment() { return violationsByDepartment; }
    public void setViolationsByDepartment(Map<String, Integer> violationsByDepartment) { 
        this.violationsByDepartment = violationsByDepartment; 
    }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getGrade() {
        if (complianceScore >= 95) return "A+";
        if (complianceScore >= 90) return "A";
        if (complianceScore >= 85) return "B+";
        if (complianceScore >= 80) return "B";
        if (complianceScore >= 70) return "C";
        if (complianceScore >= 60) return "D";
        return "F";
    }

    public boolean isCompliant() {
        return complianceScore >= 80;
    }

    public double getResolutionRate() {
        if (totalViolations == 0) return 100.0;
        return (resolvedViolations * 100.0) / totalViolations;
    }

    @Override
    public String toString() {
        return String.format("ComplianceReport{id=%s, score=%.1f, grade=%s, violations=%d}",
            reportId, complianceScore, getGrade(), totalViolations);
    }
}
