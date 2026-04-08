package com.livingagent.core.compliance;

import com.livingagent.core.security.AccessAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ComplianceManager {

    private static final Logger log = LoggerFactory.getLogger(ComplianceManager.class);

    private final Map<String, ComplianceRule> rules = new ConcurrentHashMap<>();
    private final List<ComplianceViolation> violations = new ArrayList<>();
    private final List<AccessAuditLog> auditLogs = new ArrayList<>();
    
    private boolean complianceEnabled = true;
    private int maxAuditLogDays = 90;

    public ComplianceManager() {
        initDefaultRules();
    }

    private void initDefaultRules() {
        ComplianceRule rule1 = new ComplianceRule(
            "敏感数据访问限制",
            ComplianceRule.RuleCategory.DATA_PRIVACY,
            ComplianceRule.RuleSeverity.HIGH
        );
        rule1.setDescription("限制对敏感数据的访问，仅授权人员可访问");
        rule1.setViolationMessage("未授权访问敏感数据");
        rule1.setRemediation("立即撤销访问权限并审查访问日志");
        addRule(rule1);

        ComplianceRule rule2 = new ComplianceRule(
            "跨部门数据访问审批",
            ComplianceRule.RuleCategory.ACCESS_CONTROL,
            ComplianceRule.RuleSeverity.MEDIUM
        );
        rule2.setDescription("跨部门访问数据需要审批");
        rule2.setViolationMessage("未经审批跨部门访问数据");
        rule2.setRemediation("提交跨部门访问申请");
        addRule(rule2);

        ComplianceRule rule3 = new ComplianceRule(
            "操作审计记录",
            ComplianceRule.RuleCategory.AUDIT_TRAIL,
            ComplianceRule.RuleSeverity.HIGH
        );
        rule3.setDescription("所有关键操作必须记录审计日志");
        rule3.setViolationMessage("操作未记录审计日志");
        rule3.setRemediation("补充审计日志记录");
        addRule(rule3);

        ComplianceRule rule4 = new ComplianceRule(
            "数据保留期限",
            ComplianceRule.RuleCategory.DATA_RETENTION,
            ComplianceRule.RuleSeverity.MEDIUM
        );
        rule4.setDescription("数据保留期限不得超过规定时间");
        rule4.setViolationMessage("数据保留时间超过限制");
        rule4.setRemediation("清理过期数据");
        addRule(rule4);

        ComplianceRule rule5 = new ComplianceRule(
            "登录失败锁定",
            ComplianceRule.RuleCategory.SECURITY_POLICY,
            ComplianceRule.RuleSeverity.HIGH
        );
        rule5.setDescription("连续登录失败5次后锁定账户");
        rule5.setViolationMessage("检测到暴力破解尝试");
        rule5.setRemediation("锁定账户并通知安全团队");
        addRule(rule5);

        log.info("Initialized {} default compliance rules", rules.size());
    }

    private ComplianceRule addRule(ComplianceRule rule) {
        rules.put(rule.getRuleId(), rule);
        return rule;
    }

    public void registerRule(ComplianceRule rule) {
        rules.put(rule.getRuleId(), rule);
        log.info("Registered compliance rule: {} [{}]", rule.getName(), rule.getCategory());
    }

    public void removeRule(String ruleId) {
        ComplianceRule removed = rules.remove(ruleId);
        if (removed != null) {
            log.info("Removed compliance rule: {}", removed.getName());
        }
    }

    public Optional<ComplianceRule> getRule(String ruleId) {
        return Optional.ofNullable(rules.get(ruleId));
    }

    public List<ComplianceRule> getAllRules() {
        return new ArrayList<>(rules.values());
    }

    public List<ComplianceRule> getRulesByCategory(ComplianceRule.RuleCategory category) {
        return rules.values().stream()
            .filter(r -> r.getCategory() == category)
            .collect(Collectors.toList());
    }

    public void recordAuditLog(AccessAuditLog auditLog) {
        auditLogs.add(auditLog);
        log.debug("Recorded audit log: {} - {} - {}", 
            auditLog.getEmployeeId(), auditLog.getAction(), auditLog.isGranted());
        
        checkCompliance(auditLog);
    }

    public List<AccessAuditLog> getAuditLogs(String employeeId, Instant from, Instant to) {
        return auditLogs.stream()
            .filter(log -> employeeId == null || employeeId.equals(log.getEmployeeId()))
            .filter(log -> from == null || log.getTimestamp() >= from.toEpochMilli())
            .filter(log -> to == null || log.getTimestamp() <= to.toEpochMilli())
            .sorted(Comparator.comparingLong(AccessAuditLog::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    public List<AccessAuditLog> getRecentAuditLogs(int limit) {
        return auditLogs.stream()
            .sorted(Comparator.comparingLong(AccessAuditLog::getTimestamp).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    public void checkCompliance(AccessAuditLog auditLog) {
        if (!complianceEnabled) return;

        for (ComplianceRule rule : rules.values()) {
            if (!rule.isEnabled()) continue;

            if (evaluateRule(rule, auditLog)) {
                ComplianceViolation violation = new ComplianceViolation(
                    rule, auditLog.getEmployeeId(), 
                    auditLog.getResource(), auditLog.getAction()
                );
                violation.setEmployeeName(auditLog.getEmployeeName());
                violation.setDescription(rule.getViolationMessage());
                
                violations.add(violation);
                log.warn("Compliance violation detected: {} by employee {} for rule {}", 
                    violation.getViolationId(), auditLog.getEmployeeId(), rule.getName());
            }
        }
    }

    private boolean evaluateRule(ComplianceRule rule, AccessAuditLog auditLog) {
        return switch (rule.getName()) {
            case "敏感数据访问限制" -> {
                yield isSensitiveDataAccess(auditLog) && !auditLog.isGranted();
            }
            case "跨部门数据访问审批" -> {
                yield isCrossDepartmentAccess(auditLog) && !hasApproval(auditLog);
            }
            case "操作审计记录" -> {
                yield false;
            }
            case "登录失败锁定" -> {
                yield isLoginFailure(auditLog) && countRecentFailures(auditLog) >= 5;
            }
            default -> false;
        };
    }

    private boolean isSensitiveDataAccess(AccessAuditLog auditLog) {
        String resource = auditLog.getResource();
        return resource != null && (
            resource.contains("salary") ||
            resource.contains("personal") ||
            resource.contains("financial") ||
            resource.contains("contract")
        );
    }

    private boolean isCrossDepartmentAccess(AccessAuditLog auditLog) {
        return auditLog.getResource() != null && 
               auditLog.getResource().contains("department:");
    }

    private boolean hasApproval(AccessAuditLog auditLog) {
        return auditLog.isGranted();
    }

    private boolean isLoginFailure(AccessAuditLog auditLog) {
        return "login".equals(auditLog.getAction()) && !auditLog.isGranted();
    }

    private int countRecentFailures(AccessAuditLog currentLog) {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        return (int) auditLogs.stream()
            .filter(log -> log.getEmployeeId().equals(currentLog.getEmployeeId()))
            .filter(log -> "login".equals(log.getAction()))
            .filter(log -> !log.isGranted())
            .filter(log -> log.getTimestamp() >= fiveMinutesAgo.toEpochMilli())
            .count();
    }

    public List<ComplianceViolation> getViolations() {
        return new ArrayList<>(violations);
    }

    public List<ComplianceViolation> getOpenViolations() {
        return violations.stream()
            .filter(v -> !v.isResolved())
            .sorted(Comparator.comparing(v -> v.getSeverity().getLevel(), Comparator.reverseOrder()))
            .collect(Collectors.toList());
    }

    public List<ComplianceViolation> getViolationsByEmployee(String employeeId) {
        return violations.stream()
            .filter(v -> employeeId.equals(v.getEmployeeId()))
            .collect(Collectors.toList());
    }

    public void resolveViolation(String violationId, String resolvedBy, String resolution) {
        violations.stream()
            .filter(v -> violationId.equals(v.getViolationId()))
            .findFirst()
            .ifPresent(v -> {
                v.resolve(resolvedBy, resolution);
                log.info("Resolved violation {} by {}", violationId, resolvedBy);
            });
    }

    public ComplianceReport generateReport(Instant from, Instant to) {
        ComplianceReport report = new ComplianceReport();
        report.setReportPeriod(from, to);
        
        report.setTotalAuditLogs((int) auditLogs.stream()
            .filter(log -> log.getTimestamp() >= from.toEpochMilli() 
                        && log.getTimestamp() <= to.toEpochMilli())
            .count());
        
        List<ComplianceViolation> periodViolations = violations.stream()
            .filter(v -> v.getDetectedAt().isAfter(from) && v.getDetectedAt().isBefore(to))
            .collect(Collectors.toList());
        
        report.setTotalViolations(periodViolations.size());
        
        Map<ComplianceRule.RuleSeverity, Integer> bySeverity = new EnumMap<>(ComplianceRule.RuleSeverity.class);
        for (ComplianceRule.RuleSeverity severity : ComplianceRule.RuleSeverity.values()) {
            bySeverity.put(severity, (int) periodViolations.stream()
                .filter(v -> v.getSeverity() == severity)
                .count());
        }
        report.setViolationsBySeverity(bySeverity);
        
        Map<ComplianceRule.RuleCategory, Integer> byCategory = new EnumMap<>(ComplianceRule.RuleCategory.class);
        for (ComplianceRule.RuleCategory category : ComplianceRule.RuleCategory.values()) {
            byCategory.put(category, (int) periodViolations.stream()
                .filter(v -> v.getCategory() == category)
                .count());
        }
        report.setViolationsByCategory(byCategory);
        
        int resolved = (int) periodViolations.stream().filter(ComplianceViolation::isResolved).count();
        report.setResolvedViolations(resolved);
        report.setOpenViolations(periodViolations.size() - resolved);
        
        report.setComplianceScore(calculateComplianceScore(periodViolations));
        
        return report;
    }

    private double calculateComplianceScore(List<ComplianceViolation> violations) {
        if (violations.isEmpty()) return 100.0;
        
        double penalty = violations.stream()
            .mapToDouble(v -> v.getSeverity().getLevel() * 5)
            .sum();
        
        return Math.max(0, 100 - penalty);
    }

    public void cleanupOldAuditLogs() {
        Instant cutoff = Instant.now().minus(maxAuditLogDays, ChronoUnit.DAYS);
        int removed = 0;
        
        Iterator<AccessAuditLog> iterator = auditLogs.iterator();
        while (iterator.hasNext()) {
            AccessAuditLog log = iterator.next();
            if (log.getTimestamp() < cutoff.toEpochMilli()) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned up {} audit logs older than {} days", removed, maxAuditLogDays);
        }
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRules", rules.size());
        stats.put("enabledRules", rules.values().stream().filter(ComplianceRule::isEnabled).count());
        stats.put("totalViolations", violations.size());
        stats.put("openViolations", getOpenViolations().size());
        stats.put("totalAuditLogs", auditLogs.size());
        stats.put("complianceEnabled", complianceEnabled);
        return stats;
    }

    public void setComplianceEnabled(boolean enabled) {
        this.complianceEnabled = enabled;
        log.info("Compliance checking {}", enabled ? "enabled" : "disabled");
    }

    public void setMaxAuditLogDays(int days) {
        this.maxAuditLogDays = days;
    }
}
