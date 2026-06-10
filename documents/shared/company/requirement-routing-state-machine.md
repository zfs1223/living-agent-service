# 需求路由状态机

## 状态定义

- `draft`：草稿
- `submitted`：已提交
- `need_clarification`：需澄清
- `assessing`：评估中
- `awaiting_approval`：待审批
- `approved`：已批准
- `routed`：已路由
- `in_progress`：处理中
- `blocked`：阻塞中
- `completed`：已完成
- `rejected`：已拒绝
- `archived`：已归档

## 状态流转

### 1. 提交

`draft` -> `submitted`

### 2. 澄清

`submitted` -> `need_clarification`

当输入不完整或不规范时进入此状态。

### 3. 评估

`submitted` -> `assessing`

`need_clarification` -> `assessing`

澄清后可继续评估。

### 4. 审批

`assessing` -> `awaiting_approval`

评估完成后等待董事长审批。

### 5. 批准

`awaiting_approval` -> `approved`

董事长批准后进入执行。

### 6. 路由

`approved` -> `routed`

系统将需求分配给对应部门协调链。

### 7. 执行

`routed` -> `in_progress`

任务正式开始。

### 8. 阻塞

`in_progress` -> `blocked`

遇到依赖、风险、权限或资源阻塞时进入此状态。

### 9. 完成

`in_progress` -> `completed`

或

`blocked` -> `completed`

阻塞解除后完成。

### 10. 拒绝

`submitted` -> `rejected`

`assessing` -> `rejected`

`awaiting_approval` -> `rejected`

需求不可做或审批拒绝时进入此状态。

### 11. 归档

`completed` -> `archived`

`rejected` -> `archived`

历史记录归档保存。

## 处理原则

- 任何状态都必须可审计
- 任何状态变更都必须有原因
- 高风险状态变更必须留痕
- 不允许绕过审批直接进入执行
