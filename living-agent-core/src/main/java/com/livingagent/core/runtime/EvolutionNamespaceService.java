package com.livingagent.core.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 进化空间命名空间服务
 * 管理 .living/ 目录下的所有路径，与 data/ 业务空间严格分离
 * 大脑对此空间拥有自由权限（读写自如）
 */
public class EvolutionNamespaceService {

    private final String baseLivingDir;

    public EvolutionNamespaceService() {
        this(".living");
    }

    public EvolutionNamespaceService(String baseLivingDir) {
        this.baseLivingDir = baseLivingDir;
    }

    // ==================== 进化空间根目录 ====================

    /** 进化空间根目录 */
    public String getEvolutionRoot() {
        return baseLivingDir;
    }

    // ==================== 代码库镜像 ====================

    /** 代码库镜像根目录 */
    public String getCodebasePath() {
        return String.format("%s/codebase", baseLivingDir);
    }

    /** 代码库文档镜像（docs/） */
    public String getCodebaseDocsPath() {
        return String.format("%s/codebase/docs", baseLivingDir);
    }

    /** 代码库文档镜像（documents/） */
    public String getCodebaseDocumentsPath() {
        return String.format("%s/codebase/documents", baseLivingDir);
    }

    /** 代码库源码树镜像 */
    public String getCodebaseSourceTreePath() {
        return String.format("%s/codebase/source-tree", baseLivingDir);
    }

    // ==================== 进化工作区 ====================

    /** 进化工作区命名空间 */
    public String getEvolutionNamespace() {
        return String.format("%s/evolution", baseLivingDir);
    }

    /** 进化信号目录 */
    public String getEvolutionSignalsPath() {
        return String.format("%s/evolution/signals", baseLivingDir);
    }

    /** 单个进化信号路径 */
    public String getEvolutionSignalPath(String signalId) {
        return String.format("%s/evolution/signals/%s", baseLivingDir, safe(signalId));
    }

    /** 进化补丁目录 */
    public String getEvolutionPatchesPath() {
        return String.format("%s/evolution/patches", baseLivingDir);
    }

    /** 单个进化补丁路径 */
    public String getEvolutionPatchPath(String executionId) {
        return String.format("%s/evolution/patches/%s", baseLivingDir, safe(executionId));
    }

    /** 大脑专属进化知识路径 */
    public String getEvolutionKnowledgePath(String brainDomain) {
        return String.format("%s/evolution/knowledge/%s", baseLivingDir, safe(brainDomain));
    }

    /** 进化技能目录 */
    public String getEvolutionSkillsPath() {
        return String.format("%s/evolution/skills", baseLivingDir);
    }

    /** 单个进化技能路径 */
    public String getEvolutionSkillPath(String skillName) {
        return String.format("%s/evolution/skills/%s", baseLivingDir, safe(skillName));
    }

    /** 进化报告目录 */
    public String getEvolutionReportsPath() {
        return String.format("%s/evolution/reports", baseLivingDir);
    }

    /** 大脑专属回滚路径 */
    public String getEvolutionRollbackPath(String brainDomain) {
        return String.format("%s/evolution/rollback/%s", baseLivingDir, safe(brainDomain));
    }

    // ==================== 升级通知工作区 ====================

    /** 升级通知根目录 */
    public String getEscalationRoot() {
        return String.format("%s/escalation", baseLivingDir);
    }

    /** 待处理升级通知目录 */
    public String getEscalationPendingPath() {
        return String.format("%s/escalation/pending", baseLivingDir);
    }

    /** 单个待处理升级通知路径 */
    public String getEscalationPendingPath(String escalationId) {
        return String.format("%s/escalation/pending/%s", baseLivingDir, safe(escalationId));
    }

    /** 已解决升级通知目录 */
    public String getEscalationResolvedPath() {
        return String.format("%s/escalation/resolved", baseLivingDir);
    }

    /** 单个已解决升级通知路径 */
    public String getEscalationResolvedPath(String escalationId) {
        return String.format("%s/escalation/resolved/%s", baseLivingDir, safe(escalationId));
    }

    /** 升级通知模板目录 */
    public String getEscalationTemplatesPath() {
        return String.format("%s/escalation/templates", baseLivingDir);
    }

    // ==================== 指令空间（与 InstructionFileLoader 兼容） ====================

    /**
     * 全局指令路径（与 InstructionFileLoader 兼容）
     * 格式: .living/global/instructions.md
     */
    public String getGlobalInstructionsPath() {
        return String.format("%s/global/instructions.md", baseLivingDir);
    }

    /**
     * 员工级指令路径（与 InstructionFileLoader 兼容）
     * 格式: .living/{normalizedEmployeeId}/instructions.md
     * normalizedEmployeeId = employeeId 去掉 "employee://" 前缀，路径分隔符统一为 "/"
     * 对每段路径组件分别安全化，保留层级结构
     */
    public String getEmployeeInstructionsPath(String employeeId) {
        String normalizedId = employeeId;
        if (normalizedId.startsWith("employee://")) {
            normalizedId = normalizedId.substring("employee://".length());
        }
        normalizedId = normalizedId.replace("\\", "/");
        // 对每段路径组件分别安全化，保留 "/" 层级结构
        String[] segments = normalizedId.split("/");
        StringBuilder safePath = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                safePath.append("/");
            }
            safePath.append(safe(segments[i]));
        }
        return String.format("%s/%s/instructions.md", baseLivingDir, safePath);
    }

    // ==================== 工具方法 ====================

    /** 将命名空间字符串转换为 Path 对象 */
    public Path toPath(String namespace) {
        return Paths.get(namespace);
    }

    /** 安全化路径组件，防止路径注入 */
    private String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "_";
        }
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
