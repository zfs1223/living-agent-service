package com.livingagent.gateway.controller;

import com.livingagent.core.operation.dashboard.DashboardDTOs;
import com.livingagent.core.operation.dashboard.DashboardService;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.DashboardDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardDataService dashboardDataService;
    private final DashboardService dashboardService;
    private final AccessGateService accessGateService;

    public DashboardController(DashboardDataService dashboardDataService, DashboardService dashboardService, AccessGateService accessGateService) {
        this.dashboardDataService = dashboardDataService;
        this.dashboardService = dashboardService;
        this.accessGateService = accessGateService;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(@RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }
        return ResponseEntity.ok(dashboardDataService.buildOverview());
    }

    @GetMapping("/enterprise/summary")
    public ResponseEntity<ApiResponse<DashboardDTOs.EnterpriseSummary>> getEnterpriseSummary(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401)
                .body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        if (!accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403)
                .body(ApiResponse.err("forbidden", "Enterprise access required"));
        }

        DashboardDTOs.EnterpriseSummary summary = dashboardService.getEnterpriseSummary(employeeId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/enterprise/departments")
    public ResponseEntity<ApiResponse<List<DashboardDTOs.DepartmentHealth>>> getDepartmentHealth(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        if (employeeId == null || !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403)
                .body(ApiResponse.err("forbidden", "Enterprise access required"));
        }

        List<DashboardDTOs.DepartmentHealth> health = dashboardService.getDepartmentHealth();
        return ResponseEntity.ok(ApiResponse.ok(health));
    }

    @GetMapping("/enterprise/risks")
    public ResponseEntity<ApiResponse<List<DashboardDTOs.RiskAlert>>> getRiskAlerts(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        if (employeeId == null || !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403)
                .body(ApiResponse.err("forbidden", "Enterprise access required"));
        }

        List<DashboardDTOs.RiskAlert> alerts = dashboardService.getRiskAlerts();
        return ResponseEntity.ok(ApiResponse.ok(alerts));
    }

    @GetMapping("/enterprise/costs")
    public ResponseEntity<ApiResponse<DashboardDTOs.CostAnalysis>> getCostAnalysis(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        if (employeeId == null || !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403)
                .body(ApiResponse.err("forbidden", "Enterprise access required"));
        }

        DashboardDTOs.CostAnalysis costAnalysis = dashboardService.getCostAnalysis();
        return ResponseEntity.ok(ApiResponse.ok(costAnalysis));
    }

    @GetMapping("/department/{code}/summary")
    public ResponseEntity<ApiResponse<DashboardDTOs.DepartmentSummary>> getDepartmentSummary(
            @PathVariable String code,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401)
                .body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        boolean hasAccess = accessGateService.hasFullAccess(employeeId)
                         || accessGateService.belongsToDepartment(employeeId, code);

        if (!hasAccess) {
            return ResponseEntity.status(403)
                .body(ApiResponse.err("forbidden", "Department access required"));
        }

        DashboardDTOs.DepartmentSummary summary = dashboardService.getDepartmentSummary(code);
        if (summary == null) {
            return ResponseEntity.status(404)
                .body(ApiResponse.err("not_found", "Department not found: " + code));
        }
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/employee/workspace")
    public ResponseEntity<ApiResponse<DashboardDTOs.WorkspaceSummary>> getWorkspaceSummary(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401)
                .body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        DashboardDTOs.WorkspaceSummary summary = dashboardService.getWorkspaceSummary(employeeId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
