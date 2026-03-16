package com.livingagent.core.tool;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {

    void register(Tool tool);

    void unregister(String toolName);

    Optional<Tool> get(String toolName);

    List<Tool> getAll();

    List<Tool> getByDepartment(String department);

    List<ToolSchema> getSchemas();

    List<ToolSchema> getSchemas(List<String> toolNames);

    boolean exists(String toolName);

    int count();

    void clear();
}
