# Pattern Predictor - 用户行为模式预测器技能

> 习惯识别与需求预测

## 技能概述

Pattern Predictor 是贾维斯模式的核心技能，通过分析用户的历史行为数据，识别用户习惯模式，预测用户可能的需求，并提前准备相关资源。这是实现"个性化主动服务"的关键能力。

## 核心能力

| 能力 | 说明 |
|------|------|
| **习惯识别** | 识别用户的操作习惯和时间规律 |
| **需求预测** | 预测用户下一步可能需要的资源 |
| **偏好学习** | 学习用户的偏好和风格 |
| **智能预加载** | 提前加载用户可能需要的工具和数据 |

## 技能参数

### 分析用户习惯

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | analyze_patterns |
| `userId` | string | 是 | 用户ID |
| `analysisType` | string | 否 | COMPREHENSIVE/QUICK |
| `lookbackDays` | int | 否 | 回溯天数，默认30天 |

### 预测用户需求

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | predict_needs |
| `userId` | string | 是 | 用户ID |
| `context` | object | 否 | 当前上下文 |

### 学习用户偏好

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | learn_preference |
| `userId` | string | 是 | 用户ID |
| `feedback` | object | 是 | 反馈信息 |

## 使用示例

### 分析用户习惯

```json
{
  "action": "analyze_patterns",
  "userId": "user_001",
  "analysisType": "COMPREHENSIVE",
  "lookbackDays": 30
}
```

**响应:**

```json
{
  "userId": "user_001",
  "patterns": [
    {
      "patternType": "LOGIN_TIME",
      "description": "用户通常在早上9:00-9:30登录",
      "confidence": 0.85,
      "data": {
        "avgLoginTime": "09:15",
        "variance": "15min",
        "frequency": "工作日"
      }
    },
    {
      "patternType": "WEEKLY_REPORT",
      "description": "用户每周五下午4点开始写周报",
      "confidence": 0.92,
      "data": {
        "dayOfWeek": "FRIDAY",
        "timeRange": "16:00-18:00",
        "duration": "45min"
      }
    },
    {
      "patternType": "TOOL_SEQUENCE",
      "description": "登录后依次查看GitLab→Jira→Jenkins",
      "confidence": 0.78,
      "data": {
        "sequence": ["GitLab", "Jira", "Jenkins"],
        "avgInterval": "5min"
      }
    }
  ]
}
```

### 预测用户需求

```json
{
  "action": "predict_needs",
  "userId": "user_001",
  "context": {
    "currentTime": "2026-03-06T16:00:00Z",
    "currentAction": "LOGIN"
  }
}
```

**响应:**

```json
{
  "predictions": [
    {
      "needType": "PROJECT_STATUS",
      "description": "用户可能需要查看项目状态",
      "confidence": 0.88,
      "suggestedAction": "PRELOAD_PROJECT_DASHBOARD",
      "params": {
        "projectId": "PRJ-2026-001"
      }
    },
    {
      "needType": "WEEKLY_REPORT",
      "description": "用户可能需要准备周报",
      "confidence": 0.75,
      "suggestedAction": "PREPARE_WEEKLY_REPORT_TEMPLATE",
      "params": {
        "templateType": "TECH"
      }
    }
  ]
}
```

### 学习用户偏好

```json
{
  "action": "learn_preference",
  "userId": "user_001",
  "feedback": {
    "type": "RESPONSE_STYLE",
    "preference": "CONCISE",
    "context": "用户偏好简洁的回复风格"
  }
}
```

## 行为模式类型

### 时间模式

| 模式类型 | 说明 | 示例 |
|---------|------|------|
| **登录时间** | 用户常登录的时间段 | 每天早上9点登录 |
| **操作周期** | 重复操作的周期 | 每周五下午写周报 |
| **活跃时段** | 用户最活跃的时间段 | 上午10-12点最活跃 |

### 操作模式

| 模式类型 | 说明 | 示例 |
|---------|------|------|
| **常用工具** | 用户最常使用的工具 | GitLab, Jira, Jenkins |
| **常用查询** | 用户常查询的数据 | 项目进度、团队工时 |
| **操作序列** | 常见的操作顺序 | 登录→查项目→看任务→写代码 |

### 偏好模式

| 模式类型 | 说明 | 示例 |
|---------|------|------|
| **交互风格** | 用户偏好的交互方式 | 简洁回复 vs 详细解释 |
| **响应时机** | 用户期望的响应时机 | 即时响应 vs 批量汇总 |
| **通知偏好** | 用户偏好的通知方式 | 钉钉 vs 邮件 |

## 触发词

- 习惯、模式、预测、偏好
- pattern、habit、predict、preference

## 神经元集成

```java
public abstract class AbstractNeuron implements Neuron {
    
    @Autowired
    protected PatternPredictor patternPredictor;
    
    // 用户登录时预测需求
    protected void onUserLogin(String userId) {
        // 分析用户习惯
        PatternAnalysisResult patterns = patternPredictor.analyzePatterns(userId);
        
        // 预测用户需求
        List<PredictedNeed> predictions = patternPredictor.predictNeeds(userId, 
            Map.of("currentTime", Instant.now(), "currentAction", "LOGIN"));
        
        // 预加载资源
        for (PredictedNeed need : predictions) {
            if (need.getConfidence() > 0.8) {
                preloadResource(need);
            }
        }
    }
    
    // 学习用户反馈
    protected void learnFromFeedback(String userId, UserFeedback feedback) {
        patternPredictor.learnPreference(userId, feedback);
    }
    
    // 预加载资源
    private void preloadResource(PredictedNeed need) {
        switch (need.getNeedType()) {
            case "PROJECT_STATUS" -> preloadProjectDashboard(need.getParams());
            case "WEEKLY_REPORT" -> prepareWeeklyReportTemplate(need.getParams());
            case "MEETING_PREP" -> prepareMeetingMaterials(need.getParams());
        }
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    pattern-predictor:
      enabled: true
      
      # 数据采集配置
      data-collection:
        enabled: true
        actions-to-track: [LOGIN, QUERY, TOOL_CALL, NAVIGATION]
        retention-days: 90
        
      # 模式识别配置
      pattern-recognition:
        min-support: 0.3        # 最小支持度
        min-confidence: 0.6     # 最小置信度
        time-window: 30         # 时间窗口(天)
        
      # 预测配置
      prediction:
        enabled: true
        confidence-threshold: 0.7
        max-predictions: 5
        
      # 预加载配置
      preload:
        enabled: true
        max-preload-items: 10
        preload-ttl: 300        # 预加载数据有效期(秒)
```

## 隐私保护

| 数据类型 | 存储策略 | 使用范围 |
|---------|---------|---------|
| 操作日志 | 加密存储 | 仅模式分析 |
| 时间模式 | 匿名化 | 预测服务 |
| 偏好数据 | 用户可控 | 个性化服务 |
| 敏感操作 | 不记录 | - |

## 相关技能

- [proactive-agent](../proactive-agent/SKILL.md) - 主动代理
- [event-driven-notifier](../event-driven-notifier/SKILL.md) - 事件驱动通知器
- [risk-predictor](../risk-predictor/SKILL.md) - 风险预警器
