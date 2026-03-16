package com.livingagent.core.autonomous.platform.impl;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.GitHubScanner;
import com.livingagent.core.autonomous.platform.PlatformIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class GitHubPlatformIntegration implements PlatformIntegration, GitHubScanner {

    private static final Logger log = LoggerFactory.getLogger(GitHubPlatformIntegration.class);

    private static final String API_BASE = "https://api.github.com";
    private static final String PLATFORM_NAME = "github";

    private final RestTemplate restTemplate;
    private final String authToken;
    private final String username;
    private boolean authenticated;

    public GitHubPlatformIntegration(
            @Value("${github.token:}") String authToken,
            @Value("${github.username:}") String username) {
        this.authToken = authToken;
        this.username = username;
        this.restTemplate = new RestTemplate();
        this.authenticated = authToken != null && !authToken.isEmpty();
        log.info("GitHubPlatformIntegration initialized (authenticated: {})", authenticated);
    }

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public boolean authenticate(AuthConfig config) {
        if (config.apiKey() != null && !config.apiKey().isEmpty()) {
            this.authenticated = true;
            log.info("GitHub authentication configured");
            return true;
        }
        return false;
    }

    @Override
    public List<Opportunity> searchOpportunities(SearchQuery query) {
        List<Opportunity> opportunities = new ArrayList<>();

        opportunities.addAll(searchGitHubIssues(query));
        opportunities.addAll(searchBounties(query));

        opportunities.sort((a, b) -> Double.compare(
            calculateOpportunityScore(b),
            calculateOpportunityScore(a)
        ));

        return opportunities.stream().limit(query.limit()).toList();
    }

    private List<Opportunity> searchGitHubIssues(SearchQuery query) {
        String url = API_BASE + "/search/issues";

        StringBuilder q = new StringBuilder("is:issue is:open");
        if (query.keywords() != null) {
            q.append(" ").append(query.keywords());
        }
        for (String label : query.labels()) {
            q.append(" label:\"").append(label).append("\"");
        }

        HttpHeaders headers = createHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");

        Map<String, String> params = Map.of(
            "q", q.toString(),
            "sort", query.sortBy(),
            "order", query.sortOrder(),
            "per_page", String.valueOf(query.limit())
        );

        String fullUrl = url + "?q=" + q.toString().replace(" ", "+") + 
            "&sort=" + query.sortBy() + "&order=" + query.sortOrder() + 
            "&per_page=" + query.limit();

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                if (items != null) {
                    return items.stream()
                        .map(this::mapIssueToOpportunity)
                        .filter(Objects::nonNull)
                        .toList();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to search GitHub issues: {}", e.getMessage());
        }

        return List.of();
    }

    private List<Opportunity> searchBounties(SearchQuery query) {
        List<Opportunity> bounties = new ArrayList<>();

        if (query.minBudget() <= 0) {
            bounties.addAll(searchBountyIssues("label:bounty"));
            bounties.addAll(searchBountyIssues("label:\"good first issue\""));
            bounties.addAll(searchBountyIssues("label:\"help wanted\""));
        }

        return bounties;
    }

    private List<Opportunity> searchBountyIssues(String labelQuery) {
        String url = API_BASE + "/search/issues?q=is:issue+is:open+" + labelQuery + "&per_page=20";

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                if (items != null) {
                    return items.stream()
                        .map(this::mapIssueToOpportunity)
                        .filter(Objects::nonNull)
                        .toList();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to search bounty issues: {}", e.getMessage());
        }

        return List.of();
    }

    @Override
    public Optional<Opportunity> getOpportunity(String opportunityId) {
        String[] parts = opportunityId.split("/");
        if (parts.length < 4) {
            return Optional.empty();
        }

        String owner = parts[1];
        String repo = parts[2];
        String issueNumber = parts[3];

        String url = API_BASE + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber;

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(mapIssueToOpportunity(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get opportunity {}: {}", opportunityId, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public boolean claimOpportunity(String opportunityId) {
        log.info("Claiming opportunity: {}", opportunityId);

        String[] parts = opportunityId.split("/");
        if (parts.length < 4) {
            return false;
        }

        String owner = parts[1];
        String repo = parts[2];
        String issueNumber = parts[3];

        String url = API_BASE + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/assignees";

        try {
            HttpHeaders headers = createHeaders();
            Map<String, Object> body = Map.of("assignees", List.of(username));
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to claim opportunity {}: {}", opportunityId, e.getMessage());
            return false;
        }
    }

    @Override
    public SubmissionResult submitWork(String opportunityId, WorkSubmission submission) {
        log.info("Submitting work for opportunity: {}", opportunityId);

        if (submission.pullRequestUrl() != null) {
            return SubmissionResult.success(opportunityId, submission.pullRequestUrl());
        }

        String[] parts = opportunityId.split("/");
        if (parts.length < 4) {
            return SubmissionResult.failure("Invalid opportunity ID format");
        }

        String owner = parts[1];
        String repo = parts[2];
        String issueNumber = parts[3];

        String url = API_BASE + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments";

        try {
            HttpHeaders headers = createHeaders();
            Map<String, Object> body = Map.of(
                "body", "## Work Submission\n\n" + submission.description() + 
                    "\n\n**Deliverables:**\n" + String.join("\n", submission.deliverables())
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String commentUrl = (String) response.getBody().get("html_url");
                return SubmissionResult.success(opportunityId, commentUrl);
            }
        } catch (Exception e) {
            log.error("Failed to submit work: {}", e.getMessage());
        }

        return SubmissionResult.failure("Failed to submit work");
    }

    @Override
    public PaymentStatus checkPayment(String opportunityId) {
        return new PaymentStatus(opportunityId, "PENDING", 0, "USD", null, null);
    }

    @Override
    public UserProfile getProfile() {
        if (!authenticated) {
            return null;
        }

        String url = API_BASE + "/user";

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return new UserProfile(
                    String.valueOf(body.get("id")),
                    (String) body.get("login"),
                    (String) body.get("name"),
                    (String) body.get("email"),
                    ((Number) body.getOrDefault("public_repos", 0)).intValue(),
                    Map.of(
                        "followers", body.getOrDefault("followers", 0),
                        "following", body.getOrDefault("following", 0)
                    )
                );
            }
        } catch (Exception e) {
            log.warn("Failed to get profile: {}", e.getMessage());
        }

        return null;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github.v3+json");
        if (authToken != null && !authToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + authToken);
        }
        return headers;
    }

    private Opportunity mapIssueToOpportunity(Map<String, Object> issue) {
        if (issue == null) return null;

        String htmlUrl = (String) issue.get("html_url");
        String[] urlParts = htmlUrl != null ? htmlUrl.split("/") : new String[0];
        String id = "github/" + (urlParts.length >= 5 ? urlParts[3] + "/" + urlParts[4] + "/" + issue.get("number") : issue.get("id"));

        List<String> labels = new ArrayList<>();
        List<Map<String, Object>> labelMaps = (List<Map<String, Object>>) issue.get("labels");
        if (labelMaps != null) {
            for (Map<String, Object> label : labelMaps) {
                labels.add((String) label.get("name"));
            }
        }

        int budget = extractBudget(labels, (String) issue.get("body"));

        return new Opportunity(
            id,
            PLATFORM_NAME,
            (String) issue.get("title"),
            (String) issue.get("body"),
            htmlUrl,
            labels.contains("bounty") ? OpportunityType.GITHUB_BOUNTY : OpportunityType.GITHUB_ISSUE,
            budget,
            "USD",
            (String) issue.get("state"),
            null,
            labels,
            List.of(),
            estimateComplexity((String) issue.get("body"), labels),
            Map.of("number", issue.get("number"))
        );
    }

    private int extractBudget(List<String> labels, String body) {
        for (String label : labels) {
            if (label.contains("$") || label.toLowerCase().contains("bounty")) {
                try {
                    String numStr = label.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        return Integer.parseInt(numStr) * 100;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (body != null) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1)) * 100;
            }
        }

        return 0;
    }

    private int estimateComplexity(String body, List<String> labels) {
        int complexity = 5;

        if (labels.contains("good first issue")) complexity = 3;
        if (labels.contains("help wanted")) complexity = 4;
        if (labels.contains("bug")) complexity = 4;
        if (labels.contains("enhancement")) complexity = 5;
        if (labels.contains("breaking change")) complexity = 8;

        if (body != null) {
            String lowerBody = body.toLowerCase();
            if (lowerBody.contains("simple") || lowerBody.contains("easy")) complexity -= 1;
            if (lowerBody.contains("complex") || lowerBody.contains("difficult")) complexity += 2;
            if (lowerBody.contains("urgent") || lowerBody.contains("critical")) complexity += 1;
        }

        return Math.max(1, Math.min(10, complexity));
    }

    private double calculateOpportunityScore(Opportunity opp) {
        double score = 0;
        score += opp.budgetCents() / 10000.0;
        score += (10 - opp.complexity()) * 5;
        if (opp.labels().contains("bounty")) score += 20;
        if (opp.labels().contains("help wanted")) score += 10;
        return score;
    }

    @Override
    public List<BountyHunterSkill.Opportunity> scan(List<String> searchQueries, int maxBudget) {
        List<BountyHunterSkill.Opportunity> results = new ArrayList<>();
        
        for (String query : searchQueries) {
            SearchQuery sq = new SearchQuery(
                query, List.of(), 0, maxBudget, "created", "desc", 50, Map.of()
            );
            List<Opportunity> opportunities = searchOpportunities(sq);
            
            for (Opportunity opp : opportunities) {
                results.add(new BountyHunterSkill.Opportunity(
                    opp.id(),
                    opp.title(),
                    opp.description(),
                    BountyHunterSkill.OpportunityType.valueOf(opp.type().name()),
                    opp.platform(),
                    opp.id(),
                    opp.url(),
                    opp.budgetCents(),
                    opp.currency(),
                    opp.deadline(),
                    "medium",
                    opp.metadata()
                ));
            }
        }
        
        return results;
    }
}
