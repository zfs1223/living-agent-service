# Data Aggregator - 数据聚合器技能

> 多源数据自动汇总分析

## 技能概述

Data Aggregator 从多个数据源自动收集、汇总、分析数据，为主动预判和报告生成提供数据支撑。

## 核心能力

| 能力 | 说明 |
|------|------|
| **多源采集** | 从多种数据源采集数据 |
| **数据清洗** | 清洗和标准化数据 |
| **智能汇总** | 自动汇总和聚合数据 |
| **趋势分析** | 分析数据趋势和模式 |

## 技能参数

### 聚合数据

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | aggregate_data |
| `purpose` | string | 是 | 用途: WEEKLY_REPORT/PROJECT_STATUS/TEAM_METRICS |
| `params.userId` | string | 否 | 用户ID |
| `params.dateRange` | object | 否 | 日期范围 |
| `params.sources` | array | 否 | 数据源列表 |

### 聚合项目数据

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | aggregate_project_data |
| `projectId` | string | 是 | 项目ID |
| `metrics` | array | 否 | 指标列表: PROGRESS/QUALITY/TEAM |

### 聚合团队数据

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | aggregate_team_data |
| `teamId` | string | 是 | 团队ID |
| `period` | string | 否 | 周期: DAY/WEEK/MONTH |
| `metrics` | array | 否 | 指标列表 |

## 使用示例

### 聚合周报数据

```json
{
  "action": "aggregate_data",
  "purpose": "WEEKLY_REPORT",
  "params": {
    "userId": "user_001",
    "dateRange": {
      "start": "2026-03-03",
      "end": "2026-03-07"
    },
    "sources": ["GITLAB", "JIRA", "JENKINS", "CALENDAR"]
  }
}
```

**响应:**

```json
{
  "aggregationId": "AGG-20260306-001",
  "purpose": "WEEKLY_REPORT",
  "dateRange": {
    "start": "2026-03-03",
    "end": "2026-03-07"
  },
  "data": {
    "gitlab": {
      "commits": 12,
      "mergeRequests": 3,
      "codeReviews": 5,
      "linesAdded": 1500,
      "linesDeleted": 300
    },
    "jira": {
      "tasksCompleted": 8,
      "tasksInProgress": 2,
      "storyPoints": 15,
      "bugsFixed": 5
    },
    "jenkins": {
      "builds": 25,
      "successRate": 0.96,
      "deployments": 4
    },
    "calendar": {
      "meetings": 5,
      "totalMeetingHours": 6
    }
  },
  "summary": {
    "workItems": 10,
    "productivity": "HIGH",
    "highlights": [
      "完成了用户认证模块开发",
      "修复了5个Bug"
    ]
  }
}
```

## 支持的数据源

| 类型 | 数据源 | 数据内容 |
|------|--------|---------|
| **代码仓库** | GitLab/GitHub | 提交记录、MR、代码审查 |
| **项目管理** | Jira/Trello | 任务、进度、工时 |
| **CI/CD** | Jenkins/GitLab CI | 构建、部署、测试 |
| **监控** | Prometheus/Grafana | 性能指标、告警 |
| **日志** | ELK/Loki | 应用日志、错误日志 |
| **日历** | Outlook/钉钉 | 会议、事件 |
| **工时** | 内部系统 | 工时记录 |

## 触发词

- 数据汇总、聚合、统计、分析

## 神经元集成

```java
@Service
public class DataAggregator {
    
    @Autowired
    private GitLabClient gitLabClient;
    
    @Autowired
    private JiraClient jiraClient;
    
    @Autowired
    private JenkinsClient jenkinsClient;
    
    public AggregatedData aggregateForWeeklyReport(String userId, LocalDate start, LocalDate end) {
        // 并行采集数据
        CompletableFuture<GitLabData> gitLabFuture = CompletableFuture.supplyAsync(
            () -> gitLabClient.getUserActivity(userId, start, end));
        CompletableFuture<JiraData> jiraFuture = CompletableFuture.supplyAsync(
            () -> jiraClient.getUserTasks(userId, start, end));
        CompletableFuture<JenkinsData> jenkinsFuture = CompletableFuture.supplyAsync(
            () -> jenkinsClient.getUserBuilds(userId, start, end));
        
        // 等待所有数据采集完成
        CompletableFuture.allOf(gitLabFuture, jiraFuture, jenkinsFuture).join();
        
        // 汇总数据
        return AggregatedData.builder()
            .gitlab(gitLabFuture.join())
            .jira(jiraFuture.join())
            .jenkins(jenkinsFuture.join())
            .summary(generateSummary(gitLabFuture.join(), jiraFuture.join()))
            .build();
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    data-aggregator:
      enabled: true
      
      # 数据源配置
      sources:
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
          
      # 缓存配置
      cache:
        enabled: true
        ttl: 3600            # 缓存1小时
        
      # 限流配置
      rate-limit:
        enabled: true
        requests-per-minute: 60
```

## 复用来源

| 组件 | 来源 | 复用方式 |
|------|------|----------|
| 数据采集 | [Langfuse](../../../../../antigravity-awesome-skills-main/skills/langfuse/SKILL.md) | 数据处理模式 |
| 指标聚合 | [last30days](../../../../../antigravity-awesome-skills-main/skills/last30days/SKILL.md) | 聚合逻辑 |

## 相关技能

- [weekly-report-generator](../weekly-report-generator/SKILL.md) - 周报生成器
- [project-risk-monitor](../project-risk-monitor/SKILL.md) - 项目风险监控器
