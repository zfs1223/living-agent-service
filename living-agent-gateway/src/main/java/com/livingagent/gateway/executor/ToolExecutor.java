package com.livingagent.gateway.executor;

import java.util.Map;

import com.livingagent.core.tool.ToolResult;

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
