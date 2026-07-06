package com.livingagent.core.evolution.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "evolution.governance")
public class SelfGovernanceProperties {

    private boolean enabled = true;
    private List<Integer> priorityOrder = List.of(30, 24, 27, 28, 25, 26, 29);
    private Map<String, Integer> cooldownSeconds = Map.of(
        "self-healing", 300,
        "degradation", 60,
        "security", 30
    );
    private String conflictArbitration = "human";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<Integer> getPriorityOrder() { return priorityOrder; }
    public void setPriorityOrder(List<Integer> priorityOrder) { this.priorityOrder = priorityOrder; }

    public Map<String, Integer> getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Map<String, Integer> cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }

    public String getConflictArbitration() { return conflictArbitration; }
    public void setConflictArbitration(String conflictArbitration) { this.conflictArbitration = conflictArbitration; }
}
