# Living Agent Service 待修复问题清单

> 最后更新：2026-06-09
> 状态标记：🔴 未解决 | 🟡 部分解决 | 🟢 已修改未部署 | ✅ 已部署

---

## 本次会话已修复但未部署的问题

| 编号 | 问题 | 修复文件 | 状态 |
|------|------|----------|------|
| FIX-1 | `onReceiptRecorded` 监听路径 `executionResult=null` | `DepartmentChatService.java` | 🟢 已修改 |
| FIX-2 | 前端不处理 `execution_event` | `DepartmentChatInline.tsx` + `DepartmentDetail.tsx` | 🟢 已修改 |
| FIX-3 | `SelfImprovingTool` NPE (`Map.of` 不允许 null) | `SelfImprovingTool.java` | 🟢 已修改 |
| FIX-4 | MainBrain 工具迭代限制过低 (5→20) | `MainBrain.java` | 🟢 已修改 |
| FIX-5 | `DefaultExecutionReceiptReviewer` 降级验收太宽松 | `DefaultExecutionReceiptReviewer.java` | 🟢 已修改 |
| FIX-6 | WebSocket 僵尸会话循环（前端无心跳） | `DepartmentChatInline.tsx` | 🟢 已修改 |
| FIX-7 | 员工 delegateBrain 绑定无部门匹配校验 | `FixedEmployeeRegistry.java` | 🟢 已修改 |
| FIX-8 | 模型健康摘要观测缺失 | `GatewayConfig.java` | 🟢 已修改 |
| FIX-9 | 调试日志噪声偏高 | `application.yml` | 🟢 已修改 |
| **FIX-10** | **员工状态模型混乱：ONLINE/OFFLINE 与工作状态混用导致所有员工都在工作区** | **`EmployeeStatus.java` + `status.ts` + `EmployeeNeuron.java` + `DepartmentWebSocketHandler.java`** | **🟢 已修改** |

---

## P0 级：核心流程阻塞

### P0-1: DefaultExecutionReceiptReviewer 降级验收太宽松

- **现状**：降级验收（LLM 不可用时）只做关键词匹配，摘要为空也标记 accepted=true
- **修复**：已修改为更严格的降级验收逻辑：
  - 摘要为空 → accepted=false, needsRetry=true
  - 验收标准未满足 → accepted=false
  - 期望产物但无文件 → accepted=false, needsRetry=true
- **代码位置**：`living-agent-core/.../autonomy/impl/DefaultExecutionReceiptReviewer.java`
- **状态**：🟢 已修改未部署

### P0-2: 端到端 Trace 缺失最终聚合/收口

- **现状**：`triggerAsyncFinalResponse` 未被监听路径触发，导致 `main_brain_finalized` Trace 缺失
- **影响**：无法追踪任务从发起到最终响应的完整链路
- **修复方案**：FIX-1 部署后应解决（`onReceiptRecorded` 能正确触发 `triggerAsyncFinalResponse`）
- **状态**：🟡 依赖 FIX-1 部署验证

---

## P1 级：影响用户体验

### P1-1: WebSocket 僵尸会话循环

- **现状**：前端不发心跳，约 87 秒后被服务端判定为僵尸连接关闭，前端立即重连，形成死循环
- **修复**：前端添加 30 秒 ping 心跳，在 `ws.onopen` 启动，`ws.onclose` 清理
- **代码位置**：`frontend/src/pages/DepartmentDetail/DepartmentChatInline.tsx`
- **状态**：🟢 已修改未部署

### P1-2: 员工 delegateBrain 绑定到错误部门

- **现状**：`bindBrain()` 通过 `brainRegistry.getByDepartment()` 绑定，逻辑正确，但数据库中员工定义的 `departmentCode` 可能不正确
- **修复**：添加启动时部门匹配校验，日志输出 mismatch 详情
- **代码位置**：`living-agent-core/.../employee/registry/FixedEmployeeRegistry.java`
- **状态**：🟢 已修改未部署（需部署后根据日志核查数据库）

### P1-3: DockerSandboxService 不可用

- **现状**：代码存在 `DockerSandboxService` + `HybridSandboxService`，但容器内 Docker 不可用，降级为 `ARTIFACT_ONLY`
- **影响**：代码执行类任务无法真正运行，只能生成代码文本
- **修复方案**：配置 Docker-in-Docker 或使用宿主机 Docker socket
- **状态**：🟡 降级可用（需运维配置）

### P1-4: 跨部门协调缺失 CrossDepartmentCoordinator

- **现状**：代码中不存在 `CrossDepartmentCoordinator` 类，跨部门任务仍由 MainBrain 直接处理
- **影响**：跨部门任务缺乏专门的协调逻辑，可能遗漏部门间依赖
- **修复**：实现 `CrossDepartmentCoordinator`，处理跨部门任务分解和结果聚合
  - 新增 `CrossDepartmentCoordinator` 类，接受 `DepartmentExecutionCoordinator` 和 `AutonomyTraceService`
  - `coordinate()` 方法接收 `requestId`、`MainBrainTaskPlan`、`Map<String, DepartmentExecutionResult>`
  - `needsCrossDepartmentCoordination()` 静态方法判断是否需要跨部门协调
  - 在 `GatewayConfig` 注册 Bean，在 `DepartmentChatService` 中集成
- **代码位置**：`living-agent-core/.../autonomy/CrossDepartmentCoordinator.java`
- **状态**：� 已修改未部署

---

## P2 级：可观测性与体验优化

### P2-1: 模型健康摘要观测缺失

- **修复**：在 `GatewayConfig` 中添加 `@Scheduled` 定时任务，每 5 分钟输出模型健康摘要
- **代码位置**：`living-agent-gateway/.../config/GatewayConfig.java`
- **状态**：🟢 已修改未部署

### P2-2: 调试日志噪声

- **修复**：在 `application.yml` 中将高频非关键日志降为 INFO：
  - `DigitalEmployee` → INFO
  - `evolution` → INFO
  - `knowledge` → INFO
  - `FounderService` → INFO
- **代码位置**：`living-agent-app/src/main/resources/application.yml`
- **状态**：🟢 已修改未部署

### P2-3: 前端不展示需求状态

- **现状**：前端不处理 `requirementStatus`，用户看不到"等待澄清/等待确认/正式执行"
- **影响**：用户不知道任务处于什么阶段
- **修复**：在 `DepartmentChatInline.tsx` 中添加 `requirementStatus` state 和 UI 展示标签
  - 处理 `execution_event` 中的 `requirementStatus` 字段
  - 展示需求状态流转标签（草稿/需要澄清/已确认/规划中/执行中/已完成/失败）
- **代码位置**：`frontend/src/pages/DepartmentDetail/DepartmentChatInline.tsx`
- **状态**：� 已修改未部署

### P2-4: 需求冻结/防漂移逻辑缺失

- **现状**：`MainBrainTaskPlan` 有 `requirementVersion` 字段，但无冻结/防漂移逻辑
- **影响**：任务执行过程中需求可能被修改，导致结果不一致
- **修复**：实现需求冻结机制，执行中不允许修改需求
  - `MainBrainTaskPlan` 添加 `isRequirementFrozen()` — 状态 >= REQUIREMENT_CONFIRMED 视为冻结
  - `MainBrainTaskPlan` 添加 `withRequirementStatus()` — 带状态转换校验的不可变更新
  - `MainBrainTaskPlan` 添加 `withIncrementedVersion()` — 递增需求版本号
  - `DepartmentChatService` 添加 `activeSessionPlans` 映射追踪活跃计划
  - 新消息到达时检查是否有冻结计划，拒绝重新规划并推送 `requirement_frozen` 事件
  - 执行完成后自动清理活跃计划映射
  - 30 分钟超时自动清理防止内存泄漏
- **代码位置**：`MainBrainTaskPlan.java` + `DepartmentChatService.java`
- **状态**：� 已修改未部署

---

## 修复进度追踪

| 日期 | 修复项 | 状态 |
|------|--------|------|
| 2026-06-08 | FIX-1~4: receipt缓存/前端事件/NPE/迭代限制 | 🟢 已修改未部署 |
| 2026-06-08 | FIX-5: ReceiptReviewer 降级验收严格化 | 🟢 已修改未部署 |
| 2026-06-08 | FIX-6: WebSocket 30秒心跳 | 🟢 已修改未部署 |
| 2026-06-08 | FIX-7: 员工绑定部门匹配校验 | 🟢 已修改未部署 |
| 2026-06-08 | FIX-8: 模型健康5分钟周期摘要 | 🟢 已修改未部署 |
| 2026-06-08 | FIX-9: 日志降噪 | 🟢 已修改未部署 |
| 2026-06-08 | P1-4: CrossDepartmentCoordinator 跨部门协调 | 🟢 已修改未部署 |
| 2026-06-08 | P2-3: 前端需求状态展示 | 🟢 已修改未部署 |
| 2026-06-08 | P2-4: 需求冻结/防漂移逻辑 | 🟢 已修改未部署 |
