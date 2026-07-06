package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.BugBountyScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * NoOp implementation of BugBountyScanner.
 * Returns empty list when bug bounty scanning is disabled.
 */
public class NoOpBugBountyScanner implements BugBountyScanner {

    private static final Logger log = LoggerFactory.getLogger(NoOpBugBountyScanner.class);

    @Override
    public List<BountyHunterSkill.Opportunity> scan(List<String> keywords, int maxBudget) {
        log.debug("Bug bounty scanning disabled, returning empty opportunities");
        return List.of();
    }
}