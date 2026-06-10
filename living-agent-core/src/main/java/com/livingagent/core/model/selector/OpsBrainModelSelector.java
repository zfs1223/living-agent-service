package com.livingagent.core.model.selector;

import org.springframework.stereotype.Component;

/**
 * OpsBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class OpsBrainModelSelector extends BrainModelSelector {

    public OpsBrainModelSelector() {
        super("neuron://ops/ops-brain/001", "OpsBrain", "ops");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "数据分析、运营策略");
    }
}
