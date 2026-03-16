package com.livingagent.core.tool.impl;

import com.livingagent.core.tool.*;
import com.livingagent.core.security.SecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistryImpl implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryImpl.class);

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Map<String, List<Tool>> departmentTools = new ConcurrentHashMap<>();

    @Override
    public void register(Tool tool) {
        String name = tool.getName();
        if (tools.containsKey(name)) {
            log.warn("Tool {} already registered, replacing", name);
        }
        tools.put(name, tool);
        
        String dept = tool.getDepartment();
        if (dept != null && !dept.isEmpty()) {
            departmentTools.computeIfAbsent(dept, k -> new ArrayList<>()).add(tool);
        }
        
        log.info("Registered tool: {} (department: {})", name, dept);
    }

    @Override
    public void unregister(String toolName) {
        Tool tool = tools.remove(toolName);
        if (tool != null) {
            String dept = tool.getDepartment();
            if (dept != null) {
                List<Tool> deptTools = departmentTools.get(dept);
                if (deptTools != null) {
                    deptTools.removeIf(t -> t.getName().equals(toolName));
                }
            }
            log.info("Unregistered tool: {}", toolName);
        }
    }

    @Override
    public Optional<Tool> get(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    @Override
    public List<Tool> getAll() {
        return List.copyOf(tools.values());
    }

    @Override
    public List<Tool> getByDepartment(String department) {
        List<Tool> deptTools = departmentTools.get(department);
        return deptTools != null ? List.copyOf(deptTools) : List.of();
    }

    @Override
    public List<ToolSchema> getSchemas() {
        return tools.values().stream()
            .map(Tool::getSchema)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<ToolSchema> getSchemas(List<String> toolNames) {
        return toolNames.stream()
            .map(tools::get)
            .filter(Objects::nonNull)
            .map(Tool::getSchema)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public boolean exists(String toolName) {
        return tools.containsKey(toolName);
    }

    @Override
    public int count() {
        return tools.size();
    }

    @Override
    public void clear() {
        tools.clear();
        departmentTools.clear();
    }
}
