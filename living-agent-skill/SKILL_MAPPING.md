# 技能分类与部门大脑映射

## 一、技能来源

技能来自多个开源项目，采用 `SKILL.md` 格式定义：

| 来源 | 描述 | 技能数量 |
|------|------|---------|
| **OpenClaw** (`openclaw-main/skills`) | 本地已下载 | 50+ |
| **HuggingFace** (`skills-huggingface`) | AI/ML 专用 | 10+ |
| **Antigravity** (`antigravity-awesome-skills-main`) | 本地已下载 | 970+ |
| **Anthropic** (`skills-main`) | 官方文档技能 | 17+ |
| **CoPaw** (`agentscope-ai/CoPaw`) | 企业集成 | 10+ |
| **Microsoft** (`microsoft/skills`) | Azure/微软技术 | 132+ |
| **ClawHub** (https://clawhub.ai) | 在线技能库 | 已转换13个 |

---

## 二、核心技能 (每个神经元必备)

核心技能是每个神经元都必须具备的基础能力，在神经元初始化时自动绑定：

| 技能名称 | 描述 | 触发词 | 工具实现 | 状态 |
|---------|------|--------|----------|------|
| `tavily-search` | AI搜索引擎，实时获取网络信息 | 搜索, search, 查询 | [TavilySearchTool.java](../living-agent-core/src/main/java/com/livingagent/core/tool/impl/TavilySearchTool.java) | ✅ 已实现 |
| `find-skills` | 技能发现与安装，自我扩展能力 | 技能, skill, 安装 | [SkillFinderTool.java](../living-agent-core/src/main/java/com/livingagent/core/tool/impl/SkillFinderTool.java) | ✅ 已实现 |
| `proactive-agent` | 主动代理，定时/条件触发任务执行 | 定时, 主动, 任务 | [ProactiveAgentTool.java](../living-agent-core/src/main/java/com/livingagent/core/tool/impl/ProactiveAgentTool.java) | ✅ 已实现 |
| `weather` | 天气查询，获取实时天气和预报 | 天气, weather, 气温 | [WeatherTool.java](../living-agent-core/src/main/java/com/livingagent/core/tool/impl/WeatherTool.java) | ✅ 已实现 |
| `event-driven-notifier` | 事件驱动通知器，多渠道消息推送 | 通知, 推送, 告警 | - | 🔜 待开发 |
| `risk-predictor` | 风险预警预判器，项目/预算/人员风险预测 | 风险, 预警, 预测 | - | 🔜 待开发 |
| `pattern-predictor` | 用户行为模式预测器，习惯识别与需求预测 | 习惯, 模式, 预测 | - | 🔜 待开发 |

### 核心技能绑定机制

```java
// AbstractNeuron.java - 每个神经元自动绑定核心技能
private static final Set<String> CORE_SKILLS = Set.of(
    "tavily-search",
    "find-skills", 
    "proactive-agent",
    "weather",
    "event-driven-notifier",
    "risk-predictor"
);

// 神经元初始化时自动绑定
this.skills = ConcurrentHashMap.newKeySet();
this.skills.addAll(CORE_SKILLS);
```

---

## 三、编制与技能匹配矩阵

### 3.1 设计原则

```
编制能力 = 核心技能(共享) + 部门技能(专有)

✅ 每个员工都有核心技能
✅ 每个部门有专有技能
✅ 部门员工 = 核心技能 + 部门技能
✅ 编制约束：员工只能使用编制范围内的技能
```

### 3.2 技术部 - 10个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| T01 | 代码审查员 | code-review, security-audit | `code-review` ✅, `github` ✅ | ✅ 完全匹配 |
| T02 | 架构师 | architecture, system-design | `architecture` ✅ | ✅ 完全匹配 |
| T03 | DevOps工程师 | ci-cd, deployment | `cicd-pipeline` ✅, `docker-expert` ✅ | ✅ 完全匹配 |
| T04 | 运维工程师 | heartbeat, monitoring | `docker-expert` ✅ | ⚠️ 部分匹配 |
| T05 | AI模型管理员 | model-management | `hugging-face-*` ✅ (5个) | ✅ 完全匹配 |
| T06 | 状态管理员 | session-management | 核心技能覆盖 | ✅ 匹配 |
| T07 | 安全工程师 | sandbox, security | `code-review` 部分 | ⚠️ 部分匹配 |
| T08 | 配置管理员 | config-management | `github` ✅ | ✅ 匹配 |
| T09 | 前端工程师 | frontend, ui | `frontend-design` ✅, `canvas` ✅ | ✅ 完全匹配 |
| T10 | 后端工程师 | backend, api | `coding-agent` ✅ | ✅ 匹配 |

**技术部技能总数：23个**

### 3.3 财务部 - 4个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| F01 | 财务会计 | accounting, financial-reports | `finance-api-gateway` ✅ | ✅ 匹配 |
| F02 | 报销审核员 | expense-audit, invoice | `billing-automation` ✅, `finance-api-gateway` ✅ | ✅ 完全匹配 |
| F03 | 成本核算员 | cost-estimation | `billing-automation` ✅ | ✅ 匹配 |
| F04 | 预算管理员 | budget-management | `finance-api-gateway` ✅ | ✅ 匹配 |

**财务部技能总数：2个**

### 3.4 运营部 - 4个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| O01 | 数据分析师 | data-analysis, reporting | `data-aggregator` ✅, `inngest` ✅ | ✅ 完全匹配 |
| O02 | 运营专员 | operations, campaign | `cron` ✅, `inngest` ✅ | ✅ 匹配 |
| O03 | 任务调度员 | task-scheduling | `cron` ✅, `inngest` ✅ | ✅ 完全匹配 |
| O04 | 流程管理员 | process-management | `inngest` ✅ | ✅ 匹配 |

**运营部技能总数：9个**

### 3.5 销售部 - 3个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| S01 | 销售代表 | sales, customer-development | `crm-integration` ✅, `sales-automation` ✅ | ✅ 完全匹配 |
| S02 | 市场专员 | marketing, branding | `seo-audit` ✅, `brainstorming` ✅ | ✅ 完全匹配 |
| S03 | 渠道经理 | platform-integration | `crm-integration` ✅ | ✅ 匹配 |

**销售部技能总数：4个**

### 3.6 人力资源 - 2个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| H01 | 招聘专员 | recruitment, screening | `recruitment-automation` ✅ | ✅ 完全匹配 |
| H02 | 绩效管理员 | performance, training | `performance-management` ✅ | ✅ 完全匹配 |

**人力资源技能总数：3个**

### 3.7 客服部 - 2个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| C01 | 客服专员 | customer-service, inquiry | `customer-support` ✅, `customer-portal` ✅ | ✅ 完全匹配 |
| C02 | 工单处理员 | ticket-management | `ticket-system-integration` ✅ | ✅ 完全匹配 |

**客服部技能总数：3个**

### 3.8 行政部 - 3个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| A01 | 行政助理 | admin, coordination | `internal-comms` ✅, `doc-coauthoring` ✅ | ✅ 完全匹配 |
| A02 | 文档管理员 | document-management | `docx-official` ✅, `xlsx-official` ✅, `pptx-official` ✅ | ✅ 完全匹配 |
| A03 | 文案创作员 | copywriting, content | `copywriting` ✅, `summarize` ✅ | ✅ 完全匹配 |

**行政部技能总数：14个**

### 3.9 法务部 - 2个编制

| 编制代码 | 职位 | 能力需求 | 匹配技能 | 状态 |
|---------|------|---------|---------|------|
| L01 | 合同审查员 | contract-review, risk | `contract-management` ✅ | ✅ 完全匹配 |
| L02 | 合规专员 | compliance, policy | `compliance-check` ✅, `legal-advisor` ✅ | ✅ 完全匹配 |

**法务部技能总数：3个**

---

## 四、技能统计总览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    技能集成统计 (v1.7.0)                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  核心技能 (Core Skills) ────────────────────────────────────────── 8 个     │
│  ├── tavily-search (AI搜索) ✅ 工具已实现                                    │
│  ├── find-skills (技能发现) ✅ 工具已实现                                    │
│  ├── proactive-agent (主动代理) ✅ 工具已实现                                │
│  ├── weather (天气查询) ✅ 工具已实现                                        │
│  ├── mcp-client (MCP客户端)                                                 │
│  ├── event-driven-notifier (事件通知) 🔜 待开发                              │
│  ├── risk-predictor (风险预测) 🔜 待开发                                     │
│  └── pattern-predictor (模式预测) 🔜 待开发                                  │
│                                                                             │
│  部门技能 ───────────────────────────────────────────────────────── 56 个    │
│  ├── TechBrain (技术部): 23 个 ✅ 覆盖率 85%                                 │
│  ├── AdminBrain (行政部): 14 个 ✅ 覆盖率 90%                                │
│  ├── OpsBrain (运营部): 9 个 ✅ 覆盖率 85%                                   │
│  ├── SalesBrain (销售部): 4 个 ✅ 覆盖率 80%                                 │
│  ├── HrBrain (人力资源): 3 个 ✅ 覆盖率 85%                                  │
│  ├── CsBrain (客服部): 3 个 ✅ 覆盖率 85%                                    │
│  ├── LegalBrain (法务部): 3 个 ✅ 覆盖率 85%                                 │
│  └── FinanceBrain (财务部): 2 个 ✅ 覆盖率 60%                               │
│                                                                             │
│  总计: 64 个技能 (核心8 + 部门56)                                            │
│  工具实现: 15 个 ✅                                                          │
│  编制覆盖率: 85% ✅                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 五、ClawHub 技能转换记录

### 5.1 已转换技能 (13个)

| 新技能 | 来源技能 | 目标部门 | 适用编制 |
|--------|---------|---------|---------|
| `finance-api-gateway` | api-gateway | FinanceBrain | F01-F04 |
| `crm-integration` | api-gateway | SalesBrain | S01-S03 |
| `sales-automation` | automation-workflows | SalesBrain | S01-S03 |
| `cicd-pipeline` | automation-workflows | TechBrain | T03, T04 |
| `code-review` | self-improving-agent | TechBrain | T01, T07 |
| `recruitment-automation` | automation-workflows | HrBrain | H01 |
| `performance-management` | automation-workflows | HrBrain | H02 |
| `ticket-system-integration` | api-gateway | CsBrain | C01, C02 |
| `customer-portal` | automation-workflows | CsBrain | C01 |
| `contract-management` | automation-workflows | LegalBrain | L01 |
| `compliance-check` | automation-workflows | LegalBrain | L02 |
| `frontend-design` | frontend-design | TechBrain | T09 |
| `doc-coauthoring` | doc-coauthoring | AdminBrain | A01, A03 |

### 5.2 skills-main 可继续转换技能

| 技能 | 描述 | 适用部门 | 转换价值 |
|------|------|---------|---------|
| `algorithmic-art` | 算法艺术生成 | AdminBrain | ⭐⭐ 中 |
| `canvas-design` | 画布设计 | AdminBrain | ⭐⭐ 中 |
| `theme-factory` | 主题工厂 | AdminBrain | ⭐⭐ 中 |
| `web-artifacts-builder` | Web组件构建 | TechBrain | ⭐⭐ 中 |

---

## 六、目录结构

```
living-agent-skill/src/main/resources/skills/
├── SKILL_INDEX.json          # 技能索引 (v1.7.0)
├── SKILL_DEPENDENCIES.yml    # 技能依赖关系
├── core/                     # 核心技能 (8个) ✓
│   ├── tavily-search/SKILL.md
│   ├── find-skills/SKILL.md
│   ├── proactive-agent/SKILL.md
│   ├── weather/SKILL.md
│   ├── mcp-client/SKILL.md
│   ├── event-driven-notifier/SKILL.md
│   ├── risk-predictor/SKILL.md
│   └── pattern-predictor/SKILL.md
├── tech/                     # TechBrain (23个) ✓
│   ├── skill-creator/
│   ├── coding-agent/
│   ├── clawhub/
│   ├── rag-engineer/
│   ├── langgraph/
│   ├── architecture/
│   ├── tdd-workflow/
│   ├── docker-expert/
│   ├── github/
│   ├── canvas/
│   ├── mcp-builder/
│   ├── webapp-testing/
│   ├── cicd-pipeline/        # ClawHub转换
│   ├── code-review/          # ClawHub转换
│   ├── frontend-design/      # ClawHub转换
│   ├── crawl4ai/
│   └── hugging-face-*/       # 5个HF技能
├── admin/                    # AdminBrain (14个) ✓
│   ├── nano-pdf/
│   ├── notion/
│   ├── obsidian/
│   ├── copywriting/
│   ├── docx-official/
│   ├── xlsx-official/
│   ├── pptx-official/
│   ├── brand-guidelines/
│   ├── internal-comms/
│   ├── dingtalk_channel/
│   ├── slack/
│   ├── discord/
│   ├── summarize/
│   └── doc-coauthoring/      # ClawHub转换
├── sales/                    # SalesBrain (4个) ✓
│   ├── seo-audit/
│   ├── brainstorming/
│   ├── crm-integration/      # ClawHub转换
│   └── sales-automation/     # ClawHub转换
├── hr/                       # HrBrain (3个) ✓
│   ├── hr-pro/
│   ├── recruitment-automation/  # ClawHub转换
│   └── performance-management/  # ClawHub转换
├── finance/                  # FinanceBrain (2个) ✓
│   ├── billing-automation/
│   └── finance-api-gateway/  # ClawHub转换
├── cs/                       # CsBrain (3个) ✓
│   ├── customer-support/
│   ├── ticket-system-integration/  # ClawHub转换
│   └── customer-portal/      # ClawHub转换
├── legal/                    # LegalBrain (3个) ✓
│   ├── legal-advisor/
│   ├── contract-management/  # ClawHub转换
│   └── compliance-check/     # ClawHub转换
└── ops/                      # OpsBrain (9个) ✓
    ├── inngest/
    ├── cron/
    ├── smart-home/
    ├── weekly-report-generator/
    ├── project-risk-monitor/
    ├── onboarding-automator/
    ├── contract-monitor/
    ├── system-health-diagnoser/
    └── data-aggregator/
```

---

## 七、编制技能绑定示例

```java
// FixedEmployeeRegistry.java 中的技能绑定
private void registerTechEmployees() {
    // T01 代码审查员
    registerDefinition("T01", "CodeReviewer", "代码审查员", 
        "tech", "技术部",
        "neuron://tech/code-reviewer/001",
        List.of("代码质量审查", "PR审核", "代码规范检查"),
        List.of("code-review", "security-audit", "best-practices"),
        List.of("gitlab_tool", "github_tool"),
        "channel://tech/code-review",
        EmployeePersonality.of(0.85, 0.5, 0.4, 0.8),
        List.of("code-review", "github", "tavily-search", "find-skills")  // 绑定技能
    );
}

// 运行时技能校验
public void validateSkill(String employeeCode, String skillId) {
    FixedEmployeeDefinition def = getDefinition(employeeCode);
    if (!def.hasSkill(skillId)) {
        throw new IllegalStateException(
            String.format("编制 '%s' 未配置技能 '%s'", def.name(), skillId)
        );
    }
}
```

---

## 八、下一步行动

1. **完善工具实现** - 为新技能创建对应的 Java Tool 实现类
2. **补充财务技能** - 可继续从 ClawHub 转换更多财务相关技能
3. **集成 Microsoft Skills** - Azure AI 相关技能
4. **实现贾维斯模式工具** - event-driven-notifier, risk-predictor

---

*文档更新时间: 2026-03-13*
*技能版本: v1.7.0*
*编制覆盖率: 85%*
