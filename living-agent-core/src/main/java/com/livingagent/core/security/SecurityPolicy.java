package com.livingagent.core.security;

import com.livingagent.core.tool.ToolCall;

import java.nio.file.Path;
import java.util.List;

public interface SecurityPolicy {

    AutonomyLevel getAutonomyLevel();

    void setAutonomyLevel(AutonomyLevel level);

    Path getWorkspaceDir();

    boolean isWorkspaceOnly();

    List<String> getAllowedCommands();

    List<String> getForbiddenPaths();

    CommandRiskLevel validateCommandExecution(String command, boolean approved);

    boolean isPathAllowed(String path);

    boolean isResolvedPathAllowed(Path resolved);

    void enforceToolOperation(ToolOperation operation, String operationName);

    boolean recordAction();

    boolean isToolAllowed(String toolName);
    
    boolean isRateLimited();

    int getMaxActionsPerHour();

    int getMaxCostPerDayCents();

    enum CommandRiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    enum ToolOperation {
        READ,
        ACT
    }
}
