package com.livingagent.core.tool;

import java.util.Map;

public interface ToolExecutor {
    
    String getName();
    
    String getDescription();
    
    ToolResult execute(Map<String, Object> parameters, String userId);
    
    default boolean requiresApproval() {
        return false;
    }
    
    default String[] getRequiredParameters() {
        return new String[0];
    }
}
