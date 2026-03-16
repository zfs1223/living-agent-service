# Event Driven Notifier - 事件驱动通知器技能

> 多渠道消息推送能力，支持 WebSocket/钉钉/飞书/邮件/短信

## 技能概述

Event Driven Notifier 是贾维斯模式的核心技能，使智能体能够主动向用户推送消息、通知和报告。支持多种推送渠道，可根据消息优先级和用户偏好自动选择最佳渠道。

## 核心能力

| 能力 | 说明 |
|------|------|
| **多渠道推送** | WebSocket/钉钉/飞书/邮件/短信 |
| **事件订阅** | 订阅系统事件并触发通知 |
| **优先级路由** | 根据消息优先级选择推送渠道 |
| **模板渲染** | 支持通知模板和变量替换 |
| **状态追踪** | 追踪消息送达和阅读状态 |

## 技能参数

### 推送通知

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | 操作类型: push/subscribe/batch |
| `userId` | string | 是 | 目标用户ID |
| `title` | string | 是 | 通知标题 |
| `content` | string | 是 | 通知内容 |
| `type` | string | 否 | 通知类型: ALERT/REMINDER/SUGGESTION/REPORT |
| `priority` | string | 否 | 优先级: CRITICAL/HIGH/MEDIUM/LOW |
| `channels` | array | 否 | 推送渠道列表 |
| `actionUrl` | string | 否 | 操作链接 |
| `data` | object | 否 | 附加数据 |

### 订阅事件

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | subscribe |
| `eventType` | string | 是 | 事件类型 |
| `channels` | array | 是 | 推送渠道 |
| `priority` | string | 否 | 默认优先级 |
| `template` | string | 否 | 通知模板ID |
| `requiresAck` | bool | 否 | 是否需要确认 |

## 使用示例

### 推送通知

```json
{
  "action": "push",
  "userId": "user_001",
  "title": "周报已准备好",
  "content": "您的本周周报已自动生成，请查阅。",
  "type": "REMINDER",
  "priority": "MEDIUM",
  "channels": ["WEBSOCKET", "DINGTALK"],
  "actionUrl": "/reports/weekly/2026-W10",
  "data": {
    "reportId": "weekly-2026-W10",
    "generatedAt": "2026-03-06T16:00:00Z"
  }
}
```

### 订阅事件

```json
{
  "action": "subscribe",
  "eventType": "PROJECT_RISK_DETECTED",
  "channels": ["EMAIL", "DINGTALK", "SMS"],
  "priority": "HIGH",
  "template": "project_risk_alert",
  "requiresAck": true
}
```

### 批量推送

```json
{
  "action": "batch",
  "notifications": [
    {
      "userId": "user_001",
      "title": "会议提醒",
      "content": "您有一个会议将在15分钟后开始",
      "priority": "HIGH"
    },
    {
      "userId": "user_002",
      "title": "审批待处理",
      "content": "您有3个待审批事项",
      "priority": "MEDIUM"
    }
  ]
}
```

## 推送渠道配置

| 渠道 | 优先级 | 延迟要求 | 适用场景 |
|------|--------|---------|---------|
| **WebSocket** | 实时 | < 1s | 在线用户即时通知 |
| **钉钉/飞书** | 高 | < 5s | 重要事项提醒 |
| **邮件** | 中 | < 1min | 报告/文档通知 |
| **短信** | 紧急 | < 10s | 紧急事项告警 |
| **系统消息** | 低 | 异步 | 一般信息记录 |

## 通知类型

| 类型 | 说明 | 默认渠道 |
|------|------|---------|
| **ALERT** | 告警通知 | WebSocket + 钉钉 |
| **REMINDER** | 提醒通知 | WebSocket |
| **SUGGESTION** | 建议通知 | WebSocket |
| **REPORT** | 报告通知 | 邮件 + 钉钉 |
| **SYSTEM** | 系统通知 | 系统消息 |

## 触发词

- 通知、推送、告警、提醒
- notify、push、alert、reminder

## 神经元集成

每个神经元都应具备此技能：

```java
public abstract class AbstractNeuron implements Neuron {
    
    @Autowired
    protected EventDrivenNotifier notifier;
    
    protected void pushNotification(String userId, String title, String content, 
                                     NotificationPriority priority) {
        Notification notification = Notification.builder()
            .userId(userId)
            .title(title)
            .content(content)
            .priority(priority)
            .channels(resolveChannels(priority))
            .build();
        notifier.pushNotification(notification);
    }
    
    protected void subscribeToEvent(String eventType, NotificationRule rule) {
        notifier.subscribe(SystemEvent.of(eventType), rule);
    }
    
    private List<String> resolveChannels(NotificationPriority priority) {
        return switch (priority) {
            case CRITICAL -> List.of("WEBSOCKET", "DINGTALK", "SMS");
            case HIGH -> List.of("WEBSOCKET", "DINGTALK");
            case MEDIUM -> List.of("WEBSOCKET");
            case LOW -> List.of("SYSTEM_MESSAGE");
        };
    }
}
```

## 通知模板

```yaml
templates:
  project_risk_alert:
    title: "项目风险预警: {{projectName}}"
    content: |
      检测到项目 "{{projectName}}" 存在延期风险
      风险等级: {{riskLevel}}
      延期概率: {{probability}}%
      建议措施: {{suggestions}}
      
  weekly_report_ready:
    title: "周报已准备好"
    content: |
      您的本周周报已自动生成
      时间范围: {{dateRange}}
      工作项: {{taskCount}} 个
      请点击查看详情
      
  contract_expiry_alert:
    title: "合同即将到期"
    content: |
      合同 "{{contractName}}" 将在 {{daysRemaining}} 天后到期
      合同编号: {{contractId}}
      请及时处理续签事宜
```

## 配置

```yaml
living-agent:
  skills:
    event-driven-notifier:
      enabled: true
      
      channels:
        websocket:
          enabled: true
          timeout: 5000
        dingtalk:
          enabled: true
          webhook: ${DINGTALK_WEBHOOK}
        feishu:
          enabled: true
          webhook: ${FEISHU_WEBHOOK}
        email:
          enabled: true
          smtp: ${SMTP_CONFIG}
        sms:
          enabled: false
          
      priority-routing:
        CRITICAL: [WEBSOCKET, DINGTALK, SMS]
        HIGH: [WEBSOCKET, DINGTALK]
        MEDIUM: [WEBSOCKET]
        LOW: [SYSTEM_MESSAGE]
        
      batch:
        enabled: true
        interval: 5000
        max-batch-size: 100
```

## 复用来源

本技能参考以下开源项目实现：

| 组件 | 来源 | 复用方式 |
|------|------|----------|
| Alert API | Trackio Alerts | Webhook 调用方式 |
| 事件系统 | OpenClaw Hooks | 事件驱动模式 |

## 相关技能

- [proactive-agent](../proactive-agent/SKILL.md) - 主动代理
- [risk-predictor](../risk-predictor/SKILL.md) - 风险预警器
- [weekly-report-generator](../../ops/weekly-report-generator/SKILL.md) - 周报生成器
