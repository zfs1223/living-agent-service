# AI企业管理智能体系统 - 产品使用说明书（测试验证篇）

> 版本：v1.0
> 日期：2026-04-10
> 适用版本：living-agent-service

---

## 一、系统概述

### 1.1 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (React)                               │
│                    http://localhost:8383                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API网关 (Spring Boot)                          │
│                    http://localhost:8382                          │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                │
│  │ AgentWS    │ │ DeptWS     │ │ REST API   │                │
│  │ /ws/agent  │ │ /ws/dept/* │ │ /api/*     │                │
│  └─────────────┘ └─────────────┘ └─────────────┘                │
└─────────────────────────────────────────────────────────────────┘
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                        核心服务 (living-agent-core)                │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │
│  │ Neuron      │ │ Brain       │ │ Permission   │            │
│  │ (Qwen3Neuron)│ │ (部门大脑)  │ │ Service     │            │
│  └──────────────┘ └──────────────┘ └──────────────┘            │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                     模型服务 (Python Daemon)                       │
│              /scripts/python/model_daemon.py                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │
│  │ Qwen3-0.6B  │ │ Qwen3.5-2B  │ │ Sherpa ASR  │            │
│  │ (闲聊神经元) │ │ (工具神经元) │ │ (语音识别)  │            │
│  └──────────────┘ └──────────────┘ └──────────────┘            │
└─────────────────────────────────────────────────────────────────┘

依赖服务：
┌─────────────────────────────────────────────────────────────────┐
│  PostgreSQL :5432  │  Redis :6379  │  Qdrant :6333  │  Kafka :9092  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 核心端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 8383 | Web UI |
| 后端 API | 8382 | REST API + WebSocket |
| PostgreSQL | 5432 | 主数据库 |
| Redis | 6379 | 缓存 |
| Qdrant | 6333 | 向量数据库 |
| Kafka | 9092 | 消息队列 |
| MemOS Neo4j | 7687 | 图数据库 |
| MemOS | 8381 | 记忆系统 |

---

## 二、服务管理命令

### 2.1 查看所有容器状态

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" --filter "name=living-agent"
```

### 2.2 重启所有服务

```bash
cd f:\SoarCloudAI\docker\living-agent-service
docker-compose up --build -d
```

### 2.3 查看服务日志

```bash
# 查看后端日志
docker logs living-agent-service --tail 100 -f

# 查看前端日志
docker logs living-agent-frontend --tail 50 -f
```

### 2.4 进入容器调试

```bash
# 进入后端容器
docker exec -it living-agent-service bash

# 进入数据库
docker exec -it living-agent-postgres psql -U livingagent
```

---

## 三、API验证步骤

### 3.1 系统健康检查

**步骤1：检查系统状态**

```powershell
# PowerShell
$response = Invoke-RestMethod -Uri "http://localhost:8382/api/system/status" -Method Get
$response | ConvertTo-Json -Depth 5
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "hasFounder": true,
    "isFirstUser": false,
    "isConfigured": true,
    "configuredProviders": ["qwen_local"]
  }
}
```

**状态说明：**
- `hasFounder`: true = 董事长已注册
- `isConfigured`: true = LLM Provider已配置
- `configuredProviders`: 非空 = 模型服务可用

---

### 3.2 配置Ollama Provider（如未配置）

**步骤1：检查可用Provider**

```bash
curl -s http://localhost:8382/api/system/config/providers
```

**步骤2：验证Ollama连通性**

```bash
# 从宿主机访问
curl -s http://localhost:11434/api/tags

# 从Docker内部访问
docker exec living-agent-service curl -s http://host.docker.internal:11434/api/tags
```

**步骤3：配置Provider**

```powershell
$body = @{
    providerId = "qwen_local"
    name = "Qwen Local (Ollama)"
    apiKey = "ollama"
    baseUrl = "http://host.docker.internal:11434/v1"
    enabled = $true
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8382/api/system/config/providers/qwen_local" -Method Put -ContentType "application/json" -Body $body
```

---

### 3.3 认证与登录

#### 方式一：手机号登录（需董事长已绑定手机）

**步骤1：发送验证码**

```powershell
$body = @{
    phone = "18988886666"
    type = "login"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8382/api/auth/sms/send" -Method Post -ContentType "application/json" -Body $body
```

**步骤2：获取验证码**
> 验证码会发送到手机，同时打印在Docker日志中：
```bash
docker logs living-agent-service --tail 200 | findstr "Verification code"
```

**步骤3：登录**

```powershell
$body = @{
    phone = "18988886666"
    code = "123456"  # 输入实际收到的验证码
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8382/api/auth/phone/login" -Method Post -ContentType "application/json" -Body $body
$response | ConvertTo-Json -Depth 5
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "accessToken": "sess_xxxxxxxxxxxxxx",
    "user": {
      "id": "founder_xxx",
      "name": "张三",
      "identity": "INTERNAL_ENTERPRISE",
      "accessLevel": "FULL"
    }
  }
}
```

#### 方式二：OAuth登录（钉钉/飞书/企微）

**步骤1：获取授权URL**

```powershell
# 钉钉
Invoke-RestMethod -Uri "http://localhost:8382/api/auth/oauth/dingtalk/url" -Method Get

# 飞书
Invoke-RestMethod -Uri "http://localhost:8382/api/auth/oauth/feishu/url" -Method Get
```

**步骤2：在浏览器中完成OAuth授权**

**步骤3：使用回调URL完成登录**

---

### 3.4 获取用户信息（验证Token）

```powershell
$headers = @{
    "Authorization" = "Bearer sess_xxxxxxxxxxxxxx"
}

$response = Invoke-RestMethod -Uri "http://localhost:8382/api/auth/user" -Method Get -Headers $headers
$response | ConvertTo-Json -Depth 5
```

---

### 3.5 验证神经元和大脑注册

```powershell
$headers = @{
    "Authorization" = "Bearer sess_xxxxxxxxxxxxxx"
}

# 列出所有神经元
Invoke-RestMethod -Uri "http://localhost:8382/api/neurons" -Method Get -Headers $headers | ConvertTo-Json -Depth 3

# 列出所有智能体
Invoke-RestMethod -Uri "http://localhost:8382/api/agents" -Method Get -Headers $headers | ConvertTo-Json -Depth 3

# 列出所有部门
Invoke-RestMethod -Uri "http://localhost:8382/api/departments" -Method Get -Headers $headers | ConvertTo-Json -Depth 3
```

---

## 四、WebSocket对话测试

### 4.1 对话流程架构

```
用户输入 → WebSocket /ws/agent → 认证 → 闲聊神经元(Qwen3-0.6B)
                                           ↓
                        意图分类 → 权限检查 → 部门路由 → 大脑
```

### 4.2 WebSocket连接参数

| 参数 | 说明 | 示例 |
|------|------|------|
| 端点 | 智能体对话 | `/ws/agent` |
| token | 认证Token | `sess_xxxxxxxx` |
| agentId | 智能体ID（可选） | `agent_001` |
| session_id | 会话ID（可选） | `sess_xxx` |

**完整URL格式：**
```
ws://localhost:8382/ws/agent?token=sess_xxxxxxxxxxxxxx&agentId=agent_001
```

### 4.3 WebSocket消息格式

**发送消息：**
```json
{
  "type": "text",
  "content": "你好，请帮我设计一个新系统"
}
```

**接收消息类型：**

| 类型 | 说明 |
|------|------|
| `connected` | 连接成功确认 |
| `thinking` | 思考中（流式） |
| `chunk` | 内容块（流式） |
| `tool_call` | 工具调用 |
| `done` | 完成 |
| `error` | 错误 |

### 4.4 测试脚本

#### PowerShell WebSocket测试

```powershell
# 创建WebSocket对象
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = [Threading.CancellationToken]::None

# 连接
$url = "ws://localhost:8382/ws/agent?token=sess_xxxxxxxxxxxxxx"
$response = $ws.ConnectAsync($url, $ct)
$response.Wait()

# 发送消息
$message = '{"type":"text","content":"你好"}'
$bytes = [Text.Encoding]::UTF8.GetBytes($message)
$ws.SendAsync([ArraySegment[byte]]$bytes, 'Text', $true, $ct)

# 接收响应
$buffer = New-Object byte[] 8192
while ($ws.State -eq 'Open') {
    $result = $ws.ReceiveAsync([ArraySegment[byte]]$buffer, $ct)
    $result.Wait()
    if ($result.Result.Count -gt 0) {
        $text = [Text.Encoding]::UTF8.GetString($result.Result.Array, 0, $result.Result.Count)
        Write-Host $text
    }
    if ($result.Result.EndOfMessage) { break }
}

# 关闭
$ws.CloseAsync('NormalClosure', "", $ct)
```

#### Python WebSocket测试脚本

```python
import websocket
import json
import time

def on_message(ws, message):
    data = json.loads(message)
    print(f"收到: {data}")

def on_error(ws, error):
    print(f"错误: {error}")

def on_close(ws, close_status_code, close_msg):
    print("连接关闭")

def on_open(ws):
    # 发送消息
    ws.send(json.dumps({
        "type": "text",
        "content": "你好"
    }))

# 创建WebSocket连接
token = "sess_xxxxxxxxxxxxxx"
url = f"ws://localhost:8382/ws/agent?token={token}"
ws = websocket.WebSocketApp(url,
    on_message=on_message,
    on_error=on_error,
    on_close=on_close,
    on_open=on_open)

ws.run_forever()
```

---

## 五、董事长场景测试用例

### 5.1 测试场景描述

**角色：** 董事长（FULL权限）
**设备：** PC浏览器
**目标：** 验证从需求输入到大脑处理的完整流程

### 5.2 测试步骤

#### 步骤1：登录系统

1. 打开浏览器访问 `http://localhost:8383`
2. 选择登录方式（手机号/OAuth）
3. 完成认证，获取Token

**验证点：** Token成功获取，显示用户信息

#### 步骤2：进入聊天页面

1. 点击「闲聊神经元」或任意数字员工
2. 确认WebSocket连接成功

**验证点：** 页面显示「已连接」状态

#### 步骤3：发送闲聊消息

1. 输入：`你好，今天天气怎么样？`
2. 发送

**预期行为：**
- 闲聊消息由 Qwen3-0.6B 处理（响应快速）
- 回复内容为日常闲聊风格

#### 步骤4：发送复杂任务

1. 输入：`请按《AI企业管理智能体系统软件概要设计文档》开发一个新系统`
2. 发送

**预期行为：**
- 系统识别为复杂任务（COMPLEX_TASK）
- FULL权限 → 路由到 MainBrain
- MainBrain分析需求，调用相关技能

**路由判断逻辑：**

```
用户输入 → 意图分类
  ├── GREETING/CASUAL_CHAT/SIMPLE_QUESTION → Qwen3Neuron (闲聊)
  ├── TOOL_CALL + DEPARTMENT权限 → ToolNeuron (工具检测)
  └── COMPLEX_TASK
       ├── FULL权限 → MainBrain (主大脑/董事长)
       ├── DEPARTMENT权限 → 部门大脑
       └── LIMITED权限 → AdminBrain/CsBrain
```

#### 步骤5：跨部门协调

1. 输入：`我需要技术部和人力资源部协作完成招聘系统的开发`
2. 发送

**预期行为：**
- MainBrain识别跨部门任务
- 协调 TechBrain 和 HrBrain

### 5.3 测试检查清单

| 序号 | 检查项 | 预期结果 | 实际结果 |
|------|--------|----------|----------|
| 1 | 系统状态API正常 | hasFounder=true, isConfigured=true | |
| 2 | 登录成功 | 返回accessToken | |
| 3 | WebSocket连接 | 收到connected消息 | |
| 4 | 闲聊响应正常 | Qwen3-0.6B快速回复 | |
| 5 | 复杂任务路由 | 路由到MainBrain | |
| 6 | 权限正确识别 | FULL权限可访问所有大脑 | |
| 7 | 工具调用正常 | 可触发工具执行 | |
| 8 | 响应格式正确 | JSON格式，包含type字段 | |

---

## 六、测试验证记录

### 6.1 验证码登录测试（2026-04-10）

#### 测试结果：✅ 通过

**测试步骤：**
1. 发送验证码到 18970718886
2. 从Docker日志获取验证码
3. 使用验证码调用登录API

**API调用：**
```bash
# 发送验证码
POST http://localhost:8382/api/auth/sms/send
Body: {"phone":"18970718886","type":"login"}

# 登录获取Token
POST http://localhost:8382/api/auth/phone/login
Body: {"phone":"18970718886","code":"{验证码}"}
```

**响应示例：**
```json
{
  "success": true,
  "data": {
    "token": "sess_bb90278f15e1493a",
    "employee": {
      "id": "founder_58366a87",
      "name": "董事长",
      "identity": "INTERNAL_ENTERPRISE",
      "accessLevel": "FULL"
    }
  },
  "error": null
}
```

**验证点：**
- ✅ 验证码发送功能正常
- ✅ 登录API返回正确
- ✅ 董事长身份确认：INTERNAL_ENTERPRISE
- ✅ 访问级别正确：FULL

---

## 七、已知问题

### 7.1 前端登录状态保存问题

**问题描述：**
通过浏览器自动化测试（Puppeteer）进行登录时，存在时序问题。验证码登录成功后，前端可能未正确保存token到localStorage。

**临时解决方案：**
1. 通过API登录获取token
2. 手动设置token到浏览器localStorage

```javascript
// 手动设置token
localStorage.setItem('authToken', 'sess_xxx');
localStorage.setItem('user', JSON.stringify({
  id: 'founder_58366a87',
  name: '董事长',
  identity: 'INTERNAL_CHAIN',
  accessLevel: 'FULL'
}));
```

### 7.2 验证码有效期

**问题描述：**
验证码有效期较短（约5分钟），在自动化测试中容易过期。

**建议：**
- 人工操作时无影响
- 自动化测试需要快速完成验证码输入

---

## 八、下一步测试计划

- [ ] WebSocket连接测试
- [ ] 闲聊神经元响应测试
- [ ] 董事长角色完整流程测试
- [ ] 大脑路由测试

---

## 九、常见问题排查

### 9.1 服务无法启动

**问题：** `living-agent-service` 显示 unhealthy

**排查步骤：**
```bash
# 查看详细日志
docker logs living-agent-service --tail 200

# 检查依赖服务
docker ps --filter "name=living-agent"

# 检查网络连通性
docker exec living-agent-service ping postgres
docker exec living-agent-service ping redis
```

### 9.2 WebSocket连接失败

**问题：** 前端显示「连接失败」

**排查步骤：**
```bash
# 确认后端运行
curl -s http://localhost:8382/api/health

# 检查WebSocket日志
docker logs living-agent-service --tail 100 | findstr "WebSocket"

# 验证Token有效性
curl -s http://localhost:8382/api/auth/user -H "Authorization: Bearer <token>"
```

### 9.3 消息无响应

**问题：** 发送消息后无任何响应

**排查步骤：**
```bash
# 检查模型守护进程
docker exec living-agent-service ps aux | grep model_daemon

# 检查Python进程
docker exec living-agent-service python3 -c "import sys; sys.path.insert(0, '/opt/python_scripts'); from model_daemon import *"

# 检查Ollama连通性
docker exec living-agent-service curl -s http://host.docker.internal:11434/api/tags
```

### 9.4 Qdrant/MemOS unhealthy

**问题：** 向量数据库或记忆系统不健康

**排查步骤：**
```bash
# 检查Qdrant健康状态
curl -s http://localhost:6333/health

# 检查MemOS健康状态
curl -s http://localhost:8381/product/users

# 重启相关服务
docker-compose restart qdrant
docker-compose restart memos memos-neo4j
```

---

## 十、关键配置文件

### 10.1 Docker Compose

**文件位置：** `f:\SoarCloudAI\docker\living-agent-service\docker-compose.yml`

**关键环境变量：**

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `DATABASE_URL` | PostgreSQL连接 | `jdbc:postgresql://postgres:5432/livingagent` |
| `REDIS_HOST` | Redis主机 | `redis` |
| `QDRANT_HOST` | Qdrant主机 | `qdrant` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka地址 | `kafka:9092` |
| `AI_MODELS_PATH` | 本地模型路径 | `/app/ai-models` |
| `OLLAMA_BASE_URL` | Ollama地址 | `http://192.168.0.249:2025` |

### 10.2 本地模型文件

**模型目录结构：**
```
f:\SoarCloudAI\ai-models\
├── Qwen3-0.6B-GGUF\
│   └── Qwen3-0.6B-Q8_0.gguf
├── Qwen3.5-2B-GGUF\
│   └── Qwen3.5-2B-Q4_K_M.gguf
├── sherpa-ncnn\
│   └── sherpa-ncnn-sense-voice-zh-en-ja-ko-yue-2025-09-09\
└── MeloTTS\
```

---

## 十一、联系方式

- 技术支持：[项目邮箱]
- 问题反馈：[GitHub Issues]
- 文档更新：[内部Wiki]

---

*本文档最后更新于 2026-04-10*
