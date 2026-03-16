package com.livingagent.core.security;

import java.util.List;
import java.util.Map;

public interface ContentValidator {

    ValidationResult validate(String content, ContentType type);

    ValidationResult validateFile(byte[] content, String filename);

    boolean containsMaliciousCode(String content);

    List<ThreatInfo> detectThreats(String content);

    ContentSafetyLevel assessSafetyLevel(String content);

    enum ContentType {
        TEXT,
        CODE,
        JSON,
        XML,
        HTML,
        URL,
        FILE_PATH,
        COMMAND,
        SQL,
        SCRIPT
    }

    enum ContentSafetyLevel {
        SAFE,
        LOW_RISK,
        MEDIUM_RISK,
        HIGH_RISK,
        DANGEROUS,
        UNKNOWN
    }

    record ValidationResult(
        boolean safe,
        ContentSafetyLevel safetyLevel,
        List<ThreatInfo> threats,
        String sanitizedContent,
        String message,
        Map<String, Object> metadata
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, ContentSafetyLevel.SAFE, List.of(), null, "Content is safe", Map.of());
        }

        public static ValidationResult unsafe(List<ThreatInfo> threats, String message) {
            return new ValidationResult(false, ContentSafetyLevel.DANGEROUS, threats, null, message, Map.of());
        }

        public static ValidationResult warning(List<ThreatInfo> threats, String message) {
            return new ValidationResult(false, ContentSafetyLevel.MEDIUM_RISK, threats, null, message, Map.of());
        }
    }

    record ThreatInfo(
        String threatId,
        ThreatType type,
        String description,
        String matchedPattern,
        int severity,
        String recommendation
    ) {
        public static ThreatInfo of(ThreatType type, String description, String pattern) {
            return new ThreatInfo("threat_" + System.currentTimeMillis(), type, description, pattern, type.severity, type.recommendation);
        }
    }

    enum ThreatType {
        SQL_INJECTION(9, "SQL注入攻击", "使用参数化查询"),
        XSS(8, "跨站脚本攻击", "对输出进行HTML编码"),
        COMMAND_INJECTION(10, "命令注入攻击", "避免shell执行"),
        PATH_TRAVERSAL(9, "路径遍历攻击", "限制文件访问路径"),
        MALICIOUS_CODE(10, "恶意代码", "在沙箱中执行"),
        DATA_EXFILTRATION(7, "数据泄露风险", "过滤敏感信息"),
        DENIAL_OF_SERVICE(6, "拒绝服务攻击", "限制资源使用"),
        CODE_INJECTION(10, "代码注入攻击", "禁用动态代码执行"),
        SENSITIVE_DATA(5, "敏感数据暴露", "加密存储敏感数据");

        private final int severity;
        private final String description;
        private final String recommendation;

        ThreatType(int severity, String description, String recommendation) {
            this.severity = severity;
            this.description = description;
            this.recommendation = recommendation;
        }

        public int getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getRecommendation() { return recommendation; }
    }
}
