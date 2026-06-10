# 需求路由状态机

## 状态定义

- draft：草稿
- submitted：已提交
- need_clarification：需澄清
- assessing：评估中
- awaiting_approval：待审批
- approved：已批准
- routed：已路由到部门
- in_progress：处理中
- blocked：已阻塞
- completed：已完成
- rejected：已拒绝
- archived：已归档

## 状态流转

1. draft -> submitted
2. submitted -> need_clarification
3. submitted -> assessing
4. need_clarification -> submitted
5. assessing -> awaiting_approval
6. awaiting_approval -> approved
7. awaiting_approval -> rejected
8. approved -> routed
9. routed -> in_progress
10. in_progress -> blocked
11. blocked -> in_progress
12. in_progress -> completed
13. completed -> archived
14. rejected -> archived

## 处理规则

### submitted

系统解析需求并检查是否完整。

### need_clarification

系统返回缺失字段与澄清问题，等待董事长补充。

### assessing

系统或固定员工评估可行性、周期、成本、风险和依赖。

### awaiting_approval

等待董事长确认是否继续。

### approved

需求正式进入执行链路。

### routed

系统将任务分发到对应部门协调队列或固定员工。

### in_progress

部门正在执行任务。

### blocked

任务因依赖、风险或审批问题暂停。

### completed

任务已完成并生成结果。

### archived

任务关闭并归档到历史记录。
