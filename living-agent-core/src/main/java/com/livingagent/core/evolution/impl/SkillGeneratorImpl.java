package com.livingagent.core.evolution.impl;

import com.livingagent.core.evolution.SkillGenerator;
import com.livingagent.core.security.SkillVetter;
import com.livingagent.core.skill.GeneratedSkill;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SkillGeneratorImpl implements SkillGenerator {
    
    private static final Logger log = LoggerFactory.getLogger(SkillGeneratorImpl.class);
    
    private static final String SKILL_TEMPLATE = """
            ---
            name: %s
            description: %s
            ---
            
            # %s
            
            ## 触发条件
            %s
            
            ## 执行步骤
            %s
            
            ## 注意事项
            %s
            """;
    
    private final SkillRegistry skillRegistry;
    private final SkillVetter skillVetter;
    
    @Autowired
    public SkillGeneratorImpl(SkillRegistry skillRegistry, SkillVetter skillVetter) {
        this.skillRegistry = skillRegistry;
        this.skillVetter = skillVetter;
    }
    
    @Override
    public Skill generateSkill(String requirement, Map<String, Object> context) {
        log.info("Generating skill for requirement: {}", requirement);
        
        String skillName = generateSkillName(requirement);
        String skillDescription = generateDescription(requirement);
        List<String> triggers = generateTriggers(requirement);
        String skillContent = generateSkillContent(skillName, skillDescription, triggers);

        GeneratedSkill skill = new GeneratedSkill(skillName, skillDescription);
        skill.setContent(skillContent);
        skill.setCategory((String) context.getOrDefault("category", "general"));
        skill.setTargetBrain((String) context.getOrDefault("targetBrain", "TechBrain"));
        
        if (!validateSkill(skill)) {
            log.warn("Skill validation failed for: {}", skillName);
            return null;
        }
        
        SkillVetter.VettingResult vettingResult = skillVetter.vetSkill(skill);
        skill.getMetadata().put("vettingId", vettingResult.vettingId());
        skill.getMetadata().put("vettingStatus", vettingResult.status().name());
        skill.getMetadata().put("riskLevel", vettingResult.riskLevel().name());
        
        if (vettingResult.status() == SkillVetter.VettingStatus.REJECTED) {
            log.warn("Generated skill {} rejected by security vetter: {}", skillName, vettingResult.summary());
            return null;
        }
        
        if (vettingResult.status() == SkillVetter.VettingStatus.QUARANTINED) {
            log.warn("Generated skill {} quarantined: {}", skillName, vettingResult.summary());
            skill.getMetadata().put("quarantined", true);
        }
        
        if (vettingResult.status() == SkillVetter.VettingStatus.APPROVED_WITH_WARNINGS) {
            log.info("Generated skill {} approved with warnings: {}", skillName, vettingResult.summary());
            skill.getMetadata().put("warnings", vettingResult.findings());
        }
        
        log.info("Skill generated successfully: {} (status: {}, risk: {})", 
            skillName, vettingResult.status(), vettingResult.riskLevel());
        return skill;
    }
    
    @Override
    public Skill refineSkill(Skill existingSkill, String feedback) {
        log.info("Refining skill: {} with feedback: {}", existingSkill.getName(), feedback);
        
        String refinedContent = existingSkill.getContent() + "\n\n## 改进记录\n" + feedback;
        existingSkill.setContent(refinedContent);
        
        SkillVetter.VettingResult vettingResult = skillVetter.vetSkill(existingSkill);
        existingSkill.getMetadata().put("refineVettingId", vettingResult.vettingId());
        existingSkill.getMetadata().put("refineVettingStatus", vettingResult.status().name());
        
        if (vettingResult.status() == SkillVetter.VettingStatus.REJECTED) {
            log.warn("Refined skill {} rejected by security vetter", existingSkill.getName());
        }
        
        return existingSkill;
    }
    
    @Override
    public List<Skill> suggestSkills(String scenario) {
        log.info("Suggesting skills for scenario: {}", scenario);
        
        List<Skill> suggestions = new ArrayList<>();
        List<Skill> existingSkills = skillRegistry.getAllSkills();
        
        Set<String> existingNames = existingSkills.stream()
                .map(Skill::getName)
                .collect(Collectors.toSet());
        
        String[] commonSkills = {
            "email-handler", "calendar-manager", "document-processor",
            "data-analyzer", "report-generator", "notification-sender",
            "api-integrator", "file-manager", "search-engine", "translator"
        };
        
        for (String skillName : commonSkills) {
            if (!existingNames.contains(skillName) && isRelevant(skillName, scenario)) {
                GeneratedSkill suggestion = new GeneratedSkill(skillName, "Auto-suggested for: " + scenario);
                suggestions.add(suggestion);
            }
        }
        
        return suggestions;
    }
    
    @Override
    public boolean validateSkill(Skill skill) {
        if (skill == null || skill.getName() == null || skill.getName().isEmpty()) {
            return false;
        }
        if (skill.getDescription() == null || skill.getDescription().isEmpty()) {
            return false;
        }
        if (skill.getContent() == null || skill.getContent().isEmpty()) {
            return false;
        }
        return skill.getContent().length() >= 100;
    }
    
    @Override
    public String generateSkillContent(String name, String description, List<String> triggers) {
        String triggersSection = triggers.stream()
                .map(t -> "- " + t)
                .collect(Collectors.joining("\n"));
        
        String steps = """
                1. 分析用户需求
                2. 收集必要信息
                3. 执行核心操作
                4. 验证结果
                5. 返回响应
                """;
        
        String notes = """
                - 确保操作安全
                - 处理异常情况
                - 记录操作日志
                """;
        
        return String.format(SKILL_TEMPLATE, name, description, name, 
                triggersSection, steps, notes);
    }
    
    private String generateSkillName(String requirement) {
        String[] words = requirement.toLowerCase().split("\\s+");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.length() > 2) {
                name.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1));
            }
        }
        if (name.length() == 0) {
            name.append("CustomSkill").append(System.currentTimeMillis() % 1000);
        }
        return name.toString();
    }
    
    private String generateDescription(String requirement) {
        return "自动生成的技能: " + requirement;
    }
    
    private List<String> generateTriggers(String requirement) {
        List<String> triggers = new ArrayList<>();
        String[] words = requirement.toLowerCase().split("\\s+");
        for (String word : words) {
            if (word.length() > 2) {
                triggers.add(word);
            }
        }
        if (triggers.isEmpty()) {
            triggers.add("auto");
        }
        return triggers;
    }
    
    private boolean isRelevant(String skillName, String scenario) {
        String lowerScenario = scenario.toLowerCase();
        return lowerScenario.contains(skillName.split("-")[0]);
    }
}
