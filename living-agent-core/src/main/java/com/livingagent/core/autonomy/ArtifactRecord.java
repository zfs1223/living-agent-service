package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ArtifactRecord(
    String artifactId,
    String executionId,
    String department,
    String ownerEmployeeCode,
    String ownerEmployeeNeuronId,
    String type,
    String path,
    String name,
    String summary,
    long sizeBytes,
    String sha256,
    String taskId,
    String projectId,
    List<String> tags,
    Instant createdAt,
    Map<String, Object> metadata
) {
    public static ArtifactRecord of(
            String executionId, String department,
            String ownerEmployeeCode, String ownerEmployeeNeuronId,
            String type, String path, String name, String summary) {
        return new ArtifactRecord(
            java.util.UUID.randomUUID().toString(),
            executionId, department,
            ownerEmployeeCode, ownerEmployeeNeuronId,
            type, path, name, summary,
            0L, null, null, null,
            List.of(), Instant.now(), Map.of()
        );
    }

    public static ArtifactRecord of(
            String executionId, String department,
            String ownerEmployeeCode, String ownerEmployeeNeuronId,
            String type, String path, String name, String summary,
            String taskId, String projectId) {
        return new ArtifactRecord(
            java.util.UUID.randomUUID().toString(),
            executionId, department,
            ownerEmployeeCode, ownerEmployeeNeuronId,
            type, path, name, summary,
            0L, null, taskId, projectId,
            List.of(), Instant.now(), Map.of()
        );
    }

    public ArtifactRecord withTaskId(String taskId) {
        return new ArtifactRecord(
            artifactId, executionId, department, ownerEmployeeCode, ownerEmployeeNeuronId,
            type, path, name, summary, sizeBytes, sha256, taskId, projectId,
            tags, createdAt, metadata
        );
    }

    public ArtifactRecord withProjectId(String projectId) {
        return new ArtifactRecord(
            artifactId, executionId, department, ownerEmployeeCode, ownerEmployeeNeuronId,
            type, path, name, summary, sizeBytes, sha256, taskId, projectId,
            tags, createdAt, metadata
        );
    }
}
