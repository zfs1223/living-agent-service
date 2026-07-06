package com.livingagent.core.sandbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "living-agent.claude-cli")
public class ClaudeCliProperties {

    private boolean enabled = true;
    private String command = "claude";
    private String workspace = "./agent_workspace";
    private String defaultOutputFormat = "stream-json";
    private boolean dangerouslySkipPermissions = true;
    private boolean bashNoLogin = true;
    private String shell = "sh";
    private List<String> allowedDirs = List.of("./agent_workspace");
    private int maxConcurrentSessions = 3;
    private int sessionTimeoutMinutes = 30;
    private int jobTimeoutMinutes = 60;
    private String mcpConfigPath = "classpath:claude/mcp.json";
    private boolean mcpEnabled = true;
    private Codegraph codegraph = new Codegraph();
    private Proxy proxy = new Proxy();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public String getDefaultOutputFormat() { return defaultOutputFormat; }
    public void setDefaultOutputFormat(String defaultOutputFormat) { this.defaultOutputFormat = defaultOutputFormat; }
    public boolean isDangerouslySkipPermissions() { return dangerouslySkipPermissions; }
    public void setDangerouslySkipPermissions(boolean dangerouslySkipPermissions) { this.dangerouslySkipPermissions = dangerouslySkipPermissions; }
    public boolean isBashNoLogin() { return bashNoLogin; }
    public void setBashNoLogin(boolean bashNoLogin) { this.bashNoLogin = bashNoLogin; }
    public String getShell() { return shell; }
    public void setShell(String shell) { this.shell = shell; }
    public List<String> getAllowedDirs() { return allowedDirs; }
    public void setAllowedDirs(List<String> allowedDirs) { this.allowedDirs = allowedDirs; }
    public int getMaxConcurrentSessions() { return maxConcurrentSessions; }
    public void setMaxConcurrentSessions(int maxConcurrentSessions) { this.maxConcurrentSessions = maxConcurrentSessions; }
    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }
    public int getJobTimeoutMinutes() { return jobTimeoutMinutes; }
    public void setJobTimeoutMinutes(int jobTimeoutMinutes) { this.jobTimeoutMinutes = jobTimeoutMinutes; }
    public String getMcpConfigPath() { return mcpConfigPath; }
    public void setMcpConfigPath(String mcpConfigPath) { this.mcpConfigPath = mcpConfigPath; }
    public boolean isMcpEnabled() { return mcpEnabled; }
    public void setMcpEnabled(boolean mcpEnabled) { this.mcpEnabled = mcpEnabled; }
    public Codegraph getCodegraph() { return codegraph; }
    public void setCodegraph(Codegraph codegraph) { this.codegraph = codegraph; }
    public Proxy getProxy() { return proxy; }
    public void setProxy(Proxy proxy) { this.proxy = proxy; }

    /**
     * CodeGraph 语义代码索引配置
     * CodeGraph 为 Claude CLI 提供符号关系图谱、影响分析和全文搜索能力
     * 索引建在客户端本地工作目录，不修改服务器端代码库镜像
     */
    public static class Codegraph {
        private boolean enabled = true;
        private String command = "codegraph";
        private int watchDebounceMs = 5000;
        private boolean autoSync = true;
        private List<String> excludePatterns = List.of(
            ".env", "credentials", "secret", "*.key", "*.pem", "*.p12",
            "node_modules", ".git", "target", "build", "dist"
        );

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public int getWatchDebounceMs() { return watchDebounceMs; }
        public void setWatchDebounceMs(int watchDebounceMs) { this.watchDebounceMs = watchDebounceMs; }
        public boolean isAutoSync() { return autoSync; }
        public void setAutoSync(boolean autoSync) { this.autoSync = autoSync; }
        public List<String> getExcludePatterns() { return excludePatterns; }
        public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }
    }

    public static class Proxy {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8480/api/v1/proxy/anthropic";
        private String apiUrl = "http://localhost:8480/api/v1/proxy/anthropic/v1";
        private String apiKeyPlaceholder = "sk-living-agent-claude-proxy";
        private String authToken = "";
        private boolean requireAuth = false;
        private String defaultBrainId = "tech";
        private String defaultDepartmentId = "tech";
        private String defaultTaskType = "code_generation";
        private int streamTimeoutSeconds = 600;
        private int maxInputTokens = 120000;
        private int maxOutputTokens = 8192;
        private boolean auditEnabled = true;
        private boolean pushStreamEvents = true;
        private Map<String, String> virtualModelMapping = Map.of(
            "claude-sonnet-4-20250514", "balanced",
            "claude-sonnet-4", "balanced",
            "claude-opus-4-20250514", "powerful",
            "claude-opus-4", "powerful",
            "claude-haiku-4-20250514", "fast",
            "claude-haiku-4", "fast"
        );

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKeyPlaceholder() { return apiKeyPlaceholder; }
        public void setApiKeyPlaceholder(String apiKeyPlaceholder) { this.apiKeyPlaceholder = apiKeyPlaceholder; }
        public String getAuthToken() { return authToken; }
        public void setAuthToken(String authToken) { this.authToken = authToken; }
        public boolean isRequireAuth() { return requireAuth; }
        public void setRequireAuth(boolean requireAuth) { this.requireAuth = requireAuth; }
        public String getDefaultBrainId() { return defaultBrainId; }
        public void setDefaultBrainId(String defaultBrainId) { this.defaultBrainId = defaultBrainId; }
        public String getDefaultDepartmentId() { return defaultDepartmentId; }
        public void setDefaultDepartmentId(String defaultDepartmentId) { this.defaultDepartmentId = defaultDepartmentId; }
        public String getDefaultTaskType() { return defaultTaskType; }
        public void setDefaultTaskType(String defaultTaskType) { this.defaultTaskType = defaultTaskType; }
        public int getStreamTimeoutSeconds() { return streamTimeoutSeconds; }
        public void setStreamTimeoutSeconds(int streamTimeoutSeconds) { this.streamTimeoutSeconds = streamTimeoutSeconds; }
        public int getMaxInputTokens() { return maxInputTokens; }
        public void setMaxInputTokens(int maxInputTokens) { this.maxInputTokens = maxInputTokens; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
        public boolean isAuditEnabled() { return auditEnabled; }
        public void setAuditEnabled(boolean auditEnabled) { this.auditEnabled = auditEnabled; }
        public boolean isPushStreamEvents() { return pushStreamEvents; }
        public void setPushStreamEvents(boolean pushStreamEvents) { this.pushStreamEvents = pushStreamEvents; }
        public Map<String, String> getVirtualModelMapping() { return virtualModelMapping; }
        public void setVirtualModelMapping(Map<String, String> virtualModelMapping) { this.virtualModelMapping = virtualModelMapping; }
    }
}
