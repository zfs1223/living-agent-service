# 主动预判与主动输出设计

> 贾维斯模式主动预判能力

---

## 一、主动预判概述

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    主动预判架构                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  核心理念: 像贾维斯一样，在用户开口之前就做好准备                               │
│                                                                             │
│  四大预判类型:                                                               │
│  ├── 时间预判 (TimePredictor) - 基于时间规律                                │
│  ├── 事件预判 (EventPredictor) - 基于系统事件                               │
│  ├── 模式预判 (PatternPredictor) - 基于用户行为模式                         │
│  └── 风险预判 (RiskPredictor) - 基于风险指标                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、预判器设计

### 2.1 时间预判器

```java
public interface TimePredictor {
    // 预测下一个任务
    Optional<PredictedTask> predictNextTask(String userId);
    
    // 获取时间规律
    List<TimePattern> getTimePatterns(String userId);
    
    // 记录任务执行时间
    void recordTaskTime(String userId, String taskId, Instant time);
}
```

**使用场景:**
- 每周五下午4点生成周报
- 每天早上9点准备日报
- 每月1号生成月度报告

### 2.2 事件预判器

```java
public interface EventPredictor {
    // 根据事件预测需要的动作
    Optional<PredictedAction> predictFromEvent(SystemEvent event);
    
    // 订阅事件
    void subscribe(String eventType, EventHandler handler);
}
```

**使用场景:**
- 新员工入职 → 准备入职流程
- 项目里程碑 → 准备汇报材料
- 系统告警 → 准备诊断报告

### 2.3 模式预判器

```java
public interface PatternPredictor {
    // 分析用户行为模式
    List<UserPattern> analyzePatterns(String userId);
    
    // 预测用户下一步操作
    Optional<PredictedAction> predictNextAction(String userId);
}
```

**使用场景:**
- 用户登录后通常查看的项目
- 用户开会前通常准备的材料
- 用户下班前通常处理的任务

### 2.4 风险预判器

```java
public interface RiskPredictor {
    // 评估风险等级
    RiskAssessment assessRisk(String scope, String scopeId);
    
    // 获取风险预警
    List<RiskAlert> getRiskAlerts(String department);
}
```

**使用场景:**
- 项目延期风险
- 预算超支风险
- 系统异常风险

---

## 三、主动输出流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    主动输出完整流程                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  触发源                                                                      │
│  ├── 定时触发 (Cron表达式)                                                   │
│  ├── 事件触发 (系统事件)                                                     │
│  ├── 模式触发 (用户行为模式)                                                 │
│  └── 风险触发 (风险指标)                                                     │
│      │                                                                      │
│      ▼                                                                      │
│  Step 1: 预判任务匹配                                                        │
│  ProactiveTaskScheduler.match(trigger)                                      │
│      │                                                                      │
│      ▼                                                                      │
│  Step 2: 大脑决策 (MainBrain - Qwen3.5-27B)                                 │
│  ├── 评估任务重要性                                                         │
│  ├── 判断执行时机                                                           │
│  └── 决定执行方式 (自动执行/提示确认/静默准备)                               │
│      │                                                                      │
│      ▼                                                                      │
│  Step 3: 任务执行                                                           │
│  ├── 数据准备                                                               │
│  ├── 内容生成                                                               │
│  └── 工具调用                                                               │
│      │                                                                      │
│      ▼                                                                      │
│  Step 4: 输出分发                                                           │
│  EventDrivenNotifier.dispatch(result)                                       │
│  ├── 渠道选择: WebSocket/钉钉/飞书/邮件/短信                                 │
│  └── 格式适配                                                               │
│      │                                                                      │
│      ▼                                                                      │
│  Step 5: 效果追踪                                                           │
│  ├── 用户反馈收集                                                           │
│  └── 预判准确率统计                                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、主动任务配置

```yaml
# 周报主动生成
weekly_report:
  task_id: "generate-weekly-report"
  task_type: TIME_DRIVEN
  trigger:
    cron: "0 0 16 * * FRI"      # 每周五下午4点
  condition:
    user_status: "ACTIVE"
  action:
    type: PREPARE_AND_NOTIFY
    channel: [WEBSOCKET, DINGTALK]
    template: "weekly_report"
    include_suggestions: true
  target:
    role: "ALL_EMPLOYEES"

# 项目延期风险预警
project_delay_risk:
  task_id: "project-delay-warning"
  task_type: RISK_DRIVEN
  trigger:
    cron: "0 0 10 * * ?"        # 每天上午10点检查
  condition:
    check: "progress_deviation > 20%"
  action:
    type: NOTIFY_AND_SUGGEST
    channel: [WEBSOCKET, EMAIL]
    template: "project_delay_risk"
  target:
    role: "PROJECT_MANAGER"
```

---

## 五、通知渠道

| 渠道 | 说明 | 使用场景 |
|------|------|---------|
| **WebSocket** | 实时推送 | 在线用户即时通知 |
| **钉钉** | 企业IM | 重要通知、审批提醒 |
| **飞书** | 企业IM | 重要通知、审批提醒 |
| **邮件** | 电子邮件 | 正式通知、报告发送 |
| **短信** | 手机短信 | 紧急通知、验证码 |

---

## 六、与贾维斯模式对比

| 贾维斯特征 | living-agent-service实现 | 状态 |
|-----------|-------------------------|------|
| "先生，我检测到战甲能量不足" | RiskPredictor + EventDrivenNotifier | ✅ |
| "我已经为您准备好了会议资料" | ProactiveTaskScheduler + PREPARE动作 | ✅ |
| "建议您检查一下这个异常数据" | RiskPredictor + SUGGEST动作 | ✅ |
| "我学会了新的操作方式" | SkillGenerator + 即学即会 | ✅ |
| "根据您的习惯，我建议..." | PatternPredictor + 用户行为模式 | ✅ |
| "我已经自动处理了这个问题" | EvolutionExecutor + EXECUTE动作 | ✅ |

---

## 七、相关文档

- [02-architecture.md](./02-architecture.md) - 架构设计
- [06-evolution-system.md](./06-evolution-system.md) - 进化系统
