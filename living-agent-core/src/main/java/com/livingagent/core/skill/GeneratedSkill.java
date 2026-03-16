package com.livingagent.core.skill;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于进化系统生成和安装到文件系统的技能实现。
 * 主要承载技能的 Markdown 内容和元数据，不负责实际执行逻辑。
 */
public class GeneratedSkill implements Skill {

    private final String name;
    private final String description;
    private String category;
    private String targetBrain;
    private String content;
    private String skillPath;
    private final Map<String, Object> metadata = new HashMap<>();

    public GeneratedSkill(String name, String description) {
        this.name = name;
        this.description = description;
        this.category = "general";
        this.targetBrain = "TechBrain";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String getTargetBrain() {
        return targetBrain;
    }

    @Override
    public void setTargetBrain(String targetBrain) {
        this.targetBrain = targetBrain;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String getSkillPath() {
        return skillPath;
    }

    public void setSkillPath(String skillPath) {
        this.skillPath = skillPath;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata.clear();
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
    }

    @Override
    public String getMetadataSummary() {
        if (metadata.isEmpty()) {
            return "";
        }
        return metadata.entrySet().stream()
            .limit(10)
            .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    @Override
    public List<String> getRequiredCapabilities() {
        return List.of();
    }
}

