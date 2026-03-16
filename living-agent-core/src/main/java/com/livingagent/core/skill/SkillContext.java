package com.livingagent.core.skill;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class SkillContext {
    
    private final String skillId;
    private final String sessionId;
    private final String neuronId;
    private final Map<String, Object> attributes;
    private final Map<String, Object> parameters;
    
    public SkillContext(String skillId, String sessionId) {
        this.skillId = skillId;
        this.sessionId = sessionId;
        this.neuronId = null;
        this.attributes = new HashMap<>();
        this.parameters = new HashMap<>();
    }
    
    public SkillContext(String skillId, String sessionId, String neuronId) {
        this.skillId = skillId;
        this.sessionId = sessionId;
        this.neuronId = neuronId;
        this.attributes = new HashMap<>();
        this.parameters = new HashMap<>();
    }
    
    public String getSkillId() {
        return skillId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getNeuronId() {
        return neuronId;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
    
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            value = attributes.get(key);
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    public String getParameter(String key, String defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            value = attributes.get(key);
        }
        return value != null ? value.toString() : defaultValue;
    }
    
    public int getParameter(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            value = attributes.get(key);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    public double getParameter(String key, double defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            value = attributes.get(key);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    public <T> List<T> getParameter(String key, List<T> defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            value = attributes.get(key);
        }
        if (value instanceof List) {
            return (List<T>) value;
        }
        return defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getParameter(String key, Map<K, V> defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            value = attributes.get(key);
        }
        if (value instanceof Map) {
            return (Map<K, V>) value;
        }
        return defaultValue;
    }
    
    public static SkillContext of(String skillId, String sessionId) {
        return new SkillContext(skillId, sessionId);
    }
    
    public static SkillContext of(String skillId, String sessionId, String neuronId) {
        return new SkillContext(skillId, sessionId, neuronId);
    }
}
