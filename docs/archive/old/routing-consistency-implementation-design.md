# 路由一致性落地设计

> 基于 `对话入口逻辑梳理.md`、当前前端页面入口结构，以及后端 `DepartmentApiController` / `DepartmentWebSocketHandler` / `AgentWebSocketHandler` 的实际职责整理。目标是在不改写现有规则的前提下，把“硬路由 / 软路由”的边界落到代码约束上。

---

## 1. 目标与范围

本设计只讨论：

- `/chat?id=...`
- `/chat?brain=...`
- 无参数 `/chat`
- `/api/dept/{department}` 与 `/ws/dept/{brain}` 的边界
- agent 与 department 两类入口的职责隔离

本设计不直接讨论：

- 自动进化策略
- provider / model pool 管理
- 组织模型本身

---

## 2. 当前代码实际状态

## 2.1 已存在的能力

### 文档规范
- `对话入口逻辑梳理.md` 已明确：
  - `id` 是 agent 硬路由
  - `brain` 是 department/brain 硬路由
  - 无参数 `/chat` 才是身份软路由

### 后端链路
- `DepartmentApiController` 只处理 `/api/dept/*`
- `DepartmentWebSocketHandler` 已按 URI 提取 department
- `AgentWebSocketHandler` 与 `DepartmentWebSocketHandler` 是分开的

### 前端入口
- `Chat.tsx`
- `DepartmentDetail.tsx`
- `AgentDetail.tsx`

当前特点：
- 规则已经被文档定义
- 后端也已经按 agent / department 拆了 handler
- 但随着业务继续扩展，最容易偏移的是“入口语义”而不是底层通道能力

---

## 3. 当前主要问题

### 3.1 部门 REST 接口容易被误用成通用聊天入口
- `DepartmentApiController.chat(...)` 当前存在
- 如果不强调边界，后续很容易被理解成“另一种默认聊天入口”
- 这会冲掉 `id > brain > 身份软路由` 的判定顺序

### 3.2 REST 与 WebSocket 的部门语义还没完全统一
- WebSocket 部门链路已经比较接近真实处理
- REST 部门聊天还偏示意
- 前端若同时接两种入口，可能看到不同错误模型与响应结构

### 3.3 department / brain / brainId 命名可能错位
- 前端 URL 里可能传 `brain`
- 后端 URI 里可能提取的是 `department`
- 配置层又可能使用 `brainId`
- 如果没有统一映射规范，后面会持续出错

---

## 4. 正确的边界关系

```text
/chat?id=...           -> agent 硬路由
/chat?brain=...        -> department/brain 硬路由
/chat                  -> 身份软路由
/api/dept/{department} -> 部门信息与部门 chat 的 REST 补充接口
/ws/dept/{brain}       -> 部门脑会话链路
/ws/agent              -> agent 会话链路
```

### 核心原则
- 入口路由决定“走谁”
- 后端 handler/controller 决定“怎么处理”
- 自动进化决定“那个 brain 用什么模型”
- 三者不能互相越权

---

## 5. 职责边界

## 5.1 `Chat.tsx`

### 负责
- 按 URL 参数做入口判定
- 保持 `id > brain > 身份软路由`

### 不负责
- 定义部门脑内部推理逻辑
- 决定模型切换策略

## 5.2 `DepartmentDetail.tsx`

### 负责
- 提供部门页到部门脑入口的跳转
- 明确跳到 `brain` 硬路由语义

### 不负责
- 代理 agent 直连
- 决定无参数 `/chat` 的 fallback

## 5.3 `AgentDetail.tsx`

### 负责
- 提供 agent 详情页到 `id` 直连入口的跳转

### 不负责
- 转成部门脑入口
- 混用部门 chat 语义

## 5.4 `DepartmentApiController`

### 负责
- 部门信息查询
- 部门 chat 的 REST 补充接口

### 不负责
- 处理 `id` 直连
- 决定无参数 `/chat` 的身份软路由

## 5.5 `DepartmentWebSocketHandler`

### 负责
- 处理部门脑会话链路
- 服务 `/ws/dept/{brain}` 语义

### 不负责
- 处理 agent 直连
- 处理无参数软路由

---

## 6. 建议的落地顺序

## 6.1 P0：统一部门入口语义

目标：REST 的部门入口只是部门脑硬路由的补充，不是新的软路由规则。

### 要做什么
1. 明确 `DepartmentApiController.chat(...)` 的语义
2. 让 `/api/dept/{department}/chat` 与 `/ws/dept/{brain}` 的错误模型和返回语义尽量一致
3. 在文档与代码注释中都强调：这不是通用聊天入口

## 6.2 P0：统一命名映射

目标：department / brain / brainId 三套命名在前后端不再错位。

### 要做什么
1. 明确前端 URL 中的 `brain` 参数代表什么
2. 明确 `department` code 与 `brainId` 的映射关系
3. 统一 controller / handler / 配置层使用方式

## 6.3 P1：统一错误与 fallback 语义

目标：同一类异常在不同入口中不再表现不一致。

### 要做什么
1. 统一权限不足、未配置、初始化中、无响应等错误语义
2. 明确 fallback 只发生在哪一层
3. 避免把部门脑错误 fallback 到 agent 直连或身份软路由

## 6.4 P1：形成回归检查项

目标：以后改聊天入口时，不会再次破坏当前规则。

### 要做什么
1. 把 `id > brain > 身份软路由` 加入开发检查项
2. 对部门入口、agent 入口的职责边界做文档化约束

---

## 7. 关键实现建议

## 7.1 建议统一参数语义

建议明确：
- `department`：业务部门 code，如 `tech`
- `brain`：前端 URL 层使用的 brain 语义参数
- `brainId`：后端运行/配置层唯一 ID

如果前端 URL 用的是 `brain=tech`，那就要明确它本质上映射的是部门 brain，而不是完整 `brainId`。

## 7.2 建议统一部门错误结构

无论 REST 还是 WebSocket，至少应能统一到：
- `FORBIDDEN`
- `PERMISSION_DENIED`
- `INITIALIZING`
- `BRAIN_NOT_FOUND`
- `SYSTEM_ERROR`

## 7.3 建议统一回归检查问题

每次改入口时至少检查：
1. `/chat?id=...` 是否仍然优先于 `brain`
2. `/chat?brain=...` 是否仍然保持部门脑硬路由语义
3. 无参数 `/chat` 是否仍然只做身份软路由
4. `DepartmentApiController` 是否没有吞掉 agent 入口语义

---

## 8. 开发任务清单版

> 本节按“文件名 / 方法名 / 具体改动 / 依赖关系 / 验收标准”整理，重点关注规则落地而不是重写路由。

### 8.1 P0：统一部门入口语义

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `frontend/src/pages/Chat.tsx` | URL 参数判定逻辑 | 明确保留 `id > brain > 身份软路由` 顺序，避免后续改动打乱优先级 | 依赖 `DepartmentDetail.tsx` / `AgentDetail.tsx` 的跳转语义 | `/chat?id=...` 始终优先于 `brain`；无参数 `/chat` 仍只做软路由 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentApiController.java` | `chat(...)` | 明确这是部门脑硬路由的 REST 补充接口，不承担默认聊天入口职责 | 依赖部门对话设计与错误语义统一 | controller 不会被继续扩展成通用聊天入口 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | department 提取与处理逻辑 | 保持 handler 只服务于部门脑会话，不覆盖 agent 直连 | 依赖 URI 约定与前端 brain 参数映射 | `/ws/dept/{brain}` 只服务部门链路，不混入 agent 语义 |

### 8.2 P0：统一错误与 fallback 语义

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentApiController.java` | `chat(...)` 错误返回 | 统一 REST 部门入口错误结构 | 依赖部门对话统一错误模型 | 前端可按统一错误类别处理 REST 部门 chat |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | error / initializing / permission 分支 | 统一 WebSocket 部门入口错误语义 | 依赖部门对话统一错误模型 | REST / WebSocket 部门入口错误结构可映射 |
| `frontend/src/pages/Chat.tsx` | 错误处理状态 | 不再按入口类型各自硬编码错误语义，统一消费标准错误码 | 依赖后端统一错误结构 | 同类错误在不同入口显示一致 |

### 8.3 P1：统一命名映射

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `frontend/src/pages/Chat.tsx` | `brain` 参数解析 | 明确 `brain` 参数到底表示 department code 还是更完整的 brain 语义 | 依赖后端 department / brain / brainId 命名规范 | 前端不会把 `tech`、`tech-brain`、`neuron://...` 混成一个字段 |
| `frontend/src/pages/DepartmentDetail.tsx` | 部门跳转方法 | 明确它只负责跳到 `brain` 硬路由语义 | 依赖 `Chat.tsx` 路由规则 | 部门页不会误跳到 agent 直连或无参数 `/chat` |
| `frontend/src/pages/AgentDetail.tsx` | agent 跳转方法 | 明确它只负责跳到 `id` 直连 | 依赖 `Chat.tsx` 路由规则 | agent 详情页不会被部门脑逻辑覆盖 |
| `living-agent-core/src/main/java/com/livingagent/core/security/Department.java` | `mapDepartmentToBrain(...)` / `mapBrainToDepartment(...)` 使用规范 | 统一前后端对 department / brain 映射的理解 | 依赖组织模型与部门对话设计 | 前后端对同一 department/brain 的表达一致 |

### 8.4 P1：形成回归检查项

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `docs/planning/对话入口逻辑梳理.md` | 文档校准 | 保持文档与当前实现同步，明确边界与错误语义 | 依赖当前前后端实现 | 文档不再落后于代码实现 |
| `docs/planning/routing-consistency-implementation-design.md` | 开发检查项维护 | 增加入口一致性回归检查项 | 依赖团队开发流程 | 以后改聊天入口时有明确检查清单 |
| 前端/后端测试或验收脚本（如后续新增） | 路由回归校验 | 为 `id` / `brain` / 无参数 `/chat` 增加最小回归校验 | 依赖测试体系 | 变更后能快速验证三类入口语义未被破坏 |

### 8.5 按优先级排序的开发待办表

| 优先级 | 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|---|
| P0 | `Chat.tsx` | URL 参数判定逻辑 | 固化 `id > brain > 身份软路由` | 依赖页面跳转语义 | 三类入口顺序不变 |
| P0 | `DepartmentApiController.java` | `chat(...)` | 明确只是部门脑 REST 补充 | 依赖部门对话设计 | 不被误用成默认聊天入口 |
| P0 | `DepartmentWebSocketHandler.java` | handler 处理逻辑 | 保持只服务部门链路 | 依赖 URI/brain 语义 | 不吞 agent 直连 |
| P0 | `DepartmentApiController.java` / `DepartmentWebSocketHandler.java` / `Chat.tsx` | 错误处理相关逻辑 | 统一错误与 fallback 语义 | 依赖统一错误结构 | 同类错误前后端表现一致 |
| P1 | `Chat.tsx` / `DepartmentDetail.tsx` / `AgentDetail.tsx` | 跳转与解析逻辑 | 统一 `department` / `brain` / `brainId` 命名映射 | 依赖 `Department.java` 映射规范 | 前后端命名不再错位 |
| P1 | 规划文档与测试检查项 | 回归检查 | 增加入口一致性检查清单 | 依赖开发流程 | 后续迭代不轻易破坏现有规则 |

---

## 10. 入口一致性回归检查清单

> 每次修改聊天入口相关代码时，必须逐项检查以下问题。这是防止路由语义被破坏的最后一道防线。

### 10.1 核心路由顺序检查

| 检查项 | 检查内容 | 涉及文件 | 验收标准 |
|---|---|---|---|
| R1 | `/chat?id=...` 是否仍然优先于 `brain` | `Chat.tsx` | `id` 参数存在时，绝不走 brain 路由 |
| R2 | `/chat?brain=...` 是否仍然保持部门脑硬路由语义 | `Chat.tsx`, `DepartmentWebSocketHandler.java` | brain 参数存在时，固定走 `/ws/dept/{brain}` |
| R3 | 无参数 `/chat` 是否仍然只做身份软路由 | `Chat.tsx` | 无 id/brain 时，根据用户身份选择通道 |
| R4 | `DepartmentApiController` 是否没有吞掉 agent 入口语义 | `DepartmentApiController.java` | controller 不处理 id 直连 |

### 10.2 错误语义一致性检查

| 检查项 | 检查内容 | 涉及文件 | 验收标准 |
|---|---|---|---|
| E1 | REST 与 WebSocket 部门入口的错误结构是否可映射 | `DepartmentApiController.java`, `DepartmentWebSocketHandler.java` | 同类错误码在 REST/WS 中表现一致 |
| E2 | 权限不足错误是否统一使用 `PERMISSION_DENIED` | 所有 controller/handler | 前端可按统一错误类别处理 |
| E3 | 初始化中错误是否统一使用 `INITIALIZING` | `DepartmentWebSocketHandler.java` | 用户看到一致的等待提示 |
| E4 | fallback 是否只发生在正确层级 | 全链路 | 部门脑错误不会 fallback 到 agent 直连或身份软路由 |

### 10.3 命名映射一致性检查

| 检查项 | 检查内容 | 涉及文件 | 验收标准 |
|---|---|---|---|
| N1 | 前端 URL 中 `brain` 参数是否代表部门 code | `Chat.tsx`, `DepartmentDetail.tsx` | `brain=tech` 映射到 TechBrain，不混淆 |
| N2 | `department` code 与 `brainId` 是否不混用 | `Department.java`, 全链路 | 不使用 `tech-brain` 或 `neuron://...` 作为 brain 参数 |
| N3 | 后端 controller/handler 是否使用统一映射方法 | `DepartmentApiController.java`, `DepartmentWebSocketHandler.java` | 都使用 `mapDepartmentToBrain()` |

### 10.4 职责边界检查

| 检查项 | 检查内容 | 涉及文件 | 验收标准 |
|---|---|---|---|
| B1 | `Chat.tsx` 是否只做路由判定，不定义部门脑内部逻辑 | `Chat.tsx` | 不包含模型切换、推理逻辑 |
| B2 | `DepartmentDetail.tsx` 是否只跳到 brain 硬路由 | `DepartmentDetail.tsx` | 不跳到 agent 直连或无参数 /chat |
| B3 | `AgentDetail.tsx` 是否只跳到 id 直连 | `AgentDetail.tsx` | 固定员工隐藏 chat tab |
| B4 | `DepartmentWebSocketHandler` 是否只服务部门脑会话 | `DepartmentWebSocketHandler.java` | 不处理 agent 直连、无参数软路由 |

### 10.5 固定数字员工规则检查

| 检查项 | 检查内容 | 涉及文件 | 验收标准 |
|---|---|---|---|
| F1 | 固定员工是否禁止通过 `/ws/agent` 直连 | `AgentWebSocketHandler.java`, `Chat.tsx` | origin=fixed 时降级到 /ws/public |
| F2 | 固定员工是否在详情页隐藏 chat tab | `AgentDetail.tsx` | canShowChatTab=false 当 origin=fixed |
| F3 | 固定员工是否只能通过部门大脑管理 | 全链路 | 无其他直连入口暴露 |

---

## 11. 开发任务完成状态

> 本节记录路由一致性落地设计的实施状态。

### 已完成 (P0 + P1)

| 任务 | 优先级 | 状态 | 说明 |
|---|---|---|---|
| 为 DepartmentApiController.chat() 添加职责边界注释 | P0 | ✅ | 明确这是部门脑REST补充，非通用聊天入口 |
| 统一错误语义与工厂方法 | P0 | ✅ | ChatResponse 添加 error()/success() 工厂方法 |
| 为 DepartmentWebSocketHandler 添加职责注释 | P0 | ✅ | 明确只服务部门脑会话链路 |
| 为 Chat.tsx 路由逻辑添加详细注释 | P0 | ✅ | 固化 id > brain > 身份软路由 顺序 |
| 统一 brain/department/brainId 命名映射注释 | P0 | ✅ | 前端和后端都添加了映射说明 |
| AgentDetail.tsx 固定员工 chat tab 隐藏逻辑确认 | P1 | ✅ | 逻辑已正确实现，添加注释说明 |
| Department.java 映射方法注释 | P1 | ✅ | 添加命名映射规范文档注释 |
| 添加回归检查清单 | P1 | ✅ | 本章即为新增的回归检查项 |

---

## 12. 一句话结论

当前路由一致性问题不是“没有规则”，而是：

- **规则已经有了**
- **入口链路也已经拆开了**
- **最容易出问题的是后续迭代时再次混用语义**

所以后续重点不是重新设计路由，而是：

**把现有规则持续落到前后端命名、错误语义和职责边界上。**
