# Brain / Model Pool Selector 改进说明

## 目标边界

本文件只记录三件事：

1. **模型池模型可用于新建个人数字助理**
2. **大脑配置用于主大脑与部门大脑绑定**
3. **系统可基于进化结果自动调整大脑配置**

不再保留与当前实现无关的旧 LLM 状态、旧页面清理说明、历史双轨描述。

---

## 1. 代码现状核对

### 1.1 前端 `EnterpriseSettings.tsx`

已确认当前页面主导航包含：

- 公司信息 `info`
- 模型池 `llm`
- 大脑配置 `brain`
- 工具 `tools`
- 技能 `skills`
- 邀请码 `invites`
- 配额 `quotas`
- 用户 `users`
- 组织 `org`
- 审批 `approvals`
- 审计 `audit`

其中：

- `llm` tab 只渲染 `ModelPoolProviders`
- `brain` tab 渲染 `BrainConfig`
- 页面里仍然存在旧的 `llm-models` 查询与 mutation 定义，需要最终清理，但本文件不再记录其历史细节
- 现有结构说明：**模型池 UI 已经切到 `ModelPoolProviders`，大脑配置入口也已经在页面主导航中显式存在**

### 1.2 前端 `ModelPoolProviders.tsx`

当前模型池子组件已经具备以下能力：

- provider 列表
- 模型列表
- 新增模型
- 编辑模型
- 测试 provider 连接
- 删除模型
- provider manifest / fallback provider 配置

这意味着它已经可以作为**新建个人数字助理时的模型来源**，但前提是创建页需要接入模型池 API 来读取可选模型。

### 1.3 前端"新建个人数字助理"页面现状

**已确认**：页面文件为 `AgentCreate.tsx`，通过 `?type=personal` 参数触发个人模式。

- ✅ 创建页有"选择模型"字段（`primary_model_id`）
- ✅ 该字段已从模型池 API 读取模型（`modelPoolApi.models.list()`）
- ✅ 允许从模型池中选择 provider 下的某个模型
- ✅ 模型显示格式为 `displayName + providerId + contextWindow + 推荐标签`

---

## 2. API 对齐情况

### 2.1 已存在的模型池 API

`docker/living-agent-service/docs/references/API_REFERENCE.md` 中已经列出：

- `GET /api/model-pool/providers`
- `POST /api/model-pool/providers`
- `POST /api/model-pool/providers/{id}/test`
- `POST /api/model-pool/providers/{id}/discover`
- `GET /api/model-pool/models`
- `GET /api/model-pool/models/{id}`
- `GET /api/model-pool/models/provider/{providerId}`
- `POST /api/model-pool/models`
- `PUT /api/model-pool/models/{id}`
- `DELETE /api/model-pool/models/{id}`
- `GET /api/model-pool/models/available`（文档中已列出“可用模型列表”能力）

结论：

- **模型池后端能力是齐的**
- 前端创建个人数字助理如果要支持模型选择，应优先复用这些接口

### 2.2 已存在的大脑配置 API

`API_REFERENCE.md` 中已列出：

- `GET /api/brain-models`
- `GET /api/brain-models/{brainId}`
- `PUT /api/brain-models/{brainId}`
- `DELETE /api/brain-models/{brainId}`
- `GET /api/brain-models/available`

结论：

- **主大脑 / 部门大脑的绑定能力后端已经存在**
- 这与模型池是两层不同职责，不能混用

### 2.3 系统进化相关 API

`API_REFERENCE.md` 中也列出了：

- `POST /api/evolution/feedback`
- `GET /api/evolution/feedback/recent`

结论：

- 系统进化侧已经有反馈接口
- 文档里应把“自动配置大脑”描述为：**基于进化反馈驱动大脑模型调整**
- 不能把它写成“模型池自动分配”

---

## 3. 实际业务流程应如何理解

### 3.1 新建个人数字助理

创建个人数字助理时，应支持：

1. 创建基础信息
2. 选择一个可用模型
3. 该可用模型应来自模型池

因此，前端创建页需要对接的不是“大脑配置 API”，而是：

- `model-pool/models`
- 或 `model-pool/models/available`

如果创建页现在还没有模型选择，那就是当前缺口。

### 3.2 大脑配置

大脑配置不是给个人数字助理新建时直接随便选模型，而是：

- 配置主大脑
- 配置部门大脑
- 这些大脑绑定某个模型池中的模型

因此，大脑配置页应对接：

- `brain-models`
- `brain-models/available`

如果页面需要展示可选模型，模型数据应仍然来自模型池，但“绑定关系”应落到 brain-models。

同时要和对话入口逻辑对齐：

- **带 `brain` 的 `/chat?brain=...` 是固定走部门大脑通道**，不是按身份自动分配；
- **无参数 `/chat` 才会按身份进入 enterprise / dept / public 通道**；
- 所以大脑配置页修改的是“默认与固定脑绑定”，不是“聊天时临时切换路由”。

### 3.3 系统自动配置（进化）

系统进化的目标是：

- 通过反馈观察效果
- 自动调整主大脑/部门大脑的模型绑定

因此，系统自动配置应视为：

- 一种基于进化反馈的 brain-models 更新机制
- 不是对模型池本身做修改

---

## 4. 需要补齐或确认的问题

### 4.1 新建个人数字助理页面是否真的接入模型选择

**已确认并已完成**：

- ✅ 存在创建页：`AgentCreate.tsx`
- ✅ 有模型下拉选择：`primary_model_id` 字段
- ✅ 从模型池读取模型：`modelPoolApi.models.list()`
- ✅ 过滤不可用模型：`models.filter((m: any) => m.enabled)`
- ✅ 模型显示格式优化：`displayName + providerId + contextWindow + 推荐标签`

### 4.2 模型池模型的“可用性”定义是否明确

需要确认：

- `available` 是不是仅返回启用模型
- 是否要排除未测试 provider
- 是否要排除禁用模型
- 是否需要按租户过滤

### 4.3 `brain-models` 与 `model-pool` 的职责是否清晰

**已明确**：

- ✅ 模型池 = 模型资源管理（`model-pool/providers`, `model-pool/models`）
- ✅ brain-models = 大脑绑定关系管理（`brain-models`）
- ✅ 前端 `BrainConfig.tsx` 使用 `brainModelApi`（对接 `/api/brain-models/*`）
- ✅ 前端 `ModelPoolProviders.tsx` 使用 `modelPoolApi`（对接 `/api/model-pool/*`）
- ✅ 职责边界清晰，无重复配置入口

### 4.4 自动进化与人工配置的关系

必须明确：

- 自动进化是否覆盖人工配置
- 是否只在特定条件下自动调整
- 是否允许回滚
- 是否保留最近一次手工配置为基线

这些规则如果不明确，前后端都会出现冲突。

---

## 5. 当前后端对接结论

### 模型池侧

- 后端已支持
- 前端 `ModelPoolProviders` 已支持管理
- 需要确认“新建个人数字助理”页面是否复用该模型列表

### 大脑配置侧

- 后端已支持
- 需要明确大脑配置页是否已经存在，或是否由后续页面承接
- 需要明确主大脑与部门大脑的 UI 结构

### 自动进化侧

- 后端已有进化反馈接口
- 但“自动更新 brain-models”的完整闭环仍需要页面和流程设计

---

## 6. 逐步推敲后的流程修改清单

### 6.1 模型池添加 → 测试 → 保存

#### 第一步 添加 provider / 模型

**前端要改的文件**
- `docker/living-agent-service/frontend/src/pages/ModelPoolProviders.tsx`
- `docker/living-agent-service/frontend/src/types/modelPool.ts`

**前端要改的函数 / 状态**
- `handleSubmit`
- `openCreate`
- `openEdit`
- `handleProviderChange`
- `saveProviderMutation`
- `saveModelMutation`

**前端要对接的 API**
- `POST /api/model-pool/providers`
- `POST /api/model-pool/models`
- `PUT /api/model-pool/models/{id}`

**后端对应**
- `ModelPoolController.addProvider`
- `ModelPoolController.addModel`
- `ModelPoolController.updateModel`

**多个模型供应商分析**

1. **硅基流动（SiliconFlow）**
   - 属于云端供应商，应该走 API Key + baseUrl 配置模式；
   - 需要保留在 `providerOptions` 和 `FALLBACK_PROVIDERS` 中；
   - 测试连接、发现模型、保存 provider 都要和其它云端供应商走同一条链路；
   - 适合在 UI 里直接展示为一个独立 provider，而不是“自定义”里的特殊值。

2. **Claude Opus 兼容类**
   - 包含 Anthropic / OpenAI / OpenAI Responses / Azure / DeepSeek / Qwen / MiniMax / OpenRouter / 智谱 / 百度 / Gemini / Kimi 等云端或兼容接口供应商；
   - 这类供应商共性是：`protocol` 需要和后端兼容，`baseUrl` 和 `supportsToolChoice` 要区分开配置；
   - 模型发现和测试连接要优先支持 API Key 驱动；
   - 前端展示上应统一为“云端兼容供应商”，避免各家逻辑分叉过多。

3. **本地部署类**
   - 包含 vLLM / Ollama / SGLang；
   - 默认 `baseUrl` 必须预填，但允许用户手动覆盖；
   - 通常不依赖云端 API Key 的强制配置；
   - 更适合优先做“发现模型”和“测试连接”，因为本地部署更常见于动态模型目录。

4. **自定义类**
   - `custom` 需要保留为通用兜底入口；
   - 必须允许用户手工填写 `baseUrl`、API Key、模型名；
   - 不应强制预填固定协议映射；
   - 适合处理文档外新增供应商、私有网关、兼容代理等场景。

**统一处理要点**
- `providerOptions` 既支持后端 `providers.manifest()`，也支持 `FALLBACK_PROVIDERS`；
- 不同供应商需要区分 `protocol`、`baseUrl`、`supportsToolChoice`、`defaultMaxTokens`；
- 前端当前应覆盖：硅基流动、Claude Opus 兼容类、本地部署类、自定义类，并保证各组在测试、发现、保存上使用一致流程；
- 对于本地或自托管供应商，默认 `baseUrl` 需要预填，用户仍可手动覆盖；
- 对于 `custom`，必须允许用户自己填写 `baseUrl`。

#### 第二步 测试 provider

**前端要改的文件**
- `docker/living-agent-service/frontend/src/pages/ModelPoolProviders.tsx`
- `docker/living-agent-service/frontend/src/services/modelPoolApi.ts`

**前端要改的函数 / 状态**
- `testMutation`
- `testingProvider`
- `setTestResult`

**前端要对接的 API**
- `POST /api/model-pool/providers/{id}/test`

**后端对应**
- `ModelPoolController.testProvider`

**多个供应商的处理要点**
- 测试时用户输入的 `baseUrl` 要优先于预置 `baseUrl`
- `apiKey` 为空时应直接禁用测试按钮
- `testModel` 必须对不同供应商有合理默认值或手动输入要求
- `protocol` 不同会影响后端真实请求能力，前端要保留供应商展示而不要过度抽象成单一模型类型

**当前问题**
- 现在如果 provider 不存在，测试时会先自动创建 provider
- 这会让“测试”带写入副作用

**解决方法**
- 保留当前流程以保证可用，但在 UI 上明确提示“测试时会自动保存 provider”
- 或拆分成“先保存 provider，再测试”两个显式动作
- 若后续要简化体验，可增加独立的“仅测试不保存”能力，但那需要后端额外支持临时参数测试

#### 第三步 发现可用模型

**前端要改的文件**
- `docker/living-agent-service/frontend/src/pages/ModelPoolProviders.tsx`
- `docker/living-agent-service/frontend/src/services/modelPoolApi.ts`

**前端要改的函数 / 状态**
- `discoverModelsMutation`
- `discoveredModels`
- `discovering`
- `showDiscovered`

**前端要对接的 API**
- `POST /api/model-pool/providers/{id}/discover`

**后端对应**
- `ModelPoolController.discoverModels`

**多个供应商的处理要点**
- 发现模型时不同供应商返回的候选模型格式可能不同，前端需要统一成字符串列表或统一实体结构
- 对本地供应商（vLLM、Ollama、SGLang）优先展示可发现模型，不要只靠手工输入
- 对云端供应商（OpenAI、Anthropic、DeepSeek、Qwen、Gemini、Kimi、智谱、MiniMax、百度、OpenRouter）要允许基于 API Key 自动发现

**当前问题**
- `discover` 接口存在，但 UI 入口还不够明确

**解决方法**
- 在模型名称输入旁边保留“发现模型”按钮
- 发现结果展示成可点击列表，点选后自动回填 `modelName` 和 `displayName`

#### 第四步 保存后刷新列表

**前端要改的文件**
- `docker/living-agent-service/frontend/src/pages/ModelPoolProviders.tsx`

**前端要改的函数 / 状态**
- `saveModelMutation.onSuccess`
- `deleteModelMutation.onSuccess`
- `queryClient.invalidateQueries`

**前端要对接的 API**
- `GET /api/model-pool/models`
- `GET /api/model-pool/providers`

**后端对应**
- `ModelPoolController.getAllModels`
- `ModelPoolController.getAllProviders`

**多个供应商的处理要点**
- 保存后要能立即看到对应 provider 下的模型
- provider/baseUrl 变更后，模型列表不能卡在旧缓存
- 模型名、显示名、上下文长度、输出长度、视觉/推理能力都要从后端返回的真实值刷新

**当前问题**
- 删除成功后只刷新了模型列表，provider 统计和展示也需要确认是否一起更新

**解决方法**
- 保持模型和 provider 两个 query 都失效刷新
- 如果模型统计显示要依赖 provider 数量，则在保存/删除后一起更新

### 6.2 新建个人数字助理模型选择

#### 文件任务清单

##### `个人数字助理创建页对应文件`

**要加什么**
- 模型选择下拉或选择卡片
- 从模型池拉取可用模型的 `useQuery`
- 模型的多字段展示：`displayName`、`provider`、`contextWindow`、`maxOutputTokens`、`supportsVision`、`supportsReasoning`
- 保存时提交 `modelId`

**要删什么**
- 只展示短模型名的旧选择方式
- 直接填写 provider 细节的冗余输入
- 不依赖模型池的本地静态模型列表

**要改什么函数**
- 创建表单初始化逻辑
- 模型选择变更逻辑
- 创建保存提交逻辑
- 模型下拉的搜索 / 过滤 / 展示逻辑

**对应后端 API**
- `GET /api/model-pool/models/available`
- `GET /api/model-pool/models`
- 创建个人数字助理的保存 API（按实际页面对齐）

##### `个人数字助理创建页复用的表单组件`

**要加什么**
- 如果创建页复用了表单组件，则在该组件内补模型选择能力
- 多供应商展示信息
- 同名模型区分来源的展示文案

**要删什么**
- 只显示模型短名的旧渲染
- 任何和 provider 管理强绑定的输入项

**要改什么函数**
- 表单组件的模型选项渲染函数
- value / onChange 绑定逻辑
- 搜索或筛选函数

**对应后端 API**
- `GET /api/model-pool/models/available`
- `GET /api/model-pool/models`

##### `创建弹窗 / 创建抽屉组件`（如果存在）

**要加什么**
- 模型选择入口
- 更宽的候选项展示
- 当前选中模型摘要

**要删什么**
- 只显示单行模型名的旧 UI
- 重复的 provider 配置入口

**要改什么函数**
- 弹窗打开时的默认模型加载
- 选择模型后的回填逻辑
- 提交时的 payload 组装逻辑

**对应后端 API**
- `GET /api/model-pool/models/available`
- `GET /api/model-pool/models`
- 创建个人数字助理的保存 API（按实际页面对齐）

#### 统一说明

**多个供应商的处理要点**
- 前端展示必须区分供应商，不要只显示模型名；
- 对本地/自托管模型，建议优先显示 `baseUrl` 或“本地部署”标识；
- 对云端供应商，建议显示 provider 名称与能力标签；
- 若模型池里存在同名模型，创建页必须能区分来源，否则用户会选错。

**当前问题**
- 还没定位到具体“创建个人数字助理”的页面文件，所以没法直接点到具体函数名；
- 但从流程上看，当前缺口就是创建表单没把模型池接进去，或者接进去后展示信息不足；
- 如果创建页是多个地方共用，可能需要抽一个统一的模型选择组件。

**解决方法**
- 先定位实际创建页；
- 在创建页增加模型池可选模型查询；
- 用更宽的选择 UI 展示模型详情；
- 保存时只提交 modelId，不直接提交 provider 的配置细节；
- 后端再按 modelId 反查 provider 和模型详情。

### 6.3 大脑配置（主大脑 / 部门大脑）

#### 第一步：页面入口与基础展示

**前端要改的文件**
- `docker/living-agent-service/frontend/src/pages/BrainConfig.tsx`
- `docker/living-agent-service/frontend/src/pages/EnterpriseSettings.tsx`

**前端要改的函数 / 状态**
- `expandedBrain`
- `getAssignmentForBrain`
- `getProviderName`
- brain 标签页的入口与跳转

**前端要对接的 API**
- `GET /api/brain-models`
- `GET /api/brain-models/{brainId}`
- `GET /api/brain-models/available`

**后端对应**
- `BrainModelController.getAllAssignments`
- `BrainModelController.getAssignment`
- `BrainModelController.getAvailableModels`

**多个供应商的处理要点**
- 大脑配置页展示的是“模型池中的可选模型”，所以模型名称必须带 provider 信息，避免同名模型混淆；
- 主大脑、技术大脑、行政大脑、人力大脑、财务大脑、销售大脑、客服大脑、运营大脑、法务大脑等 brainId 要统一命名和显示；
- 页面默认只展开一个脑，降低误操作概率。

**当前问题**
- 当前 `BrainConfig.tsx` 已能展示脑卡片，但入口仍是“配置展示”，不是“完整的主大脑/部门大脑配置工作台”；
- 组件里直接使用了静态 brainId 映射，后续如果后端 brain 列表变化，需要同步维护。

**解决方法**
- 保留 `BrainConfig.tsx` 作为大脑配置核心页；
- `EnterpriseSettings.tsx` 的 `brain` tab 只负责承载和跳转；
- 如果未来 brainId 需要后端动态下发，再把静态映射抽成常量或配置接口。

#### 第二步：绑定 / 清除关系

**前端要改的文件**
- `docker/living-agent-service/frontend/src/pages/BrainConfig.tsx`
- `docker/living-agent-service/frontend/src/services/modelPoolApi.ts`
- `docker/living-agent-service/frontend/src/types/modelPool.ts`

**前端要改的函数 / 状态**
- `assignMutation`
- `clearMutation`
- `onChange` 里的模型切换逻辑

**前端要对接的 API**
- `PUT /api/brain-models/{brainId}`
- `DELETE /api/brain-models/{brainId}`

**后端对应**
- `BrainModelController.assignModel`
- `BrainModelController.clearAssignment`

**多个供应商的处理要点**
- 下拉框里显示的不是纯 modelName，而是 `displayName + provider`；
- 如果两个供应商下存在同名模型，必须能看出来源；
- 清除绑定时要回到默认模型，不要把脑配置留成空白状态。

**当前问题**
- 当前页面直接把“选择模型”与“保存绑定”合在一起，逻辑是通的，但失败提示不够细；
- 绑定成功后只刷新 assignments，没有显式刷新可用模型列表。

**解决方法**
- 成功后统一刷新 assignments；
- 绑定失败时在页面中明确展示错误，不要只依赖浏览器控制台；
- 若后端以后支持脑级别的默认模型切换，再扩展到更清晰的“保存/恢复默认”操作。

#### 第三步：自动进化与手工配置

**前端要改的文件**
- 大脑配置页
- 进化反馈展示区

**前端要改的函数 / 状态**
- 自动配置开关
- 最近一次调整记录展示
- 手工恢复入口

**前端要对接的 API**
- `POST /api/evolution/feedback`
- `GET /api/evolution/feedback/recent`
- `PUT /api/brain-models/{brainId}`

**后端对应**
- `EvolutionAdminController.feedback`
- `EvolutionAdminController.getRecentFeedback`
- `BrainModelController.assignModel`

**多个供应商的处理要点**
- 自动进化可能在多个供应商之间切换模型，但页面必须保留 provider 语义；
- 切换时不能只记 modelName，要保留 provider / modelId；
- 若同一脑在不同供应商间切换，历史记录必须可追踪。

**当前问题**
- 自动进化是否覆盖人工配置、如何回滚，当前还未完全明确；
- 进化结果与脑配置的展示还没有在前端形成闭环。

**解决方法**
- 明确自动进化只写回 brain-models，不改模型池；
- 人工配置作为默认基线，自动进化为条件触发；
- 页面上保留最近一次手工配置与自动调整记录，便于回滚；
- 如果后续需要更多脑类型，再把 brainId 列表做成后端可配置。

**统一结论**
- 大脑配置页展示的是模型池中的可选模型，但绑定关系必须落到 brain-models，而不是 model-pool；
- `model-pool` 只提供可选模型，不负责绑定。

### 6.4 系统进化自动配置

**前端要改的文件**
- 大脑配置页
- 进化结果展示页或配置区

**前端要改的函数 / 状态**
- 自动配置开关
- 进化状态展示
- 最近一次调整记录

**前端要对接的 API**
- `POST /api/evolution/feedback`
- `GET /api/evolution/feedback/recent`
- `PUT /api/brain-models/{brainId}`（自动进化最终还是要写回 brain-models）

**后端对应**
- `EvolutionAdminController.feedback`
- `EvolutionAdminController.getRecentFeedback`
- `BrainModelController.assignModel`

**多个供应商的处理要点**
- 自动进化可能会在多个供应商之间切换模型
- 切换时要保留供应商信息，避免只存 modelName 丢失 provider 语义
- 本地模型与云端模型混用时，要保证显示和保存都统一

**当前问题**
- 自动进化是否覆盖人工配置，当前未完全明确

**解决方法**
- 自动进化只更新 brain-models
- 人工配置作为基线
- 页面上保留“手工配置”和“自动调整记录”
- 允许回滚到最近一次人工配置

### 6.5 部门对话依赖部门大脑配置

**前端要改的文件**
- 部门配置页、组织页、部门对话入口

**前端要改的函数 / 状态**
- 部门 brain 绑定展示
- 部门聊天入口的 fallback 提示

**前端要对接的 API**
- `POST /api/dept/{department}/chat`
- 如需展示部门绑定状态，则复用 `GET /api/brain-models`

**后端对应**
- `DepartmentApiController.chat`
- `BrainModelController.getAssignment`

**多个供应商的处理要点**
- 部门对话使用的模型可能来自不同供应商，显示时要保留 provider 信息
- 如果部门脑未配置，建议 fallback 到主大脑

**当前问题**
- 前端是否已经把部门大脑配置和部门对话建立起明确依赖关系，还需确认

**解决方法**
- 在文档中明确“部门对话依赖部门大脑绑定”
- 未配置时 fallback 到主大脑或默认模型
- 在组织页展示当前部门绑定状态

### 6.6 对话入口逻辑对齐说明

> 这部分是对 `对话入口逻辑梳理.md` 的补充校准，用来避免把硬路由和软路由混用。

**前端要改的文件**
- `docker/living-agent-service/frontend/src/pages/Chat.tsx`
- `docker/living-agent-service/frontend/src/pages/DepartmentDetail.tsx`
- `docker/living-agent-service/frontend/src/pages/AgentDetail.tsx`

**前端要改的函数 / 状态**
- `Chat.tsx` 中对 URL 参数的解析顺序
- `/chat?id=...` 的固定 agent 分支
- `/chat?brain=...` 的固定部门脑分支
- `/chat` 无参数时的身份软路由分支
- 部门页按钮显隐逻辑

**前端要对接的 API**
- `POST /api/dept/{department}/chat`
- `/ws/agent` 对应 agent 直连
- `/ws/dept/{brain}` 对应部门大脑直连
- `/ws/enterprise`、`/ws/public` 对应无参数 `/chat` 的软路由

**后端对应**
- `AgentWebSocketHandler`
- `DepartmentWebSocketHandler`
- `WebSocketConfig`

**多个供应商的处理要点**
- 这里的供应商不是模型供应商，而是“入口路由供应商”——即不同 URL 参数走不同 WebSocket 通道；
- `id` 与 `brain` 都是硬路由，不受身份影响；
- 只有无参数 `/chat` 才允许根据身份 fallback 到 enterprise / dept / public。

**当前问题**
- 文档里之前写过“部门对话依赖部门大脑绑定”的结论，但如果不注明硬路由优先级，容易误以为所有聊天都能按部门脑兜底；
- 这会和 `对话入口逻辑梳理.md` 冲突。

**解决方法**
- 把 `Chat.tsx` 的入口判定顺序固定为：`id` > `brain` > 身份软路由；
- `DepartmentDetail.tsx` 中的部门大脑按钮只负责跳到 `brain` 硬路由；
- `AgentDetail.tsx` 里如果存在直连入口，必须保持 `id` 固定直连。

---

## 7. 后端改造清单

### 7.1 自动调整 `brain-models`

| 文件名 | 方法名 | 要补什么 | 对应 API |
|---|---|---|---|
| `docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EvolutionAdminController.java` | `feedback` / 最近反馈入口 / 自动调整控制入口 | 反馈接收后的异步触发或入队、最近反馈聚合触发、手工触发自动调整、回滚/历史查询入口 | `POST /api/evolution/feedback`、`GET /api/evolution/feedback/recent`、`PUT /api/brain-models/{brainId}`、`DELETE /api/brain-models/{brainId}` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/evolution/engine/EvolutionOrchestrator.java` | 决策主流程 / 反馈评分 / 候选选择 / 回滚入口 | 反馈评分、策略选择、候选模型排序、brainId 定向更新、周期性或事件驱动执行入口 | `POST /api/evolution/feedback`、`GET /api/evolution/feedback/recent`、`PUT /api/brain-models/{brainId}` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/evolution/executor/EvolutionFeedbackService.java` | 反馈归一化 / 统计聚合 | 反馈归一化、分数计算、历史反馈聚合 | `POST /api/evolution/feedback`、`GET /api/evolution/feedback/recent` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/model/selector/BrainModelSelectorManager.java` | selector 注册 / 分发 | 选择器注册、按 brainType / department 选主大脑与部门大脑的派发 | `GET /api/brain-models/available`、`GET /api/brain-models/{brainId}` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/model/selector/BrainModelSelector.java` | `selectBestModel(...)` / `score(...)` / `supports(...)` | 统一模型选择契约、打分、候选过滤 | `GET /api/brain-models/available`、`PUT /api/brain-models/{brainId}` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/model/selector/BrainModelSelectorRegistrar.java` | 启动注册 / 缓存刷新 | 启动时注册 selector、刷新 selector 缓存 | 无直接 API；支撑 `GET /api/brain-models/available` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/evolution/scheduler/` | 定时任务 / 重试 / 持久化 | 定时任务执行、失败重试、结果持久化、回滚任务 | `POST /api/evolution/feedback`、`PUT /api/brain-models/{brainId}`、`DELETE /api/brain-models/{brainId}` |
| `docker/living-agent-service/living-agent-core/src/main/resources/db/migration/` | 表结构迁移 | 自动调整记录表、调度状态表、回滚历史表 | 支撑 `GET /api/brain-models`、`GET /api/evolution/feedback/recent` |

### 7.2 部门对话 / 信息闭环

| 文件名 | 方法名 | 要补什么 | 对应 API |
|---|---|---|---|
| `docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentApiController.java` | `chat` / `info` / `members` / `brains` / `my` | 真实 chat 调用、部门成员查询、部门脑列表查询、当前部门信息查询、fallback 策略统一 | `POST /api/dept/{department}/chat`、`GET /api/dept/{department}/info`、`GET /api/dept/{department}/members`、`GET /api/dept/{department}/brains`、`GET /api/dept/my` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/BrainRegistry.java` | `register` / `unregister` / `getByDepartment` / `get` / `getAll` | 脑注册注销、brain -> department 反查、默认 brain fallback、brain 与 department 映射同步 | `GET /api/dept/{department}/brains`、`GET /api/dept/{department}/info`、`GET /api/brain-models/{brainId}` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/` 下对应部门脑实现类 | 推理入口 / 上下文拼装 / 结果输出 | 输入归一化、业务上下文拼装、推理调用、部门场景适配 | `POST /api/dept/{department}/chat`、`/ws/dept/{brain}` |
| `docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/prompt/` | prompt 构建方法 | 部门上下文 prompt 构建、角色/权限上下文注入 | `POST /api/dept/{department}/chat` |
| `docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | 消息转发 / ACL / 审计 | 按部门 brain 路由的真实消息转发、错误回传、流式消息处理、ACL 校验、审计 | `/ws/dept/{brain}` |
| `docker/living-agent-service/living-agent-core/src/main/resources/db/migration/` | 表结构迁移 | 部门、成员、brain 绑定、对话记录等表结构 | `GET /api/dept/{department}/info`、`GET /api/dept/{department}/members`、`GET /api/dept/{department}/brains`、`GET /api/dept/my` |

---

## 8. 必须推进的待办

1. 先补自动调整 `brain-models` 的策略层与调度层；
2. 再把 `DepartmentApiController.chat` 从示意响应升级为真实部门对话闭环；
3. 补齐 `members` / `brains` / `info` 的真实组织数据；
4. 继续保持 `Chat.tsx` 的入口逻辑与 `对话入口逻辑梳理.md` 一致；
5. 后端文档只保留文件名 / 方法名 / 要补什么 / 对应 API，避免重复描述。

---

## 9. 结论

当前文档已收敛为纯后端改造清单，后端部分可以按下表直接推进：

### 已完成
- ✅ 反馈 API 与模型池基础能力已存在；
- ✅ 部门对话相关 API 路由骨架已存在；
- ✅ 大脑配置前端入口已存在。

### 半实现
- 🟡 自动进化还没有形成完整的 brain-models 自动更新闭环；
- 🟡 `DepartmentApiController` 目前能通路由与权限，但业务数据和对话实现仍偏示意。

### 未实现
- ❌ 自动更新 brain-models 的决策器/调度器；
- ❌ 部门成员/部门大脑/部门信息的真实数据闭环；
- ❌ 部门对话真正接入后端推理链路。

本文件后续只保留这份后端改造清单，不再扩展其它说明。
