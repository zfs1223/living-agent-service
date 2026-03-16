# Onboarding Automator - 入职自动化器技能

> 新员工入职准备

## 技能概述

Onboarding Automator 在新员工入职前自动准备入职所需的各项工作，包括IT设备、账号权限、入职材料等，并通知相关人员。

## 核心能力

| 能力 | 说明 |
|------|------|
| **入职清单生成** | 自动生成入职准备清单 |
| **任务自动分配** | 分配任务给相关部门 |
| **进度跟踪** | 跟踪入职准备进度 |
| **提醒通知** | 入职前提醒相关人员 |

## 技能参数

### 创建入职准备

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | create_onboarding |
| `employee.name` | string | 是 | 员工姓名 |
| `employee.department` | string | 是 | 部门 |
| `employee.position` | string | 是 | 职位 |
| `employee.onboardingDate` | string | 是 | 入职日期 |
| `employee.manager` | string | 否 | 直属上级 |

### 查询入职进度

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | get_onboarding_status |
| `onboardingId` | string | 是 | 入职ID |

## 使用示例

### 创建入职准备

```json
{
  "action": "create_onboarding",
  "employee": {
    "name": "张三",
    "department": "技术部",
    "position": "高级Java开发工程师",
    "onboardingDate": "2026-03-15",
    "manager": "李四"
  }
}
```

**响应:**

```json
{
  "onboardingId": "ONB-2026-001",
  "employee": {
    "name": "张三",
    "department": "技术部",
    "onboardingDate": "2026-03-15"
  },
  "checklist": [
    {
      "category": "IT",
      "items": [
        {"task": "准备工位", "assignee": "IT部", "dueDate": "2026-03-14", "status": "PENDING"},
        {"task": "配置电脑", "assignee": "IT部", "dueDate": "2026-03-14", "status": "PENDING"},
        {"task": "创建邮箱账号", "assignee": "IT部", "dueDate": "2026-03-14", "status": "PENDING"}
      ]
    },
    {
      "category": "行政",
      "items": [
        {"task": "制作门禁卡", "assignee": "行政部", "dueDate": "2026-03-14", "status": "PENDING"},
        {"task": "准备办公用品", "assignee": "行政部", "dueDate": "2026-03-14", "status": "PENDING"}
      ]
    }
  ],
  "notifications": [
    {"to": "IT部", "message": "新员工张三将于2026-03-15入职，请准备相关设备"},
    {"to": "李四", "message": "您的团队新员工张三将于2026-03-15入职，请分配导师"}
  ]
}
```

## 入职准备事项

| 类别 | 事项 | 负责部门 |
|------|------|---------|
| **IT准备** | 工位、电脑、显示器、键盘鼠标 | IT部 |
| **账号权限** | 邮箱、GitLab、Jira、VPN | IT部 |
| **行政准备** | 门禁卡、办公用品、工牌 | 行政部 |
| **HR准备** | 入职材料、合同、培训安排 | HR部 |
| **团队准备** | 导师分配、欢迎会安排 | 所属部门 |

## 触发词

- 入职、onboarding、新员工、准备

## 神经元集成

```java
@Service
public class OnboardingAutomator {
    
    @Autowired
    private EventDrivenNotifier notifier;
    
    @Autowired
    private HrBrain hrBrain;
    
    // 监听新员工创建事件
    @EventListener
    public void onEmployeeCreated(EmployeeCreatedEvent event) {
        Employee employee = event.getEmployee();
        
        // 生成入职清单
        OnboardingChecklist checklist = generateChecklist(employee);
        
        // 分配任务
        assignTasks(checklist);
        
        // 发送通知
        notifyStakeholders(employee, checklist);
    }
    
    // 入职前3天提醒
    @Scheduled(cron = "0 0 9 * * ?")
    public void remindUpcomingOnboarding() {
        LocalDate threeDaysLater = LocalDate.now().plusDays(3);
        List<Employee> upcomingEmployees = employeeService.findByOnboardingDate(threeDaysLater);
        
        for (Employee employee : upcomingEmployees) {
            notifier.pushNotification(Notification.builder()
                .userId(getDepartmentHead(employee.getDepartment()))
                .title("入职提醒: " + employee.getName())
                .content("新员工将于3天后入职，请确认准备就绪")
                .type(NotificationType.REMINDER)
                .priority(NotificationPriority.HIGH)
                .build());
        }
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    onboarding-automator:
      enabled: true
      
      # 入职模板
      templates:
        tech:
          - category: IT
            items: [工位, 电脑, 邮箱, GitLab, Jira, VPN]
          - category: 团队
            items: [导师分配, 代码仓库介绍, 开发环境配置]
        admin:
          - category: IT
            items: [工位, 电脑, 邮箱, OA系统]
          - category: 团队
            items: [导师分配, 部门介绍]
            
      # 提醒配置
      reminders:
        - days_before: 3
          message: "入职准备还有3天，请确认各项准备就绪"
        - days_before: 1
          message: "入职准备还有1天，请完成所有准备事项"
```

## 相关技能

- [event-driven-notifier](../../core/event-driven-notifier/SKILL.md) - 事件驱动通知器
- [risk-predictor](../../core/risk-predictor/SKILL.md) - 风险预警器
