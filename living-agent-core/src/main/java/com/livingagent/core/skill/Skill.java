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

    /** 技能作用域：global=项目内置, evolved=进化生成, personal=个人添加 */
    default String getScope() { return "global"; }
    default void setScope(String scope) {}

    /** 技能所有者（仅 personal/evolved 有值） */
    default String getOwnerId() { return null; }
    default void setOwnerId(String ownerId) {}

    /** 技能所属部门（仅 evolved 有值） */
    default String getDepartmentId() { return null; }
    default void setDepartmentId(String departmentId) {}

    default SkillResult execute(SkillContext context) {
        return SkillResult.failure("Skill execution not implemented");
    }
}
