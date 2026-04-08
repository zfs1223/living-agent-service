# 飞书集成可行性分析报告

> **文档版本**: v3.0  
> **创建日期**: 2026-03-28  
> **更新日期**: 2026-03-30  
> **状态**: 已确认  
> **关联文档**: [21-compliance-optimization.md](./21-compliance-optimization.md)

---

## 一、概述

本文档分析 Living Agent Service 与飞书开放平台的集成方案，采用**管理者机器人 + 员工OAuth授权**的混合架构。

**核心决策**：
1. ✅ 管理者使用机器人（tenant_access_token）进行组织架构管理
2. ✅ 员工使用 OAuth 授权（user_access_token）以自己身份操作
3. ✅ 所有飞书操作必须记录审计日志并关联合规系统
4. ✅ 参考 lark-cli-main 的 OAuth 流程实现员工授权

---

## 二、合规要求与飞书集成的关系

### 2.1 合规文档核心要求

根据 [21-compliance-optimization.md](./21-compliance-optimization.md)，飞书集成需要满足以下合规要求：

| 合规要求 | 对飞书集成的影响 |
|---------|----------------|
| **审计追溯** | 每次飞书 API 调用必须记录操作人身份 |
| **AI决策可解释性** | 数字员工通过飞书执行操作时需记录决策依据 |
| **数据变更历史** | 飞书中的组织架构变更需记录前后快照 |
| **权限隔离** | 不同权限级别的数字员工使用不同的飞书机器人 |
| **敏感数据保护** | App Secret 加密存储，禁止明文配置 |

### 2.2 飞书集成的合规风险矩阵

```
┌─────────────────────────────────────────────────────────────────┐
│                   飞书集成合规风险矩阵                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【高风险】                                                      │
│  ├── App Secret 明文存储 → 数据泄露风险                          │
│  ├── 所有数字员工共用一个机器人 → 身份追溯困难                    │
│  └── 敏感操作无审批流程 → 合规审计失败                            │
│                                                                 │
│  【中风险】                                                      │
│  ├── Token 未加密传输 → 中间人攻击                               │
│  ├── 操作日志不完整 → 审计证据不足                               │
│  └── 权限未最小化 → 越权操作风险                                 │
│                                                                 │
│  【低风险】                                                      │
│  ├── API 调用频率过高 → 服务不可用                               │
│  └── 回调丢失 → 状态不一致                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、架构设计（v3.0 优化版）

### 3.1 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│              管理者机器人 + 员工OAuth授权 混合架构                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【管理飞书】- tenant_access_token（机器人身份）                 │
│  ══════════════════════════════════════════════                  │
│  ┌─────────────────────┐    ┌─────────────────────┐             │
│  │   董事长机器人       │    │     HR机器人        │             │
│  │   App ID: A         │    │     App ID: B       │             │
│  │   权限: 全部         │    │     权限: 通讯录     │             │
│  └──────────┬──────────┘    └──────────┬──────────┘             │
│             │                          │                         │
│             ▼                          ▼                         │
│  ┌─────────────────────┐    ┌─────────────────────┐             │
│  │   MainBrain         │    │     HrBrain         │             │
│  │   董事长数字员工      │    │     HR数字员工       │             │
│  └─────────────────────┘    └─────────────────────┘             │
│                                                                 │
│  【使用飞书】- user_access_token（员工自己身份）                  │
│  ══════════════════════════════════════════════                  │
│  ┌─────────────────────────────────────────────────┐             │
│  │              OAuth 授权流程                       │             │
│  │  真实员工/数字员工 → OAuth登录 → 以自己身份操作   │             │
│  └─────────────────────────────────────────────────┘             │
│             │                                                   │
│             ▼                                                   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐               │
│  │TechBrain│ │FinBrain │ │ CsBrain │ │AdminBrain│ ...           │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘               │
│                                                                 │
│  【优势】                                                        │
│  ✅ 管理操作由机器人执行，权限清晰                                │
│  ✅ 员工操作以自己身份，审计追溯准确                              │
│  ✅ 只需2个管理者机器人，架构简洁                                 │
│  ✅ 符合"谁操作谁负责"的合规原则                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 机器人配置详情

| 机器人名称 | App ID | 绑定数字员工 | 权限范围 | 用途 |
|-----------|--------|-------------|----------|------|
| **ChairmanBot** | cli_chairman | MainBrain | 全部权限 | 董事长专属，管理组织架构、审批流程 |
| **HrBot** | cli_hr | HrBrain | 通讯录+部门管理 | HR专属，管理员工和部门 |

### 3.3 员工 OAuth 授权模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| **真实员工授权** | 员工通过 OAuth 登录，获取 user_access_token | 员工日常操作 |
| **数字员工绑定** | 数字员工绑定真实员工账号，使用其授权 | AI 辅助操作 |

### 3.4 操作身份对照表

| 操作类型 | 执行身份 | Token 类型 | 审计追溯 |
|---------|---------|-----------|----------|
| 创建部门 | 机器人 | tenant_access_token | 追溯到机器人 |
| 员工入职 | 机器人 | tenant_access_token | 追溯到机器人 |
| 发送消息 | 员工自己 | user_access_token | 追溯到员工 |
| 查看日程 | 员工自己 | user_access_token | 追溯到员工 |
| 编辑文档 | 员工自己 | user_access_token | 追溯到员工 |
| 发起审批 | 机器人或员工 | 视场景 | 视场景 |
| 审批操作 | 审批人自己 | user_access_token | 追溯到审批人 |

---

## 四、飞书 API 权限范围 (Scope) 列表

### 4.1 董事长机器人权限范围

| Scope ID | 权限名称 | 说明 | 用途 |
|----------|---------|------|------|
| `contact:user.base:readonly` | 获取用户基本信息 | 获取用户 ID、名称、头像等 | 用户查询 |
| `contact:user:readonly` | 获取用户详细信息 | 获取用户邮箱、手机号、部门等 | 用户管理 |
| `contact:user` | 用户信息管理 | 创建、更新、删除用户 | 用户管理 |
| `contact:department.base:readonly` | 获取部门基本信息 | 获取部门 ID、名称等 | 部门查询 |
| `contact:department:readonly` | 获取部门详细信息 | 获取部门层级、成员等 | 部门管理 |
| `contact:department` | 部门管理 | 创建、更新、删除部门 | 部门管理 |
| `contact:group:readonly` | 获取群组信息 | 获取群组列表和详情 | 群组查询 |
| `contact:group` | 群组管理 | 创建、更新、解散群组 | 群组管理 |
| `im:message` | 消息管理 | 发送、撤回消息 | 消息通知 |
| `im:message:readonly` | 获取消息 | 读取消息内容 | 消息查询 |
| `im:chat` | 群聊管理 | 创建、更新群聊 | 群聊管理 |
| `approval:approval` | 审批管理 | 创建、更新审批定义 | 审批管理 |
| `approval:instance:readonly` | 获取审批实例 | 查询审批状态 | 审批查询 |
| `approval:instance` | 审批实例管理 | 创建、取消审批 | 审批发起 |
| `calendar:calendar:readonly` | 获取日历 | 查询日历信息 | 日程查询 |
| `calendar:calendar` | 日历管理 | 创建、更新日程 | 日程管理 |
| `drive:drive:readonly` | 获取云文档 | 查询文档信息 | 文档查询 |
| `drive:drive` | 云文档管理 | 创建、更新、删除文档 | 文档管理 |
| `wiki:wiki:readonly` | 获取知识库 | 查询知识库内容 | 知识库查询 |
| `wiki:wiki` | 知识库管理 | 创建、更新知识库 | 知识库管理 |
| `bitable:bitable:readonly` | 获取多维表格 | 查询多维表格数据 | 表格查询 |
| `bitable:bitable` | 多维表格管理 | 创建、更新表格 | 表格管理 |
| `task:task:readonly` | 获取任务 | 查询任务信息 | 任务查询 |
| `task:task` | 任务管理 | 创建、更新任务 | 任务管理 |
| `vc:room:readonly` | 获取会议室 | 查询会议室信息 | 会议查询 |
| `vc:room` | 会议室管理 | 预定会议室 | 会议管理 |
| `mail:mail:readonly` | 获取邮件 | 查询邮件信息 | 邮件查询 |
| `mail:mail` | 邮件管理 | 发送邮件 | 邮件发送 |
| `docx:docx:readonly` | 获取文档 | 查询文档内容 | 文档查询 |
| `docx:docx` | 文档管理 | 创建、编辑文档 | 文档编辑 |

### 4.2 HR机器人权限范围

| Scope ID | 权限名称 | 说明 | 用途 |
|----------|---------|------|------|
| `contact:user.base:readonly` | 获取用户基本信息 | 获取用户 ID、名称、头像等 | 用户查询 |
| `contact:user:readonly` | 获取用户详细信息 | 获取用户邮箱、手机号、部门等 | 用户管理 |
| `contact:user` | 用户信息管理 | 创建、更新用户 | 员工入职/转正 |
| `contact:department.base:readonly` | 获取部门基本信息 | 获取部门 ID、名称等 | 部门查询 |
| `contact:department:readonly` | 获取部门详细信息 | 获取部门层级、成员等 | 部门管理 |
| `contact:department` | 部门管理 | 创建、更新部门 | 组织架构调整 |
| `im:message` | 消息管理 | 发送消息 | 入职通知等 |
| `im:message:readonly` | 获取消息 | 读取消息内容 | 消息查询 |

### 4.3 员工 OAuth 授权权限范围

员工通过 OAuth 授权后，以自己身份操作，权限由员工自己决定：

| Scope ID | 权限名称 | 说明 | 用途 |
|----------|---------|------|------|
| `contact:user.base:readonly` | 获取用户基本信息 | 获取同事信息 | 同事查询 |
| `contact:department.base:readonly` | 获取部门基本信息 | 获取部门信息 | 部门查询 |
| `im:message` | 消息管理 | 发送消息 | 工作沟通 |
| `im:message:readonly` | 获取消息 | 读取消息内容 | 消息查询 |
| `calendar:calendar` | 日历管理 | 管理自己的日程 | 日程安排 |
| `calendar:calendar:readonly` | 获取日历 | 查看日程 | 日程查看 |
| `docx:document` | 文档管理 | 编辑文档 | 文档协作 |
| `drive:drive:readonly` | 获取云文档 | 查看文档 | 文档查看 |

> **注意**: 员工 OAuth 授权的权限范围由员工在授权时确认，遵循最小权限原则。

---

## 五、飞书服务端 API 列表

### 5.1 通讯录 API

| API 路径 | 方法 | 功能 | 权限要求 |
|---------|------|------|----------|
| `/open-apis/contact/v3/users` | GET | 获取用户列表 | `contact:user:readonly` |
| `/open-apis/contact/v3/users/{user_id}` | GET | 获取用户信息 | `contact:user.base:readonly` |
| `/open-apis/contact/v3/users` | POST | 创建用户 | `contact:user` |
| `/open-apis/contact/v3/users/{user_id}` | PUT | 更新用户信息 | `contact:user` |
| `/open-apis/contact/v3/users/{user_id}` | DELETE | 删除用户 | `contact:user` |
| `/open-apis/contact/v3/users/batch_get_id` | POST | 批量获取用户ID | `contact:user.base:readonly` |
| `/open-apis/contact/v3/users/find_by_department` | GET | 按部门获取用户 | `contact:user:readonly` |
| `/open-apis/contact/v3/departments` | GET | 获取部门列表 | `contact:department:readonly` |
| `/open-apis/contact/v3/departments/{department_id}` | GET | 获取部门信息 | `contact:department.base:readonly` |
| `/open-apis/contact/v3/departments` | POST | 创建部门 | `contact:department` |
| `/open-apis/contact/v3/departments/{department_id}` | PUT | 更新部门信息 | `contact:department` |
| `/open-apis/contact/v3/departments/{department_id}` | DELETE | 删除部门 | `contact:department` |
| `/open-apis/contact/v3/departments/{department_id}/children` | GET | 获取子部门列表 | `contact:department:readonly` |
| `/open-apis/contact/v3/groups` | GET | 获取群组列表 | `contact:group:readonly` |
| `/open-apis/contact/v3/groups` | POST | 创建群组 | `contact:group` |

### 5.2 消息 API

| API 路径 | 方法 | 功能 | 权限要求 |
|---------|------|------|----------|
| `/open-apis/im/v1/messages` | POST | 发送消息 | `im:message` |
| `/open-apis/im/v1/messages/{message_id}` | GET | 获取消息内容 | `im:message:readonly` |
| `/open-apis/im/v1/messages/{message_id}` | DELETE | 撤回消息 | `im:message` |
| `/open-apis/im/v1/messages/{message_id}/read_status` | GET | 获取消息已读状态 | `im:message:readonly` |
| `/open-apis/im/v1/chats` | POST | 创建群聊 | `im:chat` |
| `/open-apis/im/v1/chats/{chat_id}` | GET | 获取群聊信息 | `im:chat` |
| `/open-apis/im/v1/chats/{chat_id}` | PUT | 更新群聊信息 | `im:chat` |
| `/open-apis/im/v1/chats/{chat_id}/members` | POST | 添加群成员 | `im:chat` |
| `/open-apis/im/v1/chats/{chat_id}/members` | DELETE | 移除群成员 | `im:chat` |

### 5.3 审批 API

| API 路径 | 方法 | 功能 | 权限要求 |
|---------|------|------|----------|
| `/open-apis/approval/v4/approvals` | GET | 获取审批定义列表 | `approval:approval:readonly` |
| `/open-apis/approval/v4/approvals` | POST | 创建审批定义 | `approval:approval` |
| `/open-apis/approval/v4/instances` | POST | 创建审批实例 | `approval:instance` |
| `/open-apis/approval/v4/instances/{instance_id}` | GET | 获取审批实例详情 | `approval:instance:readonly` |
| `/open-apis/approval/v4/instances/{instance_id}/cancel` | POST | 取消审批实例 | `approval:instance` |
| `/open-apis/approval/v4/instances/{instance_id}/approve` | POST | 审批同意（需用户授权） | 用户授权 |
| `/open-apis/approval/v4/instances/{instance_id}/reject` | POST | 审批拒绝（需用户授权） | 用户授权 |

### 5.4 日历 API

| API 路径 | 方法 | 功能 | 权限要求 |
|---------|------|------|----------|
| `/open-apis/calendar/v4/calendars` | GET | 获取日历列表 | `calendar:calendar:readonly` |
| `/open-apis/calendar/v4/calendars/{calendar_id}/events` | GET | 获取日程列表 | `calendar:calendar:readonly` |
| `/open-apis/calendar/v4/calendars/{calendar_id}/events` | POST | 创建日程 | `calendar:calendar` |
| `/open-apis/calendar/v4/calendars/{calendar_id}/events/{event_id}` | PUT | 更新日程 | `calendar:calendar` |
| `/open-apis/calendar/v4/calendars/{calendar_id}/events/{event_id}` | DELETE | 删除日程 | `calendar:calendar` |

### 5.5 云文档 API

| API 路径 | 方法 | 功能 | 权限要求 |
|---------|------|------|----------|
| `/open-apis/drive/v1/files` | GET | 获取文件列表 | `drive:drive:readonly` |
| `/open-apis/drive/v1/files` | POST | 创建文件 | `drive:drive` |
| `/open-apis/drive/v1/files/{file_token}` | GET | 获取文件信息 | `drive:drive:readonly` |
| `/open-apis/drive/v1/files/{file_token}` | DELETE | 删除文件 | `drive:drive` |
| `/open-apis/drive/v1/files/upload_all` | POST | 上传文件 | `drive:drive` |
| `/open-apis/drive/v1/files/{file_token}/download` | GET | 下载文件 | `drive:drive:readonly` |

### 5.6 多维表格 API

| API 路径 | 方法 | 功能 | 权限要求 |
|---------|------|------|----------|
| `/open-apis/bitable/v1/apps` | POST | 创建多维表格 | `bitable:bitable` |
| `/open-apis/bitable/v1/apps/{app_token}` | GET | 获取多维表格信息 | `bitable:bitable:readonly` |
| `/open-apis/bitable/v1/apps/{app_token}/tables` | GET | 获取数据表列表 | `bitable:bitable:readonly` |
| `/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records` | GET | 获取记录列表 | `bitable:bitable:readonly` |
| `/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records` | POST | 创建记录 | `bitable:bitable` |
| `/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records/{record_id}` | PUT | 更新记录 | `bitable:bitable` |
| `/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records/{record_id}` | DELETE | 删除记录 | `bitable:bitable` |

### 5.7 任务 API

| API 路径 | 方法 | 功能 | 权限要求 |
|---------|------|------|----------|
| `/open-apis/task/v1/tasks` | GET | 获取任务列表 | `task:task:readonly` |
| `/open-apis/task/v1/tasks` | POST | 创建任务 | `task:task` |
| `/open-apis/task/v1/tasks/{task_id}` | GET | 获取任务详情 | `task:task:readonly` |
| `/open-apis/task/v1/tasks/{task_id}` | PUT | 更新任务 | `task:task` |
| `/open-apis/task/v1/tasks/{task_id}` | DELETE | 删除任务 | `task:task` |

---

## 六、董事长飞书工具完整功能

### 6.1 ChairmanFeishuTool 支持的操作

| 操作 | API 路径 | 权限要求 | 状态 |
|------|---------|----------|------|
| **消息管理** ||||
| `send_message` | POST /open-apis/im/v1/messages | `im:message` | ✅ 已实现 |
| `send_card` | POST /open-apis/im/v1/messages | `im:message` | ✅ 已实现 |
| `get_message` | GET /open-apis/im/v1/messages/{id} | `im:message:readonly` | ✅ 已实现 |
| `recall_message` | DELETE /open-apis/im/v1/messages/{id} | `im:message` | ✅ 已实现 |
| **用户管理** ||||
| `get_user` | GET /open-apis/contact/v3/users/{id} | `contact:user.base:readonly` | ✅ 已实现 |
| `get_user_list` | GET /open-apis/contact/v3/users | `contact:user:readonly` | ✅ 已实现 |
| `create_user` | POST /open-apis/contact/v3/users | `contact:user` | ✅ 已实现 |
| `update_user` | PUT /open-apis/contact/v3/users/{id} | `contact:user` | ✅ 已实现 |
| `delete_user` | DELETE /open-apis/contact/v3/users/{id} | `contact:user` | ✅ 已实现 |
| **部门管理** ||||
| `get_department` | GET /open-apis/contact/v3/departments/{id} | `contact:department.base:readonly` | ✅ 已实现 |
| `get_department_list` | GET /open-apis/contact/v3/departments | `contact:department:readonly` | ✅ 已实现 |
| `create_department` | POST /open-apis/contact/v3/departments | `contact:department` | ✅ 已实现 |
| `update_department` | PUT /open-apis/contact/v3/departments/{id} | `contact:department` | ✅ 已实现 |
| `delete_department` | DELETE /open-apis/contact/v3/departments/{id} | `contact:department` | ✅ 已实现 |
| **审批管理** ||||
| `create_approval` | POST /open-apis/approval/v4/instances | `approval:instance` | ✅ 已实现 |
| `get_approval` | GET /open-apis/approval/v4/instances/{id} | `approval:instance:readonly` | ✅ 已实现 |
| `cancel_approval` | POST /open-apis/approval/v4/instances/{id}/cancel | `approval:instance` | ✅ 已实现 |
| **文件管理** ||||
| `upload_file` | POST /open-apis/drive/v1/files/upload_all | `drive:drive` | ✅ 已实现 |
| `get_file` | GET /open-apis/drive/v1/files/{token} | `drive:drive:readonly` | 📝 待实现 |
| `download_file` | GET /open-apis/drive/v1/files/{token}/download | `drive:drive:readonly` | 📝 待实现 |
| **日历管理** ||||
| `get_calendar_list` | GET /open-apis/calendar/v4/calendars | `calendar:calendar:readonly` | 📝 待实现 |
| `create_event` | POST /open-apis/calendar/v4/calendars/{id}/events | `calendar:calendar` | 📝 待实现 |
| `get_event_list` | GET /open-apis/calendar/v4/calendars/{id}/events | `calendar:calendar:readonly` | 📝 待实现 |
| **任务管理** ||||
| `create_task` | POST /open-apis/task/v1/tasks | `task:task` | 📝 待实现 |
| `get_task_list` | GET /open-apis/task/v1/tasks | `task:task:readonly` | 📝 待实现 |
| **群聊管理** ||||
| `create_chat` | POST /open-apis/im/v1/chats | `im:chat` | 📝 待实现 |
| `get_chat` | GET /open-apis/im/v1/chats/{id} | `im:chat` | 📝 待实现 |
| `add_chat_members` | POST /open-apis/im/v1/chats/{id}/members | `im:chat` | 📝 待实现 |

### 6.2 ChairmanFeishuTool Schema 定义

```java
ToolSchema.builder()
    .name("chairman_feishu")
    .description("董事长专属飞书管理工具，具备全部权限，可进行组织架构管理、人员管理、审批管理等所有操作")
    .parameter("action", "string", 
        "操作类型: " +
        "send_message(发送消息), " +
        "send_card(发送卡片), " +
        "get_message(获取消息), " +
        "recall_message(撤回消息), " +
        "get_user(获取用户), " +
        "get_user_list(获取用户列表), " +
        "create_user(创建用户), " +
        "update_user(更新用户), " +
        "delete_user(删除用户), " +
        "get_department(获取部门), " +
        "get_department_list(获取部门列表), " +
        "create_department(创建部门), " +
        "update_department(更新部门), " +
        "delete_department(删除部门), " +
        "create_approval(发起审批), " +
        "get_approval(查询审批), " +
        "cancel_approval(取消审批), " +
        "upload_file(上传文件), " +
        "get_file(获取文件), " +
        "download_file(下载文件), " +
        "get_calendar_list(获取日历列表), " +
        "create_event(创建日程), " +
        "get_event_list(获取日程列表), " +
        "create_task(创建任务), " +
        "get_task_list(获取任务列表), " +
        "create_chat(创建群聊), " +
        "get_chat(获取群聊), " +
        "add_chat_members(添加群成员), " +
        "get_token_info(获取令牌信息)", 
        true)
    // ... 其他参数
    .build();
```

---

## 七、数字员工与飞书身份对应关系

### 7.1 数据库表设计

```sql
-- 数字员工表（扩展现有结构）
CREATE TABLE digital_employees (
    id VARCHAR(36) PRIMARY KEY,
    neuron_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(50),
    access_level VARCHAR(20) DEFAULT 'DEPARTMENT',
    
    -- 飞书绑定（管理者模式）
    feishu_bot_id VARCHAR(36),          -- 绑定管理者机器人（仅董事长/HR）
    
    -- 飞书绑定（员工模式）
    feishu_user_id VARCHAR(100),        -- 绑定真实员工账号
    feishu_open_id VARCHAR(100),        -- 员工的 open_id
    oauth_refresh_token TEXT,           -- OAuth 刷新令牌（加密存储）
    oauth_scope JSONB,                  -- 已授权的权限范围
    oauth_expires_at TIMESTAMP,         -- 令牌过期时间
    
    -- 权限配置
    allowed_actions JSONB,
    approval_codes JSONB,
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (feishu_bot_id) REFERENCES feishu_bot_config(bot_id)
);

-- 飞书机器人配置表（仅管理者机器人）
CREATE TABLE feishu_bot_config (
    bot_id VARCHAR(36) PRIMARY KEY,
    bot_name VARCHAR(100) NOT NULL,
    
    -- 飞书应用信息
    app_id VARCHAR(100) UNIQUE NOT NULL,
    app_secret_encrypted TEXT NOT NULL,
    
    -- 权限配置
    permission_scope JSONB,
    allowed_actions JSONB,
    
    -- 角色类型
    role_type VARCHAR(20) NOT NULL,     -- 'CHAIRMAN' 或 'HR'
    
    -- 状态
    is_active BOOLEAN DEFAULT true,
    token_expires_at TIMESTAMP,
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 飞书操作审计日志表
CREATE TABLE feishu_operation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- 操作来源
    operation_mode VARCHAR(20) NOT NULL,    -- 'BOT' 或 'OAUTH'
    bot_id VARCHAR(36),                     -- 机器人ID（BOT模式）
    user_open_id VARCHAR(100),              -- 用户open_id（OAUTH模式）
    digital_employee_id VARCHAR(36),
    
    -- 操作信息
    operation_type VARCHAR(50) NOT NULL,
    api_endpoint VARCHAR(200),
    request_params JSONB,
    response_data JSONB,
    
    -- 结果
    success BOOLEAN,
    error_code VARCHAR(50),
    error_message TEXT,
    
    -- 合规关联
    compliance_audit_id UUID,
    
    -- 时间
    operation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 7.2 数字员工绑定模式

| 数字员工 | neuron_id | 绑定模式 | 绑定对象 | 操作身份 |
|---------|-----------|---------|---------|---------|
| MainBrain | main-brain-001 | BOT | ChairmanBot | 机器人身份 |
| HrBrain | hr-brain-001 | BOT | HrBot | 机器人身份 |
| TechBrain | tech-brain-001 | OAUTH | 绑定员工账号 | 员工身份 |
| FinanceBrain | finance-brain-001 | OAUTH | 绑定员工账号 | 员工身份 |
| CsBrain | cs-brain-001 | OAUTH | 绑定员工账号 | 员工身份 |
| AdminBrain | admin-brain-001 | OAUTH | 绑定员工账号 | 员工身份 |

### 7.3 数字员工命名规范

数字员工在飞书中以"听"姓命名，体现感知和洞察能力：

| 数字员工 | 飞书名称 | 含义 | 部门 |
|---------|---------|------|------|
| MainBrain | **听风** | 听风者，感知全局 | 董事长办公室 |
| HrBrain | **听雨** | 听雨声，细腻关怀 | 人力资源部 |
| TechBrain | **听云** | 听云端，技术洞察 | 技术部 |
| FinanceBrain | **听涛** | 听波涛，把握趋势 | 财务部 |
| CsBrain | **听语** | 听心声，服务客户 | 客服部 |
| AdminBrain | **听墨** | 听笔墨，文思敏捷 | 行政部 |

### 7.4 数字员工绑定方案（测试结论）

**测试结果**：✅ **成功！可以使用虚拟手机号和邮箱创建用户**

**测试详情**：
```
创建用户: 听风
手机号: +8613800138001 (虚拟)
邮箱: tingfeng@digital-employee.local (虚拟)
返回: open_id=ou_8f32e722d4a6ee2c67dc782fd580486e
状态: is_unjoin=true (待激活)
```

**关键发现**：
1. 飞书允许使用虚拟手机号和邮箱创建用户
2. 创建后用户状态为 `is_unjoin=true`（待激活）
3. 必填字段：`name`, `employee_type`, `department_ids`
4. 可选字段：`mobile`, `email`, `employee_no`, `gender`

**最终方案**：创建虚拟员工账号

```
┌─────────────────────────────────────────────────────────────────┐
│                    数字员工创建方案                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【创建虚拟员工账号】                                            │
│  ├── 手机号: +861380013800X (虚拟，不实际使用)                   │
│  ├── 邮箱: xxx@digital-employee.local (虚拟域名)                │
│  ├── 状态: is_unjoin=true (无需激活，直接使用)                   │
│  └── 用途: 数字员工身份标识                                      │
│                                                                 │
│  【管理者数字员工】- 机器人模式                                   │
│  ├── MainBrain (听风) → 绑定 ChairmanBot → 机器人身份操作       │
│  └── HrBrain (听雨) → 绑定 HrBot → 机器人身份操作               │
│                                                                 │
│  【普通数字员工】- 虚拟员工账号                                   │
│  ├── TechBrain (听云) → 虚拟员工账号 → API 操作                 │
│  ├── FinanceBrain (听涛) → 虚拟员工账号 → API 操作              │
│  ├── CsBrain (听语) → 虚拟员工账号 → API 操作                   │
│  └── AdminBrain (听墨) → 虚拟员工账号 → API 操作                │
│                                                                 │
│  【优势】                                                        │
│  ✅ 数字员工有独立身份，便于审计追溯                              │
│  ✅ 无需绑定真实员工，避免权限冲突                               │
│  ✅ 虚拟联系方式不占用真实资源                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.5 数字员工创建参数

| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `name` | string | ✅ | 用户姓名 | 听风 |
| `department_ids` | array | ✅ | 部门ID列表 | ["0"] |
| `employee_type` | int | ✅ | 员工类型 | 1=正式员工 |
| `mobile` | string | ❌ | 手机号 | +8613800138001 |
| `email` | string | ❌ | 邮箱 | tingfeng@digital-employee.local |
| `employee_no` | string | ❌ | 工号 | DE-001 |
| `gender` | int | ❌ | 性别 | 1=男, 2=女 |

### 7.6 数字员工激活测试

**测试时间**: 2026-03-30

**测试结果**:

| 操作 | 结果 | 说明 |
|------|------|------|
| 创建虚拟员工 | ✅ 成功 | 使用虚拟手机号和邮箱 |
| 更新邮箱为真实邮箱 | ✅ 成功 | tingfeng@hengebiotech.com |
| 发送激活邀请 | ❌ API 404 | 需要通过飞书管理后台操作 |

**已创建的数字员工**:

| 数字员工 | open_id | user_id | 邮箱 | 状态 |
|---------|---------|---------|------|------|
| 听风 | ou_8f32e722d4a6ee2c67dc782fd580486e | 44676455 | tingfeng@hengebiotech.com | 待激活 |
| 听雨 | ou_7263def76e22eaad03a7b9412363b7a4 | da7bbaee | tingyu@digital-employee.local | 待激活 |
| 听云 | ou_c3f9e7a187b40001d5227b25984b1b13 | eb78be33 | tingyun@digital-employee.local | 待激活 |
| 听涛 | ou_3a2b0713c360e9f0a7442218d13f7b75 | 8a3c437a | tingtao@digital-employee.local | 待激活 |
| 听语 | ou_b40de12395ea8e6785f7a1e910ff10f9 | a28e988b | tingyu2@digital-employee.local | 待激活 |
| 听墨 | ou_325a7a8c3958f629562b530b268cdf05 | 4369c86f | tingmo@digital-employee.local | 待激活 |

**激活方式**:
1. **管理后台激活**: 登录飞书管理后台，找到待激活用户，发送邀请邮件
2. **用户自助激活**: 用户通过邮件链接完成激活

**注意**: 虚拟员工激活后，可以以该员工身份进行 OAuth 授权操作。

---

## 八、审批流程集成分析

### 8.1 审批操作身份限制

**关键限制**：飞书审批的"审批操作"（同意/拒绝）必须由**真实用户**在飞书客户端完成，API **不支持**代为审批。

```
┌─────────────────────────────────────────────────────────────────┐
│                   审批操作身份限制                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ API 可以做:                                                  │
│     - 创建审批实例 (发起审批)                                    │
│     - 查询审批状态                                               │
│     - 取消审批实例                                               │
│                                                                 │
│  ❌ API 不能做:                                                  │
│     - 代审批人进行"同意"操作                                     │
│     - 代审批人进行"拒绝"操作                                     │
│     - 修改审批流程                                               │
│                                                                 │
│  原因: 审批操作需要审批人的身份验证                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 推荐方案：混合模式

```
数字员工发起审批 → 真实员工审批 → 回调触发后续动作
```

**流程设计**：

1. **数字员工发起审批**
   - 使用绑定的飞书机器人调用 API
   - 记录发起人身份（数字员工 neuron_id）
   - 记录审批内容和决策依据

2. **真实员工审批**
   - 审批人在飞书客户端操作
   - 飞书记录审批人身份和时间

3. **回调处理**
   - 接收飞书审批状态变更事件
   - 更新业务系统状态
   - 触发数字员工后续动作
   - 记录完整审计日志

---

## 九、审计追溯设计

### 9.1 审计日志关联

```java
@Service
public class FeishuAuditService {
    
    private final ComplianceAuditService complianceAuditService;
    private final FeishuOperationLogRepository operationLogRepository;
    
    public void logFeishuOperation(FeishuOperation operation) {
        // 1. 记录飞书操作日志
        FeishuOperationLog log = FeishuOperationLog.builder()
            .botId(operation.getBotId())
            .digitalEmployeeId(operation.getDigitalEmployeeId())
            .operationType(operation.getType())
            .apiEndpoint(operation.getEndpoint())
            .requestParams(operation.getParams())
            .responseData(operation.getResponse())
            .success(operation.isSuccess())
            .build();
        operationLogRepository.save(log);
        
        // 2. 关联合规审计日志
        AuditEvent auditEvent = AuditEvent.builder()
            .eventType(AuditEventType.FEISHU_OPERATION)
            .employeeId(operation.getDigitalEmployeeId())
            .resourceType(operation.getResourceType())
            .resourceId(operation.getResourceId())
            .action(operation.getType())
            .actionDetail(Map.of(
                "botId", operation.getBotId(),
                "appId", operation.getAppId(),
                "apiEndpoint", operation.getEndpoint()
            ))
            .isAiDecision(true)
            .decisionBasis(operation.getDecisionBasis())
            .decisionModel(operation.getModel())
            .build();
        
        UUID auditId = complianceAuditService.logAuditEvent(auditEvent);
        
        // 3. 建立关联
        log.setComplianceAuditId(auditId);
        operationLogRepository.save(log);
    }
}
```

---

## 十、当前代码实现分析

### 10.1 已实现的飞书工具硬区分

```
living-agent-core/src/main/java/com/livingagent/core/tool/impl/enterprise/
├── ChairmanFeishuTool.java    ← 董事长专用（全部权限）
├── HrFeishuTool.java          ← HR专用（通讯录+部门管理）
└── EmployeeFeishuTool.java    ← 普通员工（基础消息+查询）
```

### 10.2 权限对比矩阵

| 操作 | ChairmanFeishuTool | HrFeishuTool | EmployeeFeishuTool |
|------|-------------------|--------------|-------------------|
| `send_message` | ✅ | ✅ | ✅ |
| `get_user` | ✅ | ✅ | ✅ |
| `get_department` | ✅ | ✅ | ✅ |
| `get_user_list` | ✅ | ✅ | ❌ |
| `create_department` | ✅ | ✅ | ❌ |
| `update_department` | ✅ | ✅ | ❌ |
| `delete_department` | ✅ | ❌ | ❌ |
| `create_user` | ✅ | ✅ | ❌ |
| `update_user` | ✅ | ✅ | ❌ |
| `delete_user` | ✅ | ❌ | ❌ |
| `create_approval` | ✅ | ❌ | ❌ |
| `cancel_approval` | ✅ | ❌ | ❌ |
| `upload_file` | ✅ | ❌ | ❌ |
| `get_calendar_list` | ✅ | ❌ | ❌ |
| `create_event` | ✅ | ❌ | ❌ |
| `create_task` | ✅ | ❌ | ❌ |
| `create_chat` | ✅ | ❌ | ❌ |

### 10.3 遗留文件处理

| 文件 | 状态 | 建议 |
|------|------|------|
| `FeishuTool.java` | ⚠️ 遗留 | 删除，已被 `ChairmanFeishuTool` 替代 |
| `FeishuSyncAdapter.java` | ⚠️ 遗留 | 删除或重命名为 `ChairmanFeishuSyncAdapter` |

### 10.4 配置文件

`application.yml` 已更新为分层配置：

```yaml
feishu:
  enabled: ${FEISHU_ENABLED:true}
  webhook-key: ${FEISHU_WEBHOOK_KEY:}
  chairman:
    app-id: ${FEISHU_CHAIRMAN_APP_ID:cli_a920321f3b7a5cc1}
    app-secret: ${FEISHU_CHAIRMAN_APP_SECRET:gmU0opRuS3Aps30BWR84ghkv7ELrkncG}
  hr:
    app-id: ${FEISHU_HR_APP_ID:}
    app-secret: ${FEISHU_HR_APP_SECRET:}
  employee:
    app-id: ${FEISHU_EMPLOYEE_APP_ID:}
    app-secret: ${FEISHU_EMPLOYEE_APP_SECRET:}
```

### 10.5 合规检查清单

| 合规要求 | 当前状态 | 说明 |
|---------|---------|------|
| **审计追溯** | ⚠️ 部分 | 工具已区分，审计日志关联待完善 |
| **AI决策可解释性** | ⚠️ 部分 | 需记录决策依据 |
| **数据变更历史** | ❌ 未实现 | 需记录前后快照 |
| **权限隔离** | ✅ 已实现 | 三层工具硬区分 |
| **敏感数据保护** | ⚠️ 部分 | 配置已分离，加密存储待实现 |

---

## 十一、结论

### 11.1 可行性评估

| 功能 | 可行性 | 备注 |
|------|--------|------|
| 创建部门 | ✅ 完全可行 | 已测试成功 |
| 发送消息 | ✅ 完全可行 | 已实现 |
| 发起审批 | ✅ 完全可行 | 需要审批定义 |
| 查询审批状态 | ✅ 完全可行 | 已实现 |
| 代为审批 | ❌ 不可行 | 飞书限制 |
| 多机器人配置 | ✅ 可行 | 需要数据库支持 |
| 审计追溯 | ✅ 可行 | 需关联合规系统 |

### 11.2 核心结论

1. **已确认多机器人架构**：每个角色使用独立的飞书机器人应用

2. **数字员工与飞书机器人对应**：通过 `feishu_bot_id` 绑定，实现身份追溯

3. **合规要求必须满足**：所有飞书操作必须记录审计日志并关联合规系统

4. **审批发起可行，审批操作受限**：API 可以创建审批实例，但审批操作必须由真实用户完成

---

## 十三、lark-cli-main 项目分析

### 13.1 项目概述

`lark-cli-main` 是一个基于 Go 语言开发的飞书/Lark CLI 工具，专门为 AI 助手（如 Claude Code）设计，用于与飞书开放平台交互。

**项目位置**: `f:\SoarCloudAI\docker\lark-cli-main`

**核心特点**:
- 🎯 **为 AI 助手优化**: 输出精简 JSON，Token 消耗低
- 🔐 **双 Token 模式**: 支持 `user_access_token` 和 `tenant_access_token`
- 📦 **模块化设计**: 按功能分组（calendar, contacts, messages 等）
- 🌍 **多区域支持**: 同时支持飞书和 Lark 国际版

### 13.2 技术架构

```
lark-cli-main/
├── internal/
│   ├── api/           # API 客户端实现
│   │   ├── client.go      # 核心 HTTP 客户端
│   │   ├── calendar.go    # 日历 API
│   │   ├── contacts.go    # 通讯录 API
│   │   ├── messages.go    # 消息 API
│   │   ├── documents.go   # 文档 API
│   │   ├── bitable.go     # 多维表格 API
│   │   ├── sheets.go      # 电子表格 API
│   │   ├── wiki.go        # 知识库 API
│   │   └── minutes.go     # 会议记录 API
│   ├── auth/          # OAuth 认证
│   │   ├── oauth.go       # OAuth 流程
│   │   ├── token.go       # Token 管理
│   │   └── server.go      # 回调服务器
│   ├── cmd/           # CLI 命令
│   └── scopes/        # 权限范围定义
└── skills/            # AI 助手技能定义
```

### 13.3 功能模块对比

| 模块 | lark-cli-main | ChairmanFeishuTool | 说明 |
|------|---------------|-------------------|------|
| **日历管理** | ✅ 完整支持 | 📝 部分实现 | lark-cli 支持冲突检测、共同空闲时间 |
| **通讯录** | ✅ 完整支持 | ✅ 已实现 | 功能相当 |
| **消息管理** | ✅ 完整支持 | ✅ 已实现 | lark-cli 支持回复、表情、资源下载 |
| **审批管理** | ❌ 不支持 | ✅ 已实现 | 本项目独有 |
| **文档管理** | ✅ 完整支持 | 📝 部分实现 | lark-cli 支持创建、编辑、评论 |
| **多维表格** | ✅ 完整支持 | ❌ 未实现 | lark-cli 独有 |
| **电子表格** | ✅ 完整支持 | ❌ 未实现 | lark-cli 独有 |
| **知识库** | ✅ 完整支持 | ❌ 未实现 | lark-cli 独有 |
| **邮件** | ✅ 支持 | ❌ 未实现 | lark-cli 支持 IMAP 邮件 |
| **会议记录** | ✅ 支持 | ❌ 未实现 | lark-cli 支持妙记 |

### 13.4 认证模式对比

#### lark-cli-main 认证模式

```go
// 双 Token 模式
type Client struct {
    httpClient *http.Client
}

// 用户访问令牌 (user_access_token)
func (c *Client) doRequest(method, path string, ...) {
    token := auth.GetTokenStore().GetAccessToken()
    req.Header.Set("Authorization", "Bearer "+token)
}

// 租户访问令牌 (tenant_access_token)
func (c *Client) doRequestWithTenantToken(method, path string, ...) {
    token := auth.GetTenantTokenStore().GetAccessToken()
    req.Header.Set("Authorization", "Bearer "+token)
}
```

#### ChairmanFeishuTool 认证模式

```java
// 仅租户访问令牌 (tenant_access_token)
public String getTenantAccessToken() {
    String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    // ...
    return token;
}
```

**关键差异**:

| 特性 | lark-cli-main | ChairmanFeishuTool |
|------|---------------|-------------------|
| **user_access_token** | ✅ 支持 | ❌ 不支持 |
| **tenant_access_token** | ✅ 支持 | ✅ 支持 |
| **OAuth 用户授权** | ✅ 完整流程 | ❌ 不支持 |
| **Token 自动刷新** | ✅ 支持 | ✅ 支持 |
| **多机器人配置** | ❌ 单配置 | ✅ 多配置 |

### 13.5 OAuth Scope 分组

lark-cli-main 定义了清晰的权限分组：

```go
var Groups = map[string]ScopeGroup{
    "calendar": {
        Scopes: []string{"calendar:calendar", "calendar:calendar:readonly"},
    },
    "contacts": {
        Scopes: []string{
            "contact:contact.base:readonly",
            "contact:department.base:readonly",
            "contact:user:search",
        },
    },
    "documents": {
        Scopes: []string{
            "docx:document:readonly",
            "docx:document",
            "drive:drive:readonly",
            "wiki:wiki:readonly",
        },
    },
    "messages": {
        Scopes: []string{
            "im:message:readonly",
            "im:message",
            "im:message:send_as_bot",
        },
    },
    // ... 更多分组
}
```

### 13.6 与本项目的集成可行性评估

#### 方案一：直接调用 CLI（不推荐）

```bash
# 在 Java 中调用 Go CLI
ProcessBuilder pb = new ProcessBuilder("lark", "cal", "list", "--week");
Process p = pb.start();
String output = new String(p.getInputStream().readAllBytes());
```

**缺点**:
- ❌ 需要额外部署 Go 二进制文件
- ❌ 进程间通信开销大
- ❌ 错误处理复杂
- ❌ 不支持多机器人配置

#### 方案二：参考实现，增强现有工具（推荐）

借鉴 lark-cli-main 的优秀设计，增强 ChairmanFeishuTool：

```
┌─────────────────────────────────────────────────────────────────┐
│              lark-cli-main 可借鉴的设计                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. OAuth 用户授权流程                                           │
│     - 支持 user_access_token，可代表用户执行操作                 │
│     - 适用于需要用户身份的场景（如审批操作）                      │
│                                                                 │
│  2. 日历冲突检测                                                 │
│     - 检测日程重叠                                               │
│     - 计算共同空闲时间                                           │
│                                                                 │
│  3. 消息高级功能                                                 │
│     - 线程回复                                                   │
│     - 表情反应                                                   │
│     - 资源下载                                                   │
│                                                                 │
│  4. 文档操作                                                     │
│     - 创建/编辑文档                                              │
│     - 读取文档评论                                               │
│                                                                 │
│  5. 多维表格/电子表格                                            │
│     - 读取/写入数据                                              │
│     - 适合业务数据管理                                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 方案三：混合架构（可选）

```
┌─────────────────────────────────────────────────────────────────┐
│                    混合架构设计                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Java 服务层 (living-agent-service)                              │
│  ├── ChairmanFeishuTool  ← 核心业务逻辑                          │
│  ├── HrFeishuTool        ← HR 专属操作                           │
│  └── EmployeeFeishuTool  ← 员工基础功能                          │
│                                                                 │
│  Go CLI 层 (lark-cli-main)                                      │
│  ├── 日历冲突检测       ← 复杂算法                               │
│  ├── 共同空闲时间计算   ← 性能敏感                               │
│  └── 文档内容解析       ← 格式复杂                               │
│                                                                 │
│  通信方式: HTTP API 或 gRPC                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 13.7 功能增强建议

基于 lark-cli-main 分析，建议为 ChairmanFeishuTool 增加以下功能：

| 功能 | 优先级 | 说明 |
|------|--------|------|
| **日历冲突检测** | 高 | 避免日程重叠 |
| **共同空闲时间** | 高 | 会议安排优化 |
| **线程回复** | 中 | 消息组织更清晰 |
| **表情反应** | 低 | 增强互动体验 |
| **文档创建/编辑** | 中 | 知识管理需求 |
| **多维表格操作** | 中 | 业务数据管理 |
| **知识库搜索** | 中 | 知识检索需求 |

### 13.8 结论

| 评估维度 | 结论 |
|---------|------|
| **是否替代方案** | ❌ 不建议替代，应作为参考和补充 |
| **可借鉴价值** | ✅ 高，OAuth 流程、日历功能、消息高级功能值得借鉴 |
| **集成可行性** | ✅ 可行，建议采用方案二（参考实现） |
| **优先级** | 中，先完善现有功能，再借鉴 lark-cli-main 扩展 |

**核心建议**:
1. 保持现有的多机器人架构设计
2. 借鉴 lark-cli-main 的 OAuth 用户授权流程，支持 `user_access_token`
3. 参考其日历冲突检测和共同空闲时间计算实现
4. 逐步增加文档、多维表格等高级功能

---

## 十四、参考资料

- [飞书开放平台 - API 权限列表](https://open.feishu.cn/document/server-docs/application-scope/introduction)
- [飞书开放平台 - 服务端 API 列表](https://open.feishu.cn/document/server-docs/api-call-guide/server-api-list)
- [飞书开放平台 - 创建审批实例](https://open.feishu.cn/document/server-docs/approval-v4/instance/create)
- [飞书开放平台 - 审批开发指南](https://open.feishu.cn/document/server-docs/approval-v4/development-guide)
- [21-compliance-optimization.md](./21-compliance-optimization.md) - 企业合规管理优化方案

---

## 十三、版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-03-28 | 初始版本 |
| v1.1 | 2026-03-28 | 添加合规要求分析、审计追溯设计 |
| v2.0 | 2026-03-28 | 确认多机器人方案，移除单机器人方案，补齐董事长飞书功能，添加API权限范围和API列表 |
| v2.1 | 2026-03-30 | 添加 lark-cli-main 项目分析，评估替代集成方案 |
| v3.0 | 2026-03-30 | 优化架构：管理者机器人+员工OAuth授权混合模式，移除员工机器人，添加数字员工命名规范 |
| v3.1 | 2026-03-30 | 数字员工创建测试成功，添加激活测试结果，记录已创建的数字员工信息 |

---

*更新时间: 2026-03-30*
