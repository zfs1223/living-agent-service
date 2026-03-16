package com.livingagent.core.security.impl;

import com.livingagent.core.security.ContentValidator;
import com.livingagent.core.security.SkillVetter;
import com.livingagent.core.skill.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class SkillVetterImpl implements SkillVetter {

    private static final Logger log = LoggerFactory.getLogger(SkillVetterImpl.class);

    private final ContentValidator contentValidator;

    private static final Map<FindingType, List<Pattern>> DANGEROUS_PATTERNS = new EnumMap<>(FindingType.class);

    static {
        DANGEROUS_PATTERNS.put(FindingType.MALICIOUS_CODE, List.of(
            Pattern.compile("(?i)(eval\\s*\\(|exec\\s*\\(|compile\\s*\\()"),
            Pattern.compile("(?i)(Runtime\\.getRuntime|ProcessBuilder)"),
            Pattern.compile("(?i)(__import__|subprocess\\.Popen)"),
            Pattern.compile("(?i)(os\\.system|shell_exec|passthru)")
        ));

        DANGEROUS_PATTERNS.put(FindingType.FILE_SYSTEM_ACCESS, List.of(
            Pattern.compile("(?i)(Files\\.(delete|move|copy)|File\\.(delete|rename))"),
            Pattern.compile("(?i)(os\\.(remove|rename|makedirs|rmtree))"),
            Pattern.compile("(?i)(shutil\\.(rmtree|move|copy))"),
            Pattern.compile("(?i)(\\bdelete\\b.*\\bfrom\\b|\\bdrop\\b.*\\btable\\b)")
        ));

        DANGEROUS_PATTERNS.put(FindingType.NETWORK_ACCESS, List.of(
            Pattern.compile("(?i)(HttpURLConnection|HttpClient|RestTemplate)"),
            Pattern.compile("(?i)(requests\\.(get|post|put|delete))"),
            Pattern.compile("(?i)(fetch\\(|axios\\.|http\\.get|http\\.post)"),
            Pattern.compile("(?i)(socket\\.(connect|send)|WebSocket)")
        ));

        DANGEROUS_PATTERNS.put(FindingType.DATA_EXFILTRATION, List.of(
            Pattern.compile("(?i)(password|secret|token|api[_-]?key|private[_-]?key)"),
            Pattern.compile("(?i)(\\.env|credentials|config\\s*=)"),
            Pattern.compile("(?i)(base64\\.encode|btoa\\(|Buffer\\.from.*base64)"),
            Pattern.compile("(?i)(upload|send|transmit|exfil)")
        ));

        DANGEROUS_PATTERNS.put(FindingType.PRIVILEGE_ESCALATION, List.of(
            Pattern.compile("(?i)(sudo\\s+|chmod\\s+[0-7]{3,4}|chown\\s+)"),
            Pattern.compile("(?i)(runAsPrivileged|executePrivileged)"),
            Pattern.compile("(?i)(admin|root|superuser)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(grant\\s+permission|elevate)")
        ));

        DANGEROUS_PATTERNS.put(FindingType.CODE_EXECUTION, List.of(
            Pattern.compile("(?i)(new\\s+Function\\(|Function\\s*\\()"),
            Pattern.compile("(?i)(script\\s*engine|ScriptEngine)"),
            Pattern.compile("(?i)(groovy\\s*shell|GroovyShell)"),
            Pattern.compile("(?i)(python\\s*-c|node\\s*-e|ruby\\s*-e)")
        ));

        DANGEROUS_PATTERNS.put(FindingType.SUSPICIOUS_PATTERN, List.of(
            Pattern.compile("(?i)(obfuscat|encrypt\\s*\\(|decrypt\\s*\\()"),
            Pattern.compile("(?i)(sleep\\s*\\(|Thread\\.sleep|time\\.sleep)"),
            Pattern.compile("(?i)(while\\s*\\(true\\)|for\\s*\\(;;\\))"),
            Pattern.compile("(?i)(\\\\x[0-9a-f]{2}|\\\\u[0-9a-f]{4})")
        ));
    }

    private static final Set<String> SAFE_DOMAINS = Set.of(
        "api.openai.com", "api.anthropic.com", "api.github.com",
        "api.stripe.com", "api.twilio.com", "api.sendgrid.com"
    );

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "sh", "ps1", "vbs", "jar", "dll"
    );

    @Autowired
    public SkillVetterImpl(ContentValidator contentValidator) {
        this.contentValidator = contentValidator;
    }

    @Override
    public VettingResult vetSkill(Skill skill) {
        log.info("Vetting skill: {}", skill.getName());

        String vettingId = generateVettingId();
        List<SecurityFinding> findings = new ArrayList<>();

        if (skill.getContent() != null) {
            findings.addAll(analyzeContent(skill.getContent()));
        }

        findings.addAll(analyzeMetadata(skill));
        findings.addAll(analyzeDependencies(skill));

        return buildResult(vettingId, findings, skill.getName());
    }

    @Override
    public VettingResult vetSkillContent(String skillContent) {
        log.info("Vetting skill content (length: {})", skillContent.length());

        String vettingId = generateVettingId();
        List<SecurityFinding> findings = analyzeContent(skillContent);

        return buildResult(vettingId, findings, "unknown-skill");
    }

    @Override
    public VettingResult vetSkillFile(String filePath) {
        log.info("Vetting skill file: {}", filePath);

        String vettingId = generateVettingId();
        List<SecurityFinding> findings = new ArrayList<>();

        findings.addAll(analyzeFilePath(filePath));

        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                String content = Files.readString(path);
                findings.addAll(analyzeContent(content));
            }
        } catch (IOException e) {
            findings.add(SecurityFinding.of(
                FindingType.INSECURE_CONFIGURATION,
                FindingSeverity.HIGH,
                "无法读取技能文件",
                "技能文件读取失败: " + e.getMessage(),
                filePath
            ));
        }

        return buildResult(vettingId, findings, filePath);
    }

    @Override
    public VettingResult vetExternalSkill(String source, String skillContent) {
        log.info("Vetting external skill from source: {}", source);

        String vettingId = generateVettingId();
        List<SecurityFinding> findings = new ArrayList<>();

        findings.add(SecurityFinding.of(
            FindingType.SUSPICIOUS_PATTERN,
            FindingSeverity.INFO,
            "外部来源技能",
            "技能来自外部来源: " + source,
            "source"
        ));

        findings.addAll(analyzeContent(skillContent));

        if (contentValidator.containsMaliciousCode(skillContent)) {
            findings.add(SecurityFinding.withRemediation(
                FindingType.MALICIOUS_CODE,
                FindingSeverity.CRITICAL,
                "检测到恶意代码",
                "技能内容包含潜在的恶意代码模式",
                "content",
                "建议隔离此技能并进行人工审查"
            ));
        }

        VettingResult baseResult = buildResult(vettingId, findings, "external-skill");

        if (baseResult.riskLevel().getLevel() >= RiskLevel.HIGH.getLevel()) {
            return VettingResult.quarantined(vettingId, findings,
                "外部技能风险过高，已隔离: " + baseResult.summary());
        }

        return baseResult;
    }

    @Override
    public List<String> analyzeSkillDependencies(Skill skill) {
        List<String> dependencies = new ArrayList<>();

        if (skill.getContent() == null) {
            return dependencies;
        }

        String content = skill.getContent();

        Pattern importPattern = Pattern.compile("(?i)(import\\s+[\\w.]+|require\\s*\\(['\"]([\\w./-]+)['\"]\\)|from\\s+['\"]([\\w./-]+)['\"]\\s+import)");
        var matcher = importPattern.matcher(content);
        while (matcher.find()) {
            String dep = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
            if (dep != null && !dep.isEmpty()) {
                dependencies.add(dep);
            }
        }

        return dependencies;
    }

    @Override
    public RiskAssessment assessSkillRisk(Skill skill) {
        List<SecurityFinding> findings = new ArrayList<>();

        if (skill.getContent() != null) {
            findings.addAll(analyzeContent(skill.getContent()));
        }
        findings.addAll(analyzeMetadata(skill));

        if (findings.isEmpty()) {
            return RiskAssessment.minimal();
        }

        double riskScore = calculateRiskScore(findings);
        List<String> riskFactors = findings.stream()
            .filter(f -> f.severity().getSeverity() >= FindingSeverity.MEDIUM.getSeverity())
            .map(SecurityFinding::title)
            .toList();

        RiskLevel level = determineRiskLevel(riskScore);

        return new RiskAssessment(level, riskScore, Map.of(), riskFactors,
            "技能风险评估完成，发现 " + findings.size() + " 个问题");
    }

    @Override
    public boolean isSkillSafe(Skill skill) {
        VettingResult result = vetSkill(skill);
        return result.status() == VettingStatus.APPROVED ||
               result.status() == VettingStatus.APPROVED_WITH_WARNINGS;
    }

    private List<SecurityFinding> analyzeContent(String content) {
        List<SecurityFinding> findings = new ArrayList<>();

        for (Map.Entry<FindingType, List<Pattern>> entry : DANGEROUS_PATTERNS.entrySet()) {
            FindingType type = entry.getKey();
            for (Pattern pattern : entry.getValue()) {
                var matcher = pattern.matcher(content);
                while (matcher.find()) {
                    findings.add(SecurityFinding.of(
                        type,
                        determineSeverity(type),
                        getFindingTitle(type),
                        "检测到潜在危险模式: " + pattern.pattern(),
                        "位置: " + matcher.start()
                    ));
                }
            }
        }

        ContentValidator.ValidationResult validation = contentValidator.validate(content, ContentValidator.ContentType.TEXT);
        if (!validation.safe()) {
            for (ContentValidator.ThreatInfo threat : validation.threats()) {
                findings.add(SecurityFinding.withRemediation(
                    mapThreatTypeToFindingType(threat.type()),
                    mapSeverity(threat.type().getSeverity()),
                    threat.type().getDescription(),
                    threat.description(),
                    "content",
                    threat.type().getRecommendation()
                ));
            }
        }

        return findings;
    }

    private List<SecurityFinding> analyzeMetadata(Skill skill) {
        List<SecurityFinding> findings = new ArrayList<>();

        if (skill.getName() != null) {
            if (skill.getName().contains("..") || skill.getName().contains("/")) {
                findings.add(SecurityFinding.of(
                    FindingType.SUSPICIOUS_PATTERN,
                    FindingSeverity.HIGH,
                    "可疑的技能名称",
                    "技能名称包含路径遍历字符",
                    "name"
                ));
            }
        }

        if (skill.getMetadata() != null) {
            Object external = skill.getMetadata().get("external");
            if (Boolean.TRUE.equals(external)) {
                findings.add(SecurityFinding.of(
                    FindingType.SUSPICIOUS_PATTERN,
                    FindingSeverity.LOW,
                    "外部技能标记",
                    "技能标记为外部来源",
                    "metadata.external"
                ));
            }
        }

        return findings;
    }

    private List<SecurityFinding> analyzeDependencies(Skill skill) {
        List<SecurityFinding> findings = new ArrayList<>();
        List<String> dependencies = analyzeSkillDependencies(skill);

        for (String dep : dependencies) {
            if (isDangerousDependency(dep)) {
                findings.add(SecurityFinding.of(
                    FindingType.DEPENDENCY_VULNERABILITY,
                    FindingSeverity.HIGH,
                    "危险依赖",
                    "检测到潜在危险的依赖: " + dep,
                    "dependency: " + dep
                ));
            }
        }

        return findings;
    }

    private List<SecurityFinding> analyzeFilePath(String filePath) {
        List<SecurityFinding> findings = new ArrayList<>();

        if (filePath.contains("..") || filePath.contains("~")) {
            findings.add(SecurityFinding.of(
                FindingType.FILE_SYSTEM_ACCESS,
                FindingSeverity.HIGH,
                "路径遍历风险",
                "文件路径包含潜在的路径遍历字符",
                filePath
            ));
        }

        String extension = getFileExtension(filePath);
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            findings.add(SecurityFinding.of(
                FindingType.MALICIOUS_CODE,
                FindingSeverity.CRITICAL,
                "危险文件类型",
                "技能文件具有可执行扩展名: " + extension,
                filePath
            ));
        }

        return findings;
    }

    private VettingResult buildResult(String vettingId, List<SecurityFinding> findings, String skillName) {
        if (findings.isEmpty()) {
            return VettingResult.approved(vettingId, "技能审查通过，未发现安全问题");
        }

        boolean hasCritical = findings.stream()
            .anyMatch(f -> f.severity() == FindingSeverity.CRITICAL);
        boolean hasHigh = findings.stream()
            .anyMatch(f -> f.severity() == FindingSeverity.HIGH);

        if (hasCritical) {
            return VettingResult.rejected(vettingId, findings,
                "技能包含严重安全问题，已拒绝: " + findings.stream()
                    .filter(f -> f.severity() == FindingSeverity.CRITICAL)
                    .map(SecurityFinding::title)
                    .toList());
        }

        if (hasHigh) {
            return VettingResult.quarantined(vettingId, findings,
                "技能包含高风险问题，已隔离待审查");
        }

        long mediumCount = findings.stream()
            .filter(f -> f.severity() == FindingSeverity.MEDIUM)
            .count();

        if (mediumCount > 0) {
            return VettingResult.warning(vettingId, findings,
                "技能包含 " + mediumCount + " 个中等风险问题");
        }

        return VettingResult.approved(vettingId, "技能审查通过，发现 " + findings.size() + " 个低风险提示");
    }

    private double calculateRiskScore(List<SecurityFinding> findings) {
        if (findings.isEmpty()) return 0.0;

        double score = 0.0;
        for (SecurityFinding finding : findings) {
            score += switch (finding.severity()) {
                case CRITICAL -> 1.0;
                case HIGH -> 0.7;
                case MEDIUM -> 0.4;
                case LOW -> 0.2;
                case INFO -> 0.1;
            };
        }

        return Math.min(1.0, score / findings.size());
    }

    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 0.8) return RiskLevel.CRITICAL;
        if (riskScore >= 0.6) return RiskLevel.HIGH;
        if (riskScore >= 0.4) return RiskLevel.MEDIUM;
        if (riskScore >= 0.2) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }

    private FindingSeverity determineSeverity(FindingType type) {
        return switch (type) {
            case MALICIOUS_CODE, PRIVILEGE_ESCALATION -> FindingSeverity.CRITICAL;
            case CODE_EXECUTION, DATA_EXFILTRATION -> FindingSeverity.HIGH;
            case FILE_SYSTEM_ACCESS, NETWORK_ACCESS -> FindingSeverity.MEDIUM;
            case DEPENDENCY_VULNERABILITY, INSECURE_CONFIGURATION -> FindingSeverity.MEDIUM;
            case SUSPICIOUS_PATTERN, UNUSUAL_BEHAVIOR -> FindingSeverity.LOW;
            case SENSITIVE_DATA_ACCESS -> FindingSeverity.HIGH;
            case DANGEROUS_FUNCTION -> FindingSeverity.HIGH;
        };
    }

    private String getFindingTitle(FindingType type) {
        return switch (type) {
            case MALICIOUS_CODE -> "恶意代码检测";
            case DANGEROUS_FUNCTION -> "危险函数调用";
            case SENSITIVE_DATA_ACCESS -> "敏感数据访问";
            case NETWORK_ACCESS -> "网络访问";
            case FILE_SYSTEM_ACCESS -> "文件系统访问";
            case CODE_EXECUTION -> "代码执行";
            case PRIVILEGE_ESCALATION -> "权限提升风险";
            case DATA_EXFILTRATION -> "数据泄露风险";
            case DEPENDENCY_VULNERABILITY -> "依赖漏洞";
            case INSECURE_CONFIGURATION -> "不安全配置";
            case UNUSUAL_BEHAVIOR -> "异常行为";
            case SUSPICIOUS_PATTERN -> "可疑模式";
        };
    }

    private FindingType mapThreatTypeToFindingType(ContentValidator.ThreatType threatType) {
        return switch (threatType) {
            case SQL_INJECTION -> FindingType.CODE_EXECUTION;
            case XSS -> FindingType.MALICIOUS_CODE;
            case COMMAND_INJECTION -> FindingType.CODE_EXECUTION;
            case PATH_TRAVERSAL -> FindingType.FILE_SYSTEM_ACCESS;
            case MALICIOUS_CODE -> FindingType.MALICIOUS_CODE;
            case DATA_EXFILTRATION -> FindingType.DATA_EXFILTRATION;
            case DENIAL_OF_SERVICE -> FindingType.UNUSUAL_BEHAVIOR;
            case CODE_INJECTION -> FindingType.CODE_EXECUTION;
            case SENSITIVE_DATA -> FindingType.SENSITIVE_DATA_ACCESS;
        };
    }

    private FindingSeverity mapSeverity(int threatSeverity) {
        if (threatSeverity >= 9) return FindingSeverity.CRITICAL;
        if (threatSeverity >= 7) return FindingSeverity.HIGH;
        if (threatSeverity >= 5) return FindingSeverity.MEDIUM;
        if (threatSeverity >= 3) return FindingSeverity.LOW;
        return FindingSeverity.INFO;
    }

    private boolean isDangerousDependency(String dep) {
        String lowerDep = dep.toLowerCase();
        return lowerDep.contains("eval") ||
               lowerDep.contains("exec") ||
               lowerDep.contains("shell") ||
               lowerDep.contains("process") ||
               lowerDep.contains("subprocess");
    }

    private String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return dotIndex > 0 ? filePath.substring(dotIndex + 1).toLowerCase() : "";
    }

    private String generateVettingId() {
        return "vet_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
