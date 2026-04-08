package com.livingagent.core.project;

import java.util.List;
import java.util.Optional;

public interface ProjectService {
    
    Project createProject(CreateProjectRequest request);
    
    Optional<Project> getProject(String projectId);
    
    List<Project> listProjects(ProjectQuery query);
    
    Project updateProject(String projectId, UpdateProjectRequest request);
    
    void deleteProject(String projectId);
    
    Project advancePhase(String projectId, String phaseCode);
    
    Project setPhaseProgress(String projectId, String phaseCode, double progress);
    
    ProjectStatistics getStatistics();
    
    List<Project> getProjectsByDepartment(String department);
    
    List<Project> getProjectsByManager(String managerId);
    
    record CreateProjectRequest(
        String name,
        String description,
        String ownerDepartment,
        String managerId
    ) {}
    
    record UpdateProjectRequest(
        String name,
        String description,
        String status,
        String managerId
    ) {}
    
    record ProjectQuery(
        String status,
        String department,
        String managerId,
        Integer limit,
        Integer offset
    ) {}
}
