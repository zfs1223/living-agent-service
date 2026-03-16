package com.livingagent.gateway.prompt;

import java.util.HashMap;
import java.util.Map;

public class RoleConfig {
    
    private String name;
    private String systemPrompt;
    private String description;
    private Map<String, String> variables;
    
    public RoleConfig() {
        this.variables = new HashMap<>();
    }
    
    public static RoleConfig of(String name, String systemPrompt) {
        RoleConfig config = new RoleConfig();
        config.setName(name);
        config.setSystemPrompt(systemPrompt);
        return config;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, String> getVariables() {
        return variables;
    }
    
    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }
    
    public RoleConfig addVariable(String key, String value) {
        this.variables.put(key, value);
        return this;
    }
}
