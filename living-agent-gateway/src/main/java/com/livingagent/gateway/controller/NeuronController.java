package com.livingagent.gateway.controller;

import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/neurons")
public class NeuronController {

    private static final Logger log = LoggerFactory.getLogger(NeuronController.class);
    private final AccessGateService accessGateService;
    private final BrainModelResolver brainModelResolver;

    public NeuronController(AccessGateService accessGateService, BrainModelResolver brainModelResolver) {
        this.accessGateService = accessGateService;
        this.brainModelResolver = brainModelResolver;
    }

    private String resolveModelDisplayName(String brainId) {
        try {
            ResolvedBrainModel resolved = brainModelResolver.resolve(brainId);
            if (resolved != null && resolved.getDisplayName() != null && !resolved.getDisplayName().isBlank()) {
                return resolved.getDisplayName();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve model for brain {}: {}", brainId, e.getMessage());
        }
        return "model-pool";
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NeuronInfo>>> listNeurons(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing neurons");

        List<NeuronInfo> neurons = new ArrayList<>();
        neurons.add(new NeuronInfo(
                "neuron://tech/code-reviewer/001",
                "代码审查神经元",
                "tech",
                "running",
                resolveModelDisplayName("neuron://tech/tech-brain/001"),
                Instant.now()
        ));
        neurons.add(new NeuronInfo(
                "neuron://hr/recruiter/001",
                "招聘神经元",
                "hr",
                "running",
                resolveModelDisplayName("neuron://hr/hr-brain/001"),
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.ok(neurons));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NeuronDetail>> getNeuron(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting neuron: {}", id);

        NeuronDetail detail = new NeuronDetail(
                id,
                "代码审查神经元",
                "tech",
                "running",
                resolveModelDisplayName("neuron://tech/tech-brain/001"),
                "负责代码审查和质量把控",
                List.of("code-review", "quality-check"),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<NeuronStatus>> getNeuronStatus(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting neuron status: {}", id);

        NeuronStatus status = new NeuronStatus(
                id,
                "running",
                0.85,
                128,
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<ApiResponse<NeuronMetrics>> getNeuronMetrics(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting neuron metrics: {}", id);

        NeuronMetrics metrics = new NeuronMetrics(
                id,
                1000,
                950,
                0.95,
                150.5,
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }

    public record NeuronInfo(
            String id,
            String name,
            String department,
            String status,
            String model,
            Instant created_at
    ) {}

    public record NeuronDetail(
            String id,
            String name,
            String department,
            String status,
            String model,
            String description,
            List<String> capabilities,
            Instant created_at
    ) {}

    public record NeuronStatus(
            String id,
            String status,
            double load,
            int queue_size,
            Instant last_active
    ) {}

    public record NeuronMetrics(
            String id,
            int total_requests,
            int successful_requests,
            double success_rate,
            double avg_latency_ms,
            Instant updated_at
    ) {}
}
