package com.livingagent.core.intervention;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class RiskAssessment {

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
