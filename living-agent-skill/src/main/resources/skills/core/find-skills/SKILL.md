# Find Skills - 技能发现与推荐技能

> 智能技能发现、匹配和推荐系统

## 技能概述

Find Skills 是神经元的核心技能，用于自动发现、匹配和推荐相关技能。当智能体遇到需要特定能力的任务时，可以自动查找并安装所需技能。

## 核心能力

| 能力 | 说明 |
|------|------|
| **技能搜索** | 根据任务描述搜索相关技能 |
| **智能匹配** | AI 驱动的技能匹配算法 |
| **技能安装** | 自动下载和安装技能 |
| **技能推荐** | 基于上下文的技能推荐 |
| **技能评估** | 评估技能质量和适用性 |

## 技能参数

### 搜索技能

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | string | 是 | 技能需求描述 |
| `category` | string | 否 | 技能类别筛选 |
| `brain` | string | 否 | 目标大脑筛选 |
| `limit` | int | 否 | 返回数量限制 |

### 安装技能

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `skill_id` | string | 是 | 技能ID或名称 |
| `source` | string | 否 | 来源: local/remote/github |
| `version` | string | 否 | 版本号 |

### 推荐技能

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `context` | string | 是 | 当前任务上下文 |
| `current_skills` | array | 否 | 已有技能列表 |

## 使用示例

### 搜索技能

```json
{
  "action": "search",
  "query": "我需要处理PDF文档",
  "category": "document",
  "limit": 5
}
```

**响应:**

```json
{
  "skills": [
    {
      "id": "nano-pdf",
      "name": "PDF Natural Editor",
      "description": "PDF自然语言编辑",
      "relevance": 0.95,
      "brain": "AdminBrain",
      "installed": true
    },
    {
      "id": "pdf-extractor",
      "name": "PDF Extractor",
      "description": "PDF内容提取",
      "relevance": 0.85,
      "brain": "AdminBrain",
      "installed": false
    }
  ]
}
```

### 安装技能

```json
{
  "action": "install",
  "skill_id": "tavily-search",
  "source": "github",
  "version": "latest"
}
```

**响应:**

```json
{
  "success": true,
  "skill": {
    "id": "tavily-search",
    "name": "Tavily Search",
    "version": "1.0.0",
    "installed_at": "2024-01-15T10:30:00Z"
  },
  "message": "技能安装成功"
}
```

### 推荐技能

```json
{
  "action": "recommend",
  "context": "用户想要分析销售数据并生成报告",
  "current_skills": ["xlsx-official", "summarize"]
}
```

**响应:**

```json
{
  "recommendations": [
    {
      "skill_id": "data-analyst",
      "reason": "数据分析能力可增强数据处理",
      "priority": "high"
    },
    {
      "skill_id": "chart-generator",
      "reason": "图表生成可增强报告可视化",
      "priority": "medium"
    }
  ]
}
```

## 技能来源

### 本地技能库

```
./data/skills/
├── tech/
│   ├── coding-agent/
│   └── github/
├── admin/
│   ├── nano-pdf/
│   └── notion/
└── core/
    ├── tavily-search/
    └── find-skills/
```

### 远程技能仓库

| 来源 | URL | 说明 |
|------|-----|------|
| ClawHub | https://hub.claw.ai | 官方技能仓库 |
| GitHub | https://github.com/skills | 社区技能 |
| 自定义 | 配置文件指定 | 企业内部仓库 |

## 技能安装流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    技能安装流程                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 发现需求                                                     │
│     └── 神经元检测到需要特定能力                                   │
│                                                                 │
│  2. 搜索技能                                                     │
│     └── find-skills 搜索匹配的技能                                │
│                                                                 │
│  3. 评估选择                                                     │
│     └── AI 评估技能适用性和质量                                   │
│                                                                 │
│  4. 下载安装                                                     │
│     └── 从远程仓库下载并安装到本地                                 │
│                                                                 │
│  5. 注册激活                                                     │
│     └── 注册到 SkillRegistry 并激活                               │
│                                                                 │
│  6. 开始使用                                                     │
│     └── 神经元可以立即使用新技能                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 触发词

- 安装技能、下载技能
- 查找技能、搜索技能
- 需要技能、缺少能力
- install skill、find skill

## 神经元集成

每个神经元都应具备此技能：

```java
// 神经元初始化时自动绑定
public abstract class AbstractNeuron implements Neuron {
    protected SkillRegistry skillRegistry;
    protected SkillFinder skillFinder;
    
    protected void autoDiscoverSkills(String taskDescription) {
        List<SkillRecommendation> recommendations = 
            skillFinder.recommend(taskDescription, getSkills());
        
        for (SkillRecommendation rec : recommendations) {
            if (rec.getPriority() == Priority.HIGH && !rec.isInstalled()) {
                skillFinder.install(rec.getSkillId());
            }
        }
    }
}
```

## 技能质量评估

| 指标 | 权重 | 说明 |
|------|------|------|
| 相关性 | 30% | 与任务匹配程度 |
| 可靠性 | 25% | 稳定性和错误率 |
| 效率 | 20% | 执行速度和资源占用 |
| 评分 | 15% | 用户评分和反馈 |
| 更新频率 | 10% | 维护活跃度 |

## 配置

```yaml
living-agent:
  skills:
    find-skills:
      enabled: true
      auto-install: true          # 自动安装推荐技能
      auto-install-priority: high # 仅高优先级自动安装
      sources:
        - type: local
          path: ./data/skills
        - type: remote
          url: https://hub.claw.ai/api/skills
        - type: github
          repo: living-agent-skills
      cache-ttl-minutes: 60
```

## 安全考虑

1. **来源验证**: 只从可信来源安装技能
2. **沙箱执行**: 新技能在沙箱中测试
3. **权限控制**: 技能安装需要审批
4. **回滚机制**: 支持技能卸载和回滚

## 相关技能

- [tavily-search](../tavily-search/SKILL.md) - 搜索技能信息
- [proactive-agent](../proactive-agent/SKILL.md) - 主动执行安装
- [skill-creator](../../tech/skill-creator/SKILL.md) - 创建新技能
