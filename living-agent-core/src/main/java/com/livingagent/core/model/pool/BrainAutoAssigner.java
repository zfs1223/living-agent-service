package com.livingagent.core.model.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BrainAutoAssigner {

    private static final Logger log = LoggerFactory.getLogger(BrainAutoAssigner.class);

    private final BrainModelAssignmentRepository assignmentRepo;
    private final LlmModelRepository modelRepo;
    private final ProviderConfigRepository providerRepo;
    private final ModelHealthRegistry modelHealthRegistry;

    private final AtomicBoolean hasRun = new AtomicBoolean(false);

    public static final List<BrainDefinition> ALL_BRAINS = List.of(
        new BrainDefinition("neuron://core/main-brain/001", "MainBrain", "main",
            List.of("推理", "规划", "协调", "决策", "综合")),
        new BrainDefinition("neuron://tech/tech-brain/001", "TechBrain", "tech",
            List.of("代码", "编程", "开发", "架构", "推理")),
        new BrainDefinition("neuron://admin/admin-brain/001", "AdminBrain", "admin",
            List.of("文档", "写作", "行政", "综合")),
        new BrainDefinition("neuron://hr/hr-brain/001", "HrBrain", "hr",
            List.of("招聘", "管理", "绩效", "沟通")),
        new BrainDefinition("neuron://finance/finance-brain/001", "FinanceBrain", "finance",
            List.of("财务", "预算", "分析", "计算")),
        new BrainDefinition("neuron://sales/sales-brain/001", "SalesBrain", "sales",
            List.of("营销", "销售", "客户", "沟通")),
        new BrainDefinition("neuron://cs/cs-brain/001", "CsBrain", "cs",
            List.of("客服", "沟通", "问题解决", "响应")),
        new BrainDefinition("neuron://ops/ops-brain/001", "OpsBrain", "ops",
            List.of("运维", "监控", "部署", "自动化")),
        new BrainDefinition("neuron://legal/legal-brain/001", "LegalBrain", "legal",
            List.of("法务", "合规", "审查", "文档"))
    );

    public BrainAutoAssigner(
            BrainModelAssignmentRepository assignmentRepo,
            LlmModelRepository modelRepo,
            ProviderConfigRepository providerRepo,
            ModelHealthRegistry modelHealthRegistry) {
        this.assignmentRepo = assignmentRepo;
        this.modelRepo = modelRepo;
        this.providerRepo = providerRepo;
        this.modelHealthRegistry = modelHealthRegistry;
    }

    public void tryAutoAssignIfNeeded() {
        if (hasRun.get()) {
            return;
        }
        List<LlmModel> enabledModels = modelRepo.findByEnabledTrue();
        if (enabledModels.isEmpty()) {
            log.info("[BrainAutoAssign] No enabled models in pool, skipping auto-assignment");
            return;
        }

        long availableCount = countAvailableModels(enabledModels);
        if (availableCount == 0) {
            log.info("[BrainAutoAssign] {} enabled models but 0 available after health check, skipping", enabledModels.size());
            return;
        }

        log.info("[BrainAutoAssign] Starting auto-assignment: {} enabled models, {} available", enabledModels.size(), availableCount);
        
        int assigned = 0;
        int skipped = 0;
        int failed = 0;

        for (BrainDefinition brain : ALL_BRAINS) {
            boolean alreadyAssigned = assignmentRepo.findByBrainId(brain.brainId).isPresent();
            if (alreadyAssigned) {
                skipped++;
                continue;
            }

            try {
                LlmModel selected = selectBestModelForBrain(brain, enabledModels);
                if (selected != null) {
                    BrainModelAssignment assignment = assignmentRepo
                        .findByBrainId(brain.brainId)
                        .orElse(new BrainModelAssignment());
                    assignment.setBrainId(brain.brainId);
                    assignment.setBrainName(brain.brainName);
                    assignment.setBrainType(brain.brainType);
                    assignment.setModelId(selected.getId());
                    assignment.setAssignedBy("auto-assign");
                    assignment.setAssignedAt(LocalDateTime.now());
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepo.save(assignment);
                    log.info("[BrainAutoAssign] Assigned {} -> {}/{} ({})",
                        brain.brainName, selected.getProviderId(), selected.getModelName(), selected.getDisplayName());
                    assigned++;
                } else {
                    log.warn("[BrainAutoAssign] No suitable model found for {}", brain.brainName);
                    failed++;
                }
            } catch (Exception e) {
                log.error("[BrainAutoAssign] Failed to assign model for {}: {}", brain.brainName, e.getMessage());
                failed++;
            }
        }

        hasRun.set(true);
        log.info("[BrainAutoAssign] Completed: {} assigned, {} already configured, {} failed",
            assigned, skipped, failed);
    }

    public void resetAndReassign() {
        hasRun.set(false);
        tryAutoAssignIfNeeded();
    }

    private long countAvailableModels(List<LlmModel> models) {
        return models.stream()
            .filter(m -> {
                if (m.getId() != null) {
                    String idStr = m.getId().toString();
                    return modelHealthRegistry.isModelAvailable(idStr);
                }
                return true;
            })
            .count();
    }

    private LlmModel selectBestModelForBrain(BrainDefinition brain, List<LlmModel> candidates) {
        List<ModelScore> scored = new ArrayList<>();

        for (LlmModel model : candidates) {
            ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);
            if (provider == null || !provider.isEnabled()) continue;
            if (!isModelAvailable(model)) continue;

            double score = calculateScore(model, brain.preferredCapabilities);
            scored.add(new ModelScore(model, score));
        }

        if (scored.isEmpty()) return null;

        scored.sort(Comparator.comparingDouble(ModelScore::score).reversed());
        return scored.get(0).model;
    }

    private double calculateScore(LlmModel model, List<String> preferredCapabilities) {
        double score = 0.0;

        if (model.isRecommended()) score += 30.0;
        Integer contextWindow = model.getContextWindow();
        if (contextWindow != null && contextWindow >= 32000) score += 10.0;
        if (contextWindow != null && contextWindow >= 128000) score += 10.0;

        String bestFor = model.getBestFor() != null ? model.getBestFor().toLowerCase() : "";
        for (String cap : preferredCapabilities) {
            if (bestFor.contains(cap.toLowerCase())) {
                score += 20.0;
            }
        }

        Integer perfScore = model.getPerformanceScore();
        if (perfScore != null && perfScore > 0) {
            score += Math.min(perfScore * 0.5, 20.0);
        }

        if ("ollama".equalsIgnoreCase(model.getProviderId())) {
            score -= 5.0;
        }

        return score;
    }

    private boolean isModelAvailable(LlmModel model) {
        if (model.getId() == null) return true;
        return modelHealthRegistry.isModelAvailable(model.getId().toString());
    }

    public record BrainDefinition(
        String brainId,
        String brainName,
        String brainType,
        List<String> preferredCapabilities
    ) {}

    private record ModelScore(LlmModel model, double score) {}
}
