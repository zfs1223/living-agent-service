# 需求反馈规范

## 反馈目标

让董事长在提交需求后立即知道：

- 是否完整
- 是否可执行
- 风险在哪里
- 需要补什么
- 预计多久能完成
- 预计成本多少
- 由谁负责

## 反馈类型

### 1. 完整反馈

当需求完整且可执行时，返回：

- 可行性
- 周期
- 成本
- 风险
- 推荐部门
- 审批建议

### 2. 澄清反馈

当需求缺失信息时，返回：

- 缺失字段
- 建议问题
- 为什么缺失会影响评估
- 下一状态

### 3. 风险反馈

当需求存在高风险时，返回：

- 风险等级
- 风险来源
- 建议升级对象
- 是否暂停执行

## 统一输出格式

- status
- summary
- missing_fields
- risk_level
- estimated_duration
- estimated_cost
- recommended_department
- approval_required
- next_action
