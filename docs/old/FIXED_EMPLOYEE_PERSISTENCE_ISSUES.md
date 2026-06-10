# 固定员工持久化逻辑检查报告

> 检查日期: 2026-04-25  
> 涉及模块: living-agent-core, living-agent-gateway, frontend  
> 状态: ✅ 所有问题已解决

---

## 1. 数据重复定义（已解决 ✅）

**解决方案**: 后端 `registerAllFixedEmployees()` 优先从数据库加载，V5 迁移有标准化 UPDATE 覆盖所有记录的 `neuron_id`、`channel` 和 `personality` 字段。代码硬编码的 `registerTechEmployees()` 等方法作为 fallback 保留。

---

## 2. Persona/Profile 仓库使用（已解决 ✅）

**Controller 端点**:
- `GET /api/fixed-employees/personas` - 获取所有 persona
- `GET /api/fixed-employees/personas/{code}` - 获取单个 persona
- `GET /api/fixed-employees/profiles` - 获取所有 profile
- `GET /api/fixed-employees/profiles/{code}` - 获取单个 profile

**前端调用**: `DepartmentDetail.tsx` 在页面加载时并行调用 `getPersonas()` 和 `getProfiles()`，构建 `fixedPersonas` 和 `fixedProfiles` map。

**渲染优先级**: `dbPersona` (数据库) > `fallbackPersona` (personality 推导) > 默认值

---

## 3. Personality 字段映射（已解决 ✅）

**V5 迁移 207-217 行 UPDATE 语句**:
```sql
personality = COALESCE(personality, '{}'::jsonb)
    || jsonb_build_object(
        'risk_tolerance', COALESCE((personality->>'risk_tolerance')::numeric, 0.4),
        'agreeableness', COALESCE((personality->>'agreeableness')::numeric, 0.75)
    )
```

**后端双向兼容** (`FixedEmployeeRegistry.java:743-746`):
- `conscientiousness` ↔ `rigor`
- `openness` ↔ `creativity`
- `risk_tolerance` ↔ `riskTolerance`
- `agreeableness` ↔ `obedience`

---

## 4. Neuron ID / Channel 格式（已解决 ✅）

**V5 迁移 207-210 行 UPDATE 语句**统一所有记录的格式:
```sql
neuron_id = fixed_employee_neuron_uri(department_code, code),   -- neuron://tech/t01/001
channel = fixed_employee_channel_uri(department_code, code)     -- channel://tech/t01
```

**后端 fallback** (`toDefinition` 方法 702-703 行): 如果数据库字段为空，自动生成 `neuron://` / `channel://` 格式。

---

## 5. 前端 persona 数据使用（已解决 ✅）

**V5 迁移函数冗余**: 删除了第 83-93 行的重复函数定义，保留第 140-152 行（在 INSERT 之前定义）。

**前端 persona 消费链路**:

```
后端 API → GET /api/fixed-employees/personas
    ↓
DepartmentDetail.tsx 加载 fixedPersonas → 构建 personaByCode map
    ↓
fixedEmployees 构建时: dbPersona?.hair || fallbackPersona?.hair || 'short'
    ↓
AgentLike.persona 字段填充完整 persona 数据
    ↓
PixelAgent.tsx → getFixedEmployeePersona(agent) → agent.persona 优先
    ↓
LoungeStrip.tsx → getFixedEmployeePersonaFromAgent(agent) → agent.persona 优先
    ↓
EmployeeStationCard.tsx → getFixedEmployeePersonaFromAgent(agent) → agent.persona 优先
```

**新增工具函数**: `getFixedEmployeePersonaFromAgent()` - 从 AgentLike 的 persona 字段获取个性化数据，fallback 到 personality 推导。

---

## 6. 触发器依赖（通过 ✅）

V5 迁移中使用了 `update_updated_at_column()` 触发器函数，该函数在 V4 迁移中已创建。

---

## 7. 修复记录

| 优先级 | 问题 | 修复文件 | 状态 |
|--------|------|---------|------|
| ~~P0~~ | 数据重复定义 | `FixedEmployeeRegistry.java` | ✅ Fallback 机制 |
| ~~P1~~ | Persona/Profile 未暴露 | `FixedEmployeeController.java` | ✅ 新增 4 个端点 |
| ~~P1~~ | Neuron ID 格式不一致 | `V5__...sql` | ✅ UPDATE 标准化 |
| ~~P2~~ | Personality 字段缺失 | `V5__...sql` | ✅ COALESCE 补全 |
| ~~P2~~ | 前端 PERSONA_MAP | `fixedEmployeePersona.ts` | ✅ fallbackPersonaFromCode |
| ~~P2~~ | V5 函数定义冗余 | `V5__...sql` | ✅ 删除重复定义 |
| ~~P2~~ | LoungeStrip/EmployeeStationCard 未使用 persona | `LoungeStrip.tsx`, `EmployeeStationCard.tsx` | ✅ 使用 getFixedEmployeePersonaFromAgent |

---

## 附录：涉及文件清单

### 后端
- `living-agent-core/src/main/java/com/livingagent/core/employee/registry/FixedEmployeeRegistry.java`
- `living-agent-core/src/main/java/com/livingagent/core/database/entity/FixedEmployeeDefinitionEntity.java`
- `living-agent-core/src/main/java/com/livingagent/core/database/entity/FixedEmployeeProfileEntity.java`
- `living-agent-core/src/main/java/com/livingagent/core/database/entity/FixedEmployeePersonaEntity.java`
- `living-agent-core/src/main/java/com/livingagent/core/database/repository/FixedEmployeeDefinitionRepository.java`
- `living-agent-core/src/main/java/com/livingagent/core/database/repository/FixedEmployeeProfileRepository.java`
- `living-agent-core/src/main/java/com/livingagent/core/database/repository/FixedEmployeePersonaRepository.java`
- `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/FixedEmployeeController.java`

### 数据库迁移
- `living-agent-core/src/main/resources/db/migration/V5__fixed_employee_persistence.sql`

### 前端
- `frontend/src/services/fixedEmployeeApi.ts`
- `frontend/src/pages/DepartmentDetail/fixedEmployeePersona.ts`
- `frontend/src/pages/DepartmentDetail/DepartmentDetail.tsx`
- `frontend/src/pages/DepartmentDetail/PixelAgent.tsx`
- `frontend/src/pages/DepartmentDetail/LoungeStrip.tsx`
- `frontend/src/pages/DepartmentDetail/EmployeeStationCard.tsx`
- `frontend/src/pages/DepartmentDetail/types.ts`
