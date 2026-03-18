package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.BountyHunterSkill.OpportunityType;
import com.livingagent.core.autonomous.bounty.GitHubScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GitHubScannerImpl implements GitHubScanner {

    private static final Logger log = LoggerFactory.getLogger(GitHubScannerImpl.class);

    @Override
    public List<BountyHunterSkill.Opportunity> scan(List<String> searchQueries, int maxBudget) {
        log.info("Scanning GitHub for bounty opportunities with queries: {}", searchQueries);
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        for (String query : searchQueries) {
            List<BountyHunterSkill.Opportunity> queryResults = scanQuery(query, maxBudget);
            opportunities.addAll(queryResults);
        }

        log.info("Found {} GitHub opportunities", opportunities.size());
        return opportunities;
    }

    private List<BountyHunterSkill.Opportunity> scanQuery(String query, int maxBudget) {
        List<BountyHunterSkill.Opportunity> results = new ArrayList<>();

        results.add(createMockOpportunity(
            "GitHub Issue: " + query,
            "Fix bug in authentication module related to " + query,
            OpportunityType.GITHUB_ISSUE,
            5000,
            "github",
            "issue-" + UUID.randomUUID().toString().substring(0, 8),
            "https://github.com/example/repo/issues/1"
        ));

        results.add(createMockOpportunity(
            "GitHub Bounty: " + query,
            "Implement new feature for " + query,
            OpportunityType.GITHUB_BOUNTY,
            15000,
            "github",
            "bounty-" + UUID.randomUUID().toString().substring(0, 8),
            "https://github.com/example/repo/issues/2"
        ));

        return results;
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scanBounties(String owner, String repo) {
        log.info("Scanning bounties for {}/{}", owner, repo);
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        opportunities.add(createMockOpportunity(
            "Bounty in " + owner + "/" + repo,
            "Contribute to " + repo + " repository",
            OpportunityType.GITHUB_BOUNTY,
            20000,
            "github",
            owner + "-" + repo + "-bounty",
            "https://github.com/" + owner + "/" + repo + "/issues"
        ));

        return opportunities;
    }

    @Override
    public boolean claimIssue(String issueUrl) {
        log.info("Claiming GitHub issue: {}", issueUrl);
        return true;
    }

    private BountyHunterSkill.Opportunity createMockOpportunity(
            String title, String description, OpportunityType type,
            int payoutCents, String sourceType, String sourceId, String url) {
        return new BountyHunterSkill.Opportunity(
            "opp-" + UUID.randomUUID().toString().substring(0, 8),
            title,
            description,
            type,
            sourceType,
            sourceId,
            url,
            payoutCents,
            "USD",
            Instant.now().plusSeconds(7 * 24 * 60 * 60),
            "medium",
            Map.of("scanner", "github")
        );
    }
}
