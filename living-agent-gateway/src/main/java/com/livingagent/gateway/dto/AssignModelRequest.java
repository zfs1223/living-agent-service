package com.livingagent.gateway.dto;

public record AssignModelRequest(
    java.util.UUID modelId,
    String assignedBy
) {
    public AssignModelRequest(java.util.UUID modelId) {
        this(modelId, null);
    }
}
