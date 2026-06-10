# 主脑服务管理与数字员工治理落地文档

> **版本**: v2.0 | **日期**: 2026-05-26
>
> 本文档说明主脑（MainBrain）如何充分利用 GitLab、OpenProject、Jenkins、MemOS 等外部服务，
> 管理部门大脑和固定数字员工，构建完整的"主脑治理 → 服务协调 → 部门执行"三层闭环。
>
> **核心区分**：
> - **服务初始配置**：主脑（MainBrain）以管理员身份完成的**一次性配置**，包括创建项目/仓库/Pipeline/记忆空间
> - **工具使用**：数字员工在执行任务时调用 Tool 层（GitLabTool/OpenProjectTool/JenkinsTool）的**日常操作**
> - 本文档聚焦前者——主脑如何以超级管理员身份完成初始配置

---

## 一、核心理解：工具独立 vs 服务配置

### 1.1 工具是员工用的，配置是主脑做的

### 1.2 组织架构源自 `documents/`，服务只是"映射"

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    组织架构：单一事实来源                                    │
│                                                                            │
│  documents/  ←── 部门架构、员工编制、职责卡的唯一定义来源                   │
│    │                                                                       │
│    ├── shared/governance/                                                  │
│    │   ├── 01-employee-governance.md  → 统一员工模型 (9部门+32编制)        │
│    │   ├── 02-brain-governance.md     → 部门大脑架构 (8部门大脑+MainBrain) │
│    │   └── 03-channel-governance.md   → 通道通信治理                       │
│    │                                                                       │
│    ├── shared/company/                                                     │
│    │   ├── fixed-employee-routing-config.yaml → 部门路由映射               │
│    │   ├── hr-20~hr-26 → 各部门固定员工职责卡                              │
│    │   └── hr-16-digital-employee-strategy.md → 数字员工战略               │
│    │                                                                       │
│    └── department/{dept}/policies/                                         │
│        └── hr-0X-{dept}-department-responsibilities.md → 各部门岗位职责    │
│                                                                            │
│  主脑的职责：                                                               │
│  ├── 从 documents/ 读取部门列表、编制信息                                   │
│  ├── 在外部服务(GitLab/OpenProject/Jenkins/MemOS)中创建对应的空间          │
│  └── 不重新定义部门架构，不做冗余的"创造"                                   │
│                                                                            │
│  ⚠️ 原则：documents/ = 部门定义的事实来源                                    │
│     外部服务配置 = documents/ 在工具中的投影                                │
└────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 两层的关系图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        运行时的两个层面                                   │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────┐        │
│  │  层面1: 服务初始配置 (一次性)                                 │        │
│  │  ├── 执行者: MainBrain (管理员身份)                          │        │
│  │  ├── 时机: 系统首次启动、新员工入职、新服务接入               │        │
│  │  └── 内容: 创建项目、仓库、账号、权限、Pipeline模板、记忆空间 │        │
│  └─────────────────────────────────────────────────────────────┘        │
│                              │                                           │
│                              ▼                                           │
│  ┌─────────────────────────────────────────────────────────────┐        │
│  │  层面2: 工具使用 (日常运行)                                   │        │
│  │  ├── 执行者: 部门大脑 + 数字员工 (员工专属账号)               │        │
│  │  ├── 时机: 每次用户请求触发任务执行                           │        │
│  │  ├── GitLabTool: list_projects, get_mr, create_mr_comment    │        │
│  │  ├── OpenProjectTool: search_issue, create_issue, add_comment│        │
│  │  └── JenkinsTool: list_jobs, build, build_status              │        │
│  └─────────────────────────────────────────────────────────────┘        │
│                                                                          │
│  ⚠️ 两者不冲突：工具独立运行是正确的，但初始配置必须先完成                │
│     就像要先创建仓库，才能用 GitLabTool 操作仓库                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 1.4 当前代码状态

| 层面的内容 | 代码位置 | 状态 | 使用者 |
|-----------|---------|------|--------|
| **服务配置** (项目/仓库/账号) | ❌ 未实现 | 需新建 `ServiceAdminBootstrap` | MainBrain |
| **工具使用** (操作项目/仓库/MR) | `GitLabTool.java` | ✅ 已实现 | TechBrain+员工 |
| **工具使用** (操作工作包/任务) | `OpenProjectTool.java` | ✅ 已实现 | 部门脑+员工 |
| **工具使用** (触发构建/状态) | `JenkinsTool.java` | ✅ 已实现 | T03 DevOps |

---

## 二、GitLab 初始配置详解

### 2.1 首次启动状态

```
docker-compose up gitlab 后：
  ├── GitLab 容器启动
  ├── 初始化 PostgreSQL、Redis、Puma、Sidekiq 等内部服务
  ├── 生成 root 初始密码 → /etc/gitlab/initial_root_password
  ├── 健康检查: curl http://gitlab:8929/-/health → 200
  └── 状态: 没有任何 Group/Project/User（仅有 root）
```

### 2.2 主脑需要完成的初始配置

#### Step 1: 获取 Root Token

```bash
# 1.1 手动获取 root 初始密码
docker exec living-agent-gitlab cat /etc/gitlab/initial_root_password
# 示例输出: Password: A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6

# 1.2 用 root 密码创建 Personal Access Token（通过 API 或 UI 创建）
# Token 范围: api, read_user, sudo, read_api
# 存储: 加密后存入 ServiceAdminCredential 表
```

#### Step 2: 读取组织架构 → 创建部门 Group

> **⚠️ 组织的部门架构已在 `documents/` 中完整定义，不需要主脑重新"创造"部门概念。**
> 主脑的职责是：读取已有的组织架构，在 GitLab 中创建对应的 Group。
>
> **数据源**（单一事实来源）：
> - `documents/shared/governance/01-employee-governance.md` → 统一员工模型（9个部门 + 编制定义）
> - `documents/shared/governance/02-brain-governance.md` → 部门大脑架构（8个部门大脑 + MainBrain）
> - `documents/department/{dept}/` → 各部门职责卡、人员编制、路由配置
> - `documents/shared/company/fixed-employee-routing-config.yaml` → 部门路由映射

```
从 documents/ 读取的组织架构 → 映射到 GitLab Group：

来源: documents/shared/governance/ (8个部门大脑)
来源: documents/department/{dept}/policies/ (部门职责)
来源: documents/shared/company/fixed-employee-routing-config.yaml (路由映射)

POST /api/v4/groups
Authorization: PRIVATE-TOKEN <root_token>

tech     → 技术部    (TechBrain)       | T01-T10 (10个编制)
hr       → 人力资源  (HrBrain)         | H01-H02
finance  → 财务部    (FinanceBrain)     | F01-F04
sales    → 销售部    (SalesBrain)       | S01-S03
ops      → 运营部    (OpsBrain)         | O01-O04
cs       → 客服部    (CsBrain)          | C01-C02
admin    → 行政部    (AdminBrain)       | A01-A03
legal    → 法务部    (LegalBrain)       | L01-L02
core     → 跨部门    (MainBrain)        | M01-M02
```

#### Step 3: 在技术部 Group 下创建研发项目

```
POST /api/v4/projects
Authorization: PRIVATE-TOKEN <root_token>

技术部需要以下项目：
├── { "name": "living-agent-service", "namespace_id": tech_group_id }
├── { "name": "living-agent-frontend", "namespace_id": tech_group_id }
├── { "name": "living-agent-native", "namespace_id": tech_group_id }
└── { "name": "infrastructure-config", "namespace_id": tech_group_id }
```

#### Step 4: 为数字员工创建 GitLab 账号

```
POST /api/v4/users
Authorization: PRIVATE-TOKEN <root_token>

{ "email": "t01-bot@living-agent.local", "username": "t01-code-reviewer-bot", "name": "T01-代码审查员", "reset_password": false, "force_random_password": true }

{ "email": "t02-bot@living-agent.local", "username": "t02-architect-bot", "name": "T02-架构师", "reset_password": false, "force_random_password": true }

{ "email": "t03-bot@living-agent.local", "username": "t03-devops-bot", "name": "T03-DevOps工程师", "reset_password": false, "force_random_password": true }

{ "email": "t09-bot@living-agent.local", "username": "t09-frontend-bot", "name": "T09-前端工程师", "reset_password": false, "force_random_password": true }

{ "email": "t10-bot@living-agent.local", "username": "t10-backend-bot", "name": "T10-后端工程师", "reset_password": false, "force_random_password": true }
```

#### Step 5: 为员工创建 Personal Access Token

```
POST /api/v4/users/{user_id}/personal_access_tokens
Authorization: PRIVATE-TOKEN <root_token>

{ "name": "living-agent-token", "scopes": ["api", "read_api"] }

# 每个员工返回的 token 存入 EmployeeExternalAccount
```

#### Step 6: 加入部门 Group 并授权

```
POST /api/v4/groups/{tech_group_id}/members
Authorization: PRIVATE-TOKEN <root_token>

{ "user_id": t01_gitlab_user_id, "access_level": 30 }  # 30=Developer
{ "user_id": t02_gitlab_user_id, "access_level": 30 }
{ "user_id": t03_gitlab_user_id, "access_level": 30 }
{ "user_id": t09_gitlab_user_id, "access_level": 30 }
{ "user_id": t10_gitlab_user_id, "access_level": 30 }
```

#### Step 7: 设置 CI/CD 变量（可选）

```
POST /api/v4/projects/{project_id}/variables
Authorization: PRIVATE-TOKEN <root_token>

{ "key": "DOCKER_REGISTRY", "value": "registry.living-agent.local", "protected": true }
{ "key": "KUBE_CONFIG", "value": "...", "protected": true, "masked": true }
```

### 2.3 需要扩展的 GitLabTool API

| 新 action | API 端点 | 用途 | 当前状态 |
|-----------|---------|------|---------|
| `create_user` | `POST /api/v4/users` | 创建数字员工GitLab账号 | ❌ 未实现 |
| `create_group` | `POST /api/v4/groups` | 创建部门Group | ❌ 未实现 |
| `add_group_member` | `POST /api/v4/groups/{id}/members` | 加入Group并授权 | ❌ 未实现 |
| `create_token` | `POST /api/v4/users/{id}/personal_access_tokens` | 为用户创建Token | ❌ 未实现 |
| `block_user` | `POST /api/v4/users/{id}/block` | 锁定员工账号 | ❌ 未实现 |

---

## 三、OpenProject 初始配置详解

### 3.1 首次启动状态

```
docker-compose up openproject 后：
  ├── 连接 PostgreSQL，自动创建 openproject 数据库
  ├── 自动执行数据库迁移（MIGRATE=true）
  ├── 自动创建 admin 账号（admin/admin123456）
  ├── 健康检查: curl http://openproject:8080/health_checks/default → 200
  └── 状态: 有 admin 账号，无任何 Project/WorkPackage/User
```

### 3.2 主脑需要完成的初始配置

#### Step 1: 获取 Admin API Key

```
# OpenProject 使用 Basic Auth 形式: "apikey:{api_key}"
# 方式1: 在 UI → "我的账户" → "访问令牌" 中创建
# 方式2: 登录后通过 /my/access_token 接口创建

# 存储: 将 admin api_key 加密存入 ServiceAdminCredential
```

#### Step 2: 创建自定义角色（RBAC）

```
POST /api/v3/roles
Authorization: Basic apikey:{admin_api_key}

1. 数字员工-任务执行者（Project Role）
{
  "name": "Digital Employee - Worker",
  "permissions": [
    "view_work_packages", "edit_work_packages",
    "add_work_package_notes", "log_time",
    "view_members", "view_wiki_pages"
  ],
  "global": false
}

2. 部门脑-任务管理者（Project Role）
{
  "name": "Digital Employee - Manager",
  "permissions": [
    "view_work_packages", "edit_work_packages", "add_work_package_notes",
    "manage_work_package_relations", "manage_members",
    "log_time", "view_time_entries",
    "view_wiki_pages", "edit_wiki_pages"
  ],
  "global": false
}
```

#### Step 3: 读取组织架构 → 创建部门项目

> **⚠️ 部门定义来源于 `documents/`，主脑不做重复定义。**
> 主脑从 `documents/shared/governance/` 和 `documents/department/{dept}/` 读取部门信息，
> 在 OpenProject 中创建对应的项目空间。

```
从 documents/ 读取 → 映射到 OpenProject 项目：

来源: documents/department/{dept}/policies/hr-0X-{dept}-department-responsibilities.md
来源: documents/shared/company/hr-2X-{dept}-fixed-employee-duty-card.md

POST /api/v3/projects
Authorization: Basic apikey:{admin_api_key}

identifier: "tech"   → name: "技术部" | 10个编制 (T01-T10)
identifier: "hr"     → name: "人力资源" | 2个编制 (H01-H02)
identifier: "finance"→ name: "财务部"   | 4个编制 (F01-F04)
identifier: "sales"  → name: "销售部"   | 3个编制 (S01-S03)
identifier: "ops"    → name: "运营部"   | 4个编制 (O01-O04)
identifier: "cs"     → name: "客服部"   | 2个编制 (C01-C02)
identifier: "admin"  → name: "行政部"   | 3个编制 (A01-A03)
identifier: "legal"  → name: "法务部"   | 2个编制 (L01-L02)
identifier: "cross-dept" → name: "跨部门协作" | 2个编制 (M01-M02)
```

#### Step 4: 配置工作包类型和状态

```
# OpenProject 默认自带: Task, Bug, Feature, Milestone, Epic, Phase 等类型
# 默认状态: New, In Progress, Resolved, Closed, On Hold, Rejected

# 不需要额外创建（默认足够），但需要验证：
GET /api/v3/types       # 确认有哪些类型可用
GET /api/v3/statuses    # 确认有哪些状态可用
```

#### Step 5: 创建数字员工 OpenProject 账号

```
POST /api/v3/users
Authorization: Basic apikey:{admin_api_key}

{ "login": "t01-code-reviewer-bot", "email": "t01-bot@living-agent.local",
  "firstName": "T01", "lastName": "代码审查员", "admin": false,
  "status": "active", "password": "<自动生成>",
  "language": "zh-CN" }

{ "login": "t02-architect-bot", ... }
... (32个员工全部注册)
```

#### Step 6: 将员工加入对应部门项目

```
POST /api/v3/projects/{project_id}/memberships
Authorization: Basic apikey:{admin_api_key}

# T01 加入技术部项目，角色为 Worker
{ "principal": { "href": "/api/v3/users/{t01_user_id}" },
  "roles": [{ "href": "/api/v3/roles/{worker_role_id}" }] }

# TechBrain 神经元加入技术部项目，角色为 Manager
{ "principal": { "href": "/api/v3/users/{techbrain_user_id}" },
  "roles": [{ "href": "/api/v3/roles/{manager_role_id}" }] }
```

#### Step 7: 验证配置

```
GET /api/v3/projects/{tech_project_id}/memberships  → 确认成员列表
GET /api/v3/work_packages?pageSize=1                → 确认项目可操作
```

### 3.3 需要扩展的 OpenProjectTool API

| 新 action | API 端点 | 用途 | 当前状态 |
|-----------|---------|------|---------|
| `create_project` | `POST /api/v3/projects` | 创建部门项目 | ❌ 未实现 |
| `create_user` | `POST /api/v3/users` | 创建员工OpenProject账号 | ❌ 未实现 |
| `add_member` | `POST /api/v3/projects/{id}/memberships` | 加入项目并授权 | ❌ 未实现 |
| `create_role` | `POST /api/v3/roles` | 创建自定义角色 | ❌ 未实现 |
| `lock_user` | `PATCH /api/v3/users/{id}` | 锁定/解锁员工账号 | ❌ 未实现 |

> **注意**：OpenProjectTool 当前实现中 `NAME = "jira"`，对外暴露为 jira 工具名。这是设计上兼容 Jira 语义，内部映射到 OpenProject API。

---

## 四、Jenkins 初始配置详解

### 4.1 首次启动状态

```
docker-compose up jenkins 后：
  ├── Jenkins 容器启动
  ├── 初始化插件安装（默认推荐插件或空）
  ├── 生成 admin 初始密码 → /var/jenkins_home/secrets/initialAdminPassword
  ├── 显示 Setup Wizard（如果未跳过）
  ├── 健康检查: curl http://jenkins:8080/login → 200
  └── 状态: 有 admin 账号，无任何 Job/Credential/Pipeline
```

### 4.2 主脑需要完成的初始配置

#### Step 1: 获取 Admin Token

```bash
# 1.1 手动获取初始密码
docker exec living-agent-jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# 1.2 登录 Jenkins 完成 Setup Wizard（首次必须手动）
#   - 跳过插件安装（或安装推荐插件）
#   - 创建 admin 用户，设置密码

# 1.3 创建 API Token
#   - 点击右上角 admin → Configure → API Token → Add new Token
#   - 将生成的 Token 加密存入 ServiceAdminCredential
```

#### Step 2: 安装必要插件

```
通过 /pluginManager/installNecessaryPlugins 或 CLI：
├── git          - Git 集成
├── gitlab-plugin - GitLab 触发
├── workflow-aggregator - Pipeline 全套
├── docker-workflow - Docker Pipeline 支持
├── blueocean    - Pipeline 可视化
├── role-strategy - 基于角色的权限
├── pipeline-stage-view - Pipeline 阶段可视化
└── build-timeout - 构建超时控制
```

#### Step 3: 创建凭据

```
POST /credentials/store/system/domain/_/createCredentials
X-Jenkins-Crumb: <crumb>
Authorization: Basic admin:{api_token}

1. GitLab API Token 凭据:
{
  "credentials": {
    "scope": "GLOBAL",
    "id": "gitlab-root-token",
    "description": "GitLab Root Token for CI/CD",
    "secret": "<gitlab_root_token>",
    "stapler-class": "org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl"
  }
}

2. Docker Registry 凭据:
{
  "credentials": {
    "scope": "GLOBAL",
    "username": "robot",
    "password": "<registry_pass>",
    "id": "docker-registry",
    "description": "Docker Registry",
    "stapler-class": "com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"
  }
}
```

#### Step 4: 创建构建 Job（Pipeline 模板）

```
POST /createItem?name=living-agent-build
Authorization: Basic admin:{api_token}
Content-Type: application/xml

<flow-definition plugin="workflow-job">
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition">
    <script><![CDATA[
pipeline {
    agent any
    triggers { pollSCM('H/5 * * * *') }
    stages {
        stage('Checkout') {
            steps {
                git url: 'http://gitlab:8929/tech/living-agent-service.git',
                    branch: 'main',
                    credentialsId: 'gitlab-root-token'
            }
        }
        stage('Build') { steps { sh 'mvn compile -DskipTests' } }
        stage('Test') { steps { sh 'mvn test' } }
        stage('Package') { steps { sh 'mvn package -DskipTests' } }
    }
    post {
        failure { emailext(to: 't01-bot@living-agent.local', subject: "Build Failed", body: "Check ${BUILD_URL}") }
    }
}
]]></script>
  </definition>
</flow-definition>
```

创建以下 Job：
- `living-agent-build` - 编译+测试+打包
- `living-agent-deploy` - 部署到测试环境
- `frontend-build` - 前端构建
- `native-build` - Rust 原生模块构建

#### Step 5: 配置 Jenkins 权限

```
# 安装 Role-based Authorization Strategy 插件后
# 通过 /role-strategy/ 配置:

角色：
├── admin-role: 所有权限 (仅 main-brain-jenkins 用户)
├── devops-bot-role: Build, Read (仅 T03)
├── bot-readonly-role: Read (T01, T02, T09, T10)
```

#### Step 6: 创建 T03 DevOps 员工 Jenkins 账号

```
POST /securityRealm/createAccountByAdmin
{ "username": "t03-devops-bot", "password1": "<auto_generated>", "password2": "<auto_generated>",
  "email": "t03-bot@living-agent.local", "fullname": "T03-DevOps工程师" }

# 然后创建 T03 的 API Token（同上方式）
```

### 4.3 需要扩展的 JenkinsTool API

| 新 action | API 端点 | 用途 | 当前状态 |
|-----------|---------|------|---------|
| `create_job` | `POST /createItem?name={name}` | 创建Pipeline Job | ❌ 未实现 |
| `delete_job` | `POST /job/{name}/doDelete` | 删除Job | ❌ 未实现 |
| `list_plugins` | `GET /pluginManager/api/json` | 查看已安装插件 | ❌ 未实现 |
| `create_credential` | `POST /credentials/...` | 创建凭据 | ❌ 未实现 |

---

## 五、MemOS 初始配置详解

### 5.1 首次启动状态

```
docker-compose up memos memos-neo4j 后：
  ├── MemOS FastAPI 服务启动 (端口8381)
  ├── Neo4j 启动 (端口7688)
  ├── Qdrant 向量数据库就绪
  ├── 健康检查: curl http://localhost:8000/openapi.json → 200
  └── 状态: 就绪，无任何 Cube/Memory/User
```

### 5.2 主脑需要完成的初始配置

#### Step 1: 创建企业级记忆 Cube

```
POST /api/cubes
{ "cubeId": "enterprise", "cubeName": "企业记忆空间", "description": "全企业共享的记忆和知识" }

POST /api/cubes
{ "cubeId": "enterprise-docs", "cubeName": "企业文档记忆", "description": "制度、规范、流程文档的记忆" }
```

#### Step 2: 创建部门记忆 Cube

```
每个部门独立的 Cube：
POST /api/cubes { "cubeId": "tech-memory", "cubeName": "技术部记忆", ... }
POST /api/cubes { "cubeId": "hr-memory", "cubeName": "人力资源记忆", ... }
POST /api/cubes { "cubeId": "finance-memory", "cubeName": "财务部记忆", ... }
POST /api/cubes { "cubeId": "sales-memory", "cubeName": "销售部记忆", ... }
POST /api/cubes { "cubeId": "cs-memory", "cubeName": "客服部记忆", ... }
POST /api/cubes { "cubeId": "admin-memory", "cubeName": "行政部记忆", ... }
POST /api/cubes { "cubeId": "legal-memory", "cubeName": "法务部记忆", ... }
POST /api/cubes { "cubeId": "ops-memory", "cubeName": "运营部记忆", ... }
POST /api/cubes { "cubeId": "core-memory", "cubeName": "跨部门记忆", ... }
```

#### Step 3: 创建数字员工个人记忆 Cube

```
32个员工每人一个：
POST /api/cubes { "cubeId": "employee-T01-memory", "cubeName": "T01-代码审查员记忆", ... }
POST /api/cubes { "cubeId": "employee-T02-memory", "cubeName": "T02-架构师记忆", ... }
... (32个)
```

#### Step 4: 存储初始化种子记忆

```
POST /api/memos
{
  "cubeId": "enterprise",
  "content": "企业制度: 数字员工编制32人，覆盖9个部门。技术部10人(T01-T10)、财务部4人(F01-F04)...",
  "tags": ["enterprise", "structure", "init"]
}

POST /api/memos
{
  "cubeId": "tech-memory",
  "content": "技术部开发规范: Java 21 + Spring Boot 3.4，前端 React + Vite + TypeScript...",
  "tags": ["tech", "standards", "init"]
}
```

### 5.3 MemOS API 对接说明

当前代码中 `MemosMemoryBackend` 已集成，但 MemOS 的 cube 管理员 API 尚未完全封装。建议在 `ServiceAdminBootstrap` 中直接调用 MemOS REST API（FastAPI 自动生成的 `/api/cubes` 端点），不经过 `MemosMemoryBackend` 的 Memory 层。

---

## 六、完整初始化流程：ServiceAdminBootstrap

### 6.1 整体时序

```
MainBrain.start()
  │
  ├── Step 0: 等待所有服务健康就绪
  │     ├── curl http://openproject:8080/health_checks/default → 200
  │     ├── curl http://gitlab:8929/-/health → 200
  │     ├── curl http://jenkins:8080/login → 200
  │     └── curl http://memos:8000/openapi.json → 200
  │
  ├── Step 1: 加载管理员凭据
  │     ├── OpenProject: admin / admin123456 → 获取 api_key
  │     ├── GitLab: root / (读取 initial_root_password) → 创建 PAT
  │     ├── Jenkins: admin / (读取 initialAdminPassword) → 创建 API Token
  │     └── MemOS: 首次注册管理员用户
  │
  ├── Step 1.5: 从 documents/ 读取组织架构（单一事实来源）
  │     ├── 来源: documents/shared/governance/  → 部门大脑架构、员工模型
  │     ├── 来源: documents/department/{dept}/policies/  → 岗位职责、编制信息
  │     ├── 来源: documents/shared/company/fixed-employee-routing-config.yaml  → 路由映射
  │     ├── 解析出: 9个部门列表 (tech/hr/finance/sales/ops/cs/admin/legal/core)
  │     └── 解析出: 32个编制 (T01-T10/F01-F04/S01-S03/O01-O04/C01-C02/A01-A03/L01-L02/M01-M02)
  │
  ├── Step 2: [OpenProject] 初始化项目框架
  │     ├── 创建 2 个自定义角色 (Worker / Manager)
  │     ├── 创建 9 个部门项目 + 1 个跨部门项目
  │     └── 创建 32 个数字员工用户
  │
  ├── Step 3: [GitLab] 初始化代码仓库
  │     ├── 创建 9 个部门 Group
  │     ├── 创建 4 个研发项目
  │     ├── 创建 5 个研发部员工用户
  │     ├── 为员工创建 Personal Access Token
  │     └── 将员工加入对应 Group
  │
  ├── Step 4: [Jenkins] 初始化 CI/CD
  │     ├── 安装必要插件
  │     ├── 创建 GitLab / Docker 凭据
  │     ├── 创建 4 个 Pipeline Job
  │     ├── 配置权限矩阵
  │     └── 创建 T03 DevOps 用户
  │
  ├── Step 5: [MemOS] 初始化记忆空间
  │     ├── 创建 1 个企业 Cube
  │     ├── 创建 9 个部门 Cube
  │     ├── 创建 32 个员工 Cube
  │     └── 写入种子记忆
  │
  ├── Step 6: 记录所有外部ID到 Employee 元数据
  │     └── EmployeeExternalAccount 表批量写入
  │
  └── Step 7: 将配置写入 Tool 层
        ├── OpenProjectTool.configure(baseUrl, adminApiKey)
        ├── GitLabTool.configure(gitlabUrl, adminToken)
        ├── GitLabTool.setEmployeeAccounts(tokenMap)
        ├── JenkinsTool.(baseUrl, username, apiToken)
        └── MemosMemoryBackend.connect(memosUrl)
```

### 6.2 幂等性设计

```
每次启动时检查：
  ├── 检查是否已初始化 (ServiceAdminCredential.initialized == true)
  │     ├── 是 → 跳过创建，仅验证配置完整性
  │     └── 否 → 执行完整初始化
  │
  ├── 每个创建操作前先 GET 确认不存在
  │     ├── GitLab: GET /api/v4/groups?search=tech → 判断是否存在
  │     ├── OpenProject: GET /api/v3/projects?filters=[{...}] → 判断是否存在
  │     └── Jenkins: GET /job/living-agent-build/api/json → 判断是否存在
  │
  └── 失败不阻塞启动，记录到admin_audit_log
```

---

## 七、双管理员体系

### 7.1 MainBrain 与"董事长"的关系

```
┌─────────────────────────────────────────────────────────────┐
│                    双管理员架构                              │
│                                                             │
│  MainBrain (neuron://core/main-brain/001)                   │
│  ├── 角色: 自动化超级管理员                                  │
│  ├── 操作: 创建账号、分配权限、初始化配置、日常运维           │
│  ├── 权限: 持有所有外部服务的管理员 Token                     │
│  └── 审计: 所有操作自动记录到 admin_audit_log                 │
│                                                             │
│  董事长 (employee://human/{provider}/{accountId})            │
│  ├── 角色: 人类超级管理员                                    │
│  ├── 操作: 战略查看、审批决策、人工干预、配置调整             │
│  └── 权限: MainBrain 自动授予所有外部服务管理员               │
│                                                             │
│  关系:                                                      │
│  ├── 董事长向 MainBrain 下达指令                             │
│  ├── MainBrain 通过外部服务 API 执行操作                     │
│  └── 董事长也可以直接访问外部服务 UI 进行手动管理             │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 董事长注册后自动授予外部管理员

```
ChairmanOnboardingService.onboard(userId):
  
  1. 检测用户身份
     PermissionService.getAccessLevel(userId) == AccessLevel.FULL
     && user.role == "chairman"
     → 触发自动授权流程
     
  2. OpenProject 管理员授权
     POST /api/v3/users/{openproject_user_id}/lock  → 先解锁
     PATCH /api/v3/users/{openproject_user_id}  → 设为 admin
     
  3. GitLab 管理员授权  
     POST /api/v4/groups/{admin_group_id}/members
     { "user_id": gitlab_user_id, "access_level": 50 }  # 50=Owner
     
  4. Jenkins 管理员授权
     POST /role-strategy/strategy/assignRole
     { "type": "globalRoles", "roleName": "admin-role", "sid": jenkins_username }
     
  5. 记录日志 → 完成
```

> **注意**：如果董事长尚未在 GitLab/OpenProject/Jenkins 注册账号，则 Step 2-4 需要先通过 MainBrain 为其创建外部服务账号。

---

## 八、关键代码组件

### 8.1 ServiceAdminCredential（新增）

```
位置: living-agent-core/.../core/deployment/ServiceAdminCredential.java

字段:
  - serviceName: String        // "openproject", "gitlab", "jenkins", "memos"
  - adminUsername: String      // 管理员用户名
  - adminPassword: String      // 加密存储
  - adminToken: String         // API Token，加密存储
  - baseUrl: String            // 服务地址
  - tokenExpiry: LocalDateTime
  - initialized: boolean       // 是否已初始化配置
  - createdAt: LocalDateTime
  - updatedAt: LocalDateTime
```

### 8.2 EmployeeExternalAccount（新增）

```
位置: living-agent-core/.../core/employee/EmployeeExternalAccount.java

字段:
  - employeeCode: String       // "T01", "F01" 等
  - serviceName: String        // "openproject", "gitlab", "jenkins", "memos"
  - externalUserId: String     // 外部服务平台上的用户ID
  - externalUsername: String   // 外部服务平台上的用户名
  - accessToken: String        // API Token (AES加密)
  - accessLevel: String        // 权限级别 (developer/admin/...)
  - active: boolean
  - createdAt: LocalDateTime
  - lastUsedAt: LocalDateTime
```

### 8.3 ServiceAdminBootstrap（新增）

```
位置: living-agent-core/.../core/deployment/ServiceAdminBootstrap.java

方法:
  - initializeAll(): void                              // 入口
  - initializeOpenProject(credential): void            // OpenProject初始化
  - initializeGitLab(credential): void                 // GitLab初始化  
  - initializeJenkins(credential): void                // Jenkins初始化
  - initializeMemos(credential): void                  // MemOS初始化
  - provisionEmployeeAccounts(employees): void         // 批量创建员工账号
  - verifyAllServices(): HealthMap                     // 验证所有服务配置
  - isInitialized() boolean                            // 检查是否已初始化
```

### 8.4 ChairmanOnboardingService（新增）

```
位置: living-agent-core/.../core/security/ChairmanOnboardingService.java

方法:
  - onboardChairman(userId): List<OnboardResult>       // 授权所有外部服务
  - grantOpenProjectAdmin(userId): OnboardResult       // OpenProject授权
  - grantGitLabAdmin(userId): OnboardResult            // GitLab授权
  - grantJenkinsAdmin(userId): OnboardResult           // Jenkins授权
  - rollbackOnboarding(userId): void                   // 回滚（出错时）
```

---

## 九、工具扩展清单（GitLabTool / OpenProjectTool / JenkinsTool）

### 9.1 GitLabTool 需新增的 action（管理类）

| action | API | 权限级别 |
|--------|-----|---------|
| `create_group` | `POST /api/v4/groups` | Admin |
| `delete_group` | `DELETE /api/v4/groups/{id}` | Admin |
| `create_project` | `POST /api/v4/projects` | Admin |
| `delete_project` | `DELETE /api/v4/projects/{id}` | Admin |
| `create_user` | `POST /api/v4/users` | Admin |
| `block_user` | `POST /api/v4/users/{id}/block` | Admin |
| `unblock_user` | `POST /api/v4/users/{id}/unblock` | Admin |
| `add_member` | `POST /api/v4/groups/{id}/members` | Maintainer+ |
| `remove_member` | `DELETE /api/v4/groups/{id}/members/{uid}` | Maintainer+ |
| `create_token` | `POST /api/v4/users/{id}/personal_access_tokens` | Admin |

### 9.2 OpenProjectTool 需新增的 action（管理类）

| action | API | 权限级别 |
|--------|-----|---------|
| `create_project` | `POST /api/v3/projects` | Admin |
| `create_user` | `POST /api/v3/users` | Admin |
| `lock_user` | `PATCH /api/v3/users/{id}` (status=locked) | Admin |
| `add_member` | `POST /api/v3/projects/{id}/memberships` | Admin |
| `remove_member` | `DELETE /api/v3/memberships/{id}` | Admin |
| `create_role` | `POST /api/v3/roles` | Admin |
| `list_roles` | `GET /api/v3/roles` | Admin |
| `list_projects` | `GET /api/v3/projects` | Any authenticated |
| `get_project` | `GET /api/v3/projects/{id}` | Project member |

### 9.3 JenkinsTool 需新增的 action（管理类）

| action | API | 权限级别 |
|--------|-----|---------|
| `create_job` | `POST /createItem?name={name}` | Admin |
| `delete_job` | `POST /job/{name}/doDelete` | Admin |
| `create_credential` | `POST /credentials/store/system/domain/_/createCredentials` | Admin |
| `list_credentials` | `GET /credentials/store/system/domain/_/api/json` | Admin |
| `create_user` | `POST /securityRealm/createAccountByAdmin` | Admin |

### 9.4 管理类 action 的安全限制

```
关键规则：管理类 action（上述列表）仅在以下条件下可用：
  1. 调用者是 MainBrain (neuron://core/main-brain/001)
  2. 或 调用者通过了 AccessLevel.FULL + role=chairman 检查
  3. 使用管理员 Token（非员工专属 Token）

GitLabTool.resolveAccessToken() 需要区分：
  ├── 员工调用 (有 employeeCode) → 使用员工专属 Token
  └── MainBrain 调用 (no employeeCode) → 使用管理员 Token
```

---

## 十、落地实施计划

### 10.1 任务清单

| 编号 | 优先级 | 任务 | 工作量估计 | 依赖 |
|------|--------|------|-----------|------|
| M-01 | P0 | `ServiceAdminCredential` 实体 + JPA Repository | 小 | 无 |
| M-02 | P0 | `EmployeeExternalAccount` 实体 + JPA Repository | 小 | 无 |
| M-03 | P0 | `ServiceAdminBootstrap` - 服务健康检查 + 凭据获取 | 中 | M-01 |
| M-04 | P0 | `ServiceAdminBootstrap` - OpenProject 初始化 | 中 | M-03 |
| M-05 | P0 | `ServiceAdminBootstrap` - GitLab 初始化 | 中 | M-03 |
| M-06 | P0 | `ServiceAdminBootstrap` - Jenkins 初始化 | 中 | M-03 |
| M-07 | P0 | `ServiceAdminBootstrap` - MemOS 初始化 | 小 | M-03 |
| M-08 | P0 | `ServiceAdminBootstrap` - 员工账号批量创建 + 联结 | 中 | M-02, M-04~M-07 |
| M-09 | P0 | GitLabTool 扩展 - 管理类 action 实现 | 中 | M-05 |
| M-10 | P0 | OpenProjectTool 扩展 - 管理类 action 实现 | 中 | M-04 |
| M-11 | P0 | JenkinsTool 扩展 - 管理类 action 实现 | 中 | M-06 |
| M-12 | P1 | `ChairmanOnboardingService` - 董事长自动授权 | 中 | M-01, M-02 |
| M-13 | P1 | `ServiceHealthMonitor` - 运行时健康检查 | 小 | 无 |
| M-14 | P1 | OpenProjectTaskBridge - 任务双向同步 | 大 | M-10 |
| M-15 | P1 | GitLabMRBridge - MR 自动关联 | 大 | M-09 |
| M-16 | P1 | JenkinsBuildBridge - 构建自动化 | 中 | M-11 |
| M-17 | P2 | 员工绩效看板 | 中 | M-14 |
| M-18 | P2 | MemOS 知识自动晋升 | 中 | M-07 |

### 10.2 推荐执行顺序

```
第一周（基础设施）:
  M-01 → M-02 → M-03
  （创建实体、Repository、基础框架和健康检查）

第二周（服务初始化）:
  M-04 → M-05 → M-06 → M-07
  （实现四个服务的完整初始化流程）

第三周（员工账号 + 工具扩展）:
  M-08 → M-09 → M-10 → M-11
  （批量创建员工账号 + 扩展Tool的管理类 action）

第四周（授权 + 运行时监控）:
  M-12 → M-13
  （董事长自动授权 + 健康监控）

后续迭代:
  M-14 → M-15 → M-16  （任务闭环）
  M-17 → M-18          （治理能力）
```

---

## 附录A：服务密钥速查

| 服务 | 容器名 | 初始凭据获取命令 | 管理员账号 |
|------|--------|-----------------|-----------|
| OpenProject | living-agent-openproject | 固定: admin/admin123456 | admin |
| GitLab | living-agent-gitlab | `docker exec living-agent-gitlab cat /etc/gitlab/initial_root_password` | root |
| Jenkins | living-agent-jenkins | `docker exec living-agent-jenkins cat /var/jenkins_home/secrets/initialAdminPassword` | admin |
| MemOS | living-agent-memos | 首次访问 localhost:8381，注册第一个用户 | 首个注册用户 |

## 附录B：容器内网络地址

```
OpenProject: http://openproject:8080         (容器间调用)
GitLab:      http://gitlab:8929              (容器间调用)
Jenkins:     http://jenkins:8080             (容器间调用)
MemOS:       http://memos:8000               (容器间调用)
```

## 附录C：Jenkins CSRF 处理

```
# Jenkins 写操作前需要获取 CSRF Crumb
GET /crumbIssuer/api/json
Authorization: Basic admin:{api_token}

# 响应: { "_class": "...", "crumb": "abcdef123...", "crumbRequestField": "Jenkins-Crumb" }

# 后续 POST 请求需要附加 Header:
Jenkins-Crumb: <crumb_value>
```

## 附录D：参考文档

| 文档 | 说明 |
|------|------|
| `docs/core/02-core-architecture.md` | 核心架构设计 |
| `docs/权限与入口矩阵.md` | 权限与入口矩阵 |
| `docs/COMPLETE_LANDING_TODO.md` | 完全落地待办清单 |
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` | 代码结构与文件功能索引 |
| `documents/shared/governance/00-index.md` | 企业治理索引（部门架构的单一事实来源） |
| `documents/shared/company/fixed-employee-routing-config.yaml` | 部门路由映射配置 |
| `docker-compose.yml` | 所有服务配置 |