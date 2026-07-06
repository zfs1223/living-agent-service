# Windows 自动化能力增强方案

> 目标：借鉴 Windows-MCP 的控制技术栈，增强 living-agent-service 的 Windows 自动化能力，统一认证管理，逐步完善 pywinauto 业务化封装。

---

## 一、背景与目标

### 1.1 当前架构问题

| 问题 | 影响 |
|------|------|
| server.py (pywinauto) 能力有限 | 仅支持应用启动、登录、菜单操作，无 PowerShell/注册表/文件系统等通用能力 |
| 业务化封装不完善 | login/menu_select 等操作依赖 pywinauto，缺乏 UIA 深度控制 |
| 缺乏通用系统控制 | 无法执行 PowerShell、无法操作注册表、无法管理进程 |

### 1.2 目标架构

**核心定位**：不是"集成 Windows-MCP"，而是**借鉴其控制技术栈，增强 living-agent-service 的 Windows 自动化能力**。

```
living-agent-service Windows 自动化能力体系
│
├── 业务化封装层（pywinauto/server.py，现有）
│   ├── login（财务软件登录）
│   ├── menu_select（菜单操作）
│   ├── get_text（文本获取）
│   ├── close（关闭应用）
│   └── 会话管理（active_sessions）
│   └── 自动注册到后端 + 心跳
│
├── 通用系统控制层（新增，借鉴 Windows-MCP 技术栈）
│   ├── UIA 控件操作（click, type, snapshot, screenshot）
│   ├── PowerShell 执行（shell）
│   ├── 注册表操作（registry_get/set/delete/list）
│   ├── 文件系统操作（filesystem_read/write/copy/move/delete/list）
│   ├── 进程管理（process_list/kill）
│   ├── 剪贴板操作（clipboard_get/set）
│   ├── 输入模拟（click, type, scroll, move, shortcut）
│   ├── 条件等待（wait_for）
│   ├── 批量操作（multi_select, multi_edit）
│   ├── 虚拟桌面管理（vdm）
│   └── Toast 通知（notification）
│
└── 统一认证与权限层（living-agent-service 后端）
    ├── UnifiedAuthService: Token 验证
    ├── AccessGateService: 权限检查（CHAT_ONLY/LIMITED/DEPARTMENT/FULL）
    ├── ApprovalManager: 高风险操作审批
    ├── ClientOperationAuditLog: 审计日志
    └── WebSocketSessionRegistry: clientId → Session 映射

```

### 1.3 核心原则

1. **借鉴控制技术栈**：学习 Windows-MCP 的 UIA、PowerShell、注册表等实现方式，增强自身能力
2. **统一认证**：所有操作通过 living-agent-service 后端认证和权限检查
3. **权限隔离**：不同 AccessLevel 可访问的操作不同
4. **审计追踪**：所有操作记录到 `client_operation_audit_log` 表
5. **高风险审批**：Shell、Registry 等高风险操作需要 ApprovalManager 审批
6. **逐步完善业务化封装**：利用通用控制能力，增强 pywinauto 的业务化操作效率

---

## 二、通用系统控制能力清单与权限分级

### 2.1 能力清单（借鉴 Windows-MCP 技术栈）

| 操作类型 | 功能 | 风险等级 | 权限要求 | 技术栈 |
|----------|------|----------|----------|--------|
| **UIA 控件操作** | | | | |
| `click` | 鼠标点击（坐标/控件） | 中 | DEPARTMENT | UIAutomation API |
| `type` | 键盘输入 | 中 | DEPARTMENT | UIAutomation API |
| `scroll` | 滚动操作 | 低 | DEPARTMENT | UIAutomation API |
| `move` | 鼠标移动 | 低 | DEPARTMENT | user32.dll |
| `shortcut` | 快捷键组合 | 中 | DEPARTMENT | SendInput |
| `snapshot` | UI树+截图 | 低 | DEPARTMENT | UIAutomation + dxcam |
| `screenshot` | 快速截图 | 低 | DEPARTMENT | dxcam/mss/pillow |
| **条件等待** | | | | |
| `wait` | 固定时间等待 | 低 | CHAT_ONLY | - |
| `wait_for` | 条件等待（5种） | 低 | CHAT_ONLY | UIAutomation |
| **PowerShell** | | | | |
| `shell` | PowerShell 命令 | **高** | FULL + 审批 | pwsh/powershell |
| **进程管理** | | | | |
| `process_list` | 列出进程 | 低 | DEPARTMENT | psutil |
| `process_kill` | 终止进程 | **高** | FULL + 审批 | psutil/taskkill |
| **注册表** | | | | |
| `registry_get` | 读取注册表 | 中 | DEPARTMENT | PowerShell cmdlets |
| `registry_set` | 写入注册表 | **高** | FULL + 审批 | PowerShell cmdlets |
| `registry_delete` | 删除注册表 | **高** | FULL + 审批 | PowerShell cmdlets |
| `registry_list` | 列出注册表键 | 低 | DEPARTMENT | PowerShell cmdlets |
| **文件系统** | | | | |
| `filesystem_read` | 读取文件 | 中 | DEPARTMENT | Python fs |
| `filesystem_write` | 写入文件 | **高** | FULL + 审批 | Python fs |
| `filesystem_copy` | 复制文件 | 中 | DEPARTMENT | Python fs |
| `filesystem_move` | 移动文件 | 中 | DEPARTMENT | Python fs |
| `filesystem_delete` | 删除文件 | **高** | FULL + 审批 | Python fs |
| `filesystem_list` | 列出目录 | 低 | CHAT_ONLY | Python fs |
| `filesystem_search` | 搜索文件 | 低 | CHAT_ONLY | Python fs |
| `filesystem_info` | 文件信息 | 低 | CHAT_ONLY | Python fs |
| **剪贴板** | | | | |
| `clipboard_get` | 读取剪贴板 | 低 | DEPARTMENT | win32clipboard |
| `clipboard_set` | 设置剪贴板 | 中 | DEPARTMENT | win32clipboard |
| **其他** | | | | |
| `notification` | Toast 通知 | 低 | CHAT_ONLY | WinRT Toast |
| `scrape` | 网页抓取 | 低 | CHAT_ONLY | HTTP/DOM |
| `multi_select` | 批量点击 | 中 | DEPARTMENT | 组合操作 |
| `multi_edit` | 批量编辑 | 中 | DEPARTMENT | 组合操作 |
| **虚拟桌面** | | | | |
| `vdm_switch` | 切换虚拟桌面 | 低 | DEPARTMENT | COM IVirtualDesktop |
| `vdm_create` | 创建虚拟桌面 | 中 | DEPARTMENT | COM IVirtualDesktop |
| `vdm_move_window` | 移动窗口到桌面 | 中 | DEPARTMENT | COM IVirtualDesktop |

### 2.2 与 pywinauto 业务化封装的协同

| 业务场景 | pywinauto 封装 | 通用控制增强 | 协同效果 |
|----------|---------------|-------------|----------|
| **财务软件登录** | `login`（用户名/密码模式匹配） | `snapshot`（UI树定位控件） | 更精准的控件定位 |
| **菜单操作** | `menu_select`（菜单路径） | `wait_for`（等待菜单出现） | 更可靠的菜单等待 |
| **数据录入** | `type_keys`（控件输入） | `clipboard_set`（批量粘贴） | 批量数据更高效 |
| **报表导出** | `get_text`（文本获取） | `filesystem_*`（文件操作） | 完整的导出流程 |
| **异常处理** | `close`（关闭应用） | `process_kill`（强制终止） | 更可靠的异常恢复 |

### 2.3 权限分级

| AccessLevel | 可访问操作 | 说明 |
|-------------|-----------|------|
| `CHAT_ONLY` (0) | wait, wait_for, filesystem_list/search/info, notification, scrape | 仅低风险只读操作 |
| `LIMITED` (1) | + click, type, scroll, move, shortcut, snapshot, screenshot, process_list, registry_get/list, filesystem_read/copy/move, clipboard | 基础操作，无高风险 |
| `DEPARTMENT` (2) | + registry_get, filesystem_read, clipboard_set, multi_*, vdm_* | 本部门完整操作 |
| `FULL` (3) | 所有操作 + 高风险操作（需审批） | 完整权限，高风险需审批 |

### 2.4 高风险操作（需要审批）

| 操作 | 审批原因 |
|------|----------|
| `shell` | 可执行任意 PowerShell 命令，潜在系统破坏 |
| `process_kill` | 可终止关键进程，导致系统不稳定 |
| `registry_set/delete` | 可修改/删除注册表，影响系统配置 |
| `filesystem_write/delete` | 可写入/删除文件，潜在数据丢失 |

---

## 三、实现方案

### 3.1 桌面端：内嵌 Windows Automation 服务

#### 3.1.1 服务管理器

**新建文件**：`living-agent-desktop/src/main/win-automation-service.ts`

```typescript
/**
 * Windows 自动化服务管理器
 * 
 * 职责：
 * 1. 启动 Python 子进程（内嵌 UIAutomation/PowerShell/注册表等控制能力）
 * 2. 通过 stdin/stdout 进行协议通信
 * 3. 处理响应并回调
 * 4. 不使用独立认证，所有请求来自后端认证后的转发
 * 
 * 技术栈借鉴 Windows-MCP：
 * - UIAutomation API (comtypes)
 * - PowerShell 执行
 * - 注册表操作 (PowerShell cmdlets)
 * - 文件系统操作 (Python fs)
 * - 进程管理 (psutil)
 * - 截图 (dxcam/mss)
 */

import { spawn, ChildProcess } from 'child_process';
import path from 'path';
import { app } from 'electron';

interface AutomationRequest {
  id: number;
  operation: string;
  args: Record<string, any>;
}

interface AutomationResponse {
  id: number;
  success: boolean;
  result?: any;
  error?: string;
}

export class WindowsAutomationService {
  private automationProcess: ChildProcess | null = null;
  private pendingRequests: Map<number, { resolve: Function; reject: Function }> = new Map();
  private responseBuffer: string = '';
  
  /**
   * 启动 Windows 自动化服务子进程
   */
  async start(): Promise<void> {
    // Python 脚本路径（打包后在 resources 目录）
    const scriptPath = path.join(process.resourcesPath, 'win-automation', 'service.py');
    const pythonExe = path.join(process.resourcesPath, 'python', 'python.exe');
    
    this.automationProcess = spawn(pythonExe, [scriptPath], {
      cwd: path.dirname(scriptPath),
      stdio: ['pipe', 'pipe', 'pipe'],
      env: {
        ...process.env,
        PYTHONPATH: path.join(process.resourcesPath, 'win-automation'),
      }
    });
    
    // 处理 stdout（响应）
    this.automationProcess.stdout?.on('data', (data: Buffer) => {
      this.handleStdout(data);
    });
    
    // 处理 stderr（日志）
    this.automationProcess.stderr?.on('data', (data: Buffer) => {
      console.log('[WinAutomation]', data.toString());
    });
    
    // 处理进程退出
    this.automationProcess.on('close', (code) => {
      console.log('[WinAutomation] Process exited with code', code);
      this.automationProcess = null;
      this.pendingRequests.forEach(({ reject }) => reject(new Error('Automation process exited')));
      this.pendingRequests.clear();
    });
    
    console.log('[WinAutomation] Windows automation service started');
  }
  
  /**
   * 处理 stdout 数据
   */
  private handleStdout(data: Buffer): void {
    this.responseBuffer += data.toString();
    
    const lines = this.responseBuffer.split('\n');
    this.responseBuffer = lines.pop() || '';
    
    for (const line of lines) {
      if (!line.trim()) continue;
      
      try {
        const response = JSON.parse(line) as AutomationResponse;
        const pending = this.pendingRequests.get(response.id);
        if (pending) {
          this.pendingRequests.delete(response.id);
          if (response.success) {
            pending.resolve(response.result);
          } else {
            pending.reject(new Error(response.error || 'Operation failed'));
          }
        }
      } catch (e) {
        console.error('[WinAutomation] Failed to parse response:', line, e);
      }
    }
  }
  
  /**
   * 执行自动化操作
   */
  async execute(operation: string, args: Record<string, any>): Promise<any> {
    if (!this.automationProcess || !this.automationProcess.stdin) {
      throw new Error('Automation service not started');
    }
    
    const id = Date.now();
    const request = { id, operation, args };
    
    return new Promise((resolve, reject) => {
      this.pendingRequests.set(id, { resolve, reject });
      this.automationProcess.stdin.write(JSON.stringify(request) + '\n');
      
      // 超时处理（30秒）
      setTimeout(() => {
        if (this.pendingRequests.has(id)) {
          this.pendingRequests.delete(id);
          reject(new Error('Operation timeout'));
        }
      }, 30000);
    });
  }
  
  /**
   * 停止服务
   */
  stop(): void {
    if (this.automationProcess) {
      this.automationProcess.kill('SIGTERM');
      this.automationProcess = null;
    }
  }
  
  /**
   * 检查服务是否运行
   */
  isRunning(): boolean {
    return this.automationProcess !== null && !this.automationProcess.killed;
  }
}

// 单例导出
export const winAutomationService = new WindowsAutomationService();
```

#### 3.1.2 IPC Handler

**修改文件**：`living-agent-desktop/src/main/ipc.ts`

```typescript
// 新增 Windows 自动化相关 IPC handler
ipcMain.handle('win-automation:start', async () => {
  await winAutomationService.start();
  return { success: true };
});

ipcMain.handle('win-automation:stop', async () => {
  winAutomationService.stop();
  return { success: true };
});

ipcMain.handle('win-automation:execute', async (_event, operation: string, args: Record<string, any>) => {
  try {
    const result = await winAutomationService.execute(operation, args);
    return { success: true, result };
  } catch (e: any) {
    return { success: false, error: e.message };
  }
});

ipcMain.handle('win-automation:status', async () => {
  return { running: winAutomationService.isRunning() };
});
```

#### 3.1.3 WebSocket 消息处理

**修改文件**：`living-agent-desktop/src/main/ws-client.ts`

```typescript
// 在 WSClient 类中添加 WIN_AUTOMATION_CALL 消息处理
private handleWinAutomationCall(data: any): void {
  const { id, operation, args } = data;
  
  winAutomationService.execute(operation, args)
    .then(result => {
      // 通过 WebSocket 发送响应回后端
      this.send({
        type: 'WIN_AUTOMATION_RESPONSE',
        data: { id, success: true, result }
      });
    })
    .catch(error => {
      this.send({
        type: 'WIN_AUTOMATION_RESPONSE',
        data: { id, success: false, error: error.message }
      });
    });
}

// 在 onMessage 中添加处理
private onMessage(data: Buffer): void {
  const msg = JSON.parse(data.toString());
  
  switch (msg.type) {
    case 'WIN_AUTOMATION_CALL':
      this.handleWinAutomationCall(msg.data);
      break;
    // ... 其他消息类型
  }
}
```

#### 3.1.4 启动流程集成

**修改文件**：`living-agent-desktop/src/main/index.ts`

```typescript
// 在 app.whenReady() 中启动 Windows 自动化服务
app.whenReady().then(async () => {
  // ... 现有启动流程
  
  // 启动 Windows 自动化服务
  await winAutomationService.start();
  console.log('[LivingAgent] Windows automation service started');
  
  // ... 创建窗口等
});

// 在 app.on('will-quit') 中停止服务
app.on('will-quit', () => {
  winAutomationService.stop();
});
```

### 3.2 后端：WindowsAutomationTool 实现

#### 3.2.1 工具定义

**新建文件**：`living-agent-core/src/main/java/com/livingagent/core/tool/impl/WindowsAutomationTool.java`

```java
package com.livingagent.core.tool.impl;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.approval.ApprovalManager;
import com.livingagent.core.security.approval.ApprovalRequest;
import com.livingagent.core.security.approval.ApprovalStatus;
import com.livingagent.core.tool.*;
import com.livingagent.core.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Windows 自动化工具
 * 
 * 调用通用系统控制能力（UIA、PowerShell、注册表、文件系统等），
 * 通过 WebSocket 转发到桌面端执行。
 * 所有操作需要权限检查，高风险操作需要审批。
 */
@Slf4j
@Component
@Tool(name = "win_automation", description = "通用Windows系统操作")
@RequiredArgsConstructor
public class WindowsAutomationTool implements Tool {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ApprovalManager approvalManager;
    
    // 高风险操作列表（需要审批）
    private static final Set<String> HIGH_RISK_OPERATIONS = Set.of(
        "shell", "process_kill", "registry_set", "registry_delete",
        "filesystem_write", "filesystem_delete"
    );
    
    // 操作权限映射
    private static final Map<String, AccessLevel> OPERATION_PERMISSIONS = Map.ofEntries(
        // CHAT_ONLY
        Map.entry("wait", AccessLevel.CHAT_ONLY),
        Map.entry("wait_for", AccessLevel.CHAT_ONLY),
        Map.entry("filesystem_list", AccessLevel.CHAT_ONLY),
        Map.entry("filesystem_search", AccessLevel.CHAT_ONLY),
        Map.entry("filesystem_info", AccessLevel.CHAT_ONLY),
        Map.entry("notification", AccessLevel.CHAT_ONLY),
        Map.entry("scrape", AccessLevel.CHAT_ONLY),
        
        // LIMITED
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
        
        // FULL (高风险操作)
        Map.entry("shell", AccessLevel.FULL),
        Map.entry("process_kill", AccessLevel.FULL),
        Map.entry("registry_set", AccessLevel.FULL),
        Map.entry("registry_delete", AccessLevel.FULL),
        Map.entry("filesystem_write", AccessLevel.FULL),
        Map.entry("filesystem_delete", AccessLevel.FULL)
    );
    
    // 等待响应的 Future 映射
    private final Map<Long, CompletableFuture<ToolResult>> pendingResponses = new ConcurrentHashMap<>();
    
    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        String operation = params.get("operation", String.class);
        Map<String, Object> args = params.get("args", Map.class, new HashMap<>());
        String clientId = context.clientId();
        AccessLevel userLevel = context.securityPolicy().getAccessLevel();
        
        log.info("[WindowsAutomationTool] Executing operation={}, clientId={}, userLevel={}", 
            operation, clientId, userLevel);
        
        // 1. 检查操作是否存在
        if (!OPERATION_PERMISSIONS.containsKey(operation)) {
            return ToolResult.error("Unknown operation: " + operation);
        }
        
        // 2. 权限检查
        AccessLevel requiredLevel = OPERATION_PERMISSIONS.get(operation);
        if (userLevel.getValue() < requiredLevel.getValue()) {
            log.warn("[WindowsAutomationTool] Permission denied: operation={}, required={}, actual={}", 
                operation, requiredLevel, userLevel);
            return ToolResult.error("权限不足：需要 " + requiredLevel + " 权限才能执行 " + operation);
        }
        
        // 3. 高风险操作审批
        if (HIGH_RISK_OPERATIONS.contains(operation)) {
            ApprovalRequest approval = approvalManager.requestApproval(
                context.employeeId(),
                "win_automation:" + operation,
                "执行高风险 Windows 操作: " + operation,
                args
            );
            
            if (approval.getStatus() != ApprovalStatus.APPROVED) {
                log.warn("[WindowsAutomationTool] High-risk operation requires approval: operation={}, approvalId={}", 
                    operation, approval.getId());
                return ToolResult.error("操作需要审批，审批ID: " + approval.getId());
            }
            
            log.info("[WindowsAutomationTool] High-risk operation approved: operation={}, approvalId={}", 
                operation, approval.getId());
        }
        
        // 4. 查找 WebSocket Session
        WebSocketSession session = sessionRegistry.findByClientId(clientId);
        if (session == null || !session.isOpen()) {
            log.warn("[WindowsAutomationTool] No WebSocket session for clientId={}", clientId);
            return ToolResult.error("客户端未连接 WebSocket");
        }
        
        // 5. 发送 WIN_AUTOMATION_CALL 消息到桌面端
        long requestId = System.currentTimeMillis();
        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        pendingResponses.put(requestId, future);
        
        try {
            WinAutomationCallMessage message = new WinAutomationCallMessage(requestId, operation, args);
            session.sendMessage(new TextMessage(message.toJson()));
            log.info("[WindowsAutomationTool] Sent WIN_AUTOMATION_CALL: requestId={}, operation={}", 
                requestId, operation);
            
            // 6. 等待响应（超时30秒）
            ToolResult result = future.get(30, TimeUnit.SECONDS);
            return result;
            
        } catch (TimeoutException e) {
            pendingResponses.remove(requestId);
            log.error("[WindowsAutomationTool] Timeout waiting for response: requestId={}", requestId);
            return ToolResult.error("操作超时");
        } catch (Exception e) {
            pendingResponses.remove(requestId);
            log.error("[WindowsAutomationTool] Error executing operation", e);
            return ToolResult.error("操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理来自桌面端的 WIN_AUTOMATION_RESPONSE
     */
    public void handleResponse(Long requestId, boolean success, Object result, String error) {
        CompletableFuture<ToolResult> future = pendingResponses.remove(requestId);
        if (future == null) {
            log.warn("[WindowsAutomationTool] No pending request for requestId={}", requestId);
            return;
        }
        
        if (success) {
            future.complete(ToolResult.success(result));
        } else {
            future.complete(ToolResult.error(error));
        }
    }
    
    // WIN_AUTOMATION_CALL 消息结构
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class WinAutomationCallMessage {
        private Long id;
        private String operation;
        private Map<String, Object> args;
        
        public String toJson() {
            return "{\"type\":\"WIN_AUTOMATION_CALL\",\"data\":{\"id\":" + id + 
                ",\"operation\":\"" + operation + "\",\"args\":" + 
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args) + "}}";
        }
    }
}
```

#### 3.2.2 WebSocket Session Registry

**新建文件**：`living-agent-core/src/main/java/com/livingagent/core/websocket/WebSocketSessionRegistry.java`

```java
package com.livingagent.core.websocket;

import org.springframework.web.socket.WebSocketSession;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Session 注册表
 * 
 * 维护 clientId → WebSocketSession 的映射，供 WindowsAutomationTool 查找目标客户端。
 */
public class WebSocketSessionRegistry {
    
    // clientId → WebSocketSession
    private final ConcurrentHashMap<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();
    
    public void register(String clientId, WebSocketSession session) {
        clientSessions.put(clientId, session);
    }
    
    public void unregister(String clientId) {
        clientSessions.remove(clientId);
    }
    
    public WebSocketSession findByClientId(String clientId) {
        return clientSessions.get(clientId);
    }
    
    public boolean isOnline(String clientId) {
        WebSocketSession session = clientSessions.get(clientId);
        return session != null && session.isOpen();
    }
}
```

#### 3.2.3 WebSocket Handler 集成

**修改文件**：`living-agent-gateway/.../AgentWebSocketHandler.java`

```java
// 在 afterConnectionEstablished 中注册 clientId
@Override
public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String clientId = extractClientId(session);
    if (clientId != null) {
        sessionRegistry.register(clientId, session);
    }
    // ... 现有逻辑
}

// 在 afterConnectionClosed 中移除 clientId
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    String clientId = extractClientId(session);
    if (clientId != null) {
        sessionRegistry.unregister(clientId);
    }
    // ... 现有逻辑
}

// 在 handleTextMessage 中处理 WIN_AUTOMATION_RESPONSE
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String payload = message.getPayload();
    Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
    String type = (String) msg.get("type");
    
    if ("WIN_AUTOMATION_RESPONSE".equals(type)) {
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        Long requestId = ((Number) data.get("id")).longValue();
        Boolean success = (Boolean) data.get("success");
        Object result = data.get("result");
        String error = (String) data.get("error");
        
        windowsAutomationTool.handleResponse(requestId, success, result, error);
        return;
    }
    
    // ... 现有消息处理
}
```

### 3.3 Python 自动化服务（借鉴 Windows-MCP 技术栈）

**新建文件**：`living-agent-desktop/resources/win-automation/service.py`

```python
"""
Windows 自动化服务

借鉴 Windows-MCP 技术栈实现：
- UIAutomation API (comtypes)
- PowerShell 执行
- 注册表操作 (PowerShell cmdlets)
- 文件系统操作 (Python fs)
- 进程管理 (psutil)
- 截图 (dxcam/mss)
- 剪贴板 (win32clipboard)
- 虚拟桌面 (COM IVirtualDesktop)

不使用独立认证，所有请求来自 Electron 主进程转发。
"""

import sys
import json
import subprocess
import psutil
import win32clipboard
import win32con
from pathlib import Path
from typing import Any, Dict

# UIAutomation (借鉴 Windows-MCP)
try:
    import comtypes.client
    from comtypes.gen.UIAutomationClient import IUIAutomation
    UIA_AVAILABLE = True
except ImportError:
    UIA_AVAILABLE = False

# 截图后端
try:
    import dxcam
    DXCAM_AVAILABLE = True
except ImportError:
    DXCAM_AVAILABLE = False

try:
    import mss
    MSS_AVAILABLE = True
except ImportError:
    MSS_AVAILABLE = False


class WindowsAutomationService:
    """Windows 自动化服务"""
    
    def __init__(self):
        self.uia_client = None
        if UIA_AVAILABLE:
            self._init_uia()
    
    def _init_uia(self):
        """初始化 UIAutomation"""
        self.uia_client = comtypes.client.CoCreateInstance(
            comtypes.gen.UIAutomationClient.CUIAutomation._com_interfaces_[0],
            comtypes.gen.UIAutomationClient.CUIAutomation,
            comtypes.gen.UIAutomationClient.IUIAutomation
        )
    
    def execute(self, operation: str, args: Dict[str, Any]) -> Dict[str, Any]:
        """执行自动化操作"""
        try:
            result = self._dispatch(operation, args)
            return {"success": True, "result": result}
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    def _dispatch(self, operation: str, args: Dict[str, Any]) -> Any:
        """操作分发"""
        handlers = {
            # UIA 控件操作
            "click": self._handle_click,
            "type": self._handle_type,
            "snapshot": self._handle_snapshot,
            "screenshot": self._handle_screenshot,
            
            # 条件等待
            "wait": self._handle_wait,
            "wait_for": self._handle_wait_for,
            
            # PowerShell
            "shell": self._handle_shell,
            
            # 进程管理
            "process_list": self._handle_process_list,
            "process_kill": self._handle_process_kill,
            
            # 注册表
            "registry_get": self._handle_registry_get,
            "registry_set": self._handle_registry_set,
            "registry_delete": self._handle_registry_delete,
            "registry_list": self._handle_registry_list,
            
            # 文件系统
            "filesystem_read": self._handle_fs_read,
            "filesystem_write": self._handle_fs_write,
            "filesystem_copy": self._handle_fs_copy,
            "filesystem_move": self._handle_fs_move,
            "filesystem_delete": self._handle_fs_delete,
            "filesystem_list": self._handle_fs_list,
            "filesystem_search": self._handle_fs_search,
            "filesystem_info": self._handle_fs_info,
            
            # 剪贴板
            "clipboard_get": self._handle_clipboard_get,
            "clipboard_set": self._handle_clipboard_set,
            
            # 其他
            "notification": self._handle_notification,
            "scrape": self._handle_scrape,
            
            # 虚拟桌面
            "vdm_switch": self._handle_vdm_switch,
            "vdm_create": self._handle_vdm_create,
            "vdm_move_window": self._handle_vdm_move_window,
        }
        
        handler = handlers.get(operation)
        if not handler:
            raise ValueError(f"Unknown operation: {operation}")
        
        return handler(args)
    
    # === UIA 控件操作 ===
    
    def _handle_click(self, args: Dict) -> Dict:
        """鼠标点击"""
        x = args.get("x")
        y = args.get("y")
        # ... 实现点击逻辑
        return {"clicked": True, "position": (x, y)}
    
    def _handle_type(self, args: Dict) -> Dict:
        """键盘输入"""
        text = args.get("text")
        # ... 实现输入逻辑
        return {"typed": text}
    
    def _handle_snapshot(self, args: Dict) -> Dict:
        """UI树+截图"""
        # ... 实现 UI 树遍历 + 截图
        return {"ui_tree": [], "screenshot": None}
    
    def _handle_screenshot(self, args: Dict) -> Dict:
        """快速截图"""
        if DXCAM_AVAILABLE:
            camera = dxcam.create()
            frame = camera.grab()
            return {"screenshot": frame}
        elif MSS_AVAILABLE:
            with mss.mss() as sct:
                monitor = sct.monitors[1]
                img = sct.grab(monitor)
                return {"screenshot": img}
        else:
            raise RuntimeError("No screenshot backend available")
    
    # === PowerShell ===
    
    def _handle_shell(self, args: Dict) -> Dict:
        """PowerShell 命令"""
        command = args.get("command")
        timeout = args.get("timeout", 30)
        
        # 使用 base64 编码 UTF-16LE（借鉴 Windows-MCP）
        encoded = subprocess.list2cmdline(["powershell", "-EncodedCommand", 
            subprocess._encode_command(command)])
        
        result = subprocess.run(
            encoded,
            capture_output=True,
            text=True,
            timeout=timeout,
            shell=True
        )
        
        return {
            "stdout": result.stdout,
            "stderr": result.stderr,
            "exit_code": result.returncode
        }
    
    # === 进程管理 ===
    
    def _handle_process_list(self, args: Dict) -> Dict:
        """列出进程"""
        sort_by = args.get("sort_by", "name")
        limit = args.get("limit", 50)
        
        processes = []
        for proc in psutil.process_iter(['pid', 'name', 'memory_info', 'cpu_percent']):
            processes.append({
                "pid": proc.info['pid'],
                "name": proc.info['name'],
                "memory": proc.info['memory_info'].rss,
                "cpu": proc.info['cpu_percent']
            })
        
        # 排序
        processes.sort(key=lambda p: p.get(sort_by, 0), reverse=True)
        
        return {"processes": processes[:limit]}
    
    def _handle_process_kill(self, args: Dict) -> Dict:
        """终止进程"""
        pid = args.get("pid")
        name = args.get("name")
        force = args.get("force", False)
        
        if pid:
            proc = psutil.Process(pid)
            if force:
                proc.kill()
            else:
                proc.terminate()
            return {"killed": True, "pid": pid}
        elif name:
            for proc in psutil.process_iter(['pid', 'name']):
                if proc.info['name'] == name:
                    if force:
                        proc.kill()
                    else:
                        proc.terminate()
            return {"killed": True, "name": name}
        
        raise ValueError("pid or name required")
    
    # === 注册表 ===
    
    def _handle_registry_get(self, args: Dict) -> Dict:
        """读取注册表"""
        path = args.get("path")
        value = args.get("value")
        
        result = subprocess.run(
            ["powershell", "-Command", f"Get-ItemProperty -Path '{path}' -Name '{value}'"],
            capture_output=True,
            text=True
        )
        
        return {"value": result.stdout.strip()}
    
    # ... 其他操作实现
    
    def run(self):
        """主循环：从 stdin 读取请求，执行，返回响应"""
        for line in sys.stdin:
            if not line.strip():
                continue
            
            try:
                request = json.loads(line)
                response = self.execute(request["operation"], request["args"])
                response["id"] = request["id"]
                print(json.dumps(response), flush=True)
            except Exception as e:
                print(json.dumps({"id": request.get("id"), "success": False, "error": str(e)}), flush=True)


if __name__ == "__main__":
    service = WindowsAutomationService()
    service.run()
```

---

## 四、网络架构与客户端配置优化

### 4.1 网络连接方向分析

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 连接方向：客户端主动连接服务器（出站连接）                                      │
│                                                                             │
│ 客户端电脑（局域网 192.168.1.x 或家庭网络）                                    │
│     │                                                                       │
│     │ ① WebSocket 主动连接（出站，防火墙通常允许）                             │
│     │    ws(s)://服务器地址:8382/ws/agent                                    │
│     ▼                                                                       │
│ 服务器（Docker 部署，云服务器或内网服务器）                                     │
│     │                                                                       │
│     │ ② 通过已建立的 WS 连接发送 WIN_AUTOMATION_CALL                          │
│     │    （同一条连接，无需服务器主动连接客户端）                               │
│     ▼                                                                       │
│ 客户端本地执行 Python 服务                                                    │
│     │                                                                       │
│     │ ③ 通过已建立的 WS 连接返回 WIN_AUTOMATION_RESPONSE                      │
│     ▼                                                                       │
│ 服务器                                                                       │
└─────────────────────────────────────────────────────────────────────────────┘

关键结论：
✅ 客户端主动连接服务器，网络方向正确
✅ 所有后续通信通过已建立连接，无需服务器主动连接客户端
✅ 客户端 IP 变化不影响（使用 clientId 标识）
```

### 4.2 客户端配置管理

**核心优势**：`living-agent-desktop` 在每个客户端电脑安装，可灵活配置网络参数。

#### 4.2.1 现有配置项

| 配置项 | 文件 | 说明 |
|--------|------|------|
| 后端地址 | `Settings.tsx` | 用户可配置 `backendUrl`（如 `http://192.168.1.248:8382`） |
| clientId | `client-id.ts` | 自动生成 UUID + MAC 指纹，持久化 |
| Token | `auth.ts` | 登录后自动保存，safeStorage 加密 |

#### 4.2.2 新增配置项（建议）

```typescript
// shared/types.ts 新增网络配置类型
export interface NetworkConfig {
  // 后端地址（现有）
  backendUrl: string;
  
  // 新增：网络适配配置
  useWss: boolean;              // 是否使用 WSS（WebSocket over TLS）
  proxyUrl?: string;            // 代理地址（可选）
  pollingFallback: boolean;     // WebSocket 不可用时是否降级为 HTTP 轮询
  pollingInterval: number;      // 轮询间隔（毫秒，默认 5000）
  
  // 新增：连接超时配置
  connectTimeout: number;       // WebSocket 连接超时（毫秒，默认 10000）
  reconnectMaxAttempts: number; // 最大重连次数（默认 10）
  reconnectMaxDelay: number;    // 最大重连延迟（毫秒，默认 30000）
  
  // 新增：心跳配置
  heartbeatInterval: number;    // 心跳间隔（毫秒，默认 30000）
  heartbeatTimeout: number;     // 心跳超时（毫秒，默认 60000）
  
  // 新增：离线模式配置
  offlineMode: boolean;         // 是否启用离线模式
  offlineCacheSize: number;     // 离线缓存最大条数（默认 100）
}
```

#### 4.2.3 Settings 页面增强

```tsx
// Settings.tsx 新增网络配置区域
const NetworkSettingsSection = () => {
  const [networkConfig, setNetworkConfig] = useState<NetworkConfig>({
    backendUrl: '',
    useWss: false,
    pollingFallback: true,
    pollingInterval: 5000,
    connectTimeout: 10000,
    reconnectMaxAttempts: 10,
    reconnectMaxDelay: 30000,
    heartbeatInterval: 30000,
    heartbeatTimeout: 60000,
    offlineMode: false,
    offlineCacheSize: 100,
  });
  
  return (
    <div className="settings-section">
      <h3>网络配置</h3>
      
      {/* 后端地址 */}
      <div className="setting-item">
        <label>后端地址</label>
        <input 
          type="text" 
          value={networkConfig.backendUrl}
          onChange={(e) => setNetworkConfig({...networkConfig, backendUrl: e.target.value})}
          placeholder="http://192.168.1.248:8382 或 https://api.example.com"
        />
      </div>
      
      {/* WebSocket over TLS */}
      <div className="setting-item">
        <label>使用安全连接（WSS）</label>
        <input 
          type="checkbox" 
          checked={networkConfig.useWss}
          onChange={(e) => setNetworkConfig({...networkConfig, useWss: e.target.checked})}
        />
        <small>推荐：防火墙环境下使用 WSS 更稳定</small>
      </div>
      
      {/* HTTP 轮询降级 */}
      <div className="setting-item">
        <label>WebSocket 不可用时降级为 HTTP 轮询</label>
        <input 
          type="checkbox" 
          checked={networkConfig.pollingFallback}
          onChange={(e) => setNetworkConfig({...networkConfig, pollingFallback: e.target.checked})}
        />
        <small>确保 WebSocket 连接失败时仍可操作</small>
      </div>
      
      {/* 轮询间隔 */}
      {networkConfig.pollingFallback && (
        <div className="setting-item">
          <label>轮询间隔（毫秒）</label>
          <input 
            type="number" 
            value={networkConfig.pollingInterval}
            onChange={(e) => setNetworkConfig({...networkConfig, pollingInterval: parseInt(e.target.value)})}
            min={1000}
            max={60000}
          />
        </div>
      )}
      
      {/* 离线模式 */}
      <div className="setting-item">
        <label>启用离线缓存</label>
        <input 
          type="checkbox" 
          checked={networkConfig.offlineMode}
          onChange={(e) => setNetworkConfig({...networkConfig, offlineMode: e.target.checked})}
        />
        <small>断网时操作缓存，重连后自动同步</small>
      </div>
      
      {/* 网络状态检测 */}
      <div className="setting-item">
        <label>网络状态</label>
        <span className={isConnected ? 'status-connected' : 'status-disconnected'}>
          {isConnected ? '✅ 已连接' : '❌ 未连接'}
        </span>
        <button onClick={testConnection}>测试连接</button>
      </div>
    </div>
  );
};
```

### 4.3 WebSocket 连接优化

#### 4.3.1 WSS（WebSocket over TLS）支持

```typescript
// ws-client.ts 增强
export class WSClient {
  private config: NetworkConfig;
  
  async connect(path: string, params: Record<string, string> = {}): Promise<void> {
    const baseUrl = this.config.backendUrl;
    
    // 自动转换 ws/wss
    let wsUrl: string;
    if (this.config.useWss || baseUrl.startsWith('https://')) {
      wsUrl = baseUrl.replace('https://', 'wss://');
    } else {
      wsUrl = baseUrl.replace('http://', 'ws://');
    }
    
    const url = `${wsUrl}${path}?${new URLSearchParams(params).toString()}`;
    
    // 通过 Sec-WebSocket-Protocol 传递 token
    const token = await loadToken();
    const protocols = [`bearer.${token}`];
    
    this.socket = new WebSocket(url, protocols);
    
    // 设置超时
    this.socket.binaryType = 'arraybuffer';
    this.connectTimer = setTimeout(() => {
      if (this.socket?.readyState !== WebSocket.OPEN) {
        this.socket?.close();
        this.onConnectFailed();
      }
    }, this.config.connectTimeout);
  }
  
  private onConnectFailed = async () => {
    console.warn('[WS] Connection failed');
    
    // 降级为 HTTP 轮询
    if (this.config.pollingFallback) {
      console.log('[WS] Falling back to HTTP polling');
      this.startPolling();
    } else {
      // 指数退避重连
      this.scheduleReconnect();
    }
  };
}
```

#### 4.3.2 HTTP 轮询降级

```typescript
// ws-client.ts 新增轮询模式
export class WSClient {
  private pollingTimer?: NodeJS.Timeout;
  private usePolling = false;
  
  private startPolling = async () => {
    this.usePolling = true;
    console.log('[WS] HTTP polling started, interval:', this.config.pollingInterval);
    
    this.pollingTimer = setInterval(async () => {
      try {
        const clientId = getCachedClientId();
        const token = await loadToken();
        
        // 获取待执行的操作
        const response = await fetch(`${this.config.backendUrl}/api/win-automation/pending`, {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${token}`,
            'X-Client-Id': clientId
          }
        });
        
        if (response.ok) {
          const ops = await response.json();
          for (const op of ops.data || []) {
            // 执行本地操作
            const result = await winAutomationService.execute(op.operation, op.args);
            
            // 返回结果
            await fetch(`${this.config.backendUrl}/api/win-automation/result`, {
              method: 'POST',
              headers: {
                'Authorization': `Bearer ${token}`,
                'X-Client-Id': clientId,
                'Content-Type': 'application/json'
              },
              body: JSON.stringify({ id: op.id, ...result })
            });
          }
        }
      } catch (e) {
        console.error('[WS] Polling error:', e);
      }
    }, this.config.pollingInterval);
  };
  
  private stopPolling = () => {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = undefined;
    }
    this.usePolling = false;
  };
}
```

### 4.4 离线缓存能力

```typescript
// offline-cache.ts 新增
import { writeFile, readFile } from 'fs/promises';
import path from 'path';
import { app } from 'electron';

interface CachedOperation {
  id: number;
  operation: string;
  args: Record<string, any>;
  timestamp: number;
  status: 'pending' | 'completed' | 'failed';
  result?: any;
  error?: string;
}

export class OfflineCache {
  private cachePath: string;
  private cache: CachedOperation[] = [];
  private maxSize: number;
  
  constructor(maxSize: number = 100) {
    this.cachePath = path.join(app.getPath('userData'), 'offline-cache.json');
    this.maxSize = maxSize;
    this.loadCache();
  }
  
  private async loadCache() {
    try {
      const data = await readFile(this.cachePath, 'utf-8');
      this.cache = JSON.parse(data);
    } catch (e) {
      this.cache = [];
    }
  }
  
  private async saveCache() {
    await writeFile(this.cachePath, JSON.stringify(this.cache));
  }
  
  // 添加待执行操作
  async addPending(op: CachedOperation): void {
    if (this.cache.length >= this.maxSize) {
      // 移除最旧的已完成操作
      this.cache = this.cache.filter(c => c.status === 'pending');
      if (this.cache.length >= this.maxSize) {
        this.cache.shift(); // 移除最旧的
      }
    }
    this.cache.push(op);
    await this.saveCache();
  }
  
  // 标记完成
  async markCompleted(id: number, result: any): void {
    const op = this.cache.find(c => c.id === id);
    if (op) {
      op.status = 'completed';
      op.result = result;
      await this.saveCache();
    }
  }
  
  // 获取待执行操作
  getPending(): CachedOperation[] {
    return this.cache.filter(c => c.status === 'pending');
  }
  
  // 清理已完成操作
  async clearCompleted(): void {
    this.cache = this.cache.filter(c => c.status === 'pending');
    await this.saveCache();
  }
}

export const offlineCache = new OfflineCache();
```

### 4.5 网络场景适配

| 场景 | 客户端网络 | 服务器网络 | 配置建议 | 说明 |
|------|-----------|-----------|----------|------|
| **场景A** | 公司局域网 | 公司内网服务器（同一局域网） | `backendUrl: http://192.168.1.x:8382`<br>`useWss: false` | 直接 WebSocket，无需 TLS |
| **场景B** | 公司局域网 | 云服务器（公网） | `backendUrl: https://api.example.com`<br>`useWss: true` | WSS 穿透防火墙 |
| **场景C** | 公司局域网（严格防火墙） | 云服务器 | `useWss: true`<br>`pollingFallback: true`<br>`pollingInterval: 5000` | WSS + HTTP 轮询降级 |
| **场景D** | 家庭网络/移动网络 | 云服务器 | `backendUrl: https://api.example.com`<br>`useWss: true` | WSS，稳定连接 |
| **场景E** | 不稳定网络 | 任意 | `offlineMode: true`<br>`offlineCacheSize: 100` | 离线缓存，断网不丢操作 |

### 4.6 后端支持 HTTP 轮询

**新增 API**：`living-agent-gateway/controller/WinAutomationController.java`

```java
@RestController
@RequestMapping("/api/win-automation")
public class WinAutomationController {
  
  private final WebSocketSessionRegistry sessionRegistry;
  private final PendingOperationStore pendingStore;
  
  /**
   * 获取待执行操作（HTTP 轮询模式）
   */
  @GetMapping("/pending")
  public ApiResponse<List<PendingOperation>> getPendingOperations(
      @RequestHeader("X-Client-Id") String clientId,
      @RequestHeader("Authorization") String auth) {
    
    // 1. 验证 Token
    AuthSession session = unifiedAuthService.validateToken(auth.replace("Bearer ", ""));
    if (session == null) {
      return ApiResponse.err("AUTH_FAILED", "Token 无效");
    }
    
    // 2. 获取该 clientId 的待执行操作
    List<PendingOperation> ops = pendingStore.getByClientId(clientId);
    
    return ApiResponse.ok(ops);
  }
  
  /**
   * 提交操作结果（HTTP 轮询模式）
   */
  @PostMapping("/result")
  public ApiResponse<Void> submitResult(
      @RequestHeader("X-Client-Id") String clientId,
      @RequestHeader("Authorization") String auth,
      @RequestBody OperationResult result) {
    
    // 1. 验证 Token
    AuthSession session = unifiedAuthService.validateToken(auth.replace("Bearer ", ""));
    
    // 2. 处理结果
    windowsAutomationTool.handleResponse(result.getId(), result.isSuccess(), result.getResult(), result.getError());
    
    // 3. 从 pending store 移除
    pendingStore.remove(clientId, result.getId());
    
    return ApiResponse.ok(null);
  }
}
```

---

## 五、安全机制

### 5.1 认证链路

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. 用户登录 → 后端验证 → Token + AuthSession                                  │
│    - UnifiedAuthService.validateToken(token) → AuthSession                   │
│    - AuthSession: { employeeId, accessLevel, department, tenantId }          │
└─────────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. 桌面端 WS 连接 → 后端验证 Token → 建立 clientId ↔ session 映射             │
│    - AuthHandshakeInterceptor 提取 Token                                     │
│    - AgentWebSocketHandler.afterConnectionEstablished() 注册 clientId        │
│    - WebSocketSessionRegistry.register(clientId, session)                    │
└─────────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. 后端调用 WindowsAutomationTool → 权限检查 → 发送消息到桌面端                │
│    - OPERATION_PERMISSIONS.get(operation) → requiredLevel                    │
│    - userLevel >= requiredLevel → 允许                                       │
│    - HIGH_RISK_OPERATIONS.contains(operation) → ApprovalManager审批          │
│    - session.sendMessage(WIN_AUTOMATION_CALL)                                │
└─────────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. 桌面端收到消息 → 转发到本地 Python 服务 → 执行 → 返回结果                    │
│    - ws-client.ts handleWinAutomationCall()                                  │
│    - winAutomationService.execute(operation, args)                           │
│    - Python service (stdin/stdout) 执行                                       │
│    - 返回结果 → ws.send(WIN_AUTOMATION_RESPONSE)                              │
└─────────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. 桌面端通过 WS 发送响应 → 后端接收 → 返回给调用方                             │
│    - AgentWebSocketHandler.handleTextMessage() → WIN_AUTOMATION_RESPONSE     │
│    - WindowsAutomationTool.handleResponse()                                  │
│    - CompletableFuture.complete()                                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 权限检查流程

```java
// WindowsAutomationTool.execute() 中的权限检查
public ToolResult execute(ToolParams params, ToolContext context) {
    String operation = params.get("operation");
    AccessLevel userLevel = context.securityPolicy().getAccessLevel();
    AccessLevel requiredLevel = OPERATION_PERMISSIONS.get(operation);
    
    // 权限不足 → 直接拒绝
    if (userLevel.getValue() < requiredLevel.getValue()) {
        return ToolResult.error("权限不足：需要 " + requiredLevel + " 权限");
    }
    
    // 高风险操作 → 需要审批
    if (HIGH_RISK_OPERATIONS.contains(operation)) {
        ApprovalRequest approval = approvalManager.requestApproval(...);
        if (approval.getStatus() != ApprovalStatus.APPROVED) {
            return ToolResult.error("操作需要审批，审批ID: " + approval.getId());
        }
    }
    
    // 执行...
}
```

### 5.3 审计日志

**表结构**：`client_operation_audit_log`（已存在）

```sql
-- 每次操作都记录审计日志
INSERT INTO client_operation_audit_log (
    client_id, user_id, operation_type, operation_detail,
    target_resource, result_status, approval_id, created_at
) VALUES (
    :clientId, :userId, 'WIN_AUTOMATION', :operation + :args,
    :targetResource, :success, :approvalId, NOW()
);
```

---

## 六、实施步骤

### Phase 1：桌面端 Windows Automation 服务（1周）

| 任务 | 文件 | 状态 |
|------|------|------|
| 创建服务管理器 | `win-automation-service.ts` | ✅ 已完成 |
| 添加 IPC handler | `ipc.ts` | ✅ 已完成 |
| WS 消息处理 | `ws-client.ts` | ✅ 已完成 |
| 启动流程集成 | `index.ts` | ✅ 已完成 |
| Preload API 添加 | `preload/index.ts` | ✅ 已完成 |
| 类型定义添加 | `shared/api-types.ts`、`shared/types.ts` | ✅ 已完成 |
| Python 服务实现 | `resources/win-automation/service.py` | ✅ 已完成 |
| electron-builder 配置 | `electron-builder.yml` | ✅ 已完成 |

### Phase 2：后端 WindowsAutomationTool（1周）

| 任务 | 文件 | 状态 |
|------|------|------|
| 创建 WindowsAutomationClientGateway 接口 | `core/websocket/WindowsAutomationClientGateway.java` | ✅ 已完成 |
| 创建 WindowsAutomationClientGatewayImpl 实现 | `gateway/websocket/WindowsAutomationClientGatewayImpl.java` | ✅ 已完成 |
| 创建 WindowsAutomationTool | `core/tool/impl/WindowsAutomationTool.java` | ✅ 已完成 |
| AgentWebSocketHandler 集成 | `AgentWebSocketHandler.java` | ✅ 已完成 |
| 权限映射配置 | `WindowsAutomationTool.java` | ✅ 已完成 |
| 高风险操作审批集成 | `WindowsAutomationTool.java` | ✅ 已完成 |
| 审计日志记录 | `WindowsAutomationTool.java` | ✅ 已完成 |
| 工具注册 | `core/config/ToolConfig.java` | ✅ 已完成 |

### Phase 3：测试与文档（1周）

| 任务 | 内容 | 状态 |
|------|------|------|
| TypeScript 类型检查 | `npm run typecheck` | ✅ 通过 |
| Java 编译检查 | `mvn clean compile` | ✅ 通过 |
| 功能测试 | 各操作逐一验证 | 🔲 待测试 |
| 权限测试 | 各级别权限隔离验证 | 🔲 待测试 |
| 审批测试 | 高风险操作审批流程 | 🔲 待测试 |
| 打包测试 | electron-builder 打包验证 | 🔲 待测试 |
| 文档更新 | 更新 CODE_STRUCTURE_AND_FILE_GUIDE.md | ✅ 已完成 |

---

## 七、文件变更清单

### 新建文件

| 文件路径 | 说明 |
|----------|------|
| `living-agent-desktop/src/main/win-automation-service.ts` | Windows 自动化服务管理器（管理 Python 子进程，JSON 行协议通信） |
| `living-agent-desktop/resources/win-automation/service.py` | Python 自动化服务（借鉴 Windows-MCP 技术栈，UIA/PowerShell/注册表/文件系统等） |
| `living-agent-desktop/resources/win-automation/requirements.txt` | Python 依赖清单（psutil/pywin32/comtypes/dxcam/mss/Pillow） |
| `living-agent-core/.../websocket/WindowsAutomationClientGateway.java` | 客户端网关接口（core 定义，gateway 实现，解耦 core 与 WebSocket） |
| `living-agent-core/.../tool/impl/WindowsAutomationTool.java` | Windows 自动化工具实现（权限检查+审批+WebSocket 转发） |
| `living-agent-gateway/.../websocket/WindowsAutomationClientGatewayImpl.java` | 网关实现（维护 clientId → WebSocketSession 映射，发送/接收消息） |

### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `living-agent-desktop/src/main/ipc.ts` | 添加 win-automation IPC handler（start/stop/status/execute） |
| `living-agent-desktop/src/main/ws-client.ts` | 添加 WIN_AUTOMATION_CALL 处理，转发到本地 Python 服务并回传响应 |
| `living-agent-desktop/src/main/index.ts` | 启动/停止 Windows 自动化服务 |
| `living-agent-desktop/src/preload/index.ts` | 添加 winAutomation.* API |
| `living-agent-desktop/src/shared/types.ts` | 添加 WinAutomationOperation/Result/Status 类型 + IPC 通道 |
| `living-agent-desktop/src/shared/api-types.ts` | 添加 winAutomation 类型定义 |
| `living-agent-desktop/electron-builder.yml` | 添加 extraResources 打包 win-automation 目录 |
| `living-agent-gateway/.../AgentWebSocketHandler.java` | 注入 gateway，连接建立/关闭时注册/注销 clientId，处理 WIN_AUTOMATION_RESPONSE |
| `living-agent-core/.../config/ToolConfig.java` | 注册 WindowsAutomationTool，注入 gateway 和 approvalManager |
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` | 更新 Windows 自动化架构说明和文件清单 |

---

## 八、与 pywinauto 业务化封装的协同发展

### 8.1 协同模式

```
业务场景请求 → 后端路由判断
                    │
    ┌───────────────┴───────────────┐
    │                               │
    ▼                               ▼
业务化操作                       通用系统操作
(login/menu_select)              (shell/registry/filesystem)
    │                               │
    ▼                               ▼
server.py (pywinauto)            winAutomationService
    │                               │
    └─── 会话管理 + 状态保持 ────────┘
                    │
                    ▼
              更高效的业务操作
```

### 8.2 业务化封装增强路径

| 阶段 | 增强内容 | 效果 |
|------|----------|------|
| **阶段1** | 利用 `snapshot` 增强 pywinauto 的控件定位 | 更精准的控件识别 |
| **阶段2** | 利用 `wait_for` 增强菜单/对话框等待 | 更可靠的操作等待 |
| **阶段3** | 利用 `clipboard_*` 实现批量数据录入 | 批量操作更高效 |
| **阶段4** | 利用 `filesystem_*` 实现完整导出流程 | 文件处理更完整 |
| **阶段5** | 利用 `process_kill` 实现异常恢复 | 异常处理更可靠 |

### 8.3 最终目标

**living-agent-service Windows 自动化能力体系**：

1. **业务化封装层**（pywinauto）：财务软件登录、菜单操作、数据录入等业务场景
2. **通用系统控制层**（借鉴 Windows-MCP）：PowerShell、注册表、文件系统、进程管理等
3. **统一认证与权限层**：Token 验证、权限隔离、审批机制、审计日志

---

## 九、总结

本方案实现了以下目标：

1. **借鉴控制技术栈**：学习 Windows-MCP 的 UIA、PowerShell、注册表等实现方式，增强自身能力
2. **统一认证**：所有操作通过 living-agent-service 后端认证和权限检查
3. **权限隔离**：不同 AccessLevel 可访问的操作不同，高风险操作需要审批
4. **审计追踪**：所有操作记录到 `client_operation_audit_log` 表
5. **协同发展**：通用控制能力与 pywinauto 业务化封装协同，逐步完善业务操作效率

实施周期约 3 周，建议按 Phase 顺序逐步推进。最终形成完整的 Windows 自动化能力体系。