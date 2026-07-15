package com.livingagent.core.evolution.orchestrator;

import com.livingagent.core.diagnosis.HealthIssue;
import com.livingagent.core.evolution.signal.EvolutionSignal;

/**
 * P31-A: 跨闭环协同治理接口。
 */
public interface SelfGovernanceOrchestrator {

    void submitEvent(CrossLoopEvent event);

    GovernanceReport orchestrate();

    GovernanceStatus getStatus();
    
    // EventListener 方法声明（解决 JDK 代理无法调用的问题）
    void onCrossLoopEvent(CrossLoopEvent event);
    
    void onHealthIssue(HealthIssue issue);
    
    void onEvolutionSignal(EvolutionSignal signal);
}
