package com.livingagent.core.autonomous.incentive;

import com.livingagent.core.autonomous.evolution.EvolutionManager;

public interface EvolutionTracker {

    void recordAchievement(String employeeId, IncentiveManager.TaskResult result);

    void updateTier(String employeeId);

    EvolutionManager.EvolutionTier getCurrentTier(String employeeId);

    int getAccumulatedFunds(String employeeId);
}
