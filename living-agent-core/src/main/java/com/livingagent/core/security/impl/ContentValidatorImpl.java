package com.livingagent.core.security.impl;

import com.livingagent.core.security.ContentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class ContentValidatorImpl implements ContentValidator {

    private static final Logger log = LoggerFactory.getLogger(ContentValidatorImpl.class);

    private static final Map<ThreatType, List<Pattern>> THREAT_PATTERNS = new EnumMap<>(ThreatType.class);

    static {
        THREAT_PATTERNS.put(ThreatType.SQL_INJECTION, List.of(
            Pattern.compile("(?i)(\\bunion\\b.*\\bselect\\b|\\bselect\\b.*\\bfrom\\b)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(\\binsert\\b.*\\binto\\b|\\bdelete\\b.*\\bfrom\\b)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(\\bdrop\\b.*\\b(table|database)\\b)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(--|;|\\/\\*|\\*\\/)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(\\bexec\\b|\\bexecute\\b)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)('\\s*or\\s*'\\s*=\\s*')", Pattern.CASE_INSENSITIVE)
        ));

        THREAT_PATTERNS.put(ThreatType.XSS, List.of(
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE)
        ));

        THREAT_PATTERNS.put(ThreatType.COMMAND_INJECTION, List.of(
            Pattern.compile("[;&|`$]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\|\\|", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$\\([^)]+\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("`[^`]+`", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$\\{[^}]+\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(rm\\s+-rf|rm\\s+-r|del\\s+/|format\\s+)", Pattern.CASE_INSENSITIVE)
        ));

        THREAT_PATTERNS.put(ThreatType.PATH_TRAVERSAL, List.of(
            Pattern.compile("\\.\\./", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\.\\.\\\\", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%2e%2e", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%252e", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\.\\.%2f", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%c0%ae", Pattern.CASE_INSENSITIVE)
        ));

        THREAT_PATTERNS.put(ThreatType.MALICIOUS_CODE, List.of(
            Pattern.compile("(?i)(eval\\s*\\(|exec\\s*\\(|system\\s*\\()", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(Runtime\\.getRuntime|ProcessBuilder)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(import\\s+os|subprocess|Popen)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(shell_exec|passthru|system\\()", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(base64_decode|gzinflate|str_rot13)", Pattern.CASE_INSENSITIVE)
        ));

        THREAT_PATTERNS.put(ThreatType.CODE_INJECTION, List.of(
            Pattern.compile("(?i)(eval\\(|Function\\(|new\\s+Function)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(require\\s*\\(|include\\s*\\()", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(import\\s+__|__import__)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(compile\\(|exec\\(\\s*\\(\\s*\"\\s*\")", Pattern.CASE_INSENSITIVE)
        ));

        THREAT_PATTERNS.put(ThreatType.SENSITIVE_DATA, List.of(
            Pattern.compile("(?i)(password\\s*=|passwd\\s*=)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(api[_-]?key\\s*=|secret[_-]?key\\s*=)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(token\\s*=|auth[_-]?token\\s*=)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(private[_-]?key\\s*=|access[_-]?key\\s*=)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b[A-Za-z0-9]{32,}\\b")
        ));
    }

    @Override
    public ValidationResult validate(String content, ContentType type) {
        if (content == null || content.isEmpty()) {
            return ValidationResult.ok();
        }

        List<ThreatInfo> threats = detectThreats(content);
        ContentSafetyLevel safetyLevel = assessSafetyLevel(content);

        if (threats.isEmpty()) {
            return new ValidationResult(true, safetyLevel, List.of(), null, "Content validated successfully", Map.of("contentType", type));
        }

        String sanitizedContent = sanitize(content, threats);

        boolean safe = threats.stream().noneMatch(t -> t.type().getSeverity() >= 8);
        String message = safe ? "Content contains minor issues, sanitized" : "Content contains security threats";

        return new ValidationResult(safe, safetyLevel, threats, sanitizedContent, message, Map.of("contentType", type, "threatCount", threats.size()));
    }

    @Override
    public ValidationResult validateFile(byte[] content, String filename) {
        if (content == null || content.length == 0) {
            return ValidationResult.ok();
        }

        String extension = getFileExtension(filename);
        boolean isExecutable = isExecutableFile(extension);
        boolean isArchive = isArchiveFile(extension);

        if (isExecutable) {
            ThreatInfo threat = ThreatInfo.of(ThreatType.MALICIOUS_CODE, "Executable file detected", filename);
            return ValidationResult.unsafe(List.of(threat), "Executable files are not allowed");
        }

        if (isArchive) {
            ThreatInfo threat = ThreatInfo.of(ThreatType.MALICIOUS_CODE, "Archive file detected", filename);
            return ValidationResult.warning(List.of(threat), "Archive files require additional validation");
        }

        try {
            String textContent = new String(content);
            return validate(textContent, ContentType.TEXT);
        } catch (Exception e) {
            return new ValidationResult(true, ContentSafetyLevel.UNKNOWN, List.of(), null, "Binary content", Map.of("filename", filename));
        }
    }

    @Override
    public boolean containsMaliciousCode(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        for (Map.Entry<ThreatType, List<Pattern>> entry : THREAT_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(content).find()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public List<ThreatInfo> detectThreats(String content) {
        List<ThreatInfo> threats = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return threats;
        }

        for (Map.Entry<ThreatType, List<Pattern>> entry : THREAT_PATTERNS.entrySet()) {
            ThreatType threatType = entry.getKey();
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(content).find()) {
                    threats.add(ThreatInfo.of(threatType, threatType.getDescription(), pattern.pattern()));
                }
            }
        }

        return threats.stream()
            .sorted(Comparator.comparingInt((ThreatInfo t) -> -t.type().getSeverity()))
            .toList();
    }

    @Override
    public ContentSafetyLevel assessSafetyLevel(String content) {
        if (content == null || content.isEmpty()) {
            return ContentSafetyLevel.SAFE;
        }

        List<ThreatInfo> threats = detectThreats(content);

        if (threats.isEmpty()) {
            return ContentSafetyLevel.SAFE;
        }

        int maxSeverity = threats.stream()
            .mapToInt(t -> t.type().getSeverity())
            .max()
            .orElse(0);

        if (maxSeverity >= 9) return ContentSafetyLevel.DANGEROUS;
        if (maxSeverity >= 7) return ContentSafetyLevel.HIGH_RISK;
        if (maxSeverity >= 5) return ContentSafetyLevel.MEDIUM_RISK;
        if (maxSeverity >= 3) return ContentSafetyLevel.LOW_RISK;

        return ContentSafetyLevel.SAFE;
    }

    private String sanitize(String content, List<ThreatInfo> threats) {
        String sanitized = content;

        sanitized = sanitized.replaceAll("<", "&lt;");
        sanitized = sanitized.replaceAll(">", "&gt;");
        sanitized = sanitized.replaceAll("\"", "&quot;");
        sanitized = sanitized.replaceAll("'", "&#39;");

        for (ThreatInfo threat : threats) {
            if (threat.type().getSeverity() >= 8) {
                sanitized = sanitized.replaceAll(threat.matchedPattern(), "[REDACTED]");
            }
        }

        return sanitized;
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }

    private boolean isExecutableFile(String extension) {
        return Set.of("exe", "bat", "cmd", "com", "scr", "pif", "vbs", "vbe", "ws", "wsf", "ps1", "psm1", "sh", "bash").contains(extension);
    }

    private boolean isArchiveFile(String extension) {
        return Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz").contains(extension);
    }
}
