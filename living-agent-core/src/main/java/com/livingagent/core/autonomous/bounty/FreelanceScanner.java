package com.livingagent.core.autonomous.bounty;

import java.util.List;

public interface FreelanceScanner {

    List<BountyHunterSkill.Opportunity> scan(List<String> keywords, int maxBudget);

    default List<BountyHunterSkill.Opportunity> scanUpwork(List<String> keywords, int maxBudget) {
        return List.of();
    }

    default List<BountyHunterSkill.Opportunity> scanFiverr(List<String> keywords, int maxBudget) {
        return List.of();
    }
}
