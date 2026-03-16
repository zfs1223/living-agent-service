package com.livingagent.gateway.executor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.livingagent.core.tool.ToolResult;
import com.livingagent.gateway.event.ToolResultEvent;

@Service
public class ToolExecutorService {
    
    private static final Logger log = LoggerFactory.getLogger(ToolExecutorService.class);
    
    private final ConcurrentHashMap<String, ToolExecutor> executors;
    private final ApplicationEventPublisher eventPublisher;
    
    public ToolExecutorService(ApplicationEventPublisher eventPublisher) {
        this.executors = new ConcurrentHashMap<>();
        this.eventPublisher = eventPublisher;
    }
    
    public void register(ToolExecutor executor) {
        executors.put(executor.getName(), executor);
        log.info("Registered tool executor: {}", executor.getName());
    }
    
    public void unregister(String name) {
        executors.remove(name);
        log.info("Unregistered tool executor: {}", name);
    }
    
    public boolean hasExecutor(String name) {
        return executors.containsKey(name);
    }
    
    public ToolResult execute(String toolName, Map<String, Object> parameters, String sessionId, String userId) {
        log.info("Executing tool: {} for session: {}", toolName, sessionId);
        
        ToolExecutor executor = executors.get(toolName);
        if (executor == null) {
            log.warn("No executor found for tool: {}", toolName);
            ToolResult result = ToolResult.failure("不支持的工具: " + toolName);
            publishEvent(sessionId, toolName, result);
            return result;
        }
        
        if (executor.requiresApproval()) {
            log.info("Tool {} requires approval, pending...", toolName);
        }
        
        long startTime = System.currentTimeMillis();
        try {
            ToolResult result = executor.execute(parameters, userId);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Tool {} executed in {}ms, success: {}", toolName, duration, result.success());
            
            publishEvent(sessionId, toolName, result);
            
            return result;
        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            ToolResult result = ToolResult.failure("执行错误: " + e.getMessage());
            publishEvent(sessionId, toolName, result);
            return result;
        }
    }
    
    private void publishEvent(String sessionId, String toolName, ToolResult result) {
        ToolResultEvent event = result.success()
            ? ToolResultEvent.success(sessionId, toolName, result.getData())
            : ToolResultEvent.failure(sessionId, toolName, result.error());
        
        eventPublisher.publishEvent(event);
    }
    
    public java.util.Collection<ToolExecutor> getAllExecutors() {
        return executors.values();
    }
    
    public int getExecutorCount() {
        return executors.size();
    }
}
