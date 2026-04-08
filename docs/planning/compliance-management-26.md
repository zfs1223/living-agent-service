# 企业合规管理系统设计

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    企业合规管理系统架构                                        │
├─────────────────────────────────────────────────────────────────────────────┤

│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ComplianceManager (合规管理器)                    │   │
│  │  - 规则管理                                                          │   │
│  │  - 违规检测                                                          │   │
│  │  - 审计日志                                                          │   │
│  │  - 报告生成                                                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│         ┌──────────────────────────┼──────────────────────────┐            │
│         ▼                          ▼                          ▼            │
│  ┌─────────────┐           ┌─────────────┐           ┌─────────────┐      │
│  │ComplianceRule│          │ComplianceViolation│     │ComplianceReport│   │
│  │ 合规规则     │           │ 合规违规      │          │ 合规报告      │      │
│  └─────────────┘           └─────────────┘           └─────────────┘      │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    AccessAuditLog (审计日志)                         │   │
│  │  - 访问记录                                                          │   │
│  │  - 操作追踪                                                          │   │
│  │  - 权限变更                                                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 二、核心组件

### 2.1 ComplianceRule (合规规则)

**规则类别**:
| 类别 | 说明 |
|------|------|
| DATA_PRIVACY | 数据隐私 |
| ACCESS_CONTROL | 访问控制 |
| AUDIT_TRAIL | 审计追踪 |
| DATA_RETENTION | 数据保留 |
| SECURITY_POLICY | 安全策略 |
| INDUSTRY_REGULATION | 行业法规 |
| INTERNAL_POLICY | 内部政策 |

**严重级别**:
| 级别 | 分数 | 说明 |
|------|------|------|
| CRITICAL | 4 | 严重 |
| HIGH | 3 | 高 |
| MEDIUM | 2 | 中 |
| LOW | 1 | 低 |
| INFO | 0 | 信息 |

### 2.2 ComplianceViolation (合规违规)

**违规状态**:
| 状态 | 说明 |
|------|------|
| DETECTED | 已检测 |
| ACKNOWLEDGED | 已确认 |
| IN_REVIEW | 审查中 |
| RESOLVED | 已解决 |
| FALSE_POSITIVE | 误报 |
| ESCALATED | 已升级 |

### 2.3 ComplianceManager (合规管理器)

**核心功能**:
- 规则注册和管理
- 审计日志记录
- 违规自动检测
- 合规报告生成

### 2.4 ComplianceReport (合规报告)

**报告内容**:
- 审计日志统计
- 违规数量统计
- 合规评分
- 评级 (A+/A/B+/B/C/D/F)

## 三、默认合规规则

| 规则名称 | 类别 | 严重性 | 说明 |
|---------|------|--------|------|
| 敏感数据访问限制 | DATA_PRIVACY | HIGH | 限制对敏感数据的访问 |
| 跨部门数据访问审批 | ACCESS_CONTROL | MEDIUM | 跨部门访问需要审批 |
| 操作审计记录 | AUDIT_TRAIL | HIGH | 关键操作必须记录日志 |
| 数据保留期限 | DATA_RETENTION | MEDIUM | 数据保留时间限制 |
| 登录失败锁定 | SECURITY_POLICY | HIGH | 连续失败5次锁定 |

## 四、合规评分计算

```java
private double calculateComplianceScore(List<ComplianceViolation> violations) {
    if (violations.isEmpty()) return 100.0;
    
    // 每个违规扣除 severity.level * 5 分
    double penalty = violations.stream()
        .mapToDouble(v -> v.getSeverity().getLevel() * 5)
        .sum();
    
    return Math.max(0, 100 - penalty);
}
```

**评级标准**:
| 分数 | 评级 |
|------|------|
| ≥95 | A+ |
| ≥90 | A |
| ≥85 | B+ |
| ≥80 | B |
| ≥70 | C |
| ≥60 | D |
| <60 | F |

## 五、审计日志管理

**日志字段**:
- logId - 日志ID
- employeeId - 员工ID
- employeeName - 员工名称
- resource - 资源
- action - 操作
- granted - 是否授权
- reason - 原因
- timestamp - 时间戳
- sessionId - 会话ID
- ipAddress - IP地址

**日志清理**:
- 默认保留 90 天
- 可配置保留期限

## 六、文件结构

```
living-agent-core/src/main/java/com/livingagent/core/
├── compliance/
│   ├── ComplianceRule.java        # 合规规则
│   ├── ComplianceViolation.java   # 合规违规
│   ├── ComplianceManager.java     # 合规管理器
│   └── ComplianceReport.java      # 合规报告
└── security/
    └── AccessAuditLog.java        # 审计日志
```

## 七、使用示例

### 7.1 记录审计日志

```java
@Autowired
ComplianceManager complianceManager;

public void recordAccess(String employeeId, String resource, String action, boolean granted) {
    AccessAuditLog log = new AccessAuditLog();
    log.setEmployeeId(employeeId);
    log.setResource(resource);
    log.setAction(action);
    log.setGranted(granted);
    
    complianceManager.recordAuditLog(log);
}
```

### 7.2 生成合规报告

```java
Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
Instant to = Instant.now();

ComplianceReport report = complianceManager.generateReport(from, to);

System.out.println("合规评分: " + report.getComplianceScore());
System.out.println("评级: " + report.getGrade());
System.out.println("违规总数: " + report.getTotalViolations());
```

### 7.3 解决违规

```java
complianceManager.resolveViolation(
    "violation_xxx",
    "admin",
    "已确认并修复访问权限配置"
);
```
