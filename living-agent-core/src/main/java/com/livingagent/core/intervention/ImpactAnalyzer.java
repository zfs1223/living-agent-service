package com.livingagent.core.intervention;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public interface ImpactAnalyzer {

    ImpactAnalysis analyze(String operationType, Map<String, Object> operationDetails);

    ImpactAnalysis analyzeWithScope(String operationType, Map<String, Object> operationDetails, String department);

    void registerImpactRule(String operationType, ImpactRule rule);

    void unregisterImpactRule(String ruleId);

    List<ImpactRule> getImpactRules(String operationType);

    public static class ImpactAnalysis {
        private InterventionDecision.ImpactLevel impactLevel;
        private double impactScore;
        private List<String> impactScope;
        private Map<String, Object> impactDetails;
        private String analysisReason;
        private int affectedUsersCount;
        private int affectedSystemsCount;
        private boolean reversible;

        public ImpactAnalysis() {
            this.impactScope = new ArrayList<>();
            this.impactDetails = new HashMap<>();
            this.reversible = true;
        }

        public static ImpactAnalysis minimal() {
            ImpactAnalysis analysis = new ImpactAnalysis();
            analysis.setImpactLevel(InterventionDecision.ImpactLevel.MINIMAL);
            analysis.setImpactScore(0.1);
            return analysis;
        }

        public static ImpactAnalysis small() {
            ImpactAnalysis analysis = new ImpactAnalysis();
            analysis.setImpactLevel(InterventionDecision.ImpactLevel.SMALL);
            analysis.setImpactScore(0.25);
            return analysis;
        }

        public static ImpactAnalysis moderate() {
            ImpactAnalysis analysis = new ImpactAnalysis();
            analysis.setImpactLevel(InterventionDecision.ImpactLevel.MODERATE);
            analysis.setImpactScore(0.5);
            return analysis;
        }

        public static ImpactAnalysis large() {
            ImpactAnalysis analysis = new ImpactAnalysis();
            analysis.setImpactLevel(InterventionDecision.ImpactLevel.LARGE);
            analysis.setImpactScore(0.75);
            return analysis;
        }

        public static ImpactAnalysis critical() {
            ImpactAnalysis analysis = new ImpactAnalysis();
            analysis.setImpactLevel(InterventionDecision.ImpactLevel.CRITICAL);
            analysis.setImpactScore(0.95);
            analysis.setReversible(false);
            return analysis;
        }

        public InterventionDecision.ImpactLevel getImpactLevel() { return impactLevel; }
        public void setImpactLevel(InterventionDecision.ImpactLevel impactLevel) { this.impactLevel = impactLevel; }

        public double getImpactScore() { return impactScore; }
        public void setImpactScore(double impactScore) { this.impactScore = impactScore; }

        public List<String> getImpactScope() { return impactScope; }
        public void setImpactScope(List<String> impactScope) { this.impactScope = impactScope; }

        public Map<String, Object> getImpactDetails() { return impactDetails; }
        public void setImpactDetails(Map<String, Object> impactDetails) { this.impactDetails = impactDetails; }

        public String getAnalysisReason() { return analysisReason; }
        public void setAnalysisReason(String analysisReason) { this.analysisReason = analysisReason; }

        public int getAffectedUsersCount() { return affectedUsersCount; }
        public void setAffectedUsersCount(int affectedUsersCount) { this.affectedUsersCount = affectedUsersCount; }

        public int getAffectedSystemsCount() { return affectedSystemsCount; }
        public void setAffectedSystemsCount(int affectedSystemsCount) { this.affectedSystemsCount = affectedSystemsCount; }

        public boolean isReversible() { return reversible; }
        public void setReversible(boolean reversible) { this.reversible = reversible; }

        public void addImpactScope(String scope) {
            this.impactScope.add(scope);
        }

        public void addImpactDetail(String key, Object value) {
            this.impactDetails.put(key, value);
        }
    }

    public static class ImpactRule {
        private String ruleId;
        private String ruleName;
        private String description;
        private double weight;
        private List<String> operationTypePrefixes;
        private Map<String, Object> conditions;
        private InterventionDecision.ImpactLevel impactLevel;
        private boolean reversible;

        public ImpactRule() {
            this.weight = 1.0;
            this.reversible = true;
            this.operationTypePrefixes = new ArrayList<>();
            this.conditions = new HashMap<>();
        }

        public boolean matches(String operationType, Map<String, Object> operationDetails) {
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

            if (conditions != null && !conditions.isEmpty()) {
                for (Map.Entry<String, Object> condition : conditions.entrySet()) {
                    Object value = operationDetails.get(condition.getKey());
                    if (value == null || !value.equals(condition.getValue())) {
                        return false;
                    }
                }
            }

            return true;
        }

        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }

        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public List<String> getOperationTypePrefixes() { return operationTypePrefixes; }
        public void setOperationTypePrefixes(List<String> operationTypePrefixes) { this.operationTypePrefixes = operationTypePrefixes; }

        public Map<String, Object> getConditions() { return conditions; }
        public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }

        public InterventionDecision.ImpactLevel getImpactLevel() { return impactLevel; }
        public void setImpactLevel(InterventionDecision.ImpactLevel impactLevel) { this.impactLevel = impactLevel; }

        public boolean isReversible() { return reversible; }
        public void setReversible(boolean reversible) { this.reversible = reversible; }
    }
}
