package com.livingagent.core.proactive.scenario;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.event.EventHookManager;
import com.livingagent.core.proactive.event.HookEvent;
import com.livingagent.core.security.Employee;
import com.livingagent.core.security.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class EmployeeOnboardingHandler {

    private static final Logger log = LoggerFactory.getLogger(EmployeeOnboardingHandler.class);

    private final EventHookManager eventHookManager;
    private final List<AlertNotifier> notifiers;

    public EmployeeOnboardingHandler(EventHookManager eventHookManager, List<AlertNotifier> notifiers) {
        this.eventHookManager = eventHookManager;
        this.notifiers = notifiers != null ? new ArrayList<>(notifiers) : new ArrayList<>();
        
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        if (eventHookManager != null) {
            eventHookManager.registerHandler(new com.livingagent.core.proactive.event.HookHandler() {
                @Override
                public String[] supportedEvents() {
                    return new String[]{"employee.joined", "employee.onboarding.start"};
                }

                @Override
                public int getOrder() {
                    return 10;
                }

                @Override
                public void handle(HookEvent event) {
                    handleOnboardingEvent(event);
                }
            });
        }
    }

    public OnboardingResult prepareOnboarding(Employee employee) {
        log.info("Preparing onboarding for new employee: {}", employee.getName());

        OnboardingChecklist checklist = generateChecklist(employee);
        
        List<OnboardingTask> tasks = createOnboardingTasks(employee, checklist);
        
        notifyRelevantParties(employee, checklist);

        return new OnboardingResult(
                "onboard_" + System.currentTimeMillis(),
                employee.getEmployeeId(),
                employee.getName(),
                checklist,
                tasks,
                Instant.now(),
                true,
                null
        );
    }

    private OnboardingChecklist generateChecklist(Employee employee) {
        List<ChecklistItem> items = new ArrayList<>();

        items.add(new ChecklistItem(
                "account_setup",
                "账号开通",
                "开通企业邮箱、OA账号、GitLab账号",
                "IT",
                Priority.HIGH,
                false
        ));

        items.add(new ChecklistItem(
                "equipment_prep",
                "设备准备",
                "准备办公电脑、显示器、键盘鼠标",
                "行政",
                Priority.HIGH,
                false
        ));

        items.add(new ChecklistItem(
                "access_card",
                "门禁卡",
                "办理门禁卡、工牌",
                "行政",
                Priority.MEDIUM,
                false
        ));

        items.add(new ChecklistItem(
                "workspace_setup",
                "工位安排",
                "分配工位、准备办公用品",
                "行政",
                Priority.MEDIUM,
                false
        ));

        String department = employee.getDepartment();
        if (department != null) {
            items.add(new ChecklistItem(
                    "dept_access",
                    "部门权限",
                    "开通" + department + "相关系统权限",
                    department,
                    Priority.HIGH,
                    false
            ));
        }

        items.add(new ChecklistItem(
                "orientation_schedule",
                "入职培训",
                "安排入职培训、公司介绍",
                "HR",
                Priority.MEDIUM,
                false
        ));

        items.add(new ChecklistItem(
                "mentor_assign",
                "导师分配",
                "指定入职导师、工作对接人",
                department != null ? department : "HR",
                Priority.HIGH,
                false
        ));

        items.add(new ChecklistItem(
                "policy_briefing",
                "制度说明",
                "考勤制度、报销流程、休假制度说明",
                "HR",
                Priority.LOW,
                false
        ));

        return new OnboardingChecklist(
                "checklist_" + System.currentTimeMillis(),
                employee.getEmployeeId(),
                items,
                Instant.now()
        );
    }

    private List<OnboardingTask> createOnboardingTasks(Employee employee, OnboardingChecklist checklist) {
        List<OnboardingTask> tasks = new ArrayList<>();
        
        for (ChecklistItem item : checklist.items()) {
            OnboardingTask task = new OnboardingTask(
                    "task_" + System.currentTimeMillis() + "_" + item.id(),
                    employee.getEmployeeId(),
                    item.id(),
                    item.title(),
                    item.description(),
                    item.responsibleParty(),
                    item.priority(),
                    TaskStatus.PENDING,
                    Instant.now(),
                    null,
                    null
            );
            tasks.add(task);
        }
        
        return tasks;
    }

    private void notifyRelevantParties(Employee employee, OnboardingChecklist checklist) {
        Set<String> parties = new HashSet<>();
        parties.add("HR");
        parties.add("IT");
        parties.add("行政");
        
        if (employee.getDepartment() != null) {
            parties.add(employee.getDepartment());
        }

        String content = buildNotificationContent(employee, checklist);

        for (AlertNotifier notifier : notifiers) {
            if (notifier.isAvailable()) {
                try {
                    Alert alert = Alert.info(
                            "新员工入职准备通知",
                            content
                    ).withTargetUsers(new ArrayList<>(parties));

                    notifier.send(alert);
                    log.info("Onboarding notification sent via {}", notifier.getChannelName());
                } catch (Exception e) {
                    log.warn("Failed to send onboarding notification via {}: {}", 
                            notifier.getChannelName(), e.getMessage());
                }
            }
        }
    }

    private String buildNotificationContent(Employee employee, OnboardingChecklist checklist) {
        StringBuilder content = new StringBuilder();
        
        content.append("### 新员工入职准备\n\n");
        content.append("**员工信息**\n");
        content.append("- 姓名: ").append(employee.getName()).append("\n");
        content.append("- 部门: ").append(employee.getDepartment() != null ? employee.getDepartment() : "待分配").append("\n");
        content.append("- 职位: ").append(employee.getPosition() != null ? employee.getPosition() : "待定").append("\n");
        content.append("- 入职日期: ").append(employee.getJoinDate() != null ? employee.getJoinDate() : "待定").append("\n\n");
        
        content.append("**准备清单**\n");
        for (ChecklistItem item : checklist.items()) {
            String priority = switch (item.priority()) {
                case HIGH -> "🔴";
                case MEDIUM -> "🟡";
                case LOW -> "🟢";
            };
            content.append("- ").append(priority).append(" ").append(item.title());
            content.append(" (").append(item.responsibleParty()).append(")\n");
        }
        
        content.append("\n---\n");
        content.append("请各部门及时完成相关准备工作。");
        
        return content.toString();
    }

    private void handleOnboardingEvent(HookEvent event) {
        String eventType = event.eventType();
        
        if ("employee.joined".equals(eventType) || "employee.onboarding.start".equals(eventType)) {
            String employeeId = event.getString("employeeId");
            String name = event.getString("name");
            String department = event.getString("department");
            
            Employee employee = new Employee();
            employee.setEmployeeId(employeeId);
            employee.setName(name);
            employee.setDepartment(department);
            employee.setIdentity(UserIdentity.INTERNAL_PROBATION);
            
            prepareOnboarding(employee);
        }
    }

    public record OnboardingResult(
            String resultId,
            String employeeId,
            String employeeName,
            OnboardingChecklist checklist,
            List<OnboardingTask> tasks,
            Instant createdAt,
            boolean success,
            String error
    ) {}

    public record OnboardingChecklist(
            String checklistId,
            String employeeId,
            List<ChecklistItem> items,
            Instant createdAt
    ) {
        public int getTotalItems() {
            return items != null ? items.size() : 0;
        }
        
        public long getCompletedCount() {
            return items != null ? items.stream().filter(ChecklistItem::completed).count() : 0;
        }
        
        public double getCompletionRate() {
            int total = getTotalItems();
            return total > 0 ? (double) getCompletedCount() / total : 0;
        }
    }

    public record ChecklistItem(
            String id,
            String title,
            String description,
            String responsibleParty,
            Priority priority,
            boolean completed
    ) {
        public ChecklistItem markCompleted() {
            return new ChecklistItem(id, title, description, responsibleParty, priority, true);
        }
    }

    public record OnboardingTask(
            String taskId,
            String employeeId,
            String checklistItemId,
            String title,
            String description,
            String assignee,
            Priority priority,
            TaskStatus status,
            Instant createdAt,
            Instant completedAt,
            String completedBy
    ) {
        public OnboardingTask complete(String completedBy) {
            return new OnboardingTask(
                    taskId, employeeId, checklistItemId, title, description,
                    assignee, priority, TaskStatus.COMPLETED, createdAt, Instant.now(), completedBy
            );
        }
    }

    public enum Priority {
        HIGH, MEDIUM, LOW
    }

    public enum TaskStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
