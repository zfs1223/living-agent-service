package com.livingagent.core.autonomous.bounty.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class CreditEconomyMonitor {

    private static final Logger log = LoggerFactory.getLogger(CreditEconomyMonitor.class);
    private static final double INFLATION_THRESHOLD = 2.0;
    private static final double DEFLATION_THRESHOLD = 0.3;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalEarned = new LongAdder();
    private final LongAdder totalSpent = new LongAdder();
    private final LongAdder totalExchanged = new LongAdder();

    public CreditEconomyMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordEarn(String userId, long amount) {
        totalEarned.add(amount);
    }

    public void recordSpend(String userId, long amount) {
        totalSpent.add(amount);
    }

    public void recordExchange(String userId, long amount) {
        totalExchanged.add(amount);
    }

    public CreditEconomyReport getReport() {
        long earned = totalEarned.sum();
        long spent = totalSpent.sum();
        long exchanged = totalExchanged.sum();
        double velocity = earned > 0 ? (double) spent / earned : 0;

        if (velocity > INFLATION_THRESHOLD) {
            log.warn("[闭环54] 积分通胀: velocity={:.2f} > {:.1f}", velocity, INFLATION_THRESHOLD);
            eventBus.publish(54, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Credit inflation detected: velocity %.2f exceeds %.1f", velocity, INFLATION_THRESHOLD)));
        } else if (velocity < DEFLATION_THRESHOLD && earned > 100) {
            log.warn("[闭环54] 积分通缩: velocity={:.2f} < {:.1f}", velocity, DEFLATION_THRESHOLD);
            eventBus.publish(54, "improvement_opportunity", CrossLoopEvent.EventPriority.ECONOMY,
                Map.of("content", String.format("Credit deflation: velocity %.2f below %.1f, consider adjusting rewards", velocity, DEFLATION_THRESHOLD)));
        }

        return new CreditEconomyReport(earned, spent, exchanged, velocity);
    }

    public record CreditEconomyReport(long totalEarned, long totalSpent, long totalExchanged,
                                       double velocity) {}
}
