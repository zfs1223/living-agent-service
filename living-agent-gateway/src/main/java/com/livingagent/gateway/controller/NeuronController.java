package com.livingagent.gateway.controller;

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

    @GetMapping
    public ResponseEntity<ApiResponse<List<NeuronInfo>>> listNeurons() {
        log.debug("Listing neurons");

        List<NeuronInfo> neurons = new ArrayList<>();
        neurons.add(new NeuronInfo(
                "neuron://tech/code-reviewer/001",
                "代码审查神经元",
                "tech",
                "running",
                "Qwen3.5-27B",
                Instant.now()
        ));
        neurons.add(new NeuronInfo(
                "neuron://hr/recruiter/001",
                "招聘神经元",
                "hr",
                "running",
                "Qwen3.5-27B",
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.success(neurons));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NeuronDetail>> getNeuron(@PathVariable String id) {
        log.debug("Getting neuron: {}", id);

        NeuronDetail detail = new NeuronDetail(
                id,
                "代码审查神经元",
                "tech",
                "running",
                "Qwen3.5-27B",
                "负责代码审查和质量把控",
                List.of("code-review", "quality-check"),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<NeuronStatus>> getNeuronStatus(@PathVariable String id) {
        log.debug("Getting neuron status: {}", id);

        NeuronStatus status = new NeuronStatus(
                id,
                "running",
                0.85,
                128,
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<ApiResponse<NeuronMetrics>> getNeuronMetrics(@PathVariable String id) {
        log.debug("Getting neuron metrics: {}", id);

        NeuronMetrics metrics = new NeuronMetrics(
                id,
                1000,
                950,
                0.95,
                150.5,
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
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
