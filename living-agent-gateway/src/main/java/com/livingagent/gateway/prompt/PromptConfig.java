package com.livingagent.gateway.prompt;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "prompt")
public class PromptConfig {
    
    private Map<String, RoleConfig> roles = new HashMap<>();
    private RoleMapping roleMapping = new RoleMapping();
    private String defaultTemplate;
    
    public static class RoleMapping {
        private String defaultRole = "assistant";
        private Map<String, String> keywordMapping = new HashMap<>();
        
        public String getDefaultRole() {
            return defaultRole;
        }
        
        public void setDefaultRole(String defaultRole) {
            this.defaultRole = defaultRole;
        }
        
        public Map<String, String> getKeywordMapping() {
            return keywordMapping;
        }
        
        public void setKeywordMapping(Map<String, String> keywordMapping) {
            this.keywordMapping = keywordMapping;
        }
        
        public String detectRole(String text) {
            if (text == null || text.isEmpty()) {
                return defaultRole;
            }
            
            String lowerText = text.toLowerCase();
            for (Map.Entry<String, String> entry : keywordMapping.entrySet()) {
                if (lowerText.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            
            return defaultRole;
        }
    }
    
    public Map<String, RoleConfig> getRoles() {
        return roles;
    }
    
    public void setRoles(Map<String, RoleConfig> roles) {
        this.roles = roles;
    }
    
    public RoleConfig getRole(String roleName) {
        return roles.getOrDefault(roleName, roles.get(roleMapping.getDefaultRole()));
    }
    
    public RoleMapping getRoleMapping() {
        return roleMapping;
    }
    
    public void setRoleMapping(RoleMapping roleMapping) {
        this.roleMapping = roleMapping;
    }
    
    public String getDefaultTemplate() {
        return defaultTemplate;
    }
    
    public void setDefaultTemplate(String defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }
}
