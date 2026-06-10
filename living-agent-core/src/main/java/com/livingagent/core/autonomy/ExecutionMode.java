package com.livingagent.core.autonomy;

/**
 * 执行模式枚举。
 * 决定沙箱、工具、人工审核等执行方式。
 */
public enum ExecutionMode {
    ARTIFACT_ONLY("只生成产物，不改仓库"),
    DOCKER_SANDBOX("在 Docker 沙箱执行/构建/测试"),
    LOCAL_RESTRICTED("受限本地执行"),
    HUMAN_REVIEW_REQUIRED("生成方案后必须人工审核"),
    APPROVAL_REQUIRED("必须审批后执行"),
    TOOL_EXECUTION("调用工具并直接返回结果，不生成 artifact"),
    NO_EXECUTION("只回答/只澄清/拒绝执行");

    private final String description;

    ExecutionMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
