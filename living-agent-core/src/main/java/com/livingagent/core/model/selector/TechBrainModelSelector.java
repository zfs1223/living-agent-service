package com.livingagent.core.model.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TechBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class TechBrainModelSelector extends BrainModelSelector {

    private static final Logger log = LoggerFactory.getLogger(TechBrainModelSelector.class);

    public TechBrainModelSelector() {
        super("neuron://tech/tech-brain/001", "TechBrain", "tech");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "代码审查、架构设计、复杂推理");
    }
}
