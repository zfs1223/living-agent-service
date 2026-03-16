package com.livingagent.skill.model;

import com.livingagent.core.skill.Skill;
import java.util.HashMap;
import java.util.Map;

public class SkillImpl implements Skill {
    private String name;
    private String description;
    private String category;
    private String targetBrain;
    private String content;
    private Map<String, Object> metadata;
    private String skillPath;

    public SkillImpl() {
        this.metadata = new HashMap<>();
    }

    public SkillImpl(String name, String description) {
        this.name = name;
        this.description = description;
        this.metadata = new HashMap<>();
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String getTargetBrain() {
        return targetBrain;
    }

    public void setTargetBrain(String targetBrain) {
        this.targetBrain = targetBrain;
    }

    @Override
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String getSkillPath() {
        return skillPath;
    }

    public void setSkillPath(String skillPath) {
        this.skillPath = skillPath;
    }

    @Override
    public String getMetadataSummary() {
        return String.format("name: %s\ndescription: %s", name, description);
    }

    @Override
    public String toString() {
        return "Skill{" +
                "name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", targetBrain='" + targetBrain + '\'' +
                '}';
    }
}
