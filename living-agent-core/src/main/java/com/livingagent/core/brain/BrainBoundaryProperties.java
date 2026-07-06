package com.livingagent.core.brain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConfigurationProperties(prefix = "brain.boundary")
public class BrainBoundaryProperties {

    private boolean enabled = true;
    private int consecutiveFailuresThreshold = 5;
    private boolean auditLogEnabled = true;
    private String auditLogLevel = "warn";
    private boolean autoEscalateToMainBrain = true;
    private List<BoundaryDefinition> boundaries = new ArrayList<>();

    public static class BoundaryDefinition {
        private String brainId;
        private String department;
        private List<String> allowedActions = new ArrayList<>();
        private List<String> forbiddenActions = new ArrayList<>();
        private List<String> escalationTriggers = new ArrayList<>();
        private List<String> mustEscalateScenarios = new ArrayList<>();

        public String getBrainId() { return brainId; }
        public void setBrainId(String brainId) { this.brainId = brainId; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public List<String> getAllowedActions() { return allowedActions; }
        public void setAllowedActions(List<String> allowedActions) { this.allowedActions = allowedActions; }

        public List<String> getForbiddenActions() { return forbiddenActions; }
        public void setForbiddenActions(List<String> forbiddenActions) { this.forbiddenActions = forbiddenActions; }

        public List<String> getEscalationTriggers() { return escalationTriggers; }
        public void setEscalationTriggers(List<String> escalationTriggers) { this.escalationTriggers = escalationTriggers; }

        public List<String> getMustEscalateScenarios() { return mustEscalateScenarios; }
        public void setMustEscalateScenarios(List<String> mustEscalateScenarios) { this.mustEscalateScenarios = mustEscalateScenarios; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getConsecutiveFailuresThreshold() { return consecutiveFailuresThreshold; }
    public void setConsecutiveFailuresThreshold(int threshold) { this.consecutiveFailuresThreshold = threshold; }

    public boolean isAuditLogEnabled() { return auditLogEnabled; }
    public void setAuditLogEnabled(boolean enabled) { this.auditLogEnabled = enabled; }

    public String getAuditLogLevel() { return auditLogLevel; }
    public void setAuditLogLevel(String level) { this.auditLogLevel = level; }

    public boolean isAutoEscalateToMainBrain() { return autoEscalateToMainBrain; }
    public void setAutoEscalateToMainBrain(boolean v) { this.autoEscalateToMainBrain = v; }

    public List<BoundaryDefinition> getBoundaries() { return boundaries; }
    public void setBoundaries(List<BoundaryDefinition> boundaries) { this.boundaries = boundaries; }
}
