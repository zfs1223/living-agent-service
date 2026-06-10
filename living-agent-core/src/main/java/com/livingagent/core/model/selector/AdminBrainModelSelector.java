package com.livingagent.core.model.selector;

import org.springframework.stereotype.Component;

/**
 * AdminBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class AdminBrainModelSelector extends BrainModelSelector {

    public AdminBrainModelSelector() {
        super("neuron://admin/admin-brain/001", "AdminBrain", "admin");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "文档处理、文案创作、行政事务");
    }
}
