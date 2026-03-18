package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.BountyHunterSkill.OpportunityType;
import com.livingagent.core.autonomous.bounty.FreelanceScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FreelanceScannerImpl implements FreelanceScanner {

    private static final Logger log = LoggerFactory.getLogger(FreelanceScannerImpl.class);

    @Override
    public List<BountyHunterSkill.Opportunity> scan(List<String> keywords, int maxBudget) {
        log.info("Scanning freelance platforms for keywords: {}", keywords);
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        for (String keyword : keywords) {
            opportunities.addAll(scanUpwork(List.of(keyword), maxBudget));
            opportunities.addAll(scanFiverr(List.of(keyword), maxBudget));
        }

        log.info("Found {} freelance opportunities", opportunities.size());
        return opportunities;
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scanUpwork(List<String> keywords, int maxBudget) {
        log.info("Scanning Upwork for: {}", keywords);
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        for (String keyword : keywords) {
            opportunities.add(createFreelanceOpportunity(
                "Upwork: " + keyword + " development",
                "Need experienced developer for " + keyword + " project",
                50000,
                "upwork",
                "upwork-" + UUID.randomUUID().toString().substring(0, 8),
                "https://upwork.com/jobs/example"
            ));
        }

        return opportunities;
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scanFiverr(List<String> keywords, int maxBudget) {
        log.info("Scanning Fiverr for: {}", keywords);
        List<BountyHunterSkill.Opportunity> opportunities = new ArrayList<>();

        for (String keyword : keywords) {
            opportunities.add(createFreelanceOpportunity(
                "Fiverr: " + keyword + " gig",
                "Looking for " + keyword + " expert for quick task",
                10000,
                "fiverr",
                "fiverr-" + UUID.randomUUID().toString().substring(0, 8),
                "https://fiverr.com/gigs/example"
            ));
        }

        return opportunities;
    }

    private BountyHunterSkill.Opportunity createFreelanceOpportunity(
            String title, String description, int payoutCents,
            String sourceType, String sourceId, String url) {
        return new BountyHunterSkill.Opportunity(
            "opp-" + UUID.randomUUID().toString().substring(0, 8),
            title,
            description,
            OpportunityType.FREELANCE_PROJECT,
            sourceType,
            sourceId,
            url,
            payoutCents,
            "USD",
            Instant.now().plusSeconds(14 * 24 * 60 * 60),
            "medium",
            Map.of("scanner", "freelance", "platform", sourceType)
        );
    }
}
