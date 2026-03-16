package com.livingagent.core.tool.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RobotsChecker {

    private static final Logger log = LoggerFactory.getLogger(RobotsChecker.class);

    private final HttpClient httpClient;
    private final String userAgent;
    private final Map<String, RobotsRule> cachedRules = new ConcurrentHashMap<>();

    public RobotsChecker(String userAgent) {
        this.userAgent = userAgent != null ? userAgent : "LivingAgent";
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public boolean isAllowed(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getScheme() + "://" + uri.getHost() + 
                (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            
            RobotsRule rule = cachedRules.computeIfAbsent(host, this::fetchRobotsTxt);
            
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            
            return rule.isAllowed(path, userAgent);
            
        } catch (Exception e) {
            log.warn("Failed to check robots.txt for {}: {}", url, e.getMessage());
            return true;
        }
    }

    private RobotsRule fetchRobotsTxt(String host) {
        String robotsUrl = host + "/robots.txt";
        log.debug("Fetching robots.txt from {}", robotsUrl);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(robotsUrl))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseRobotsTxt(response.body());
            } else {
                log.debug("No robots.txt found at {} (status: {})", robotsUrl, response.statusCode());
                return new RobotsRule();
            }
            
        } catch (Exception e) {
            log.debug("Failed to fetch robots.txt from {}: {}", robotsUrl, e.getMessage());
            return new RobotsRule();
        }
    }

    private RobotsRule parseRobotsTxt(String content) {
        RobotsRule rule = new RobotsRule();
        
        String[] lines = content.split("\n");
        String currentUserAgent = "*";
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                continue;
            }
            
            String directive = line.substring(0, colonIndex).trim().toLowerCase();
            String value = line.substring(colonIndex + 1).trim();
            
            switch (directive) {
                case "user-agent":
                    currentUserAgent = value;
                    break;
                case "disallow":
                    if (!value.isEmpty()) {
                        rule.addDisallow(currentUserAgent, value);
                    }
                    break;
                case "allow":
                    if (!value.isEmpty()) {
                        rule.addAllow(currentUserAgent, value);
                    }
                    break;
                case "crawl-delay":
                    try {
                        rule.setCrawlDelay(currentUserAgent, Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                    }
                    break;
            }
        }
        
        return rule;
    }

    public void clearCache() {
        cachedRules.clear();
    }

    private static class RobotsRule {
        private final Map<String, java.util.List<String>> disallowRules = new ConcurrentHashMap<>();
        private final Map<String, java.util.List<String>> allowRules = new ConcurrentHashMap<>();
        private final Map<String, Integer> crawlDelays = new ConcurrentHashMap<>();

        void addDisallow(String userAgent, String path) {
            disallowRules.computeIfAbsent(userAgent, k -> new java.util.ArrayList<>()).add(path);
        }

        void addAllow(String userAgent, String path) {
            allowRules.computeIfAbsent(userAgent, k -> new java.util.ArrayList<>()).add(path);
        }

        void setCrawlDelay(String userAgent, int seconds) {
            crawlDelays.put(userAgent, seconds);
        }

        boolean isAllowed(String path, String userAgent) {
            java.util.List<String> disallows = disallowRules.get(userAgent);
            if (disallows == null) {
                disallows = disallowRules.get("*");
            }
            
            java.util.List<String> allows = allowRules.get(userAgent);
            if (allows == null) {
                allows = allowRules.get("*");
            }
            
            if (allows != null) {
                for (String allow : allows) {
                    if (pathMatches(path, allow)) {
                        return true;
                    }
                }
            }
            
            if (disallows != null) {
                for (String disallow : disallows) {
                    if (pathMatches(path, disallow)) {
                        return false;
                    }
                }
            }
            
            return true;
        }

        private boolean pathMatches(String path, String pattern) {
            if (pattern.equals("/")) {
                return true;
            }
            
            if (pattern.endsWith("*")) {
                return path.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            
            if (pattern.endsWith("$")) {
                return path.equals(pattern.substring(0, pattern.length() - 1));
            }
            
            return path.startsWith(pattern);
        }
    }
}
