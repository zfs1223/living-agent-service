package com.livingagent.core.social.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class PlazaEngagementTracker {

    private static final Logger log = LoggerFactory.getLogger(PlazaEngagementTracker.class);
    private static final double LOW_ENGAGEMENT_THRESHOLD = 0.15;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalPosts = new LongAdder();
    private final LongAdder totalLikes = new LongAdder();
    private final LongAdder totalComments = new LongAdder();
    private final LongAdder totalShares = new LongAdder();

    public PlazaEngagementTracker(CrossLoopEventBus eventBus) {
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

        if (posts > 20 && engagementRate < LOW_ENGAGEMENT_THRESHOLD) {
            log.warn("[闭环55] 广场活跃度低: engagement={:.2f} < {:.2f}", engagementRate, LOW_ENGAGEMENT_THRESHOLD);
            eventBus.publish(55, "improvement_opportunity", CrossLoopEvent.EventPriority.ECONOMY,
                Map.of("content", String.format("Plaza engagement rate %.2f below %.2f, suggest optimizing recommendation algorithm", engagementRate, LOW_ENGAGEMENT_THRESHOLD)));
        }

        return new PlazaEngagementReport(posts, totalLikes.sum(), totalComments.sum(), totalShares.sum(), engagementRate);
    }

    public record PlazaEngagementReport(long totalPosts, long totalLikes, long totalComments,
                                         long totalShares, double engagementRate) {}
}
