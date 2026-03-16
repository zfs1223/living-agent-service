package com.livingagent.core.autonomous.bounty;

import java.util.List;

public interface BugBountyScanner {

    List<BountyHunterSkill.Opportunity> scan(List<String> platforms, int maxBudget);

    default List<BountyHunterSkill.Opportunity> scanHackerOne(int maxBudget) {
        return List.of();
    }

    default List<BountyHunterSkill.Opportunity> scanBugcrowd(int maxBudget) {
        return List.of();
    }
}
