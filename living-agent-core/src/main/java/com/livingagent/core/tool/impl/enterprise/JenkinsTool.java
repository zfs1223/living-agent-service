package com.livingagent.core.tool.impl.enterprise;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

public class JenkinsTool implements Tool {
    
    private static final Logger log = LoggerFactory.getLogger(JenkinsTool.class);
    private static final String NAME = "jenkins";
    private static final String DESCRIPTION = "Jenkins CI/CD工具，用于触发构建、查看构建状态和管理Jenkins任务";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "devops";
    
    private final String baseUrl;
    private final String username;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private ToolStats stats = ToolStats.empty(NAME);
    
    private final ToolSchema schema;
    
    public JenkinsTool(String baseUrl, String username, String apiToken) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.username = username;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
        
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string", "操作类型: list_jobs, get_job, build, build_status, console_output, cancel_build", true)
            .parameter("job_name", "string", "任务名称", false)
            .parameter("build_number", "integer", "构建编号", false)
            .parameter("parameters", "object", "构建参数", false)
            .parameter("queue_id", "integer", "队列ID (用于取消构建)", false)
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
    public ToolSchema getSchema() { return schema; }
    
    @Override
    public List<String> getCapabilities() {
        return List.of("job_management", "build_trigger", "build_monitoring", "console_output");
    }
    
    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("缺少必要参数: action");
        }
        
        try {
            ToolResult result = switch (action) {
                case "list_jobs" -> listJobs();
                case "get_job" -> getJob(params);
                case "build" -> triggerBuild(params);
                case "build_status" -> getBuildStatus(params);
                case "console_output" -> getConsoleOutput(params);
                case "cancel_build" -> cancelBuild(params);
                default -> ToolResult.failure("不支持的操作: " + action);
            };
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("Jenkins操作失败: {}", e.getMessage(), e);
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("操作失败: " + e.getMessage());
        }
    }
    
    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("缺少必要参数: action");
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
    
    private String getAuthHeader() {
        return "Basic " + java.util.Base64.getEncoder().encodeToString(
            (username + ":" + apiToken).getBytes());
    }
    
    private ToolResult listJobs() throws Exception {
        String url = baseUrl + "/api/json?tree=jobs[name,url,color,lastBuild[number,result,timestamp]]";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", getAuthHeader())
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> jobs = (List<Map<String, Object>>) result.get("jobs");
            
            List<Map<String, Object>> simplifiedJobs = new ArrayList<>();
            if (jobs != null) {
                for (Map<String, Object> job : jobs) {
                    Map<String, Object> simplified = new HashMap<>();
                    simplified.put("name", job.get("name"));
                    simplified.put("url", job.get("url"));
                    simplified.put("color", job.get("color"));
                    
                    Map<String, Object> lastBuild = (Map<String, Object>) job.get("lastBuild");
                    if (lastBuild != null) {
                        simplified.put("lastBuildNumber", lastBuild.get("number"));
                        simplified.put("lastBuildResult", lastBuild.get("result"));
                        simplified.put("lastBuildTime", lastBuild.get("timestamp"));
                    }
                    
                    simplifiedJobs.add(simplified);
                }
            }
            
            return ToolResult.success(Map.of("jobs", simplifiedJobs));
        } else {
            return ToolResult.failure("获取任务列表失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getJob(ToolParams params) throws Exception {
        String jobName = params.getString("job_name");
        if (jobName == null || jobName.isEmpty()) {
            return ToolResult.failure("缺少必要参数: job_name");
        }
        
        String url = baseUrl + "/job/" + jobName + "/api/json";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", getAuthHeader())
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> job = objectMapper.readValue(response.body(), Map.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("name", job.get("name"));
            result.put("url", job.get("url"));
            result.put("description", job.get("description"));
            result.put("buildable", job.get("buildable"));
            result.put("lastBuild", job.get("lastBuild"));
            result.put("lastSuccessfulBuild", job.get("lastSuccessfulBuild"));
            result.put("lastFailedBuild", job.get("lastFailedBuild"));
            result.put("nextBuildNumber", job.get("nextBuildNumber"));
            result.put("inQueue", job.get("inQueue"));
            
            return ToolResult.success(result);
        } else {
            return ToolResult.failure("获取任务详情失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult triggerBuild(ToolParams params) throws Exception {
        String jobName = params.getString("job_name");
        if (jobName == null || jobName.isEmpty()) {
            return ToolResult.failure("缺少必要参数: job_name");
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> buildParams = (Map<String, Object>) params.get("parameters");
        
        String url;
        HttpRequest.Builder requestBuilder;
        
        if (buildParams != null && !buildParams.isEmpty()) {
            StringBuilder paramStr = new StringBuilder();
            for (Map.Entry<String, Object> entry : buildParams.entrySet()) {
                if (paramStr.length() > 0) {
                    paramStr.append("&");
                }
                paramStr.append(entry.getKey())
                    .append("=")
                    .append(java.net.URLEncoder.encode(String.valueOf(entry.getValue()), "UTF-8"));
            }
            url = baseUrl + "/job/" + jobName + "/buildWithParameters?" + paramStr;
        } else {
            url = baseUrl + "/job/" + jobName + "/build";
        }
        
        requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", getAuthHeader())
            .POST(HttpRequest.BodyPublishers.noBody());
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 201 || response.statusCode() == 302) {
            String queueUrl = response.headers().firstValue("Location").orElse(null);
            String queueId = null;
            
            if (queueUrl != null) {
                String[] parts = queueUrl.split("/");
                queueId = parts[parts.length - 1];
            }
            
            return ToolResult.success(Map.of(
                "jobName", jobName,
                "queueId", queueId,
                "queueUrl", queueUrl,
                "message", "构建已触发"
            ));
        } else {
            return ToolResult.failure("触发构建失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getBuildStatus(ToolParams params) throws Exception {
        String jobName = params.getString("job_name");
        if (jobName == null || jobName.isEmpty()) {
            return ToolResult.failure("缺少必要参数: job_name");
        }
        
        Integer buildNumber = params.getInteger("build_number");
        
        String url;
        if (buildNumber != null) {
            url = baseUrl + "/job/" + jobName + "/" + buildNumber + "/api/json";
        } else {
            url = baseUrl + "/job/" + jobName + "/lastBuild/api/json";
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", getAuthHeader())
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> build = objectMapper.readValue(response.body(), Map.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("number", build.get("number"));
            result.put("url", build.get("url"));
            result.put("result", build.get("result"));
            result.put("building", build.get("building"));
            result.put("duration", build.get("duration"));
            result.put("timestamp", build.get("timestamp"));
            result.put("estimatedDuration", build.get("estimatedDuration"));
            result.put("displayName", build.get("displayName"));
            result.put("description", build.get("description"));
            
            Map<String, Object> changeSet = (Map<String, Object>) build.get("changeSet");
            if (changeSet != null) {
                result.put("changeSetKind", changeSet.get("kind"));
                List<Map<String, Object>> items = (List<Map<String, Object>>) changeSet.get("items");
                if (items != null && !items.isEmpty()) {
                    result.put("commitCount", items.size());
                }
            }
            
            return ToolResult.success(result);
        } else {
            return ToolResult.failure("获取构建状态失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getConsoleOutput(ToolParams params) throws Exception {
        String jobName = params.getString("job_name");
        if (jobName == null || jobName.isEmpty()) {
            return ToolResult.failure("缺少必要参数: job_name");
        }
        
        Integer buildNumber = params.getInteger("build_number");
        
        String url;
        if (buildNumber != null) {
            url = baseUrl + "/job/" + jobName + "/" + buildNumber + "/consoleText";
        } else {
            url = baseUrl + "/job/" + jobName + "/lastBuild/consoleText";
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", getAuthHeader())
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            String consoleOutput = response.body();
            int maxLength = 10000;
            String truncated = consoleOutput.length() > maxLength 
                ? "..." + consoleOutput.substring(consoleOutput.length() - maxLength)
                : consoleOutput;
            
            return ToolResult.success(Map.of(
                "consoleOutput", truncated,
                "fullLength", consoleOutput.length(),
                "truncated", consoleOutput.length() > maxLength
            ));
        } else {
            return ToolResult.failure("获取控制台输出失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult cancelBuild(ToolParams params) throws Exception {
        Integer queueId = params.getInteger("queue_id");
        
        if (queueId == null) {
            return ToolResult.failure("缺少必要参数: queue_id");
        }
        
        String url = baseUrl + "/queue/cancelItem?id=" + queueId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", getAuthHeader())
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200 || response.statusCode() == 302) {
            return ToolResult.success(Map.of(
                "queueId", queueId,
                "message", "构建已取消"
            ));
        } else {
            return ToolResult.failure("取消构建失败: HTTP " + response.statusCode());
        }
    }
}
