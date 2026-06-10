# Windows 自动化桥接服务

基于 pywinauto + FastAPI 的 Windows 桌面应用自动化服务，支持局域网内多节点集中控制。

## 架构

```
┌─────────────────────────────────────────┐
│     服务器端 (living-agent-service)     │
│                                         │
│  AI大脑 → WindowsAppTool (Java)        │
│                ↓ HTTP API               │
└─────────────────────────────────────────┘
                 │ 局域网
    ┌────────────┼────────────┐
    ↓            ↓            ↓
┌────────┐  ┌────────┐  ┌────────┐
│ 客户端A│  │ 客户端B│  │ 客户端C│
│ Python │  │ Python │  │ Python │
│ Server │  │ Server │  │ Server │
│   ↓    │  │   ↓    │  │   ↓    │
│pywinauto│ │pywinauto│ │pywinauto│
│   ↓    │  │   ↓    │  │   ↓    │
│金蝶KIS │  │其他应用│  │财务软件│
└────────┘  └────────┘  └────────┘
```

## 部署方式

### 方案一：独立使用（单台电脑）

适用于本地测试或单机自动化场景。

#### 1. 安装依赖

```bash
cd living-agent-core/src/main/resources/scripts/windows_automation
pip install -r requirements.txt
```

#### 2. 修改配置

编辑 `config.json`，设置应用路径：

```json
{
    "applications": {
        "kingdee_mini": {
            "exe_path": "C:\\Program Files\\Kingdee\\KIS\\mini.exe",
            "backend": "win32"
        }
    }
}
```

#### 3. 启动服务

```bash
python server.py
```

服务默认运行在 `http://localhost:8765`

#### 4. 测试

```bash
curl http://localhost:8765/health
```

### 方案二：多节点部署（服务器 + 多台客户端）

适用于服务器集中控制局域网内多台电脑的 Windows 应用。

#### 服务器端配置

在 `living-agent-service` 中配置客户端节点：

```java
// WindowsAppTool.java
private void initializeDefaultNodes() {
    addNode("pc-finance-01", "http://192.168.1.101:8765", "财务电脑01-金蝶KIS");
    addNode("pc-hr-01", "http://192.168.1.102:8765", "人事电脑01");
    addNode("pc-admin-01", "http://192.168.1.103:8765", "行政电脑01");
}
```

#### 客户端配置

1. **复制文件到客户端电脑**
   将整个 `windows_automation` 目录复制到客户端电脑。

2. **修改配置文件**
   复制 `config.client.example.json` 为 `config.json`，修改：
   - `exe_path` - 应用安装路径
   - `backend` - `win32` 或 `uia`

3. **启动服务**

```bash
pip install -r requirements.txt
python server.py
```

4. **设置开机自启动（可选）**

创建 Windows 计划任务：

```powershell
$Trigger = New-ScheduledTaskTrigger -AtStartup
$Action = New-ScheduledTaskAction -Execute "python" -Argument "server.py" -WorkingDirectory "C:\path\to\windows_automation"
Register-ScheduledTask -TaskName "WindowsAutomationService" -Trigger $Trigger -Action $Action -User "SYSTEM" -RunLevel Highest
```

5. **开放防火墙端口**

```powershell
New-NetFirewallRule -DisplayName "Windows Automation Service" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow
```

#### 测试连接

在服务器上测试每个节点：

```bash
# 测试客户端A
curl http://192.168.1.101:8765/health

# 测试客户端B
curl http://192.168.1.102:8765/health
```

## API 文档

### 健康检查

```bash
GET /health
```

### 启动应用

```bash
POST /api/windows/launch

{
    "app_name": "kingdee_mini",
    "exe_path": "应用路径（可选，从配置读取）",
    "backend": "win32",
    "session_id": "会话ID（可选）"
}
```

### 登录应用

```bash
POST /api/windows/login

{
    "session_id": "会话ID",
    "username": "用户名",
    "password": "密码",
    "timeout": 30
}
```

### 选择菜单

```bash
POST /api/windows/menu

{
    "session_id": "会话ID",
    "menu_path": "财务报表->资产负债表"
}
```

### 点击控件

```bash
POST /api/windows/click

{
    "session_id": "会话ID",
    "control_type": "Button",
    "title_pattern": ".*确定.*",
    "timeout": 5
}
```

### 输入文本

```bash
POST /api/windows/type_keys

{
    "session_id": "会话ID",
    "control_type": "Edit",
    "title_pattern": ".*用户名.*",
    "text": "要输入的文本"
}
```

### 获取控件文本

```bash
POST /api/windows/get_text

{
    "session_id": "会话ID",
    "control_type": "Edit",
    "title_pattern": ".*用户名.*"
}
```

### 截图

```bash
POST /api/windows/screenshot

{
    "session_id": "会话ID",
    "output_path": "保存路径（可选）"
}
```

### 获取控件树

```bash
GET /api/windows/controls?session_id=会话ID
```

### 关闭应用

```bash
POST /api/windows/close

{
    "session_id": "会话ID"
}
```

### 列出活跃会话

```bash
GET /api/windows/sessions
```

### 列出所有节点（服务器端）

```java
// 通过 ToolNeuron 调用
ToolParams params = ToolParams.of(Map.of(
    "action", "list_nodes"
));
```

## 使用示例

### Java 调用示例

```java
// 启动金蝶
ToolResult result = tool.execute(ToolParams.of(Map.of(
    "action", "launch",
    "node", "pc-finance-01",
    "app_name", "kingdee_mini"
)), context);

// 登录
result = tool.execute(ToolParams.of(Map.of(
    "action", "login",
    "node", "pc-finance-01",
    "session_id", sessionId,
    "username", "admin",
    "password", "123456"
)), context);

// 导出报表
result = tool.execute(ToolParams.of(Map.of(
    "action", "menu",
    "node", "pc-finance-01",
    "session_id", sessionId,
    "menu_path", "财务报表->资产负债表"
)), context);
```

## 配置文件说明

### config.json（服务器端）

```json
{
    "server": {
        "host": "0.0.0.0",
        "port": 8765
    },
    "applications": {
        "应用ID": {
            "name": "应用名称",
            "exe_path": "可执行文件路径",
            "backend": "win32或uia",
            "enabled": true
        }
    },
    "security": {
        "allowed_commands": ["启动", "登录", "菜单", ...],
        "max_execution_time": 300
    }
}
```

## 常见问题

### Q1: 服务无法启动

**A**: 检查端口是否被占用，或尝试更换端口。

### Q2: 控件识别失败

**A**: 尝试切换 `backend`（`win32` 或 `uia`），或使用控件分析功能。

### Q3: 无法连接客户端

**A**: 
1. 检查服务是否运行
2. 检查防火墙设置
3. 检查网络连通性

### Q4: 应用启动失败

**A**: 
1. 确认 `exe_path` 正确
2. 检查是否有足够权限
3. 查看服务日志

## 安全注意事项

1. 该服务具有系统控制权限，请妥善保管
2. 建议在内网使用，不要暴露到公网
3. 可以配置访问令牌增强安全性
4. 定期更新 pywinauto 版本

## 技术栈

- **FastAPI**: Python Web 框架
- **pywinauto**: Windows GUI 自动化库
- **uvicorn**: ASGI 服务器
- **Pillow**: 图像处理（截图功能）
