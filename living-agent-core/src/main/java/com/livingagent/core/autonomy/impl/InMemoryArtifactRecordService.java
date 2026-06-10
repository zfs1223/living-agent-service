package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.ArtifactRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryArtifactRecordService implements ArtifactRecordService {

    private final Map<String, ArtifactRecord> byId = new ConcurrentHashMap<>();
    private final int maxRecords;

    public InMemoryArtifactRecordService() {
        this(5000);
    }

    public InMemoryArtifactRecordService(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    @Override
    public ArtifactRecord recordArtifact(ArtifactRecord artifact) {
        if (artifact == null) return null;
        byId.put(artifact.artifactId(), artifact);
        if (byId.size() > maxRecords) {
            Iterator<String> it = byId.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        return artifact;
    }

    @Override
    public Optional<ArtifactRecord> getArtifact(String artifactId) {
        return Optional.ofNullable(byId.get(artifactId));
    }

    @Override
    public List<ArtifactRecord> getByExecutionId(String executionId) {
        return byId.values().stream()
            .filter(a -> executionId.equals(a.executionId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactRecord> getByDepartment(String department) {
        return byId.values().stream()
            .filter(a -> department.equals(a.department()))
            .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactRecord> getByEmployeeCode(String employeeCode) {
        return byId.values().stream()
            .filter(a -> employeeCode.equals(a.ownerEmployeeCode()))
            .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactRecord> getByDepartmentAndType(String department, String type) {
        return byId.values().stream()
            .filter(a -> department.equals(a.department()) && type.equals(a.type()))
            .collect(Collectors.toList());
    }

    @Override
    public Page<ArtifactRecord> getByType(String type, Pageable pageable) {
        List<ArtifactRecord> filtered = byId.values().stream()
            .filter(a -> type.equals(a.type()))
            .sorted(Comparator.comparing(ArtifactRecord::createdAt).reversed())
            .collect(Collectors.toList());
        return paginate(filtered, pageable);
    }

    @Override
    public Page<ArtifactRecord> getAllOrderByCreatedAtDesc(Pageable pageable) {
        List<ArtifactRecord> sorted = byId.values().stream()
            .sorted(Comparator.comparing(ArtifactRecord::createdAt).reversed())
            .collect(Collectors.toList());
        return paginate(sorted, pageable);
    }

    @Override
    public long countByExecutionId(String executionId) {
        return byId.values().stream()
            .filter(a -> executionId.equals(a.executionId()))
            .count();
    }

    @Override
    public long countByDepartment(String department) {
        return byId.values().stream()
            .filter(a -> department.equals(a.department()))
            .count();
    }

    @Override
    public boolean exists(String artifactId) {
        return byId.containsKey(artifactId);
    }

    @Override
    public List<ArtifactRecord> scanAndIndexDirectory(String baseDir) {
        log.info("In-memory service does not support directory scanning");
        return List.of();
    }

    @Override
    public List<ArtifactRecord> getByTaskId(String taskId) {
        return byId.values().stream()
            .filter(a -> taskId.equals(a.taskId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactRecord> getByProjectId(String projectId) {
        return byId.values().stream()
            .filter(a -> projectId.equals(a.projectId()))
            .collect(Collectors.toList());
    }

    @Override
    public ArtifactRecord associateTaskAndProject(String artifactId, String taskId, String projectId) {
        ArtifactRecord existing = byId.get(artifactId);
        if (existing == null) return null;
        ArtifactRecord updated = existing;
        if (taskId != null) updated = updated.withTaskId(taskId);
        if (projectId != null) updated = updated.withProjectId(projectId);
        byId.put(artifactId, updated);
        return updated;
    }

    private Page<ArtifactRecord> paginate(List<ArtifactRecord> items, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InMemoryArtifactRecordService.class);
}
