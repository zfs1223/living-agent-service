# 安全模块

> 版本：2026-05-18 | 路径：living-agent-core/security/

## 访问级别

| 级别 | level值 | 说明 | allowedModels | allowedBrains | canAccessKnowledge |
|------|---------|------|---------------|---------------|-------------------|
| `FULL` | 3 | 完全访问 | 全部 | 全部含 MainBrain | true |
| `DEPARTMENT` | 2 | 本部门 | Qwen3-0.6B, Qwen3.5-2B | 本部门大脑 | true |
| `LIMITED` | 1 | 限制访问 | Qwen3-0.6B, Qwen3.5-2B | AdminBrain, CsBrain | true |
| `CHAT_ONLY` | 0 | 仅闲聊 | Qwen3-0.6B | (空) | false |

## 身份权限矩阵

| 身份 | 级别 | 部门大脑 | 闲聊 |
|------|------|----------|------|
| 董事长 | FULL | 所有 | ✅ |
| 在职员工 | DEPARTMENT | 本部门 | ✅ |
| 试用期 | LIMITED | Admin + Cs | ✅ |
| 访客/离职 | CHAT_ONLY | 无 | ✅ |

## 权限检查流程

```
1. 检查登录状态
   ↓ 未登录 → CHAT_ONLY /ws/public
2. 获取 AccessLevel
   ↓ 级别检查
3. 如果 DEPARTMENT → 验证部门匹配
4. 如果 LIMITED → 验证大脑在白名单
```

## PermissionService 核心逻辑

```java
// PermissionService.java
canAccessBrain(String employeeId, String brainName) {
    1. 获取员工 AccessLevel
    2. FULL → 允许所有大脑
    3. DEPARTMENT → 验证部门匹配
    4. LIMITED → 检查白名单
}

canExecuteTool(String employeeId, String toolName) {
    1. 工具是否需要特殊权限
    2. 验证员工权限等级
}

canUseModel(String employeeId, String modelName) {
    1. 检查模型是否在员工 allowedModels 中
}

// 多因素验证
verifyByPhone(String phone, String verificationCode) → Optional<Employee>
verifyByVoicePrint(String voicePrintId, float[] voiceVector) → Optional<Employee>
verifyByOAuth(String provider, String oauthUserId, String accessToken) → Optional<Employee>

// 访问审计
recordAccess(String employeeId, String resource, String action, boolean granted)
getAccessLogs(String employeeId, int limit) → List<AccessAuditLog>
```

## Sandbox 沙箱执行

```java
// SandboxExecutor.java（统一执行入口）
interface SandboxExecutor {
    <T> ExecutionResult<T> execute(String code, SandboxConfig config, Class<T> resultType);
    <T> ExecutionResult<T> executeFile(Path file, SandboxConfig config, Class<T> resultType);
    boolean isAvailable();
}

record SandboxConfig(
    long timeoutMs,                  // 超时毫秒
    long memoryLimitMB,              // 内存限制 MB
    boolean networkAllowed,          // 是否允许网络
    List<String> allowedPaths,       // 允许的路径
    List<String> deniedPaths,        // 禁止的路径
    Map<String, String> environment, // 环境变量
    boolean allowFileWrite,          // 允许文件写入
    boolean allowFileRead,           // 允许文件读取
    boolean allowProcessExecution,   // 允许进程执行
    String workingDirectory          // 工作目录
) {
    static SandboxConfig defaults() { ... }   // 常规配置
    static SandboxConfig strict() { ... }     // 严格配置
    static SandboxConfig forScript() { ... }  // 脚本配置
}

record ExecutionResult<T>(
    boolean success,
    T result,                       // 泛型结果
    String error,
    long executionTimeMs,
    double memoryUsedMB,
    boolean timedOut,
    boolean memoryExceeded,
    List<String> warnings,
    Map<String, Object> metadata
) {}
```

> **注意**：`SandboxService`（在 `core/sandbox/` 包中）已标记为 `@Deprecated`，优先使用 `SandboxExecutor`。

## 沙箱类型

> **待实现**：`ExecutionEnvironment` 枚举（DOCKER_SANDBOX, LOCAL_RESTRICTED, ARTIFACT_ONLY, HUMAN_REVIEW_REQUIRED）尚未在代码中实现。当前通过 `SandboxConfig` 的布尔标志隐式控制执行环境。

## BashSecurityValidator

```java
// BashSecurityValidator.java — 正则模式匹配（非黑名单）
validate(command) {
    1. SHELL_METACHAR  → 检测 [;&|`$]          → MEDIUM
    2. SUDO            → 检测 \bsudo\b          → CRITICAL
    3. RM_RF           → 检测 \brm\s+(-[a-zA-Z]*)?r → CRITICAL
    4. CMD_SUBSTITUTION → 检测 \$\(             → HIGH
    5. IFS_INJECTION   → 检测 \bIFS\s*=        → HIGH
}
```

## 代码路径

```
security/
├── PermissionService.java           # 权限服务（含访问审计）
├── AccessLevel.java                 # 访问级别（0-3 + allowedModels/allowedBrains）
├── AccessGateService.java           # 访问网关
├── SandboxExecutor.java             # 沙箱执行器（统一入口）
├── BashSecurityValidator.java       # Bash 安全验证（正则模式匹配）
├── auth/
│   ├── UnifiedAuthService.java      # 统一认证
│   └── AuthContext.java             # 认证上下文
└── bash/
    └── BashSecurityValidator.java   # Bash 安全验证实现

core/sandbox/
├── SandboxService.java              # 已 @Deprecated
└── ...

database/entity/
└── (审计日志由 PermissionService 内部 AccessAuditLog 管理)
```

## 快速定位

| 需求 | 文件 |
|------|------|
| 修改权限级别 | `AccessLevel.java` |
| 修改权限检查逻辑 | `PermissionService.java` |
| 修改 Bash 安全规则 | `BashSecurityValidator.java` |
| 修改沙箱配置 | `SandboxExecutor.SandboxConfig` |
| 添加新沙箱类型 | `SandboxExecutor.java` + 实现 |
| 修改访问审计 | `PermissionService.recordAccess/getAccessLogs()` |

---

## 外部服务管理员与自动激活

### 管理员账号体系

主脑（MainBrain）持有所有外部服务的**管理员账号**，负责：
1. 初始化服务配置
2. 为部门大脑和固定数字员工分配专属账号
3. 管理权限回收与审计

管理员账号**仅主脑使用**，不分配给数字员工：

| 服务 | 管理员 | 密码 | 端口 | 用途 |
|------|--------|------|------|------|
| OpenProject | `admin` | `admin123456` | 8386 | 主脑管理项目、分配员工账号 |
| GitLab | `root` | (初始配置) | 8385 | 主脑管理仓库、分配员工账号 |
| Jenkins | `admin` | (初始配置) | 8384 | 主脑管理CI/CD、分配员工账号 |
| Memos | 首个注册用户 | - | 8383 | 主脑管理知识备忘 |

### 主脑账号分配规则

主脑根据岗位和业务需要，为固定数字员工在外部服务中注册**专属账号**，按需分配：

| 外部服务 | 需要账号的岗位 | 不需要账号的岗位 | 分配权限 |
|---------|--------------|----------------|---------|
| OpenProject | 所有部门（项目管理、任务跟踪） | 无 | Developer/Reporter（按岗位） |
| GitLab | 研发部（代码编辑、代码审查） | 财务部、法务部、客服部 | Developer/Reporter |
| Jenkins | 研发部（CI/CD 构建） | 其他部门 | Builder/Viewer |
| Memos | 所有部门（知识记录） | 无 | Editor/Viewer |

### 固定数字员工账号分配

主脑在初始化固定数字员工时，根据其岗位自动在相关外部服务中注册账号：

| 员工 | OpenProject | GitLab | Jenkins | Memos |
|------|------------|--------|---------|-------|
| 代码审查员 | ✅ `{id}-bot` (Developer) | ✅ `{id}-bot` (Developer) | ✅ `{id}-bot` (Builder) | ✅ `{id}-bot` (Editor) |
| 会计 | ✅ `{id}-bot` (Reporter) | - | - | ✅ `{id}-bot` (Editor) |
| 客服代表 | ✅ `{id}-bot` (Reporter) | - | - | ✅ `{id}-bot` (Editor) |
| 法务专员 | ✅ `{id}-bot` (Reporter) | - | - | ✅ `{id}-bot` (Editor) |

> 命名规则：`{employeeId}-bot`，例如 `code-reviewer-bot`、`accountant-bot`

### 临时数字员工账号

临时数字员工在任务执行时由主脑按需创建，仅授予任务所需的最小权限：

| 类型 | 创建时机 | 命名规则 | 权限范围 | 生命周期 |
|------|---------|---------|---------|---------|
| 临时数字员工 | 任务执行时 | `temp-{taskId}-{random}` | 仅任务相关资源 | 任务完成后回收 |

### 董事长自动管理员

注册为"董事长"身份后，自动获得所有外部服务管理员权限：
- Living Agent Service: `AccessLevel.FULL` + `admin=true`
- OpenProject: `admin=true`
- GitLab: 加入 `admin` 组
- Jenkins: 加入 `admin` 角色

### 自注册与自动激活

为方便主脑批量创建账号，所有外部服务的自注册均设置为自动激活：

| 服务 | 自注册设置 | 说明 |
|------|-----------|------|
| OpenProject | `self_registration=3` (自动激活) | 注册后无需管理员审批 |
| GitLab | `signup_enabled=true` | 注册后自动激活 |
| Jenkins | 安全矩阵配置 | 按需开放 |
| Memos | 默认开放注册 | 首个用户为管理员 |
