package com.livingagent.skill.loader;

import com.livingagent.core.skill.Skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkillLoadResult {
    
    private final List<Skill> skills;
    private final List<Skill> quarantinedSkills;
    
    public SkillLoadResult() {
        this.skills = new ArrayList<>();
        this.quarantinedSkills = new ArrayList<>();
    }
    
    public SkillLoadResult(List<Skill> skills, List<Skill> quarantinedSkills) {
        this.skills = skills != null ? skills : new ArrayList<>();
        this.quarantinedSkills = quarantinedSkills != null ? quarantinedSkills : new ArrayList<>();
    }
    
    public List<Skill> getSkills() {
        return Collections.unmodifiableList(skills);
    }
    
    public List<Skill> getQuarantinedSkills() {
        return Collections.unmodifiableList(quarantinedSkills);
    }
    
    public int getTotalCount() {
        return skills.size() + quarantinedSkills.size();
    }
    
    public int getApprovedCount() {
        return skills.size();
    }
    
    public int getQuarantinedCount() {
        return quarantinedSkills.size();
    }
    
    public void addSkill(Skill skill) {
        skills.add(skill);
    }
    
    public void addQuarantinedSkill(Skill skill) {
        quarantinedSkills.add(skill);
    }
    
    public static SkillLoadResult empty() {
        return new SkillLoadResult();
    }
}
