package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.ArtifactRecordService;
import com.livingagent.core.database.entity.ArtifactRecordEntity;
import com.livingagent.core.database.repository.ArtifactRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA 实现的 ArtifactRecordService
 * 提供 artifact 的数据库持久化能力
 */
public class JpaArtifactRecordService implements ArtifactRecordService {

    private static final Logger log = LoggerFactory.getLogger(JpaArtifactRecordService.class);

    private final ArtifactRecordRepository repository;
    private final ObjectMapper objectMapper;

    public JpaArtifactRecordService(ArtifactRecordRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ArtifactRecord recordArtifact(ArtifactRecord record) {
        ArtifactRecordEntity entity = toEntity(record);
        entity = repository.save(entity);
        log.info("Artifact recorded to database: artifactId={}, name={}, type={}", 
            entity.getArtifactId(), entity.getName(), entity.getType());
        return toRecord(entity);
    }

    @Override
    public Optional<ArtifactRecord> getArtifact(String artifactId) {
        return repository.findByArtifactId(artifactId)
            .map(this::toRecord);
    }

    @Override
    public List<ArtifactRecord> getByExecutionId(String executionId) {
        return repository.findByExecutionId(executionId)
            .stream()
            .map(this::toRecord)
            .toList();
    }

    @Override
    public List<ArtifactRecord> getByDepartment(String department) {
        return repository.findByDepartment(department)
            .stream()
            .map(this::toRecord)
            .toList();
    }

    @Override
    public List<ArtifactRecord> getByEmployeeCode(String employeeCode) {
        return repository.findByOwnerEmployeeCode(employeeCode)
            .stream()
            .map(this::toRecord)
            .toList();
    }

    @Override
    public List<ArtifactRecord> getByDepartmentAndType(String department, String type) {
        return repository.findByDepartmentAndType(department, type)
            .stream()
            .map(this::toRecord)
            .toList();
    }

    @Override
    public Page<ArtifactRecord> getByType(String type, Pageable pageable) {
        return repository.findByType(type, pageable)
            .map(this::toRecord);
    }

    @Override
    public Page<ArtifactRecord> getAllOrderByCreatedAtDesc(Pageable pageable) {
        return repository.findAllOrderByCreatedAtDesc(pageable)
            .map(this::toRecord);
    }

    @Override
    public long countByExecutionId(String executionId) {
        return repository.countByExecutionId(executionId);
    }

    @Override
    public long countByDepartment(String department) {
        return repository.countByDepartment(department);
    }

    @Override
    public boolean exists(String artifactId) {
        return repository.existsByArtifactId(artifactId);
    }

    @Override
    public List<ArtifactRecord> scanAndIndexDirectory(String baseDir) {
        log.info("Scanning directory for artifact indexing: {}", baseDir);
        java.nio.file.Path basePath = java.nio.file.Paths.get(baseDir);
        
        if (!java.nio.file.Files.exists(basePath)) {
            log.warn("Base directory does not exist: {}", baseDir);
            return List.of();
        }
        
        List<ArtifactRecord> indexed = new java.util.ArrayList<>();
        
        try (var stream = java.nio.file.Files.walk(basePath)) {
            var files = stream
                .filter(java.nio.file.Files::isRegularFile)
                .filter(p -> !p.toString().endsWith(".json"))
                .toList();
            
            for (var file : files) {
                String relativePath = basePath.relativize(file).toString();
                String artifactId = generateArtifactId(relativePath);
                
                if (!repository.existsByArtifactId(artifactId)) {
                    try {
                        long sizeBytes = java.nio.file.Files.size(file);
                        String name = file.getFileName().toString();
                        String type = detectFileType(name);
                        
                        ArtifactRecordEntity entity = new ArtifactRecordEntity(
                            artifactId, "indexed", "indexed", "system", "system",
                            type, relativePath, name, "Indexed from filesystem",
                            sizeBytes, null, null
                        );
                        repository.save(entity);
                        indexed.add(toRecord(entity));
                        log.debug("Indexed artifact from filesystem: {}", relativePath);
                    } catch (Exception e) {
                        log.warn("Failed to index file {}: {}", file, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan directory {}: {}", baseDir, e.getMessage());
        }
        
        log.info("Indexed {} new artifacts from directory {}", indexed.size(), baseDir);
        return indexed;
    }

    @Override
    public List<ArtifactRecord> getByTaskId(String taskId) {
        return repository.findByTaskId(taskId)
            .stream()
            .map(this::toRecord)
            .toList();
    }

    @Override
    public List<ArtifactRecord> getByProjectId(String projectId) {
        return repository.findByProjectId(projectId)
            .stream()
            .map(this::toRecord)
            .toList();
    }

    @Override
    public ArtifactRecord associateTaskAndProject(String artifactId, String taskId, String projectId) {
        return repository.findByArtifactId(artifactId)
            .map(entity -> {
                if (taskId != null) entity.setTaskId(taskId);
                if (projectId != null) entity.setProjectId(projectId);
                repository.save(entity);
                log.info("Artifact {} associated with taskId={}, projectId={}", artifactId, taskId, projectId);
                return toRecord(entity);
            })
            .orElse(null);
    }

    private String generateArtifactId(String relativePath) {
        return "artifact_" + UUID.nameUUIDFromBytes(relativePath.getBytes()).toString().replace("-", "");
    }

    private String detectFileType(String fileName) {
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) return "html";
        if (fileName.endsWith(".css")) return "css";
        if (fileName.endsWith(".js")) return "js";
        if (fileName.endsWith(".md")) return "markdown";
        if (fileName.endsWith(".json")) return "json";
        if (fileName.endsWith(".txt")) return "text";
        if (fileName.endsWith(".py")) return "python";
        if (fileName.endsWith(".java")) return "java";
        return "other";
    }

    private ArtifactRecordEntity toEntity(ArtifactRecord record) {
        String metadataJson = null;
        try {
            if (record.metadata() != null && !record.metadata().isEmpty()) {
                metadataJson = objectMapper.writeValueAsString(record.metadata());
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize artifact metadata: {}", e.getMessage());
        }
        
        ArtifactRecordEntity entity = new ArtifactRecordEntity(
            record.artifactId(),
            record.executionId(),
            record.department(),
            record.ownerEmployeeCode(),
            record.ownerEmployeeNeuronId(),
            record.type(),
            record.path(),
            record.name(),
            record.summary(),
            record.sizeBytes(),
            record.sha256(),
            metadataJson
        );
        entity.setTaskId(record.taskId());
        entity.setProjectId(record.projectId());
        return entity;
    }

    private ArtifactRecord toRecord(ArtifactRecordEntity entity) {
        java.util.Map<String, Object> metadata = parseMetadata(entity.getMetadataJson());
        
        return new ArtifactRecord(
            entity.getArtifactId(),
            entity.getExecutionId(),
            entity.getDepartment(),
            entity.getOwnerEmployeeCode(),
            entity.getOwnerEmployeeNeuronId(),
            entity.getType(),
            entity.getPath(),
            entity.getName(),
            entity.getSummary(),
            entity.getSizeBytes() != null ? entity.getSizeBytes() : 0L,
            entity.getSha256(),
            entity.getTaskId(),
            entity.getProjectId(),
            List.of(),
            entity.getCreatedAt(),
            metadata
        );
    }

    private java.util.Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return java.util.Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize artifact metadata: {}", e.getMessage());
            return java.util.Map.of();
        }
    }
}
