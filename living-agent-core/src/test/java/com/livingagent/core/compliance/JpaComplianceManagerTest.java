package com.livingagent.core.compliance;

import com.livingagent.core.database.entity.ComplianceViolationEntity;
import com.livingagent.core.database.entity.ComplianceViolationEntity.ViolationCategory;
import com.livingagent.core.database.entity.ComplianceViolationEntity.ViolationSeverity;
import com.livingagent.core.database.entity.ComplianceViolationEntity.ViolationStatus;
import com.livingagent.core.database.repository.AccessAuditLogRepository;
import com.livingagent.core.database.repository.ComplianceViolationRepository;
import com.livingagent.core.security.AccessAuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * JpaComplianceManager 单测 - P2-3 修复验证。
 *
 * 测试范围：
 * - recordAuditLog：审计日志记录
 * - getOpenViolations：获取未解决违规
 * - resolveViolation：解决违规
 * - generateReport：合规报告生成
 */
@ExtendWith(MockitoExtension.class)
class JpaComplianceManagerTest {

    @Mock
    private ComplianceViolationRepository violationRepository;

    @Mock
    private AccessAuditLogRepository auditLogRepository;

    private JpaComplianceManager complianceManager;

    @BeforeEach
    void setUp() {
        complianceManager = new JpaComplianceManager(violationRepository, auditLogRepository);
    }

    @Test
    @DisplayName("初始化默认规则")
    void testDefaultRulesInitialized() {
        List<ComplianceRule> rules = complianceManager.getAllRules();
        assertTrue(rules.size() >= 5, "Should have at least 5 default rules");
    }

    @Test
    @DisplayName("recordAuditLog - 保存审计日志")
    void testRecordAuditLog_SavesToRepository() {
        AccessAuditLog auditLog = new AccessAuditLog();
        auditLog.setLogId("log_001");
        auditLog.setEmployeeId("employee://human/dingtalk/123");
        auditLog.setAction("read");
        auditLog.setResource("salary_data");
        auditLog.setGranted(false);

        when(auditLogRepository.save(any(AccessAuditLog.class))).thenReturn(auditLog);

        complianceManager.recordAuditLog(auditLog);

        verify(auditLogRepository).save(any(AccessAuditLog.class));
    }

    @Test
    @DisplayName("getOpenViolations - 返回未解决违规列表")
    void testGetOpenViolations_ReturnsList() {
        ComplianceViolationEntity v1 = new ComplianceViolationEntity();
        v1.setViolationId("v_001");
        v1.setStatus(ViolationStatus.DETECTED);
        v1.setSeverity(ViolationSeverity.HIGH);

        when(violationRepository.findOpenViolations(anyList())).thenReturn(List.of(v1));

        List<ComplianceViolationEntity> violations = complianceManager.getOpenViolations();

        assertEquals(1, violations.size());
        assertEquals("v_001", violations.get(0).getViolationId());
    }

    @Test
    @DisplayName("resolveViolation - 更新违规状态为已解决")
    void testResolveViolation_UpdatesStatus() {
        ComplianceViolationEntity entity = new ComplianceViolationEntity();
        entity.setViolationId("v_001");
        entity.setStatus(ViolationStatus.DETECTED);

        when(violationRepository.findByViolationId("v_001")).thenReturn(Optional.of(entity));
        when(violationRepository.save(any(ComplianceViolationEntity.class))).thenReturn(entity);

        complianceManager.resolveViolation("v_001", "admin", "Issue fixed");

        verify(violationRepository).save(any(ComplianceViolationEntity.class));
    }

    @Test
    @DisplayName("getViolationsByEmployee - 按员工查询违规")
    void testGetViolationsByEmployee_ReturnsList() {
        ComplianceViolationEntity v1 = new ComplianceViolationEntity();
        v1.setViolationId("v_001");
        v1.setEmployeeId("emp_001");

        when(violationRepository.findByEmployeeIdOrderByDetectedAtDesc("emp_001"))
            .thenReturn(List.of(v1));

        List<ComplianceViolationEntity> violations = complianceManager.getViolationsByEmployee("emp_001");

        assertEquals(1, violations.size());
        assertEquals("emp_001", violations.get(0).getEmployeeId());
    }

    @Test
    @DisplayName("getStatistics - 返回合规统计")
    void testGetStatistics_ReturnsCorrectStats() {
        when(violationRepository.count()).thenReturn(5L);
        when(auditLogRepository.count()).thenReturn(100L);
        when(violationRepository.findOpenViolations(anyList())).thenReturn(List.of());

        var stats = complianceManager.getStatistics();

        assertEquals(5L, stats.get("totalViolations"));
        assertEquals(100L, stats.get("totalAuditLogs"));
        assertEquals("JPA", stats.get("persistenceMode"));
    }

    @Test
    @DisplayName("registerRule - 添加新规则")
    void testRegisterRule_AddsNewRule() {
        ComplianceRule newRule = new ComplianceRule(
            "Custom Rule",
            ComplianceRule.RuleCategory.INTERNAL_POLICY,
            ComplianceRule.RuleSeverity.MEDIUM
        );

        complianceManager.registerRule(newRule);

        assertTrue(complianceManager.getRule(newRule.getRuleId()).isPresent());
    }

    @Test
    @DisplayName("removeRule - 移除规则")
    void testRemoveRule_RemovesRule() {
        ComplianceRule rule = complianceManager.getAllRules().get(0);
        String ruleId = rule.getRuleId();

        complianceManager.removeRule(ruleId);

        assertFalse(complianceManager.getRule(ruleId).isPresent());
    }

    @Test
    @DisplayName("setComplianceEnabled - 禁用合规检查")
    void testSetComplianceEnabled_DisablesCheck() {
        complianceManager.setComplianceEnabled(false);

        AccessAuditLog auditLog = new AccessAuditLog();
        auditLog.setEmployeeId("emp_001");

        complianceManager.recordAuditLog(auditLog);

        // 禁用后不应检测违规
        verify(violationRepository, never()).save(any(ComplianceViolationEntity.class));
    }
}