package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.BountyHunterSkill.OpportunityType;
import com.livingagent.core.autonomous.bounty.BugBountyScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BugBountyScannerImpl implements BugBountyScanner {

    private static final Logger log = LoggerFactory.getLogger(BugBountyScannerImpl.class);

    @Override
    public List<BountyHunterSkill.Opportunity> scan(List<String> platforms, int maxBudget) {
        log.info("Scanning bug bounty platforms: {}", platforms);
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        for (String platform : platforms) {
            switch (platform.toLowerCase()) {
                case "hackerone" -> opportunities.addAll(scanHackerOne(maxBudget));
                case "bugcrowd" -> opportunities.addAll(scanBugcrowd(maxBudget));
                default -> log.warn("Unknown platform: {}", platform);
            }
        }

        log.info("Found {} bug bounty opportunities", opportunities.size());
        return opportunities;
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scanHackerOne(int maxBudget) {
        log.info("Scanning HackerOne programs");
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        opportunities.add(createBugBountyOpportunity(
            "HackerOne: XSS vulnerability",
            "Find XSS vulnerabilities in target application",
            100000,
            "hackerone",
            "h1-xss-" + UUID.randomUUID().toString().substring(0, 8),
            "https://hackerone.com/example-program"
        ));

        opportunities.add(createBugBountyOpportunity(
            "HackerOne: Authentication bypass",
            "Find authentication bypass vulnerabilities",
            200000,
            "hackerone",
            "h1-auth-" + UUID.randomUUID().toString().substring(0, 8),
            "https://hackerone.com/example-program"
        ));

        return opportunities;
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scanBugcrowd(int maxBudget) {
        log.info("Scanning Bugcrowd programs");
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        opportunities.add(createBugBountyOpportunity(
            "Bugcrowd: IDOR vulnerability",
            "Find IDOR vulnerabilities in API endpoints",
            50000,
            "bugcrowd",
            "bc-idor-" + UUID.randomUUID().toString().substring(0, 8),
            "https://bugcrowd.com/example-program"
        ));

        return opportunities;
    }

    private BountyHunterSkill.Opportunity createBugBountyOpportunity(
            String title, String description, int payoutCents,
            String sourceType, String sourceId, String url) {
        return new BountyHunterSkill.Opportunity(
            "opp-" + UUID.randomUUID().toString().substring(0, 8),
            title,
            description,
            OpportunityType.BUG_BOUNTY,
            sourceType,
            sourceId,
            url,
            payoutCents,
            "USD",
            Instant.now().plusSeconds(30 * 24 * 60 * 60),
            "high",
            Map.of("scanner", "bugbounty", "platform", sourceType)
        );
    }
}
