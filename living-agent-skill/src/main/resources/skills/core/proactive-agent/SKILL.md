# Proactive Agent - 主动代理技能

> 智能体主动执行、任务调度和自主行动能力

## 技能概述

Proactive Agent 是神经元的核心技能，使智能体能够主动执行任务、调度工作和自主行动。这是"生命智能体"的关键能力，让智能体不只是被动响应，而是能够主动思考、规划和执行。

## 核心能力

| 能力 | 说明 |
|------|------|
| **主动监控** | 持续监控环境和任务状态 |
| **自主决策** | 基于目标自主做出决策 |
| **任务调度** | 定时任务和条件触发任务 |
| **自我驱动** | 无需用户触发即可行动 |
| **目标导向** | 围绕目标持续优化行动 |

## 技能参数

### 创建主动任务

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task_type` | string | 是 | 任务类型: scheduled/conditional/continuous |
| `description` | string | 是 | 任务描述 |
| `schedule` | object | 否 | 调度配置 (定时任务) |
| `condition` | string | 否 | 触发条件 (条件任务) |
| `actions` | array | 是 | 执行动作列表 |
| `priority` | string | 否 | 优先级: critical/high/medium/low |

### 调度配置

| 参数 | 类型 | 说明 |
|------|------|------|
| `type` | string | once/daily/weekly/monthly/cron |
| `time` | string | 执行时间 (HH:mm) |
| `cron` | string | Cron 表达式 |
| `timezone` | string | 时区 |

### 条件配置

| 参数 | 类型 | 说明 |
|------|------|------|
| `type` | string | 事件类型: data_change/threshold/time_elapsed/custom |
| `expression` | string | 条件表达式 |
| `check_interval` | int | 检查间隔 (秒) |

## 使用示例

### 定时任务

```json
{
  "action": "create_task",
  "task_type": "scheduled",
  "description": "每日汇总技术部工单状态",
  "schedule": {
    "type": "daily",
    "time": "09:00",
    "timezone": "Asia/Shanghai"
  },
  "actions": [
    {
      "type": "query",
      "target": "jira",
      "params": {"status": "open", "department": "tech"}
    },
    {
      "type": "summarize",
      "params": {"format": "markdown"}
    },
    {
      "type": "notify",
      "target": "dingtalk",
      "params": {"channel": "tech-daily"}
    }
  ],
  "priority": "high"
}
```

### 条件触发任务

```json
{
  "action": "create_task",
  "task_type": "conditional",
  "description": "服务器异常告警",
  "condition": {
    "type": "threshold",
    "expression": "cpu_usage > 80 OR memory_usage > 90",
    "check_interval": 60
  },
  "actions": [
    {
      "type": "alert",
      "params": {"severity": "critical", "channels": ["dingtalk", "sms"]}
    },
    {
      "type": "diagnose",
      "params": {"collect_logs": true}
    }
  ],
  "priority": "critical"
}
```

### 持续任务

```json
{
  "action": "create_task",
  "task_type": "continuous",
  "description": "知识库自动更新",
  "actions": [
    {
      "type": "crawl",
      "params": {"sources": ["internal_docs", "wiki"]}
    },
    {
      "type": "extract",
      "params": {"extract_type": "knowledge"}
    },
    {
      "type": "store",
      "params": {"target": "knowledge_base"}
    }
  ],
  "schedule": {
    "type": "weekly",
    "time": "02:00",
    "day": "sunday"
  }
}
```

## 主动行为模式

### 1. 监控模式

```
┌─────────────────────────────────────────────────────────────────┐
│                    监控模式                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  持续监控 → 检测变化 → 评估影响 → 决策行动                          │
│                                                                 │
│  监控对象:                                                        │
│  ├── 系统状态 (CPU/内存/磁盘/网络)                                 │
│  ├── 业务指标 (订单/用户/转化率)                                   │
│  ├── 知识库 (更新/过期/质量)                                       │
│  ├── 技能状态 (可用性/性能)                                        │
│  └── 外部数据 (新闻/市场/竞品)                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2. 学习模式

```
┌─────────────────────────────────────────────────────────────────┐
│                    学习模式                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  收集数据 → 分析模式 → 提取知识 → 优化行为                          │
│                                                                 │
│  学习内容:                                                        │
│  ├── 用户偏好 (交互方式/响应风格)                                  │
│  ├── 业务知识 (流程/规则/最佳实践)                                 │
│  ├── 技能效果 (成功率/用户反馈)                                    │
│  └── 环境变化 (新工具/新需求)                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3. 成长模式

```
┌─────────────────────────────────────────────────────────────────┐
│                    成长模式                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  发现需求 → 搜索技能 → 安装学习 → 应用验证                          │
│                                                                 │
│  成长维度:                                                        │
│  ├── 技能扩展 (安装新技能)                                        │
│  ├── 知识积累 (学习新知识)                                        │
│  ├── 能力提升 (优化现有能力)                                      │
│  └── 人格进化 (调整行为参数)                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 神经元集成

每个神经元都应具备此技能：

```java
public abstract class AbstractNeuron implements Neuron {
    
    // 主动任务队列
    protected Queue<ProactiveTask> proactiveTasks = new PriorityQueue<>();
    
    // 主动执行线程
    protected void startProactiveLoop() {
        scheduler.scheduleAtFixedRate(() -> {
            // 1. 检查监控条件
            checkMonitoringConditions();
            
            // 2. 执行到期任务
            executeDueTasks();
            
            // 3. 学习和优化
            learnAndOptimize();
            
            // 4. 发现新需求
            discoverNewNeeds();
            
        }, 0, 1, TimeUnit.MINUTES);
    }
    
    // 自主决策
    protected void autonomousDecision(String context) {
        // 分析当前状态
        State currentState = analyzeCurrentState();
        
        // 评估可选行动
        List<Action> actions = evaluatePossibleActions(context);
        
        // 选择最优行动
        Action bestAction = selectBestAction(actions, currentState);
        
        // 执行行动
        executeAction(bestAction);
    }
}
```

## 主动行为示例

### 示例 1: 主动知识更新

```java
// 智能体主动发现知识库过时
if (knowledgeBase.hasExpiredEntries()) {
    // 主动搜索更新
    List<KnowledgeUpdate> updates = tavilySearch.searchUpdates();
    
    // 自动更新知识库
    knowledgeBase.applyUpdates(updates);
    
    // 通知相关部门大脑
    notifyRelatedBrains(updates);
}
```

### 示例 2: 主动技能安装

```java
// 智能体发现缺少某项能力
if (taskRequiresSkill("pdf-processing") && !hasSkill("nano-pdf")) {
    // 主动搜索技能
    SkillRecommendation rec = findSkills.search("pdf-processing");
    
    // 自动安装
    if (rec.getPriority() == Priority.HIGH) {
        findSkills.install(rec.getSkillId());
    }
}
```

### 示例 3: 主动健康检查

```java
// 定时健康检查
@Scheduled(cron = "0 */5 * * * *")  // 每5分钟
public void proactiveHealthCheck() {
    // 检查各组件状态
    HealthStatus status = healthMonitor.checkHealth();
    
    // 发现问题主动修复
    if (status.hasIssues()) {
        for (HealthIssue issue : status.getIssues()) {
            if (issue.canAutoFix()) {
                autoFix(issue);
            } else {
                alert(issue);
            }
        }
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    proactive-agent:
      enabled: true
      
      # 主动行为配置
      proactive:
        enabled: true
        check-interval-seconds: 60
        max-concurrent-tasks: 5
        
      # 自动学习
      auto-learning:
        enabled: true
        interval-hours: 24
        
      # 自动技能安装
      auto-skill-install:
        enabled: true
        priority-threshold: high
        require-approval: false
        
      # 监控配置
      monitoring:
        system-health: true
        knowledge-freshness: true
        skill-performance: true
        user-satisfaction: true
```

## 触发词

- 主动、自动、定时
- 监控、检查、扫描
- proactive、auto、schedule

## 安全边界

| 行为 | 需要审批 | 说明 |
|------|---------|------|
| 安装新技能 | 高优先级以下需要 | 防止恶意技能 |
| 修改配置 | 需要 | 防止配置破坏 |
| 发送通知 | 不需要 | 低风险 |
| 数据删除 | 需要 | 高风险操作 |
| 外部请求 | 不需要 | 已有安全策略 |

## 相关技能

- [tavily-search](../tavily-search/SKILL.md) - 主动搜索信息
- [find-skills](../find-skills/SKILL.md) - 主动发现技能
- [skill-creator](../../tech/skill-creator/SKILL.md) - 主动创建技能
