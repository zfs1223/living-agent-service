# System Health Diagnoser - 系统健康诊断器技能

> 主动异常检测与诊断

## 技能概述

System Health Diagnoser 持续监控系统运行状态，在检测到异常时主动进行诊断分析，生成诊断报告并通知技术人员。

## 核心能力

| 能力 | 说明 |
|------|------|
| **实时监控** | 监控系统关键指标 |
| **异常检测** | 自动检测异常模式 |
| **根因分析** | 分析异常根本原因 |
| **诊断报告** | 生成详细诊断报告 |

## 技能参数

### 健康检查

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | health_check |
| `scope` | string | 否 | FULL/QUICK |
| `components` | array | 否 | 组件列表: DATABASE/CACHE/MESSAGE_QUEUE/APPLICATION |

### 异常诊断

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | diagnose_anomaly |
| `anomalyId` | string | 是 | 异常ID |
| `symptoms` | object | 是 | 症状描述 |

## 使用示例

### 执行健康检查

```json
{
  "action": "health_check",
  "scope": "FULL",
  "components": ["DATABASE", "CACHE", "MESSAGE_QUEUE", "APPLICATION"]
}
```

**响应:**

```json
{
  "checkId": "HC-20260306-001",
  "timestamp": "2026-03-06T10:30:00Z",
  "overallStatus": "WARNING",
  "components": [
    {
      "name": "DATABASE",
      "status": "HEALTHY",
      "metrics": {
        "connections": 45,
        "maxConnections": 100,
        "queryTime": "15ms"
      }
    },
    {
      "name": "CACHE",
      "status": "WARNING",
      "metrics": {
        "hitRate": 0.65,
        "memoryUsage": 0.85
      },
      "issues": ["缓存命中率偏低", "内存使用接近上限"]
    }
  ],
  "recommendations": [
    "建议增加缓存容量",
    "检查缓存策略是否需要优化"
  ]
}
```

### 异常诊断

```json
{
  "action": "diagnose_anomaly",
  "anomalyId": "ANM-20260306-001",
  "symptoms": {
    "type": "PERFORMANCE_DEGRADATION",
    "component": "APPLICATION",
    "metrics": {
      "responseTime": "5000ms",
      "errorRate": 0.08
    }
  }
}
```

**响应:**

```json
{
  "diagnosisId": "DIA-20260306-001",
  "rootCause": {
    "type": "DATABASE_SLOW_QUERY",
    "description": "数据库慢查询导致应用响应变慢",
    "confidence": 0.85,
    "evidence": [
      "数据库连接池使用率突增到95%",
      "发现3个执行时间超过10秒的SQL",
      "应用日志显示大量数据库等待"
    ]
  },
  "solutions": [
    {
      "priority": 1,
      "action": "优化慢查询SQL",
      "estimatedEffort": "30分钟"
    },
    {
      "priority": 2,
      "action": "增加数据库连接池大小",
      "estimatedEffort": "5分钟"
    }
  ]
}
```

## 监控指标

| 类别 | 指标 | 正常范围 | 异常阈值 |
|------|------|---------|---------|
| **CPU** | 使用率 | < 70% | > 90% 持续5分钟 |
| **内存** | 使用率 | < 80% | > 95% |
| **磁盘** | 使用率 | < 80% | > 90% |
| **网络** | 延迟 | < 100ms | > 500ms |
| **应用** | 错误率 | < 1% | > 5% |
| **应用** | 响应时间 | < 1s | > 3s |

## 触发词

- 诊断、健康检查、异常、监控

## 神经元集成

```java
@Service
public class SystemHealthDiagnoser {
    
    @Autowired
    private EventDrivenNotifier notifier;
    
    @Autowired
    private RiskPredictor riskPredictor;
    
    // 每分钟采集指标
    @Scheduled(fixedRate = 60000)
    public void collectMetrics() {
        SystemMetrics metrics = collectSystemMetrics();
        
        // 检测异常
        List<Anomaly> anomalies = detectAnomalies(metrics);
        
        for (Anomaly anomaly : anomalies) {
            // 诊断
            DiagnosisResult diagnosis = diagnose(anomaly);
            
            // 通知技术人员
            notifier.pushNotification(Notification.builder()
                .userId(getTechAdminId())
                .title("系统异常: " + anomaly.getType())
                .content(diagnosis.getSummary())
                .type(NotificationType.ALERT)
                .priority(mapToPriority(anomaly.getSeverity()))
                .channels(List.of("WEBSOCKET", "DINGTALK"))
                .data(Map.of("diagnosis", diagnosis))
                .build());
        }
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    system-health-diagnoser:
      enabled: true
      
      # 监控配置
      monitor:
        interval: 60            # 每分钟采集一次
        metrics:
          - cpu_usage
          - memory_usage
          - disk_usage
          - network_latency
          - app_error_rate
          - app_response_time
          
      # 异常阈值
      thresholds:
        cpu_usage: 0.9
        memory_usage: 0.95
        disk_usage: 0.9
        network_latency: 500
        app_error_rate: 0.05
        app_response_time: 3000
```

## 复用来源

| 组件 | 来源 | 复用方式 |
|------|------|----------|
| 健康检查 | [OpenClaw Health](../../../../../openclaw-main/docs/cli/health.md) | 健康检查模式 |
| 告警机制 | [Trackio Alerts](../../../../../skills-huggingface/skills/hugging-face-trackio/references/alerts.md) | 告警级别定义 |

## 相关技能

- [risk-predictor](../../core/risk-predictor/SKILL.md) - 风险预警预判器
- [event-driven-notifier](../../core/event-driven-notifier/SKILL.md) - 事件驱动通知器
