package com.livingagent.gateway.controller;

import com.livingagent.core.diagnosis.VitalSignsService;
import com.livingagent.core.diagnosis.VitalSignsService.VitalSnapshot;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * P32-A: 生命体征仪表盘 REST API。
 */
@RestController
@RequestMapping("/api/vitals")
public class VitalSignsController {

    private final VitalSignsService vitalSignsService;

    public VitalSignsController(VitalSignsService vitalSignsService) {
        this.vitalSignsService = vitalSignsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<VitalSnapshot>> getCurrentVitals() {
        return ResponseEntity.ok(ApiResponse.ok(vitalSignsService.getCurrentVitals()));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<VitalSnapshot>>> getVitalHistory(
            @RequestParam(defaultValue = "30") int minutes) {
        Duration duration = Duration.ofMinutes(Math.max(1, Math.min(minutes, 360)));
        return ResponseEntity.ok(ApiResponse.ok(vitalSignsService.getVitalHistory(duration)));
    }
}
