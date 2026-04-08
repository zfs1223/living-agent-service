package com.livingagent.core.project;

public record ProjectStatistics(
    int totalProjects,
    int planningCount,
    int inProgressCount,
    int completedCount,
    int onHoldCount,
    int cancelledCount
) {
    public double getCompletionRate() {
        return totalProjects > 0 ? (double) completedCount / totalProjects * 100 : 0;
    }
    
    public double getActiveRate() {
        return totalProjects > 0 ? (double) inProgressCount / totalProjects * 100 : 0;
    }
}
