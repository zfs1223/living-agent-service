# Windows 自动化多节点部署方案

## 📐 架构设计

```
┌───────────────────────────────────────────────────────────────────────┐
│                    服务器端 (living-agent-service)                    │
│                                                                       │
│  ┌─────────────┐     ┌──────────────────┐     ┌──────────────────┐   │
│  │ AI大脑      │────>│ WindowsAppTool   │────>│ HTTP Client      │   │
│  │ MainBrain   │     │ (Java)           │     │                  │   │
│  │ ToolNeuron  │     │                  │     │                  │   │
│  └─────────────┘     └──────────────────┘     └────────┬─────────┘   │
│                                                        │              │
└────────────────────────────────────────────────────────┼──────────────┘
                                                         │
                    ┌────────────────────────────────────┼────────────┐
                    │           局域网                    │            │
                    │                                    │            │
          ┌─────────▼──────────┐              ┌─────────▼──────────┐  │
          │  客户端电脑A       │              │  客户端电脑B       │  │
          │                    │              │                    │  │
          │ Python Server      │              │ Python Server      │  │
          │ :8765              │              │ :8765              │  │
          │         ↓          │              │         ↓          │  │
          │ pywinauto          │              │ pywinauto          │  │
          │         ↓          │              │         ↓          │  │
          │ 金蝶KIS            │              │ 其他Windows应用    │  │
          └────────────────────┘              └────────────────────┘  │
                    │                                    │            │
                    └────────────────────────────────────┼────────────┘
                                                         │
          ┌──────────────────────────────────────────────┼────────────┐
          │  客户端电脑C                                 │            │
          │                    │                         │            │
          │ Python Server      │                         │            │
          │ :8765              │                         │            │
          │         ↓          │                         │            │
          │ pywinauto          │                         │            │
          │         ↓          │                         │            │
          │ 财务软件           │                         │            │
          └────────────────────┘                         │            │
                                                         │            │
                                                         ▼            │
```

## 🎯 部署步骤

### 第1步：服务器端配置

在服务器上修改 `windows-app-tool.json`：

```json
{
  "enabled": true,
  "nodes": {
    "pc-finance-01": {
      "name": "财务电脑01",
      "url": "http://192.168.1.101:8765",
      "description": "财务部电脑，运行金蝶KIS"
    },
    "pc-hr-01": {
      "name": "人事电脑01",
      "url": "http://192.168.1.102:8765",
      "description": "人事部电脑，运行人事系统"
    },
    "pc-admin-01": {
      "name": "行政电脑01",
      "url": "http://192.168.1.103:8765",
      "description": "行政部电脑"
    }
  }
}
```

### 第2步：客户端电脑配置

在每台客户端电脑上：

1. **安装 Python 依赖**
```bash
pip install -r requirements.txt
```

2. **修改配置文件**
复制 `config.client.example.json` 为 `config.json`，修改：
- `exe_path` - 应用安装路径
- `backend` - 根据应用类型选择 `win32` 或 `uia`

3. **启动服务**
```bash
python server.py
```

4. **设置开机自启动（可选）**

创建 Windows 计划任务：
```powershell
$Trigger = New-ScheduledTaskTrigger -AtStartup
$Action = New-ScheduledTaskAction -Execute "python" -Argument "server.py" -WorkingDirectory "C:\path\to\windows_automation"
Register-ScheduledTask -TaskName "WindowsAutomationService" -Trigger $Trigger -Action $Action -User "SYSTEM" -RunLevel Highest
```

### 第3步：防火墙配置

在每台客户端电脑上开放端口：

```powershell
# 开放8765端口
New-NetFirewallRule -DisplayName "Windows Automation Service" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow
```

### 第4步：测试连接

在服务器上测试：

```bash
# 测试客户端A
curl http://192.168.1.101:8765/health

# 测试客户端B
curl http://192.168.1.102:8765/health
```

## 💻 使用示例

### 示例1：控制财务电脑导出报表

```java
// 通过 ToolNeuron 调用
ToolParams params = ToolParams.of(Map.of(
    "action", "launch",
    "app_name", "kingdee_mini",
    "node", "pc-finance-01"  // 指定目标电脑
));

ToolResult result = tool.execute(params, context);
```

### 示例2：批量控制多台电脑

```json
{
  "action": "launch",
  "app_name": "kingdee_mini",
  "nodes": ["pc-finance-01", "pc-finance-02"]
}
```

## 🔐 安全配置

### 1. 访问令牌

在每台客户端的 `config.json` 中设置：

```json
{
  "security": {
    "token": "your-secret-token-here"
  }
}
```

### 2. IP白名单

限制只允许服务器IP访问：

```json
{
  "security": {
    "allowed_ips": ["192.168.1.100"]
  }
}
```

## 📊 监控与管理

### 查看所有节点状态

```bash
curl http://localhost:8382/api/tools/windows/nodes
```

### 节点状态

| 节点 | 状态 | 活跃会话 | 最后心跳 |
|------|------|----------|----------|
| pc-finance-01 | ✅ 在线 | 1 | 2024-04-24 10:00 |
| pc-hr-01 | ✅ 在线 | 0 | 2024-04-24 09:58 |
| pc-admin-01 | ❌ 离线 | - | 2024-04-24 08:30 |

## ⚙️ 高级配置

### 1. 指定节点操作

```json
{
  "action": "login",
  "session_id": "kingdee_mini_abc123",
  "username": "admin",
  "password": "123456",
  "node": "pc-finance-01"
}
```

### 2. 自动发现节点

在服务器端配置自动发现：

```json
{
  "auto_discovery": {
    "enabled": true,
    "subnet": "192.168.1.0/24",
    "port": 8765,
    "interval_seconds": 60
  }
}
```

## 🛠️ 故障排查

### 问题1：无法连接客户端

**症状**：服务器无法访问客户端服务

**解决方案**：
1. 检查客户端服务是否运行：`python server.py`
2. 检查防火墙是否开放端口
3. 检查网络连通性：`ping 192.168.1.101`

### 问题2：应用启动失败

**症状**：`exe_path` 不存在

**解决方案**：
1. 确认应用已正确安装
2. 检查 `config.json` 中的 `exe_path` 是否正确
3. 检查是否有足够的权限启动应用

### 问题3：控件识别失败

**症状**：找不到指定的控件

**解决方案**：
1. 尝试切换 `backend`（`win32` 或 `uia`）
2. 使用控件分析功能查看实际控件树
3. 调整正则表达式匹配模式
