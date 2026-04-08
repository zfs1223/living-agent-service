package com.livingagent.core.workflow;

public interface PhaseHandler {

    void execute(WorkflowContext context);

    String getName();

    String getDescription();
}
