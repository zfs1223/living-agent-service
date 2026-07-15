package com.livingagent.core.autonomous.bounty.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Component
public class CreditEconomyMonitor {

    private static final Logger log = LoggerFactory.getLogger(CreditEconomyMonitor.class);
    private static final double INFLATION_THRESHOLD = 2.0;
    private static final double DEFLATION_THRESHOLD = 0.3;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalEarned = new LongAdder();
    private final LongAdder totalSpent = new LongAdder();
    private final LongAdder totalExchanged = new LongAdder();
    private volatile double exchangeRateMultiplier = 1.0;

    public CreditEconomyMonitor(@Autowired(required = false) CrossLoopEventBus eventBus) {
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
            log.warn("[闭环54] 积分通胀: velocity={} > {}",
                String.format("%.2f", velocity), String.format("%.1f", INFLATION_THRESHOLD));
            if (eventBus != null) {
                eventBus.publish(54, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("content", String.format("Credit inflation detected: velocity %.2f exceeds %.1f", velocity, INFLATION_THRESHOLD)));
            }
        } else if (velocity < DEFLATION_THRESHOLD && earned > 100) {
            log.warn("[闭环54] 积分通缩: velocity={} < {}",
                String.format("%.2f", velocity), String.format("%.1f", DEFLATION_THRESHOLD));
            if (eventBus != null) {
                eventBus.publish(54, "improvement_opportunity", CrossLoopEvent.EventPriority.ECONOMY,
                    Map.of("content", String.format("Credit deflation: velocity %.2f below %.1f, consider adjusting rewards", velocity, DEFLATION_THRESHOLD)));
            }
        }

        return new CreditEconomyReport(earned, spent, exchanged, velocity);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void checkAndAdjustExchangeRate() {
        long earned = totalEarned.sum();
        if (earned < 100) return;
        double velocity = (double) totalSpent.sum() / earned;

        if (velocity > INFLATION_THRESHOLD && exchangeRateMultiplier < 1.5) {
            double old = exchangeRateMultiplier;
            exchangeRateMultiplier = Math.min(1.5, exchangeRateMultiplier + 0.1);
            log.info("[闭环54] 积分通胀，兑换倍率从{}提升至{}",
                String.format("%.2f", old), String.format("%.2f", exchangeRateMultiplier));
            if (eventBus != null) {
                eventBus.publish(54, "exchange_rate_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("exchangeRateMultiplier", exchangeRateMultiplier, "velocity", velocity), 300);
            }
        } else if (velocity < DEFLATION_THRESHOLD && exchangeRateMultiplier > 0.8) {
            double old = exchangeRateMultiplier;
            exchangeRateMultiplier = Math.max(0.8, exchangeRateMultiplier - 0.05);
            log.info("[闭环54] 积分通缩，兑换倍率从{}降至{}",
                String.format("%.2f", old), String.format("%.2f", exchangeRateMultiplier));
            if (eventBus != null) {
                eventBus.publish(54, "exchange_rate_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("exchangeRateMultiplier", exchangeRateMultiplier, "velocity", velocity), 300);
            }
        }
    }

    public double getExchangeRateMultiplier() {
        return exchangeRateMultiplier;
    }

    public record CreditEconomyReport(long totalEarned, long totalSpent, long totalExchanged,
                                       double velocity) {}
}
