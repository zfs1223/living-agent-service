package com.livingagent.core.model.selector;

import org.springframework.stereotype.Component;

/**
 * FinanceBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class FinanceBrainModelSelector extends BrainModelSelector {

    public FinanceBrainModelSelector() {
        super("neuron://finance/finance-brain/001", "FinanceBrain", "finance");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "财务分析、预算管理");
    }
}
