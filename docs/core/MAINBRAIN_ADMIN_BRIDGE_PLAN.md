# 主脑服务管理与做事规则衔接方案

> **版本**: v1.0 | **日期**: 2026-06-25
>
> **目的**: 解决 `MAINBRAIN_EXECUTION_RULES.md`（做事规则）与 `MAINBRAIN_SERVICE_MANAGEMENT.md`（服务管理）单独使用时的冲突，明确职责边界，统一管理类操作的实现路径。
>
> **核心原则**: 管理类操作作为独立的管理工具，通过 ToolRegistry 权限机制区分，MainBrain 可访问，其他大脑不可访问。

---

## 一、冲突点识别

### 1.1 定位差异（无冲突，互补关系）

| 维度 | EXECUTION_RULES | SERVICE_MANAGEMENT |
|------|-----------------|-------------------|
| **定位** | 运行时决策流程 | 启动时一次性配置 |
| **触发时机** | 每次用户消息 | 系统首次启动/新服务接入 |
| **执行者** | MainBrain + 部门大脑 + 数字员工 | MainBrain（管理员身份） |

两者定位互补，**不冲突**。

### 1.2 潜在冲突点

#### 冲突点 1：工具扩展归属混乱

SERVICE_MANAGEMENT 第 2.3/3.3/4.3 节要求在员工使用的工具中添加管理类 action：

| 工具 | 要求新增的管理类 action | 当前归属 |
|------|----------------------|---------|
| GitLabTool | create_user, create_group, add_group_member, create_token, block_user | enterprise 包，员工使用 |
| OpenProjectTool | create_project, create_user, add_member, create_role, lock_user | enterprise 包，员工使用 |
| JenkinsTool | create_job, create_credential, install_plugin, set_permission | enterprise 包，员工使用 |

**解决方案**：管理类操作作为独立的管理工具（GitLabAdminTool、OpenProjectAdminTool、JenkinsAdminTool），实现 Tool 接口，注册到 ToolRegistry，设置部门为 "admin_management"，MainBrain 可访问，其他大脑不可访问。

#### 冲突点 2：MainBrain 双重身份通过 ToolRegistry 区分

- EXECUTION_RULES 中 MainBrain 是"决策者+协调者"（运行时）
- SERVICE_MANAGEMENT 中 MainBrain 是"管理员+配置者"（启动时）
- **解决方案**：通过 ToolRegistry 的 BRAIN_TOOL_DEPARTMENT_MAPPING 区分：
  - MainBrain 使用 `toolRegistry.getAll()` 获取所有工具（包括管理类工具）
  - 其他大脑使用 `filterToolsByBrainDepartment()` 过滤工具（不包括管理类工具）
  - 管理类工具设置部门为 "admin_management"，只有 MainBrain 可访问

#### 冲突点 3：组织架构数据源重复定义风险

- SERVICE_MANAGEMENT 说"组织架构源自 documents/"
- EXECUTION_RULES 的员工分派逻辑中，员工定义来自 `FixedEmployeeRegistry`
- 两者是否同步、谁是单一事实来源，未明确

---

## 二、统一方案：管理类操作作为独立的管理工具

### 2.1 架构原则

```
┌──────────────────────────────────────────────────────────────────┐
│                    MainBrain 双重身份通过 ToolRegistry 区分         │
│                                                                  │
│  ┌────────────────────────────┐  ┌────────────────────────────┐ │
│  │  身份1: 决策者（运行时）     │  │  身份2: 管理员（启动时）     │ │
│  │  ├── 调用六步决策法          │  │  ├── 调用 ServiceAdminBootstrap│ │
│  │  ├── 通过 ReAct 循环        │  │  ├── 直接调用管理类工具       │ │
│  │  ├── 使用管理类工具          │  │  ├── 不走 ReAct 循环         │ │
│  │  └── 触发: 每次用户消息     │  │  └── 触发: 系统启动/新服务接入│ │
│  └────────────────────────────┘  └────────────────────────────┘ │
│                                                                  │
│  ⚠️ 两个身份都通过 ToolRegistry 调用管理类工具，权限机制统一         │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 包结构设计

```
core/tool/impl/admin/                 # 新建 admin 子包（在 tool 包内）
├── GitLabAdminTool.java              # GitLab 管理工具（实现 Tool 接口）
├── OpenProjectAdminTool.java         # OpenProject 管理工具（实现 Tool 接口）
├── JenkinsAdminTool.java             # Jenkins 管理工具（实现 Tool 接口）
└── MemOSAdminTool.java               # MemOS 管理工具（预留）

core/admin/                           # 保留 admin 包（编排层）
├── ServiceAdminBootstrap.java        # 服务初始化入口（接口）
├── ServiceAdminCredential.java       # 管理员凭据（值对象）
├── AdminOperationResult.java         # 管理操作结果（值对象）
├── EmployeeExternalAccount.java      # 员工外部账号映射（值对象）
└── impl/
    └── DefaultServiceAdminBootstrap.java  # 默认实现（调用管理类工具）
```

### 2.3 权限隔离规则

| 操作类型 | 执行者 | 调用路径 | 权限检查 |
|---------|--------|---------|---------|
| **管理类操作**（create_user/group/project） | MainBrain（管理员身份） | ServiceAdminBootstrap → AdminTool | 通过 ToolRegistry，部门为 "admin_management" |
| **日常操作**（list_projects/get_mr/build） | 部门大脑 + 数字员工 | BrainReActEngine → Tool | 通过 BRAIN_TOOL_DEPARTMENT_MAPPING 过滤 |

**关键约束**：
- AdminTool **实现 Tool 接口**，注册到 ToolRegistry
- AdminTool 设置部门为 "admin_management"
- MainBrain 使用 `toolRegistry.getAll()` 获取所有工具（包括管理类工具）
- 其他大脑使用 `filterToolsByBrainDepartment()` 过滤工具（不包括 "admin_management"）
- 员工工具（GitLabTool 等）**不添加**管理类 action

### 2.4 数据源一致性

```
documents/ (单一事实来源)
    ↓ 加载
FixedEmployeeRegistry (内存缓存)
    ↓ 映射
ServiceAdminBootstrap → 在外部服务中创建对应账号
    ↓ 存储映射关系
EmployeeExternalAccount (数据库表)
```

---

## 三、实施计划

### 3.1 阶段一：基础组件（P0）

| 任务 | 文件 | 说明 |
|------|------|------|
| 新建 admin 子包 | `core/tool/impl/admin/` | 在 tool 包内，管理类工具 |
| ServiceAdminCredential | `core/admin/ServiceAdminCredential.java` | 管理员凭据值对象 |
| EmployeeExternalAccount 实体 | `core/database/entity/EmployeeExternalAccountEntity.java` | 员工外部账号映射 |
| 数据库表 | `schema.sql` / `init-db/01_init.sql` | service_admin_credential 和 employee_external_account 表（已合并到 V27） |
| ServiceAdminBootstrap 接口 | `core/admin/ServiceAdminBootstrap.java` | 初始化入口 |

### 3.2 阶段二：管理工具实现（P1）

| 任务 | 文件 | 说明 |
|------|------|------|
| GitLabAdminTool | `core/tool/impl/admin/GitLabAdminTool.java` | 实现 Tool 接口，部门为 "admin_management" |
| OpenProjectAdminTool | `core/tool/impl/admin/OpenProjectAdminTool.java` | 实现 Tool 接口，部门为 "admin_management" |
| JenkinsAdminTool | `core/tool/impl/admin/JenkinsAdminTool.java` | 实现 Tool 接口，部门为 "admin_management" |
| DefaultServiceAdminBootstrap | `core/admin/impl/DefaultServiceAdminBootstrap.java` | 编排各 AdminTool 的初始化流程 |

### 3.3 阶段三：集成与配置（P2）

| 任务 | 文件 | 说明 |
|------|------|------|
| AdminConfig | `core/config/AdminConfig.java` | Spring Bean 配置，注册管理类工具到 ToolRegistry |
| application.yml 配置项 | `application.yml` | 管理员凭据配置 |
| 启动钩子 | `LivingAgentCoreConfig.java` | 应用就绪后触发 bootstrap |

### 3.4 阶段四：MemOS 与董事长入驻（P3，预留）

| 任务 | 文件 | 说明 |
|------|------|------|
| MemOSAdminTool | `core/tool/impl/admin/MemOSAdminTool.java` | 记忆空间管理（实现 Tool 接口） |
| ChairmanOnboardingService | `core/admin/ChairmanOnboardingService.java` | 董事长入驻流程 |

---

## 四、与现有文档的关系

### 4.1 对 EXECUTION_RULES 的影响

- **无影响**：六步决策法、五步执行法、知识沉淀、绩效记录等运行时逻辑保持不变
- **增强**：MainBrain 可以通过六步决策法调用管理类工具，权限隔离更清晰

### 4.2 对 SERVICE_MANAGEMENT 的调整

- **调整 1**：管理类 action **不扩展到 GitLabTool/OpenProjectTool/JenkinsTool**，而是独立成 AdminTool
- **调整 2**：AdminTool 实现 Tool 接口，注册到 ToolRegistry，通过 BRAIN_TOOL_DEPARTMENT_MAPPING 区分权限
- **调整 3**：EmployeeExternalAccount 作为数据库表存储映射关系，替代配置文件中的 employee-accounts

### 4.3 衔接点

```
启动阶段:
  ServiceAdminBootstrap
    ├── 读取 documents/ 组织架构
    ├── 通过 ToolRegistry 获取 GitLabAdminTool
    ├── 调用 GitLabAdminTool.execute("create_group", ...)
    ├── 调用 OpenProjectAdminTool.execute("create_project", ...)
    ├── 调用 JenkinsAdminTool.execute("create_job", ...)
    └── 存储映射关系到 EmployeeExternalAccount 表

运行阶段:
  MainBrain（决策者身份）
    ├── 六步决策法（EXECUTION_RULES）
    ├── 可调用管理类工具（GitLabAdminTool 等）
    ├── 员工使用 GitLabTool/OpenProjectTool/JenkinsTool（日常操作）
    └── 员工凭据从 EmployeeExternalAccount 表读取
```

---

## 五、验收标准

1. ✅ `core/tool/impl/admin/` 包存在，管理类工具实现 Tool 接口
2. ✅ AdminTool 注册到 ToolRegistry，部门为 "admin_management"
3. ✅ GitLabTool/OpenProjectTool/JenkinsTool 中不包含管理类 action
4. ✅ EmployeeExternalAccount 表存在（已合并到 schema.sql）
5. ✅ ServiceAdminBootstrap 可在启动时被调用（通过配置开关控制）
6. ✅ 管理员凭据加密存储在 ServiceAdminCredential 表
7. ✅ MainBrain 可访问管理类工具，其他大脑不可访问
8. ✅ 编译通过，不影响现有功能

---

## 六、回滚方案

如果新方案出现问题：
1. AdminTool 实现了 Tool 接口，可以随时从 ToolRegistry 中移除
2. 数据库表只新增，不修改现有表
3. ServiceAdminBootstrap 通过配置开关控制，默认不启用
4. 可随时回滚到 SERVICE_MANAGEMENT 文档的原方案（在工具中扩展管理类 action）
5. 或者回滚到原来的 AdminService 方案（不实现 Tool 接口）
