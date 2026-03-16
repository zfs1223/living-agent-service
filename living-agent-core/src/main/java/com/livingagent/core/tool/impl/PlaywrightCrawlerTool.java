package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlaywrightCrawlerTool implements Tool, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightCrawlerTool.class);

    private static final String NAME = "playwright_crawler";
    private static final String DESCRIPTION = "Java原生网页爬取工具，支持JavaScript渲染、反爬虫措施、内容提取";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "data";

    private Playwright playwright;
    private Browser browser;
    private final CrawlerConfig config;
    private ToolStats stats = ToolStats.empty(NAME);
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private final List<String> userAgentPool = List.of(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15"
    );

    public PlaywrightCrawlerTool() {
        this.config = new CrawlerConfig();
        initBrowser();
    }

    public PlaywrightCrawlerTool(CrawlerConfig config) {
        this.config = config;
        initBrowser();
    }

    private synchronized void initBrowser() {
        if (browser == null) {
            log.info("Initializing Playwright browser...");
            playwright = Playwright.create();
            
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(config.isHeadless())
                .setArgs(Arrays.asList(
                    "--disable-blink-features=AutomationControlled",
                    "--disable-features=IsolateOrigins,site-per-process",
                    "--disable-site-isolation-trials",
                    "--no-sandbox",
                    "--disable-dev-shm-usage"
                ));
            
            if (config.getProxyServer() != null) {
                options.setProxy(config.getProxyServer());
            }
            
            browser = playwright.chromium().launch(options);
            log.info("Playwright browser initialized successfully");
        }
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
                .parameter("extract_type", "string", "内容提取类型: markdown, text, html, links", false)
                .parameter("selector", "string", "CSS选择器，提取特定内容", false)
                .parameter("js_script", "string", "JavaScript脚本，用于动态页面交互", false)
                .parameter("wait_time", "integer", "页面加载等待时间(毫秒)", false)
                .parameter("screenshot", "boolean", "是否截图", false)
                .parameter("stealth", "boolean", "是否启用反检测模式", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("web_crawling", "javascript_rendering", "content_extraction", "anti_detection");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String url = params.getString("url");
        
        if (url == null || url.isEmpty()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("URL参数不能为空");
        }
        
        try {
            validateUrl(url);
            applyRateLimit(url);
            
            String extractType = params.getString("extract_type");
            if (extractType == null) extractType = "markdown";
            
            String selector = params.getString("selector");
            String jsScript = params.getString("js_script");
            Integer waitTimeInt = params.getInteger("wait_time");
            int waitTime = waitTimeInt != null ? waitTimeInt : 2000;
            
            Boolean screenshotBool = params.getBoolean("screenshot");
            boolean screenshot = screenshotBool != null && screenshotBool;
            
            Boolean stealthBool = params.getBoolean("stealth");
            boolean stealth = stealthBool == null || stealthBool;
            
            CrawlResult result = crawl(url, extractType, selector, jsScript, waitTime, screenshot, stealth);
            
            recordRequest(url);
            
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("url", url);
            output.put("title", result.getTitle());
            output.put("content", result.getContent());
            output.put("links", result.getLinks());
            output.put("metadata", result.getMetadata());
            output.put("crawl_time_ms", result.getCrawlTimeMs());
            
            if (result.getScreenshotPath() != null) {
                output.put("screenshot", result.getScreenshotPath());
            }
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(output);
            
        } catch (Exception e) {
            log.error("Crawl failed for {}: {}", url, e.getMessage());
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

    private CrawlResult crawl(String url, String extractType, String selector, 
                              String jsScript, int waitTime, boolean screenshot, boolean stealth) {
        long startTime = System.currentTimeMillis();
        CrawlResult result = new CrawlResult();
        result.setUrl(url);
        
        BrowserContext context = createStealthContext(stealth);
        Page page = context.newPage();
        
        try {
            log.info("Crawling: {}", url);
            
            page.navigate(url, new Page.NavigateOptions()
                .setTimeout(config.getNavigationTimeout())
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            
            if (waitTime > 0) {
                page.waitForTimeout(waitTime);
            }
            
            page.waitForLoadState(LoadState.NETWORKIDLE, 
                new Page.WaitForLoadStateOptions().setTimeout(config.getLoadTimeout()));
            
            if (jsScript != null && !jsScript.isEmpty()) {
                page.evaluate(jsScript);
                page.waitForTimeout(500);
            }
            
            result.setTitle(page.title());
            
            String content = extractContent(page, extractType, selector);
            result.setContent(content);
            
            List<String> links = extractLinks(page);
            result.setLinks(links);
            
            Map<String, Object> metadata = extractMetadata(page);
            result.setMetadata(metadata);
            
            if (screenshot) {
                String screenshotPath = config.getScreenshotDir() + "/screenshot_" + System.currentTimeMillis() + ".png";
                page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(screenshotPath))
                    .setFullPage(true));
                result.setScreenshotPath(screenshotPath);
            }
            
            result.setCrawlTimeMs(System.currentTimeMillis() - startTime);
            log.info("Crawled {} in {}ms", url, result.getCrawlTimeMs());
            
        } catch (Exception e) {
            log.error("Error crawling {}: {}", url, e.getMessage());
            throw new RuntimeException("Crawl failed: " + e.getMessage(), e);
        } finally {
            page.close();
            context.close();
        }
        
        return result;
    }

    private BrowserContext createStealthContext(boolean stealth) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
            .setUserAgent(getRandomUserAgent())
            .setViewportSize(1920, 1080)
            .setLocale("zh-CN")
            .setTimezoneId("Asia/Shanghai");
        
        if (config.getProxyServer() != null) {
            options.setProxy(config.getProxyServer());
        }
        
        BrowserContext context = browser.newContext(options);
        
        if (stealth) {
            String stealthScript = generateStealthScript();
            context.addInitScript(stealthScript);
        }
        
        return context;
    }

    private String generateStealthScript() {
        return """
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
            Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });
            window.chrome = { runtime: {} };
            Object.defineProperty(navigator, 'permissions', {
                query: () => Promise.resolve({ state: 'granted' })
            });
            """;
    }

    private String extractContent(Page page, String extractType, String selector) {
        String content;
        
        switch (extractType.toLowerCase()) {
            case "html":
                if (selector != null) {
                    content = (String) page.evaluate(
                        "sel => document.querySelector(sel)?.outerHTML || ''", selector);
                } else {
                    content = page.content();
                }
                break;
                
            case "text":
                if (selector != null) {
                    content = (String) page.evaluate(
                        "sel => document.querySelector(sel)?.textContent || ''", selector);
                } else {
                    content = (String) page.evaluate(
                        "() => document.body?.innerText || ''");
                }
                break;
                
            case "links":
                content = String.join("\n", extractLinks(page));
                break;
                
            case "markdown":
            default:
                content = extractMarkdown(page, selector);
                break;
        }
        
        return content != null ? content.trim() : "";
    }

    private String extractMarkdown(Page page, String selector) {
        return (String) page.evaluate("""
            (selector) => {
                const element = selector ? document.querySelector(selector) : document.body;
                if (!element) return '';
                
                function htmlToMarkdown(el, depth = 0) {
                    if (!el) return '';
                    
                    let result = '';
                    
                    for (const child of el.childNodes) {
                        if (child.nodeType === Node.TEXT_NODE) {
                            const text = child.textContent.trim();
                            if (text) result += text + ' ';
                        } else if (child.nodeType === Node.ELEMENT_NODE) {
                            const tag = child.tagName.toLowerCase();
                            const content = htmlToMarkdown(child, depth + 1);
                            
                            switch (tag) {
                                case 'h1': result += '# ' + content + '\\n\\n'; break;
                                case 'h2': result += '## ' + content + '\\n\\n'; break;
                                case 'h3': result += '### ' + content + '\\n\\n'; break;
                                case 'h4': result += '#### ' + content + '\\n\\n'; break;
                                case 'h5': result += '##### ' + content + '\\n\\n'; break;
                                case 'h6': result += '###### ' + content + '\\n\\n'; break;
                                case 'p': result += content + '\\n\\n'; break;
                                case 'br': result += '\\n'; break;
                                case 'a':
                                    const href = child.getAttribute('href') || '';
                                    result += '[' + content.trim() + '](' + href + ') ';
                                    break;
                                case 'strong':
                                case 'b': result += '**' + content.trim() + '** '; break;
                                case 'em':
                                case 'i': result += '*' + content.trim() + '* '; break;
                                case 'code': result += '`' + content.trim() + '` '; break;
                                case 'pre': result += '```\\n' + content.trim() + '\\n```\\n\\n'; break;
                                case 'blockquote': result += '> ' + content.trim() + '\\n\\n'; break;
                                case 'ul':
                                case 'ol': result += content + '\\n'; break;
                                case 'li': result += '- ' + content.trim() + '\\n'; break;
                                case 'div':
                                case 'section':
                                case 'article': result += content + '\\n'; break;
                                case 'img':
                                    const alt = child.getAttribute('alt') || '';
                                    const src = child.getAttribute('src') || '';
                                    result += '![' + alt + '](' + src + ')\\n';
                                    break;
                                default: result += content;
                            }
                        }
                    }
                    
                    return result;
                }
                
                return htmlToMarkdown(element);
            }
            """, selector);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractLinks(Page page) {
        Object result = page.evaluate("""
            () => {
                const links = [];
                document.querySelectorAll('a[href]').forEach(a => {
                    const href = a.getAttribute('href');
                    const text = a.textContent.trim();
                    if (href && !href.startsWith('javascript:')) {
                        links.push(text + ' | ' + href);
                    }
                });
                return links;
            }
            """);
        
        if (result instanceof List) {
            return (List<String>) result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetadata(Page page) {
        Object result = page.evaluate("""
            () => {
                const meta = {};
                
                const title = document.querySelector('title');
                if (title) meta.title = title.textContent;
                
                const description = document.querySelector('meta[name="description"]');
                if (description) meta.description = description.getAttribute('content');
                
                const keywords = document.querySelector('meta[name="keywords"]');
                if (keywords) meta.keywords = keywords.getAttribute('content');
                
                const author = document.querySelector('meta[name="author"]');
                if (author) meta.author = author.getAttribute('content');
                
                const ogTitle = document.querySelector('meta[property="og:title"]');
                if (ogTitle) meta.ogTitle = ogTitle.getAttribute('content');
                
                const ogImage = document.querySelector('meta[property="og:image"]');
                if (ogImage) meta.ogImage = ogImage.getAttribute('content');
                
                meta.url = window.location.href;
                meta.domain = window.location.hostname;
                
                return meta;
            }
            """);
        
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return new HashMap<>();
    }

    private void validateUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL必须以http://或https://开头");
        }
        
        if (config.getBlockedDomains() != null) {
            try {
                String domain = new java.net.URL(url).getHost();
                if (config.getBlockedDomains().contains(domain)) {
                    throw new IllegalArgumentException("域名 " + domain + " 已被禁止爬取");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("无效的URL: " + e.getMessage());
            }
        }
    }

    private void applyRateLimit(String url) {
        try {
            String domain = new java.net.URL(url).getHost();
            Long lastRequest = lastRequestTime.get(domain);
            
            if (lastRequest != null) {
                long elapsed = System.currentTimeMillis() - lastRequest;
                int minInterval = config.getMinRequestIntervalMs();
                
                if (elapsed < minInterval) {
                    Thread.sleep(minInterval - elapsed);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
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
            String domain = new java.net.URL(url).getHost();
            lastRequestTime.put(domain, System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
        log.info("PlaywrightCrawlerTool closed");
    }

    public static class CrawlerConfig {
        private boolean headless = true;
        private int navigationTimeout = 30000;
        private int loadTimeout = 10000;
        private int minRequestIntervalMs = 1000;
        private String userAgent;
        private String proxyServer;
        private String screenshotDir = "./screenshots";
        private Set<String> blockedDomains = new HashSet<>();

        public boolean isHeadless() { return headless; }
        public void setHeadless(boolean headless) { this.headless = headless; }
        
        public int getNavigationTimeout() { return navigationTimeout; }
        public void setNavigationTimeout(int ms) { this.navigationTimeout = ms; }
        
        public int getLoadTimeout() { return loadTimeout; }
        public void setLoadTimeout(int ms) { this.loadTimeout = ms; }
        
        public int getMinRequestIntervalMs() { return minRequestIntervalMs; }
        public void setMinRequestIntervalMs(int ms) { this.minRequestIntervalMs = ms; }
        
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String ua) { this.userAgent = ua; }
        
        public String getProxyServer() { return proxyServer; }
        public void setProxyServer(String proxy) { this.proxyServer = proxy; }
        
        public String getScreenshotDir() { return screenshotDir; }
        public void setScreenshotDir(String dir) { this.screenshotDir = dir; }
        
        public Set<String> getBlockedDomains() { return blockedDomains; }
        public void setBlockedDomains(Set<String> domains) { this.blockedDomains = domains; }
    }

    public static class CrawlResult {
        private String url;
        private String title;
        private String content;
        private List<String> links;
        private Map<String, Object> metadata;
        private long crawlTimeMs;
        private String screenshotPath;

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
        public String getScreenshotPath() { return screenshotPath; }
        public void setScreenshotPath(String path) { this.screenshotPath = path; }
    }
}
