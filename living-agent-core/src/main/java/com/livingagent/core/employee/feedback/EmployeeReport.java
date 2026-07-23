package com.livingagent.core.employee.feedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 闭环65: 员工汇报数据模型。
 * 人类员工周期性汇报个人表现和改进建议，固定数字员工自动产出。
 *
 * DBS People 工具区分：
 * - origin=HUMAN → 需周期性汇报（每日/每周/每月），个人+管理可见
 * - origin=FIXED → 自动产出，无需手动汇报
 * - origin=PERSONAL → 允许直连，可选汇报
 */
public class EmployeeReport {

    private String reportId;
    private String employeeId;
    private String employeeName;
    private EmployeeOrigin origin;
    private ReportType reportType;
    private ReportPeriod period;
    private Instant periodStart;
    private Instant periodEnd;
    private Instant submittedAt;

    /** 工作完成情况 */
    private String workSummary;
    /** 关键成果 */
    private List<String> keyAchievements = new ArrayList<>();
    /** 遇到问题 */
    private List<String> challenges = new ArrayList<>();
    /** 改进建议 */
    private List<String> improvementSuggestions = new ArrayList<>();
    /** 下周期计划 */
    private String nextPeriodPlan;

    /** 可见性控制：个人可见 + 管理可见 */
    private Visibility visibility = Visibility.SELF_AND_MANAGER;
    /** 是否同步发送给管理者 */
    private boolean notifyManager = true;

    /** 附加元数据 */
    private Map<String, Object> metadata = new HashMap<>();

    public enum EmployeeOrigin {
        HUMAN, FIXED, PERSONAL
    }

    public enum ReportType {
        DAILY, WEEKLY, MONTHLY, AD_HOC
    }

    public enum ReportPeriod {
        DAILY, WEEKLY, MONTHLY, QUARTERLY
    }

    public enum Visibility {
        SELF_ONLY,           // 仅个人可见
        SELF_AND_MANAGER,    // 个人+直属管理可见
        SELF_AND_TEAM,       // 个人+团队可见
        ORG_WIDE             // 组织公开
    }

    public EmployeeReport() {
        this.reportId = java.util.UUID.randomUUID().toString();
        this.submittedAt = Instant.now();
    }

    public EmployeeReport(String employeeId, String employeeName, EmployeeOrigin origin, ReportType reportType) {
        this();
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.origin = origin;
        this.reportType = reportType;
    }

    public boolean isHumanReport() {
        return origin == EmployeeOrigin.HUMAN;
    }

    public boolean isDigitalReport() {
        return origin == EmployeeOrigin.FIXED || origin == EmployeeOrigin.PERSONAL;
    }

    // === Getters & Setters ===

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public EmployeeOrigin getOrigin() { return origin; }
    public void setOrigin(EmployeeOrigin origin) { this.origin = origin; }

    public ReportType getReportType() { return reportType; }
    public void setReportType(ReportType reportType) { this.reportType = reportType; }

    public ReportPeriod getPeriod() { return period; }
    public void setPeriod(ReportPeriod period) { this.period = period; }

    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }

    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public String getWorkSummary() { return workSummary; }
    public void setWorkSummary(String workSummary) { this.workSummary = workSummary; }

    public List<String> getKeyAchievements() { return keyAchievements; }
    public void setKeyAchievements(List<String> keyAchievements) { this.keyAchievements = keyAchievements; }

    public List<String> getChallenges() { return challenges; }
    public void setChallenges(List<String> challenges) { this.challenges = challenges; }

    public List<String> getImprovementSuggestions() { return improvementSuggestions; }
    public void setImprovementSuggestions(List<String> improvementSuggestions) { this.improvementSuggestions = improvementSuggestions; }

    public String getNextPeriodPlan() { return nextPeriodPlan; }
    public void setNextPeriodPlan(String nextPeriodPlan) { this.nextPeriodPlan = nextPeriodPlan; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public boolean isNotifyManager() { return notifyManager; }
    public void setNotifyManager(boolean notifyManager) { this.notifyManager = notifyManager; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return String.format("EmployeeReport{id=%s, employee=%s, origin=%s, type=%s, submitted=%s}",
            reportId, employeeName, origin, reportType, submittedAt);
    }
}
