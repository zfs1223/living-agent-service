package com.livingagent.core.tool;

import com.livingagent.core.security.SecurityPolicy;

import java.time.Duration;

public record ToolContext(
    String neuronId,
    String sessionId,
    SecurityPolicy securityPolicy,
    Duration timeout,
    boolean sandboxed,
    String employeeCode
) {
    public static ToolContext of(String neuronId, String sessionId) {
        return new ToolContext(neuronId, sessionId, null, Duration.ofSeconds(60), false, null);
    }

    public static ToolContext of(String neuronId, String sessionId, SecurityPolicy policy) {
        return new ToolContext(neuronId, sessionId, policy, Duration.ofSeconds(60), false, null);
    }

    public static ToolContext sandboxed(String neuronId, String sessionId, SecurityPolicy policy) {
        return new ToolContext(neuronId, sessionId, policy, Duration.ofSeconds(60), true, null);
    }

    public static ToolContext of(String neuronId, String sessionId, SecurityPolicy policy, String employeeCode) {
        return new ToolContext(neuronId, sessionId, policy, Duration.ofSeconds(60), false, employeeCode);
    }
}
