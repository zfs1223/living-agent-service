package com.livingagent.core.intervention;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class RiskFactor {

    private String factorId;
    private String factorName;
    private String description;
    private double weight;
    private List<String> applicableOperations;
    private Map<String, Object> triggerConditions;

    public RiskFactor() {
        this.weight = 1.0;
        this.applicableOperations = new ArrayList<>();
        this.triggerConditions = new HashMap<>();
    }

    public boolean isApplicable(String operationType, Map<String, Object> operationDetails) {
        if (applicableOperations != null && !applicableOperations.isEmpty()) {
            boolean operationMatch = false;
            for (String pattern : applicableOperations) {
                if (operationType != null && operationType.startsWith(pattern)) {
                    operationMatch = true;
                    break;
                }
            }
            if (!operationMatch) {
                return false;
            }
        }

        if (triggerConditions != null && !triggerConditions.isEmpty()) {
            for (Map.Entry<String, Object> condition : triggerConditions.entrySet()) {
                Object value = operationDetails.get(condition.getKey());
                if (value != null) {
                    if (condition.getValue() instanceof List) {
                        List<?> list = (List<?>) condition.getValue();
                        if (list.contains(value)) {
                            return true;
                        }
                    } else if (value.equals(condition.getValue())) {
                        return true;
                    } else if (condition.getValue() instanceof Number && value instanceof Number) {
                        double condVal = ((Number) condition.getValue()).doubleValue();
                        double actualVal = ((Number) value).doubleValue();
                        if (actualVal >= condVal) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        return true;
    }

    public String getFactorId() { return factorId; }
    public void setFactorId(String factorId) { this.factorId = factorId; }

    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public List<String> getApplicableOperations() { return applicableOperations; }
    public void setApplicableOperations(List<String> applicableOperations) { this.applicableOperations = applicableOperations; }

    public Map<String, Object> getTriggerConditions() { return triggerConditions; }
    public void setTriggerConditions(Map<String, Object> triggerConditions) { this.triggerConditions = triggerConditions; }
}
