package com.livingagent.core.model.selector;

import org.springframework.stereotype.Component;

/**
 * CsBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class CsBrainModelSelector extends BrainModelSelector {

    public CsBrainModelSelector() {
        super("neuron://cs/cs-brain/001", "CsBrain", "cs");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "工单处理、问题解答");
    }
}
