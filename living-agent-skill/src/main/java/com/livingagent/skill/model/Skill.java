package com.livingagent.skill.model;

import java.util.HashMap;
import java.util.Map;

public class Skill implements com.livingagent.core.skill.Skill {
    private String name;
    private String description;
    private String category;
    private String targetBrain;
    private String content;
    private Map<String, Object> metadata;
    private String skillPath;

    public Skill() {
        this.metadata = new HashMap<>();
    }

    public Skill(String name, String description) {
        this.name = name;
        this.description = description;
        this.metadata = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTargetBrain() {
        return targetBrain;
    }

    public void setTargetBrain(String targetBrain) {
        this.targetBrain = targetBrain;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getSkillPath() {
        return skillPath;
    }

    public void setSkillPath(String skillPath) {
        this.skillPath = skillPath;
    }

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
