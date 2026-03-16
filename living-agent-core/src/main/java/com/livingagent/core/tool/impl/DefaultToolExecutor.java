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
        return "Default tool executor";
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters, String userId) {
        log.info("Executing default tool with parameters: {}", parameters);
        return ToolResult.success(Map.of("message", "Default execution completed"));
    }
}
