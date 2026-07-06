package com.livingagent.core.tool.impl;

import com.livingagent.core.tool.ToolExecutor;
import com.livingagent.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultToolExecutor implements ToolExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);
    
    @Override
    public String getName() {
        return "default";
    }
    
    @Override
    public String getDescription() {
        return "Default fallback tool executor - returns failure when no specific executor matches";
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters, String userId) {
        log.warn("DefaultToolExecutor invoked - no specific tool executor matched. Parameters: {}, userId: {}", parameters, userId);
        return ToolResult.failure("no_matching_executor: No specific tool executor available for this request. Parameters: " + parameters.keySet());
    }
}
