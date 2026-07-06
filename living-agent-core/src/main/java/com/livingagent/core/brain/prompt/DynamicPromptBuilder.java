package com.livingagent.core.brain.prompt;

import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DynamicPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(DynamicPromptBuilder.class);

    private String basePrompt = "";
    private String roleDescription = "";
    private String capabilities = "";
    private String personalitySection = "";
    private String knowledgeSection = "";
    private String skillsSection = "";
    private String toolsSection = "";
    private String guidelines = "";
    private String workspaceSection = "";

    public DynamicPromptBuilder basePrompt(String basePrompt) {
        this.basePrompt = basePrompt != null ? basePrompt : "";
        return this;
    }

    public DynamicPromptBuilder role(String employeeName, String title, String department,
                                      List<String> capabilityTags) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 你的身份\n\n");
        sb.append("你是 **").append(title).append("**（").append(employeeName).append("），");
        sb.append("隶属于").append(department).append("。\n\n");

        if (capabilityTags != null && !capabilityTags.isEmpty()) {
            sb.append("### 核心能力\n");
            for (String tag : capabilityTags) {
                sb.append("- ").append(tag).append("\n");
            }
            sb.append("\n");
        }

        this.roleDescription = sb.toString();
        return this;
    }

    public DynamicPromptBuilder personality(BrainPersonality personality) {
        if (personality == null) {
            this.personalitySection = "";
            return this;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 人格参数\n\n");
        sb.append("| 维度 | 值 | 说明 |\n");
        sb.append("|------|-----|------|\n");
        sb.append(String.format("| 严谨度 | %.2f | %s |\n", personality.getRigor(),
            personality.getRigor() >= 0.8 ? "高度严谨，注重细节和准确性" : "适度严谨"));
        sb.append(String.format("| 创造力 | %.2f | %s |\n", personality.getCreativity(),
            personality.getCreativity() >= 0.7 ? "富有创造力，善于创新" : "注重规范和传统"));
        sb.append(String.format("| 风险容忍 | %.2f | %s |\n", personality.getRiskTolerance(),
            personality.getRiskTolerance() >= 0.5 ? "适度冒险，勇于尝试" : "保守稳健，规避风险"));
        sb.append(String.format("| 服从性 | %.2f | %s |\n", personality.getObedience(),
            personality.getObedience() >= 0.85 ? "严格遵循规则和指令" : "灵活执行，适度变通"));
        sb.append("\n");

        this.personalitySection = sb.toString();
        return this;
    }

    public DynamicPromptBuilder knowledge(KnowledgeBase knowledgeBase, String query, int limit) {
        if (knowledgeBase == null || query == null || query.isEmpty()) {
            this.knowledgeSection = "";
            return this;
        }

        try {
            // 搜索部门相关知识
            List<KnowledgeEntry> results = knowledgeBase.search(query);

            // 额外搜索架构知识（让大脑能"看到"自己的代码结构）
            List<KnowledgeEntry> archResults = knowledgeBase.search("arch");

            // 合并去重（按 entryId 去重）
            Set<String> seen = new HashSet<>();
            List<KnowledgeEntry> merged = new ArrayList<>();
            for (KnowledgeEntry entry : results) {
                if (entry.getEntryId() != null && seen.add(entry.getEntryId())) {
                    merged.add(entry);
                }
            }
            for (KnowledgeEntry entry : archResults) {
                if (entry.getEntryId() != null && seen.add(entry.getEntryId())) {
                    merged.add(entry);
                }
            }

            if (merged.isEmpty()) {
                this.knowledgeSection = "";
                return this;
            }

            int count = 0;
            StringBuilder sb = new StringBuilder();
            sb.append("## 相关知识\n\n");
            for (KnowledgeEntry entry : merged) {
                if (count++ >= limit) break;
                sb.append("### ").append(entry.getKey()).append("\n");
                Object content = entry.getContent();
                sb.append(content != null ? content.toString() : "").append("\n\n");
                if (entry.getConfidence() > 0) {
                    sb.append("*(置信度: ").append(String.format("%.2f", entry.getConfidence())).append(")*\n\n");
                }
            }

            this.knowledgeSection = sb.toString();
        } catch (Exception e) {
            log.debug("Failed to search knowledge base for prompt: {}", e.getMessage());
            this.knowledgeSection = "";
        }

        return this;
    }

    public DynamicPromptBuilder skills(SkillRegistry skillRegistry, String brainName) {
        if (skillRegistry == null) {
            this.skillsSection = "";
            return this;
        }

        try {
            List<Skill> skills = skillRegistry.getSkillsByBrain(brainName);
            if (skills == null || skills.isEmpty()) {
                this.skillsSection = "";
                return this;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 可用技能\n\n");
            for (Skill skill : skills) {
                sb.append("- **").append(skill.getName()).append("**");
                if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
                    sb.append(": ").append(skill.getDescription());
                }
                sb.append("\n");
            }
            sb.append("\n");

            this.skillsSection = sb.toString();
        } catch (Exception e) {
            log.debug("Failed to load skills for prompt: {}", e.getMessage());
            this.skillsSection = "";
        }

        return this;
    }

    public DynamicPromptBuilder tools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            this.toolsSection = "";
            return this;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 可用工具\n\n");
        for (String tool : toolNames) {
            sb.append("- ").append(tool).append("\n");
        }
        sb.append("\n");

        this.toolsSection = sb.toString();
        return this;
    }

    public DynamicPromptBuilder guidelines(String guidelines) {
        this.guidelines = guidelines != null ? guidelines : "";
        return this;
    }

    public DynamicPromptBuilder workspace(String workspaceRoot, boolean writeEnabled) {
        if (workspaceRoot == null || workspaceRoot.isEmpty()) {
            this.workspaceSection = "";
            return this;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 工作环境\n\n");
        sb.append("- **工作目录**: `").append(workspaceRoot).append("`\n");
        sb.append("- **写入权限**: ").append(writeEnabled ? "已启用" : "已禁用").append("\n");
        sb.append("- 使用 file_edit 工具编辑文件时，路径相对于工作目录\n");
        sb.append("- 使用 build 工具执行构建时，在工作目录下执行\n");
        sb.append("\n");
        this.workspaceSection = sb.toString();
        return this;
    }

    public String build() {
        StringJoiner joiner = new StringJoiner("\n");

        if (!basePrompt.isEmpty()) {
            joiner.add(basePrompt);
        }
        if (!roleDescription.isEmpty()) {
            joiner.add(roleDescription);
        }
        if (!personalitySection.isEmpty()) {
            joiner.add(personalitySection);
        }
        if (!skillsSection.isEmpty()) {
            joiner.add(skillsSection);
        }
        if (!toolsSection.isEmpty()) {
            joiner.add(toolsSection);
        }
        if (!workspaceSection.isEmpty()) {
            joiner.add(workspaceSection);
        }
        if (!knowledgeSection.isEmpty()) {
            joiner.add(knowledgeSection);
        }
        if (!guidelines.isEmpty()) {
            joiner.add(guidelines);
        }

        return joiner.toString();
    }
}
