package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 默认执行能力解析器。
 * 策略：规则兜底优先 → 枚举校验 → 置信度检查 → 无法归一则 NEEDS_CLARIFICATION / HUMAN_HANDOFF。
 *
 * 设计原则：任务意图可以开放，执行能力必须收敛。
 * LLM 自主理解任务 → 系统归一到有限的 executionCapability → 执行器只消费 executionCapability。
 */
public class DefaultExecutionCapabilityResolver implements ExecutionCapabilityResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultExecutionCapabilityResolver.class);

    /** 置信度阈值：低于此值需要澄清 */
    private static final double CLARIFICATION_THRESHOLD = 0.6;
    /** 置信度阈值：低于此值但高于澄清阈值，可执行但记录风险 */
    private static final double PARTIAL_READY_THRESHOLD = 0.75;

    /** taskType/intent 到 ExecutionCapability 的关键词映射 */
    private static final Map<ExecutionCapability, List<String>> CAPABILITY_KEYWORDS = Map.ofEntries(
        Map.entry(ExecutionCapability.WEB_APP_BUILD, List.of(
            "web_app_build", "web_development", "web_prototype", "game_development",
            "web_game_development", "网页", "前端", "html", "css", "javascript",
            "游戏网页", "h5", "小游戏", "web page", "frontend", "web app",
            "游戏", "game", "射击", "飞机", "跳动", "动画", "交互页面",
            "前端游戏开发", "网页游戏开发", "单机游戏开发", "web游戏",
            "星空", "飞机射击", "小球跳动", "互动", "可运行页面"
        )),
        Map.entry(ExecutionCapability.DOCUMENT_GENERATION, List.of(
            "document_generation", "文档", "报告", "方案", "sop", "说明书",
            "撰写", "编写", "起草", "document", "report", "proposal"
        )),
        Map.entry(ExecutionCapability.DATA_ANALYSIS, List.of(
            "data_analysis", "数据分析", "表格", "指标", "趋势", "数据诊断",
            "analysis", "excel", "csv", "统计"
        )),
        Map.entry(ExecutionCapability.CODE_CHANGE, List.of(
            "code_change", "bug_fix", "修bug", "修bug", "加接口", "修改代码",
            "fix", "patch", "refactor"
        )),
        Map.entry(ExecutionCapability.CODE_REVIEW, List.of(
            "code_review", "代码审查", "质量检查", "安全检查", "review",
            "代码评审"
        )),
        Map.entry(ExecutionCapability.ARCHITECTURE_DESIGN, List.of(
            "architecture_design", "架构设计", "技术方案", "模块拆分",
            "architecture", "design", "系统设计"
        )),
        Map.entry(ExecutionCapability.RESEARCH_ANALYSIS, List.of(
            "research_analysis", "调研", "竞品分析", "资料整理", "research",
            "市场调研", "行业分析"
        )),
        Map.entry(ExecutionCapability.BUSINESS_PLAN, List.of(
            "business_plan", "商业方案", "销售方案", "运营方案", "business",
            "营销方案", "推广方案"
        )),
        Map.entry(ExecutionCapability.CUSTOMER_SUPPORT, List.of(
            "customer_support", "客服回复", "工单处理", "faq", "投诉",
            "客户", "support"
        )),
        Map.entry(ExecutionCapability.LEGAL_REVIEW, List.of(
            "legal_review", "合同", "合规", "法律风险", "legal", "法务",
            "审核", "审批"
        )),
        Map.entry(ExecutionCapability.FINANCE_ANALYSIS, List.of(
            "finance_analysis", "财务分析", "预算", "成本", "报销",
            "finance", "财务"
        )),
        Map.entry(ExecutionCapability.HR_WORKFLOW, List.of(
            "hr_workflow", "招聘", "绩效", "人事流程", "hr", "人力",
            "员工管理"
        )),
        Map.entry(ExecutionCapability.OPERATION_PLAN, List.of(
            "operation_plan", "运营活动", "流程优化", "排期计划",
            "operation", "运维计划"
        )),
        Map.entry(ExecutionCapability.APPROVAL_REQUIRED, List.of(
            "approval_required", "审批", "批准", "申请"
        )),
        Map.entry(ExecutionCapability.HUMAN_HANDOFF, List.of(
            "human_handoff", "人工", "转人工", "人工处理"
        )),
        Map.entry(ExecutionCapability.FILE_SYSTEM_QUERY, List.of(
            "file_listing", "file_system_query", "list_dir", "directory_listing",
            "列出文件", "目录内容", "文件列表", "查看文件", "工作目录",
            "浏览目录", "文件浏览", "文件查询", "目录浏览",
            "list files", "directory", "workspace files"
        ))
    );

    /** ExecutionCapability 到默认 ArtifactType 的映射 */
    private static final Map<ExecutionCapability, ArtifactType> CAPABILITY_TO_ARTIFACT = Map.ofEntries(
        Map.entry(ExecutionCapability.WEB_APP_BUILD, ArtifactType.INTERACTIVE_WEB_PAGE),
        Map.entry(ExecutionCapability.DOCUMENT_GENERATION, ArtifactType.DOCUMENT),
        Map.entry(ExecutionCapability.DATA_ANALYSIS, ArtifactType.DATA_REPORT),
        Map.entry(ExecutionCapability.CODE_CHANGE, ArtifactType.CODE_PATCH),
        Map.entry(ExecutionCapability.CODE_REVIEW, ArtifactType.REVIEW_REPORT),
        Map.entry(ExecutionCapability.ARCHITECTURE_DESIGN, ArtifactType.ARCHITECTURE_SPEC),
        Map.entry(ExecutionCapability.RESEARCH_ANALYSIS, ArtifactType.DOCUMENT),
        Map.entry(ExecutionCapability.BUSINESS_PLAN, ArtifactType.BUSINESS_PROPOSAL),
        Map.entry(ExecutionCapability.CUSTOMER_SUPPORT, ArtifactType.SUPPORT_REPLY),
        Map.entry(ExecutionCapability.LEGAL_REVIEW, ArtifactType.LEGAL_MEMO),
        Map.entry(ExecutionCapability.FINANCE_ANALYSIS, ArtifactType.FINANCE_REPORT),
        Map.entry(ExecutionCapability.HR_WORKFLOW, ArtifactType.HR_DOCUMENT),
        Map.entry(ExecutionCapability.OPERATION_PLAN, ArtifactType.OPERATION_RUNBOOK),
        Map.entry(ExecutionCapability.APPROVAL_REQUIRED, ArtifactType.APPROVAL_REQUEST),
        Map.entry(ExecutionCapability.HUMAN_HANDOFF, ArtifactType.HUMAN_HANDOFF_NOTE),
        Map.entry(ExecutionCapability.FILE_SYSTEM_QUERY, ArtifactType.TOOL_RESULT)
    );

    /** ExecutionCapability 到默认 ExecutionMode 的映射 */
    private static final Map<ExecutionCapability, ExecutionMode> CAPABILITY_TO_MODE = Map.ofEntries(
        Map.entry(ExecutionCapability.WEB_APP_BUILD, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.DOCUMENT_GENERATION, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.DATA_ANALYSIS, ExecutionMode.LOCAL_RESTRICTED),
        Map.entry(ExecutionCapability.CODE_CHANGE, ExecutionMode.DOCKER_SANDBOX),
        Map.entry(ExecutionCapability.CODE_REVIEW, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.ARCHITECTURE_DESIGN, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.RESEARCH_ANALYSIS, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.BUSINESS_PLAN, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.CUSTOMER_SUPPORT, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.LEGAL_REVIEW, ExecutionMode.HUMAN_REVIEW_REQUIRED),
        Map.entry(ExecutionCapability.FINANCE_ANALYSIS, ExecutionMode.LOCAL_RESTRICTED),
        Map.entry(ExecutionCapability.HR_WORKFLOW, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.OPERATION_PLAN, ExecutionMode.ARTIFACT_ONLY),
        Map.entry(ExecutionCapability.APPROVAL_REQUIRED, ExecutionMode.APPROVAL_REQUIRED),
        Map.entry(ExecutionCapability.HUMAN_HANDOFF, ExecutionMode.NO_EXECUTION),
        Map.entry(ExecutionCapability.FILE_SYSTEM_QUERY, ExecutionMode.TOOL_EXECUTION)
    );

    /** 中文任务类型到 ExecutionCapability 的直接映射 */
    private static final Map<String, ExecutionCapability> CHINESE_TASK_TYPE_MAP = Map.ofEntries(
        Map.entry("前端游戏开发", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("网页游戏开发", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("单机游戏开发", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("web游戏开发", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("网页开发", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("前端开发", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("游戏开发", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("文档生成", ExecutionCapability.DOCUMENT_GENERATION),
        Map.entry("数据分析", ExecutionCapability.DATA_ANALYSIS),
        Map.entry("代码修改", ExecutionCapability.CODE_CHANGE),
        Map.entry("代码审查", ExecutionCapability.CODE_REVIEW),
        Map.entry("架构设计", ExecutionCapability.ARCHITECTURE_DESIGN),
        Map.entry("调研分析", ExecutionCapability.RESEARCH_ANALYSIS),
        Map.entry("商业方案", ExecutionCapability.BUSINESS_PLAN),
        Map.entry("客服回复", ExecutionCapability.CUSTOMER_SUPPORT),
        Map.entry("法务审核", ExecutionCapability.LEGAL_REVIEW),
        Map.entry("财务分析", ExecutionCapability.FINANCE_ANALYSIS),
        Map.entry("人事流程", ExecutionCapability.HR_WORKFLOW),
        Map.entry("运营计划", ExecutionCapability.OPERATION_PLAN),
        Map.entry("文件查询", ExecutionCapability.FILE_SYSTEM_QUERY),
        Map.entry("列出文件", ExecutionCapability.FILE_SYSTEM_QUERY),
        Map.entry("目录浏览", ExecutionCapability.FILE_SYSTEM_QUERY)
    );

    /** 英文 taskType/intent 到 ExecutionCapability 的直接映射 */
    private static final Map<String, ExecutionCapability> ENGLISH_TASK_TYPE_MAP = Map.ofEntries(
        Map.entry("game_development", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("web_game_development", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("web_development", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("web_prototype", ExecutionCapability.WEB_APP_BUILD),
        Map.entry("software_development", ExecutionCapability.CODE_CHANGE),
        Map.entry("document_generation", ExecutionCapability.DOCUMENT_GENERATION),
        Map.entry("data_analysis", ExecutionCapability.DATA_ANALYSIS),
        Map.entry("code_change", ExecutionCapability.CODE_CHANGE),
        Map.entry("bug_fix", ExecutionCapability.CODE_CHANGE),
        Map.entry("code_review", ExecutionCapability.CODE_REVIEW),
        Map.entry("architecture_design", ExecutionCapability.ARCHITECTURE_DESIGN),
        Map.entry("research_analysis", ExecutionCapability.RESEARCH_ANALYSIS),
        Map.entry("business_plan", ExecutionCapability.BUSINESS_PLAN),
        Map.entry("customer_support", ExecutionCapability.CUSTOMER_SUPPORT),
        Map.entry("legal_review", ExecutionCapability.LEGAL_REVIEW),
        Map.entry("finance_analysis", ExecutionCapability.FINANCE_ANALYSIS),
        Map.entry("finance_workflow", ExecutionCapability.FINANCE_ANALYSIS),
        Map.entry("hr_workflow", ExecutionCapability.HR_WORKFLOW),
        Map.entry("operation_plan", ExecutionCapability.OPERATION_PLAN),
        Map.entry("file_listing", ExecutionCapability.FILE_SYSTEM_QUERY),
        Map.entry("file_system_query", ExecutionCapability.FILE_SYSTEM_QUERY),
        Map.entry("list_dir", ExecutionCapability.FILE_SYSTEM_QUERY),
        Map.entry("directory_listing", ExecutionCapability.FILE_SYSTEM_QUERY)
    );

    @Override
    public ExecutionCapabilityResolution resolve(ExecutionCapabilityRequest request) {
        if (request == null) {
            return ExecutionCapabilityResolution.needsClarification(
                List.of("请求为空，无法判断执行能力"), "null request");
        }

        log.info("Resolving execution capability: taskType={}, intent={}, department={}",
            request.taskType(), request.intent(), request.department());

        // 1. 尝试直接匹配 taskType/intent 到枚举
        ExecutionCapability directMatch = tryDirectMatch(request.taskType(), request.intent());
        if (directMatch != null) {
            ArtifactType artifactType = CAPABILITY_TO_ARTIFACT.get(directMatch);
            ExecutionMode mode = CAPABILITY_TO_MODE.get(directMatch);
            String reason = String.format("Direct match: taskType=%s, intent=%s -> %s",
                request.taskType(), request.intent(), directMatch);
            log.info("Capability resolved by direct match: {} -> {} (confidence=0.95)",
                request.taskType(), directMatch);
            return ExecutionCapabilityResolution.resolved(directMatch, artifactType, mode, 0.95, reason);
        }

        // 2. 基于关键词评分匹配
        ScoredCapability scored = scoreByKeywords(request);
        if (scored != null && scored.confidence >= CLARIFICATION_THRESHOLD) {
            ArtifactType artifactType = CAPABILITY_TO_ARTIFACT.get(scored.capability);
            ExecutionMode mode = CAPABILITY_TO_MODE.get(scored.capability);
            String reason = String.format("Keyword match: score=%.2f, matched=%d keywords -> %s",
                scored.confidence, scored.matchedKeywords, scored.capability);
            log.info("Capability resolved by keyword scoring: {} (confidence={})",
                scored.capability, scored.confidence);
            return ExecutionCapabilityResolution.resolved(scored.capability, artifactType, mode,
                scored.confidence, reason);
        }

        // 3. 基于交付物和技能辅助判断
        ExecutionCapability deliverableMatch = tryDeliverableMatch(request);
        if (deliverableMatch != null) {
            ArtifactType artifactType = CAPABILITY_TO_ARTIFACT.get(deliverableMatch);
            ExecutionMode mode = CAPABILITY_TO_MODE.get(deliverableMatch);
            String reason = String.format("Deliverable/skill match -> %s", deliverableMatch);
            log.info("Capability resolved by deliverable/skill match: {} (confidence=0.7)",
                deliverableMatch);
            return ExecutionCapabilityResolution.resolved(deliverableMatch, artifactType, mode, 0.7, reason);
        }

        // 4. 无法归一化，返回澄清
        log.warn("Cannot resolve execution capability for taskType={}, intent={}",
            request.taskType(), request.intent());
        return ExecutionCapabilityResolution.needsClarification(
            List.of(
                "请确认希望生成哪类产物（网页/文档/数据分析/代码修改等）",
                "请确认是否需要可运行的交互页面",
                "请确认任务的核心目标是什么"
            ),
            String.format("Cannot classify task: taskType=%s, intent=%s", request.taskType(), request.intent())
        );
    }

    /**
     * 尝试直接匹配 taskType 或 intent 到 ExecutionCapability。
     */
    private ExecutionCapability tryDirectMatch(String taskType, String intent) {
        if (taskType != null && !taskType.isBlank()) {
            String normalized = taskType.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
            // 先查英文映射
            ExecutionCapability cap = ENGLISH_TASK_TYPE_MAP.get(normalized);
            if (cap != null) return cap;
            // 再查中文映射
            cap = CHINESE_TASK_TYPE_MAP.get(taskType.trim());
            if (cap != null) return cap;
            // 尝试枚举名直接匹配
            try {
                return ExecutionCapability.valueOf(normalized.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }
        if (intent != null && !intent.isBlank()) {
            String normalized = intent.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
            ExecutionCapability cap = ENGLISH_TASK_TYPE_MAP.get(normalized);
            if (cap != null) return cap;
            cap = CHINESE_TASK_TYPE_MAP.get(intent.trim());
            if (cap != null) return cap;
        }
        return null;
    }

    /**
     * 基于关键词评分匹配。
     * 合并 taskType + intent + userMessage + deliverables + requiredSkills 的文本，
     * 对每个 ExecutionCapability 的关键词列表计算匹配分数。
     */
    private ScoredCapability scoreByKeywords(ExecutionCapabilityRequest request) {
        String combinedText = buildCombinedText(request).toLowerCase(Locale.ROOT);
        if (combinedText.isBlank()) return null;

        ScoredCapability best = null;
        for (Map.Entry<ExecutionCapability, List<String>> entry : CAPABILITY_KEYWORDS.entrySet()) {
            int matched = 0;
            for (String keyword : entry.getValue()) {
                if (combinedText.contains(keyword.toLowerCase(Locale.ROOT))) {
                    matched++;
                }
            }
            if (matched > 0) {
                double confidence = Math.min(0.5 + (matched * 0.1), 0.95);
                if (best == null || confidence > best.confidence) {
                    best = new ScoredCapability(entry.getKey(), confidence, matched);
                }
            }
        }
        return best;
    }

    /**
     * 基于交付物和技能辅助判断。
     */
    private ExecutionCapability tryDeliverableMatch(ExecutionCapabilityRequest request) {
        List<String> deliverables = request.deliverables();
        if (deliverables != null) {
            String deliverableText = String.join(" ", deliverables).toLowerCase(Locale.ROOT);
            if (deliverableText.contains("网页") || deliverableText.contains("html") ||
                deliverableText.contains("页面") || deliverableText.contains("游戏") ||
                deliverableText.contains("web") || deliverableText.contains("page")) {
                return ExecutionCapability.WEB_APP_BUILD;
            }
            if (deliverableText.contains("文档") || deliverableText.contains("报告") ||
                deliverableText.contains("doc") || deliverableText.contains("report")) {
                return ExecutionCapability.DOCUMENT_GENERATION;
            }
            if (deliverableText.contains("分析") || deliverableText.contains("analysis")) {
                return ExecutionCapability.DATA_ANALYSIS;
            }
        }

        List<String> skills = request.requiredSkills();
        if (skills != null) {
            String skillText = String.join(" ", skills).toLowerCase(Locale.ROOT);
            if (skillText.contains("frontend") || skillText.contains("html") ||
                skillText.contains("css") || skillText.contains("javascript") ||
                skillText.contains("canvas") || skillText.contains("game_loop")) {
                return ExecutionCapability.WEB_APP_BUILD;
            }
        }

        return null;
    }

    private String buildCombinedText(ExecutionCapabilityRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.taskType() != null) sb.append(request.taskType()).append(" ");
        if (request.intent() != null) sb.append(request.intent()).append(" ");
        if (request.userMessage() != null) sb.append(request.userMessage()).append(" ");
        if (request.deliverables() != null) sb.append(String.join(" ", request.deliverables())).append(" ");
        if (request.requiredSkills() != null) sb.append(String.join(" ", request.requiredSkills())).append(" ");
        return sb.toString();
    }

    private record ScoredCapability(ExecutionCapability capability, double confidence, int matchedKeywords) {}
}
