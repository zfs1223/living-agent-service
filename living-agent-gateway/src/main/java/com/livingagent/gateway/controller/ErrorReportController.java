package com.livingagent.gateway.controller;

import com.livingagent.core.diagnosis.ErrorReportFeedbackService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * P17-D: 前端错误上报接收端点
 */
@RestController
@RequestMapping("/api/error-reports")
public class ErrorReportController {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportController.class);

    private final ErrorReportFeedbackService feedbackService;

    public ErrorReportController(ErrorReportFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> reportErrors(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");

        if (errors != null && !errors.isEmpty()) {
            for (Map<String, Object> error : errors) {
                String type = String.valueOf(error.getOrDefault("type", "unknown"));
                String message = String.valueOf(error.getOrDefault("message", ""));
                String url = String.valueOf(error.getOrDefault("url", ""));
                log.warn("Frontend error [{}]: {} (url={})", type, message, url);
            }
            // P17-C: 高频错误反馈闭环
            feedbackService.processErrorReports(errors);
        }

        return ResponseEntity.ok(ApiResponse.ok());
    }
}
