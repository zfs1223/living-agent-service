package com.livingagent.core.proactive.llm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LlmRiskAssessor {

    Optional<RiskAssessmentResult> assessRisk(String domain, Map<String, Object> indicators);

    record RiskAssessmentResult(
        String assessmentId,
        String domain,
        RiskLevel level,
        String evidence,
        String impactScope,
        String recommendedAction,
        boolean requiresApproval,
        boolean requiresHumanIntervention,
        double confidence,
        String source
    ) {}

    enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
