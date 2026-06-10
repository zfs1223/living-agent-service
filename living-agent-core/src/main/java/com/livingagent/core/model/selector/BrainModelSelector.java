package com.livingagent.core.model.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BrainModelSelector {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String brainId;
    protected final String brainName;
    protected final String department;

    protected final AtomicReference<BrainModel> currentModel = new AtomicReference<>();
    protected final List<BrainModel> availableModels = new ArrayList<>();

    public BrainModelSelector(String brainId, String brainName, String department) {
        this.brainId = brainId;
        this.brainName = brainName;
        this.department = department;
        initializeAvailableModels();
        this.currentModel.set(createDefaultModel());
        log.info("BrainModelSelector initialized: brain={}, department={}, defaultModel={}",
            brainName, department, currentModel.get().displayName());
    }

    protected abstract void initializeAvailableModels();
    protected abstract BrainModel createDefaultModel();

    public BrainModel selectModel() {
        return currentModel.get();
    }

    public String getEffectiveModelId() {
        return currentModel.get().id();
    }

    public synchronized void setCurrentModel(BrainModel model) {
        if (model == null) {
            log.warn("Attempted to set null model for brain: {}", brainId);
            return;
        }
        BrainModel previous = currentModel.getAndSet(model);
        log.info("Model switched for {}: {} -> {}", brainName,
            previous != null ? previous.displayName() : "null",
            model.displayName());
    }

    public synchronized void setModelById(String modelId) {
        BrainModel model = findModelById(modelId);
        if (model == null) {
            log.warn("Unknown model ID: {} for brain: {}", modelId, brainId);
            throw new IllegalArgumentException("Unknown model: " + modelId);
        }
        setCurrentModel(model);
    }

    public BrainModel findModelById(String modelId) {
        if (modelId == null) return null;
        return availableModels.stream()
            .filter(m -> m.id().equalsIgnoreCase(modelId))
            .findFirst()
            .orElse(null);
    }

    public String getBrainId() { return brainId; }
    public String getBrainName() { return brainName; }
    public String getDepartment() { return department; }
    public List<BrainModel> getAvailableModels() { return Collections.unmodifiableList(availableModels); }
    public BrainModel getCurrentModel() { return currentModel.get(); }

    public BrainModelConfigInfo getConfigInfo() {
        BrainModel model = currentModel.get();
        return new BrainModelConfigInfo(
            brainId,
            brainName,
            department,
            new ModelConfig(
                model.id(),
                model.displayName(),
                model.provider(),
                model.contextLength(),
                "",
                false
            ),
            availableModels,
            System.currentTimeMillis()
        );
    }

    /**
     * Whether this selector supports automatic model adjustment.
     * Override to disable auto-adjust for specific brains.
     */
    public boolean supportsAutoAdjust() {
        return true;
    }

    /**
     * Score a candidate model for this brain.
     * Override to implement brain-specific scoring logic.
     */
    public double scoreCandidate(BrainModel model) {
        double score = 0.0;
        if (model.recommended()) score += 0.3;
        if (model.cloudAvailable()) score += 0.2;
        return Math.min(1.0, score);
    }

    /**
     * Filter candidate models for auto-adjust.
     * Override to implement brain-specific filtering rules.
     */
    public boolean isCandidateCompatible(BrainModel model) {
        return model.cloudAvailable() && model.contextLength() >= 8192;
    }

    /**
     * Whether this selector supports a given brain type.
     * Override to restrict selector to specific brain types.
     */
    public boolean supportsBrainType(String brainType) {
        return true;
    }

    public record BrainModel(
        String id,
        String displayName,
        String provider,
        int contextLength,
        boolean cloudAvailable,
        boolean recommended,
        String bestFor
    ) {}

    public record ModelConfig(
        String modelId,
        String displayName,
        String provider,
        int contextLength,
        String baseUrl,
        boolean hasApiKey
    ) {}

    public record BrainModelConfigInfo(
        String brainId,
        String brainName,
        String department,
        ModelConfig currentModel,
        List<BrainModel> availableModels,
        long lastUpdated
    ) {}
}
