package com.livingagent.core.autonomous.bounty;

import java.util.List;

public interface GitHubScanner {

    List<BountyHunterSkill.Opportunity> scan(List<String> searchQueries, int maxBudget);

    default List<BountyHunterSkill.Opportunity> scanBounties(String owner, String repo) {
        return List.of();
    }

    default boolean claimIssue(String issueUrl) {
        return false;
    }
}
