package com.livingagent.core.model.selector;

import org.springframework.stereotype.Component;

/**
 * HrBrain model selector - compatibility adapter layer.
 * Actual model resolution is handled by BrainModelResolver via the model pool.
 */
@Component
public class HrBrainModelSelector extends BrainModelSelector {

    public HrBrainModelSelector() {
        super("neuron://hr/hr-brain/001", "HrBrain", "hr");
    }

    @Override
    protected void initializeAvailableModels() {
    }

    @Override
    protected BrainModel createDefaultModel() {
        return new BrainModel("model-pool", "Model Pool", "model-pool", 32768, true, true,
            "招聘管理、考勤、绩效");
    }
}
