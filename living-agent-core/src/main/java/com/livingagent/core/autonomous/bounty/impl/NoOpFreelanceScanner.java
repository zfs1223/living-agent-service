package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.FreelanceScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * NoOp implementation of FreelanceScanner.
 * Returns empty list when freelance scanning is disabled.
 */
public class NoOpFreelanceScanner implements FreelanceScanner {

    private static final Logger log = LoggerFactory.getLogger(NoOpFreelanceScanner.class);

    @Override
    public List<BountyHunterSkill.Opportunity> scan(List<String> keywords, int maxBudget) {
        log.debug("Freelance scanning disabled, returning empty opportunities");
        return List.of();
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scanUpwork(List<String> keywords, int maxBudget) {
        return List.of();
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scanFiverr(List<String> keywords, int maxBudget) {
        return List.of();
    }
}