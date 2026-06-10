package com.livingagent.core.skill;

import com.livingagent.core.security.AccessLevel;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SkillRegistry {
    
    void registerSkill(Skill skill);
    
    void registerSkills(List<Skill> skills);
    
    Optional<Skill> getSkill(String name);
    
    List<Skill> getSkillsByBrain(String brain);
    
    List<Skill> getSkillsByCategory(String category);
    
    List<Skill> getAllSkills();

    List<Skill> getSkillsByScope(String scope);

    List<Skill> getSkillsByOwnerId(String ownerId);

    /** 按 AccessLevel 过滤可见技能 */
    List<Skill> getVisibleSkills(String userId, AccessLevel accessLevel, String departmentId);
    
    List<String> getSkillMetadataForBrain(String brain);
    
    List<Skill> searchSkills(String query);
    
    Map<String, Integer> getSkillCountsByBrain();
    
    void reloadSkills();
}
