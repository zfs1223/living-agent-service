# 企业治理文档索引

> 本文档为企业生命智能体治理体系的总索引，**严格对齐项目核心框架**（01-07系列文档）。

## 1. 文档结构

```
Corporate/
├── 00-index.md                    ← 本文档（总索引）
├── 01-employee-governance.md       ← 统一员工治理（对齐03-employee-model.md）
├── 02-brain-governance.md         ← 大脑协作治理（对齐02-core-architecture.md）
├── 03-channel-governance.md        ← 通道通信治理（对齐02-core-architecture.md）
├── 04-knowledge-governance.md      ← 知识分层治理（对齐04-knowledge-system.md）
├── 05-evolution-governance.md     ← 进化闭环治理（对齐05-evolution-system.md）
├── 06-security-governance.md      ← 安全权限治理（对齐06-security-permission.md）
├── 07-deployment-governance.md    ← 部署运维治理（对齐07-deployment-operations.md）
└── hr/                            ← 人力资源制度（HR视角）
    ├── hr-01-employee-lifecycle.md      ← 员工生命周期管理
    ├── hr-02-attendance-performance.md  ← 考勤与绩效管理
    ├── hr-03-human-digital-collaboration.md ← 人机协作规范
    ├── hr-04-department-coordination-reporting.md ← 部门协调与汇报
    ├── hr-05-training-development.md    ← 培训与发展制度
    ├── hr-06-job-responsibilities-outline.md ← 岗位职责总纲
    ├── hr-07-tech-department-responsibilities.md ← 技术部岗位职责
    ├── hr-08-ops-department-responsibilities.md ← 运营部岗位职责
    ├── hr-09-finance-department-responsibilities.md ← 财务部岗位职责
    ├── hr-10-hr-department-responsibilities.md ← 人力资源部岗位职责
    ├── hr-11-cs-department-responsibilities.md ← 客服部岗位职责
    ├── hr-12-sales-department-responsibilities.md ← 销售部岗位职责
    ├── hr-13-legal-department-responsibilities.md ← 法务部岗位职责
    ├── hr-14-admin-department-responsibilities.md ← 行政部岗位职责
    ├── hr-15-founder-enterprise-system.md ← 创始人/董事长制度
    ├── hr-16-digital-employee-strategy.md ← 数字员工战略规划
    ├── hr-17-digital-employee-performance.md ← 数字员工绩效评估
    └── hr-18-external-cooperation.md ← 对外合作与签约
```

## 2. 核心框架映射

| 治理文档 | 对应核心框架 | 所属领域 |
|---------|------------|---------|
| 01-employee-governance.md | 03-employee-model.md | employee |
| 02-brain-governance.md | 02-core-architecture.md §3.1 | brain |
| 03-channel-governance.md | 02-core-architecture.md §3.2 | channel |
| 04-knowledge-governance.md | 04-knowledge-system.md | knowledge |
| 05-evolution-governance.md | 05-evolution-system.md | evolution |
| 06-security-governance.md | 06-security-permission.md | security |
| 07-deployment-governance.md | 07-deployment-operations.md | infrastructure |
| hr-01-employee-lifecycle.md | 03-employee-model.md §3 | human-resource |
| hr-02-attendance-performance.md | 03-employee-model.md | human-resource |
| hr-03-human-digital-collaboration.md | 03-employee-model.md §5 | human-resource |
| hr-04-department-coordination-reporting.md | 02-core-architecture.md §3.1-3.2 | human-resource |
| hr-05-training-development.md | 05-evolution-system.md §2 | human-resource |
| hr-06-job-responsibilities-outline.md | 03-employee-model.md §4 | human-resource |
| hr-07-tech-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-08-ops-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-09-finance-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-10-hr-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-11-cs-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-12-sales-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-13-legal-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-14-admin-department-responsibilities.md | 02-core-architecture.md §3.1 | human-resource |
| hr-15-founder-enterprise-system.md | 06-security-permission.md §3 | human-resource |
| hr-16-digital-employee-strategy.md | 02-core-architecture.md §3.1 | human-resource |
| hr-17-digital-employee-performance.md | 05-evolution-system.md §5 | human-resource |
| hr-18-external-cooperation.md | 06-security-permission.md | human-resource |

## 3. 核心设计约束（必须遵守）

### 3.1 权限检查先于路由

> 来自：`02-core-architecture.md` §4、`06-security-permission.md` §3
>
> **任何操作必须先完成权限校验，权限检查必须先于路由与工具执行。**

### 3.2 Brain不直接依赖外部SDK

> 来自：`02-core-architecture.md` §4
>
> Brain不直接依赖外部系统SDK，统一通过Tool/Skill适配。

### 3.3 知识写入标注作用域

> 来自：`02-core-architecture.md` §4、`04-knowledge-system.md` §3
>
> 知识写入必须标注作用域（L1私有/L2部门/L3全局）。

### 3.4 进化执行可追踪可熔断可回滚

> 来自：`02-core-architecture.md` §4、`05-evolution-system.md` §5
>
> 进化执行必须可追踪、可熔断、可回滚（策略层面）。

## 4. ID命名规范（来自核心框架）

```
员工ID：employee://{human|digital}/{domain-or-provider}/{name-or-account}/{instance?}
神经元ID：neuron://{domain}/{role}/{instance}
通道ID：channel://{scope}/{name}
```

## 5. 六大领域速查

### 5.1 Employee域（统一员工）

| 核心概念 | 说明 |
|---------|------|
| HumanEmployee | 企业账号映射，强调外部身份同步 |
| DigitalEmployee | 系统内生，强调自治配置与运行态 |
| 编制与实例 | 编制定义边界，实例弹性创建 |
| 工具三层授权 | 员工配置 ∩ 部门白名单 ∩ 共享工具 |

### 5.2 Brain域（大脑协作）

| 核心概念 | 说明 |
|---------|------|
| MainBrain | 跨部门协调与复杂任务分解 |
| 部门大脑 | Tech/Hr/Finance/Ops等领域任务处理 |
| BrainContext | 模型+知识+技能+指令+通道组装 |
| ReAct循环 | 推理→工具调用→结果回写 |

### 5.3 Channel域（通道通信）

| 核心概念 | 说明 |
|---------|------|
| ChannelManager | 消息发布/订阅与通道治理 |
| 标准通道模式 | 广播、单播、优先级、轮询 |
| 会话隔离 | 感知/分发/响应通道分离 |

### 5.4 Knowledge域（知识分层）

| 核心概念 | 说明 |
|---------|------|
| L1私有 | 与员工/会话绑定，强调隐私 |
| L2部门 | 部门共享，承载最佳实践 |
| L3全局 | 全公司共享，承载通用制度 |
| 检索优先级 | L2 > L3 > L1（可配置） |

### 5.5 Evolution域（进化闭环）

| 核心概念 | 说明 |
|---------|------|
| 进化闭环 | 信号采集→提取→决策→执行→验证→沉淀 |
| 五大策略 | REPAIR/OPTIMIZE/INNOVATE/DEFER/ESCALATE |
| INNOVATE约束 | 必须验证门禁+回滚策略 |
| 熔断统计 | 失败进化计入熔断，避免重复 |

### 5.6 Security域（安全权限）

| 核心概念 | 说明 |
|---------|------|
| 访问级别 | CHAT_ONLY/LIMITED/DEPARTMENT/FULL |
| 隔离框架 | 部门级/知识级/工具级隔离 |
| 沙箱 | 资源/网络/文件/命令限制 |
| 审计 | 登录/鉴权失败/权限变更/敏感访问 |

## 6. HR制度速查

### 6.1 员工生命周期

| 阶段 | 核心制度 | 权限变更 |
|------|---------|---------|
| 入职 | hr-01-employee-lifecycle.md | CHAT_ONLY → LIMITED → DEPARTMENT |
| 试用期 | hr-02-attendance-performance.md | LIMITED |
| 转正 | hr-01-employee-lifecycle.md | DEPARTMENT |
| 调岗/晋升 | hr-01-employee-lifecycle.md | 权限重新配置 |
| 离职 | hr-01-employee-lifecycle.md | DEPARTMENT → CHAT_ONLY |

### 6.2 人机协作

| 协作模式 | 说明 | 规范文档 |
|---------|------|---------|
| 日常任务 | 简单重复任务直接分配 | hr-03 |
| 部门大脑 | 复杂任务由部门大脑协调 | hr-04 |
| 项目协作 | 跨部门项目人机配合 | hr-04 |

### 6.3 培训发展

| 培训类型 | 周期 | 预算 |
|---------|------|------|
| 入职培训 | 入职首月 | 公司承担 |
| 岗位技能 | 每季度≥20h | 3000元/人 |
| 管理培训 | 每半年≥32h | 5000元/人 |
| 合规培训 | 每年≥8h | 公司承担 |

### 6.4 岗位职责速查

| 制度编号 | 部门 | 核心使命 |
|---------|------|---------|
| hr-06 | 总纲 | 岗位设置、职级序列、晋升通道 |
| hr-07 | 技术部 | 技术支撑与创新 (TechBrain) |
| hr-08 | 运营部 | 业务增长与用户价值 (OpsBrain) |
| hr-09 | 财务部 | 财务健康与合规 (FinanceBrain) |
| hr-10 | 人力资源部 | 人才保障与组织发展 (HrBrain) |
| hr-11 | 客服部 | 客户服务与问题解决 (CsBrain) |
| hr-12 | 销售部 | 收入增长与客户满意 (SalesBrain) |
| hr-13 | 法务部 | 法律合规与风险防控 (LegalBrain) |
| hr-14 | 行政部 | 行政保障与后勤服务 (AdminBrain) |

### 6.5 一人公司特殊制度

| 制度编号 | 名称 | 核心内容 |
|---------|------|---------|
| hr-15 | 创始人/董事长制度 | FULL权限、战略决策、数字员工整体管理 |
| hr-16 | 数字员工战略规划 | 编制规划、能力发展、人机协作 |
| hr-17 | 数字员工绩效评估 | 评估维度、方法、结果应用 |
| hr-18 | 对外合作与签约 | 合作流程、合作伙伴管理、风险管理 |

## 7. 文档阅读顺序建议

### 7.1 新成员入门

1. `00-index.md` - 概览
2. `01-employee-governance.md` - 员工模型
3. `02-brain-governance.md` - 大脑协作
4. `06-security-governance.md` - 安全权限

### 7.2 业务开发参考

1. `01-employee-governance.md` - 员工与工具授权
2. `04-knowledge-governance.md` - 知识分层
3. `05-evolution-governance.md` - 进化闭环

### 7.3 运维部署参考

1. `07-deployment-governance.md` - 部署运维
2. `06-security-governance.md` - 安全审计

### 7.4 HR管理参考

1. `00-index.md` - 概览
2. `hr-01-employee-lifecycle.md` - 员工生命周期
3. `hr-02-attendance-performance.md` - 考勤绩效
4. `hr-03-human-digital-collaboration.md` - 人机协作
5. `hr-04-department-coordination-reporting.md` - 部门协调
6. `hr-05-training-development.md` - 培训发展
7. `hr-06-job-responsibilities-outline.md` - 岗位职责总纲
8. `hr-07~hr-14` - 各部门岗位职责

### 7.5 一人公司治理参考

1. `hr-15-founder-enterprise-system.md` - 创始人制度
2. `hr-16-digital-employee-strategy.md` - 数字员工战略
3. `hr-17-digital-employee-performance.md` - 数字员工绩效
4. `hr-18-external-cooperation.md` - 对外合作

## 8. 与核心框架的一致性保证

所有Corporate治理文档必须：
- 在文档头部标注对齐的核心框架文件
- 不引入核心框架未定义的概念
- 如需扩展，必须标注"治理建议"而非框架层

---

> 最后更新：2026-04-14
> 版本：v1.0
> 状态：与项目核心框架（01-07系列）保持一致
