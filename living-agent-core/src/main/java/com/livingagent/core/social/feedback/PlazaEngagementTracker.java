package com.livingagent.core.social.feedback;

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
public class PlazaEngagementTracker {

    private static final Logger log = LoggerFactory.getLogger(PlazaEngagementTracker.class);
    private static final double DEFAULT_ENGAGEMENT_THRESHOLD = 0.15;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalPosts = new LongAdder();
    private final LongAdder totalLikes = new LongAdder();
    private final LongAdder totalComments = new LongAdder();
    private final LongAdder totalShares = new LongAdder();
    private volatile double engagementThreshold = DEFAULT_ENGAGEMENT_THRESHOLD;
    private volatile double recommendationBoost = 1.0;

    public PlazaEngagementTracker(@Autowired(required = false) CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordPost(String postId) {
        totalPosts.increment();
    }

    public void recordLike(String postId) {
        totalLikes.increment();
    }

    public void recordComment(String postId) {
        totalComments.increment();
    }

    public void recordShare(String postId) {
        totalShares.increment();
    }

    public PlazaEngagementReport getReport() {
        long posts = totalPosts.sum();
        long interactions = totalLikes.sum() + totalComments.sum() + totalShares.sum();
        double engagementRate = posts > 0 ? (double) interactions / posts : 0;

        if (posts > 20 && engagementRate < engagementThreshold) {
            log.warn("[闭环55] 广场活跃度低: engagement={} < {}",
                String.format("%.2f", engagementRate), String.format("%.2f", engagementThreshold));
            if (eventBus != null) {
                eventBus.publish(55, "improvement_opportunity", CrossLoopEvent.EventPriority.ECONOMY,
                    Map.of("content", String.format("Plaza engagement rate %.2f below %.2f, suggest optimizing recommendation algorithm", engagementRate, engagementThreshold)));
            }
        }

        return new PlazaEngagementReport(posts, totalLikes.sum(), totalComments.sum(), totalShares.sum(), engagementRate);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void checkAndAdjustEngagementStrategy() {
        long posts = totalPosts.sum();
        if (posts < 20) return;
        long interactions = totalLikes.sum() + totalComments.sum() + totalShares.sum();
        double engagementRate = (double) interactions / posts;

        if (engagementRate < engagementThreshold && recommendationBoost < 2.0) {
            double old = recommendationBoost;
            recommendationBoost = Math.min(2.0, recommendationBoost + 0.1);
            log.info("[闭环55] 活跃度{}低于阈值{}，推荐权重从{}提升至{}",
                String.format("%.2f", engagementRate), String.format("%.2f", engagementThreshold),
                String.format("%.1f", old), String.format("%.1f", recommendationBoost));
            if (eventBus != null) {
                eventBus.publish(55, "recommendation_boost_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("recommendationBoost", recommendationBoost, "engagementRate", engagementRate), 300);
            }
        } else if (engagementRate > 0.30 && recommendationBoost > 1.0) {
            recommendationBoost = Math.max(1.0, recommendationBoost - 0.05);
        }
    }

    public double getRecommendationBoost() {
        return recommendationBoost;
    }

    public double getEngagementThreshold() {
        return engagementThreshold;
    }

    public record PlazaEngagementReport(long totalPosts, long totalLikes, long totalComments,
                                         long totalShares, double engagementRate) {}
}
