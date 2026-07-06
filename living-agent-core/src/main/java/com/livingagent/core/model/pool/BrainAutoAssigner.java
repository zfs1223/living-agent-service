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

    /**
     * 升级到更优模型的评分差距阈值。
     * 只有当候选模型评分比当前模型高出此阈值时才触发升级，避免频繁切换。
     */
    private static final double UPGRADE_SCORE_THRESHOLD = 30.0;

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
        int upgraded = 0;
        int failed = 0;

        for (BrainDefinition brain : ALL_BRAINS) {
            Optional<BrainModelAssignment> existingAssignment = assignmentRepo.findByBrainId(brain.brainId);

            // 检查是否已分配且 modelId 有效（不为 null）
            if (existingAssignment.isPresent()) {
                BrainModelAssignment assignment = existingAssignment.get();
                if (assignment.getModelId() == null) {
                    // modelId 为 null，删除无效记录并重新分配
                    log.warn("[BrainAutoAssign] Found invalid assignment for {} with null modelId, deleting and re-assigning", brain.brainName);
                    assignmentRepo.delete(assignment);
                } else {
                    // 有效分配，检查是否需要升级到更优模型
                    if (tryUpgradeIfSuboptimal(brain, assignment, enabledModels)) {
                        upgraded++;
                    } else {
                        skipped++;
                    }
                    continue;
                }
            }

            try {
                LlmModel selected = selectBestModelForBrain(brain, enabledModels);
                if (selected != null && selected.getId() != null) {
                    BrainModelAssignment assignment = new BrainModelAssignment();
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
                } else if (selected != null && selected.getId() == null) {
                    log.warn("[BrainAutoAssign] Selected model for {} has null id, skipping: provider={}, model={}",
                        brain.brainName, selected.getProviderId(), selected.getModelName());
                    failed++;
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
        log.info("[BrainAutoAssign] Completed: {} assigned, {} upgraded, {} already optimal, {} failed",
            assigned, upgraded, skipped, failed);
    }

    /**
     * 检查已分配的大脑是否使用了非最优模型，如果是则升级到更优模型。
     * <p>
     * 当存在评分明显更高的可用模型时（评分差距 >= UPGRADE_SCORE_THRESHOLD），更新分配。
     * 避免大脑被固定在过时或低效的模型上。
     *
     * @return true 表示已升级到更优模型，false 表示当前分配已是最优或升级失败
     */
    private boolean tryUpgradeIfSuboptimal(BrainDefinition brain, BrainModelAssignment assignment, List<LlmModel> enabledModels) {
        try {
            LlmModel currentModel = modelRepo.findById(assignment.getModelId()).orElse(null);
            if (currentModel == null || !currentModel.isEnabled()) {
                log.warn("[BrainAutoAssign] Current assigned model for {} not found or disabled, will re-assign", brain.brainName);
                assignmentRepo.delete(assignment);
                LlmModel selected = selectBestModelForBrain(brain, enabledModels);
                if (selected != null && selected.getId() != null) {
                    assignment.setModelId(selected.getId());
                    assignment.setAssignedBy("auto-assign");
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepo.save(assignment);
                    log.info("[BrainAutoAssign] Re-assigned {} -> {}/{} ({})",
                        brain.brainName, selected.getProviderId(), selected.getModelName(), selected.getDisplayName());
                    return true;
                }
                return false;
            }

            // 评估当前模型和最佳模型的评分
            double currentScore = calculateScore(currentModel, brain.preferredCapabilities);
            LlmModel bestModel = selectBestModelForBrain(brain, enabledModels);

            if (bestModel == null || bestModel.getId() == null) {
                return false;
            }

            // 如果最佳模型就是当前模型，无需升级
            if (bestModel.getId().equals(currentModel.getId())) {
                return false;
            }

            double bestScore = calculateScore(bestModel, brain.preferredCapabilities);

            // 只有当最佳模型评分明显更高时才升级（避免频繁切换）
            if (bestScore - currentScore < UPGRADE_SCORE_THRESHOLD) {
                return false;
            }

            // 检查最佳模型是否可用
            if (!isModelAvailable(bestModel)) {
                return false;
            }

            // 升级到更优模型
            assignment.setModelId(bestModel.getId());
            assignment.setAssignedBy("auto-assign");
            assignment.setUpdatedAt(LocalDateTime.now());
            assignmentRepo.save(assignment);
            log.info("[BrainAutoAssign] Upgraded {} -> {}/{} ({}) (score {} -> {})",
                brain.brainName, bestModel.getProviderId(), bestModel.getModelName(), bestModel.getDisplayName(),
                String.format("%.1f", currentScore), String.format("%.1f", bestScore));
            return true;
        } catch (Exception e) {
            log.warn("[BrainAutoAssign] Failed to check upgrade for {}: {}", brain.brainName, e.getMessage());
            return false;
        }
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

        // 基础分：推荐标记
        if (model.isRecommended()) score += 30.0;

        // 上下文窗口加分
        Integer contextWindow = model.getContextWindow();
        if (contextWindow != null && contextWindow >= 32000) score += 10.0;
        if (contextWindow != null && contextWindow >= 128000) score += 10.0;

        // 能力标签匹配（每个+20）
        String bestFor = model.getBestFor() != null ? model.getBestFor().toLowerCase() : "";
        for (String cap : preferredCapabilities) {
            if (bestFor.contains(cap.toLowerCase())) {
                score += 20.0;
            }
        }

        // 静态性能分（最高+20）
        Integer perfScore = model.getPerformanceScore();
        if (perfScore != null && perfScore > 0) {
            score += Math.min(perfScore * 0.5, 20.0);
        }

        // 运行时成功率加权（最高+25）：高成功率模型优先
        if (model.getId() != null) {
            ModelHealthRegistry.ModelHealthRecord health = modelHealthRegistry.getHealth(model.getId().toString());
            if (health.totalCalls() > 0) {
                double successRate = health.totalSuccesses() * 1.0 / health.totalCalls();
                score += successRate * 25.0; // 成功率100%加25分，50%加12.5分
            }
        }

        // 本地供应商微调（不扣分，仅轻微偏好调整）
        String providerId = model.getProviderId();
        if ("vllm".equalsIgnoreCase(providerId)) {
            score += 3.0; // vllm 通常性能更好，小幅加分
        }
        // 注意：不再对 ollama 扣分

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
