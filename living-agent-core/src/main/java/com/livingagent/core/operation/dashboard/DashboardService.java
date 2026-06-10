package com.livingagent.core.operation.dashboard;

import java.util.List;

public interface DashboardService {

    DashboardDTOs.EnterpriseSummary getEnterpriseSummary(String employeeId);

    List<DashboardDTOs.DepartmentHealth> getDepartmentHealth();

    List<DashboardDTOs.DepartmentSummary> getDepartmentSummaries();

    List<DashboardDTOs.RiskAlert> getRiskAlerts();

    DashboardDTOs.CostAnalysis getCostAnalysis();

    DashboardDTOs.DepartmentSummary getDepartmentSummary(String code);

    DashboardDTOs.WorkspaceSummary getWorkspaceSummary(String employeeId);
}
