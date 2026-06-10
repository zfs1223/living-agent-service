package com.livingagent.core.model.selector;

import org.springframework.stereotype.Component;

/**
 * SalesBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class SalesBrainModelSelector extends BrainModelSelector {

    public SalesBrainModelSelector() {
        super("neuron://sales/sales-brain/001", "SalesBrain", "sales");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "销售支持、市场营销");
    }
}
