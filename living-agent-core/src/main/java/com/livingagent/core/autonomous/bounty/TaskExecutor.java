package com.livingagent.core.autonomous.bounty;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill.Opportunity;
import com.livingagent.core.autonomous.bounty.BountyHunterSkill.WorkResult;
import com.livingagent.core.autonomous.bounty.BountyHunterSkill.ExecutionContext;

public interface TaskExecutor {

    WorkResult execute(Opportunity opportunity, ExecutionContext context);

    boolean canHandle(Opportunity opportunity);

    default int estimateComplexity(Opportunity opportunity) {
        return 5;
    }
}
