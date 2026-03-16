package com.livingagent.core.autonomous.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PlatformIntegration {

    String getPlatformName();

    boolean isAuthenticated();

    boolean authenticate(AuthConfig config);

    List<Opportunity> searchOpportunities(SearchQuery query);

    Optional<Opportunity> getOpportunity(String opportunityId);

    boolean claimOpportunity(String opportunityId);

    SubmissionResult submitWork(String opportunityId, WorkSubmission submission);

    PaymentStatus checkPayment(String opportunityId);

    UserProfile getProfile();

    record AuthConfig(
        String apiKey,
        String apiSecret,
        String accessToken,
        String refreshToken,
        Map<String, Object> additionalParams
    ) {
        public static AuthConfig withToken(String token) {
            return new AuthConfig(null, null, token, null, Map.of());
        }

        public static AuthConfig withKey(String key, String secret) {
            return new AuthConfig(key, secret, null, null, Map.of());
        }
    }

    record SearchQuery(
        String keywords,
        List<String> labels,
        int minBudget,
        int maxBudget,
        String sortBy,
        String sortOrder,
        int limit,
        Map<String, Object> filters
    ) {
        public static SearchQuery defaults() {
            return new SearchQuery(null, List.of(), 0, Integer.MAX_VALUE, "created", "desc", 50, Map.of());
        }

        public static SearchQuery withKeywords(String keywords) {
            return new SearchQuery(keywords, List.of(), 0, Integer.MAX_VALUE, "created", "desc", 50, Map.of());
        }

        public static SearchQuery withBudget(int min, int max) {
            return new SearchQuery(null, List.of(), min, max, "created", "desc", 50, Map.of());
        }
    }

    record Opportunity(
        String id,
        String platform,
        String title,
        String description,
        String url,
        OpportunityType type,
        int budgetCents,
        String currency,
        String status,
        Instant deadline,
        List<String> labels,
        List<String> requiredSkills,
        int complexity,
        Map<String, Object> metadata
    ) {}

    enum OpportunityType {
        GITHUB_ISSUE,
        GITHUB_BOUNTY,
        UPWORK_JOB,
        FIVERR_GIG,
        HACKERONE_BUG,
        BUGCROWD_BUG,
        INTERNAL_TASK
    }

    record WorkSubmission(
        String submissionId,
        String title,
        String description,
        List<String> deliverables,
        Map<String, Object> artifacts,
        String commitSha,
        String pullRequestUrl
    ) {
        public static WorkSubmission simple(String description, String deliverable) {
            return new WorkSubmission(null, null, description, List.of(deliverable), Map.of(), null, null);
        }

        public static WorkSubmission pullRequest(String description, String prUrl) {
            return new WorkSubmission(null, null, description, List.of(), Map.of(), null, prUrl);
        }
    }

    record SubmissionResult(
        String submissionId,
        boolean success,
        String message,
        String reviewUrl,
        Instant submittedAt
    ) {
        public static SubmissionResult success(String id, String url) {
            return new SubmissionResult(id, true, "Submitted successfully", url, Instant.now());
        }

        public static SubmissionResult failure(String reason) {
            return new SubmissionResult(null, false, reason, null, Instant.now());
        }
    }

    record PaymentStatus(
        String opportunityId,
        String status,
        int amountCents,
        String currency,
        Instant paidAt,
        String transactionId
    ) {}

    record UserProfile(
        String platformId,
        String username,
        String displayName,
        String email,
        int reputation,
        Map<String, Object> stats
    ) {}
}
