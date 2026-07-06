package com.livingagent.core.evolution.orchestrator;

import com.livingagent.core.diagnosis.HealthIssue;

import java.util.List;

public interface SelfHealingOrchestrator {

    SelfHealingResult orchestrate(HealthIssue issue);

    List<SelfHealingResult> getRecentResults(int limit);

    boolean isEnabled();
    
    // EventListener 方法声明（解决 JDK 代理无法调用的问题）
    void onHealthIssue(HealthIssue issue);
}
