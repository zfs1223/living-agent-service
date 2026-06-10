package com.livingagent.core.autonomy;

/**
 * 系统可执行能力枚举。
 * 任务意图可以开放，执行能力必须收敛。
 * LLM 自主理解任务 → 系统归一到有限的 executionCapability → 执行器只消费 executionCapability。
 */
public enum ExecutionCapability {
    WEB_APP_BUILD("网页、Web App、网页游戏、H5、小工具页面"),
    DOCUMENT_GENERATION("文档、方案、报告、SOP、说明书"),
    DATA_ANALYSIS("表格、指标、趋势、数据诊断"),
    CODE_CHANGE("修改已有代码、修 Bug、加接口"),
    CODE_REVIEW("代码审查、质量检查、安全检查"),
    ARCHITECTURE_DESIGN("架构设计、技术方案、模块拆分"),
    RESEARCH_ANALYSIS("调研、竞品分析、资料整理"),
    BUSINESS_PLAN("商业方案、销售方案、运营方案"),
    CUSTOMER_SUPPORT("客服回复、工单处理、FAQ"),
    LEGAL_REVIEW("合同、合规、法律风险审查"),
    FINANCE_ANALYSIS("财务分析、预算、成本、报销判断"),
    HR_WORKFLOW("招聘、绩效、人事流程"),
    OPERATION_PLAN("运营活动、流程优化、排期计划"),
    FILE_SYSTEM_QUERY("文件系统查询、目录列表、文件读取、工作目录浏览"),
    PROJECT_MANAGEMENT("项目管理、任务创建/查询/更新、Issue 追踪、进度同步"),
    ISSUE_TRACKING("Issue 追踪、Bug 管理、任务状态流转、工单处理"),
    APPROVAL_REQUIRED("必须进入审批"),
    HUMAN_HANDOFF("必须人工接管");

    private final String description;

    ExecutionCapability(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
