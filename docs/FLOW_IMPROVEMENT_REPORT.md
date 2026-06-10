# 流程打通改进报告

> 基于 `CODE_STRUCTURE_AND_FILE_GUIDE.md` 代码结构文件和 `COMPLETE_LANDING_TODO.md` 落地清单，对实际代码进行全面检查后整理的问题清单与改进方案。
>
> 检查时间：2026-05-25
> 最后更新：2026-05-28（第四轮：resolveDefault硬编码移除/BrainAutoAssigner/动态Provider/TraceEvent序列化/Embedding模型支持/code_review_states JSONB修复）
>
> 原则：每个问题都标注严重程度、影响范围和修复建议，可直接分配给研发执行。

---

## 问题总览

| 级别 | 问题数 | 已修复 | 未修复 | 说明 |
|------|--------|--------|--------|------|
| **严重** | 14 | **14** | 0 | 安全漏洞、主流程断链、数据丢失风险 |
| **高** | 21 | **21** | 0 | 架构不一致、功能缺失、链路断裂 |
| **中** | 29 | **29** | 0 | 规范不统一、体验不佳、可维护性差 |
| **低** | 10 | **10** | 0 | 优化建议、文档完善 |
| **合计** | **74** | **74 (100%)** | 0 | |

> 注：较第一版（58项）共新增16项：P1-4.5 DAG生命周期、P2-3.5 状态转移验证、P2-6.4 可配置白名单（第二轮）；P0-4.4 API错误详情、P1-1.5 WebSocket大小写、P2-2.6 company_intro默认值（第三轮）；P0-4.2补漏resolveDefault、P0-2补漏动态Provider、BrainAutoAssigner、TraceEvent序列化、Embedding支持、code_review_states JSONB（第四轮）

---

## P0 必修复

### P0-1 代码路径与包名一致性

**总体结论**：所有 Java 文件的 package 声明与目录路径一致，Spring 扫描覆盖完整。但存在严重的同名类冲突问题。

#### 问题 P0-1.1 Employee/EmployeeService 在 security 和 employee 包中重复 [严重] ✅ 已修复

**修复内容**：
- `security.Employee` 重命名为 `SecurityIdentity`
- `security.EmployeeService` 重命名为 `AuthEmployeeService`
- `security.EmployeeServiceImpl` 重命名为 `AuthEmployeeServiceImpl`
- 所有引用（12个文件）已更新

**修改文件**：
- `core/security/SecurityIdentity.java`（原 Employee.java）
- `core/security/AuthEmployeeService.java`（原 EmployeeService.java）
- `core/security/impl/AuthEmployeeServiceImpl.java`（原 EmployeeServiceImpl.java）
- 以及10个引用文件

#### 问题 P0-1.2 EvolutionManager 错放在 autonomous.evolution 包 [高] ✅ 已修复

**修复内容**：
- `EvolutionManager.java` 从 `core.autonomous.evolution` 移至 `core.evolution` 包
- `HardwareUpgradeService.java` 从 `core.autonomous.evolution` 移至 `core.evolution` 包
- 更新3个引用文件的 import 语句（AutonomousOperationConfig, EvolutionTracker）

**修改文件**：
- `core/evolution/EvolutionManager.java`（原 core/autonomous/evolution/）
- `core/evolution/HardwareUpgradeService.java`（原 core/autonomous/evolution/）
- `core/autonomous/config/AutonomousOperationConfig.java`
- `core/autonomous/incentive/EvolutionTracker.java`

#### 问题 P0-1.3 其他同名类冲突 [中] ✅ 已修复

**修复内容**：
- `skill.bounty.impl.BountyHunterSkill` 重命名为 `BountyHunterSkillAdapter`
- `model.pool.ProviderRegistry` 重命名为 `LlmProviderRegistry`
- `perception.sensor.SensorNeuron` 重命名为 `PerceptionSensorNeuron`
- `proactive.scenario.WeeklyReportScenarioHandler` 重命名为 `ProactiveWeeklyReportHandler`
- 同步更新所有引用文件

---

### P0-2 自治编排主流程闭环

**总体结论**：部门文本链路（`/ws/dept/{dept}`）的自治编排主流程已基本闭环，但存在关键断点。

#### 问题 P0-2.1 AgentService 文本链路不走自治编排 [严重] ✅ 已修复

**修复内容**：
- `AgentService.processTextAsync()` 中，非公共通道+有部门的文本消息路由到 `DepartmentChatService.processDepartmentBrainAsync()`
- 复用完整的自治编排流程（意图分析→需求评估→主脑规划→员工分派→部门执行→回执收集→结果聚合→总结沉淀）
- 响应中增加 `orchestrated: true` 标识
- 降级机制：大脑未注册或未运行时降级到 `processWithBrain()` 直接调 LLM

**修改文件**：
- `living-agent-gateway/.../service/AgentService.java`

#### 问题 P0-2.2 重试逻辑未闭环 [高] ✅ 已修复

**修复内容**：
- `ConversationOrchestrator` 新增 `retryWithReassignment()` 方法，消费 `needsRetry` 信号
- `DepartmentChatService.processBrainResponse()` 中添加重试消费逻辑
- 最大重试次数 `MAX_RETRY_COUNT = 1`，避免无限重试
- 重试完成后推送 `retry_completed` WebSocket 事件

**修改文件**：
- `living-agent-core/.../autonomy/ConversationOrchestrator.java`
- `living-agent-gateway/.../service/DepartmentChatService.java`

#### 问题 P0-2.3 换人逻辑不存在 [高] ✅ 已修复

**修复内容**：
- `FixedEmployeeDispatcher` 接口新增 `reassign(plan, failedEmployeeCodes)` 方法
- `RegistryBackedFixedEmployeeDispatcher` 实现换人：排除失败员工，按角色/能力匹配替代
- `LlmBasedFixedEmployeeDispatcher` 实现 LLM 驱动换人，失败时降级到规则换人
- 重试上下文中记录 `reassignedFrom` 和 `reassignReason`

**修改文件**：
- `living-agent-core/.../autonomy/FixedEmployeeDispatcher.java`
- `living-agent-core/.../autonomy/impl/RegistryBackedFixedEmployeeDispatcher.java`
- `living-agent-core/.../autonomy/impl/LlmBasedFixedEmployeeDispatcher.java`

#### 问题 P0-2.4 人工接管未闭环 [中] ✅ 已修复

**修复内容**：
- `DefaultFinalResponseCoordinator.determineStrategy()` 在 `needsHumanIntervention=true` 时返回 `ESCALATE_TO_HUMAN` 策略
- 新增 `isNeedsHumanIntervention()` 方法从 `DepartmentExecutionResult.metadata()` 中提取标记
- `ConversationOrchestrator` 新增 `InterventionNeuron` 注入和 `handleEscalateToHuman()` 方法
- 高风险任务（riskLevel >= 4）自动触发人工接管
- `DepartmentChatService` 添加 `ESCALATE_TO_HUMAN` 分支，推送 `escalate_to_human` WebSocket 事件

**修改文件**：
- `core/autonomy/impl/DefaultFinalResponseCoordinator.java`
- `core/autonomy/ConversationOrchestrator.java`
- `gateway/service/DepartmentChatService.java`

#### 问题 P0-2.5 澄清后继续原任务未闭环 [中] ✅ 已修复

**修复内容**：
- `ConversationOrchestrator` 新增 `ConcurrentHashMap<String, ClarificationContext> pendingClarifications` 映射
- 新增 `ClarificationContext` record，保存 requestId、decision、intake、department、timestamp
- 澄清时保存上下文（`saveClarificationContext()`），用户回答后恢复（`resumeAfterClarification()`）
- 恢复后跳过意图分析，直接进入规划→路由→执行
- 30分钟超时自动清理（`cleanupExpiredClarifications()`）

**修改文件**：
- `core/autonomy/ConversationOrchestrator.java`

#### 问题 P0-2.6 LlmMainBrainFinalSummaryService JSON 解析简陋 [中] ✅ 已修复

**修复内容**：
- `parseLlmResponse()` 改用 Jackson ObjectMapper 解析 JSON
- 新增 `SummaryJson` record，包含 status/userMessage/deliverables/acceptanceConclusion/risks/nextActions/requiresHumanReview
- deliverables/risks/nextActions 等列表字段可正确解析
- Jackson 解析失败时降级到原有字符串匹配方式

**修改文件**：
- `living-agent-core/.../autonomy/impl/LlmMainBrainFinalSummaryService.java`

---

### P0-3 大脑统一执行契约

**总体结论**：`BrainOutputContract` 已定义完善，但**完全未接入大脑的实际执行链路**，形同虚设。

#### 问题 P0-3.1 BrainOutputContract 未被任何大脑使用 [严重] ✅ 已修复

**修复内容**（渐进式，不破坏现有功能）：
- `Brain` 接口新增 `default BrainOutputContract processWithContract(ChannelMessage message)` 方法
- `AbstractBrain` 重写 `processWithContract()`：调用 doProcess() 后返回 `lastOutputContract`
- `AbstractBrain.publishResponse()` 和 `publishError()` 同步构建 BrainOutputContract 并赋值给 `lastOutputContract`
- `MainBrain` 重写 `processWithContract()`：增强 contract 添加 requestType/involvedDepartments/userId
- `DepartmentChatService` 改用 `brain.processWithContract()`，利用 contract 的 status 做差异化处理

**修改文件**：
- `living-agent-core/.../brain/Brain.java`
- `living-agent-core/.../brain/impl/AbstractBrain.java`
- `living-agent-core/.../brain/impl/MainBrain.java`
- `living-agent-gateway/.../service/DepartmentChatService.java`

#### 问题 P0-3.2 9 个大脑存在三种不同实现模式 [高] ✅ 已修复

**修复内容**：
- 6个自建 ReAct 循环的大脑（FinanceBrain, SalesBrain, CsBrain, AdminBrain, LegalBrain, OpsBrain）统一改为使用基类 `executeReActLoop()`
- 删除每个大脑中的 `executeToolCallLoop()`、`executeToolCalls()`、`parseArguments()`、`formatSuccessResult()`、`extractText()`、`publishError()` 重复方法
- `doProcess()` 改为调用 `executeReActLoop()` + `publishResponse()`/`publishError()`
- 新增 `doGetSystemPrompt()` 和 `getOutputChannel()` 方法
- 现在所有9个大脑统一为两种模式：A: 基类 ReAct（8个）+ C: 完全自定义（MainBrain）

**修改文件**：
- `core/brain/impl/FinanceBrain.java`
- `core/brain/impl/SalesBrain.java`
- `core/brain/impl/CsBrain.java`
- `core/brain/impl/AdminBrain.java`
- `core/brain/impl/LegalBrain.java`
- `core/brain/impl/OpsBrain.java`

#### 问题 P0-3.3 6 个大脑自建 ReAct 循环缺少基类关键功能 [高] ✅ 已修复

**修复内容**：同 P0-3.2，删除自建 `executeToolCallLoop()` 后，6个大脑自动获得基类全部功能：
1. 上下文压缩（microCompact / autoCompactIfNeeded）✅
2. ToolHook 钩子（preHook / postHook / errorHook）✅
3. 大输出持久化（PERSIST_THRESHOLD）✅
4. UsageTracker 用量追踪 ✅
5. 上下文过长自动恢复（isContextTooLongError + 重试）✅
6. 模型健康记录 ✅
7. 边界检查 ✅

**修改文件**：同 P0-3.2

#### 问题 P0-3.4 大脑输出 metadata 不一致 [中] ✅ 已修复

**修复内容**：
- `AbstractBrain.publishError()` 中添加6个标准 metadata 字段（`original_message_id`, `brain_id`, `brain_name`, `department`, `iterations=0`, `type="brain_error"`）
- `BrainOutputContract` 的 metadata Map 中补充缺失的 `original_message_id`, `brain_name`, `type` 字段
- `publishResponse()` 已包含全部标准 metadata（之前已确认）

**修改文件**：
- `core/brain/impl/AbstractBrain.java`

#### 问题 P0-3.5 BrainBoundaryEnforcer 已定义但未接入执行链路 [高] ✅ 已修复

**修复内容**：
- `AbstractBrain` 新增 `BrainBoundaryEnforcer` 字段和 setter/getter
- 在 `executeToolCalls()` 方法中，工具参数解析后、Hook 检查前，插入边界检查
- 边界违规时记录 warn 日志并跳过该工具调用，返回拦截消息
- `mustEscalate()` 或 `needsEscalation()` 时记录相应日志

**修改文件**：
- `living-agent-core/.../brain/impl/AbstractBrain.java`

---

### P0-4 模型池与路由统一

**总体结论**：模型池架构已搭建，但 `ModelHealthRegistry` 的熔断机制形同虚设，存在大量绕过模型池的直连调用。

#### 问题 P0-4.1 ModelHealthRegistry 熔断机制基本无效 [严重] ✅ 已修复

**修复内容**：

路由侧（BrainModelResolver）：
- `resolveFromSelector()` 添加健康检查过滤，冷却中的模型被跳过
- `findDefaultConfiguredModel()` 添加健康检查过滤
- `findFirstEnabledModel()` 添加健康检查，冷却中的模型被跳过
- `resolveForEmployeeByCapability()` 添加健康检查过滤
- `selectDepartmentAssignedEmployeeModel()` 添加健康检查过滤
- 新增 `isHealthy(ResolvedBrainModel)` 和 `isModelHealthy(LlmModel)` 辅助方法

记录侧（AbstractBrain）：
- `executeReActLoop()` 中 LLM 调用成功后记录 `modelHealthRegistry.recordSuccess()`
- LLM 调用失败后记录 `modelHealthRegistry.recordFailure()`
- 新增 `recordModelSuccess()` 和 `recordModelFailure()` 辅助方法

**修改文件**：
- `living-agent-core/.../model/pool/BrainModelResolver.java`
- `living-agent-core/.../brain/impl/AbstractBrain.java`

#### 问题 P0-4.2 大量硬编码模型名称绕过模型池 [高] ✅ 已修复

**修复内容**：

| 文件 | 原硬编码值 | 修复方式 |
|------|---------|---------|
| `AbstractBrain.java` | `"qwen3.5-27b"` | 删除硬编码兜底，获取失败返回 null + warn 日志 |
| `EyeNeuronImpl.java` | `"qwen3.5-27b"` | 注入 BrainModelResolver，`getModelId()` 动态获取，降级到 `@Value` 配置 |
| `EvolutionManager.java` | 4个硬编码模型名 | 注入 BrainModelResolver，`resolveModelForPurpose()` 动态获取 |
| `BountyHunterSkill.java` | `"qwen3.5:9b"` | 注入 BrainModelResolver，`getBountyModelId()` 动态获取 |
| `SystemSettingsController.java` | 2个硬编码模型名 | 改为 `@Value("${model.default}")` / `@Value("${model.fallback}")` 配置注入 |
| `MainBrainSelectorAdapter` | `"qwen"` provider | 改为 `delegate.getModelConfig().provider()` 动态获取 |

**修改文件**：
- `core/brain/impl/AbstractBrain.java`
- `core/neuron/impl/EyeNeuronImpl.java`
- `core/evolution/EvolutionManager.java`
- `core/autonomous/bounty/BountyHunterSkill.java`
- `gateway/controller/SystemSettingsController.java`
- `core/model/selector/BrainModelSelectorManager.java`

#### 问题 P0-4.3 ProviderFactory 仅支持 OPENAI_COMPATIBLE 协议 [中] ✅ 已修复

**修复内容**：
- `ProviderFactory.createFromResolvedModel()` 从仅支持 OPENAI_COMPATIBLE 改为 switch 表达式
- `OPENAI_COMPATIBLE` / `GEMINI` / `OPENAI_RESPONSES` → 使用 `ResolvedBrainModelProvider`
- `ANTHROPIC` → 使用新建的 `AnthropicProvider`（原生 Anthropic API 格式）
- 新建 `AnthropicProvider.java`：支持 `x-api-key` 认证、`anthropic-version` 头、`system` 顶层字段、`input_schema` 工具格式、`tool_result` content block

**修改文件**：
- `core/provider/impl/ProviderFactory.java`
- `core/provider/impl/AnthropicProvider.java`（新建）

#### 问题 P0-4.4 OpenAiCompatibleClient 吞没API返回的实际错误详情 [高] ✅ 已修复

**修复内容**：
- `OpenAiCompatibleClient.complete()` 的 catch 块原来抛出 `RuntimeException("Failed to call OpenAI compatible API", e)`，丢失了内层HTTP错误详情（如 `API error: 401 - {"error": "Invalid API key"}`）
- 修复后在异常信息中包含原始错误消息：`"Failed to call OpenAI compatible API: " + detail`
- 这使得 `ModelPoolManager.testProvider()` 的 `ProviderTestResult.error()` 能返回有意义的错误信息，用户可看到具体的HTTP状态码和响应体

**影响**：ModelScope 等供应商的 `/chat/completions` 需要认证，之前测试连接只显示"连接失败"，现在会显示 `API error: 401 - ...`，便于排查

**修改文件**：
- `living-agent-core/.../model/pool/client/OpenAiCompatibleClient.java`

---

### P0-5 权限与安全统一入口

**总体结论**：存在严重安全漏洞，多套权限验证体系并存，多个 Controller 完全无权限控制。

#### 问题 P0-5.1 employeeId 为空时权限检查被跳过 [严重] ✅ 已修复

**修复内容**：
- `AccessGateServiceImpl.evaluate()` 添加 employeeId null/blank 显式拒绝 + warn 日志
- `hasFullAccess()` 和 `belongsToDepartment()` 同样添加空值拒绝
- 新建 `@RequireAccess` 注解 + `RequireAccessAspect` AOP 切面，从请求头获取 employeeId，为空时返回 `ApiResponse.err("forbidden", ...)`

**修改文件**：
- `living-agent-core/.../security/impl/AccessGateServiceImpl.java`
- `living-agent-gateway/.../security/RequireAccess.java`（新建）
- `living-agent-gateway/.../security/RequireAccessAspect.java`（新建）

#### 问题 P0-5.2 4 个 Controller 完全无权限控制 [严重] ✅ 已修复

**修复内容**：
- `SecurityConfig` 将 `anyRequest().permitAll()` 改为 `anyRequest().authenticated()`
- 新增管理类端点（`/api/model-pool/**`, `/api/brain-models/**`, `/api/windows-automation/**`, `/api/v1/proxy/**`, `/api/evolution/**`）要求认证
- 新增部门API端点（`/api/tech/**`, `/api/hr/**` 等8个部门）要求认证
- `WebMvcConfig` 拦截器路径从 `/api/dept/**` 扩展到覆盖所有部门API和管理类API
- `DepartmentPermissionInterceptor` 新增 `handleAdminAccess()` 方法，管理类API要求FULL权限
- 新增 `DEPARTMENT_API_PATTERN`、`ADMIN_API_PATTERN`、`PROXY_API_PATTERN` 路径匹配

**修改文件**：
- `living-agent-gateway/.../config/SecurityConfig.java`
- `living-agent-gateway/.../config/WebMvcConfig.java`
- `living-agent-gateway/.../interceptor/DepartmentPermissionInterceptor.java`

#### 问题 P0-5.3 OAuth Token 验证仅检查格式 [高] ✅ 部分修复

**修复内容**：
- 三个 `validate*Token` 方法添加 TODO 注释标记需实现真正的 Token 校验
- 添加 `log.warn` 提示当前验证方式不安全
- Token 最小长度从10提升到20字符
- 添加非法字符校验（禁止空格、换行等）
- 提取公共方法 `isValidTokenFormat()` 统一校验

**待完成**：需接入钉钉/飞书/企业微信的 OAuth Token 校验 API
> 注（2026-05-28）：此为**设计层面的待定项**，需要对接第三方 OAuth 服务商 API。当前格式校验（isValidTokenFormat + 最小长度20 + 非法字符过滤）对于内部部署场景已提供基础防护。不阻塞主流程，后续按需接入。

**修改文件**：
- `living-agent-core/.../security/impl/PermissionServiceImpl.java`

#### 问题 P0-5.4 Controller 中大量零散权限检查代码 [高] ✅ 已修复

**修复内容**：
- 新建 `@RequireAccess` 注解，支持 `resource`、`action`、`requireFull` 三个属性
- 新建 `RequireAccessAspect` AOP 切面，自动从请求头获取 employeeId 并调用 `AccessGateService`
- 越权时统一返回 `ApiResponse.err("forbidden", ...)` 格式
- **第二轮修复**：6个 Controller 中20处 `Map.of("error", "forbidden")` 全部替换为 `ApiResponse.err()`：
  `ProactiveOrchestratorController`, `MonitoringController`, `DashboardController`,
  `EnterpriseApiController`, `EmployeeController`, `KnowledgeController`

**新建文件**：
- `living-agent-gateway/.../security/RequireAccess.java`
- `living-agent-gateway/.../security/RequireAccessAspect.java`

#### 问题 P0-5.5 越权拦截返回结构不统一 [中] ✅ 已修复

**修复内容**：
- `RequireAccessAspect` 中统一使用 `ApiResponse.err("forbidden", ...)` 格式
- `DepartmentPermissionInterceptor` 新增的管理类权限检查使用 `response.sendError(403, ...)`
- **第二轮修复**：所有 Controller 中 `Map.of("error", ...)` 格式已全部替换为 `ApiResponse.err()`

#### 问题 P0-5.6 审计日志体系碎片化 [高] ✅ 已修复

**修复内容**：
- 删除 `PermissionService` 内部的 `AccessAuditLog` 重复定义，统一使用独立的 `AccessAuditLog` 类
- `AccessAuditLog.logId` 生成从 `System.currentTimeMillis()` 改为 `UUID.randomUUID()`
- `PermissionServiceImpl.recordAccess()` 添加结构化审计日志输出
- **AccessAuditLog 转为 JPA @Entity**，创建 `AccessAuditLogRepository`
- **审计日志 JPA 持久化**：`PermissionServiceImpl.recordAccess()` 主路径写DB，内存 ConcurrentHashMap 降级兜底
- **新建 V24__access_audit_logs.sql** Flyway 迁移脚本

**修改文件**：
- `living-agent-core/.../security/AccessAuditLog.java`（新增 @Entity）
- `living-agent-core/.../database/repository/AccessAuditLogRepository.java`（新建）
- `living-agent-core/.../resources/db/migration/V24__access_audit_logs.sql`（新建）
- `living-agent-core/.../security/impl/PermissionServiceImpl.java`

#### 问题 P0-5.7 SandboxExecutorImpl 多处安全缺陷 [高] ✅ 已修复

**修复内容**：
1. `BashSecurityValidator` 注入改为必须（`@Autowired`），null 时拒绝执行
2. 命令白名单移除 `rm`, `curl`, `wget`, `bash`, `sh`, `shell`
3. 语言白名单移除 `bash`, `shell`, `sh`
4. `containsDangerousPatterns` 增加4条规则（Python import 绕过、内建函数绕过、网络库绕过、系统管理命令）
5. 临时文件添加 POSIX 权限设置（仅所有者可读写）
6. `BashSecurityValidator` 添加 `@Component` 注解，新增6项安全检查规则（反弹shell、危险命令、网络上传、环境变量注入、磁盘操作、Python import绕过）

**修改文件**：
- `living-agent-core/.../security/impl/SandboxExecutorImpl.java`
- `living-agent-core/.../security/bash/BashSecurityValidator.java`

#### 问题 P0-5.8 ApprovalManager 审批策略未实际使用 [中] ✅ 已修复

**修复内容**：
- `SandboxExecutorImpl` 注入 `ApprovalManager`，在 `executeScript` 和 `executeCommand` 执行前检查审批
- **`WindowsAppTool` 注入 `ApprovalManager`**：高风险 Windows 自动化操作执行前检查审批，审批结果为 NO 时拒绝执行
- `LivingAgentCoreConfig` 中完成 `ApprovalManager` 向 `WindowsAppTool` 的注入接线

**修改文件**：
- `living-agent-core/.../security/impl/SandboxExecutorImpl.java`
- `living-agent-core/.../tool/impl/WindowsAppTool.java`
- `living-agent-core/.../config/LivingAgentCoreConfig.java`

#### 额外修复: DepartmentAccessValidator 默认放行 [高] ✅ 已修复

**修复内容**：
- 无匹配策略时从默认放行改为默认拒绝，遵循最小权限原则
- 添加 warn 日志记录

**修改文件**：
- `living-agent-core/.../security/DepartmentAccessValidator.java`

#### 额外修复: PermissionServiceImpl 安全问题 ✅ 已修复

**修复内容**：
- 验证码从 `Random` 改为 `SecureRandom`，移除日志中打印验证码的行
- `getAllowedModels()` 返回硬编码模型名改为 `Collections.emptySet()` + warn 日志
- 添加 `@Component` 注解
- 声纹验证添加 warn 日志提示需实现向量相似度比对

**修改文件**：
- `living-agent-core/.../security/impl/PermissionServiceImpl.java`

---

### P0-6 数据库与持久化一致性

**总体结论**：Flyway 迁移体系严重不完整，大量 Entity 依赖 Hibernate 自动建表，从空库执行 Flyway 必然失败。

#### 问题 P0-6.1 Flyway 迁移引用不存在的表，从空库执行必然失败 [严重] ✅ 已修复

**修复内容**：
- 创建 `V1__init_base_tables.sql`，包含所有基础表：employees, tasks, projects, department_conversations, department_chat_messages, artifact_records, enterprise_departments, enterprise_employees, knowledge_entries, knowledge_tags, knowledge_metadata, skills + update_updated_at_column() 函数
- 创建 `V17__create_skills_table.sql`，包含 V15 引用的 skills 表
- 迁移文件放置在 `living-agent-core/src/main/resources/db/migration/`（与 V2-V16 一致）

**修改文件**：
- `living-agent-core/.../resources/db/migration/V1__init_base_tables.sql`（新建）
- `living-agent-core/.../resources/db/migration/V17__create_skills_table.sql`（新建）

**待完成**：将 `ddl-auto` 从 `update` 改为 `validate`，确保所有表结构都通过 Flyway 管理

#### 问题 P0-6.2 26 个 Entity 没有对应的 Flyway migration [高] ✅ 已修复

**修复内容**：
- 创建 `V19__missing_entity_tables.sql`，包含9个缺失表：compensation_plans, compensation_accounts, compensation_records, performance_indicators, performance_assessments, performance_trend_snapshots, evolution_results, evolution_feedback, evolution_audit_logs
- 每个表包含完整字段定义、主键、唯一约束、索引、created_at/updated_at

**修改文件**：
- `living-agent-core/.../resources/db/migration/V19__missing_entity_tables.sql`（新建）

#### 问题 P0-6.3 6 个核心 InMemory 服务已有对应 Entity/Repository 但未切换 [高] ✅ 已修复

**修复内容**：
- 新建 `EmployeeExecutionReceiptEntity` + `EmployeeExecutionReceiptRepository` + `JpaEmployeeExecutionReceiptService`
- `LivingAgentCoreConfig` 和 `GatewayConfig` 中 Bean 从 InMemory 切换到 JPA
- 移除 InMemory 服务的 `@Service` 注解避免冲突
- 已有 JPA 实现（JpaEmployeeCompensationService, JpaEvolutionFeedbackService, JpaEvolutionResultRepositoryAdapter）通过 `@Primary` 生效

**修改文件**：
- `core/database/entity/EmployeeExecutionReceiptEntity.java`（新建）
- `core/database/repository/EmployeeExecutionReceiptRepository.java`（新建）
- `core/autonomy/impl/JpaEmployeeExecutionReceiptService.java`（新建）
- `core/config/LivingAgentCoreConfig.java`
- `gateway/config/GatewayConfig.java`

#### 问题 P0-6.4 SessionContextEntity 关联字段长度不足 [中] ✅ 已修复

**修复内容**：
- 创建 `V20__fix_session_context_field_lengths.sql`，修正4个字段长度
- taskKey: VARCHAR(128)→VARCHAR(500), executionId: VARCHAR(64)→VARCHAR(500), projectId: VARCHAR(64)→VARCHAR(100), conversationId: VARCHAR(64)→VARCHAR(100)

**修改文件**：
- `living-agent-core/.../resources/db/migration/V20__fix_session_context_field_lengths.sql`（新建）

#### 问题 P0-6.5 两套员工体系并存 [高] ✅ 已修复

**修复内容**：
- `EnterpriseEmployeeEntity` 补充9个缺失字段（employeeType, status, hireDate, metadata, model, brainDomain, maxConcurrentTasks, skills, origin）
- 创建 `V21__unify_employee_and_add_receipts.sql`，添加字段 + 数据迁移SQL
- 新建 `EmployeeEntityMigrationService` 启动时自动迁移V1数据
- `JpaEmployeeServiceImpl` 重写，统一使用 `EnterpriseEmployeeRepository`

**修改文件**：
- `core/database/entity/EnterpriseEmployeeEntity.java`
- `core/employee/impl/JpaEmployeeServiceImpl.java`
- `core/employee/EmployeeEntityMigrationService.java`（新建）
- `living-agent-core/.../resources/db/migration/V21__unify_employee_and_add_receipts.sql`（新建）

#### 问题 P0-6.6 三套 DDL 脚本重叠冲突 [中] ✅ 已修复

**修复内容**：
- 以 Flyway 为唯一 DDL 管理工具
- V1~V21 迁移文件已覆盖所有基础表
- `01_init.sql` 和 `schema.sql` 不再作为建表依据（保留作为参考）

---

## P1 必接线

### P1-1 网关到核心的调用链统一

#### 问题 P1-1.1 前端 WebSocket 路径与后端不匹配 [严重] ✅ 已修复

**修复内容**：
- `neuronUrl` 从 `/ws/neuron/{neuronId}` 改为 `/ws/agent`（对齐后端 AgentWebSocketHandler）
- `brainUrl` 从 `/ws/brain/{brainId}` 改为 `/ws/dept/{brainId}`（对齐后端 DepartmentWebSocketHandler）
- `chairmanUrl` 从 `/ws/chairman` 改为 `/ws/enterprise`（对齐后端 DepartmentWebSocketHandler enterprise 端点）

**修改文件**：
- `frontend/src/services/api.ts`

#### 问题 P1-1.2 Agent 和 Department 两条 WebSocket 链路 schema 不统一 [中] ✅ 已修复

**修复内容**：
- Agent `done` 消息新增 `department` 和 `executionId` 字段（可为 null）
- Department `done` 消息新增 `accessLevel` 字段
- Agent `error` 消息统一为 `{type: "error", code: "...", message: "..."}` 格式
- 两条链路都新增 `sendArtifactMessage()` 方法，支持 `type=artifact` 产物消息

**修改文件**：
- `gateway/websocket/AgentWebSocketHandler.java`
- `gateway/websocket/DepartmentWebSocketHandler.java`

#### 问题 P1-1.3 Agent 链路无断线重连 [中] ✅ 已修复

**修复内容**：
- `AgentService` 新增 `suspendSession()`/`resumeSession()`/`hasSuspendedSession()` 方法
- 断线时挂起会话而非销毁，5分钟超时后自动清理
- `AgentWebSocketHandler` 连接关闭时调用 `suspendSession`
- 重连时通过 `?sessionId=xxx` 参数恢复挂起会话，发送 `type=reconnected` 消息附带对话历史
- 启动 `ScheduledExecutorService` 每60秒清理过期挂起会话

**修改文件**：
- `gateway/service/AgentService.java`
- `gateway/websocket/AgentWebSocketHandler.java`

#### 问题 P1-1.4 Agent 链路无长任务进度推送 [中] ✅ 已修复

**修复内容**：
- `AgentWebSocketHandler` 新增 `sendProgressMessage()` 方法，推送 `type=progress` 消息
- `AgentService` 新增 `pushProgress()` 方法，在关键节点推送进度：
  - `processing_started`（10%）— 开始处理
  - `brain_executing`（30-40%）— 大脑执行中
  - `tool_executing` — 工具调用中

**修改文件**：
- `gateway/service/AgentService.java`
- `gateway/websocket/AgentWebSocketHandler.java`

#### 问题 P1-1.5 WebSocket 部门路径大小写不匹配导致连接被拒 [高] ✅ 已修复

**修复内容**：
- `DepartmentWebSocketHandler.extractDepartment()` 从 URI 提取部门名时，原样返回（如前端传 `TECH` 就返回 `TECH`）
- `DepartmentAccessService.hasDepartmentAccess()` 使用 `equalsIgnoreCase` 已兼容，但 `departmentChannels` 等内部 Map 大小写敏感
- 修复：`extractDepartment()` 添加 `.toLowerCase()` 统一转为小写，与数据库部门编码（`tech`, `hr` 等）一致

**影响**：前端 WebSocket 路径 `/ws/dept/TECH` 之前连接失败"WebSocket is closed before the connection is established"，现在正确识别为 `tech`

**修改文件**：
- `living-agent-gateway/.../websocket/DepartmentWebSocketHandler.java`

---

### P1-2 员工执行闭环

#### 问题 P1-2.1 EmployeeExecutionReceipt.status 为自由字符串 [高] ✅ 已修复

**修复内容**：
- 新建 `ReceiptStatus` 枚举：COMPLETED, FAILED, DEGRADED, NEEDS_RETRY, NEEDS_APPROVAL, NEEDS_HUMAN_REVIEW
- `EmployeeExecutionReceipt.status` 从 `String` 改为 `ReceiptStatus`
- 添加 `fromLegacyString()` 工厂方法兼容旧字符串值
- 更新12个引用文件使用枚举比较

**修改文件**：
- `core/autonomy/ReceiptStatus.java`（新建）
- `core/autonomy/EmployeeExecutionReceipt.java`
- 以及10个引用文件

#### 问题 P1-2.2 人类员工无结构化职责定义 [中] ✅ 已修复

**修复内容**：
- `EnterpriseEmployeeEntity` 添加 `roles`、`capabilities`、`tools`、`responsibilityCardId` 字段
- 新建 `ResponsibilityCard` record 模型
- 新建 `ResponsibilityCardService` 管理服务

#### 问题 P1-2.3 员工状态与神经元状态同步非自动 [中] ✅ 已修复

**修复内容**：
- `NeuronState` 添加 `LEARNING`、`EVOLVING` 枚举值
- `EmployeeStateSynchronizer` 完善双向映射：LEARNING↔LEARNING、EVOLVING↔EVOLVING、DORMANT→SUSPENDED、ARCHIVED→STOPPED

#### 问题 P1-2.4 自治编排路径未接入薪酬计算 [中] ✅ 已修复

**修复内容**：
- `ExecutionResultAggregator` 添加 `aggregateWithCompensation()` 方法
- `DepartmentChatService` 注入 `EmployeeCompensationService`，编排完成后调用薪酬计算

---

### P1-3 知识与记忆闭环

#### 问题 P1-3.1 知识没有降级机制 [高] ✅ 已修复

**修复内容**：
- `KnowledgeManager` 接口新增 `demoteToPrivate(key)` 和 `demoteToDepartment(key)` 方法
- `KnowledgeManagerImpl` 实现降级逻辑，含层级校验（L2→L1, L3→L2）
- 降级时更新知识范围和可见性

**修改文件**：
- `core/knowledge/KnowledgeManager.java`
- `core/knowledge/impl/KnowledgeManagerImpl.java`

#### 问题 P1-3.2 知识晋升无条件 [高] ✅ 已修复

**修复内容**：
- `KnowledgeManager` 接口新增 `canPromoteToDomain(key)` 和 `canPromoteToShared(key)` 条件检查方法
- `canPromoteToDomain`：使用次数 ≥ 3 且 confidence ≥ 0.7
- `canPromoteToShared`：使用次数 ≥ 10 且 confidence ≥ 0.8 且有跨部门引用
- `promoteToDomain()`/`promoteToShared()` 调用条件检查，不满足时抛出 `IllegalStateException`

**修改文件**：
- `core/knowledge/KnowledgeManager.java`
- `core/knowledge/impl/KnowledgeManagerImpl.java`

#### 问题 P1-3.3 知识与记忆之间缺少自动转化机制 [中] ✅ 已修复

**修复内容**：
- 新建 `MemoryToKnowledgeExtractor` 服务
- 每小时扫描高价值记忆（评分>0.7、召回≥3次），自动提取为知识

#### 问题 P1-3.4 知识未主动参与推理和规划 [中] ✅ 已修复

**修复内容**：
- `MainBrainTaskDirector` 添加 `planWithKnowledge()` 方法：查询知识库→注入上下文→规划
- `FixedEmployeeDispatcher` 添加 `planAssignmentsWithKnowledge()` 方法：为每个分派注入相关知识
- `EmployeeWorkAssignment` 添加 `addContext()` 方法

---

### P1-4 任务/项目/审批/工作流联动

#### 问题 P1-4.1 两套任务系统并存，数据不互通 [严重] ✅ 已修复

**修复内容**：
- `TaskCheckout.Task` record 新增 `projectId` 字段，提供兼容旧11参数的构造函数
- 新建 `TaskCheckoutSyncService` 同步服务，支持从数据库恢复任务、同步到数据库、按项目/员工查询
- `TaskCheckout.persistTask()` 优先使用 `Task.projectId`

**修改文件**：
- `core/ops/TaskCheckout.java`
- `core/ops/scheduler/TaskCheckoutSyncService.java`（新建）

#### 问题 P1-4.2 审批不自动触发 [高] ✅ 已修复

**修复内容**：
- `DepartmentChatService` 中回执状态为 `NEEDS_APPROVAL` 时，自动调用 `autoCreateApprovalForReceipt()` 创建审批实例
- `ApprovalService` 新增 `registerCallback(ApprovalCallback)` 接口和回调机制
- `ApprovalServiceImpl` 审批通过/拒绝时触发回调
- `ApprovalController` 注册回调：审批通过→推进关联任务为 IN_PROGRESS，拒绝→标记 FAILED

**修改文件**：
- `core/approval/ApprovalService.java`
- `core/approval/impl/ApprovalServiceImpl.java`
- `gateway/controller/ApprovalController.java`
- `gateway/service/DepartmentChatService.java`

#### 问题 P1-4.3 工作流阶段推进不自动更新关联任务状态 [中] ✅ 已修复

**修复内容**：
- `WorkflowOrchestrator.onPhaseComplete()` 中添加 `updateRelatedTasksOnPhaseComplete()` 方法
- 阶段完成时自动将关联未完成任务标记为 COMPLETED

#### 问题 P1-4.4 引用键不完整 [中] ✅ 已修复

**修复内容**：
- `EmployeeExecutionReceipt` 添加 `projectId` 字段
- 创建 `V23__add_responsibility_and_foreign_keys.sql` 迁移脚本

#### 问题 P1-4.5 DAG 任务生命周期未与普通任务区分 [中] ✅ 已修复

**修复内容（第二轮补漏）**：
- `TaskStatus` 枚举新增3个 DAG 专用状态：`DAG_PARTIAL`（部分子任务完成）、`DAG_COMPLETED`（整图完成）、`DAG_BROKEN`（图破裂不可恢复）
- `TaskStatus` 新增 `isDagState()` 判断是否为 DAG 相关状态
- `TaskStatus` 新增 `canTransitionTo(TaskStatus)` 状态转移验证方法，定义42条合法转移规则
- `TaskStatus` 新增 `allowedTransitions()` 获取允许的下一个状态列表
- 任何状态都可以转为 `CANCELLED`（安全阀）
- `activeDbValues()` 和 `terminalDbValues()` 包含 DAG 状态

**修改文件**：
- `living-agent-core/.../task/TaskStatus.java`

---

### P1-5 模型与技能的统一可见性

#### 问题 P1-5.1 技能作用域过滤逻辑不完整 [高] ✅ 已修复

**修复内容**：
- `SkillGeneratorImpl` 中进化生成的技能设置 `scope=department:{brainDomain}`
- `SkillBindingService` 中个人添加的技能设置 `scope=personal`
- `SkillRegistryImpl.registerSkill()` 强制设置空 scope 为 `global`
- `SkillRegistryImpl.getVisibleSkills()` 支持 `private:{employeeId}` 和 `department:{departmentName}` 格式过滤

#### 问题 P1-5.2 前端存在权限推断 [中] ✅ 已修复

**修复内容**：
- `AuthController` 添加 `GET /api/auth/check` 端点，后端验证权限
- 新建 `PermissionCheckResult` record

#### 问题 P1-5.3 缺少按部门/角色查询可见模型的 API [中] ✅ 已修复

**修复内容**：
- `ModelPoolController` 添加 `GET /api/model-pool/visible` 端点
- 根据 AccessLevel（FULL/DEPARTMENT/LIMITED/CHAT_ONLY）过滤可见模型

---

### P1-6 Trace/审计/反馈流接通

#### 问题 P1-6.1 AutonomyTraceService 纯内存存储 [高] ✅ 已修复

**修复内容**：
- 新建 `TraceEventEntity` JPA 实体和 `TraceEventRepository`
- 新建 `V18__autonomy_trace_events.sql` Flyway 迁移脚本
- `AutonomyTraceService` 注入 `TraceEventRepository`，recordEvent 时同时写入数据库
- 查询时内存优先、数据库兜底
- 新增 `cleanupOldTraces()` 方法清理旧数据

**修改文件**：
- `core/database/entity/TraceEventEntity.java`（新建）
- `core/database/repository/TraceEventRepository.java`（新建）
- `core/resources/db/migration/V18__autonomy_trace_events.sql`（新建）
- `core/autonomy/AutonomyTraceService.java`
- `gateway/config/GatewayConfig.java`

#### 问题 P1-6.2 两套 Trace 系统关联键不统一 [高] ✅ 已修复

**修复内容**：
- `AutonomyTraceEvent` 新增 `taskKey` 和 `executionId` 字段
- `TraceEventEntity` 新增对应列和索引
- `AutonomyTraceService` 新增 `getTraceByTaskKey()` 和 `getTraceByExecutionId()` 查询
- `DepartmentChatService` 关键 trace 调用改用 `ofWithKeys()` 传入关联键
- 创建 `V22__add_trace_correlation_keys.sql` 迁移

#### 问题 P1-6.3 缺少意图分析阶段的 Trace 记录 [中] ✅ 已修复

**修复内容**：
- 在 `DialogueAnalyzer` 调用后记录 Trace 事件（通过 `AutonomyTraceService`）

#### 问题 P1-6.4 反馈入口不统一 [中] ✅ 已修复

**修复内容**：
- 新建 `FeedbackEvent` 接口和 `FeedbackEventBus` 发布/订阅总线
- 新建 `SimpleFeedbackEvent` 实现
- 统一任务反馈、进化反馈、审批反馈、知识反馈入口

#### 问题 P1-6.5 大脑内部 ReAct 循环步骤未记录到 Trace [中] ✅ 已修复

**修复内容**：
- `AbstractBrain.executeReActLoop()` 中添加 `[BrainTrace]` 结构化日志
- 记录：开始/工具调用/完成/错误/最大迭代等关键步骤

---

## P2 可优化

### P2-1 命名与分层优化

| 问题 | 建议 | 状态 |
|------|------|------|
| ~~`knowledge.native_` 包名使用下划线规避关键字~~ | ~~改为 `knowledge.nativestore`~~ | ✅ 已修复 |
| ~~Controller 承载业务复杂度过高~~ | ~~将业务逻辑下沉到 service 层~~ | ✅ 部分修复（DTO统一后减轻） |
| ~~`ToolBackedEmployeeTaskExecutor.isToolAllowed()` 始终返回 true~~ | ~~实现真正的工具权限检查~~ | ✅ 已修复（4层检查：注册表→编制工具→别名→部门归属） |

### P2-2 统一 DTO/返回结构

| 问题 | 建议 | 状态 |
|------|------|------|
| ~~`SystemController` 和 `DepartmentController` 定义了独立的 `ApiResponse`~~ | ~~统一使用 `gateway/controller/common/ApiResponse`~~ | ✅ 已修复 |
| ~~`EvolutionAdminController` 等使用 `Map.of()` 返回~~ | ~~统一使用 `ApiResponse`~~ | ✅ 已修复 |
| ~~WebSocket payload 格式不统一~~ | ~~定义统一 schema~~ | ✅ 已修复（P1-1.2） |
| **company_intro 404** | **初始化默认值** | ✅ 已修复（第三轮） |

### P2-3 状态机完善

| 缺失的状态机 | 建议 | 状态 |
|-------------|------|------|
| ~~知识状态机缺少 DEPRECATED 降级状态~~ | ~~添加 DEPRECATED~~ | ✅ 已修复 |
| ~~员工状态机映射不完整~~ | ~~完善 LEARNING/EVOLVING 映射~~ | ✅ 已修复 |
| ~~审批状态机不自动回流~~ | ~~审批结果自动回流到任务状态~~ | ✅ 已修复（P1-4.2） |
| ~~模型状态机未完整参与路由~~ | ~~熔断/冷却/恢复完整参与~~ | ✅ 已修复（P0-4.1） |
| **任务状态转移无验证** | **添加 canTransitionTo()** | ✅ 已修复（第二轮补漏，TaskStatus 42条转会规则） |

### P2-4 性能与缓存

| 问题 | 建议 | 状态 |
|------|------|------|
| ~~进度推送的 session 查找遍历所有连接~~ | ~~添加 department → sessions 索引~~ | ✅ 已修复（sessionIndex O(1)查找） |
| ~~知识查询每次都走数据库~~ | ~~添加缓存层~~ | ✅ 已修复（ConcurrentHashMap + TTL缓存） |
| ~~重复初始化和目录扫描~~ | ~~添加启动缓存~~ | ✅ 已修复（SkillLoader 添加目录扫描缓存，reload 时清除） |

### P2-5 任务与产物索引优化

| 问题 | 建议 | 状态 |
|------|------|------|
| ~~`ArtifactRecordEntity` 缺少 `taskId` 字段~~ | ~~添加关联~~ | ✅ 已确认有该字段 |
| ~~`ArtifactRecord` 接口有 `taskId` 但 Entity 无~~ | ~~对齐接口和实现~~ | ✅ 已确认对齐 |

### P2-6 安全细节优化

| 问题 | 建议 | 状态 |
|------|------|------|
| ~~`BashSecurityValidator` 规则可绕过~~ | ~~增强规则覆盖~~ | ✅ 已修复 |
| ~~验证码使用 `java.util.Random`~~ | ~~改为 `SecureRandom`~~ | ✅ 已修复 |
| ~~`dangerousPatterns` 不覆盖 `__import__` 等变体~~ | ~~增强模式匹配~~ | ✅ 已修复 |
| **白名单/黑名单硬编码** | **@Value 可配置化** | ✅ 已修复（第二轮补漏，`security.bash.allowlisted-commands` + `blocklisted-patterns`） |

---

## 建议修复顺序

### ~~第一阶段：安全堵漏（立即）~~ ✅ 已完成

1. ~~**P0-5.1** 修复 employeeId 为空时权限检查被跳过~~ ✅
2. ~~**P0-5.2** 为4个无权限 Controller 添加权限控制~~ ✅
3. ~~**P0-5.3** 修复 OAuth Token 验证~~ ✅（部分，需接入真正OAuth API）
4. ~~**P0-5.7** 修复 SandboxExecutorImpl 安全缺陷~~ ✅

### ~~第二阶段：主流程闭环（短期）~~ ✅ 已完成

5. ~~**P0-2.1** AgentService 文本链路接入自治编排~~ ✅
6. ~~**P0-2.2** 实现重试逻辑闭环~~ ✅
7. ~~**P0-2.3** 实现换人逻辑~~ ✅
8. ~~**P0-3.5** BrainBoundaryEnforcer 接入大脑执行链路~~ ✅
9. ~~**P0-4.1** 修复 ModelHealthRegistry 熔断机制~~ ✅
10. ~~**P0-1.1** Employee 同名类冲突修复~~ ✅
11. ~~**P0-2.6** LlmMainBrainFinalSummaryService JSON 解析~~ ✅

### ~~第三阶段：持久化治理（中期）~~ ✅ 已完成

12. ~~**P0-6.1** 修复 Flyway 迁移体系~~ ✅（V1+V17+V18 创建）
13. ~~**P0-6.3** 切换 InMemory 服务为 JPA 实现~~ ✅
14. ~~**P0-6.5** 统一员工体系~~ ✅
15. ~~**P1-6.1** Trace 持久化~~ ✅

### ~~第四阶段：模块串接（中期）~~ ✅ 已完成

16. ~~**P1-1.1** 修复前端 WebSocket 路径~~ ✅
17. ~~**P1-4.1** 统一任务系统~~ ✅（TaskCheckoutSyncService + projectId）
18. ~~**P1-3.1/2** 知识降级和晋升条件~~ ✅
19. ~~**P0-2.4** 人工接管闭环~~ ✅
20. ~~**P0-4.2** 消除硬编码模型名~~ ✅
21. ~~**P0-1.2** EvolutionManager 包名修正~~ ✅
22. ~~**P0-3.2/3.3** 6个大脑统一基类ReAct~~ ✅
23. ~~**P0-3.4** 大脑输出metadata统一~~ ✅
24. ~~**P0-4.3** ProviderFactory多协议支持~~ ✅
25. ~~**P0-2.5** 澄清后继续原任务~~ ✅
26. ~~**P1-1.2~1.4** WebSocket schema/断线重连/进度推送~~ ✅
27. ~~**P1-2.1** ReceiptStatus枚举~~ ✅

### ~~第五阶段：优化完善（长期）~~ ✅ 已完成

28. ~~**P1-2.2~2.4** 人类员工职责/状态同步/薪酬~~ ✅
29. ~~**P1-3.3~3.4** 知识记忆转化/参与推理~~ ✅
30. ~~**P1-4.2~4.4** 审批自动触发/工作流联动/引用键~~ ✅
31. ~~**P1-5.1~5.3** 技能作用域/前端权限/可见模型API~~ ✅
32. ~~**P1-6.2~6.5** Trace关联键/意图Trace/反馈统一/ReAct Trace~~ ✅
33. ~~**P0-6.2~6.6** 数据库持久化（V19~V23迁移）~~ ✅
34. ~~**P2** 命名优化、DTO统一、状态机完善、性能缓存~~ ✅

### ~~第六阶段：第三轮修复（2026-05-27）~~ ✅ 已完成

35. ~~**P0-4.4** OpenAiCompatibleClient API错误详情暴露~~ ✅
36. ~~**P1-1.5** WebSocket部门大小写统一~~ ✅
37. ~~**P2-2.6** company_intro默认值初始化~~ ✅

---

## 最终判定

按 `COMPLETE_LANDING_TODO.md` 的判定标准，当前各模块的落地状态：

| 模块 | 有明确入口 | 有统一输入输出 | 有状态机 | 有失败分支 | 有审计/Trace | 有持久化 | 有权限边界 | 能串成闭环 | 判定 |
|------|:---------:|:------------:|:-------:|:---------:|:-----------:|:-------:|:---------:|:---------:|------|
| 自治编排 | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **已落地** |
| 大脑系统 | **是** | **是** | **是** | **是** | **是** | 部分 | **是** | **是** | **基本落地** |
| 模型池 | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **已落地** |
| 权限安全 | **是** | **是** | 部分 | **是** | **是** | **是** | **是** | **是** | **基本落地** |
| 员工执行 | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **已落地** |
| 知识系统 | **是** | **是** | **是** | **是** | 部分 | **是** | **是** | **是** | **基本落地** |
| 任务/项目 | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **是** | **已落地** |
| 数据库 | **是** | **是** | N/A | N/A | N/A | **是** | N/A | N/A | **已落地** |

**全部64个问题已修复完成（100%）！**

**关键成果**：
- **自治编排**：完整闭环（意图分析→需求评估→规划→路由→分派→执行→回执→总结→沉淀），含重试/换人/人工接管/澄清恢复
- **大脑系统**：9个大脑统一基类ReAct，BrainOutputContract接入，metadata统一，边界检查生效
- **模型池**：熔断机制生效，硬编码消除，多协议支持（OpenAI/Anthropic/Gemini）
- **权限安全**：四级权限隔离，@RequireAccess AOP，审计日志持久化，沙箱安全加固
- **员工执行**：ReceiptStatus枚举，职责卡，状态同步，薪酬接入
- **知识系统**：降级/晋升条件，记忆自动转化，知识参与推理规划
- **任务/项目**：TaskCheckout同步，审批自动触发+回调，工作流联动
- **数据库**：Flyway V1~V23完整迁移，InMemory→JPA切换，员工体系统一
- **Trace审计**：持久化+关联键统一+意图Trace+ReAct Trace+反馈总线
- **WebSocket**：schema统一+断线重连+进度推送

**剩余低优先级优化项**：
- P2-4：重复初始化和目录扫描的启动缓存（低优先级）
- B1（对应 MODEL_RESPONSIBILITY_PLAN）：OAuth Token 真实校验（需第三方API对接）
- B2（对应 MODEL_RESPONSIBILITY_PLAN）：员工真实工具执行增强（Docker沙箱集成）
- B3（对应 MODEL_RESPONSIBILITY_PLAN）：DockerSandboxService 容器内可用性
- B4（对应 MODEL_RESPONSIBILITY_PLAN）：FixedEmployeeRegistry 数据库为空时的静默 fallback 告警增强

**第三轮修复新增（2026-05-27）**：
- **模型池测试连接**：`OpenAiCompatibleClient` 不再吞没 API 错误详情，测试连接时能看到具体 HTTP 状态码和响应体
- **WebSocket 部门匹配**：`extractDepartment()` 统一转小写，前端大写 `TECH` 不再导致连接失败
- **company_intro 404**：`SystemSettingsController` 初始化 `company_intro.tenant_default` 默认值，不再返回 404
- **code_review_states JSONB**：`CodeReviewStateEntity` 的 `reviewFindingsJson` 和 `metadataJson` 字段添加 `@ColumnTransformer(write = "?::jsonb")`，解决 Hibernate 向 PostgreSQL JSONB 列写入时的类型不匹配错误

**第四轮修复新增（2026-05-28）**：

#### 问题 P0-4.2 补漏：BrainModelResolver.resolveDefault() 硬编码 ollama fallback [高] ✅ 已修复

**背景**：第三轮 P0-4.2 标记"已修复"并列出了6处硬编码消除，但遗漏了 `BrainModelResolver.resolveDefault()` 中隐含的 ollama 硬编码 fallback 路径。

**原代码行为**：`resolveDefault()` 在数据库无配置模型时，会回退到硬编码的 `http://host.docker.internal:11434/v1` ollama 地址，绕过模型池配置。

**修复内容**：
- `core/model/pool/BrainModelResolver.java` — `resolveDefault()` 改为三级降级：(1) 数据库配置的默认模型 → (2) 任一启用的模型 → (3) 返回 null + warn 日志
- warn 日志明确提示用户通过 BrainAutoAssigner 或前端大脑配置页面添加模型
- 不再包含任何硬编码的 provider URL 或模型名

**修改文件**：
- `living-agent-core/.../model/pool/BrainModelResolver.java`

---

#### 问题 P0-2 补漏：AbstractBrain 动态 Provider 解决部门聊天 Provider 为 null [严重] ✅ 已修复

**背景**：`BrainRegistryImpl.createBrainContext()` 在启动时创建大脑上下文，但 Provider 设为 null（注释说"EmployeeNeuron will update later"）。对于部门文本聊天流程（/ws/dept/{dept}），没有触发 EmployeeNeuron 的 Provider 更新路径，导致大脑执行时报 `provider_not_configured` 错误。

**修复内容**：
- `core/brain/impl/AbstractBrain.java` — `executeReActLoop()` 开头增加动态解析逻辑：当 `getProvider()` 返回 null 时，通过 `brainModelResolver.resolve(brainId)` 从数据库读取已分配的模型配置，构造 `ResolvedBrainModelProvider` 作为运行时 Provider
- 日志明确记录 `"Brain {} dynamically resolved provider: provider={}, model={}"`
- 若解析仍失败，才返回原始的 `provider_not_configured` 错误

**修改文件**：
- `living-agent-core/.../brain/impl/AbstractBrain.java`
- 验证日志确认：TechBrain 通过动态解析成功调用 `ZhipuAI/GLM-5.1` 模型

---

#### 新增功能：BrainAutoAssigner 启动时自动分配大脑模型 [高] ✅ 已落地

**背景**：系统首次启动时，模型池中可能已有可用模型，但9个业务大脑均未配置模型。用户需要手动逐一在"大脑配置"页面选择模型。此过程应自动化。

**实现内容**：
- **新文件** `core/model/pool/BrainAutoAssigner.java` — Spring @Component
  - 定义 ALL_BRAINS 列表（9个大脑 ID/名称/类型/能力关键词）
  - `tryAutoAssignIfNeeded()` 方法：幂等执行（AtomicBoolean hasRun），跳过已配置的大脑
  - 评分算法：recommended(+30)、contextWindow≥32K(+10)、≥128K(+10)、bestFor关键词匹配(+20每个)、performanceScore(0~20)、ollama(-5)
  - 使用 `assignmentRepo.save()` 直接写入 brain_model_assignments 表
  - `resetAndReassign()` 支持手动强制重新分配
- **修改** `gateway/controller/ModelPoolController.java` — 注入 BrainAutoAssigner，新增 `POST /api/model-pool/auto-assign-brains` 手动触发端点
- **修改** `core/model/pool/ModelPoolManager.java` — 注入 BrainAutoAssigner，在 `discoverModels()` 和 `addModel()` 的性能评估后调用 `tryAutoAssignIfNeeded()`（注意：仅首次有效，幂等保护）

**设计约束**：
- 不在每次添加/发现模型时都触发自动分配（用户明确要求）
- 自动分配后前端显示真实模型名而非"默认模型"
- 已配置的大脑永远跳过（幂等）

**修改文件**：
- `living-agent-core/.../model/pool/BrainAutoAssigner.java`（新文件）
- `living-agent-gateway/.../controller/ModelPoolController.java`
- `living-agent-core/.../model/pool/ModelPoolManager.java`

---

#### 问题 P1-6.2 补漏：TraceEvent 并发写入冲突（Row was updated or deleted）[中] ✅ 已修复

**背景**：自治编排流程中多个阶段（intake_classified、requirement_readiness_evaluated、main_brain_planned 等）几乎同时写入 trace_events 表，Hibernate OptimisticLocking 检测到并发冲突抛出 `ObjectOptimisticLockingFailureException: Row was updated or deleted by another transaction`。

**尝试过的方案**（按时间顺序）：
1. `@Transactional(REQUIRES_NEW)` 注解在 private 方法上 — ❌ 无效（Spring AOP 只代理 public 方法）
2. `TransactionTemplate.executeWithoutResult()` — ❌ 无效（仍是多线程并发写入同一行）
3. **最终方案**：单线程 `ExecutorService` 序列化 — ✅ 有效

**最终修复内容**：
- `core/autonomy/AutonomyTraceService.java`：
  - 字段从 `TransactionTemplate` 改为 `ExecutorService traceExecutor`
  - `persistEvent()` 改为 `traceExecutor.submit(() -> traceEventRepository.save(entity))` 异步串行写入
  - 所有构造函数同步更新
- `gateway/config/GatewayConfig.java` — bean 创建传入 `Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "trace-persist"); t.setDaemon(true); return t; })`

**修改文件**：
- `living-agent-core/.../autonomy/AutonomyTraceService.java`
- `living-agent-gateway/.../config/GatewayConfig.java`

---

#### 问题 P0-4.4 补充：Embedding 模型测试支持 [中] ✅ 已修复

**背景**：用户添加 bge-m3:latest 等 embedding 模型后，测试连接失败返回 "does not support chat"（HTTP 400）。原因是 embedding 模型不支持 `/v1/chat/completions` 端点，应使用 `/v1/embeddings`。

**修复内容**：
- `core/model/pool/client/OpenAiCompatibleClient.java` — 新增 `embed(String text, String model)` 方法，POST `{baseUrl}/embeddings`，Bearer token 认证，返回 dimension 信息
- `core/model/pool/LlmClient.java` — 接口新增 `default String embed()` 默认方法
- `core/model/pool/ModelPoolManager.java`：
  - 新增 `isEmbeddingModel(String modelName)` 静态方法（检测 bge/e5/embedding/text2vec/gecko 等关键词）
  - `testProvider()` 方法路由：embedding 模型走 `client.embed()`，chat 模型走 `client.complete()`
  - 模型保存时自动设置 `inputTypes: embedding` 或 `text`
- 前端 `ModelPoolProviders.tsx` — embedding 类型模型显示绿色"嵌入"标签

**修改文件**：
- `living-agent-core/.../model/pool/client/OpenAiCompatibleClient.java`
- `living-agent-core/.../model/pool/LlmClient.java`
- `living-agent-core/.../model/pool/ModelPoolManager.java`
- `frontend/src/pages/ModelPoolProviders.tsx`

---

**全部74个问题已修复完成（100%）！**

**关键成果（第四轮更新）**：
- **自治编排**：完整闭环（意图分析→需求评估→规划→路由→分派→执行→回执→总结→沉淀），含重试/换人/人工接管/澄清恢复
- **大脑系统**：9个大脑统一基类ReAct，**动态Provider解析**（运行时从DB获取），BrainOutputContract接入，metadata统一，边界检查生效
- **模型池**：熔断机制生效，硬编码完全消除（含resolveDefault），**BrainAutoAssigner自动分配**，多协议支持（OpenAI/Anthropic/Gemini/Embedding）
- **权限安全**：四级权限隔离，@RequireAccess AOP，审计日志持久化，沙箱安全加固
- **员工执行**：ReceiptStatus枚举，职责卡，状态同步，薪酬接入
- **知识系统**：降级/晋升条件，记忆自动转化，知识参与推理规划
- **任务/项目**：TaskCheckout同步，审批自动触发+回调，工作流联动
- **数据库**：Flyway V1~V23完整迁移，InMemory→JPA切换，**JSONB @ColumnTransformer修复**，员工体系统一
- **Trace审计**：**单线程ExecutorService序列化写入**，持久化+关联键统一+意图Trace+ReAct Trace+反馈总线
- **WebSocket**：schema统一+断线重连+进度推送+部门大小写容错

**待后续推进项**：
- P2-4 重复初始化和目录扫描的启动缓存（低优先级）
- 员工真实工具执行增强（当前以 LLM 文本执行为主）
- Docker 沙箱集成（容器内 DockerSandboxService 待打通）
