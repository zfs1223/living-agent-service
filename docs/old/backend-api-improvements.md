# 后端 API 改进清单（最终版）

> 基于 `living-agent-service` 当前实际代码的最终核对结果。
>
> 本文只保留两类结论：
> - **已完成**：已在代码中落地，可视为完成
> - **未完成**：仍然没有真正落到代码里，需要继续补
>
> 结论优先级：以当前代码为准，不以旧需求描述为准。

---

## 0. 核查结论总览

| 项目 | 结论 | 说明 |
|---|---|---|
| 默认租户初始化 | ✅ 已完成 | `SystemConfigService` 已通过 `@PostConstruct` 初始化 `tenant_default` |
| 身份提供商管理 | ✅ 已完成 | `EnterpriseApiController` 已提供 `/api/enterprise/identity-providers` 相关管理接口，内部复用 `SystemConfigService` provider 配置 |
| 租户配额管理 | ✅ 已完成 | `EnterpriseApiController` 已提供 `/api/enterprise/tenant-quotas` 读写接口，数据存于系统配置 settings |
| 邀请码管理 | ✅ 已完成 | `EnterpriseApiController` 已提供 `/api/enterprise/invitation-codes` 的列表、创建、删除、导出接口 |
| 花名册导入 | ✅ 已完成 | `EmployeeImporter` 已存在，`EmployeeController` 已提供导入 / 预览入口 |
| 其它 API 对齐项 | ⏳ 未完成/待确认 | 其余需按 `API_REFERENCE.md` 再做一次最终校准 |

---

## 1. 已完成项

### 1.1 默认租户初始化

**实际代码**：`living-agent-gateway/src/main/java/com/livingagent/gateway/service/SystemConfigService.java`

**已实现内容**：
- `@PostConstruct initializeDefaultTenant()`
- 启动时自动创建 `tenant_default`
- 初始化逻辑幂等，已存在则跳过

**结论**：已完成，不需要再补 API。

---

### 1.2 身份提供商管理

**实际代码**：`living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EnterpriseApiController.java`

**已实现内容**：
- `GET  /api/enterprise/identity-providers`
- `POST /api/enterprise/identity-providers`
- `POST /api/enterprise/identity-providers/oauth2`
- `PUT  /api/enterprise/identity-providers/{id}`
- `PATCH /api/enterprise/identity-providers/{id}/oauth2`
- `DELETE /api/enterprise/identity-providers/{id}`

**数据来源**：
- `SystemConfigService.getProviderConfigs()`
- `SystemConfigService.updateProviderConfig(...)`

**结论**：已完成，且已落到现有企业管理域，不需要再新建一套独立接口体系。

---

### 1.3 租户配额管理

**实际代码**：`living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EnterpriseApiController.java`

**已实现内容**：
- `GET  /api/enterprise/tenant-quotas`
- `PATCH /api/enterprise/tenant-quotas`

**数据来源**：
- `SystemConfigService.getSettings()`
- `settings["tenantQuotas"]`

**结论**：已完成。配额目前作为企业治理配置的一部分存放在系统配置中，而不是独立 quota 实体。

---

### 1.4 邀请码管理

**实际代码**：`living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EnterpriseApiController.java`

**已实现内容**：
- `GET    /api/enterprise/invitation-codes`
- `POST   /api/enterprise/invitation-codes`
- `DELETE /api/enterprise/invitation-codes/{id}`
- `GET    /api/enterprise/invitation-codes/export`

**存储方式**：
- `SystemConfigService.getSettings()`
- `settings["invitationCodes"]`

**结论**：已完成。当前实现是配置型/内存型管理，不是独立数据库实体，但 API 入口已经存在。

---

### 1.5 花名册导入

**实际代码**：
- `living-agent-core/src/main/java/com/livingagent/core/security/importer/EmployeeImporter.java`
- `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EmployeeController.java`

**已实现内容**：
- `POST /api/employees/import`
- `POST /api/employees/import/preview`
- CSV 导入能力
- 花名册字段映射与员工构建

**结论**：已完成，核心能力已存在，API 入口也已接通。

---

## 2. 未完成项

经过对当前代码的再次核对，**本轮列出的 P0 项已经都落地**，当前没有确认到必须继续补的新后端 API。

### 2.1 仍需做的不是“补接口”，而是“最终文档校准”

当前更需要关注的是：
- `docs/references/API_REFERENCE.md` 是否已经同步到最新控制器路径
- 前端服务层是否还残留旧路径
- 旧文档是否还把已完成能力写成待办

**结论**：后端能力层面没有确认到新的未完成 API；剩下主要是文档与前端同步核对。

---

## 3. 更合理的后续动作

### 3.1 立即建议
1. 把 `API_REFERENCE.md` 里涉及企业治理的接口标到正确分区
2. 将 `identity-providers / tenant-quotas / invitation-codes` 明确归入 `EnterpriseApiController`
3. 保持 `SystemConfigService` 作为 provider / tenant / settings 的统一配置容器说明
4. 保持 `EmployeeImporter` 作为花名册导入核心能力说明

### 3.2 如果后续还要继续增强
1. 再考虑把 `settings` 中的 `tenantQuotas`、`invitationCodes` 从配置型数据提升为数据库实体
2. 再考虑拆分更细的持久化仓储
3. 再考虑为 Excel 导入补强完整解析和校验流程

---

## 4. 最终结论

基于当前代码检查结果，`backend-api-improvements.md` 里提到的核心改进已经全部落地：

- 默认租户初始化：已完成
- 身份提供商管理：已完成
- 租户配额管理：已完成
- 邀请码管理：已完成
- 花名册导入：已完成

因此，当前不应再把这些内容当成“未实现 API”，而应当视为：

> **已完成的现有能力，接下来只需要把 API_REFERENCE 和相关前端调用路径做最终对齐。**
