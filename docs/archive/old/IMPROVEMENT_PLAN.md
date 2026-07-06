# Living Agent Service 问题核对与改进计划

> 核对时间：2026-06-04
> 基于报告：`living-agent-service-analysis-report.md`
> 核对方式：逐项读取源码验证，标注验证结果与修正

---

## 核对结论总览

| 严重级别 | 报告数量 | 核对确认 | 不存在/修正 | 需要修复 |
|---------|---------|---------|------------|---------|
| Critical | 7 | 7 | 0 | 7 |
| High | 22 | 21 | 1 (H-T04 不存在) | 21 |
| Medium | 25+ | 12/12 抽样确认 | 2 处数据偏差 | 全部 |
| Low | 12+ | 8/8 抽样确认 | 1 (CSS备份不存在) | 7 |

**关键发现**：报告整体准确，仅 H-T04（broadcastRawJson 类型转换错误）经验证不存在，Low 级别中"前端备份 CSS 残留"不存在。Medium 级别部分数据有偏差（`console.log` 实际 42 处 vs 报告 51 处，`localStorage.getItem('token')` 实际 51 处 vs 报告 93 处），但问题本质成立。

---

## 一、Critical — 必须立即修复（7/7 确认）

### C-01 飞书 App Secret 硬编码 ✅ 确认

- **文件**：`living-agent-app/src/main/resources/application.yml` 第 480-481 行
- **验证**：`app-secret: ${FEISHU_ENTERPRISE_APP_SECRET:gmU0opRuS3Aps30BWR84ghkv7ELrkncG}`，默认值为真实密钥
- **风险**：密钥泄露到版本控制，可冒充企业应用调用飞书 API
- **修复方案**：
  1. 移除默认值，改为 `${FEISHU_ENTERPRISE_APP_SECRET}`（无默认值，缺失时启动报错）
  2. 立即轮换该密钥
  3. 同理处理 `app-id` 的默认值

### C-02 SMS 验证码在 HTTP 响应中返回（临时调试暂时保留） ✅ 确认 — 暂不修复

- **文件**：`living-agent-gateway/.../controller/PhoneAuthController.java` 第 82-86 行
- **验证**：`SendSmsResponse` record 包含 `code` 字段，`result.code()` 直接返回验证码明文
- **附加发现**：`PhoneVerificationService` 第 65 行日志也打印了验证码明文
- **修复方案**：
  1. 从 `SendSmsResponse` 中移除 `code` 字段
  2. 移除日志中的验证码明文打印

### C-03 CORS 与 WebSocket 全域开放 ✅ 确认

- **文件**：`CorsConfig.java` 第 24+30 行、`WebSocketConfig.java` 第 27/30/33/36 行
- **验证**：CORS `allowedOriginPatterns=*` + `allowCredentials=true`；4 个 WS 端点均 `setAllowedOrigins("*")`
- **修复方案**：
  1. 将 `allowedOriginPatterns` 改为具体的前端域名列表（通过配置文件注入）
  2. WebSocket `setAllowedOrigins` 同样限制为可信来源
  3. 开发环境可通过 profile 使用宽松配置，生产环境严格限制

### C-04 MarkdownRenderer XSS 注入 ✅ 确认

- **文件**：`frontend/src/components/MarkdownRenderer.tsx` 第 16-53 行
- **验证**：`renderInline()` 未先调用 `escapeHtml()` 转义输入；链接/图片 URL 和文本未转义
- **附加细节**：`escapeHtml()` 函数已存在（第 8-14 行），但仅用于代码块内容（第 88 行）
- **修复方案**：
  1. 在 `renderInline()` 入口处先调用 `escapeHtml(text)` 进行预转义
  2. 对链接 URL 和图片 alt 文本也进行转义
  3. 考虑使用成熟库（如 `dompurify`）对最终 HTML 做消毒处理

### C-05 Docker Socket 挂载到应用容器 ✅ 确认

- **文件**：`docker-compose.yml` 第 134 行
- **验证**：`- /var/run/docker.sock:/var/run/docker.sock` 确实存在
- **修复方案**：
  1. 如业务不需要容器管理功能，直接移除该挂载
  2. 如必须使用，改用 Docker Socket Proxy（如 Tecnativa/docker-socket-proxy）限制 API 访问范围
  3. 评估是否可用 Docker API（TCP + TLS）替代 Socket 挂载

### C-06 数据库迁移脚本表名错误 ✅ 确认

- **文件**：`init-db/V20260604__add_artifact_visibility.sql` 第 5 行
- **验证**：迁移脚本引用 `artifact_record`（单数），`01_init.sql` 创建的表名为 `artifact_records`（复数）
- **影响**：迁移脚本执行报错，`visibility`、`created_by` 等字段无法添加
- **修复方案**：将迁移脚本中所有 `artifact_record` 改为 `artifact_records`（共 4 处：ALTER TABLE + 3 个 CREATE INDEX）

### C-07 AbstractBrain 会话历史缓存无限增长 ✅ 确认（严重程度略降）

- **文件**：`living-agent-core/.../brain/impl/AbstractBrain.java` 第 56 行
- **验证**：`sessionHistoryCache` 为 `ConcurrentHashMap<String, List<ChatMessage>>`，每个 session 最多 50 条消息（有上限），但 session 本身永远不会被清除
- **修正**：报告称"每个 session 最多保留 50 条消息"已有上限，但 Map 的 key 数量无上限才是核心问题
- **修复方案**：
  1. 使用 Caffeine 缓存替代 ConcurrentHashMap，配置 TTL（如 30 分钟未访问自动清除）和最大容量
  2. 或添加 `@Scheduled` 定时任务清理过期 session

---

## 二、High — 安全类（7/7 确认，1 项需修正）

### H-S01 JWT Token 通过 URL 查询参数传递 ✅ 确认

- **文件**：`AgentWebSocketHandler.java` 第 433 行、`api.ts` 第 666-704 行
- **验证**：后端 `extractQueryParam(session.getUri(), "token")` 作为回退；前端所有 WS URL 均将 token 拼入 query string
- **修复方案**：
  1. 优先使用 WebSocket 子协议传递 token：`Sec-WebSocket-Protocol` 头
  2. 或在 WebSocket 连接建立后通过首条消息发送认证信息
  3. 如必须用 URL 参数，token 应为短期一次性 ticket（5 分钟有效，用后即焚）

### H-S02 X-Employee-Id 头部可被客户端伪造 ✅ 部分确认（需修正描述）

- **文件**：`SessionAuthenticationFilter.java`
- **验证修正**：Filter 在认证成功时会通过 `EmployeeIdRequestWrapper` **覆盖**客户端传入的 `X-Employee-Id`，设计是安全的。**但漏洞在于**：当请求不带 Authorization 头或 session 无效时（`permitAll()` 端点），客户端伪造的 `X-Employee-Id` 会原样传递到下游
- **修复方案**：
  1. 在 Filter 中增加逻辑：当 session 无效时，**剥离**客户端传入的 `X-Employee-Id` 头
  2. `permitAll()` 端点的 Controller 不应依赖 `X-Employee-Id` 做鉴权

### H-S03 CSRF 保护全局禁用 ✅ 确认（风险可控）

- **文件**：`SecurityConfig.java` 第 37 行
- **验证**：`.csrf(AbstractHttpConfigurer::disable)` 确认存在
- **风险修正**：项目使用 Bearer Token 认证而非 Cookie-based Session，CSRF 风险相对较低。但若未来引入 Cookie 认证（如 SSO），则存在实际风险
- **修复方案**：
  1. 在代码注释中明确说明禁用 CSRF 的原因
  2. 确保所有认证均走 Bearer Token
  3. 如引入 Cookie 认证，必须同步启用 CSRF 保护

### H-S04 监控 API 无需认证 ✅ 确认

- **文件**：`SecurityConfig.java` 第 58 行、`MonitoringController.java`
- **验证**：`/api/monitoring/**` 在 `permitAll()` 列表中，`/components`、`/issues`、`/alerts` 等端点无需认证
- **修复方案**：
  1. 仅 `/health` 保留公开（且不暴露详细组件信息）
  2. `/components`、`/issues`、`/alerts`、`/alerts/{id}/ack` 移出 `permitAll()` 列表，要求认证

### H-S05 entrypoint.sh 使用 chmod 777 ✅ 确认

- **文件**：`living-agent-app/entrypoint.sh` 第 13、16、19 行
- **验证**：3 处 `chmod 777` 分别针对 `/app/data`、`/app/logs`、`/home/livingagent`
- **修复方案**：改为 `chmod 755` 或 `chown` + `chmod 750`

### H-S06 Token 存储在 localStorage ✅ 确认

- **文件**：`frontend/src/stores/index.ts` 第 35/40/53 行
- **验证**：Token 确实存储在 localStorage 中
- **修复方案**：
  1. 优先方案：改用 HttpOnly Cookie 存储 token（需后端配合设置 `Set-Cookie`）
  2. 次选方案：使用内存存储 + refresh token 机制
  3. 至少应避免将 token 拼入 URL（`api.ts` 第 371 行 `downloadUrl` 方法）

### H-S07 DepartmentChatInline.tsx 硬编码 localhost ✅ 确认

- **文件**：`DepartmentChatInline.tsx` 第 170 行
- **验证**：`ws://localhost:8382` 硬编码，与 `api.ts` 中动态构建方式不一致
- **修复方案**：改为动态构建：
  ```typescript
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws/dept/${encodeURIComponent(departmentCode)}?token=${currentToken}`;
  ```

---

## 三、High — 代码质量类（7/7 确认，1 项数据修正）

### H-C01 上帝类 LivingAgentCoreConfig ✅ 确认

- **文件**：`living-agent-core/.../config/LivingAgentCoreConfig.java`
- **验证**：997 行，54 个 @Bean 方法（报告称 40+，实际更多）
- **修复方案**：按领域拆分为 `BrainConfig`、`ToolConfig`、`ProviderConfig`、`MemoryConfig`、`KnowledgeConfig` 等

### H-C02 上帝类 EnterpriseFeishuTool ✅ 确认

- **文件**：`living-agent-core/.../tool/impl/enterprise/EnterpriseFeishuTool.java`
- **验证**：1571 行，27 个 action
- **修复方案**：按功能域拆分为 `FeishuMessageTool`、`FeishuContactTool`、`FeishuApprovalTool` 等

### H-C03 上帝类 AbstractBrain ✅ 确认

- **文件**：`living-agent-core/.../brain/impl/AbstractBrain.java`
- **验证**：1191 行，承担推理、会话管理、模型降级、工具执行等过多职责
- **修复方案**：将 ReAct 循环、会话历史管理、模型降级等抽取为独立组件

### H-C04 AgentDetail.tsx 5050 行 ✅ 确认

- **文件**：`frontend/src/pages/AgentDetail.tsx`
- **验证**：5050 行，包含工具配置、关系管理、聊天、触发器、审批、设置等
- **修复方案**：拆分为独立子组件（AgentChat、AgentTools、AgentRelations、AgentTriggers、AgentSettings 等）

### H-C05 前端 any 类型泛滥 ✅ 确认（数据修正）

- **验证**：实际 301 处 `: any`（35 个文件），报告称 577 处
- **修正**：差异可能来自统计口径（是否包含 `any[]`、`as any` 等变体）
- **修复方案**：添加 ESLint `@typescript-eslint/no-explicit-any` 规则，逐步替换为具体类型

### H-C06 异常吞没 ✅ 确认（数据修正）

- **验证**：实际 316 处 `catch(Exception e)`（100 个文件），报告称 517 处/188 个文件
- **修复方案**：关键路径使用更具体的异常类型，确保错误信息完整传递

### H-C07 飞书工具族重复代码 ✅ 确认

- **验证**：4 个飞书工具类（EnterpriseFeishuTool、FeishuTool、EmployeeFeishuTool、HrFeishuTool），存在大量重复的 HTTP 请求构建、token 管理、响应解析逻辑
- **修复方案**：抽取 `FeishuApiClient` 基类，统一 token 管理和请求发送

---

## 四、High — 并发安全类（3/4 确认，1 项不存在）

### H-T01 AbstractBrain 非线程安全计数器 ✅ 确认

- **文件**：`AbstractBrain.java` 第 62-64 行
- **验证**：`evolutionSuccessCount`/`evolutionFailureCount` 为普通 `int`，`lastEvolutionTime` 为普通 `long`
- **修复方案**：改为 `AtomicInteger` 和 `AtomicLong`

### H-T02 AbstractBrain ArrayList 竞态条件 ✅ 确认

- **文件**：`AbstractBrain.java` 第 643-707 行
- **验证**：`ConcurrentHashMap` 的 Value 为 `ArrayList`，`add()`/`remove()` 操作非原子
- **修复方案**：将 Value 改为 `CopyOnWriteArrayList`，或在操作时使用同步块

### H-T03 SandboxExecutorImpl 无界线程池 ✅ 确认

- **文件**：`SandboxExecutorImpl.java` 第 28 行
- **验证**：`Executors.newCachedThreadPool()` 无线程数上限
- **修复方案**：改为 `newThreadPoolExecutor`，设置核心线程数、最大线程数和拒绝策略

### H-T04 broadcastRawJson 类型转换错误 ❌ 不存在

- **文件**：`DepartmentWebSocketHandler.java` 第 910-924 行
- **验证**：方法接收 `String rawJson`，直接用 `new TextMessage(rawJson)` 发送，不存在类型转换错误
- **结论**：**报告此项有误，无需修复**

---

## 五、High — 基础设施类（5/5 确认）

### H-I01 Dockerfile EXPOSE 端口不匹配 ✅ 确认

- **文件**：`Dockerfile` 第 132 行、`Dockerfile.local` 第 147 行
- **验证**：EXPOSE 8380，实际应用端口 8382
- **修复方案**：将 EXPOSE 改为 8382

### H-I02 基础设施服务缺少内存限制 ✅ 确认

- **文件**：`docker-compose.yml`
- **验证**：postgres、redis、qdrant、zookeeper 缺少 `deploy.resources` 配置
- **修复方案**：为每个服务添加合理的内存限制（如 postgres 2G/512M、redis 1G/256M、qdrant 2G/512M）

### H-I03 Neo4j 密码硬编码 ✅ 确认

- **文件**：`docker-compose.yml` 第 373、421 行
- **验证**：`NEO4J_PASSWORD=memos123456` 和 `NEO4J_AUTH: neo4j/memos123456` 硬编码
- **修复方案**：改为 `${NEO4J_PASSWORD:-memos123456}` 格式，生产环境通过环境变量注入

### H-I04 脚本硬编码手机号 ✅ 确认

- **文件**：`do_login.py` 第 23 行、`get_token.py` 第 11/66 行
- **验证**：硬编码手机号 `18970718866`，`do_login.py` 甚至硬编码验证码 `940261`
- **修复方案**：改为从命令行参数或环境变量读取，或添加到 `.gitignore`

### H-I05 前端无 ESLint 配置 ✅ 确认

- **验证**：`frontend/` 目录下不存在 `.eslintrc*` 或 `eslint.config.*`
- **修复方案**：添加 ESLint 配置，集成到 CI 流程

---

## 六、Medium — 核对确认的问题

### 架构与设计

| 问题 | 验证结果 | 修复建议 |
|------|---------|---------|
| `autonomous` 与 `autonomy` 包命名易混淆 | ✅ 两个包均存在，职责不同（经济自治 vs 对话自治）但命名相似 | 保持现有命名，在文档和代码注释中强化区分说明 |
| `ConversationOrchestrator` 4 个递进构造函数 | ✅ 确认 | 改用 Builder 模式 |
| `ToolBackedEmployeeTaskExecutor` 5 个递进构造函数 | ✅ 确认 | 改用 Builder 模式 |
| 员工三表冗余 | ✅ `enterprise_employees` + `employees` + `fixed_employee_definition` | 逐步迁移到 `enterprise_employees` 单表，废弃 `employees` 表 |

### 前端代码质量

| 问题 | 报告数据 | 实际数据 | 修复建议 |
|------|---------|---------|---------|
| `alert()` 调用 | 43 处 | 43 处 ✅ | 替换为 Toast/Snackbar 非阻塞提示 |
| `console.log/error` 残留 | 51 处 | 42 处 | 添加 ESLint `no-console` 规则 |
| `localStorage.getItem('token')` | 93 处 | 51 处 | 统一通过 auth store 访问 token |
| 5 个独立 `fetchJson` 实现 | — | 未逐一验证 | 统一为单一 HTTP 客户端 |
| 国际化不一致 | — | 未逐一验证 | 统一使用 `t('key')` 模式 |
| 缺乏 Memoization | 仅 41 处 | 未逐一验证 | 关键列表和计算添加 `useMemo`/`useCallback` |
| 固定 30 秒轮询 | — | 未逐一验证 | 页面不可见时暂停轮询 |

### 资源与内存管理

| 问题 | 验证结果 | 修复建议 |
|------|---------|---------|
| `LayeredKnowledgeBaseImpl` 3 个无限缓存 | ✅ 3 个 ConcurrentHashMap 无大小限制 | 使用 Caffeine 缓存，配置 TTL 和最大容量 |
| `TaskCheckout.completedTasks` 只增不删 | ✅ 无任何 `remove()` 调用 | 添加定期清理或容量限制 |
| `SQLiteKnowledgeBase` 每次新建连接 | ✅ `getConnection()` 每次返回新连接 | 使用 HikariCP 连接池 |
| 多处 `ExecutorService` 未关闭 | — | 添加 `@PreDestroy` 生命周期管理 |
| `MemPalaceBackend` 外部进程无清理 | — | 添加 `@PreDestroy` 和 shutdown hook |
| WebSocket `recentAuthFailures` 无清理 | — | 添加定期清理过期记录 |

### 安全相关

| 问题 | 验证结果 | 修复建议 |
|------|---------|---------|
| `GlobalExceptionHandler` 泄露 `e.getMessage()` | ✅ 通用异常处理器直接返回 | 内部错误返回通用消息，详情仅记日志 |
| 28 个 Controller 各自定义 `ApiResponse` | — | 统一使用 `gateway/controller/common/ApiResponse.java` |
| OAuth `state` 参数未验证 | — | 回调时验证 state 参数一致性 |
| Token 刷新不执行令牌轮换 | — | 刷新时颁发新 token，旧 token 立即失效 |
| WebSocket 消息体无大小限制 | — | 配置 `maxTextMessageBufferSize` |
| 数据库默认弱密码 `livingagent123` | ✅ docker-compose.yml + application.yml 共 4 处 | 生产环境强制通过环境变量注入强密码 |
| Kafka 自动创建 Topic | — | 生产环境设置 `auto.create.topics.enable=false` |
| 所有服务同一 bridge 网络 | — | 分离前后端网络 |

---

## 七、Low — 核对确认的问题

| 问题 | 验证结果 | 修复建议 |
|------|---------|---------|
| `BitNetNeuron` @Deprecated 仍保留 | ✅ 已标记 @Deprecated，注释说明已停用 | 确认无引用后删除 |
| `EmployeeServiceImpl` 双实现并存 | ✅ 旧版内存实现 vs 新版 JPA 实现 | 移除旧版 `EmployeeServiceImpl` |
| `ProviderFactory` new 绕过 Spring IoC | ✅ `createFromResolvedModel` 中直接 new Provider | 考虑通过 Spring Bean 工厂管理 |
| `SandboxExecutorImpl` 正则每次重编译 | ✅ 9 个 Pattern 每次调用重编译 | 提取为 `private static final` 常量 |
| `MemPalaceBackend` Thread.sleep(500) | ✅ 硬编码等待 | 改用 CompletableFuture 或 CountDownLatch |
| Maven Wrapper 缺失 | ✅ 无 .mvn 目录和 mvnw 文件 | 执行 `mvn wrapper:wrapper` 生成 |
| 前端备份 CSS 残留 | ❌ 不存在 | 无需处理 |
| 根目录截图残留 | ✅ 2 个 PNG 文件 | 添加到 .gitignore 并删除 |

---

## 八、优先修复路线图（修订版）

### 第一阶段：紧急安全修复

| 序号 | 问题编号 | 修复内容 | 复杂度 |
|------|---------|---------|--------|
| 1 | C-01 | 移除 application.yml 中硬编码的飞书密钥，轮换密钥 | 低 |
| 2 | C-02 | 移除 SendSmsResponse 中的 code 字段 | 低 |
| 3 | C-03 | 收紧 CORS 和 WebSocket 允许来源 | 低 |
| 4 | C-04 | 修复 MarkdownRenderer XSS 漏洞 | 低 |
| 5 | C-06 | 修复迁移脚本表名 artifact_record → artifact_records | 低 |
| 6 | C-05 | 移除 Docker Socket 挂载或改用 Proxy | 中 |
| 7 | H-S04 | 监控 API 添加认证 | 低 |
| 8 | H-S07 | 修复 DepartmentChatInline 硬编码 localhost | 低 |
| 9 | H-I04 | 清理脚本中的硬编码手机号 | 低 |

### 第二阶段：并发与内存安全

| 序号 | 问题编号 | 修复内容 | 复杂度 |
|------|---------|---------|--------|
| 10 | H-T01 | AbstractBrain 计数器改 AtomicInteger | 低 |
| 11 | H-T02 | sessionHistoryCache Value 改 CopyOnWriteArrayList | 低 |
| 12 | H-T03 | SandboxExecutorImpl 改有界线程池 | 低 |
| 13 | C-07 | sessionHistoryCache 添加 TTL/驱逐策略 | 中 |
| 14 | M | LayeredKnowledgeBaseImpl 缓存添加容量限制 | 中 |
| 15 | M | TaskCheckout.completedTasks 添加清理机制 | 低 |
| 16 | M | SQLiteKnowledgeBase 添加连接池 | 中 |

### 第三阶段：安全加固

| 序号 | 问题编号 | 修复内容 | 复杂度 |
|------|---------|---------|--------|
| 17 | H-S02 | SessionAuthenticationFilter 剥离未认证请求的 X-Employee-Id | 低 |
| 18 | H-S01 | WebSocket Token 传递改用子协议或首条消息认证 | 中 |
| 19 | H-S06 | Token 存储改用 HttpOnly Cookie 或内存存储 | 高 |
| 20 | M | GlobalExceptionHandler 不返回 e.getMessage() | 低 |
| 21 | M | 数据库默认密码改为强制环境变量 | 低 |
| 22 | H-I03 | Neo4j 密码改为环境变量引用 | 低 |
| 23 | H-I01 | Dockerfile EXPOSE 端口改为 8382 | 低 |

### 第四阶段：代码质量基础

| 序号 | 问题编号 | 修复内容 | 复杂度 |
|------|---------|---------|--------|
| 24 | H-I05 | 添加前端 ESLint 配置 | 低 |
| 25 | H-C05 | 逐步替换 any 类型为具体类型 | 高 |
| 26 | M | 统一前端 Token 访问（通过 store） | 中 |
| 27 | M | 替换 43 处 alert() 为 Toast 组件 | 中 |
| 28 | M | 清理 console.log/error 残留 | 低 |
| 29 | H-C06 | 关键路径异常处理改为具体异常类型 | 中 |

### 第五阶段：架构优化（长期）

| 序号 | 问题编号 | 修复内容 | 复杂度 |
|------|---------|---------|--------|
| 30 | H-C01 | 拆分 LivingAgentCoreConfig 为多个配置类 | 高 |
| 31 | H-C02 | 拆分 EnterpriseFeishuTool 为多个工具类 | 高 |
| 32 | H-C03 | 拆分 AbstractBrain 职责 | 高 |
| 33 | H-C04 | 拆分 AgentDetail.tsx 为独立子组件 | 高 |
| 34 | H-C07 | 提取飞书工具公共基类 | 中 |
| 35 | M | ConversationOrchestrator / ToolBackedEmployeeTaskExecutor 改 Builder 模式 | 中 |
| 36 | H-I02 | 基础设施服务添加内存资源限制 | 低 |
| 37 | M | Docker Compose profiles 分离核心与 DevOps 服务 | 中 |

---

## 九、报告修正汇总

| 原编号 | 原描述 | 修正内容 |
|--------|--------|---------|
| H-T04 | broadcastRawJson 类型转换错误 + 绕过 WebSocket 线程安全锁 | **不存在**，方法逻辑正确，无需修复 |
| H-C05 | 577 处 any 类型 | 实际 301 处 `: any`（统计口径差异） |
| H-C06 | 517 处 catch(Exception e)，188 个文件 | 实际 316 处，100 个文件 |
| H-S02 | X-Employee-Id 头部可被客户端伪造 | 修正：认证成功时会覆盖，漏洞仅在 permitAll 端点 |
| H-S03 | CSRF 保护全局禁用 | 修正：Bearer Token 架构下风险较低，但需文档说明 |
| C-07 | 会话历史缓存无限增长 | 修正：每个 session 有 50 条消息上限，核心问题是 Map key 无上限 |
| Medium | 前端 console.log/error 51 处 | 实际 42 处 |
| Medium | 前端 localStorage.getItem('token') 93 处 | 实际 51 处 |
| Low | 前端 index copy 2.css 备份文件残留 | **不存在** |
