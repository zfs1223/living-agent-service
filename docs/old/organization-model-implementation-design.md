# 组织模型落地设计

> 基于当前仓库里已有的 `EmployeeService`、`database/entity`、`database/repository`、部门与 brain 静态映射等结构整理。目标是在不假设“完整组织系统已存在”的前提下，把组织模型逐步收敛成可支撑部门对话、大脑配置、审计与查询的稳定基础层。

---

## 1. 目标与范围

本设计只讨论：

- 部门、成员、brain 的组织关系
- 组织数据的统一读取入口
- 与 `DepartmentApiController` 的联动
- 与 brain 运行状态、brain-models 展示的关系
- 组织维度的基础查询与持久化

本设计不直接讨论：

- 模型池 provider 管理
- 自动进化策略本身
- `/chat?id=...` agent 直连
- 前端页面细节

---

## 2. 当前代码实际状态

## 2.1 已存在的能力

### 组织相关接口
- `EmployeeService`
- `EmployeeService.listByDepartment(String departmentId)`

### 数据库层
- `living-agent-core/src/main/java/com/livingagent/core/database/entity/` 下已有多类实体
- `living-agent-core/src/main/java/com/livingagent/core/database/repository/` 下已有多类 repository

### 静态映射层
- `Department.mapDepartmentToBrain(...)`
- `Department.mapBrainToDepartment(...)`

### 控制器层
- `DepartmentApiController.getDepartmentMembers(...)`
- `DepartmentApiController.getMyDepartment(...)`
- `DepartmentApiController.getDepartmentInfo(...)`
- `DepartmentApiController.getDepartmentBrains(...)`

当前特点：
- 部门与 brain 的基础映射已存在
- 组织与员工能力不是空白
- 但 controller 当前主要还是靠 session + 静态映射 + 示例值支撑

---

## 3. 当前主要问题

### 3.1 组织信息来源不统一
- `AuthContext.getDepartment()` 能提供当前会话的 department
- 但这不等同于组织模型的唯一真实来源
- 如果员工库、部门结构、brain 配置发生变化，session 信息和数据库容易不一致

### 3.2 controller 仍然承担了过多组织拼装逻辑
- `getDepartmentMembers(...)` 直接构示例成员
- `getMyDepartment(...)` 直接基于 session 字段返回结果
- `getDepartmentBrains(...)` 直接构造静态 brain 列表

### 3.3 组织关系与 brain 配置关系还没收敛
- 部门属于组织模型
- brain 是运行态能力
- `brain-models` 是模型配置
- 这三者有关联，但当前还没有稳定读取层把它们串起来

### 3.4 缺少统一查询 service
- 现在 controller 如果继续发展，会各自去拼部门、成员、brain、绑定关系
- 后面接真实 repository 时改动面会很大

---

## 4. 职责边界

## 4.1 组织模型层应该负责什么

### 负责
- 查询部门、成员、brain 的组织关系
- 提供部门成员列表、我的部门、部门脑摘要
- 为 controller 提供统一查询入口
- 为审计、部门页、后台页提供基础组织维度数据

### 不负责
- 决定模型切换策略
- 执行脑推理
- 决定聊天入口路由
- provider / model pool 管理

## 4.2 静态映射层的定位

### `Department.mapDepartmentToBrain(...)`
应继续保留，但定位是：
- 静态兜底
- 初期默认映射
- 缺少真实配置时的 fallback

不应成为：
- 永久唯一数据源
- controller 的最终真相来源

## 4.3 建议新增：`OrganizationQueryService`

### 负责
- 统一封装部门、成员、brain、绑定摘要查询
- 给 `DepartmentApiController`、后台页、部门页提供统一读取接口
- 逐步吸收 controller 里的示例拼装逻辑

### 不负责
- brain 推理
- WebSocket 会话
- 自动进化决策

---

## 5. 与其它主题的关系

### 与部门对话的关系
- 部门对话需要真实部门成员、brain 摘要、我的部门信息
- 没有组织模型，`members` / `my` / `brains` 就会长期停留在示例值

### 与自动进化的关系
- 自动进化处理的是 brain-models
- 组织模型提供的是部门/成员/brain 关系
- 二者需要关联，但不能混为一层

### 与路由一致性的关系
- 路由只决定“走哪个入口”
- 组织模型决定“这个部门下有哪些成员、brain、绑定摘要可查”

---

## 6. 建议的落地顺序

## 6.1 P0：先统一组织读取入口

目标：避免 controller 继续直接构示例数据。

### 要做什么
1. 抽 `OrganizationQueryService`，或先统一走 `EmployeeService`
2. 让 `getDepartmentMembers(...)` 不再手工构示例成员
3. 让 `getMyDepartment(...)` 不只依赖 auth session 字段

## 6.2 P0：统一部门 / brain / 绑定摘要关系

目标：部门不只知道映射到哪个 brain，还知道这个 brain 当前的摘要状态。

### 要做什么
1. 保留 `Department.mapDepartmentToBrain(...)` 作为兜底
2. 优先从 `BrainRegistry` 读取运行时 brain
3. 如果要展示绑定模型，再接 `BrainModelAssigner` 或对应 brain-model service

## 6.3 P1：补基础 migration 与 repository 语义

目标：为后续组织查询和审计联动打底。

### 要做什么
1. 校准部门、成员、brain、绑定关系涉及的实体命名
2. 明确主键、外键、code/id 的语义
3. 优先支持最基础的查询链路，而不是一次性补满所有字段

## 6.4 P1：补会话与审计联动

目标：组织模型不仅能查配置，也能支撑运行与审计。

### 要做什么
1. 部门成员、部门 brain、部门对话记录、脑配置变更记录支持关联查询
2. 后续为后台页提供组织维度汇总查询

---

## 7. 关键实现建议

## 7.1 组织查询建议统一结构

建议统一一个部门摘要对象：

```json
{
  "code": "tech",
  "name": "技术部",
  "brain": "neuron://tech/tech-brain/001",
  "brainRunning": true,
  "modelConfigured": true,
  "memberCount": 12,
  "accessLevel": "FULL"
}
```

这样 controller 不需要自己分别拼多个来源。

## 7.2 成员查询建议统一结构

```json
{
  "employeeId": "emp_001",
  "name": "张三",
  "department": "tech",
  "status": "在线",
  "origin": "human"
}
```

至少要保证：
- id 稳定
- department 统一使用 code
- status 语义统一

## 7.3 命名规范建议

建议明确：
- `department`：业务 code，如 `tech` / `finance`
- `brainId`：运行/配置层唯一 ID，如 `neuron://tech/tech-brain/001`
- `brainType`：业务类型，如 `tech`
- `departmentName`：展示名，如“技术部”

不要混用这几个概念。

---

## 8. 开发任务清单版

> 本节按“文件名 / 方法名 / 具体改动 / 依赖关系 / 验收标准”整理，便于直接进入开发阶段。

### 8.1 P0：统一组织读取入口

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentApiController.java` | `getDepartmentMembers(...)` | 把示例成员替换成真实员工数据源调用，优先接 `EmployeeService.listByDepartment(...)` | 依赖 `EmployeeService` 实现、统一成员 DTO 结构 | `GET /api/dept/{department}/members` 不再返回固定示例员工，而能反映真实部门成员 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentApiController.java` | `getMyDepartment(...)` | 不只依赖 `AuthContext.getDepartment()`；增加与真实组织数据的校准逻辑 | 依赖组织查询层、auth session | 用户部门变更后，接口能反映真实部门而不长期停留在旧 session 值 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/`（建议新增） | `OrganizationQueryService` | 提供统一部门、成员、brain、绑定摘要查询入口 | 依赖 employee/repository/brain registry | controller 不再各自拼示例值与静态映射 |

### 8.2 P0：统一部门 / brain / 绑定摘要关系

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentApiController.java` | `getDepartmentBrains(...)` | 从静态 brain 列表升级为真实 brain 摘要查询，优先接 `BrainRegistry`，必要时补接 `BrainModelAssigner` | 依赖 `BrainRegistry`、可能的 `BrainModelAssigner`、组织查询 service | `GET /api/dept/{department}/brains` 返回真实 brain 状态和配置摘要 |
| `living-agent-core/src/main/java/com/livingagent/core/security/Department.java` | `mapDepartmentToBrain(...)` / `mapBrainToDepartment(...)` 使用规范 | 保留静态映射作为 fallback，但明确它不是最终真相来源 | 依赖 controller / query service 的统一约定 | controller 不再直接把静态映射当成全部组织真相 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/OrganizationQueryService.java`（建议新增） | 部门摘要查询方法 | 统一输出 `department`、`brainId`、`brainType`、`departmentName`、`brainRunning`、`modelConfigured` 等摘要字段 | 依赖 `BrainRegistry`、员工库、可能的 brain-model 配置服务 | 部门页和后台页可以直接消费统一摘要结构 |

### 8.3 P1：补基础 migration 与 repository 语义

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `living-agent-core/src/main/resources/db/migration/` | 部门/成员/brain 关系相关迁移 | 校准主键、外键、code/id 语义，补支持基础组织查询的迁移 | 依赖现有 entity/repository 命名与字段 | 数据库层可支持部门成员、部门 brain、绑定摘要的基础查询 |
| `living-agent-core/src/main/java/com/livingagent/core/database/entity/` | 组织相关实体字段校准 | 统一 `department`、`departmentCode`、`brainId` 等字段语义 | 依赖 migration 与 repository | entity 命名不再混乱，查询语义清晰 |
| `living-agent-core/src/main/java/com/livingagent/core/database/repository/` | 组织查询相关 repository 方法 | 补基础查询方法，避免 controller 侧拼装 | 依赖 entity 字段规范 | 组织查询层可以直接依赖 repository 输出基础结果 |

### 8.4 P1：补会话与审计联动

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/OrganizationQueryService.java`（建议新增） | 组织维度聚合方法 | 增加部门成员、brain、绑定、最近会话/变更摘要的聚合输出 | 依赖会话记录、审计记录、brain 配置记录 | 部门页 / 后台页能按组织维度查看基础运行摘要 |
| `living-agent-core/src/main/resources/db/migration/` | 会话/审计关联字段 | 补组织维度可关联的字段，支撑后续查询 | 依赖现有会话与审计实体 | 可以按部门或 brain 关联到对话、变更、审计记录 |

### 8.5 按优先级排序的后端待办表

| 优先级 | 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|---|
| P0 | `DepartmentApiController.java` | `getDepartmentMembers(...)` | 接真实员工数据源 | 依赖 `EmployeeService` | members 接口反映真实成员 |
| P0 | `DepartmentApiController.java` | `getMyDepartment(...)` | 校准 session 与真实组织数据 | 依赖组织查询层 | 我的部门接口结果稳定可信 |
| P0 | `OrganizationQueryService.java` | 新增统一查询入口 | 抽统一组织查询层 | 依赖 employee/repository/brain registry | controller 不再拼示例值 |
| P0 | `DepartmentApiController.java` | `getDepartmentBrains(...)` | 接真实 brain 摘要与绑定摘要 | 依赖 `BrainRegistry`、可能的 `BrainModelAssigner` | brains 接口返回真实状态 |
| P1 | `db/migration` / `entity` / `repository` | 组织查询相关迁移与字段校准 | 收敛主键/命名语义 | 依赖当前数据库模型 | 组织查询可稳定落库/查询 |
| P1 | `OrganizationQueryService.java` | 聚合摘要方法 | 打通会话、审计、配置摘要 | 依赖审计与会话记录 | 后台页可按组织维度聚合展示 |

---

## 9. 一句话结论

当前组织模型不是“没有基础”，而是：

- **接口与数据层骨架已经存在**
- **controller 仍然依赖静态映射和示例值**
- **真正缺的是统一查询层与稳定命名语义**

所以后续重点不是重造组织系统，而是：

**先把组织读取入口统一起来，再逐步把示例值替换成真实数据源。**
