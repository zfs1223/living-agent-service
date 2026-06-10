package com.livingagent.core.model.selector;

import org.springframework.stereotype.Component;

/**
 * LegalBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class LegalBrainModelSelector extends BrainModelSelector {

    public LegalBrainModelSelector() {
        super("neuron://legal/legal-brain/001", "LegalBrain", "legal");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "合同审查、合规检查");
    }
}
