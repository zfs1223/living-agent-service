package com.livingagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class Crawl4aiClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Crawl4aiClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final WebCrawlerTool.CrawlerConfig config;

    public Crawl4aiClient(WebCrawlerTool.CrawlerConfig config) {
        this.config = config;
        this.baseUrl = config.getCrawl4aiUrl();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public WebCrawlerTool.CrawlResult crawl(WebCrawlerTool.CrawlRequest request) {
        log.info("Crawling URL: {}", request.getUrl());
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = buildRequestBody(request);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/crawl"))
                .header("Content-Type", "application/json")
                .header("User-Agent", request.getUserAgent() != null ? request.getUserAgent() : "LivingAgent/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Crawl4ai returned status " + response.statusCode() + ": " + response.body());
            }

            WebCrawlerTool.CrawlResult result = parseResponse(response.body());
            result.setCrawlTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("Crawled {} in {}ms", request.getUrl(), result.getCrawlTimeMs());
            return result;

        } catch (Exception e) {
            log.error("Failed to crawl {}: {}", request.getUrl(), e.getMessage());
            throw new RuntimeException("Crawl failed: " + e.getMessage(), e);
        }
    }

    public List<WebCrawlerTool.CrawlResult> crawlBatch(List<WebCrawlerTool.CrawlRequest> requests) {
        log.info("Batch crawling {} URLs", requests.size());
        List<WebCrawlerTool.CrawlResult> results = new ArrayList<>();
        
        for (WebCrawlerTool.CrawlRequest request : requests) {
            try {
                results.add(crawl(request));
            } catch (Exception e) {
                log.warn("Failed to crawl {}: {}", request.getUrl(), e.getMessage());
            }
        }
        
        return results;
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Crawl4ai health check failed: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildRequestBody(WebCrawlerTool.CrawlRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", request.getUrl());
        
        if (request.getExtractType() != null) {
            body.put("extract_type", request.getExtractType());
        }
        
        if (request.getSelector() != null) {
            body.put("css_selector", request.getSelector());
        }
        
        if (request.getJsScript() != null) {
            body.put("js_code", request.getJsScript());
        }
        
        if (request.getWaitTime() > 0) {
            body.put("wait_for", request.getWaitTime());
        }
        
        if (request.getUserAgent() != null) {
            body.put("user_agent", request.getUserAgent());
        }
        
        if (request.getProxy() != null) {
            body.put("proxy", request.getProxy());
        }
        
        if (request.isDeepCrawl()) {
            Map<String, Object> deepCrawlConfig = new LinkedHashMap<>();
            deepCrawlConfig.put("max_pages", request.getMaxPages());
            deepCrawlConfig.put("strategy", "bfs");
            body.put("deep_crawl", deepCrawlConfig);
        }
        
        if (request.getExtractType() != null) {
            Map<String, Object> extractionConfig = new LinkedHashMap<>();
            extractionConfig.put("type", request.getExtractType());
            body.put("extraction_strategy", extractionConfig);
        }
        
        return body;
    }

    private WebCrawlerTool.CrawlResult parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            
            WebCrawlerTool.CrawlResult result = new WebCrawlerTool.CrawlResult();
            result.setUrl(root.path("url").asText());
            result.setTitle(root.path("title").asText());
            result.setContent(root.path("markdown").asText(root.path("content").asText()));
            
            List<String> links = new ArrayList<>();
            JsonNode linksNode = root.path("links");
            if (linksNode.isArray()) {
                for (JsonNode link : linksNode) {
                    links.add(link.asText());
                }
            }
            result.setLinks(links);
            
            Map<String, Object> metadata = new HashMap<>();
            JsonNode metadataNode = root.path("metadata");
            if (metadataNode.isObject()) {
                metadataNode.fields().forEachRemaining(entry -> 
                    metadata.put(entry.getKey(), entry.getValue().asText()));
            }
            result.setMetadata(metadata);
            
            List<WebCrawlerTool.CrawlResult> subPages = new ArrayList<>();
            JsonNode subPagesNode = root.path("sub_pages");
            if (subPagesNode.isArray()) {
                for (JsonNode subPage : subPagesNode) {
                    WebCrawlerTool.CrawlResult subResult = new WebCrawlerTool.CrawlResult();
                    subResult.setUrl(subPage.path("url").asText());
                    subResult.setTitle(subPage.path("title").asText());
                    subResult.setContent(subPage.path("content").asText());
                    subPages.add(subResult);
                }
            }
            result.setSubPages(subPages);
            
            return result;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse crawl response: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // HttpClient 不需要显式关闭
    }
}
