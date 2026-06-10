package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fixed_employee_profile")
public class FixedEmployeeProfileEntity {
    @Id
    @Column(name = "code", length = 16)
    private String code;

    @Column(name = "employee_id", length = 36, unique = true)
    private String employeeId;

    @Column(name = "display_name_zh", length = 100, nullable = false)
    private String displayNameZh;

    @Column(name = "display_name_en", length = 100)
    private String displayNameEn;

    @Column(name = "summary_zh", columnDefinition = "TEXT")
    private String summaryZh;

    @Column(name = "summary_en", columnDefinition = "TEXT")
    private String summaryEn;

    @Column(name = "traits", columnDefinition = "jsonb")
    private String traits;

    @Column(name = "tool_tags", columnDefinition = "jsonb")
    private String toolTags;

    @Column(name = "long_term_memory", columnDefinition = "jsonb")
    private String longTermMemory;

    @Column(name = "preferences", columnDefinition = "jsonb")
    private String preferences;

    @Column(name = "current_task", columnDefinition = "TEXT")
    private String currentTask;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public FixedEmployeeProfileEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "active";
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getDisplayNameZh() { return displayNameZh; }
    public void setDisplayNameZh(String displayNameZh) { this.displayNameZh = displayNameZh; }
    public String getDisplayNameEn() { return displayNameEn; }
    public void setDisplayNameEn(String displayNameEn) { this.displayNameEn = displayNameEn; }
    public String getSummaryZh() { return summaryZh; }
    public void setSummaryZh(String summaryZh) { this.summaryZh = summaryZh; }
    public String getSummaryEn() { return summaryEn; }
    public void setSummaryEn(String summaryEn) { this.summaryEn = summaryEn; }
    public String getTraits() { return traits; }
    public void setTraits(String traits) { this.traits = traits; }
    public String getToolTags() { return toolTags; }
    public void setToolTags(String toolTags) { this.toolTags = toolTags; }
    public String getLongTermMemory() { return longTermMemory; }
    public void setLongTermMemory(String longTermMemory) { this.longTermMemory = longTermMemory; }
    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }
    public String getCurrentTask() { return currentTask; }
    public void setCurrentTask(String currentTask) { this.currentTask = currentTask; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
