package com.livingagent.gateway.controller;

import com.livingagent.core.model.pool.*;
import com.livingagent.core.model.selector.BrainModelSelector;
import com.livingagent.core.model.selector.BrainModelSelectorManager;
import com.livingagent.gateway.dto.BrainModelRequest;
import com.livingagent.gateway.dto.BrainModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/brain-models")
public class BrainModelConfigController {

    private final Logger log = LoggerFactory.getLogger(BrainModelConfigController.class);
    private final BrainModelSelectorManager selectorManager;
    private final BrainModelAssigner brainModelAssigner;
    private final ModelPoolManager modelPoolManager;

    public BrainModelConfigController(
            BrainModelSelectorManager selectorManager,
            BrainModelAssigner brainModelAssigner,
            ModelPoolManager modelPoolManager) {
        this.selectorManager = selectorManager;
        this.brainModelAssigner = brainModelAssigner;
        this.modelPoolManager = modelPoolManager;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAssignments() {
        List<BrainModelAssignment> assignments = brainModelAssigner.getAllAssignments();
        var data = assignments.stream().map(a -> {
            LlmModel model = modelPoolManager.getModelById(a.getModelId());
            return Map.of(
                "brainId", a.getBrainId(),
                "brainName", a.getBrainName(),
                "brainType", a.getBrainType(),
                "modelId", a.getModelId(),
                "modelName", model != null ? model.getModelName() : null,
                "displayName", model != null ? model.getDisplayName() : null,
                "assignedAt", a.getAssignedAt(),
                "updatedAt", a.getUpdatedAt()
            );
        }).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/{brainId}")
    public ResponseEntity<Map<String, Object>> getAssignment(@PathVariable String brainId) {
        LlmModel model = brainModelAssigner.getModelForBrain(brainId);
        BrainModelAssignment assignment = brainModelAssigner.getAssignment(brainId);
        if (model == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
            "brainId", brainId,
            "brainName", model.getDisplayName(),
            "modelName", model.getModelName(),
            "providerId", model.getProviderId(),
            "displayName", model.getDisplayName(),
            "contextWindow", model.getContextWindow(),
            "hasAssignment", assignment != null
        )));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> assignModel(
            @RequestParam String brainId, @RequestBody BrainModelRequest request) {
        log.info("PUT /api/brain-models?brainId={} - assigning model: {}", brainId, request.modelId());
        try {
            String brainType = extractBrainType(brainId);
            String brainName = extractBrainName(brainId);
            BrainModelAssignment assignment = brainModelAssigner.assignModel(
                brainId, brainName, brainType, request.modelId(), "system");

            LlmModel model = modelPoolManager.getModelById(request.modelId());

            return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "brainId", assignment.getBrainId(),
                "brainName", assignment.getBrainName(),
                "brainType", assignment.getBrainType(),
                "modelId", request.modelId(),
                "modelName", model != null ? model.getModelName() : null,
                "displayName", model != null ? model.getDisplayName() : null,
                "assignedAt", assignment.getAssignedAt()
            )));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "INVALID_MODEL", "message", e.getMessage()));
        }
    }

    @PutMapping("/{brainId}")
    public ResponseEntity<Map<String, Object>> assignModelWithPathVar(
            @PathVariable String brainId, @RequestBody BrainModelRequest request) {
        return assignModel(brainId, request);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAssignment(@RequestParam String brainId) {
        log.info("DELETE /api/brain-models?brainId={} - clearing assignment", brainId);
        brainModelAssigner.clearAssignment(brainId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{brainId}")
    public ResponseEntity<Map<String, Object>> clearAssignmentWithPathVar(@PathVariable String brainId) {
        return clearAssignment(brainId);
    }

    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchModelLegacy(
            @RequestParam String brainId,
            @RequestBody BrainModelResponse.SwitchModelRequest request) {
        log.info("POST /api/brain-models/switch?brainId={} - legacy switch to: {}", brainId, request.modelId());
        try {
            var result = selectorManager.switchModel(brainId, request.modelId());
            BrainModelResponse response = toResponse(result);
            return ResponseEntity.ok(Map.of("success", true, "data", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "INVALID_MODEL", "message", e.getMessage()));
        }
    }

    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableModels() {
        List<LlmModel> models = modelPoolManager.getAllModels();
        var data = models.stream().map(m -> Map.of(
            "id", m.getId(),
            "modelName", m.getModelName(),
            "displayName", m.getDisplayName(),
            "providerId", m.getProviderId(),
            "contextWindow", m.getContextWindow(),
            "recommended", m.isRecommended(),
            "bestFor", m.getBestFor()
        )).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    private String extractBrainType(String brainId) {
        if (brainId == null) return "default";
        if (brainId.contains("main")) return "main";
        if (brainId.contains("tech")) return "tech";
        if (brainId.contains("admin")) return "admin";
        if (brainId.contains("hr")) return "hr";
        if (brainId.contains("finance")) return "finance";
        if (brainId.contains("sales")) return "sales";
        if (brainId.contains("cs")) return "cs";
        if (brainId.contains("ops")) return "ops";
        if (brainId.contains("legal")) return "legal";
        return "default";
    }

    private String extractBrainName(String brainId) {
        if (brainId == null) return "Unknown";
        if (brainId.contains("main")) return "MainBrain";
        if (brainId.contains("tech")) return "TechBrain";
        if (brainId.contains("admin")) return "AdminBrain";
        if (brainId.contains("hr")) return "HrBrain";
        if (brainId.contains("finance")) return "FinanceBrain";
        if (brainId.contains("sales")) return "SalesBrain";
        if (brainId.contains("cs")) return "CsBrain";
        if (brainId.contains("ops")) return "OpsBrain";
        if (brainId.contains("legal")) return "LegalBrain";
        return "Unknown";
    }

    private BrainModelResponse toResponse(BrainModelSelector.BrainModelConfigInfo config) {
        return new BrainModelResponse(
            config.brainId(),
            config.brainName(),
            config.department(),
            new BrainModelResponse.ModelInfo(
                config.currentModel().modelId(),
                config.currentModel().displayName(),
                config.currentModel().provider(),
                config.currentModel().contextLength(),
                config.currentModel().hasApiKey()
            ),
            config.availableModels().stream()
                .map(m -> new BrainModelResponse.AvailableModel(
                    m.id(), m.displayName(), m.provider(), m.contextLength(),
                    m.cloudAvailable(), m.recommended(), m.bestFor()))
                .toList(),
            config.lastUpdated()
        );
    }
}
