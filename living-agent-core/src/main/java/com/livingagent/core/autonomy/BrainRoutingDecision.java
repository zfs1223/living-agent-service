package com.livingagent.core.autonomy;

import java.util.List;

public record BrainRoutingDecision(
    String primaryDepartment,
    String primaryBrainId,
    List<String> supportingDepartments,
    String routeReason,
    boolean reroutedFromRequestedDepartment
) {
}
