# 网关模块

> 版本：2026-05-18 | 路径：living-agent-gateway/

## WebSocket 端点

| 端点 | 处理器 | 说明 |
|------|--------|------|
| `/ws/public` | `DepartmentWebSocketHandler` | 公共闲聊（未登录） |
| `/ws/dept/*` | `DepartmentWebSocketHandler` | 部门大脑对话 |
| `/ws/enterprise` | `DepartmentWebSocketHandler` | 董事长频道 |
| `/ws/agent` | `AgentWebSocketHandler` | Agent 直连 |

## DepartmentWebSocketHandler 核心逻辑

```java
afterConnectionEstablished() {
    1. 从 URI 提取 department
    2. 验证访问权限（access level）
    3. 提取认证 token
    4. 验证 token
    5. 存储 session → department 映射
    6. 加入 department channel
}

handleTextMessage() {
    1. 解析 JSON 消息
    2. 验证认证状态
    3. 广播消息到 channel
    4. 调用 processWithBrain() 处理
}

processWithBrain() {
    1. 发送 "thinking" 指示器
    2. 调用 departmentChatService.processDepartmentChat()
    3. 发送响应到客户端
}
```

## 消息格式

```json
// 客户端发送
{
    "type": "chat",
    "content": "用户消息内容",
    "metadata": {}
}

// 服务端响应
{
    "type": "thinking",      // 或 "response"
    "content": "...",
    "metadata": {}
}
```

## 权限检查顺序

```
1. 登录状态检查
   ↓ 未登录 → 只能 /ws/public
2. 身份验证
   ↓ token 无效 → UNAUTHORIZED
3. 访问级别检查
   ↓ CHAT_ONLY → 仅 /ws/public, allowedModels={Qwen3-0.6B}, allowedBrains={}
   ↓ LIMITED → /ws/public + AdminBrain/CsBrain, allowedModels={Qwen3-0.6B, Qwen3.5-2B}
   ↓ DEPARTMENT → /ws/public + 本部门大脑, allowedModels={Qwen3-0.6B, Qwen3.5-2B}
   ↓ FULL → 所有端点, allowedModels=全部, allowedBrains=全部含 MainBrain
4. 部门权限
   ↓ 非董事长 → 只能本部门
```

## AccessLevel 详情

| 级别 | level值 | allowedModels | allowedBrains | canAccessKnowledge |
|------|---------|---------------|---------------|-------------------|
| `CHAT_ONLY` | 0 | Qwen3-0.6B | (空) | false |
| `LIMITED` | 1 | Qwen3-0.6B, Qwen3.5-2B | AdminBrain, CsBrain | true |
| `DEPARTMENT` | 2 | Qwen3-0.6B, Qwen3.5-2B | 本部门大脑 | true |
| `FULL` | 3 | 全部 | 全部含 MainBrain | true |

## DepartmentChatService 核心逻辑

```java
processDepartmentChat() {
    1. 验证会话和权限
    2. 异步调用 processDepartmentBrainAsync()
}

processDepartmentBrainAsync() {
    1. 调用 conversationOrchestrator.orchestrate()（意图分析 + 任务规划）
    2. 员工分配规划 → 分配准备 → 部门执行协调
    3. 在 processWithBrain() 中调用 brain.process(brainMessage)
    4. 处理大脑响应：
       a. 收集执行回执
       b. 结果聚合
       c. 响应组合
       d. 最终响应协调
       e. 知识/绩效/产物捕获
    5. 返回结果
}
```

## AgentWebSocketHandler

```java
// 处理 /ws/agent 路径
// 职责：
// 1. origin=personal 的个人助理直连
// 2. origin=human 的可选人类员工直连
// 3. origin=fixed 被禁止直连（前端已降级）
```

## 部门到大脑映射

```java
// Department.mapDepartmentToBrain()
"tech" → "TechBrain"
"hr" → "HrBrain"
"finance" → "FinanceBrain"
"sales" → "SalesBrain"
"cs" → "CsBrain"
"admin" → "AdminBrain"
"legal" → "LegalBrain"
"ops" → "OpsBrain"
"main"/"core" → "MainBrain"
```

## 代码路径

```
gateway/
├── websocket/
│   ├── DepartmentWebSocketHandler.java  # 部门 WebSocket
│   └── AgentWebSocketHandler.java      # Agent WebSocket
├── service/
│   ├── DepartmentChatService.java      # 部门聊天服务
│   └── AgentService.java               # Agent 服务
├── config/
│   └── GatewayConfig.java              # Bean 注册
└── controller/
    └── *.java                          # REST API
```

## 快速定位

| 需求 | 文件 |
|------|------|
| 修改 WebSocket 入口逻辑 | `DepartmentWebSocketHandler.java` |
| 修改权限检查 | `DepartmentWebSocketHandler.java` (afterConnectionEstablished) |
| 修改部门映射 | `Department.mapDepartmentToBrain()` |
| 修改聊天处理逻辑 | `DepartmentChatService.java` |
| 添加新 WebSocket 端点 | `WebSocketConfig.java` |
| 修改访问级别 | `AccessLevel.java` |
