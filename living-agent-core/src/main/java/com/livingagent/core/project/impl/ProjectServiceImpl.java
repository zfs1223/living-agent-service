package com.livingagent.core.project;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class ProjectServiceImpl implements ProjectService {
    
    private final Map<String, Project> projectStore = new ConcurrentHashMap<>();
    
    @Override
    public Project createProject(CreateProjectRequest request) {
        Project project = new Project(request.name(), request.ownerDepartment());
        project.setDescription(request.description());
        project.setManagerId(request.managerId());
        projectStore.put(project.getProjectId(), project);
        return project;
    }
    
    @Override
    public Optional<Project> getProject(String projectId) {
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
        
        return project;
    }
    
    @Override
    public void deleteProject(String projectId) {
        projectStore.remove(projectId);
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
}
