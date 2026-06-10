# ArtifactType 枚举定义

> 本文档定义了产物类型枚举。
> 版本：2026-05-20

---

## 枚举定义

```java
public enum ArtifactType {
    INTERACTIVE_WEB_PAGE,   // 交互式网页（单页）
    WEB_PROJECT,           // Web 项目（多文件）
    DOCUMENT,               // 文档
    DATA_REPORT,           // 数据报告
    CODE_PATCH,            // 代码补丁
    REVIEW_REPORT,         // 评审报告
    ARCHITECTURE_SPEC,     // 架构规格
    BUSINESS_PROPOSAL,     // 商业方案
    SUPPORT_REPLY,         // 客服回复
    LEGAL_MEMO,           // 法务备忘录
    FINANCE_REPORT,        // 财务报告
    HR_DOCUMENT,          // 人事文档
    OPERATION_RUNBOOK,    // 运营手册
    APPROVAL_REQUEST,     // 审批请求
    HUMAN_HANDOFF_NOTE    // 人工交接单
}
```

---

## 枚举说明

| 枚举值 | 描述 | 文件格式示例 |
| --- | --- | --- |
| `INTERACTIVE_WEB_PAGE` | 交互式网页（单页） | index.html |
| `WEB_PROJECT` | Web 项目（多文件） | index.html + css/ + js/ |
| `DOCUMENT` | 文档 | .md, .docx, .pdf |
| `DATA_REPORT` | 数据报告 | .xlsx, .csv, .json |
| `CODE_PATCH` | 代码补丁 | .patch, .diff |
| `REVIEW_REPORT` | 评审报告 | .md, .pdf |
| `ARCHITECTURE_SPEC` | 架构规格 | .md, .drawio |
| `BUSINESS_PROPOSAL` | 商业方案 | .pptx, .pdf, .md |
| `SUPPORT_REPLY` | 客服回复 | .md, .txt |
| `LEGAL_MEMO` | 法务备忘录 | .md, .pdf |
| `FINANCE_REPORT` | 财务报告 | .xlsx, .pdf |
| `HR_DOCUMENT` | 人事文档 | .docx, .pdf |
| `OPERATION_RUNBOOK` | 运营手册 | .md, .yaml |
| `APPROVAL_REQUEST` | 审批请求 | .md, .pdf |
| `HUMAN_HANDOFF_NOTE` | 人工交接单 | .md, .txt |

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ArtifactType.java` | 产物类型枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionCapability.java` | 执行能力枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionMode.java` | 执行模式枚举 |
