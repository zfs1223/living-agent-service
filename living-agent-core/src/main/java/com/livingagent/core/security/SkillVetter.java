package com.livingagent.core.security;

import com.livingagent.core.skill.Skill;

import java.util.List;
import java.util.Map;

public interface SkillVetter {

    VettingResult vetSkill(Skill skill);

    VettingResult vetSkillContent(String skillContent);

    VettingResult vetSkillFile(String filePath);

    VettingResult vetExternalSkill(String source, String skillContent);

    List<String> analyzeSkillDependencies(Skill skill);

    RiskAssessment assessSkillRisk(Skill skill);

    boolean isSkillSafe(Skill skill);

    enum VettingStatus {
        APPROVED,
        APPROVED_WITH_WARNINGS,
        REJECTED,
        REQUIRES_REVIEW,
        QUARANTINED
    }

    enum RiskLevel {
        MINIMAL(1, "风险极低，可安全使用"),
        LOW(2, "低风险，建议监控"),
        MEDIUM(3, "中等风险，需要审查"),
        HIGH(4, "高风险，需要隔离执行"),
        CRITICAL(5, "严重风险，禁止使用");

        private final int level;
        private final String description;

        RiskLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }

    record VettingResult(
        String vettingId,
        VettingStatus status,
        RiskLevel riskLevel,
        List<SecurityFinding> findings,
        List<String> recommendations,
        Map<String, Object> metadata,
        String summary
    ) {
        public static VettingResult approved(String vettingId, String summary) {
            return new VettingResult(vettingId, VettingStatus.APPROVED, RiskLevel.MINIMAL,
                List.of(), List.of(), Map.of(), summary);
        }

        public static VettingResult rejected(String vettingId, List<SecurityFinding> findings, String summary) {
            return new VettingResult(vettingId, VettingStatus.REJECTED, RiskLevel.CRITICAL,
                findings, List.of(), Map.of(), summary);
        }

        public static VettingResult warning(String vettingId, List<SecurityFinding> findings, String summary) {
            return new VettingResult(vettingId, VettingStatus.APPROVED_WITH_WARNINGS, RiskLevel.MEDIUM,
                findings, List.of("建议审查并修复警告项"), Map.of(), summary);
        }

        public static VettingResult quarantined(String vettingId, List<SecurityFinding> findings, String summary) {
            return new VettingResult(vettingId, VettingStatus.QUARANTINED, RiskLevel.HIGH,
                findings, List.of("技能已隔离，需要人工审查"), Map.of(), summary);
        }
    }

    record SecurityFinding(
        String findingId,
        FindingType type,
        FindingSeverity severity,
        String title,
        String description,
        String location,
        String codeSnippet,
        String remediation,
        List<String> references
    ) {
        public static SecurityFinding of(FindingType type, FindingSeverity severity,
                                         String title, String description, String location) {
            return new SecurityFinding("finding_" + System.nanoTime(), type, severity,
                title, description, location, null, null, List.of());
        }

        public static SecurityFinding withRemediation(FindingType type, FindingSeverity severity,
                                                      String title, String description,
                                                      String location, String remediation) {
            return new SecurityFinding("finding_" + System.nanoTime(), type, severity,
                title, description, location, null, remediation, List.of());
        }
    }

    enum FindingType {
        MALICIOUS_CODE,
        DANGEROUS_FUNCTION,
        SENSITIVE_DATA_ACCESS,
        NETWORK_ACCESS,
        FILE_SYSTEM_ACCESS,
        CODE_EXECUTION,
        PRIVILEGE_ESCALATION,
        DATA_EXFILTRATION,
        DEPENDENCY_VULNERABILITY,
        INSECURE_CONFIGURATION,
        UNUSUAL_BEHAVIOR,
        SUSPICIOUS_PATTERN
    }

    enum FindingSeverity {
        INFO(1),
        LOW(2),
        MEDIUM(3),
        HIGH(4),
        CRITICAL(5);

        private final int severity;

        FindingSeverity(int severity) {
            this.severity = severity;
        }

        public int getSeverity() { return severity; }
    }

    record RiskAssessment(
        RiskLevel overallRisk,
        double riskScore,
        Map<String, Double> categoryScores,
        List<String> riskFactors,
        String assessment
    ) {
        public static RiskAssessment minimal() {
            return new RiskAssessment(RiskLevel.MINIMAL, 0.1, Map.of(), List.of(), "技能风险极低");
        }

        public static RiskAssessment high(List<String> riskFactors) {
            return new RiskAssessment(RiskLevel.HIGH, 0.8, Map.of(), riskFactors, "技能存在高风险因素");
        }
    }
}
