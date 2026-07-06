package com.livingagent.gateway.controller;

import com.livingagent.core.evolution.personality.SatisfactionCollector;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P29-A: 满意度采集 API
 */
@RestController
@RequestMapping("/api/satisfaction")
public class SatisfactionController {

    private final SatisfactionCollector satisfactionCollector;

    public SatisfactionController(SatisfactionCollector satisfactionCollector) {
        this.satisfactionCollector = satisfactionCollector;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordSatisfaction(
            @RequestBody Map<String, Object> body) {
        String brainDomain = String.valueOf(body.getOrDefault("brainDomain", "unknown"));
        String sessionId = String.valueOf(body.getOrDefault("sessionId", ""));
        int score = Integer.parseInt(String.valueOf(body.getOrDefault("score", "3")));
        String feedback = String.valueOf(body.getOrDefault("feedback", ""));

        SatisfactionCollector.SatisfactionRecord record =
            satisfactionCollector.recordSatisfaction(brainDomain, sessionId, score, feedback);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "recordId", record.recordId(),
            "brainDomain", record.brainDomain(),
            "score", record.score(),
            "averageScore", satisfactionCollector.getAverageScore(brainDomain)
        )));
    }

    @GetMapping("/{brainDomain}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSatisfaction(
            @PathVariable String brainDomain) {
        double avg = satisfactionCollector.getAverageScore(brainDomain);
        var recent = satisfactionCollector.getRecentRecords(brainDomain, 10);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "brainDomain", brainDomain,
            "averageScore", avg,
            "recentCount", recent.size()
        )));
    }
}
