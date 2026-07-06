# 模型池到对话链路实际代码问题分析与改进计划

> 日期：2026-04-29  
> 本次复核：按当前代码重新检查 `Provider` 注入、对话路由、Python Daemon service、模型池 API 与安全策略  
> 范围：`living-agent-core`、`living-agent-gateway`、`scripts/python/model_daemon.py`  
> 结论状态：代码相比上一轮继续改进：`EmployeeNeuron.processWithBrain()` 已增加运行时 Provider 兜底注入；`FixedEmployeeRegistry` 已把神经元创建后移到 `ApplicationReadyEvent` 并增加 delegateBrain 绑定校验；`BrainModelResolver` 的 Selector fallback 已回查模型池补全连接信息；`ProviderFactory` 已显式拒绝非 `OPENAI_COMPATIBLE` 协议；`getProviderWithoutKey()` 已改为返回 `cloneWithoutKey()`。当前剩余重点转为：`BrainRegistryImpl.startAll()` 仍以 provider=null 启动、`ApplicationReadyEvent` 监听器顺序仍可能导致 Router 早于固定员工刷新、`TOOL_CALL -> chatNeuron` 仍缺权限门禁、Provider 列表仍直接返回实体、以及 Daemon service 与 Java public API 仍不一致。

## 1. 最新结论摘要

当前代码已经具备模型池到 Layer 1 大脑调用的主体链路：

```text
/api/model-pool 或 /api/brain-models
  -> BrainModelAssignment / ProviderConfig / LlmModel
  -> BrainModelResolver.resolve(brainId)
  -> ProviderFactory.create(brainId)
  -> ResolvedBrainModelProvider
  -> EmployeeNeuron.createBrainContext()
  -> BrainContext.provider
  -> AbstractBrain.executeReActLoop()
```

本轮实际代码复核后的核心结论：

1. **Provider 注入链路已存在，且 `AbstractBrain.start()` 已支持 running 状态下更新 Provider context**  
   `AbstractBrain.start()` 当前在 `running == true` 且新 context 带 Provider 时，会调用 `updateContextWithProvider(context)`，不再无条件丢弃 Provider。因此上一版"running 幂等保护完全阻断 Provider 更新"的判断需要下调风险。

2. **`EmployeeNeuron.processWithBrain()` 的运行时 Provider 兜底已实现，但仍缺失失败后的用户可诊断返回**  
   当前 `processWithBrain()` 会先调用 `ensureDelegateBrainProvider()`；当 `delegateBrain instanceof AbstractBrain` 且 `ab.hasProvider()==false` 时，会通过 `ProviderFactory.create(delegateBrain.getId())` 创建 Provider 并调用 `ab.updateProvider(provider)`。这已经覆盖"启动阶段 ProviderFactory 失败、后续模型池补齐"的一部分场景。仍需补充：创建失败时只静默跳过/日志记录，最终仍可能在 Brain 内返回 `Provider 未配置`，缺少面向 API/WebSocket 的明确错误结构和健康状态。

3. **固定员工创建顺序已有改善，但 `ApplicationReadyEvent` 监听器之间仍无强顺序保证**  
   `FixedEmployeeRegistry.init()` 现在只加载定义；真正 `createAndStartAllEmployees()` 已移到 `ApplicationReadyEvent`，并在创建后执行 `validateDelegateBrainBindings()`。这降低了"早于 Brain 注册创建导致 delegateBrain 永久为空"的概率。但 `LivingAgentCoreConfig.onApplicationReady()` 同样监听 `ApplicationReadyEvent` 并初始化 `ChatNeuronRouter`，两个 listener 没有 `@Order` 或显式调用关系，Router 仍可能先扫描，此时固定员工神经元还未注册，`departmentBrains` 可能为空且不会自动刷新。

4. **`BrainRegistryImpl.startAll()` 仍用 provider=null context 启动所有 Brain**  
   后续 EmployeeNeuron 启动和运行时兜底可以补 Provider，但首轮 start 后到补齐前仍存在 `running && provider=null` 窗口；如果固定员工未创建、Router 直接路由到 Brain，或启动期间复杂任务进入，仍可能触发 `Provider 未配置`。

5. **TOOL_CALL 路由仍是临时绕行，且未执行权限门禁**  
   `ChatNeuronRouter.selectTargetNeuronWithPermission()` 当前对 `TOOL_CALL` 直接返回 `chatNeuron`，没有检查 `AccessLevel.DEPARTMENT/FULL`。这避免了 `BitNetNeuron -> tool_intent` 的 Daemon 断裂，但与权限规范不一致：`CHAT_ONLY` 用户也可能进入 Python chat 的工具提示/自动路由逻辑。

6. **模型池部分边界已修复，但 Provider 列表安全仍未完成**  
   `BrainModelResolver.buildFromSelectorModel()` 已回查 `ProviderConfigRepository + LlmModelRepository`，不再生成空 baseUrl/apiKey 的 resolved model；`ProviderFactory` 已对非 `OPENAI_COMPATIBLE` 协议记录 warn 并返回 null；`getProviderWithoutKey()` 已返回 `cloneWithoutKey()`，不再直接修改托管实体。仍存在的问题是 `ModelPoolManager.getAllProviders()` 和 Controller 列表接口仍直接返回启用 Provider 实体，可能暴露 `apiKeyEncrypted`。

7. **Python Daemon service 与 `ModelManagerImpl` 仍不完全兼容**  
   会话处理只支持 `asr`、`llm`、`chat`、`classify_intent`、`tts`、speaker、`status`、`clear_history`。`ModelManagerImpl` 中的 `bitnet`、`tool_intent`、`tool`、control `llm_chat`、control `vllm_chat` 仍未被 Daemon 支持。

## 2. 实际代码链路复核

### 2.1 模型池与 Provider 调用链路

实际代码位置：

- `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ModelPoolController.java`
- `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/BrainModelConfigController.java`
- `living-agent-core/src/main/java/com/livingagent/core/model/pool/ModelPoolManager.java`
- `living-agent-core/src/main/java/com/livingagent/core/model/pool/BrainModelAssigner.java`
- `living-agent-core/src/main/java/com/livingagent/core/model/pool/BrainModelResolver.java`
- `living-agent-core/src/main/java/com/livingagent/core/provider/impl/ProviderFactory.java`
- `living-agent-core/src/main/java/com/livingagent/core/provider/impl/ResolvedBrainModelProvider.java`

当前已具备能力：

- 供应商、模型、分配记录的 CRUD 基本存在。
- `ModelPoolManager.seedDefaults()` 会初始化默认供应商和内置模型。
- `ModelPoolManager.testProvider()` 使用 `LlmClientFactory` 测试供应商连接。
- `BrainModelResolver.resolve(brainId)` 按"显式分配 -> Selector -> 默认模型"解析 `ResolvedBrainModel`。
- `ProviderFactory.create(brainId)` 使用 Resolver 创建 `ResolvedBrainModelProvider`。
- `ResolvedBrainModelProvider` 实现 `Provider`，以 OpenAI-compatible `/chat/completions` 发起请求，并支持基础 tools payload。

当前注意点：

- `LlmClientFactory` 与 `ResolvedBrainModelProvider` 是两套客户端体系：前者用于模型池测试，后者用于 Brain ReAct。
- `ProviderFactory` 当前已检查 `Protocol`，仅 `OPENAI_COMPATIBLE` 会创建 `ResolvedBrainModelProvider`；非兼容协议会记录 warn 并返回 null。
- `ResolvedBrainModelProvider` 的 URL 拼接逻辑主要面向 OpenAI-compatible：baseUrl 不是 `/v1` 结尾时追加 `/v1/chat/completions`。

### 2.2 Brain 启动与 Provider context 更新

当前 `AbstractBrain.start()` 实现已经变化：

```text
if (running) {
  if (context != null && context.getProvider() != null) {
    updateContextWithProvider(context);
    return;
  }
  log.warn("already running, ignoring start without Provider");
  return;
}
```

这意味着：

- `BrainRegistryImpl.startAll()` 先用空 Provider context 启动 Brain 后，后续 `EmployeeNeuron.doStart()` 如果传入带 Provider 的 context，理论上可以通过 `updateContextWithProvider()` 覆盖 context。
- 旧版"running 后一定无法覆盖 Provider"的问题已修复。

仍存在的问题：

- `BrainRegistryImpl.createBrainContext()` 仍固定传入 `provider=null`，因此首轮启动后的 Brain 是无 Provider 状态。
- `EmployeeNeuron` 必须成功拿到 `delegateBrain` 且成功执行 `delegateBrain.start(brainContextWithProvider)`，Provider 才能在启动阶段更新进去。
- `processWithBrain()` 已有运行时兜底检查，但只在 `AbstractBrain.hasProvider()==false` 时创建 Provider；若模型池配置变更后需要切换 Provider/模型，当前不会自动刷新已有 Provider。
- 兜底创建失败时没有 API 层结构化错误，只能依赖日志和 Brain 内部错误响应。

### 2.3 固定员工神经元与 delegateBrain 绑定

实际链路：

```text
FixedEmployeeRegistry.@PostConstruct init()
  -> registerAllFixedEmployees()
  -> createAndStartAllEmployees()
  -> createAndStartNeuron(employee)
  -> EmployeeNeuron.create(de, brainRegistry, tools)
  -> brainRegistry.getByDepartment(employee.getDepartmentId())
```

复核后的状态与风险点：

- `FixedEmployeeRegistry.@PostConstruct init()` 现在只执行 `registerAllFixedEmployees()` 和工具校验，不再立即创建神经元。
- `FixedEmployeeRegistry.onApplicationReady()` 中才执行 `createAndStartAllEmployees()`，并设置 `brainsRegistered=true`、执行 `validateDelegateBrainBindings()`。
- 这基本修复了"固定员工神经元在 `LivingAgentInitializer.@PostConstruct` 注册 Brain 之前创建"的原始问题。
- 但 `EmployeeNeuron.delegateBrain` 仍是 final 字段，创建失败后没有 rebinding 机制；如果某个 Brain 注册失败、department 映射缺失或事件顺序异常，仍会永久为空。
- `ChatNeuronRouter` 也是 `ApplicationReadyEvent` listener，且当前没有 `@Order`。如果 Router 先 initialize，再由 `FixedEmployeeRegistry` 注册固定员工神经元，则 Router 扫描不到 departmentBrain，后续没有自动 refresh。

### 2.4 对话路由现状

`ChatNeuronRouter` 当前路由策略：

```text
GREETING / CASUAL_CHAT / SIMPLE_QUESTION -> chatNeuron
TOOL_CALL -> chatNeuron（已临时修复，不再走 BitNetNeuron）
COMPLEX_TASK + FULL -> mainBrain
COMPLEX_TASK + DEPARTMENT -> departmentBrain
COMPLEX_TASK + LIMITED + admin/cs -> AdminBrain
UNKNOWN -> chatNeuron
```

当前结论：

- 闲聊和工具意图的主路径均回到 `Qwen3Neuron -> service=chat`。
- `BitNetNeuron` 仍注册为 `neuron://tool/bitnet/001`，但正常 `TOOL_CALL` 路由已不使用它。
- 复杂任务仍会走 MainBrain/DepartmentBrain，依赖 Provider context 是否成功注入。

### 2.5 Python Daemon service 兼容性

`model_daemon.py` 会话处理支持：

- `asr`
- `llm`
- `chat`
- `classify_intent`
- `tts`
- `speaker_register`
- `speaker_verify`
- `speaker_list`
- `speaker_delete`
- `status`
- `clear_history`

`ModelManagerImpl` 中仍存在但 Daemon 不支持的调用：

| Java 方法 | service / command | 发送方式 | 当前状态 |
|---|---|---|---|
| `generateTextBitNet()` | `bitnet` | session request | 不支持 |
| `processToolIntent()` | `tool_intent` | session request | 不支持，但主路由已绕开 |
| `executeToolCall()` | `tool` | session request | 不支持 |
| `chatWithHistory()` / `chatAsync()` | `llm_chat` | control request | control handler 不支持 |
| `chatWithImages()` / `chatWithImageAsync()` | `vllm_chat` | control request | control handler 不支持 |

补充结论：当前 `Grep` 未发现除 `ModelManagerImpl` 自身以外的明显调用点，但这些 public 方法仍会误导后续开发，建议标注或清理。

## 3. 问题清单

### P0-1：固定员工创建后移已完成，但 Router 与固定员工 `ApplicationReadyEvent` 顺序仍不确定

严重级别：高。

已完成改进：

- `FixedEmployeeRegistry.@PostConstruct init()` 现在只加载固定员工定义，不再创建神经元。
- `createAndStartAllEmployees()` 已迁移到 `onApplicationReady(ApplicationReadyEvent)`。
- 创建后已执行 `validateDelegateBrainBindings()`，统计已绑定/未绑定 delegateBrain。
- `EmployeeNeuron` 创建时已注入 `ProviderFactory`。

剩余表现：

- `LivingAgentCoreConfig.onApplicationReady()` 同样监听 `ApplicationReadyEvent` 并调用 `ChatNeuronRouter.initialize()`。
- 两个 listener 目前无 `@Order`，不能保证固定员工神经元已注册后 Router 才扫描。
- 如果 Router 先扫描，`departmentBrains` 可能为空；后续固定员工注册完成后不会自动刷新 Router。
- `EmployeeNeuron.delegateBrain` 仍为 final，少数未绑定场景仍无法 rebinding。

影响：

- 复杂任务可能被降级到 `chatNeuron`，即使固定员工神经元随后已注册。
- 启动日志可能显示固定员工绑定正常，但 Router 的 department brain 映射仍旧为空。

建议修复：

1. 给两个 `ApplicationReadyEvent` listener 增加明确 `@Order`：先创建固定员工神经元，再初始化/刷新 `ChatNeuronRouter`。
2. 或在 `FixedEmployeeRegistry.onApplicationReady()` 完成后显式调用 Router refresh。
3. 或让 `ChatNeuronRouter.route()` 在 departmentBrain 缺失时从 `NeuronRegistry` 懒加载/重新扫描一次。
4. 保留启动后校验，并把 Router 发现的 `mainBrain/departmentBrains` 数量也纳入健康检查。

### P0-2：Brain 首次仍由 `BrainRegistryImpl.startAll()` 使用 provider=null context 启动

严重级别：中到高。

当前缓解：

- `AbstractBrain.start()` 已允许 running 状态下用带 Provider 的 context 调用 `updateContextWithProvider()`。

仍存在风险：

- 在 EmployeeNeuron 带 Provider context 更新之前，Brain 是 running 但 provider=null。
- 如果复杂任务在这个窗口进入，会返回 `Provider 未配置`。
- 启动阶段 Provider 创建失败后，`EmployeeNeuron.processWithBrain()` 已能在消息处理前兜底重试；但直接路由到 Brain 的路径仍依赖 Brain 自身是否具备 Provider。
- `BrainRegistryImpl.createBrainContext()` 仍没有能力创建 Provider。

建议修复：

1. 给 `BrainRegistryImpl` 注入或传入 `ProviderFactory`，启动时直接创建 Provider。
2. 或不要在 `brainRegistry.startAll()` 中启动需要 Provider 的业务 Brain，只注册，由 EmployeeNeuron 启动。
3. 在 `AbstractBrain.process()` 或 `EmployeeNeuron.processWithBrain()` 前增加 Provider 缺失检测与重建。
4. 启动后输出健康检查：每个 Brain 的 `providerPresent=true/false`。

### P0-3：`EmployeeNeuron.processWithBrain()` 运行时 Provider 兜底已实现，但需要补刷新与诊断

严重级别：中。

已完成改进：

- `processWithBrain()` 当前在 `delegateBrain.process(message)` 前调用 `ensureDelegateBrainProvider()`。
- `ensureDelegateBrainProvider()` 会判断 `delegateBrain instanceof AbstractBrain`。
- 当 `ab.hasProvider()` 为 false 且 `providerFactory != null` 时，会调用 `providerFactory.create(delegateBrain.getId())`。
- 创建成功后调用 `ab.updateProvider(provider)`，并记录成功日志。

剩余问题：

- 只处理"Provider 缺失"，不处理"模型池配置已变更但旧 Provider 仍存在"的刷新问题。
- Provider 创建失败时没有把具体错误传播到路由/API 层，最终用户仍可能只看到泛化失败。
- 没有记录 resolverSource（assignment/selector/default）和 modelName，排障仍需要查日志。

建议修复：

1. 增加 Provider 配置版本或 `updatedAt` 检查，模型池配置变更后刷新对应 Brain Provider。
2. Provider 创建失败时返回结构化诊断：brainId、providerId、modelName、failureReason（不含 apiKey）。
3. 健康检查中暴露 `providerPresent/providerName/modelName/resolverSource`。
4. 对失败原因记录日志，但不得打印 apiKey。

### P0-4：复杂任务路由直接返回 Brain 神经元，仍依赖 ChatNeuronRouter 扫描时机

严重级别：中到高。

现状：

- `ChatNeuronRouter.initialize()` 在 `LivingAgentCoreConfig.onApplicationReady()` 中扫描 `NeuronRegistry.getAll()`。
- `FixedEmployeeRegistry.onApplicationReady()` 同样在 `ApplicationReadyEvent` 中创建并注册固定员工神经元。
- 由于两个 listener 无显式顺序，Router 可能在固定员工注册前扫描。
- Router 当前没有自动 refresh；`departmentBrains` 一旦为空，会一直影响复杂任务路由。

建议修复：

1. 在固定员工注册完成后显式调用 `ChatNeuronRouter.initialize()` 或新增 `refresh()`。
2. 使用 `@Order` 明确事件顺序，保证固定员工神经元注册先于 Router 扫描。
3. 在路由时如未找到 departmentBrain，可按需从 `NeuronRegistry` 再查一次。
4. 输出路由健康检查：`mainBrain` 是否存在、各部门 Brain 数量。

### P1-1：`ModelManagerImpl` 与 Python Daemon service 表不一致

严重级别：中。

现状：

- `TOOL_CALL` 主路径已经绕开 `processToolIntent()`，但不可用 public 方法仍存在。
- `bitnet`、`tool_intent`、`tool` session service 不支持。
- `llm_chat`、`vllm_chat` control command 不支持。

建议：

1. 为 `ModelManager` 接口增加"支持状态"注释或拆分接口。
2. 暂时不用的方法标记 deprecated，返回明确错误，不再真的发送未知 service。
3. 或补齐 Daemon 端 service，并加端到端测试。
4. `BitNetNeuron` 如短期不使用，应在文档中标注为停用/备用。

### P1-2：模型池供应商列表接口仍可能泄露 apiKey，详情误修改实体问题已修复

严重级别：中。

已完成改进：

- `ProviderConfig` 有 `cloneWithoutKey()` 和 `getMaskedApiKey()`。
- `ModelPoolManager.getProviderWithoutKey()` 当前已返回 `config.cloneWithoutKey()`，不再直接把托管实体的 `apiKeyEncrypted` 置空。

仍存在的问题：

- `ModelPoolManager.getAllProviders()` 仍直接返回 `providerRepo.findByEnabledTrue()`。
- `ModelPoolController.getAllProviders()` 若直接返回 providers，仍可能暴露 `apiKeyEncrypted`。

建议：

1. 列表和详情接口统一返回 DTO，不直接返回 JPA Entity。
2. DTO 中只返回 `hasApiKey` 和 `maskedApiKey`，不返回 `apiKeyEncrypted`。
3. 对新增/更新接口也避免把保存后的完整实体直接回传。

### P1-3：`BrainModelConfigController` 与 API 文档不一致，且 path-variable 不适合 brainId

现状：

- `GET /api/brain-models/{brainId}` 存在。
- `PUT /api/brain-models?brainId=...` 存在。
- `DELETE /api/brain-models?brainId=...` 存在。
- 文档中的 `PUT/DELETE /api/brain-models/{brainId}` 当前不存在。
- `brainId` 可能形如 `neuron://tech/...`，包含 `/`，不适合作为普通 path variable。

建议：

1. 文档明确推荐 query 参数形式。
2. 前端统一使用 query 参数或稳定 `brainKey`。
3. 如果保留 path-variable，需要使用 catch-all mapping 或 URL encode，并补充测试。

### P1-4：`BrainModelAssignment.brainName` 字段写入语义不一致

现状：

- `ModelPoolManager.assignModel()` 将模型 displayName 写入 `brainName`。
- `BrainModelAssigner.assignModel()` 将真实 brainName 写入 `brainName`。

建议：

- 以 `/api/brain-models` 作为主入口。
- `/api/model-pool/assignments/{brainId}` 如保留，应复用 `BrainModelAssigner` 或修正字段。

### P1-5：Selector fallback 连接信息补全已修复，仍需明确降级策略

现状：

- `BrainModelResolver.buildFromSelectorModel()` 已通过 `providerRepo.findById(selectorModel.provider())` 和 `modelRepo.findByProviderIdAndModelName(...)` 回查模型池。
- 已不再生成 baseUrl/apiKey 为空的 `ResolvedBrainModel`。
- 当 provider/model 缺失时返回 null，外层 `resolve()` 会继续 `orElseGet(resolveDefault)`。

剩余建议：

1. 日志中补充 selector brainId、providerId、modelName，便于定位配置缺失。
2. 给默认回退路径添加 resolverSource 标记，避免误以为 selector 生效。
3. 对默认 qwen provider 缺失/API key 为空的场景给出更明确错误。

### P1-6：`ProviderFactory` 已显式限制 Protocol，但缺少多协议实现

现状：

- `ProviderFactory.createFromResolvedModel()` 已读取 `Protocol`。
- `protocol == null` 时默认 `OPENAI_COMPATIBLE`。
- 非 `OPENAI_COMPATIBLE` 时记录 warn 并返回 null，不再错误按 `/v1/chat/completions` 调用。

剩余风险：

- Anthropic、Gemini、OpenAI Responses 等协议在 Brain ReAct 链路中仍不可用，只是失败方式更安全。
- API 层/前端如果允许用户选择这些协议，应提示"测试可用不等于 Brain 链路可用"。

建议：

1. `ProviderFactory` 中继续保留 Protocol switch。
2. 已支持：`OPENAI_COMPATIBLE`。
3. 暂不支持：API 返回明确不支持原因，或实现 `AnthropicProvider`、`GeminiProvider`、`OpenAiResponsesProvider`。

### P2-1：Layer 3 文档与代码定位需统一

现状：

- old 架构写 Layer 3 ToolNeuron 灵活配置。
- 当前实际：`TOOL_CALL` 已路由到 chatNeuron；BitNetNeuron 处于注册但主路径停用状态。

建议：

- 明确 Layer 3 短期由 Python `service=chat` 内部自动路由承担。
- 如果未来恢复 BitNetNeuron，必须先修复 Daemon `tool_intent` service。

### P2-2：多个 Brain 子类重复 ReAct / ToolCall 逻辑

现状：

- 多个部门 Brain 子类存在重复 tool-call loop。
- 父类 `AbstractBrain` 已有 `executeReActLoop()`。

建议：

- 子类逐步收敛到父类公共循环。
- 后续 Provider、tool calling、usage tracking 修复优先落在父类。

## 4. 改进计划

### 阶段 A：稳定初始化顺序与 delegateBrain 绑定（P0）

目标：确保固定员工神经元创建时一定能绑定到对应 Brain，且 Brain 启动后有 Provider。

任务：

1. 统一初始化流程。
   - 已有：`modelPoolManager.seedDefaults()` 在 `LivingAgentInitializer.@PostConstruct` 中先执行。
   - 已有：所有 Brain 在 `LivingAgentInitializer.@PostConstruct` 中注册到 `BrainRegistry`。
   - 已有：固定员工与 EmployeeNeuron 创建已后移到 `FixedEmployeeRegistry.onApplicationReady()`。
   - 待修复：`ChatNeuronRouter` 初始化必须保证在固定员工神经元注册之后，或支持后续 refresh。
2. 避免多个事件 listener 隐式竞争。
   - 给 `FixedEmployeeRegistry.onApplicationReady()` 与 `LivingAgentCoreConfig.onApplicationReady()` 加 `@Order`。
   - 或把 Router refresh 放进固定员工注册完成后的显式流程。
3. 增加 delegateBrain 与 Router 双重绑定校验。
   - 已有：固定员工 delegateBrain 绑定数量校验。
   - 待补：Router 发现的 `mainBrain/departmentBrains` 数量校验。
4. 如短期无法调整顺序，增加 rebinding/refresh 能力。
   - 取消 `EmployeeNeuron.delegateBrain final` 或新建替换神经元。
   - 在 Brain 注册完成后重新绑定未绑定员工神经元。
   - Router 在路由缺失时懒刷新。

验收标准：

- 所有固定数字员工神经元都能绑定到正确 department/main Brain。
- `ChatNeuronRouter.initialize()` 后能发现 MainBrain 和各部门 Brain。
- 不依赖 Spring `@PostConstruct` 非确定顺序。

### 阶段 B：补齐运行时 Provider 兜底与健康检查（P0）

目标：即使启动阶段 Provider 缺失，消息处理前也能恢复。

任务：

1. 已完成：在 `EmployeeNeuron.processWithBrain()` 中增加 `ensureDelegateBrainProvider()`。
2. 已完成：使用 `ProviderFactory.create(delegateBrain.getId())` 重建 Provider。
3. 已完成：创建成功后调用 `AbstractBrain.updateProvider(provider)`。
4. 待修复：给 `BrainRegistryImpl.startAll()` 增加 Provider 创建能力，或避免用空 Provider 启动业务 Brain。
5. 待修复：模型池配置变更后刷新已有 Brain Provider。
6. 增加健康检查接口或日志：
   - brainId
   - running state
   - providerPresent
   - providerName
   - modelName
   - resolverSource（assignment/selector/default，后续可加）

验收标准：

- 复杂任务不再出现 `Provider 未配置`。
- 修改模型池配置后，新请求能够使用新的 Provider 或至少明确提示需要刷新。
- provider 创建失败时返回可诊断错误，不吞掉原因。

### 阶段 C：收敛 Layer 3 与 ModelManager service（P1）

目标：消除未知 service 隐患。

任务：

1. 保留当前短期策略：`TOOL_CALL -> chatNeuron`。
2. 将 `BitNetNeuron` 标注为停用/备用，避免误用。
3. 对 `ModelManagerImpl` 不支持方法做处理：
   - deprecated 并返回明确错误；或
   - 改走 `service=chat` / `service=llm`；或
   - 补齐 Daemon service。
4. 如恢复 Layer 3，优先实现 `model_daemon.py` 的 `tool_intent` service，并补测试。

验收标准：

- 没有主业务路径会触发 `未知服务: tool_intent/bitnet/tool/llm_chat/vllm_chat`。
- ModelManager public 方法和 Daemon 支持表一致。

### 阶段 D：统一模型池 API、安全与协议边界（P1）

目标：让模型池 API 可安全联调，Resolver fallback 可用，协议行为明确。

任务：

1. API 文档更新：推荐 `/api/brain-models?brainId=...`。
2. Provider API 改用 DTO，统一脱敏。
3. 已完成：修复 `getProviderWithoutKey()` 修改托管实体的问题，当前返回 `cloneWithoutKey()`。
4. 修复 `BrainModelAssignment.brainName` 写入语义。
5. 已完成：修复 Selector fallback 的空 baseUrl/apiKey，当前回查 provider/model 补全。
6. 已完成基础边界：`ProviderFactory` 已限制仅 `OPENAI_COMPATIBLE` 可创建 Provider；待补多协议 Provider 或 API 提示。

验收标准：

- API 不返回完整 apiKey。
- 文档、前端、Controller 路径一致。
- Selector/default/assignment 三条 Resolver 路径都能创建 Provider 或明确失败。
- 非 OpenAI-compatible 协议不会被错误调用。

### 阶段 E：清理重复逻辑与文档（P2）

任务：

1. 更新 old 架构文档，标注 Layer 3 当前实际状态。
2. 清理 `BitNetNeuron`、`ToolNeuronModelSelector` 或标注备用。
3. 将部门 Brain 子类重复 tool-call loop 收敛到 `AbstractBrain.executeReActLoop()`。
4. 增加端到端测试文档或自动化测试。

## 5. 推荐实施顺序

1. **阶段 A**：先解决初始化顺序和 delegateBrain 绑定，这是 Provider 注入链路的前置条件。
2. **阶段 B**：补运行时 Provider 兜底和健康检查，消灭 `Provider 未配置`。
3. **阶段 C**：收敛 Layer 3 与 ModelManager service，避免未知服务。
4. **阶段 D**：统一 API、安全和协议边界。
5. **阶段 E**：清理文档、死代码和重复逻辑。

## 6. 最小修复版本建议

最小可运行版本建议包含：

- 已基本完成：固定员工神经元创建后移到 `ApplicationReadyEvent`，Brain 注册早于固定员工创建。
- 待修复：为固定员工创建 listener 与 Router 初始化 listener 增加明确顺序，或注册后显式刷新 Router。
- 待修复：给未绑定 delegateBrain 的员工神经元增加 rebinding 或重建机制。
- 已完成：在 `EmployeeNeuron.processWithBrain()` 前增加 Provider 兜底注入。
- 待修复：`BrainRegistryImpl.startAll()` 避免 provider=null 启动业务 Brain，或启动时注入 Provider。
- 待修复：`TOOL_CALL -> chatNeuron` 临时方案增加 `DEPARTMENT/FULL` 权限检查。
- 待修复：将 Provider 列表接口改为 DTO 脱敏。
- 待修复：更新 `API_REFERENCE.md` 中 `/api/brain-models` 的推荐调用方式。

完成后目标链路：

```text
模型池配置供应商和模型
  -> 绑定模型到 MainBrain/DepartmentBrain
  -> BrainModelResolver 解析
  -> ProviderFactory 创建 ResolvedBrainModelProvider
  -> Brain 注册完成
  -> 固定员工神经元绑定 delegateBrain
  -> EmployeeNeuron / AbstractBrain 稳定注入 Provider
  -> AbstractBrain ReAct 调用真实 LLM
  -> REST/WebSocket 对话返回真实响应
```

## 7. 本轮相对上一版计划的修正

| 项目 | 上一版判断 | 本轮代码复核修正 |
|---|---|---|
| `AbstractBrain.start()` | running 会阻断 Provider context 更新 | 当前已支持带 Provider context 的更新 |
| TOOL_CALL 主路径 | 仍会路由到 BitNetNeuron | 当前已临时路由到 chatNeuron |
| 首要 P0 | Provider 更新被 running 完全阻断 | 当前首要是初始化顺序、delegateBrain 绑定、运行时兜底 |
| `EmployeeNeuron.processWithBrain()` | 计划中建议加兜底 | 当前已实现 `ensureDelegateBrainProvider()`，剩余刷新/诊断问题 |
| 固定员工初始化 | 可能早于 Brain 注册 | 当前已后移到 `ApplicationReadyEvent`，但 Router listener 顺序仍需修复 |
| `BrainRegistryImpl.startAll()` | 用空 Provider context 启动 | 当前仍是事实，但可被后续带 Provider context 和运行时兜底缓解 |
| Selector fallback | 生成空 baseUrl/apiKey | 当前已回查 provider/model 补全，缺失时回退默认 |
| Provider 协议边界 | 未按 Protocol 分流 | 当前已显式只支持 `OPENAI_COMPATIBLE`，其他协议安全失败 |
| Provider 安全 | 泛泛提到脱敏 | 本轮确认详情 `getProviderWithoutKey()` 已 clone，列表接口仍直接返回实体，需 DTO 化 |

---

## 8. TOOL_CALL 意图与工具调用架构分析（2026-04-29）

### 8.1 两种工具调用机制

系统中存在两种**完全不同**的工具调用机制，必须严格区分：

| 机制 | 位置 | 触发方式 | 权限要求 | 状态 |
|------|------|---------|---------|------|
| **Layer 3 ToolNeuron 工具检测** | `BitNetNeuron` / `ToolNeuron` | 预先检测工具意图 → 执行工具 | 需要 **DEPARTMENT** 或更高权限 | ⚠️ 部分可用 |
| **Layer 1 Brain ReAct 循环** | `AbstractBrain.executeReActLoop()` | LLM 自己决定是否调用工具 | 需要 **DEPARTMENT** 或更高权限 | ✅ 正常使用 |

### 8.2 权限规范（强制）

根据 [06-security-permission.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/old/06-security-permission.md)：

| 级别 | 权限范围 | 可访问资源 |
|------|----------|-----------|
| **CHAT_ONLY** | 仅闲聊，禁止企业资源 | Qwen3-0.6B (Layer 2) |
| **LIMITED** | 部分大脑，禁止敏感知识 | Qwen3.5-27B, AdminBrain, CsBrain |
| **DEPARTMENT** | 本部门完整功能 | Qwen3.5-27B, **ToolNeuron (Layer 3)**, 本部门大脑 |
| **FULL** | 所有大脑和工具 | 所有模型 + MainBrain |

**关键约束**：
- **ToolNeuron (Layer 3) 需要 DEPARTMENT 或更高权限**
- **CHAT_ONLY 用户不能执行工具调用**

### 8.3 Layer 3 ToolNeuron 工具调用机制

```text
用户输入 → ChatIntentClassifier → TOOL_CALL 意图
    → 权限检查 (DEPARTMENT/FULL?)
    → ToolNeuron (Qwen3.5-2B 或 BitNet-1.58-3B)
    → 内部模型检测工具意图
    → 执行工具调用
```

**内部模型配置**（[02-core-architecture.md:91-96](file:///f:/SoarCloudAI/docker/living-agent-service/docs/old/02-core-architecture.md#L91-L96)）：
- 默认: Qwen3.5-2B (推荐，支持多模态)
- 备选: BitNet-1.58-3B (低资源环境)

**当前状态**：
- `BitNetNeuron` 已标记 `@Deprecated`
- Python Daemon 不支持 `tool_intent` 和 `tool` service
- TOOL_CALL 被临时路由到 `chatNeuron`

### 8.4 Layer 1 Brain ReAct 工具调用机制

```text
用户输入 → ChatNeuronRouter → COMPLEX_TASK 意图
    → 权限检查 (DEPARTMENT/FULL?)
    → DepartmentBrain.process()
    → AbstractBrain.executeReActLoop()
    → Provider.chat() → LLM 返回 tool_calls
    → AbstractBrain.executeToolCalls()
    → ToolRegistry.get() → Tool.execute()
```

**关键区别**：
- Brain 的工具调用是 **LLM 自主决策**，不是预先检测
- Brain 需要更大的模型 (Qwen3.5-27B)
- Brain 的工具调用是复杂任务处理的一部分
- **Brain 不需要 TOOL_CALL 意图**：Brain 在 ReAct 循环中自主决定是否调用工具

### 8.5 TOOL_CALL 意图只适合 Layer 3 ToolNeuron

**TOOL_CALL 意图的设计目的**（[02-core-architecture.md:92-94](file:///f:/SoarCloudAI/docker/living-agent-service/docs/old/02-core-architecture.md#L92-L94)）：
> Layer 3: 工具神经元 (ToolNeuron)
> ├── 职责: 工具检测、兜底处理、触发进化信号

**TOOL_CALL 意图只适合 Layer 3 ToolNeuron 使用**：
- ToolNeuron 预先检测用户是否想调用工具
- ToolNeuron 执行工具调用
- ToolNeuron 是**完全独立**的层级，不依赖 Layer 1 Brain

**Layer 1 Brain 不需要 TOOL_CALL 意图**（[02-core-architecture.md:79](file:///f:/SoarCloudAI/docker/living-agent-service/docs/old/02-core-architecture.md#L79)）：
> Layer 1: 主大脑 (MainBrain)
> ├── 职责: 复杂推理、跨部门协调、战略决策

Brain 通过 ReAct 循环自主决定工具调用，不需要预先的 TOOL_CALL 意图分类。

### 8.6 工具集完全分开

Layer 3 ToolNeuron 与 Layer 1 Brain ReAct 是两套独立工具调用机制，工具来源、调用方式、执行环境都不同，不能混用。

**Layer 3 ToolNeuron 工具集**（Python Daemon `model_daemon.py` 内部）：

- Python Daemon 内部硬编码的工具逻辑。
- 不依赖、不读取、不调用 Java `ToolRegistry`。
- 不拥有 GitHub、Docker、Trae、浏览器自动化等 Java 侧企业工具。
- 工具逻辑通过 `_build_tool_system_prompt()` 和 `NeuronRouter.route()` 处理。
- **不需要独立的 `tool_intent` 或 `tool` service**，工具路由逻辑在 `chat` service 内部完成。
- **当前状态：ToolNeuron 功能已实现**，就在 Python `chat` service 中。
- **TOOL_CALL 意图是 Layer 3 的专属意图**，Layer 1 Brain 不使用此意图。

**Layer 1 Brain 工具集**（Java `ToolRegistry` 注册）：

- 20+ 企业级工具，例如 GitHub、Docker、Trae、浏览器自动化、Office、PDF、搜索等。
- 通过 `ToolRegistry` 统一注册、管理和执行。
- 每个 Brain 在创建时获得完整的 `ToolRegistry` 工具列表。
- 不依赖 Python Daemon 内部轻量工具实现。
- 工具调用通过 `AbstractBrain.executeReActLoop()` → `executeToolCalls()` → `ToolRegistry.get()` → `Tool.execute()` 完成。
- **Brain 不需要 TOOL_CALL 意图**，在 ReAct 循环中自主决定工具调用。

因此：

- Layer 3 的工具检测只面向 Python Daemon 内部轻量工具。
- Layer 1 的 ReAct `tool_calls` 只面向 Java `ToolRegistry` 工具。
- 两者不存在共享工具注册表，也不存在根据工具名称在两套系统之间自动转发或改路由的逻辑。
- 文档和代码实现中应避免把 Layer 3 "工具意图检测/轻量工具执行"与 Layer 1 "Brain ReAct 企业工具调用"混为一谈。
- **TOOL_CALL 意图只属于 Layer 3，不应路由到 Layer 1 Brain**。

### 8.7 当前 TOOL_CALL 路由状态

**当前路由逻辑**（[ChatNeuronRouter.java:208-211](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/neuron/chat/ChatNeuronRouter.java#L208-L211)）：
```java
case TOOL_CALL -> {
    log.info("TOOL_CALL 意图暂时路由到 chatNeuron（由 Python chat 自动路由）");
    yield chatNeuron;  // ❌ 没有权限检查！
}
```

**当前路由是正确的**：

- TOOL_CALL 意图是 Layer 3 ToolNeuron 的专属意图
- `Qwen3Neuron` 调用 `modelManager.processChatWithIntent()`
- Python `chat` service 内部有 `_build_tool_system_prompt()` 和 `NeuronRouter.route()`
- 这就是 Layer 3 ToolNeuron 的实际实现方式
- **不需要独立的 `tool_intent` 或 `tool` service**
- **TOOL_CALL 不应该路由到 Layer 1 Brain**，因为 Brain 不需要此意图

**唯一需要修复的问题：权限检查缺失**

当前路由没有检查用户权限，CHAT_ONLY 用户也能触发工具调用路径，这与权限规范不一致。

### 8.8 修复方案：增加权限检查

**这是唯一需要修复的问题**：

```java
case TOOL_CALL -> {
    // 权限检查：ToolNeuron (Layer 3) 需要 DEPARTMENT 或更高权限
    if (accessLevel.ordinal() < AccessLevel.DEPARTMENT.ordinal()) {
        log.info("TOOL_CALL denied for {} access, downgrading to chatNeuron", accessLevel);
        yield chatNeuron;  // 降级到闲聊，不执行工具
    }
    
    // TOOL_CALL 路由到 chatNeuron（Layer 3 ToolNeuron 在 Python chat service 内部实现）
    // Layer 3 是完全独立的层级，不依赖 Layer 1 Brain
    log.debug("TOOL_CALL routed to chatNeuron for {} access", accessLevel);
    yield chatNeuron;
}
```

### 8.9 不需要"恢复"或"实现" ToolNeuron

**ToolNeuron 功能已经在 Python `chat` service 中实现**：

1. `model_daemon.py` 的 `chat` service 已有 `_build_tool_system_prompt()`
2. 已有 `NeuronRouter.route()` 内部工具路由逻辑
3. 不需要实现独立的 `tool_intent` 或 `tool` service

**唯一需要做的修复**：
- 在 `ChatNeuronRouter.selectTargetNeuronWithPermission()` 中增加权限检查
- CHAT_ONLY 用户降级到闲聊，不执行工具

**架构总结**：
- Layer 3 ToolNeuron (TOOL_CALL 意图) → Python chat service 内部工具 → 完全独立
- Layer 1 Brain ReAct (COMPLEX_TASK 意图) → Java ToolRegistry 工具 → 自主决策
- 两者不混用、不互相依赖、不互相转发
