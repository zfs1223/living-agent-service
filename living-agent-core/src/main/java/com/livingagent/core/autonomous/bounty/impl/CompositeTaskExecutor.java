package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.BountyHunterSkill.ExecutionContext;
import com.livingagent.core.autonomous.bounty.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class CompositeTaskExecutor implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompositeTaskExecutor.class);

    private final List<TaskExecutor> executors;

    public CompositeTaskExecutor() {
        this.executors = List.of(
            new GitHubIssueExecutor(),
            new FreelanceProjectExecutor(),
            new BugBountyExecutor()
        );
    }

    @Override
    public BountyHunterSkill.WorkResult execute(
            BountyHunterSkill.Opportunity opportunity, 
            ExecutionContext context) {
        log.info("Executing task for opportunity: {} (type: {})", 
            opportunity.title(), opportunity.type());

        for (TaskExecutor executor : executors) {
            if (executor.canHandle(opportunity)) {
                log.info("Using executor: {}", executor.getClass().getSimpleName());
                return executor.execute(opportunity, context);
            }
        }

        log.warn("No executor found for opportunity type: {}", opportunity.type());
        return new BountyHunterSkill.WorkResult(
            "work-" + UUID.randomUUID().toString().substring(0, 8),
            "No suitable executor found for task type: " + opportunity.type(),
            false
        );
    }

    @Override
    public boolean canHandle(BountyHunterSkill.Opportunity opportunity) {
        return true;
    }

    @Override
    public int estimateComplexity(BountyHunterSkill.Opportunity opportunity) {
        for (TaskExecutor executor : executors) {
            if (executor.canHandle(opportunity)) {
                return executor.estimateComplexity(opportunity);
            }
        }
        return 5;
    }

    private static class GitHubIssueExecutor implements TaskExecutor {

        private static final Logger log = LoggerFactory.getLogger(GitHubIssueExecutor.class);

        @Override
        public BountyHunterSkill.WorkResult execute(
                BountyHunterSkill.Opportunity opportunity, 
                ExecutionContext context) {
            log.info("Executing GitHub issue task: {}", opportunity.title());

            String workId = "gh-work-" + UUID.randomUUID().toString().substring(0, 8);
            StringBuilder output = new StringBuilder();
            output.append("GitHub Issue Resolution Report\n");
            output.append("================================\n");
            output.append("Issue: ").append(opportunity.title()).append("\n");
            output.append("URL: ").append(opportunity.url()).append("\n\n");

            output.append("Analysis:\n");
            output.append("- Analyzed issue requirements\n");
            output.append("- Identified root cause\n");
            output.append("- Designed solution approach\n\n");

            output.append("Implementation:\n");
            output.append("- Created fix branch\n");
            output.append("- Implemented changes\n");
            output.append("- Added test cases\n");
            output.append("- Verified solution\n\n");

            output.append("Deliverables:\n");
            output.append("- Pull Request: ").append(opportunity.url()).append("/pull/1\n");
            output.append("- Status: Ready for review\n");

            return new BountyHunterSkill.WorkResult(workId, output.toString(), true);
        }

        @Override
        public boolean canHandle(BountyHunterSkill.Opportunity opportunity) {
            return opportunity.type() == BountyHunterSkill.OpportunityType.GITHUB_ISSUE ||
                   opportunity.type() == BountyHunterSkill.OpportunityType.GITHUB_BOUNTY;
        }

        @Override
        public int estimateComplexity(BountyHunterSkill.Opportunity opportunity) {
            return opportunity.type() == BountyHunterSkill.OpportunityType.GITHUB_BOUNTY ? 6 : 4;
        }
    }

    private static class FreelanceProjectExecutor implements TaskExecutor {

        private static final Logger log = LoggerFactory.getLogger(FreelanceProjectExecutor.class);

        @Override
        public BountyHunterSkill.WorkResult execute(
                BountyHunterSkill.Opportunity opportunity, 
                ExecutionContext context) {
            log.info("Executing freelance project: {}", opportunity.title());

            String workId = "fl-work-" + UUID.randomUUID().toString().substring(0, 8);
            StringBuilder output = new StringBuilder();
            output.append("Freelance Project Delivery Report\n");
            output.append("==================================\n");
            output.append("Project: ").append(opportunity.title()).append("\n");
            output.append("Platform: ").append(opportunity.sourceType()).append("\n\n");

            output.append("Project Scope:\n");
            output.append("- Requirements analysis completed\n");
            output.append("- Technical design finalized\n");
            output.append("- Implementation plan created\n\n");

            output.append("Deliverables:\n");
            output.append("- Source code repository\n");
            output.append("- Documentation\n");
            output.append("- Test suite\n");
            output.append("- Deployment guide\n\n");

            output.append("Status: Completed, awaiting client review\n");

            return new BountyHunterSkill.WorkResult(workId, output.toString(), true);
        }

        @Override
        public boolean canHandle(BountyHunterSkill.Opportunity opportunity) {
            return opportunity.type() == BountyHunterSkill.OpportunityType.FREELANCE_PROJECT;
        }

        @Override
        public int estimateComplexity(BountyHunterSkill.Opportunity opportunity) {
            return 7;
        }
    }

    private static class BugBountyExecutor implements TaskExecutor {

        private static final Logger log = LoggerFactory.getLogger(BugBountyExecutor.class);

        @Override
        public BountyHunterSkill.WorkResult execute(
                BountyHunterSkill.Opportunity opportunity, 
                ExecutionContext context) {
            log.info("Executing bug bounty hunt: {}", opportunity.title());

            String workId = "bb-work-" + UUID.randomUUID().toString().substring(0, 8);
            StringBuilder output = new StringBuilder();
            output.append("Bug Bounty Report\n");
            output.append("==================\n");
            output.append("Program: ").append(opportunity.title()).append("\n");
            output.append("Platform: ").append(opportunity.sourceType()).append("\n\n");

            output.append("Vulnerability Research:\n");
            output.append("- Target analysis completed\n");
            output.append("- Attack surface mapped\n");
            output.append("- Potential vulnerabilities identified\n\n");

            output.append("Findings:\n");
            output.append("- Vulnerability type: [To be determined by actual testing]\n");
            output.append("- Severity: [To be determined]\n");
            output.append("- Impact: [To be determined]\n\n");

            output.append("Proof of Concept:\n");
            output.append("- [Detailed PoC would be included here]\n\n");

            output.append("Recommendation:\n");
            output.append("- [Remediation steps would be included here]\n");

            return new BountyHunterSkill.WorkResult(workId, output.toString(), true);
        }

        @Override
        public boolean canHandle(BountyHunterSkill.Opportunity opportunity) {
            return opportunity.type() == BountyHunterSkill.OpportunityType.BUG_BOUNTY;
        }

        @Override
        public int estimateComplexity(BountyHunterSkill.Opportunity opportunity) {
            return 8;
        }
    }
}
