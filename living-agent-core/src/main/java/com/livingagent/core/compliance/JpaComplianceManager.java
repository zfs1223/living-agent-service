package com.livingagent.core.compliance;

import com.livingagent.core.database.entity.ComplianceViolationEntity;
import com.livingagent.core.database.entity.ComplianceViolationEntity.ViolationCategory;
import com.livingagent.core.database.entity.ComplianceViolationEntity.ViolationSeverity;
import com.livingagent.core.database.entity.ComplianceViolationEntity.ViolationStatus;
import com.livingagent.core.database.repository.AccessAuditLogRepository;
import com.livingagent.core.database.repository.ComplianceViolationRepository;
import com.livingagent.core.security.AccessAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JPA 持久化版 ComplianceManager - P2-3 + N-1 修复。
 * 
 * <p>违规记录和审计日志写入数据库，重启后不丢失。
 * 规则配置仍保留内存态（可后续扩展为 DB 配置）。</p>
 */
@Service
@Primary
public class JpaComplianceManager {

    private static final Logger log = LoggerFactory.getLogger(JpaComplianceManager.class);

    private final ComplianceViolationRepository violationRepository;
    private final AccessAuditLogRepository auditLogRepository;

    private final Map<String, ComplianceRule> rules = new ConcurrentHashMap<>();
    private boolean complianceEnabled = true;
    private int maxAuditLogDays = 90;

    public JpaComplianceManager(
            ComplianceViolationRepository violationRepository,
            AccessAuditLogRepository auditLogRepository) {
        this.violationRepository = violationRepository;
        this.auditLogRepository = auditLogRepository;
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

        log.info("Initialized {} default compliance rules (JPA persistence enabled)", rules.size());
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

    @Transactional
    public void recordAuditLog(AccessAuditLog auditLog) {
        auditLogRepository.save(auditLog);
        log.debug("Recorded audit log: {} - {} - {}", 
            auditLog.getEmployeeId(), auditLog.getAction(), auditLog.isGranted());
        
        checkCompliance(auditLog);
    }

    public List<AccessAuditLog> getAuditLogs(String employeeId, Instant from, Instant to) {
        if (employeeId != null && from != null && to != null) {
            return auditLogRepository.findByEmployeeIdAndDetectedAtBetween(
                employeeId, from.toEpochMilli(), to.toEpochMilli());
        }
        if (from != null && to != null) {
            long fromMillis = from.toEpochMilli();
            long toMillis = to.toEpochMilli();
            return auditLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp() >= fromMillis && log.getTimestamp() <= toMillis)
                .sorted(Comparator.comparingLong(AccessAuditLog::getTimestamp).reversed())
                .collect(Collectors.toList());
        }
        return auditLogRepository.findAll(PageRequest.of(0, 1000)).getContent();
    }

    public List<AccessAuditLog> getRecentAuditLogs(int limit) {
        return auditLogRepository.findAll(PageRequest.of(0, limit))
            .stream()
            .sorted(Comparator.comparingLong(AccessAuditLog::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    @Transactional
    public void checkCompliance(AccessAuditLog auditLog) {
        if (!complianceEnabled) return;

        for (ComplianceRule rule : rules.values()) {
            if (!rule.isEnabled()) continue;

            if (evaluateRule(rule, auditLog)) {
                ComplianceViolationEntity violation = createViolationEntity(rule, auditLog);
                violationRepository.save(violation);
                log.warn("Compliance violation detected and saved: {} by employee {} for rule {}", 
                    violation.getViolationId(), auditLog.getEmployeeId(), rule.getName());
            }
        }
    }

    private ComplianceViolationEntity createViolationEntity(ComplianceRule rule, AccessAuditLog auditLog) {
        ComplianceViolationEntity entity = new ComplianceViolationEntity();
        entity.setViolationId("violation_" + UUID.randomUUID().toString().substring(0, 8));
        entity.setRuleId(rule.getRuleId());
        entity.setRuleName(rule.getName());
        entity.setCategory(mapCategory(rule.getCategory()));
        entity.setSeverity(mapSeverity(rule.getSeverity()));
        entity.setEmployeeId(auditLog.getEmployeeId());
        entity.setEmployeeName(auditLog.getEmployeeName());
        entity.setResource(auditLog.getResource());
        entity.setAction(auditLog.getAction());
        entity.setDescription(rule.getViolationMessage());
        entity.setDetectedAt(Instant.now());
        entity.setStatus(ViolationStatus.DETECTED);
        return entity;
    }

    private ViolationCategory mapCategory(ComplianceRule.RuleCategory category) {
        return switch (category) {
            case DATA_PRIVACY -> ViolationCategory.DATA_PRIVACY;
            case ACCESS_CONTROL -> ViolationCategory.ACCESS_CONTROL;
            case AUDIT_TRAIL -> ViolationCategory.AUDIT_TRAIL;
            case DATA_RETENTION -> ViolationCategory.DATA_RETENTION;
            case SECURITY_POLICY -> ViolationCategory.SECURITY_POLICY;
            case INDUSTRY_REGULATION -> ViolationCategory.INDUSTRY_REGULATION;
            case INTERNAL_POLICY -> ViolationCategory.INTERNAL_POLICY;
        };
    }

    private ViolationSeverity mapSeverity(ComplianceRule.RuleSeverity severity) {
        return switch (severity) {
            case INFO -> ViolationSeverity.INFO;
            case LOW -> ViolationSeverity.LOW;
            case MEDIUM -> ViolationSeverity.MEDIUM;
            case HIGH -> ViolationSeverity.HIGH;
            case CRITICAL -> ViolationSeverity.CRITICAL;
        };
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
        long cutoffMillis = fiveMinutesAgo.toEpochMilli();
        
        return (int) auditLogRepository.findAll().stream()
            .filter(log -> log.getEmployeeId().equals(currentLog.getEmployeeId()))
            .filter(log -> "login".equals(log.getAction()))
            .filter(log -> !log.isGranted())
            .filter(log -> log.getTimestamp() >= cutoffMillis)
            .count();
    }

    public List<ComplianceViolationEntity> getViolations() {
        return violationRepository.findAll();
    }

    public List<ComplianceViolationEntity> getOpenViolations() {
        return violationRepository.findOpenViolations(
            List.of(ViolationStatus.RESOLVED, ViolationStatus.FALSE_POSITIVE)
        );
    }

    public List<ComplianceViolationEntity> getViolationsByEmployee(String employeeId) {
        return violationRepository.findByEmployeeIdOrderByDetectedAtDesc(employeeId);
    }

    @Transactional
    public void resolveViolation(String violationId, String resolvedBy, String resolution) {
        violationRepository.findByViolationId(violationId)
            .ifPresent(v -> {
                v.resolve(resolvedBy, resolution);
                violationRepository.save(v);
                log.info("Resolved violation {} by {}", violationId, resolvedBy);
            });
    }

    public ComplianceReport generateReport(Instant from, Instant to) {
        ComplianceReport report = new ComplianceReport();
        report.setReportPeriod(from, to);
        
        long totalAuditLogs = auditLogRepository.count();
        report.setTotalAuditLogs((int) totalAuditLogs);
        
        List<ComplianceViolationEntity> periodViolations = 
            violationRepository.findByDetectedAtBetween(from, to);
        
        report.setTotalViolations(periodViolations.size());
        
        Map<ComplianceRule.RuleSeverity, Integer> bySeverity = new EnumMap<>(ComplianceRule.RuleSeverity.class);
        List<Object[]> severityCounts = violationRepository.countBySeverityBetween(from, to);
        for (Object[] row : severityCounts) {
            ViolationSeverity vs = (ViolationSeverity) row[0];
            Long count = (Long) row[1];
            ComplianceRule.RuleSeverity severity = switch (vs) {
                case INFO -> ComplianceRule.RuleSeverity.INFO;
                case LOW -> ComplianceRule.RuleSeverity.LOW;
                case MEDIUM -> ComplianceRule.RuleSeverity.MEDIUM;
                case HIGH -> ComplianceRule.RuleSeverity.HIGH;
                case CRITICAL -> ComplianceRule.RuleSeverity.CRITICAL;
            };
            bySeverity.put(severity, count.intValue());
        }
        report.setViolationsBySeverity(bySeverity);
        
        Map<ComplianceRule.RuleCategory, Integer> byCategory = new EnumMap<>(ComplianceRule.RuleCategory.class);
        List<Object[]> categoryCounts = violationRepository.countByCategoryBetween(from, to);
        for (Object[] row : categoryCounts) {
            ViolationCategory vc = (ViolationCategory) row[0];
            Long count = (Long) row[1];
            ComplianceRule.RuleCategory category = switch (vc) {
                case DATA_PRIVACY -> ComplianceRule.RuleCategory.DATA_PRIVACY;
                case ACCESS_CONTROL -> ComplianceRule.RuleCategory.ACCESS_CONTROL;
                case AUDIT_TRAIL -> ComplianceRule.RuleCategory.AUDIT_TRAIL;
                case DATA_RETENTION -> ComplianceRule.RuleCategory.DATA_RETENTION;
                case SECURITY_POLICY -> ComplianceRule.RuleCategory.SECURITY_POLICY;
                case INDUSTRY_REGULATION -> ComplianceRule.RuleCategory.INDUSTRY_REGULATION;
                case INTERNAL_POLICY -> ComplianceRule.RuleCategory.INTERNAL_POLICY;
            };
            byCategory.put(category, count.intValue());
        }
        report.setViolationsByCategory(byCategory);
        
        int resolved = (int) periodViolations.stream().filter(ComplianceViolationEntity::isResolved).count();
        report.setResolvedViolations(resolved);
        report.setOpenViolations(periodViolations.size() - resolved);
        
        report.setComplianceScore(calculateComplianceScore(periodViolations));
        
        return report;
    }

    private double calculateComplianceScore(List<ComplianceViolationEntity> violations) {
        if (violations.isEmpty()) return 100.0;
        
        double penalty = violations.stream()
            .mapToDouble(v -> v.getSeverity().getLevel() * 5)
            .sum();
        
        return Math.max(0, 100 - penalty);
    }

    @Transactional
    public void cleanupOldAuditLogs() {
        long cutoffTime = Instant.now().minus(maxAuditLogDays, ChronoUnit.DAYS).toEpochMilli();
        auditLogRepository.deleteByTimestampBefore(cutoffTime);
        log.info("Cleaned up audit logs older than {} days", maxAuditLogDays);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRules", rules.size());
        stats.put("enabledRules", rules.values().stream().filter(ComplianceRule::isEnabled).count());
        stats.put("totalViolations", violationRepository.count());
        stats.put("openViolations", getOpenViolations().size());
        stats.put("totalAuditLogs", auditLogRepository.count());
        stats.put("complianceEnabled", complianceEnabled);
        stats.put("persistenceMode", "JPA");
        return stats;
    }

    public void setComplianceEnabled(boolean enabled) {
        this.complianceEnabled = enabled;
        log.info("Compliance checking {} (JPA mode)", enabled ? "enabled" : "disabled");
    }

    public void setMaxAuditLogDays(int days) {
        this.maxAuditLogDays = days;
    }
}