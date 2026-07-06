package com.livingagent.core.evolution.codebase;

import java.util.*;

/**
 * 代码库访问配置
 */
public class CodebaseAccessConfig {
    private String projectRoot;
    private Map<String, String> mountPoints;     // 挂载点：docs, documents, src
    private List<String> sensitivePatterns;       // 敏感文件过滤模式
    private boolean accessLog;                    // 是否记录访问日志
    private int rateLimitPerMinute;               // 每分钟速率限制

    public CodebaseAccessConfig() {
        this.mountPoints = new LinkedHashMap<>();
        this.sensitivePatterns = new ArrayList<>(Arrays.asList(
            ".env", "credentials", "secret", "password", "token", ".key", ".pem", ".p12"
        ));
        this.accessLog = true;
        this.rateLimitPerMinute = 20;
    }

    // getter/setter
    public String getProjectRoot() { return projectRoot; }
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }
    public Map<String, String> getMountPoints() { return mountPoints; }
    public void setMountPoints(Map<String, String> mountPoints) { this.mountPoints = mountPoints; }
    public List<String> getSensitivePatterns() { return sensitivePatterns; }
    public void setSensitivePatterns(List<String> sensitivePatterns) { this.sensitivePatterns = sensitivePatterns; }
    public boolean isAccessLog() { return accessLog; }
    public void setAccessLog(boolean accessLog) { this.accessLog = accessLog; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
}
