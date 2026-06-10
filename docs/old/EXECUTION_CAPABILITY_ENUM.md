# ExecutionCapability 枚举定义

> 本文档定义了执行能力枚举，用于将 LLM 开放意图归一到有限执行能力。
> 版本：2026-05-20

---

## 枚举定义

```java
public enum ExecutionCapability {
    WEB_APP_BUILD("网页/Web App/网页游戏/H5"),
    DOCUMENT_GENERATION("文档/方案/报告"),
    DATA_ANALYSIS("数据分析"),
    CODE_CHANGE("修改代码/Bug修复"),
    CODE_REVIEW("代码审查"),
    ARCHITECTURE_DESIGN("架构设计"),
    RESEARCH_ANALYSIS("调研分析"),
    BUSINESS_PLAN("商业方案"),
    CUSTOMER_SUPPORT("客服回复"),
    LEGAL_REVIEW("法务审核"),
    FINANCE_ANALYSIS("财务分析"),
    HR_WORKFLOW("人事流程"),
    OPERATION_PLAN("运营计划"),
    APPROVAL_REQUIRED("需审批"),
    HUMAN_HANDOFF("需人工");

    private final String description;

    ExecutionCapability(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
```

---

## 枚举说明

| 枚举值 | 描述 | 适用场景 |
| --- | --- | --- |
| `WEB_APP_BUILD` | 网页/Web App/网页游戏/H5 | 前端开发、原型制作 |
| `DOCUMENT_GENERATION` | 文档/方案/报告 | 文档撰写、制度编写 |
| `DATA_ANALYSIS` | 数据分析 | 数据处理、指标分析 |
| `CODE_CHANGE` | 修改代码/Bug修复 | 代码修改、功能变更 |
| `CODE_REVIEW` | 代码审查 | 代码评审、安全检查 |
| `ARCHITECTURE_DESIGN` | 架构设计 | 技术方案、模块设计 |
| `RESEARCH_ANALYSIS` | 调研分析 | 市场调研、竞品分析 |
| `BUSINESS_PLAN` | 商业方案 | 销售方案、商业计划 |
| `CUSTOMER_SUPPORT` | 客服回复 | 工单处理、FAQ |
| `LEGAL_REVIEW` | 法务审核 | 合同审核、合规检查 |
| `FINANCE_ANALYSIS` | 财务分析 | 预算分析、成本核算 |
| `HR_WORKFLOW` | 人事流程 | 招聘、绩效、考勤 |
| `OPERATION_PLAN` | 运营计划 | 活动策划、流程优化 |
| `APPROVAL_REQUIRED` | 需审批 | 需要人工审批 |
| `HUMAN_HANDOFF` | 需人工 | 需要人工介入 |

---

## 与其他枚举的关系

- `artifactType`: 决定产物生成类型
- `executionMode`: 决定执行环境和方式

示例映射：

| 用户任务 | ExecutionCapability | ArtifactType | ExecutionMode |
| --- | --- | --- | --- |
| 做一个星空飞机射击网页游戏 | WEB_APP_BUILD | INTERACTIVE_WEB_PAGE | ARTIFACT_ONLY |
| 写一份销售方案 | DOCUMENT_GENERATION | BUSINESS_PROPOSAL | ARTIFACT_ONLY |
| 分析 Excel 财务数据 | DATA_ANALYSIS | DATA_REPORT | LOCAL_RESTRICTED |
| 评审合同风险 | LEGAL_REVIEW | LEGAL_MEMO | HUMAN_REVIEW_REQUIRED |
| 设计系统架构 | ARCHITECTURE_DESIGN | ARCHITECTURE_SPEC | ARTIFACT_ONLY |
| 修改后端接口 Bug | CODE_CHANGE | CODE_PATCH | DOCKER_SANDBOX |

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionCapability.java` | 执行能力枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ArtifactType.java` | 产物类型枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionMode.java` | 执行模式枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionCapabilityResolver.java` | 归一化解析器接口 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultExecutionCapabilityResolver.java` | 归一化解析器实现 |
