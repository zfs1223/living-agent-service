# 框架歧义点、代码现状、完整建议与修补方向

> 本文档整合并替代对 `docs/old/` 核心设计文件的逐份分析结果，目标不是重画架构，而是基于旧版框架与当前代码实现，系统性澄清歧义、确认现状，并为后续按框架修补代码提供依据。
>
> 涵盖文档：
> - `01-system-overview.md`
> - `02-core-architecture.md`
> - `03-employee-model.md`
> - `04-knowledge-system.md`
> - `05-evolution-system.md`
> - `06-security-permission.md`
> - `07-deployment-operations.md`
>
> 重要补充资料来源：
> - `documents/_meta/`（命名、访问规则、分类口径）
> - `documents/shared/governance/`（企业治理与 HR 制度）
> - `documents/shared/company/`（董事长需求、固定员工职责卡、自动路由与澄清规则）
> - `documents/department/**/README.md` 与部门制度文件
>
> 这些补充资料不改变旧框架本体，但会用于进一步澄清“流程如何落地”“职责如何衔接”“入口如何路由”。

## 1. 总原则

### 1.1 本体定位

`living-agent-service` 的本体是**企业管理生命体**。它不是文档库、任务库、审批库或单一工作流系统，而是一个以员工、脑、神经元、通道、知识、进化、安全、运维协同运转的组织系统。

### 1.2 分析原则

- 以旧框架为准，不重新定义系统本体
- 以真实代码为准，不做概念脑补
- 每份文档都拆成：框架定义、代码现状、歧义点、完整建议
- 只澄清流程与边界，不改造为新的体系
- 后续代码修补必须严格符合旧框架主线
- 结合 `documents/` 中的补充治理、职责、流程与路由规则，对歧义点做更精确的落地修正

---

## 2. 旧框架总览：七份核心文档逐份分析

---

# 2.1 `01-system-overview.md`

## 框架定义

系统总览给出的定位非常明确：

- 这是一个“生命智能体自治系统”
- 核心理念是神经元群聊模式、仿脑神经中枢架构
- 系统具备感知、决策、执行、学习、进化、赚钱能力
- 系统目标不是单纯运行，而是通过自我进化和价值输出形成经济独立能力

## 代码现状

代码中已经出现与总览高度一致的模块与能力：

- 感知侧：ASR、TTS、视觉相关组件与配置
- 员工侧：HumanEmployee、DigitalEmployee、统一 Employee 接口
- 知识侧：KnowledgeManager、KnowledgeEntry、三层知识结构
- 进化侧：EvolutionDecisionEngine、EvolutionExecutor
- 运营侧：绩效、成本核算、CEO 仪表盘、主动预判的相关模块线索
- 运维侧：健康监控、心跳、诊断、告警相关结构

## 歧义点

1. “感知、执行、学习、进化、赚钱”到底是平级能力，还是分层功能？
2. “赚钱能力”是对外接单，还是包含内部成本优化、价值交付与核算闭环？
3. 仪表盘是展示层，还是经营决策层？
4. 记忆、知识、文档三者如何区分？
5. 技术栈如何服务于企业生命体，而不是技术堆砌？

## 完整建议

- 明确“感知”是输入层，“执行”是行动层，“学习”是沉淀层，“进化”是优化层，“赚钱”是价值输出层
- 明确赚钱能力包含：对外任务收益、对内成本优化、项目核算、经营反馈
- 仪表盘应定义为经营辅助与风险反馈中心，而不只是看板
- 记忆是运行态上下文，知识是可复用语义，文档是原始输入
- 技术栈按功能层映射：Java 编排、Rust 性能能力、Python 模型脚本、数据库/缓存/向量库提供支撑

### 结合补充资料后的澄清

- `documents/_meta/taxonomy.md` 和 `documents/_meta/access-rules.md` 应作为系统总览的治理前提：文档系统不是总控，只是输入与治理对象之一。
- `documents/shared/governance/00-index.md` 与 `documents/shared/company/00-index.md` 已将董事长/数字员工/部门治理规则显式化，这意味着“赚钱与经营反馈”不能脱离组织治理与权限边界单独理解。

---

# 2.2 `02-core-architecture.md`

## 框架定义

核心架构定义了企业生命体的分层体系：

- 感知层
- 神经元层
- 技能层
- 企业系统层

并定义了三层 LLM 架构：

- 主大脑 `MainBrain`
- 闲聊神经元 `Qwen3Neuron`
- 工具神经元 `ToolNeuron`

同时定义了多个业务大脑：HR、Finance、Tech、Ops、Sales、Legal、CS、Admin、MainBrain。

## 代码现状

- 模块分层已经存在：`core`、`gateway`、`native`、`skill`、`perception`、`app`
- 业务大脑、神经元、技能、通道、工具、模型管理等都有实现线索
- `BrainContext`、`ChannelManager`、`ModelManager`、`SkillRegistry` 等组件已经形成架构基础

## 歧义点

1. 员工、神经元、大脑、通道之间的边界还不够硬
2. 主脑与部门脑的接管条件不够统一
3. 神经元是执行单元、感知单元还是路由单元，需要固定
4. 通道到底是消息通道、群聊通道，还是企业协作总线，需要明确
5. 技能层和工具层的职责边界需要固定

## 完整建议

- `Employee`：组织身份与权限主体
- `Brain`：领域推理与协调主体
- `Neuron`：感知 / 执行 / 路由单元
- `Channel`：事件与消息传输层
- `MainBrain` 仅负责跨部门协调、复杂推理、冲突裁定
- 部门脑只处理本领域闭环任务
- 技能层负责可复用行为，工具层负责真实动作执行
- 通道只是传输，不做业务判断

### 结合补充资料后的澄清

- `documents/shared/company/fixed-employee-agent-prompt.md` 与职责卡系列说明，已经把“岗位—职责—协作对象—审批边界”做成了治理实体，因此 `02-core-architecture.md` 里对 `Brain/Neuron/Employee/Channel` 的定义应与这些治理文档一致，而不是另起一个“文档处理层”概念。
- `documents/department/**/README.md` 中的部门目录约定，说明部门脑的落地必须和部门知识与部门治理同步，而不是单独运行。

---

# 2.3 `03-employee-model.md`

## 框架定义

统一员工模型的核心是：

- 人类员工与数字员工共用同一个抽象模型
- 差异只在于身份来源、信息传递方式、自治方式
- 员工分为编制（Definition）与实例（Instance）
- 数字员工以 `employee://digital/{domain}/{name}/{instance}` 命名
- 神经元以 `neuron://{domain}/{name}/{instance}` 命名

## 代码现状

- `Employee` 接口已存在
- `HumanEmployee`、`DigitalEmployee` 已存在
- `EmployeePersonality`、`EmployeeStatus`、`HumanConfig`、`DigitalConfig` 已存在
- `AccessLevel`、`UserIdentity`、认证与权限服务已存在
- 员工的任务统计、成功率、工作时间等特征也已出现

## 歧义点

1. 编制与实例的关系在代码层是否被严格约束？
2. 员工与神经元之间是否存在一一映射、还是多对一？
3. 人类员工与数字员工的权限/能力差异如何统一表达？
4. `AccessLevel` 是否就是员工能力边界的唯一闸门？
5. 实例扩容、回收、休眠与学习状态如何统一管理？

## 完整建议

- 编制负责定义岗位能力边界、工具清单、人格模板、通道订阅
- 实例负责具体执行，必须服从编制约束
- 员工身份是组织主体，神经元是执行投影，不能混用
- 人类员工与数字员工应共享统一权限与能力模型，但自治方式不同
- 数字员工实例必须支持：激活、休眠、学习、恢复、销毁等状态

### 结合补充资料后的澄清

- `documents/shared/company/hr-16-digital-employee-strategy.md`、`hr-17-digital-employee-performance.md`、`hr-15-founder-enterprise-system.md` 已把董事长、数字员工编制、绩效与授权边界写得更清楚，因此 `03-employee-model.md` 中“编制 vs 实例”的定义建议同步强调“治理授权”与“岗位职责卡”这两个现实约束。
- `documents/shared/company/fixed-employee-duty-card-template.md` 可作为编制定义的实际模板参考。

---

# 2.4 `04-knowledge-system.md`

## 框架定义

知识体系是三层结构：

- L1：神经元私有知识
- L2：大脑领域知识
- L3：共享知识库

知识晋升路径是：

- L1 → L2
- L2 → L3

知识来源包括对话、文档、工具使用、用户反馈。

## 代码现状

- `KnowledgeManager` 已经有较完整接口
- `KnowledgeLayer.PRIVATE / DOMAIN / SHARED` 已存在
- `KnowledgeScope.L1_PRIVATE / L2_DEPARTMENT / L3_SHARED` 已存在
- `KnowledgeEntry` 已包含 scope、brainDomain、neuronId、metadata、vector、relevance、source 等字段
- `Experience`、`BestPractice` 已存在
- `promoteToDomain`、`promoteToShared`、`shareKnowledge`、`moveToLayer`、`addExperience` 等能力已存在

## 歧义点

1. L1/L2/L3 与 PRIVATE/DOMAIN/SHARED 的映射需要固定
2. “晋升”与“复制”是否是同一个动作？不能混淆
3. `Experience`、`BestPractice`、`KnowledgeEntry` 三者关系需要统一
4. 领域知识与部门知识是否完全同义，还是一个映射层级？
5. 知识晋升的门槛、验证方式、回滚机制需要明确

## 完整建议

- 固定映射：PRIVATE = L1、DOMAIN = L2、SHARED = L3
- 明确“晋升”必须经过验证，不等于复制
- `Experience` 偏运行经历，`BestPractice` 偏可复用实践，`KnowledgeEntry` 是统一承载体
- 知识晋升条件建议固定为：使用次数、有效性评分、跨部门引用、验证通过
- 知识检索与写入必须尊重 scope、brainDomain、neuronId

### 结合补充资料后的澄清

- `documents/shared/company/fixed-employee-document-workflow.md` 与 `documents/shared/company/requirement-routing-state-machine.md` 说明：文档入口只是知识来源之一，不能把文档系统等同于知识系统。
- `documents/department/**` 下的部门目录与 `README.md`，可作为 L2 领域知识沉淀的落点，但晋升仍必须经过验证和治理规则，而不是简单目录移动。

---

# 2.5 `05-evolution-system.md`

## 框架定义

进化系统是生命体自我修复、自我优化、自我创新的闭环。核心流程是：

- 信号采集
- 决策
- 执行
- 验证
- 沉淀
- 再进化

进化信号包括：
- ERROR
- OPPORTUNITY
- STABILITY
- DRIFT
- CAPABILITY_GAP
- PERFORMANCE
- USER_REQUEST
- SYSTEM_EVENT

策略包括：
- REPAIR
- OPTIMIZE
- INNOVATE
- DEFER
- ESCALATE

## 代码现状

- `EvolutionDecisionEngine` 已有决策框架
- `EvolutionExecutor` 已有执行框架
- `EvolutionDecision`、`EvolutionStrategy`、`EvolutionConstraints` 已存在
- 进化结果可以记录到知识库与记忆图
- 技能生成、技能安装、技能注册已有联动迹象

## 歧义点

1. 进化信号如何映射到策略，需要统一
2. 进化是否必须经过门禁与审批，需要明确
3. `DEFER` 与 `ESCALATE` 的边界需要清晰
4. 进化执行结果回写到哪里，需要固定
5. 熔断、回滚、失败重试如何统一管理

## 完整建议

- 建立固定映射：ERROR→REPAIR、OPPORTUNITY→OPTIMIZE、CAPABILITY_GAP→INNOVATE、STABILITY→DEFER，高风险/低置信度→ESCALATE
- 把 `EvolutionConstraints` 作为进化门禁统一入口
- 进化结果必须回写：技能、知识、审计、熔断统计、能力图谱
- 创新行为必须默认保守，必须带审批/回滚策略
- 进化闭环必须可追踪、可熔断、可回滚

### 结合补充资料后的澄清

- `documents/shared/company/hr-17-digital-employee-performance.md` 与 `hr-16-digital-employee-strategy.md` 已把绩效、能力演进、组织层授权描述更具体，因此 `05-evolution-system.md` 建议在“进化”定义中明确它不仅是技能自增，还包含岗位能力、组织协作和治理规则的优化。
- `documents/shared/company/fixed-employee-autonomous-runbook.md` 提供了自动工作流运行边界，可作为进化执行门禁的治理参考。

---

# 2.6 `06-security-permission.md`

## 框架定义

安全与权限体系的核心原则是：

- 权限检查必须先于路由
- 权限分为 `CHAT_ONLY / LIMITED / DEPARTMENT / FULL`
- 资源隔离包括模型、大脑、工具、知识、通道、API、沙箱、审计
- 身份识别优先级：OAuth → 声纹 → 手机号 → 人脸

## 代码现状

- `AccessLevel` 已有完整资源视图的雏形
- `PermissionServiceImpl` 已实现认证、访问检查、审计记录、模型/脑/工具访问判断
- `EmployeeAuthService`、OAuth/声纹/手机号验证入口已存在
- 审计日志结构已有基础

## 歧义点

1. `AccessLevel` 到底是“能看什么”的总闸门，还是局部规则？
2. 权限检查是否统一前置到所有入口？
3. 部门 API、Admin API、Public API 的分类是否完全统一？
4. WebSocket 通道权限是否与访问级别、部门归属严格绑定？
5. 沙箱与工具执行的边界是否已统一？
6. 审计日志字段是否全系统一致？

## 完整建议

- `AccessLevel` 应作为资源总闸门，统一管理模型、脑、知识、工具、通道、管理资源
- 所有入口必须先身份识别，再权限判断，再路由执行
- 明确 Public / Department / Admin 三类 API
- WebSocket 频道必须严格绑定部门或 FULL 权限
- 高风险工具必须走沙箱与白名单
- 审计字段统一：操作者、资源、动作、结果、原因、入口、关联任务/需求 ID

### 结合补充资料后的澄清

- `documents/_meta/access-rules.md` 明确了文档访问与角色权限，因此 `06-security-permission.md` 可借鉴文档治理中的访问规则，但不能把文档访问规则直接等同于企业资源访问规则。
- `documents/shared/company/requirement-clarification-rules.md` 与自动路由状态机说明，高风险任务和不规范需求应在权限与审批链上被提前拦截，不应下沉到执行层再处理。

---

# 2.7 `07-deployment-operations.md`

## 框架定义

部署运维不仅是环境启动，更是企业生命体的基础设施层。其职责包括：

- 环境依赖管理
- 应用服务启动与模块分层
- 健康检查
- 监控告警
- 备份恢复
- 运营评判
- 主动预判
- 仪表盘与通知

## 代码现状

- `application.yml` 已配置数据库、Redis、Kafka、模型服务等
- 核心模块已分层：`app` / `gateway` / `core` / `native` / `skill` / `perception`
- 有健康监控、绩效、仪表盘、主动预判、成本核算等线索
- 但部分页面/服务还是空壳或未完全实现

## 歧义点

1. 哪些依赖是核心必需，哪些只是增强能力？
2. 健康检查的维度如何定义？
3. 业务指标如何统一映射到监控指标？
4. 备份恢复和任务/知识/审计状态如何同步？
5. 仪表盘是展示层还是经营决策层？
6. 主动预判如何与事件、任务、告警联动？

## 完整建议

- 区分核心运行必需与增强能力
- 健康检查分三层：基础设施、应用健康、业务健康
- 监控分四层：基础设施、应用、业务、AI 指标
- 备份恢复必须考虑任务状态、消息重复、知识索引同步
- 仪表盘应定义为经营反馈与决策辅助中心
- 主动预判应作为任务与事件的生成器，而不是仅通知器

### 结合补充资料后的澄清

- `documents/shared/company/fixed-employee-autonomous-runbook.md` 已将自动工作流边界、失败处理和运行节奏描述清楚，可作为运维体系中“业务自动化运行”的参考。
- `documents/shared/company/00-index.md` 与治理文档说明：运维不是孤立系统，而是企业生命体运行的基础设施与经营反馈层。

---

## 3. 结合 `documents/` 补充资料后的总判断

`documents/` 里的补充资料并不改变 `docs/old/` 的本体框架，但它们提供了非常关键的治理落点：

- `documents/_meta/` 提供命名、访问和分类口径
- `documents/shared/governance/` 提供企业治理与 HR 规则
- `documents/shared/company/` 提供董事长需求、固定员工职责、自动路由、澄清与运行手册
- `documents/department/**` 提供各部门目录与职责落点

这意味着旧框架中原本偏抽象的“员工、部门、知识、进化、安全、运维”现在已经有了现实落地语境，因此后续修补 `docs/old/` 时，可以把这些补充资料作为“如何落地”的依据，而不是另起一套规则。

---

## 4. 总体合并结论

对七份旧文档进行完整对照后，可以得出一个一致判断：

### 4.1 代码与框架总体一致

项目核心骨架已经存在，尤其在以下方面：
- 员工模型
- 知识层级
- 进化决策与执行
- 权限与审计
- 模块分层
- 部署配置

### 4.2 真正的问题是“流程闭环未收口”

不是功能太少，而是：
- 边界不统一
- 状态机不统一
- 门禁不统一
- 回写不统一
- 预判/监控/恢复不统一

### 4.3 最需要补齐的完整链路

1. 员工 / 脑 / 神经元 / 通道职责边界
2. 权限检查前置与路由统一
3. 知识 / 记忆 / 文档边界统一
4. 任务 / 需求 / 审批状态机统一
5. 知识晋升与进化门禁统一
6. 进化回写与熔断统一
7. 健康检查 / 监控 / 恢复 / 看板统一

---

## 5. 建议修补 `docs/old/` 的方向

根据实际代码情况，建议优先修补旧文档中的以下澄清点，而不是重写框架：

### 5.1 在 `02-core-architecture.md` 中补充
- 员工 / 脑 / 神经元 / 通道的职责边界说明
- 主脑接管条件与部门脑处理条件
- 技能层与工具层的边界

### 5.2 在 `03-employee-model.md` 中补充
- 编制 vs 实例的运行规则
- 员工与神经元的映射关系
- `AccessLevel` 与员工能力边界关系

### 5.3 在 `04-knowledge-system.md` 中补充
- L1/L2/L3 与 PRIVATE/DOMAIN/SHARED 的固定映射
- 晋升与复制的区别
- `Experience` / `BestPractice` / `KnowledgeEntry` 的关系

### 5.4 在 `05-evolution-system.md` 中补充
- 信号与策略的固定映射
- 门禁与审批条件
- 熔断与回滚规则
- 回写目标

### 5.5 在 `06-security-permission.md` 中补充
- 权限前置于路由的实现要求
- Public / Department / Admin API 的分类
- WebSocket 频道权限绑定
- 审计字段统一

### 5.6 在 `07-deployment-operations.md` 中补充
- 健康检查分层
- 监控指标分层
- 主动预判与业务事件联动
- 备份恢复与状态同步
- CEO / 董事长仪表盘的数据来源边界

---

## 6. 结论

`living-agent-service` 不是文档库，而是一个企业管理生命体。当前代码已经具备较强骨架，但要真正“活”，必须把七份旧框架中的歧义点逐项澄清，并按框架修补代码，尤其要收口以下主链路：

- 权限前置
- 状态统一
- 边界统一
- 进化门禁统一
- 运维闭环统一

只有这样，系统才能从“能力集合”真正变成“可运行的企业生命体”。
