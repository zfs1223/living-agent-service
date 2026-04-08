package com.livingagent.core.intervention;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public interface RiskAssessmentService {

    RiskAssessment assess(String operationType, Map<String, Object> operationDetails);

    RiskAssessment assessWithHistory(String operationType, Map<String, Object> operationDetails, List<InterventionDecision> history);

    void registerRiskFactor(String operationType, RiskFactor factor);

    void unregisterRiskFactor(String factorId);

    List<RiskFactor> getRiskFactors(String operationType);

    public static class RiskAssessment {
        private InterventionDecision.RiskLevel riskLevel;
        private double riskScore;
        private List<String> riskFactors;
        private Map<String, Object> riskDetails;
        private String assessmentReason;

        public RiskAssessment() {
            this.riskFactors = new ArrayList<>();
            this.riskDetails = new HashMap<>();
        }

        public static RiskAssessment low() {
            RiskAssessment assessment = new RiskAssessment();
            assessment.setRiskLevel(InterventionDecision.RiskLevel.LOW);
            assessment.setRiskScore(0.2);
            return assessment;
        }

        public static RiskAssessment medium() {
            RiskAssessment assessment = new RiskAssessment();
            assessment.setRiskLevel(InterventionDecision.RiskLevel.MEDIUM);
            assessment.setRiskScore(0.5);
            return assessment;
        }

        public static RiskAssessment high() {
            RiskAssessment assessment = new RiskAssessment();
            assessment.setRiskLevel(InterventionDecision.RiskLevel.HIGH);
            assessment.setRiskScore(0.75);
            return assessment;
        }

        public static RiskAssessment critical() {
            RiskAssessment assessment = new RiskAssessment();
            assessment.setRiskLevel(InterventionDecision.RiskLevel.CRITICAL);
            assessment.setRiskScore(0.95);
            return assessment;
        }

        public InterventionDecision.RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(InterventionDecision.RiskLevel riskLevel) { this.riskLevel = riskLevel; }

        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

        public List<String> getRiskFactors() { return riskFactors; }
        public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }

        public Map<String, Object> getRiskDetails() { return riskDetails; }
        public void setRiskDetails(Map<String, Object> riskDetails) { this.riskDetails = riskDetails; }

        public String getAssessmentReason() { return assessmentReason; }
        public void setAssessmentReason(String assessmentReason) { this.assessmentReason = assessmentReason; }

        public void addRiskFactor(String factor) {
            this.riskFactors.add(factor);
        }

        public void addRiskDetail(String key, Object value) {
            this.riskDetails.put(key, value);
        }
    }

    public static class RiskFactor {
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
                    if (value != null && value.equals(condition.getValue())) {
                        return true;
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
}
