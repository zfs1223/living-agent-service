package com.livingagent.core.proxy.anthropic;

import java.util.Map;

public record ClaudeProxyRequestContext(
    String requestId,
    String sessionId,
    String employeeId,
    String departmentId,
    String brainId,
    String taskType,
    String requestedModel,
    String apiKey,
    String authToken
) {

    public static ClaudeProxyRequestContext from(Map<String, String> headers, Map<String, String> params) {
        String requestId = headers.getOrDefault("x-request-id", "req-" + System.currentTimeMillis());
        String sessionId = headers.getOrDefault("x-session-id", params != null ? params.get("session_id") : null);
        String employeeId = headers.getOrDefault("x-employee-id", params != null ? params.get("employee_id") : null);
        String departmentId = headers.getOrDefault("x-department-id", params != null ? params.get("department_id") : null);
        String brainId = headers.getOrDefault("x-brain-id", params != null ? params.get("brain_id") : null);
        String taskType = headers.getOrDefault("x-task-type", "code_generation");
        String requestedModel = headers.getOrDefault("anthropic-model", null);
        String apiKey = extractToken(headers.get("x-api-key"));
        String authToken = extractToken(headers.get("authorization"));

        if (requestedModel == null && params != null) {
            requestedModel = params.get("model");
        }

        return new ClaudeProxyRequestContext(
            requestId, sessionId, employeeId, departmentId,
            brainId, taskType, requestedModel, apiKey, authToken
        );
    }

    private static String extractToken(String value) {
        if (value == null) return null;
        return value.startsWith("Bearer ") ? value.substring(7) : value;
    }
}
