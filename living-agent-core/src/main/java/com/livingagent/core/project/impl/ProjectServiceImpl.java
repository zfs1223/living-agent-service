package com.livingagent.core.project.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.livingagent.core.database.entity.ProjectEntity;
import com.livingagent.core.database.repository.ProjectRepository;
import com.livingagent.core.project.monitor.ProjectHealthMonitor;
import com.livingagent.core.project.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProjectServiceImpl implements ProjectService {
    
    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private final Map<String, Project> projectStore = new ConcurrentHashMap<>();
    private final ProjectRepository projectRepository;
    private final ProjectHealthMonitor projectHealthMonitor;

    public ProjectServiceImpl(ProjectRepository projectRepository, ProjectHealthMonitor projectHealthMonitor) {
        this.projectRepository = projectRepository;
        this.projectHealthMonitor = projectHealthMonitor;
    }
    
    @Override
    public Project createProject(CreateProjectRequest request) {
        Project project = new Project(request.name(), request.ownerDepartment());
        project.setDescription(request.description());
        project.setManagerId(request.managerId());
        projectStore.put(project.getProjectId(), project);
        persistProject(project);

        Instant deadline = project.getEndDate() != null ? project.getEndDate() : Instant.now().plus(java.time.Duration.ofDays(30));
        projectHealthMonitor.recordBaseline(project.getProjectId(), deadline, 0);

        log.info("Created project: {} name={}", project.getProjectId(), project.getName());
        return project;
    }
    
    @Override
    public Optional<Project> getProject(String projectId) {
        // B-1-7: 优先从 DB 查询，回填内存缓存
        try {
            Optional<ProjectEntity> entity = projectRepository.findByProjectId(projectId);
            if (entity.isPresent()) {
                Project project = toProject(entity.get());
                projectStore.put(projectId, project);
                return Optional.of(project);
            }
        } catch (Exception e) {
            log.warn("Failed to query project from DB, falling back to memory: {}", e.getMessage());
        }
        return Optional.ofNullable(projectStore.get(projectId));
    }
    
    @Override
    public List<Project> listProjects(ProjectQuery query) {
        return projectStore.values().stream()
            .filter(p -> query.status() == null || p.getStatus().name().equals(query.status()))
            .filter(p -> query.department() == null || query.department().equals(p.getOwnerDepartment()))
            .filter(p -> query.managerId() == null || query.managerId().equals(p.getManagerId()))
            .skip(query.offset() != null ? query.offset() : 0)
            .limit(query.limit() != null ? query.limit() : 100)
            .collect(Collectors.toList());
    }
    
    @Override
    public Project updateProject(String projectId, UpdateProjectRequest request) {
        Project project = projectStore.get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        
        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.status() != null) {
            project.setStatus(ProjectStatus.valueOf(request.status()));
        }
        if (request.managerId() != null) {
            project.setManagerId(request.managerId());
        }
        
        persistProject(project);
        return project;
    }
    
    @Override
    public void deleteProject(String projectId) {
        projectStore.remove(projectId);
        try {
            projectRepository.findByProjectId(projectId).ifPresent(projectRepository::delete);
        } catch (Exception e) {
            log.warn("Failed to delete project from repository: {}", e.getMessage());
        }
    }
    
    @Override
    public Project advancePhase(String projectId, String phaseCode) {
        Project project = projectStore.get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        
        ProjectPhase targetPhase = ProjectPhase.fromCode(phaseCode);
        int targetIndex = targetPhase.ordinal();
        int currentIndex = project.getCurrentPhase().ordinal();
        
        while (currentIndex < targetIndex) {
            project.advancePhase();
            currentIndex++;
        }
        
        persistProject(project);
        return project;
    }
    
    @Override
    public Project setPhaseProgress(String projectId, String phaseCode, double progress) {
        Project project = projectStore.get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        
        ProjectPhase phase = ProjectPhase.fromCode(phaseCode);
        project.setPhaseProgress(phase, progress);

        projectHealthMonitor.checkHealth(project, (int) project.getProgress(), 100);

        persistProject(project);
        return project;
    }
    
    @Override
    public ProjectStatistics getStatistics() {
        int total = projectStore.size();
        int planning = 0;
        int inProgress = 0;
        int completed = 0;
        int onHold = 0;
        int cancelled = 0;
        
        for (Project project : projectStore.values()) {
            switch (project.getStatus()) {
                case PLANNING -> planning++;
                case IN_PROGRESS -> inProgress++;
                case COMPLETED -> completed++;
                case ON_HOLD -> onHold++;
                case CANCELLED -> cancelled++;
            }
        }
        
        return new ProjectStatistics(total, planning, inProgress, completed, onHold, cancelled);
    }
    
    @Override
    public List<Project> getProjectsByDepartment(String department) {
        return projectStore.values().stream()
            .filter(p -> department.equals(p.getOwnerDepartment()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Project> getProjectsByManager(String managerId) {
        return projectStore.values().stream()
            .filter(p -> managerId.equals(p.getManagerId()))
            .collect(Collectors.toList());
    }

    private void persistProject(Project project) {
        try {
            Optional<ProjectEntity> existing = projectRepository.findByProjectId(project.getProjectId());
            ProjectEntity entity = existing.orElseGet(ProjectEntity::new);
            entity.setProjectId(project.getProjectId());
            entity.setName(project.getName());
            entity.setDescription(project.getDescription());
            entity.setStatus(project.getStatus().name());
            entity.setCurrentPhase(project.getCurrentPhase() != null ? project.getCurrentPhase().name() : null);
            entity.setOwnerDepartment(project.getOwnerDepartment());
            entity.setManagerId(project.getManagerId());
            entity.setStartDate(project.getStartDate());
            entity.setEndDate(project.getEndDate());
            entity.setProgress(project.getProgress());
            entity.setCreatedAt(project.getCreatedAt());
            entity.setUpdatedAt(Instant.now());
            if (project.getMetadata() != null) {
                Object tenantId = project.getMetadata().get("tenantId");
                if (tenantId != null) entity.setTenantId(String.valueOf(tenantId));
                Object creatorUserId = project.getMetadata().get("creatorUserId");
                if (creatorUserId != null) entity.setCreatorUserId(String.valueOf(creatorUserId));
                Object projectKey = project.getMetadata().get("projectKey");
                if (projectKey != null) entity.setProjectKey(String.valueOf(projectKey));
                Object dataNamespace = project.getMetadata().get("dataNamespace");
                if (dataNamespace != null) entity.setDataNamespace(String.valueOf(dataNamespace));
            }
            projectRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist project {}: {}", project.getProjectId(), e.getMessage());
        }
    }

    private Project toProject(ProjectEntity entity) {
        Project project = new Project(entity.getName(), entity.getOwnerDepartment());
        project.setProjectId(entity.getProjectId());
        project.setDescription(entity.getDescription());
        if (entity.getStatus() != null) {
            try { project.setStatus(ProjectStatus.valueOf(entity.getStatus())); } catch (IllegalArgumentException ignored) {}
        }
        if (entity.getCurrentPhase() != null) {
            try { project.setCurrentPhase(ProjectPhase.valueOf(entity.getCurrentPhase())); } catch (IllegalArgumentException ignored) {}
        }
        project.setManagerId(entity.getManagerId());
        project.setStartDate(entity.getStartDate());
        project.setEndDate(entity.getEndDate());
        Double progress = entity.getProgress();
        project.setProgress(progress != null ? progress : 0.0);
        project.setCreatedAt(entity.getCreatedAt());
        return project;
    }
}
