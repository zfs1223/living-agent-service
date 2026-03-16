# Weekly Report Generator - 周报自动生成器技能

> 主动收集数据生成周报

## 技能概述

Weekly Report Generator 是主动预判能力的典型应用场景。每周五下午自动收集用户的工作数据，生成周报草稿，并主动推送给用户确认。

## 核心能力

| 能力 | 说明 |
|------|------|
| **多源数据收集** | 从GitLab/Jira/Jenkins/日历等收集数据 |
| **智能内容生成** | 基于数据自动生成周报内容 |
| **模板适配** | 根据部门/角色适配不同模板 |
| **主动推送** | 生成后主动推送给用户 |

## 技能参数

### 生成周报

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | generate_weekly_report |
| `userId` | string | 是 | 用户ID |
| `weekRange` | string | 否 | 周范围，格式: YYYY-WXX |
| `templateType` | string | 否 | 模板类型: TECH/ADMIN/SALES |

### 调度周报生成

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | schedule_weekly_report |
| `userId` | string | 是 | 用户ID |
| `triggerTime` | string | 是 | 触发时间，如 FRIDAY_16:00 |
| `autoNotify` | bool | 否 | 是否自动通知，默认true |
| `channels` | array | 否 | 通知渠道 |

## 使用示例

### 生成周报

```json
{
  "action": "generate_weekly_report",
  "userId": "user_001",
  "weekRange": "2026-W10",
  "templateType": "TECH"
}
```

**响应:**

```json
{
  "reportId": "weekly-2026-W10-user_001",
  "weekRange": "2026-03-03 至 2026-03-07",
  "sections": {
    "summary": "本周完成了用户认证模块的开发和测试，参与了3次技术评审会议。",
    "completed": [
      "完成用户登录功能开发",
      "完成JWT认证实现",
      "修复了5个Bug"
    ],
    "inProgress": [
      "用户权限管理模块开发",
      "API文档编写"
    ],
    "nextWeek": [
      "完成权限管理模块",
      "开始集成测试"
    ],
    "issues": [
      "测试环境不稳定，影响调试效率"
    ],
    "metrics": {
      "commits": 12,
      "mrs": 3,
      "tasksCompleted": 8,
      "tasksInProgress": 2,
      "meetings": 3,
      "hoursLogged": 40
    }
  },
  "status": "DRAFT",
  "editUrl": "/reports/weekly/2026-W10/edit"
}
```

### 调度自动生成

```json
{
  "action": "schedule_weekly_report",
  "userId": "user_001",
  "triggerTime": "FRIDAY_16:00",
  "autoNotify": true,
  "channels": ["WEBSOCKET", "DINGTALK"]
}
```

## 数据收集源

| 数据源 | 收集内容 | 用途 |
|--------|---------|------|
| **GitLab** | 提交记录、MR、代码审查 | 技术工作统计 |
| **Jira** | 任务完成、进行中、新建 | 任务进度 |
| **Jenkins** | 构建、部署记录 | 工程活动 |
| **日历** | 会议、重要事件 | 工作安排 |
| **工时系统** | 工时记录 | 时间分配 |

## 触发词

- 周报、weekly、报告、总结

## 神经元集成

```java
@Service
public class WeeklyReportGenerator {
    
    @Autowired
    private DataAggregator dataAggregator;
    
    @Autowired
    private EventDrivenNotifier notifier;
    
    @Autowired
    private TechBrain techBrain;
    
    // 每周五下午4点自动生成
    @Scheduled(cron = "0 0 16 ? * FRI", zone = "Asia/Shanghai")
    public void autoGenerateWeeklyReports() {
        List<User> activeUsers = userService.getActiveUsers();
        
        for (User user : activeUsers) {
            // 收集数据
            AggregatedData data = dataAggregator.aggregateForWeeklyReport(user.getId());
            
            // 生成周报
            WeeklyReport report = generateReport(user, data);
            
            // 主动推送
            notifier.pushNotification(Notification.builder()
                .userId(user.getId())
                .title("周报已准备好")
                .content("您的本周周报已自动生成，请查阅。")
                .type(NotificationType.REMINDER)
                .priority(NotificationPriority.MEDIUM)
                .channels(List.of("WEBSOCKET", "DINGTALK"))
                .actionUrl("/reports/weekly/" + report.getId())
                .build());
        }
    }
    
    private WeeklyReport generateReport(User user, AggregatedData data) {
        // 使用部门大脑生成内容
        return switch (user.getDepartment()) {
            case "TECH" -> techBrain.generateWeeklyReport(data);
            case "ADMIN" -> adminBrain.generateWeeklyReport(data);
            case "SALES" -> salesBrain.generateWeeklyReport(data);
            default -> mainBrain.generateWeeklyReport(data);
        };
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    weekly-report-generator:
      enabled: true
      
      # 定时配置
      schedule:
        enabled: true
        cron: "0 0 16 ? * FRI"    # 每周五下午4点
        timezone: "Asia/Shanghai"
        
      # 数据源配置
      data-sources:
        gitlab:
          enabled: true
          api-url: ${GITLAB_API_URL}
          token: ${GITLAB_TOKEN}
        jira:
          enabled: true
          api-url: ${JIRA_API_URL}
          token: ${JIRA_TOKEN}
        jenkins:
          enabled: true
          url: ${JENKINS_URL}
          
      # 模板配置
      templates:
        tech: "templates/weekly-report-tech.md"
        admin: "templates/weekly-report-admin.md"
        sales: "templates/weekly-report-sales.md"
        
      # 通知配置
      notification:
        channels: [WEBSOCKET, DINGTALK]
        template: "weekly_report_ready"
```

## 复用来源

本技能参考以下开源项目实现：

| 组件 | 来源 | 复用方式 |
|------|------|----------|
| Cron调度 | [OpenClaw Cron](../../../../../openclaw-main/src/cron/) | 调度逻辑 |
| 数据聚合 | [Langfuse](../../../../../antigravity-awesome-skills-main/skills/langfuse/) | 数据处理模式 |

## 相关技能

- [event-driven-notifier](../../core/event-driven-notifier/SKILL.md) - 事件驱动通知器
- [data-aggregator](../data-aggregator/SKILL.md) - 数据聚合器
- [proactive-agent](../../core/proactive-agent/SKILL.md) - 主动代理
