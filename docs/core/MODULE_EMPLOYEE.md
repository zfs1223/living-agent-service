# 员工模块

> 版本：2026-05-18 | 路径：living-agent-core/employee/

## 员工类型与来源

| EmployeeType | 说明 | Origin | 说明 |
|---|---|---|--- |
| `HUMAN` | 人类员工 | `HUMAN` | 人类员工 |
| `DIGITAL` | 数字员工 | `FIXED` | 固定编制数字员工 |
| | | `EVOLVED` | LLM 动态创建数字员工 |
| | | `PERSONAL` | 个人助理 |

> **注意**：`EmployeeType` 和 `EmployeeOrigin` 是不同维度。类型区分人/机，来源区分数字员工的创建方式。

## 固定员工编制（32人）

| 部门 | 人数 | 编码范围 |
|------|------|----------|
| 技术部 | 10 | T01-T10 |
| 财务部 | 4 | F01-F04 |
| 运营部 | 4 | O01-O04 |
| 销售部 | 3 | S01-S03 |
| 人力资源 | 2 | H01-H02 |
| 客服部 | 2 | C01-C02 |
| 行政部 | 3 | A01-A03 |
| 法务部 | 2 | L01-L02 |
| 跨部门 | 2 | M01-M02 |

## FixedEmployeeRegistry 核心逻辑

```java
@PostConstruct
init() {
    1. 调用 registerAllFixedEmployees()
       a. 先尝试 registerDefinitionsFromDatabase()（数据库优先）
       b. 数据库为空则回退到静态注册：
          - registerTechEmployees()     // T01-T10
          - registerFinanceEmployees()  // F01-F04
          - registerOpsEmployees()      // O01-O04
          - registerSalesEmployees()    // S01-S03
          - registerHrEmployees()       // H01-H02
          - registerCsEmployees()       // C01-C02
          - registerAdminEmployees()    // A01-A03
          - registerLegalEmployees()    // L01-L02
          - registerCrossDeptEmployees() // M01-M02
    2. 为每个定义创建 DigitalEmployee 实例
    3. 注册到 ChannelManager
    4. 订阅相应通道
}

getEmployeeByCode(String code) {
    return codeToNeuronId映射 → getEmployeeByNeuronId()
}

getEmployeesByDepartment(String department) {
    return employeeService.listByDepartment(department)
}
```

## DigitalEmployee 核心逻辑

```java
// DigitalEmployee.java — 纯数据类（Builder 模式）
// 任务执行由 EmployeeTaskExecutor（如 ToolBackedEmployeeTaskExecutor）统一处理

public class DigitalEmployee {
    String employeeId;
    String employeeCode;
    String name;
    String department;
    String brainId;
    List<String> capabilities;
    List<String> tools;
    List<String> skills;
    // ... Builder 模式
}
```

## 能力与工具验证

```java
// FixedEmployeeRegistry.FixedEmployeeDefinition（内部 record）
validateCapability(String capability) {
    if (!hasCapability(capability)) {
        throw IllegalStateException("不具备能力: " + capability);
    }
}

validateTool(String toolId) {
    if (!hasTool(toolId)) {
        throw IllegalStateException("未授权工具: " + toolId);
    }
}

validateSkill(String skillId) {
    if (!hasSkill(skillId)) {
        throw IllegalStateException("未配置技能: " + skillId);
    }
}
```

## 代码路径

```
employee/
├── Employee.java                 # 员工接口（含 EmployeeType 枚举）
├── EmployeeOrigin.java           # 员工来源枚举
├── registry/
│   └── FixedEmployeeRegistry.java # 固定员工注册表（含 FixedEmployeeDefinition 内部 record）
├── impl/
│   ├── DigitalEmployee.java     # 数字员工（纯数据类 + Builder）
│   └── HumanEmployee.java       # 人类员工实现
├── neuron/
│   └── EmployeeNeuron.java      # 员工神经元
├── claim/
│   └── TaskClaimService.java    # 任务认领
└── entity/
    ├── EmployeeEntity.java
    ├── DigitalEmployeeEntity.java
    └── HumanEmployeeEntity.java

database/entity/
└── FixedEmployeeDefinitionEntity.java  # 固定员工定义实体
```

> **注意**：`employee/lifecycle/` 目录尚未实现，`EmployeeLifecycleService` 为待实现功能。

## 代码任务专用字段

`EmployeeWorkAssignment` 和 `EmployeeExecutionReceipt` 新增代码任务专用字段：

| 字段 | 说明 | EmployeeWorkAssignment | EmployeeExecutionReceipt |
|------|------|----------------------|--------------------------|
| `worktreePath` | 代码工作区路径 | ✅ | ✅ |
| `diffPath` | 代码差异路径 | ✅ | ✅ |

- 两个 record 均提供兼容构造器（不含 worktreePath/diffPath），旧代码无需修改
- 代码任务场景下，`worktreePath` 和 `diffPath` 应优先于 `metadata` 中的同名键
- 非代码任务这两个字段为 `null`，不影响现有逻辑

## 快速定位

| 需求 | 文件 |
|------|------|
| 添加新员工 | `FixedEmployeeRegistry.java` |
| 修改员工能力 | `FixedEmployeeRegistry.java` (withDefaultSkills) |
| 修改员工通道 | `DigitalEmployee.java` |
| 查询员工 | `FixedEmployeeRegistry.getEmployeeByCode(code)` |
| 按部门查询 | `FixedEmployeeRegistry.getEmployeesByDepartment(dept)` |
| 修改员工定义 | `FixedEmployeeDefinition` 内部 record |
| 修改任务单字段 | `EmployeeWorkAssignment.java`（含 worktreePath/diffPath） |
| 修改回执字段 | `EmployeeExecutionReceipt.java`（含 worktreePath/diffPath） |
