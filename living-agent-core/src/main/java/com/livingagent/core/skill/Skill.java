package com.livingagent.core.skill;

import java.util.Map;
import java.util.List;

public interface Skill {
    
    default String getId() {
        return getName();
    }
    
    String getName();
    
    String getDescription();
    
    String getCategory();
    
    default void setCategory(String category) {
    }
    
    String getTargetBrain();
    
    default void setTargetBrain(String targetBrain) {
    }
    
    String getContent();
    
    void setContent(String content);
    
    String getSkillPath();
    
    Map<String, Object> getMetadata();
    
    String getMetadataSummary();
    
    default List<String> getRequiredCapabilities() {
        return List.of();
    }
    
    default SkillResult execute(SkillContext context) {
        return SkillResult.failure("Skill execution not implemented");
    }
}
