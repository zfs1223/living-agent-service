package com.livingagent.core.intervention;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class InterventionRule {

    private String ruleId;
    private String ruleName;
    private String description;
    private int priority;
    private boolean enabled;

    private List<Pattern> operationPatterns;
    private List<String> operationTypePrefixes;
    private Map<String, Object> conditions;

    private InterventionDecision.RiskLevel minRiskLevel;
    private InterventionDecision.ImpactLevel minImpactLevel;
    private InterventionDecision.InterventionType interventionType;

    private int timeoutSeconds;
    private int escalationTimeoutSeconds;
    private String escalationTarget;

    private List<String> notificationChannels;
    private Map<String, Object> notificationConfig;

    private long triggerCount;
    private long successCount;
    private long lastTriggeredAt;

    public InterventionRule() {
        this.enabled = true;
        this.priority = 100;
        this.timeoutSeconds = 300;
        this.escalationTimeoutSeconds = 600;
        this.triggerCount = 0;
        this.successCount = 0;
    }

    public boolean matches(String operationType, Map<String, Object> operationDetails) {
        if (!enabled) {
            return false;
        }

        if (operationTypePrefixes != null && !operationTypePrefixes.isEmpty()) {
            boolean prefixMatch = false;
            for (String prefix : operationTypePrefixes) {
                if (operationType != null && operationType.startsWith(prefix)) {
                    prefixMatch = true;
                    break;
                }
            }
            if (!prefixMatch) {
                return false;
            }
        }

        if (operationPatterns != null && !operationPatterns.isEmpty()) {
            boolean patternMatch = false;
            for (Pattern pattern : operationPatterns) {
                if (operationType != null && pattern.matcher(operationType).matches()) {
                    patternMatch = true;
                    break;
                }
            }
            if (!patternMatch) {
                return false;
            }
        }

        if (conditions != null && !conditions.isEmpty()) {
            for (Map.Entry<String, Object> condition : conditions.entrySet()) {
                Object value = operationDetails.get(condition.getKey());
                if (value == null || !value.equals(condition.getValue())) {
                    if (condition.getValue() instanceof List) {
                        List<?> list = (List<?>) condition.getValue();
                        if (!list.contains(value)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public void recordTrigger() {
        this.triggerCount++;
        this.lastTriggeredAt = System.currentTimeMillis();
    }

    public void recordSuccess() {
        this.successCount++;
    }

    public double getSuccessRate() {
        if (triggerCount == 0) return 0.0;
        return (double) successCount / triggerCount * 100;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<Pattern> getOperationPatterns() { return operationPatterns; }
    public void setOperationPatterns(List<Pattern> operationPatterns) { this.operationPatterns = operationPatterns; }

    public List<String> getOperationTypePrefixes() { return operationTypePrefixes; }
    public void setOperationTypePrefixes(List<String> operationTypePrefixes) { this.operationTypePrefixes = operationTypePrefixes; }

    public Map<String, Object> getConditions() { return conditions; }
    public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }

    public InterventionDecision.RiskLevel getMinRiskLevel() { return minRiskLevel; }
    public void setMinRiskLevel(InterventionDecision.RiskLevel minRiskLevel) { this.minRiskLevel = minRiskLevel; }

    public InterventionDecision.ImpactLevel getMinImpactLevel() { return minImpactLevel; }
    public void setMinImpactLevel(InterventionDecision.ImpactLevel minImpactLevel) { this.minImpactLevel = minImpactLevel; }

    public InterventionDecision.InterventionType getInterventionType() { return interventionType; }
    public void setInterventionType(InterventionDecision.InterventionType interventionType) { this.interventionType = interventionType; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getEscalationTimeoutSeconds() { return escalationTimeoutSeconds; }
    public void setEscalationTimeoutSeconds(int escalationTimeoutSeconds) { this.escalationTimeoutSeconds = escalationTimeoutSeconds; }

    public String getEscalationTarget() { return escalationTarget; }
    public void setEscalationTarget(String escalationTarget) { this.escalationTarget = escalationTarget; }

    public List<String> getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(List<String> notificationChannels) { this.notificationChannels = notificationChannels; }

    public Map<String, Object> getNotificationConfig() { return notificationConfig; }
    public void setNotificationConfig(Map<String, Object> notificationConfig) { this.notificationConfig = notificationConfig; }

    public long getTriggerCount() { return triggerCount; }
    public void setTriggerCount(long triggerCount) { this.triggerCount = triggerCount; }

    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }

    public long getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(long lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
}
