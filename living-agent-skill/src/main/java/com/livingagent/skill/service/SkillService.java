package com.livingagent.skill.service;

import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SkillService {
    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final SkillRegistry skillRegistry;

    @Autowired
    public SkillService(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public List<Skill> getAllSkills() {
        return skillRegistry.getAllSkills();
    }

    public Optional<Skill> getSkill(String name) {
        return skillRegistry.getSkill(name);
    }

    public List<Skill> getSkillsForBrain(String brainName) {
        return skillRegistry.getSkillsByBrain(brainName);
    }

    public List<Skill> getSkillsForCategory(String category) {
        return skillRegistry.getSkillsByCategory(category);
    }

    public List<Skill> searchSkills(String query) {
        return skillRegistry.searchSkills(query);
    }

    public String getSkillPrompt(String skillName) {
        return skillRegistry.getSkill(skillName)
                .map(skill -> {
                    StringBuilder prompt = new StringBuilder();
                    prompt.append("# Skill: ").append(skill.getName()).append("\n\n");
                    prompt.append(skill.getContent());
                    return prompt.toString();
                })
                .orElse(null);
    }

    public String getBrainSkillsMetadata(String brainName) {
        List<String> metadata = skillRegistry.getSkillMetadataForBrain(brainName);
        if (metadata.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("# Available Skills for ").append(brainName).append("\n\n");
        for (String meta : metadata) {
            sb.append("---\n").append(meta).append("\n");
        }
        return sb.toString();
    }

    public String getSkillContentForContext(String skillName) {
        return skillRegistry.getSkill(skillName)
                .map(Skill::getContent)
                .orElse("");
    }

    public void reloadSkills() {
        log.info("Reloading all skills...");
        skillRegistry.reloadSkills();
    }
}
