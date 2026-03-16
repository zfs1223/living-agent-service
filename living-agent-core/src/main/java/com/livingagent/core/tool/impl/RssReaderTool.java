package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RssReaderTool implements Tool {

    private static final String NAME = "rss_reader";
    private static final String DESCRIPTION = "RSS/Atom 订阅阅读器，支持内容解析和更新检测";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "data";

    private final HttpClient httpClient;
    private final Map<String, Instant> lastFetchTime = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenGuids = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    public RssReaderTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
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
                .parameter("url", "string", "RSS/Atom 订阅地址", true)
                .parameter("max_items", "integer", "最大返回条目数", false)
                .parameter("only_new", "boolean", "仅返回新条目", false)
                .parameter("full_content", "boolean", "是否获取完整内容", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("rss_parsing", "atom_parsing", "content_extraction", "update_detection");
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
            Integer maxItemsInt = params.getInteger("max_items");
            int maxItems = maxItemsInt != null ? maxItemsInt : 20;
            
            Boolean onlyNewBool = params.getBoolean("only_new");
            boolean onlyNew = onlyNewBool != null && onlyNewBool;
            
            Boolean fullContentBool = params.getBoolean("full_content");
            boolean fullContent = fullContentBool != null && fullContentBool;
            
            String feedContent = fetchFeed(url);
            RssFeed feed = parseFeed(feedContent, url);
            
            List<RssItem> items = feed.getItems();
            if (onlyNew) {
                items = filterNewItems(url, items);
            }
            
            if (items.size() > maxItems) {
                items = items.subList(0, maxItems);
            }
            
            lastFetchTime.put(url, Instant.now());
            
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("feed_title", feed.getTitle());
            output.put("feed_description", feed.getDescription());
            output.put("feed_link", feed.getLink());
            output.put("item_count", items.size());
            output.put("fetched_at", Instant.now().toString());
            
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (RssItem item : items) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("title", item.getTitle());
                itemMap.put("link", item.getLink());
                itemMap.put("description", item.getDescription());
                itemMap.put("author", item.getAuthor());
                itemMap.put("published_at", item.getPubDate());
                itemMap.put("guid", item.getGuid());
                
                if (fullContent && item.getContent() != null) {
                    itemMap.put("content", item.getContent());
                }
                
                if (item.getCategories() != null && !item.getCategories().isEmpty()) {
                    itemMap.put("categories", item.getCategories());
                }
                
                itemList.add(itemMap);
            }
            output.put("items", itemList);
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(output);
            
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("RSS读取失败: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String url = params.getString("url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL参数不能为空");
        }
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

    private String fetchFeed(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "LivingAgent RSS Reader/1.0")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
        
        return response.body();
    }

    private RssFeed parseFeed(String content, String feedUrl) {
        RssFeed feed = new RssFeed();
        feed.setUrl(feedUrl);
        
        if (content.contains("<rss") || content.contains("<channel")) {
            parseRssFeed(content, feed);
        } else if (content.contains("<feed") && content.contains("xmlns=\"http://www.w3.org/2005/Atom\"")) {
            parseAtomFeed(content, feed);
        } else {
            parseRssFeed(content, feed);
        }
        
        return feed;
    }

    private void parseRssFeed(String content, RssFeed feed) {
        feed.setTitle(extractTag(content, "title", "channel"));
        feed.setDescription(extractTag(content, "description", "channel"));
        feed.setLink(extractTag(content, "link", "channel"));
        
        List<RssItem> items = new ArrayList<>();
        String[] itemParts = content.split("<item>");
        
        for (int i = 1; i < itemParts.length; i++) {
            String itemContent = itemParts[i].split("</item>")[0];
            RssItem item = new RssItem();
            item.setTitle(extractTag(itemContent, "title"));
            item.setLink(extractTag(itemContent, "link"));
            item.setDescription(extractTag(itemContent, "description"));
            item.setAuthor(extractTag(itemContent, "author"));
            item.setPubDate(extractTag(itemContent, "pubDate"));
            item.setGuid(extractTag(itemContent, "guid"));
            item.setContent(extractTag(itemContent, "content:encoded"));
            item.setCategories(extractTags(itemContent, "category"));
            items.add(item);
        }
        
        items.sort((a, b) -> {
            if (a.getPubDate() == null) return 1;
            if (b.getPubDate() == null) return -1;
            return b.getPubDate().compareTo(a.getPubDate());
        });
        
        feed.setItems(items);
    }

    private void parseAtomFeed(String content, RssFeed feed) {
        feed.setTitle(extractTag(content, "title", "feed"));
        
        String subtitle = extractTag(content, "subtitle", "feed");
        feed.setDescription(subtitle);
        
        String link = extractAttribute(content, "link", "href", "feed");
        feed.setLink(link);
        
        List<RssItem> items = new ArrayList<>();
        String[] entryParts = content.split("<entry");
        
        for (int i = 1; i < entryParts.length; i++) {
            String entryContent = entryParts[i].split("</entry>")[0];
            RssItem item = new RssItem();
            item.setTitle(extractTag(entryContent, "title"));
            item.setLink(extractAttribute("<entry" + entryContent, "link", "href"));
            item.setDescription(extractTag(entryContent, "summary"));
            item.setAuthor(extractTag(entryContent, "name", "author"));
            item.setPubDate(extractTag(entryContent, "published"));
            item.setGuid(extractTag(entryContent, "id"));
            item.setContent(extractTag(entryContent, "content"));
            item.setCategories(extractAttributes(entryContent, "category", "term"));
            items.add(item);
        }
        
        items.sort((a, b) -> {
            if (a.getPubDate() == null) return 1;
            if (b.getPubDate() == null) return -1;
            return b.getPubDate().compareTo(a.getPubDate());
        });
        
        feed.setItems(items);
    }

    private String extractTag(String content, String tagName) {
        return extractTag(content, tagName, null);
    }

    private String extractTag(String content, String tagName, String parentTag) {
        String searchContent = content;
        
        if (parentTag != null) {
            int parentStart = content.indexOf("<" + parentTag);
            if (parentStart >= 0) {
                int parentEnd = content.indexOf("</" + parentTag + ">", parentStart);
                if (parentEnd > parentStart) {
                    searchContent = content.substring(parentStart, parentEnd);
                }
            }
        }
        
        int start = searchContent.indexOf("<" + tagName);
        if (start < 0) return null;
        
        start = searchContent.indexOf(">", start) + 1;
        int end = searchContent.indexOf("</" + tagName + ">", start);
        
        if (end <= start) return null;
        
        String value = searchContent.substring(start, end);
        return decodeXml(value).trim();
    }

    private String extractAttribute(String content, String tagName, String attrName) {
        return extractAttribute(content, tagName, attrName, null);
    }

    private String extractAttribute(String content, String tagName, String attrName, String parentTag) {
        String searchContent = content;
        
        if (parentTag != null) {
            int parentStart = content.indexOf("<" + parentTag);
            if (parentStart >= 0) {
                int parentEnd = content.indexOf("</" + parentTag + ">", parentStart);
                if (parentEnd > parentStart) {
                    searchContent = content.substring(parentStart, parentEnd);
                }
            }
        }
        
        int tagStart = searchContent.indexOf("<" + tagName);
        if (tagStart < 0) return null;
        
        int tagEnd = searchContent.indexOf(">", tagStart);
        if (tagEnd < 0) return null;
        
        String tagContent = searchContent.substring(tagStart, tagEnd);
        String attrPattern = attrName + "=\"";
        int attrStart = tagContent.indexOf(attrPattern);
        if (attrStart < 0) {
            attrPattern = attrName + "='";
            attrStart = tagContent.indexOf(attrPattern);
        }
        
        if (attrStart < 0) return null;
        
        attrStart += attrPattern.length();
        char quote = attrPattern.charAt(attrPattern.length() - 1);
        int attrEnd = tagContent.indexOf(quote, attrStart);
        
        if (attrEnd < 0) return null;
        
        return decodeXml(tagContent.substring(attrStart, attrEnd));
    }

    private List<String> extractTags(String content, String tagName) {
        List<String> values = new ArrayList<>();
        int start = 0;
        
        while ((start = content.indexOf("<" + tagName, start)) >= 0) {
            int valueStart = content.indexOf(">", start) + 1;
            int valueEnd = content.indexOf("</" + tagName + ">", valueStart);
            
            if (valueEnd > valueStart) {
                values.add(decodeXml(content.substring(valueStart, valueEnd).trim()));
                start = valueEnd + tagName.length() + 3;
            } else {
                break;
            }
        }
        
        return values;
    }

    private List<String> extractAttributes(String content, String tagName, String attrName) {
        List<String> values = new ArrayList<>();
        int start = 0;
        
        while ((start = content.indexOf("<" + tagName, start)) >= 0) {
            int tagEnd = content.indexOf(">", start);
            if (tagEnd < 0) break;
            
            String tagContent = content.substring(start, tagEnd);
            String attrPattern = attrName + "=\"";
            int attrStart = tagContent.indexOf(attrPattern);
            
            if (attrStart >= 0) {
                attrStart += attrPattern.length();
                int attrEnd = tagContent.indexOf("\"", attrStart);
                if (attrEnd > attrStart) {
                    values.add(decodeXml(tagContent.substring(attrStart, attrEnd)));
                }
            }
            
            start = tagEnd + 1;
        }
        
        return values;
    }

    private String decodeXml(String xml) {
        return xml
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("<![CDATA[", "")
            .replace("]]>", "");
    }

    private List<RssItem> filterNewItems(String feedUrl, List<RssItem> items) {
        Set<String> seen = seenGuids.computeIfAbsent(feedUrl, k -> ConcurrentHashMap.newKeySet());
        List<RssItem> newItems = new ArrayList<>();
        
        for (RssItem item : items) {
            String guid = item.getGuid();
            if (guid == null) {
                guid = item.getLink();
            }
            
            if (guid != null && !seen.contains(guid)) {
                newItems.add(item);
                seen.add(guid);
            }
        }
        
        return newItems;
    }

    public void clearSeenItems(String feedUrl) {
        seenGuids.remove(feedUrl);
    }

    public void clearAllSeenItems() {
        seenGuids.clear();
    }

    public static class RssFeed {
        private String url;
        private String title;
        private String description;
        private String link;
        private List<RssItem> items = new ArrayList<>();

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
        public List<RssItem> getItems() { return items; }
        public void setItems(List<RssItem> items) { this.items = items; }
    }

    public static class RssItem {
        private String title;
        private String link;
        private String description;
        private String content;
        private String author;
        private String pubDate;
        private String guid;
        private List<String> categories;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getPubDate() { return pubDate; }
        public void setPubDate(String pubDate) { this.pubDate = pubDate; }
        public String getGuid() { return guid; }
        public void setGuid(String guid) { this.guid = guid; }
        public List<String> getCategories() { return categories; }
        public void setCategories(List<String> categories) { this.categories = categories; }
    }
}
