package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SkillFinderTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SkillFinderTool.class);

    private static final String NAME = "find_skills";
    private static final String DESCRIPTION = "智能技能发现、匹配和推荐系统，支持自动安装技能";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "skill";

    private final SkillRegistry skillRegistry;
    private final SkillInstaller skillInstaller;
    private ToolStats stats = ToolStats.empty(NAME);

    public SkillFinderTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        this.skillInstaller = new SkillInstaller();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: search/install/recommend/list", true)
                .parameter("query", "string", "搜索查询或技能需求描述", false)
                .parameter("skill_id", "string", "技能ID (安装时使用)", false)
                .parameter("category", "string", "技能类别筛选", false)
                .parameter("brain", "string", "目标大脑筛选", false)
                .parameter("limit", "integer", "返回数量限制", false)
                .parameter("context", "string", "当前任务上下文 (推荐时使用)", false)
                .parameter("source", "string", "技能来源: local/remote/github", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("skill_search", "skill_install", "skill_recommend", "skill_list");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action 参数不能为空");
        }
        
        try {
            ToolResult result;
            switch (action.toLowerCase()) {
                case "search":
                    result = handleSearch(params);
                    break;
                case "install":
                    result = handleInstall(params);
                    break;
                case "recommend":
                    result = handleRecommend(params);
                    break;
                case "list":
                    result = handleList(params);
                    break;
                default:
                    stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
                    return ToolResult.failure("未知操作: " + action);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("SkillFinder action {} failed: {}", action, e.getMessage());
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("操作失败: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null) {
            throw new IllegalArgumentException("action 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private ToolResult handleSearch(ToolParams params) {
        String query = params.getString("query");
        String category = params.getString("category");
        String brain = params.getString("brain");
        Integer limitInt = params.getInteger("limit");
        int limit = limitInt != null ? limitInt : 10;
        
        List<Skill> skills;
        
        if (query != null && !query.isEmpty()) {
            skills = skillRegistry.searchSkills(query);
        } else if (category != null) {
            skills = skillRegistry.getSkillsByCategory(category);
        } else if (brain != null) {
            skills = skillRegistry.getSkillsByBrain(brain);
        } else {
            skills = skillRegistry.getAllSkills();
        }
        
        List<Map<String, Object>> results = skills.stream()
            .limit(limit)
            .map(this::skillToMap)
            .collect(Collectors.toList());
        
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("action", "search");
        output.put("query", query);
        output.put("total_count", skills.size());
        output.put("returned_count", results.size());
        output.put("skills", results);
        
        log.info("Skill search found {} results for query: {}", results.size(), query);
        return ToolResult.success(output);
    }

    private ToolResult handleInstall(ToolParams params) {
        String skillId = params.getString("skill_id");
        String source = params.getString("source");
        if (source == null) source = "local";
        String version = params.getString("version");
        
        if (skillId == null || skillId.isEmpty()) {
            return ToolResult.failure("skill_id 参数不能为空");
        }
        
        Optional<Skill> existing = skillRegistry.getSkill(skillId);
        if (existing.isPresent()) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("action", "install");
            output.put("status", "already_installed");
            output.put("skill", skillToMap(existing.get()));
            return ToolResult.success(output);
        }
        
        SkillInstaller.InstallResult result = skillInstaller.install(skillId, source, version);
        
        if (result.isSuccess()) {
            skillRegistry.reloadSkills();
            
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("action", "install");
            output.put("status", "success");
            output.put("skill_id", skillId);
            output.put("source", source);
            output.put("version", result.getVersion());
            output.put("message", "技能安装成功");
            
            log.info("Skill installed: {} from {}", skillId, source);
            return ToolResult.success(output);
        } else {
            return ToolResult.failure("安装失败: " + result.getError());
        }
    }

    private ToolResult handleRecommend(ToolParams params) {
        String context = params.getString("context");
        
        if (context == null || context.isEmpty()) {
            return ToolResult.failure("context 参数不能为空");
        }
        
        List<SkillRecommendation> recommendations = generateRecommendations(context);
        
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("action", "recommend");
        output.put("context", context);
        output.put("recommendations", recommendations.stream()
            .map(this::recommendationToMap)
            .collect(Collectors.toList()));
        
        log.info("Generated {} skill recommendations for context", recommendations.size());
        return ToolResult.success(output);
    }

    private ToolResult handleList(ToolParams params) {
        String brain = params.getString("brain");
        String category = params.getString("category");
        
        Map<String, List<Skill>> grouped;
        
        if (brain != null) {
            grouped = Map.of(brain, skillRegistry.getSkillsByBrain(brain));
        } else if (category != null) {
            grouped = Map.of(category, skillRegistry.getSkillsByCategory(category));
        } else {
            grouped = skillRegistry.getSkillCountsByBrain().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> skillRegistry.getSkillsByBrain(e.getKey())
                ));
        }
        
        int totalSkills = skillRegistry.getAllSkills().size();
        
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("action", "list");
        output.put("total_skills", totalSkills);
        output.put("grouped_skills", grouped.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().map(this::skillToMap).collect(Collectors.toList())
            )));
        
        return ToolResult.success(output);
    }

    private List<SkillRecommendation> generateRecommendations(String context) {
        List<SkillRecommendation> recommendations = new ArrayList<>();
        String lowerContext = context.toLowerCase();
        
        if (lowerContext.contains("pdf") || lowerContext.contains("文档")) {
            recommendations.add(new SkillRecommendation("nano-pdf", "PDF处理能力", "high", true));
        }
        if (lowerContext.contains("搜索") || lowerContext.contains("查找") || lowerContext.contains("search")) {
            recommendations.add(new SkillRecommendation("tavily-search", "网络搜索能力", "high", true));
        }
        if (lowerContext.contains("代码") || lowerContext.contains("编程") || lowerContext.contains("code")) {
            recommendations.add(new SkillRecommendation("coding-agent", "代码编写能力", "high", true));
        }
        if (lowerContext.contains("数据") || lowerContext.contains("分析") || lowerContext.contains("excel")) {
            recommendations.add(new SkillRecommendation("xlsx-official", "Excel处理能力", "medium", true));
        }
        if (lowerContext.contains("爬取") || lowerContext.contains("网页") || lowerContext.contains("crawl")) {
            recommendations.add(new SkillRecommendation("crawl4ai", "网页爬取能力", "medium", false));
        }
        if (lowerContext.contains("测试") || lowerContext.contains("test")) {
            recommendations.add(new SkillRecommendation("webapp-testing", "Web测试能力", "medium", false));
        }
        
        List<Skill> matching = skillRegistry.searchSkills(context);
        for (Skill skill : matching) {
            if (recommendations.stream().noneMatch(r -> r.skillId.equals(skill.getName()))) {
                recommendations.add(new SkillRecommendation(
                    skill.getName(),
                    skill.getDescription(),
                    "low",
                    skillRegistry.getSkill(skill.getName()).isPresent()
                ));
            }
        }
        
        return recommendations;
    }

    private Map<String, Object> skillToMap(Skill skill) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", skill.getName());
        map.put("name", skill.getName());
        map.put("description", skill.getDescription());
        map.put("category", skill.getCategory());
        map.put("brain", skill.getTargetBrain());
        map.put("installed", true);
        return map;
    }

    private Map<String, Object> recommendationToMap(SkillRecommendation rec) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("skill_id", rec.skillId);
        map.put("reason", rec.reason);
        map.put("priority", rec.priority);
        map.put("installed", rec.installed);
        return map;
    }

    private static class SkillRecommendation {
        String skillId;
        String reason;
        String priority;
        boolean installed;

        SkillRecommendation(String skillId, String reason, String priority, boolean installed) {
            this.skillId = skillId;
            this.reason = reason;
            this.priority = priority;
            this.installed = installed;
        }
    }
}
