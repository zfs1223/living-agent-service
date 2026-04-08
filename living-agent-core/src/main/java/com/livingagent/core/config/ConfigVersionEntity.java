package com.livingagent.core.config;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "config_versions")
public class ConfigVersionEntity {

    @Id
    @Column(name = "version_id", length = 64)
    private String versionId;

    @Column(name = "config_type", length = 32, nullable = false)
    private String configType;

    @Column(name = "config_key", length = 128, nullable = false)
    private String configKey;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "config_value", columnDefinition = "JSONB", nullable = false)
    private String configValue;

    @Column(name = "previous_value", columnDefinition = "JSONB")
    private String previousValue;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "is_active")
    private boolean isActive;

    @PrePersist
    public void prePersist() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }

    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
