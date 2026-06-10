package com.livingagent.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.WindowsAutomationNodeEntity;
import com.livingagent.core.database.repository.WindowsAutomationNodeRepository;
import com.livingagent.core.security.ApprovalManager;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WindowsAppTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WindowsAppTool.class);

    private static final String NAME = "windows_app_automation";
    private static final String DESCRIPTION = "Windows桌面应用自动化工具，支持局域网内多台电脑的远程应用控制，包括金蝶KIS等财务软件的操作";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "admin";

    private final ObjectMapper objectMapper;
    private final Map<String, NodeConfig> nodes = new ConcurrentHashMap<>();
    private final Map<String, HttpClient> nodeClients = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);
    private WindowsAutomationNodeRepository nodeRepository;
    private ApprovalManager approvalManager;

    public WindowsAppTool() {
        this.objectMapper = new ObjectMapper();
        initializeDefaultNodes();
    }

    /** Spring 注入 Repository 后调用，从数据库动态加载节点 */
    public void setNodeRepository(WindowsAutomationNodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
        loadNodesFromDatabase();
    }

    /** Spring 注入 ApprovalManager，高风险操作执行前进行审批检查 */
    public void setApprovalManager(ApprovalManager approvalManager) {
        this.approvalManager = approvalManager;
        log.info("WindowsAppTool: ApprovalManager injected for high-risk operation approval");
    }

    private void initializeDefaultNodes() {
        // 初始不加载硬编码节点，等待 setNodeRepository 从数据库加载
        log.info("WindowsAppTool 初始化，等待数据库节点加载");
    }

    /** 从数据库加载所有已启用的节点 */
    public void loadNodesFromDatabase() {
        if (nodeRepository == null) {
            log.warn("NodeRepository 未注入，跳过数据库节点加载");
            return;
        }
        try {
            List<WindowsAutomationNodeEntity> dbNodes = nodeRepository.findByEnabledTrue();
            for (WindowsAutomationNodeEntity entity : dbNodes) {
                String url = "http://" + entity.getIpAddress() + ":" + entity.getPort();
                addNode(entity.getNodeId(), url,
                        entity.getDescription() != null ? entity.getDescription() : entity.getHostname());
            }
            log.info("从数据库加载 {} 个 Windows 自动化节点", dbNodes.size());
        } catch (Exception e) {
            log.error("从数据库加载节点失败: {}", e.getMessage());
        }
    }

    public void addNode(String nodeId, String url, String description) {
        NodeConfig config = new NodeConfig(nodeId, url, description);
        nodes.put(nodeId, config);
        
        // 为该节点创建专用的 HTTP 客户端
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        nodeClients.put(nodeId, client);
        
        log.info("添加节点: {} -> {}", nodeId, url);
    }

    /** 移除节点（禁用或删除时调用） */
    public void removeNode(String nodeId) {
        nodes.remove(nodeId);
        nodeClients.remove(nodeId);
        log.info("移除节点: {}", nodeId);
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
                .parameter("action", "string", "操作类型: launch, login, menu, click, type_keys, get_text, screenshot, controls, close, sessions", true)
                .parameter("node", "string", "目标节点ID，如 pc-finance-01", true)
                .parameter("app_name", "string", "应用名称，如 kingdee_mini", false)
                .parameter("exe_path", "string", "可执行文件路径", false)
                .parameter("backend", "string", "pywinauto后端: win32或uia", false)
                .parameter("session_id", "string", "会话ID", false)
                .parameter("username", "string", "登录用户名", false)
                .parameter("password", "string", "登录密码", false)
                .parameter("menu_path", "string", "菜单路径，如 财务报表->资产负债表", false)
                .parameter("control_type", "string", "控件类型: Button, Edit, Window等", false)
                .parameter("title_pattern", "string", "控件标题正则表达式", false)
                .parameter("text", "string", "要输入的文本", false)
                .parameter("output_path", "string", "截图或导出保存路径", false)
                .parameter("timeout", "integer", "超时时间(秒)", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("launch_app", "login", "menu_navigation", "click_control", "type_keys", "get_text", "screenshot", "export_data", "window_control");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        
        // 高风险操作审批检查
        if (approvalManager != null && approvalManager.needsApproval(NAME)) {
            ApprovalManager.ApprovalResponse approval = approvalManager.requestApproval(NAME, null);
            if (approval == ApprovalManager.ApprovalResponse.NO) {
                log.warn("WindowsAppTool 操作被审批拒绝: action={}", action);
                return ToolResult.failure("操作被审批拒绝: " + action);
            }
            if (approval == ApprovalManager.ApprovalResponse.ALWAYS) {
                approvalManager.addToAllowlist(NAME);
                log.info("WindowsAppTool 已加入审批白名单");
            }
        }
        
        try {
            String node = params.getString("node");
            if (node == null) {
                throw new IllegalArgumentException("node 参数不能为空，请指定目标电脑");
            }
            
            if (!nodes.containsKey(node)) {
                throw new IllegalArgumentException("节点不存在: " + node + "，可用节点: " + nodes.keySet());
            }

            Map<String, Object> result = switch (action) {
                case "launch" -> launch(node, params);
                case "login" -> login(node, params);
                case "menu" -> selectMenu(node, params);
                case "click" -> click(node, params);
                case "type_keys" -> typeKeys(node, params);
                case "get_text" -> getText(node, params);
                case "screenshot" -> screenshot(node, params);
                case "controls" -> getControls(node, params);
                case "close" -> close(node, params);
                case "sessions" -> listSessions(node, params);
                case "health" -> healthCheck(node, params);
                case "list_nodes" -> listNodes(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("Windows应用自动化操作失败: {}", e.getMessage(), e);
            return ToolResult.failure("Windows应用自动化操作失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> launch(String node, ToolParams params) throws IOException, InterruptedException {
        String appName = params.getString("app_name");
        if (appName == null) {
            throw new IllegalArgumentException("app_name 参数不能为空");
        }

        Map<String, Object> body = Map.of(
            "app_name", appName,
            "exe_path", params.getString("exe_path") != null ? params.getString("exe_path") : "",
            "backend", params.getString("backend") != null ? params.getString("backend") : "win32",
            "session_id", params.getString("session_id") != null ? params.getString("session_id") : ""
        );

        HttpResponse<String> response = post(node, "/api/windows/launch", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }

        Map<String, Object> body = Map.of(
            "session_id", sessionId,
            "username", params.getString("username") != null ? params.getString("username") : "",
            "password", params.getString("password") != null ? params.getString("password") : "",
            "timeout", params.getInteger("timeout") != null ? params.getInteger("timeout") : 30
        );

        HttpResponse<String> response = post(node, "/api/windows/login", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> selectMenu(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }
        String menuPath = params.getString("menu_path");
        if (menuPath == null) {
            throw new IllegalArgumentException("menu_path 参数不能为空");
        }

        Map<String, Object> body = Map.of(
            "session_id", sessionId,
            "menu_path", menuPath
        );

        HttpResponse<String> response = post(node, "/api/windows/menu", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> click(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }
        String titlePattern = params.getString("title_pattern");
        if (titlePattern == null) {
            throw new IllegalArgumentException("title_pattern 参数不能为空");
        }

        Map<String, Object> body = Map.of(
            "session_id", sessionId,
            "control_type", params.getString("control_type") != null ? params.getString("control_type") : "Button",
            "title_pattern", titlePattern,
            "timeout", params.getInteger("timeout") != null ? params.getInteger("timeout") : 5
        );

        HttpResponse<String> response = post(node, "/api/windows/click", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> typeKeys(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }
        String titlePattern = params.getString("title_pattern");
        if (titlePattern == null) {
            throw new IllegalArgumentException("title_pattern 参数不能为空");
        }
        String text = params.getString("text");
        if (text == null) {
            throw new IllegalArgumentException("text 参数不能为空");
        }

        Map<String, Object> body = Map.of(
            "session_id", sessionId,
            "control_type", params.getString("control_type") != null ? params.getString("control_type") : "Edit",
            "title_pattern", titlePattern,
            "text", text
        );

        HttpResponse<String> response = post(node, "/api/windows/type_keys", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getText(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }

        Map<String, Object> body = Map.of(
            "session_id", sessionId,
            "control_type", params.getString("control_type") != null ? params.getString("control_type") : "",
            "title_pattern", params.getString("title_pattern") != null ? params.getString("title_pattern") : ""
        );

        HttpResponse<String> response = post(node, "/api/windows/get_text", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> screenshot(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }

        Map<String, Object> body = Map.of(
            "session_id", sessionId,
            "output_path", params.getString("output_path") != null ? params.getString("output_path") : ""
        );

        HttpResponse<String> response = post(node, "/api/windows/screenshot", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getControls(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }

        HttpResponse<String> response = get(node, "/api/windows/controls?session_id=" + sessionId);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> close(String node, ToolParams params) throws IOException, InterruptedException {
        String sessionId = params.getString("session_id");
        if (sessionId == null) {
            throw new IllegalArgumentException("session_id 参数不能为空");
        }

        Map<String, Object> body = Map.of("session_id", sessionId);
        HttpResponse<String> response = post(node, "/api/windows/close", body);
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listSessions(String node, ToolParams params) throws IOException, InterruptedException {
        HttpResponse<String> response = get(node, "/api/windows/sessions");
        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> healthCheck(String node, ToolParams params) throws IOException, InterruptedException {
        HttpResponse<String> response = get(node, "/health");
        return parseResponse(response);
    }

    private Map<String, Object> listNodes(ToolParams params) {
        List<Map<String, Object>> nodesList = new ArrayList<>();
        for (NodeConfig n : nodes.values()) {
            Map<String, Object> nodeMap = new HashMap<>();
            nodeMap.put("node_id", n.nodeId());
            nodeMap.put("url", n.url());
            nodeMap.put("description", n.description());
            nodesList.add(nodeMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodesList);
        result.put("count", nodesList.size());
        return result;
    }

    private HttpResponse<String> post(String node, String path, Map<String, Object> body) throws IOException, InterruptedException {
        String baseUrl = nodes.get(node).url();
        String url = baseUrl + path;
        String jsonBody = objectMapper.writeValueAsString(body);
        
        HttpClient client = nodeClients.get(node);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String node, String path) throws IOException, InterruptedException {
        String baseUrl = nodes.get(node).url();
        String url = baseUrl + path;
        
        HttpClient client = nodeClients.get(node);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            throw new RuntimeException("请求失败: HTTP " + response.statusCode() + ", 响应: " + response.body());
        }
        
        return objectMapper.readValue(response.body(), Map.class);
    }

    @Override
    public void validate(ToolParams params) {
        if (params.getString("action") == null) {
            throw new IllegalArgumentException("action 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolStats getStats() { return stats; }

    private record NodeConfig(String nodeId, String url, String description) {}
}
