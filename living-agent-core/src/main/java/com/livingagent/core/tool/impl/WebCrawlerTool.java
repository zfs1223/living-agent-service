package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawlerTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlerTool.class);
    private static final String NAME = "web_crawler";
    private static final String DESCRIPTION = "智能网页爬取工具，支持反爬虫措施、内容提取和结构化输出";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "data";
    
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Crawl4aiClient crawl4aiClient;
    private final CrawlerConfig config;
    private ToolStats stats = ToolStats.empty(NAME);
    
    private final List<String> userAgentPool = List.of(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15"
    );
    
    private final Random random = new Random();
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> domainRequestCount = new ConcurrentHashMap<>();

    public WebCrawlerTool() {
        this.config = new CrawlerConfig();
        this.crawl4aiClient = new Crawl4aiClient(config);
    }

    public WebCrawlerTool(CrawlerConfig config) {
        this.config = config;
        this.crawl4aiClient = new Crawl4aiClient(config);
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("url", "string", "要爬取的URL地址", true)
                .parameter("extract_type", "string", "内容提取类型: markdown, text, json, links", false)
                .parameter("selector", "string", "CSS选择器，提取特定内容", false)
                .parameter("js_script", "string", "JavaScript脚本，用于动态页面交互", false)
                .parameter("wait_time", "integer", "页面加载等待时间(毫秒)", false)
                .parameter("deep_crawl", "boolean", "是否深度爬取(跟随链接)", false)
                .parameter("max_pages", "integer", "深度爬取最大页面数", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("web_crawling", "content_extraction", "anti_detection", "rate_limiting");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String url = params.getString("url");
        
        if (url == null || url.isEmpty()) {
            return ToolResult.failure("URL参数不能为空");
        }
        
        try {
            validateUrl(url);
            checkRobotsTxt(url);
            applyRateLimit(url);
            
            String extractType = params.getString("extract_type");
            if (extractType == null) extractType = "markdown";
            
            String selector = params.getString("selector");
            String jsScript = params.getString("js_script");
            Integer waitTimeInt = params.getInteger("wait_time");
            int waitTime = waitTimeInt != null ? waitTimeInt : 2000;
            
            Boolean deepCrawlBool = params.getBoolean("deep_crawl");
            boolean deepCrawl = deepCrawlBool != null && deepCrawlBool;
            
            Integer maxPagesInt = params.getInteger("max_pages");
            int maxPages = maxPagesInt != null ? maxPagesInt : 10;
            
            CrawlRequest request = new CrawlRequest();
            request.setUrl(url);
            request.setExtractType(extractType);
            request.setSelector(selector);
            request.setJsScript(jsScript);
            request.setWaitTime(waitTime);
            request.setDeepCrawl(deepCrawl);
            request.setMaxPages(maxPages);
            request.setUserAgent(getRandomUserAgent());
            request.setProxy(config.getNextProxy());
            
            CrawlResult result = crawl4aiClient.crawl(request);
            
            recordRequest(url);
            
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("url", url);
            output.put("title", result.getTitle());
            output.put("content", result.getContent());
            output.put("links", result.getLinks());
            output.put("metadata", result.getMetadata());
            output.put("crawl_time_ms", result.getCrawlTimeMs());
            
            if (deepCrawl && result.getSubPages() != null) {
                output.put("sub_pages", result.getSubPages());
            }
            
            long duration = System.currentTimeMillis() - startTime;
            stats = stats.recordCall(true, duration);
            return ToolResult.success(output);
            
        } catch (RateLimitException e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("请求频率超限: " + e.getMessage());
        } catch (RobotsBlockedException e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("robots.txt 禁止爬取: " + e.getMessage());
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("爬取失败: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String url = params.getString("url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL参数不能为空");
        }
        validateUrl(url);
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("仅支持 HTTP/HTTPS 协议");
            }
            
            String host = uri.getHost();
            if (config.getBlockedDomains().contains(host)) {
                throw new IllegalArgumentException("域名 " + host + " 已被禁止爬取");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的URL: " + e.getMessage());
        }
    }

    private void checkRobotsTxt(String url) {
        if (!config.isRespectRobotsTxt()) {
            return;
        }
        
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String robotsUrl = uri.getScheme() + "://" + host + "/robots.txt";
            
            RobotsChecker checker = new RobotsChecker(config.getUserAgent());
            if (!checker.isAllowed(url)) {
                throw new RobotsBlockedException(url);
            }
        } catch (RobotsBlockedException e) {
            throw e;
        } catch (Exception e) {
            // robots.txt 检查失败时允许继续
        }
    }

    private void applyRateLimit(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            
            Long lastRequest = lastRequestTime.get(domain);
            if (lastRequest != null) {
                long elapsed = System.currentTimeMillis() - lastRequest;
                int minInterval = config.getMinRequestIntervalMs();
                
                if (elapsed < minInterval) {
                    Thread.sleep(minInterval - elapsed);
                }
            }
            
            Integer count = domainRequestCount.getOrDefault(domain, 0);
            if (count >= config.getMaxRequestsPerDomain()) {
                throw new RateLimitException("域名 " + domain + " 请求次数已达上限");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.net.URISyntaxException e) {
            log.warn("Invalid URL for rate limiting: {}", url);
        }
    }

    private String getRandomUserAgent() {
        if (config.getUserAgent() != null) {
            return config.getUserAgent();
        }
        return userAgentPool.get(random.nextInt(userAgentPool.size()));
    }

    private void recordRequest(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            lastRequestTime.put(domain, System.currentTimeMillis());
            domainRequestCount.merge(domain, 1, Integer::sum);
        } catch (Exception ignored) {
        }
    }

    public void resetDomainCounters() {
        domainRequestCount.clear();
    }

    public void shutdown() {
        executor.shutdown();
        crawl4aiClient.close();
    }

    public static class CrawlerConfig {
        private String crawl4aiUrl = "http://crawl4ai:11235";
        private String userAgent;
        private List<String> proxies = new ArrayList<>();
        private int currentProxyIndex = 0;
        private boolean respectRobotsTxt = true;
        private int minRequestIntervalMs = 1000;
        private int maxRequestsPerDomain = 100;
        private int requestTimeoutMs = 30000;
        private Set<String> blockedDomains = new HashSet<>();
        
        public String getCrawl4aiUrl() { return crawl4aiUrl; }
        public void setCrawl4aiUrl(String url) { this.crawl4aiUrl = url; }
        
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String ua) { this.userAgent = ua; }
        
        public List<String> getProxies() { return proxies; }
        public void setProxies(List<String> proxies) { this.proxies = proxies; }
        
        public String getNextProxy() {
            if (proxies.isEmpty()) return null;
            String proxy = proxies.get(currentProxyIndex);
            currentProxyIndex = (currentProxyIndex + 1) % proxies.size();
            return proxy;
        }
        
        public boolean isRespectRobotsTxt() { return respectRobotsTxt; }
        public void setRespectRobotsTxt(boolean respect) { this.respectRobotsTxt = respect; }
        
        public int getMinRequestIntervalMs() { return minRequestIntervalMs; }
        public void setMinRequestIntervalMs(int ms) { this.minRequestIntervalMs = ms; }
        
        public int getMaxRequestsPerDomain() { return maxRequestsPerDomain; }
        public void setMaxRequestsPerDomain(int max) { this.maxRequestsPerDomain = max; }
        
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(int ms) { this.requestTimeoutMs = ms; }
        
        public Set<String> getBlockedDomains() { return blockedDomains; }
        public void setBlockedDomains(Set<String> domains) { this.blockedDomains = domains; }
    }

    public static class CrawlRequest {
        private String url;
        private String extractType = "markdown";
        private String selector;
        private String jsScript;
        private int waitTime = 2000;
        private boolean deepCrawl;
        private int maxPages = 10;
        private String userAgent;
        private String proxy;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getExtractType() { return extractType; }
        public void setExtractType(String type) { this.extractType = type; }
        public String getSelector() { return selector; }
        public void setSelector(String selector) { this.selector = selector; }
        public String getJsScript() { return jsScript; }
        public void setJsScript(String script) { this.jsScript = script; }
        public int getWaitTime() { return waitTime; }
        public void setWaitTime(int ms) { this.waitTime = ms; }
        public boolean isDeepCrawl() { return deepCrawl; }
        public void setDeepCrawl(boolean deep) { this.deepCrawl = deep; }
        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int max) { this.maxPages = max; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String ua) { this.userAgent = ua; }
        public String getProxy() { return proxy; }
        public void setProxy(String proxy) { this.proxy = proxy; }
    }

    public static class CrawlResult {
        private String url;
        private String title;
        private String content;
        private List<String> links;
        private Map<String, Object> metadata;
        private long crawlTimeMs;
        private List<CrawlResult> subPages;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getLinks() { return links; }
        public void setLinks(List<String> links) { this.links = links; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        public long getCrawlTimeMs() { return crawlTimeMs; }
        public void setCrawlTimeMs(long ms) { this.crawlTimeMs = ms; }
        public List<CrawlResult> getSubPages() { return subPages; }
        public void setSubPages(List<CrawlResult> pages) { this.subPages = pages; }
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) { super(message); }
    }

    public static class RobotsBlockedException extends RuntimeException {
        public RobotsBlockedException(String url) { super("URL blocked by robots.txt: " + url); }
    }
}