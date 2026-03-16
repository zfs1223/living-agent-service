package com.livingagent.core.evolution;

import com.livingagent.core.skill.Skill;
import java.util.List;
import java.util.Map;

public interface SkillGenerator {
    
    Skill generateSkill(String requirement, Map<String, Object> context);
    
    Skill refineSkill(Skill existingSkill, String feedback);
    
    List<Skill> suggestSkills(String scenario);
    
    boolean validateSkill(Skill skill);
    
    String generateSkillContent(String name, String description, List<String> triggers);
}
