package com.livingagent.core.skill;

import java.util.Map;
import java.util.HashMap;

public class SkillResult {
    
    private final boolean success;
    private final String message;
    private final Object data;
    private final Map<String, Object> metadata;
    
    private SkillResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.metadata = new HashMap<>();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getData() {
        return data;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public SkillResult addMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }
    
    public static SkillResult success() {
        return new SkillResult(true, "Success", null);
    }
    
    public static SkillResult success(Object data) {
        return new SkillResult(true, "Success", data);
    }
    
    public static SkillResult success(String message, Object data) {
        return new SkillResult(true, message, data);
    }
    
    public static SkillResult failure(String message) {
        return new SkillResult(false, message, null);
    }
    
    public static SkillResult failure(String message, Object data) {
        return new SkillResult(false, message, data);
    }
}
