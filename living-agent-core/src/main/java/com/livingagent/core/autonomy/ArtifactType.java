package com.livingagent.core.autonomy;

/**
 * 产物类型枚举。
 * 执行器按 artifactType 生成和前端按 artifactType 展示。
 */
public enum ArtifactType {
    INTERACTIVE_WEB_PAGE("可交互网页"),
    WEB_PROJECT("Web 项目"),
    DOCUMENT("文档"),
    DATA_REPORT("数据报告"),
    CODE_PATCH("代码补丁"),
    REVIEW_REPORT("审查报告"),
    ARCHITECTURE_SPEC("架构规格"),
    BUSINESS_PROPOSAL("商业方案"),
    SUPPORT_REPLY("客服回复"),
    LEGAL_MEMO("法律备忘录"),
    FINANCE_REPORT("财务报告"),
    HR_DOCUMENT("人事文档"),
    OPERATION_RUNBOOK("运营手册"),
    APPROVAL_REQUEST("审批请求"),
    HUMAN_HANDOFF_NOTE("人工交接说明"),
    CODE_WORKTREE("代码工作树"),
    CODE_DIFF("代码差异补丁"),
    CODE_REVIEW_REPORT("代码审查报告"),
    CODE_FINAL_SUMMARY("最终交付摘要"),
    TOOL_RESULT("工具调用结果");

    private final String description;

    ArtifactType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
