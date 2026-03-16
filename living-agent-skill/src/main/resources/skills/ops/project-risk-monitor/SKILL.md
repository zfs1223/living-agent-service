# Project Risk Monitor - 项目风险监控器技能

> 延期风险预警

## 技能概述

Project Risk Monitor 持续监控项目进度，分析风险指标，在项目出现延期风险时主动预警并提供缓解建议。

## 核心能力

| 能力 | 说明 |
|------|------|
| **进度监控** | 实时监控项目进度偏差 |
| **风险识别** | 识别延期、资源、质量风险 |
| **预警通知** | 多渠道风险预警通知 |
| **建议生成** | 自动生成缓解措施建议 |

## 技能参数

### 监控项目

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | monitor_project |
| `projectId` | string | 是 | 项目ID |
| `monitorType` | string | 否 | CONTINUOUS/ONCE |

### 获取风险报告

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | get_risk_report |
| `projectId` | string | 是 | 项目ID |

## 使用示例

### 监控项目风险

```json
{
  "action": "monitor_project",
  "projectId": "PRJ-2026-001",
  "monitorType": "CONTINUOUS"
}
```

### 获取风险报告

```json
{
  "action": "get_risk_report",
  "projectId": "PRJ-2026-001"
}
```

**响应:**

```json
{
  "projectId": "PRJ-2026-001",
  "projectName": "用户中心重构",
  "overallRisk": "HIGH",
  "risks": [
    {
      "type": "SCHEDULE_DELAY",
      "level": "HIGH",
      "probability": 0.75,
      "description": "项目存在较高延期风险",
      "indicators": {
        "progressDeviation": 0.25,
        "delayedTasks": 5,
        "criticalPathDelay": "3天"
      },
      "impact": "可能延期2-3周",
      "suggestions": [
        "重新评估关键路径",
        "增加开发资源",
        "与产品沟通调整范围"
      ]
    }
  ],
  "recommendations": [
    "建议召开项目风险评估会议",
    "考虑申请额外资源支持",
    "与客户沟通预期调整"
  ]
}
```

## 风险指标

| 指标 | 计算方式 | 预警阈值 |
|------|---------|---------|
| **进度偏差率** | (计划进度 - 实际进度) / 计划进度 | > 20% |
| **任务延期率** | 延期任务数 / 总任务数 | > 15% |
| **资源冲突率** | 冲突资源数 / 总资源数 | > 10% |
| **需求变更率** | 变更需求数 / 总需求数 | > 30% |
| **缺陷密度** | 缺陷数 / 代码行数 | > 阈值 |

## 触发词

- 项目风险、延期、进度、监控

## 神经元集成

```java
@Service
public class ProjectRiskMonitor {
    
    @Autowired
    private RiskPredictor riskPredictor;
    
    @Autowired
    private EventDrivenNotifier notifier;
    
    // 每小时检查项目风险
    @Scheduled(fixedRate = 3600000)
    public void checkProjectRisks() {
        List<Project> activeProjects = projectService.getActiveProjects();
        
        for (Project project : activeProjects) {
            RiskReport report = riskPredictor.analyzeProject(project.getId());
            
            if (report.getOverallRisk() == RiskLevel.HIGH || 
                report.getOverallRisk() == RiskLevel.CRITICAL) {
                
                // 主动预警
                notifier.pushNotification(Notification.builder()
                    .userId(project.getManagerId())
                    .title("项目风险预警: " + project.getName())
                    .content(report.getSummary())
                    .type(NotificationType.ALERT)
                    .priority(NotificationPriority.HIGH)
                    .channels(resolveChannels(report.getOverallRisk()))
                    .data(Map.of("riskReport", report))
                    .build());
            }
        }
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    project-risk-monitor:
      enabled: true
      
      # 监控配置
      monitor:
        interval: 3600          # 每小时检查一次
        projects: []            # 空表示监控所有项目
        
      # 风险阈值
      thresholds:
        progress_deviation: 0.2
        task_delay_rate: 0.15
        resource_conflict_rate: 0.1
        requirement_change_rate: 0.3
        
      # 预警配置
      alert:
        high_channels: [WEBSOCKET, DINGTALK, EMAIL]
        medium_channels: [WEBSOCKET]
        low_channels: [SYSTEM_MESSAGE]
```

## 复用来源

| 组件 | 来源 | 复用方式 |
|------|------|----------|
| 风险评估 | [Risk Manager](../../../../../antigravity-awesome-skills-main/skills/risk-manager/SKILL.md) | 风险指标设计 |
| 告警机制 | [Trackio Alerts](../../../../../skills-huggingface/skills/hugging-face-trackio/references/alerts.md) | 告警级别定义 |

## 相关技能

- [risk-predictor](../../core/risk-predictor/SKILL.md) - 风险预警预判器
- [event-driven-notifier](../../core/event-driven-notifier/SKILL.md) - 事件驱动通知器
