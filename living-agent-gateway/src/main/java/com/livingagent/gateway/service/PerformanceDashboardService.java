package com.livingagent.gateway.service;

import com.livingagent.core.employee.EmployeeCompensationService;
import com.livingagent.core.operation.performance.PerformanceAssessmentService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PerformanceDashboardService {

    private final PerformanceAssessmentService assessmentService;
    private final EmployeeCompensationService compensationService;

    public PerformanceDashboardService(PerformanceAssessmentService assessmentService,
                                       EmployeeCompensationService compensationService) {
        this.assessmentService = assessmentService;
        this.compensationService = compensationService;
    }

    public Map<String, Object> companySummary(String departmentId, int limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("departmentId", departmentId);
        payload.put("departmentAverageScores", departmentId != null ? assessmentService.getDepartmentAverageScores(departmentId) : Map.of());
        payload.put("topPerformers", departmentId != null ? assessmentService.getTopPerformers(departmentId, limit) : assessmentService.getCompanyTopPerformers(limit));
        payload.put("bottomPerformers", departmentId != null ? assessmentService.getBottomPerformers(departmentId, limit) : assessmentService.getCompanyBottomPerformers(limit));
        payload.put("compensation", departmentId != null ? compensationService.summarizeDepartment(departmentId) : Map.of());
        payload.put("summary", buildSummary(departmentId, limit));
        return payload;
    }

    public Map<String, Object> buildSummary(String departmentId, int limit) {
        List<PerformanceAssessmentService.EmployeeRanking> top = departmentId != null ? assessmentService.getTopPerformers(departmentId, limit) : assessmentService.getCompanyTopPerformers(limit);
        List<PerformanceAssessmentService.EmployeeRanking> bottom = departmentId != null ? assessmentService.getBottomPerformers(departmentId, limit) : assessmentService.getCompanyBottomPerformers(limit);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("topCount", top.size());
        summary.put("bottomCount", bottom.size());
        summary.put("averageTopScore", top.stream().mapToDouble(PerformanceAssessmentService.EmployeeRanking::score).average().orElse(0.0));
        summary.put("averageBottomScore", bottom.stream().mapToDouble(PerformanceAssessmentService.EmployeeRanking::score).average().orElse(0.0));
        summary.put("compensationBalance", departmentId != null ? compensationService.summarizeDepartment(departmentId).getOrDefault("totalBalance", 0) : 0);
        return summary;
    }
}
