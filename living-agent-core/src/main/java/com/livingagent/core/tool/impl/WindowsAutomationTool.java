package com.livingagent.core.tool.impl;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.livingagent.core.websocket.WindowsAutomationClientGateway;
import com.livingagent.core.websocket.WindowsAutomationClientGateway.WinAutomationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Windows 自动化工具
 *
 * 通过 WebSocket 将通用系统控制能力（UIA、PowerShell、注册表、文件系统等）
 * 转发到桌面端执行。所有操作需要权限检查，高风险操作需要审批。
 *
 * 与现有 {@link WindowsAppTool} 的区别：
 * - WindowsAppTool：通过 HTTP 调用远程 Python 服务（pywinauto 业务化封装）
 * - WindowsAutomationTool：通过 WebSocket 调用桌面端内嵌 Python 服务（通用系统控制）
 *
 * 详细设计：docs/WINDOWS_MCP_INTEGRATION_PLAN.md §3.2、§2.1
 */
public class WindowsAutomationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WindowsAutomationTool.class);
    private static final Logger auditLog = LoggerFactory.getLogger("WINDOWS_AUTOMATION_AUDIT");

    private static final String NAME = "win_automation";
    private static final String DESCRIPTION = "控制用户本地Windows电脑的工具。当用户要求打开应用、输入文字、点击按钮、截图、执行命令等本地操作时，必须使用此工具（而非file_edit）。" +
        "支持操作:" +
        " launch_app(启动应用,如launch_app+name:'微信'), switch_app(切换窗口,如switch_app+name:'微信'), get_windows(获取窗口列表), get_active_window(获取前台窗口)," +
        " find_element(查找UI控件,返回坐标+控件信息,支持name/className/autoId搜索), snapshot(UI树+截图,用于了解当前屏幕状态)," +
        " click(type/click坐标), type(输入文字,自动支持中文), scroll(滚动), move(移动鼠标), shortcut(快捷键如Ctrl+F)," +
        " screenshot(截图), wait/wait_for(等待), shell(PowerShell命令,高风险), process_list/kill(进程), registry(注册表), filesystem(文件系统), clipboard(剪贴板)" +
        "。典型流程: launch_app打开应用 → snapshot/screenshot了解界面 → find_element定位控件 → click/type操作 → screenshot验证结果。";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "core";

    /** 操作后需要自动截图的操作集合（视觉反馈闭环） */
    private static final Set<String> VISUAL_FEEDBACK_OPERATIONS = Set.of(
        "click", "double_click", "right_click", "type", "shortcut",
        "launch_app", "switch_app", "scroll", "drag", "find_element"
    );

    /** 操作本身就包含截图，不需要额外截图 */
    private static final Set<String> SCREENSHOT_OPERATIONS = Set.of(
        "screenshot", "snapshot"
    );

    /** 高风险操作集合（需要审批） */
    private static final Set<String> HIGH_RISK_OPERATIONS = Set.of(
        "shell", "process_kill",
        "registry_set", "registry_delete",
        "filesystem_write", "filesystem_delete"
    );

    /**
     * 操作权限映射（与 docs/WINDOWS_MCP_INTEGRATION_PLAN.md §2.3 对齐）
     * key: 操作名, value: 所需最低 AccessLevel
     *
     * 注意：AccessLevel 使用 getLevel() 取数值（CHAT_ONLY=0, LIMITED=1, DEPARTMENT=2, FULL=3）
     */
    private static final Map<String, AccessLevel> OPERATION_PERMISSIONS = Map.ofEntries(
        // CHAT_ONLY (0) - 低风险只读操作
        Map.entry("wait", AccessLevel.CHAT_ONLY),
        Map.entry("wait_for", AccessLevel.CHAT_ONLY),
        Map.entry("filesystem_list", AccessLevel.CHAT_ONLY),
        Map.entry("filesystem_search", AccessLevel.CHAT_ONLY),
        Map.entry("filesystem_info", AccessLevel.CHAT_ONLY),
        Map.entry("notification", AccessLevel.CHAT_ONLY),
        Map.entry("scrape", AccessLevel.CHAT_ONLY),
        Map.entry("get_windows", AccessLevel.CHAT_ONLY),
        Map.entry("get_active_window", AccessLevel.CHAT_ONLY),

        // LIMITED (1) - 基础操作
        Map.entry("click", AccessLevel.LIMITED),
        Map.entry("type", AccessLevel.LIMITED),
        Map.entry("scroll", AccessLevel.LIMITED),
        Map.entry("move", AccessLevel.LIMITED),
        Map.entry("shortcut", AccessLevel.LIMITED),
        Map.entry("snapshot", AccessLevel.LIMITED),
        Map.entry("screenshot", AccessLevel.LIMITED),
        Map.entry("process_list", AccessLevel.LIMITED),
        Map.entry("registry_get", AccessLevel.LIMITED),
        Map.entry("registry_list", AccessLevel.LIMITED),
        Map.entry("filesystem_read", AccessLevel.LIMITED),
        Map.entry("filesystem_copy", AccessLevel.LIMITED),
        Map.entry("filesystem_move", AccessLevel.LIMITED),
        Map.entry("clipboard_get", AccessLevel.LIMITED),
        Map.entry("clipboard_set", AccessLevel.LIMITED),
        Map.entry("multi_select", AccessLevel.LIMITED),
        Map.entry("multi_edit", AccessLevel.LIMITED),
        Map.entry("vdm_switch", AccessLevel.LIMITED),
        Map.entry("vdm_create", AccessLevel.LIMITED),
        Map.entry("vdm_move_window", AccessLevel.LIMITED),
        Map.entry("launch_app", AccessLevel.LIMITED),
        Map.entry("switch_app", AccessLevel.LIMITED),
        Map.entry("resize_app", AccessLevel.LIMITED),
        Map.entry("find_element", AccessLevel.LIMITED),

        // FULL (3) - 高风险操作（需审批）
        Map.entry("shell", AccessLevel.FULL),
        Map.entry("process_kill", AccessLevel.FULL),
        Map.entry("registry_set", AccessLevel.FULL),
        Map.entry("registry_delete", AccessLevel.FULL),
        Map.entry("filesystem_write", AccessLevel.FULL),
        Map.entry("filesystem_delete", AccessLevel.FULL)
    );

    private final WindowsAutomationClientGateway clientGateway;
    private ToolStats stats = ToolStats.empty(NAME);

    public WindowsAutomationTool(WindowsAutomationClientGateway clientGateway) {
        this.clientGateway = clientGateway;
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
            .parameter("operation", "string",
                "操作类型: launch_app(启动应用), switch_app(切换窗口), get_windows(窗口列表), get_active_window(前台窗口), " +
                "find_element(查找UI控件,需指定name/className/autoId), snapshot(UI树+截图), " +
                "click, type(输入文字,自动支持中文), scroll, move, shortcut, screenshot, " +
                "wait, wait_for, shell(高风险), process_list, process_kill, " +
                "registry_get/set/delete/list, filesystem_read/write/copy/move/delete/list/search/info, " +
                "clipboard_get/set, notification, scrape, vdm_switch/create/move_window", true)
            .parameter("args", "object",
                "操作参数。各操作不同: " +
                "launch_app={name:'微信'}, switch_app={name:'微信'}, " +
                "find_element={name:'搜索'}, find_element={className:'Edit'}, find_element={autoId:'searchBox'}, " +
                "click={x:100,y:200}, type={text:'你好'}, shortcut={keys:'Ctrl+F'}, " +
                "shell={command:'notepad.exe'}, screenshot={}, snapshot={}, " +
                "wait={seconds:2}, wait_for={condition:'window_appear',window_name:'微信'}", false)
            .parameter("clientId", "string", "目标客户端ID（可选，默认使用 context 中的 clientId）", false)
            .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of(
            "uia_control", "powershell", "registry", "filesystem",
            "process_management", "clipboard", "screenshot",
            "virtual_desktop", "notification"
        );
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();

        String operation = params.getString("operation");
        if (operation == null || operation.isBlank()) {
            return ToolResult.failure("operation 参数不能为空");
        }

        // 解析参数
        @SuppressWarnings("unchecked")
        Map<String, Object> args = params.<Map<String, Object>>get("args");
        if (args == null) {
            args = new HashMap<>();
        }

        // 确定 clientId：优先参数，其次 context
        String clientId = params.getString("clientId");
        if (clientId == null || clientId.isBlank()) {
            clientId = context != null ? context.clientId() : null;
        }

        // 用户权限级别（ToolContext.accessLevel 为 Integer，null 视为 CHAT_ONLY=0）
        int userLevel = (context != null && context.accessLevel() != null)
            ? context.accessLevel() : AccessLevel.CHAT_ONLY.getLevel();
        String employeeCode = context != null ? context.employeeCode() : "unknown";

        log.info("[WindowsAutomationTool] operation={}, clientId={}, userLevel={}, employee={}",
            operation, clientId, userLevel, employeeCode);

        // 1. 检查操作是否存在
        AccessLevel requiredLevel = OPERATION_PERMISSIONS.get(operation);
        if (requiredLevel == null) {
            auditLog.warn("[WinAutomation] 未知操作: employee={}, operation={}", employeeCode, operation);
            return ToolResult.failure("未知操作: " + operation);
        }

        // 2. 权限检查（检查先于路由）
        if (userLevel < requiredLevel.getLevel()) {
            log.warn("[WindowsAutomationTool] 权限不足: operation={}, required={}, actual={}",
                operation, requiredLevel.getLevel(), userLevel);
            auditLog.warn("[WinAutomation] 权限拒绝: employee={}, operation={}, required={}, actual={}",
                employeeCode, operation, requiredLevel.getLevel(), userLevel);
            return ToolResult.failure("权限不足：需要 " + requiredLevel.getDescription() +
                " 权限才能执行 " + operation);
        }

        // 3. 高风险操作审计（工具执行审批已移除，由 BrainBoundaryEnforcer 四重校验等价保障）
        if (HIGH_RISK_OPERATIONS.contains(operation)) {
            auditLog.info("[WinAutomation] 高风险操作: employee={}, operation={}", employeeCode, operation);
        }

        // 4. 检查客户端连接
        if (clientId == null || clientId.isBlank()) {
            return ToolResult.failure("clientId 不能为空，请确保客户端已连接");
        }
        if (!clientGateway.isClientOnline(clientId)) {
            log.warn("[WindowsAutomationTool] 客户端未连接: clientId={}", clientId);
            return ToolResult.failure("客户端未连接 WebSocket: " + clientId);
        }

        // 5. 发送操作到客户端并等待响应
        try {
            CompletableFuture<WinAutomationResponse> future =
                clientGateway.sendOperation(clientId, operation, args);

            WinAutomationResponse response = future.get(30, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;

            if (response.success()) {
                auditLog.info("[WinAutomation] 操作成功: employee={}, clientId={}, operation={}, duration={}ms",
                    employeeCode, clientId, operation, duration);
                stats = stats.recordCall(true, duration);

                // 视觉反馈闭环：对关键操作自动截图，让 Brain 能看到操作后的屏幕状态
                Object result = response.result();
                if (VISUAL_FEEDBACK_OPERATIONS.contains(operation) && clientId != null
                    && clientGateway.isClientOnline(clientId)) {
                    try {
                        CompletableFuture<WinAutomationResponse> screenshotFuture =
                            clientGateway.sendOperation(clientId, "screenshot", Map.of());
                        WinAutomationResponse screenshotResp = screenshotFuture.get(10, TimeUnit.SECONDS);
                        if (screenshotResp.success() && screenshotResp.result() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> resultMap = new HashMap<>();
                            if (result instanceof Map) {
                                resultMap.putAll((Map<String, Object>) result);
                            } else {
                                resultMap.put("result", result);
                            }
                            resultMap.put("screenshot_after", ((Map<String, Object>) screenshotResp.result()).get("screenshot"));
                            result = resultMap;
                        }
                    } catch (Exception e) {
                        log.debug("[WindowsAutomationTool] Auto-screenshot failed (non-critical): {}", e.getMessage());
                    }
                }

                return ToolResult.success(result);
            } else {
                auditLog.warn("[WinAutomation] 操作失败: employee={}, clientId={}, operation={}, error={}, duration={}ms",
                    employeeCode, clientId, operation, response.error(), duration);
                stats = stats.recordCall(false, duration);
                return ToolResult.failure("操作失败: " + response.error());
            }

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[WindowsAutomationTool] 操作超时: operation={}, clientId={}", operation, clientId);
            auditLog.error("[WinAutomation] 操作超时: employee={}, clientId={}, operation={}, duration={}ms",
                employeeCode, clientId, operation, duration);
            stats = stats.recordCall(false, duration);
            return ToolResult.failure("操作超时（30秒）");
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[WindowsAutomationTool] 操作异常: operation={}", operation, e);
            auditLog.error("[WinAutomation] 操作异常: employee={}, clientId={}, operation={}, error={}, duration={}ms",
                employeeCode, clientId, operation, e.getMessage(), duration);
            stats = stats.recordCall(false, duration);
            return ToolResult.failure("操作异常: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        if (params.getString("operation") == null) {
            throw new IllegalArgumentException("operation 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() { return stats; }
}
