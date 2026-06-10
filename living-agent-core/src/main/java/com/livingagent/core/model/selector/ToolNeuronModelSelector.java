package com.livingagent.core.model.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class ToolNeuronModelSelector {
    
    private static final Logger log = LoggerFactory.getLogger(ToolNeuronModelSelector.class);
    
    public enum ModelType {
        QWEN35_2B("qwen3.5-2b", "Qwen3.5-2B", 4 * 1024, 262144, true, true),
        QWEN3_06B("qwen3-0.6b", "Qwen3-0.6B", 512, 4096, false, false);
        
        private final String id;
        private final String displayName;
        private final int memoryMB;
        private final int contextLength;
        private final boolean multimodal;
        private final boolean recommended;
        
        ModelType(String id, String displayName, int memoryMB, int contextLength, boolean multimodal, boolean recommended) {
            this.id = id;
            this.displayName = displayName;
            this.memoryMB = memoryMB;
            this.contextLength = contextLength;
            this.multimodal = multimodal;
            this.recommended = recommended;
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getMemoryMB() { return memoryMB; }
        public int getContextLength() { return contextLength; }
        public boolean isMultimodal() { return multimodal; }
        public boolean isRecommended() { return recommended; }
    }
    
    @Value("${tool-neuron.model.default:qwen3.5-2b}")
    private String defaultModelId;
    
    @Value("${tool-neuron.model.auto-select:true}")
    private boolean autoSelectEnabled;
    
    @Value("${tool-neuron.model.memory-threshold-mb:2048}")
    private int memoryThresholdMB;
    
    private final AtomicReference<ModelType> currentModel = new AtomicReference<>();
    private final HardwareResourceMonitor resourceMonitor;
    
    public ToolNeuronModelSelector(HardwareResourceMonitor resourceMonitor) {
        this.resourceMonitor = resourceMonitor;
    }
    
    public ModelType selectModel() {
        if (!autoSelectEnabled) {
            ModelType manualModel = getModelById(defaultModelId);
            currentModel.set(manualModel);
            return manualModel;
        }
        
        ModelType selected = selectBasedOnResources();
        ModelType previous = currentModel.getAndSet(selected);
        
        if (previous != selected) {
            log.info("Tool neuron model changed: {} -> {}", 
                previous != null ? previous.getDisplayName() : "none", 
                selected.getDisplayName());
        }
        
        return selected;
    }
    
    public ModelType getCurrentModel() {
        ModelType model = currentModel.get();
        if (model == null) {
            return selectModel();
        }
        return model;
    }
    
    public void setCurrentModel(ModelType model) {
        ModelType previous = currentModel.getAndSet(model);
        log.info("Tool neuron model manually set: {} -> {}", 
            previous != null ? previous.getDisplayName() : "none", 
            model.getDisplayName());
    }
    
    public void setCurrentModel(String modelId) {
        ModelType model = getModelById(modelId);
        if (model != null) {
            setCurrentModel(model);
        } else {
            log.warn("Unknown model ID: {}, keeping current model", modelId);
        }
    }
    
    private ModelType selectBasedOnResources() {
        long availableMemoryMB = resourceMonitor.getAvailableMemoryMB();
        int availableProcessors = resourceMonitor.getAvailableProcessors();
        double cpuLoad = resourceMonitor.getCpuLoad();
        
        log.debug("Resource check: memory={}MB, processors={}, cpuLoad={}%", 
            availableMemoryMB, availableProcessors, cpuLoad * 100);
        
        if (availableMemoryMB >= ModelType.QWEN35_2B.getMemoryMB() && 
            availableProcessors >= 4 && 
            cpuLoad < 0.8) {
            log.info("Sufficient resources for Qwen3.5-2B (recommended)");
            return ModelType.QWEN35_2B;
        }
        
        if (availableMemoryMB >= ModelType.QWEN3_06B.getMemoryMB()) {
            log.info("Limited resources, falling back to Qwen3-0.6B");
            return ModelType.QWEN3_06B;
        }
        
        log.warn("Very limited resources, using Qwen3-0.6B as fallback");
        return ModelType.QWEN3_06B;
    }
    
    public ModelType getModelById(String id) {
        for (ModelType type : ModelType.values()) {
            if (type.getId().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
    
    public boolean shouldUseQwen35() {
        return getCurrentModel() == ModelType.QWEN35_2B;
    }
    
    public boolean shouldUseQwen3() {
        return getCurrentModel() == ModelType.QWEN3_06B;
    }
    
    public String getModelConfig() {
        ModelType model = getCurrentModel();
        return String.format(
            "Model: %s, Memory: %dMB, Context: %d, Multimodal: %s",
            model.getDisplayName(),
            model.getMemoryMB(),
            model.getContextLength(),
            model.isMultimodal()
        );
    }
    
    public ModelComparison getComparison() {
        return new ModelComparison();
    }
    
    public static class ModelComparison {
        public final String qwen35Id = ModelType.QWEN35_2B.getId();
        public final String qwen35DisplayName = ModelType.QWEN35_2B.getDisplayName();
        public final int qwen35MemoryMB = ModelType.QWEN35_2B.getMemoryMB();
        public final int qwen35ContextLength = ModelType.QWEN35_2B.getContextLength();
        public final boolean qwen35Multimodal = ModelType.QWEN35_2B.isMultimodal();
        
        public final String qwen3Id = ModelType.QWEN3_06B.getId();
        public final String qwen3DisplayName = ModelType.QWEN3_06B.getDisplayName();
        public final int qwen3MemoryMB = ModelType.QWEN3_06B.getMemoryMB();
        public final int qwen3ContextLength = ModelType.QWEN3_06B.getContextLength();
        public final boolean qwen3Multimodal = ModelType.QWEN3_06B.isMultimodal();
        
        public final String recommendedDefault = ModelType.QWEN35_2B.getId();
    }
}
