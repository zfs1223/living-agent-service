# Risk Predictor - 风险预警预判器技能

> 项目/预算/人员/系统风险预测与预警

## 技能概述

Risk Predictor 是贾维斯模式的核心技能，通过分析多维度数据，预测潜在风险并提前预警。支持项目延期、预算超支、人员流失、系统故障等多种风险类型的预测。

## 核心能力

| 能力 | 说明 |
|------|------|
| **项目风险预测** | 进度偏差、延期概率、资源风险 |
| **预算风险预测** | 支出趋势、超支概率、成本异常 |
| **人员风险预测** | 离职倾向、绩效异常、团队稳定性 |
| **系统风险预测** | 故障预测、性能瓶颈、安全风险 |
| **合规风险预测** | 操作异常、权限滥用、数据泄露 |

## 技能参数

### 风险分析

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | 操作类型: analyze_project/analyze_budget/analyze_personnel/analyze_system |
| `targetId` | string | 是 | 目标ID (项目ID/部门ID/用户ID) |
| `analysisType` | string | 否 | 分析类型: QUICK/COMPREHENSIVE |
| `lookbackDays` | int | 否 | 回溯天数，默认30天 |

### 风险评估

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | assess_overall |
| `scope` | string | 是 | 范围: PROJECT/DEPARTMENT/SYSTEM |
| `scopeId` | string | 是 | 范围ID |

## 使用示例

### 项目风险分析

```json
{
  "action": "analyze_project",
  "targetId": "PRJ-2026-001",
  "analysisType": "COMPREHENSIVE"
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
      "type": "PROJECT_DELAY",
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
  ]
}
```

### 预算风险分析

```json
{
  "action": "analyze_budget",
  "targetId": "DEPT-TECH",
  "lookbackDays": 90
}
```

### 综合风险评估

```json
{
  "action": "assess_overall",
  "scope": "DEPARTMENT",
  "scopeId": "DEPT-TECH"
}
```

## 风险类型与指标

### 项目风险

| 指标 | 阈值 | 风险等级 |
|------|------|---------|
| 进度偏差 | > 20% | HIGH |
| 任务完成率 | < 70% | MEDIUM |
| 资源利用率 | > 90% 或 < 50% | MEDIUM |
| 需求变更率 | > 30% | HIGH |

### 预算风险

| 指标 | 阈值 | 风险等级 |
|------|------|---------|
| 预算消耗率 | > 时间进度 * 1.2 | HIGH |
| 成本偏差 | > 15% | MEDIUM |
| 付款延迟 | > 30天 | MEDIUM |

### 人员风险

| 指标 | 阈值 | 风险等级 |
|------|------|---------|
| 活跃度下降 | > 50% | HIGH |
| 请假频率 | 异常增加 | MEDIUM |
| 绩效波动 | > 30% | MEDIUM |

### 系统风险

| 指标 | 阈值 | 风险等级 |
|------|------|---------|
| CPU使用率 | > 80% 持续 | HIGH |
| 内存使用率 | > 90% | CRITICAL |
| 错误率 | > 5% | HIGH |
| 响应时间 | > 3s | MEDIUM |

## 触发词

- 风险、预警、预测、延期、超支
- risk、predict、warning、alert

## 神经元集成

```java
public abstract class AbstractNeuron implements Neuron {
    
    @Autowired
    protected RiskPredictor riskPredictor;
    
    // 定期风险检查
    @Scheduled(cron = "0 0 10 * * ?")  // 每天上午10点
    protected void dailyRiskCheck() {
        List<Project> myProjects = getMyProjects();
        
        for (Project project : myProjects) {
            RiskAnalysisResult result = riskPredictor.analyzeProject(project.getId());
            
            if (result.getOverallRisk() == RiskLevel.HIGH || 
                result.getOverallRisk() == RiskLevel.CRITICAL) {
                // 主动预警
                pushNotification(
                    project.getManagerId(),
                    "项目风险预警: " + project.getName(),
                    result.getSummary(),
                    NotificationPriority.HIGH
                );
            }
        }
    }
    
    // 预算监控
    protected void monitorBudget(String departmentId) {
        BudgetRiskAnalysis result = riskPredictor.analyzeBudget(departmentId);
        
        if (result.getOverspendProbability() > 0.7) {
            pushNotification(
                getDepartmentHead(departmentId),
                "预算超支风险预警",
                "预算超支概率: " + (result.getOverspendProbability() * 100) + "%",
                NotificationPriority.HIGH
            );
        }
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    risk-predictor:
      enabled: true
      
      # 风险阈值配置
      thresholds:
        project:
          progress_deviation: 0.2
          task_completion_rate: 0.7
          resource_utilization_high: 0.9
          resource_utilization_low: 0.5
        budget:
          burn_rate_factor: 1.2
          cost_deviation: 0.15
        personnel:
          activity_drop: 0.5
          performance_volatility: 0.3
        system:
          cpu_threshold: 0.8
          memory_threshold: 0.9
          error_rate: 0.05
          
      # 预测配置
      prediction:
        interval: 3600  # 每小时预测一次
        lookback_days: 30
        confidence_threshold: 0.6
        
      # 预警配置
      alert:
        critical_channels: [WEBSOCKET, DINGTALK, SMS]
        high_channels: [WEBSOCKET, DINGTALK]
        medium_channels: [WEBSOCKET]
```

## 复用来源

本技能参考以下开源项目实现：

| 组件 | 来源 | 复用方式 |
|------|------|----------|
| 风险评估框架 | [Risk Manager](../../../../../antigravity-awesome-skills-main/skills/risk-manager/SKILL.md) | 风险指标设计 |
| 告警机制 | [Trackio Alerts](../../../../../skills-huggingface/skills/hugging-face-trackio/references/alerts.md) | 告警级别定义 |

## 相关技能

- [event-driven-notifier](../event-driven-notifier/SKILL.md) - 事件驱动通知器
- [project-risk-monitor](../../ops/project-risk-monitor/SKILL.md) - 项目风险监控器
- [system-health-diagnoser](../../ops/system-health-diagnoser/SKILL.md) - 系统健康诊断器
