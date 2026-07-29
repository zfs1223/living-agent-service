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

    /** 个人助手可安全复用：仅处理用户提供的输入，声明无内部能力。默认false，需显式标注 */
    default boolean isPersonalSafe() { return false; }
    default void setPersonalSafe(boolean personalSafe) {}

    default SkillResult execute(SkillContext context) {
        // 硬边界：个人助手的技能执行只能在客户端本地，服务器侧不承载
        if (context != null && context.isPersonalAssistant() && !isPersonalSafe()) {
            return SkillResult.failure(
                "技能 '" + getId() + "' 不允许在个人助手上下文中执行（服务器侧不承载个人助手技能执行）");
        }
        return SkillResult.failure("Skill execution not implemented");
    }
}
