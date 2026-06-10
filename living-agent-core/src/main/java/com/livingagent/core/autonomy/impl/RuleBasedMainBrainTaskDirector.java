package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DepartmentTaskPlan;
import com.livingagent.core.autonomy.DialogueDecision;
import com.livingagent.core.autonomy.IntakeClassification;
import com.livingagent.core.autonomy.MainBrainTaskDirector;
import com.livingagent.core.autonomy.MainBrainTaskPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RuleBasedMainBrainTaskDirector implements MainBrainTaskDirector {

    @Override
    public CompletableFuture<MainBrainTaskPlan> plan(
            IntakeClassification intake,
            DialogueDecision decision,
            String userMessage,
            String userId,
            String sessionId,
            String currentDepartment) {
        return CompletableFuture.supplyAsync(() -> createPlan(intake, decision, userMessage, currentDepartment));
    }

    private MainBrainTaskPlan createPlan(
            IntakeClassification intake,
            DialogueDecision decision,
            String userMessage,
            String currentDepartment) {
        String normalizedMessage = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        String taskType = detectTaskType(normalizedMessage, decision.intent());
        String primaryDepartment = detectPrimaryDepartment(normalizedMessage, currentDepartment, taskType);
        List<String> supportingDepartments = detectSupportingDepartments(normalizedMessage, primaryDepartment, decision.kind());
        List<String> deliverables = detectDeliverables(taskType);
        List<String> acceptanceCriteria = detectAcceptanceCriteria(taskType);
        DepartmentTaskPlan departmentPlan = createDepartmentPlan(primaryDepartment, taskType, deliverables, acceptanceCriteria);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("director_type", "rule_based_main_brain_director");
        metadata.put("source_intent", decision.intent());
        metadata.put("current_department", currentDepartment);
        metadata.put("needs_main_brain_planning", intake.needsMainBrainPlanning());

        boolean needsClarification = isVagueRequest(normalizedMessage, decision);
        if (needsClarification) {
            metadata.put("requirementStatus", "NEEDS_CLARIFICATION");
            metadata.put("clarificationQuestions", detectClarificationQuestions(normalizedMessage, taskType));
        }

        return MainBrainTaskPlan.of(
            UUID.randomUUID().toString(),
            decision.requestId(),
            taskType,
            detectGoal(taskType, userMessage),
            primaryDepartment,
            supportingDepartments,
            decision.complexity(),
            decision.riskLevel(),
            deliverables,
            acceptanceCriteria,
            List.of(departmentPlan),
            metadata
        );
    }

    private String detectTaskType(String message, String intent) {
        if (message.contains("网页") || message.contains("页面") || message.contains("前端") || "web_development".equals(intent)) {
            return "web_prototype";
        }
        if (message.contains("代码") || message.contains("开发") || message.contains("功能") || "software_development".equals(intent)) {
            return "software_development";
        }
        if (message.contains("合同") || message.contains("法务") || message.contains("合规") || message.contains("法律")) {
            return "legal_review";
        }
        if (message.contains("预算") || message.contains("报销") || message.contains("发票") || message.contains("成本") || message.contains("账目")) {
            return "finance_workflow";
        }
        if (message.contains("招聘") || message.contains("绩效") || message.contains("员工") || message.contains("考勤") || message.contains("薪酬") || message.contains("请假")) {
            return "hr_workflow";
        }
        if (message.contains("销售") || message.contains("客户") || message.contains("报价") || message.contains("方案")) {
            return "sales_workflow";
        }
        if (message.contains("投诉") || message.contains("客诉") || message.contains("工单") || message.contains("售后")) {
            return "cs_workflow";
        }
        if (message.contains("行政") || message.contains("办公") || message.contains("会议") || message.contains("档案")) {
            return "admin_workflow";
        }
        if (message.contains("数据") || message.contains("分析") || message.contains("运营") || message.contains("统计")) {
            return "data_analysis";
        }
        if (message.contains("文档") || message.contains("报告") || message.contains("总结") || message.contains("制度")) {
            return "document_generation";
        }
        return intent == null || intent.isBlank() ? "general_task" : intent;
    }

    private String detectPrimaryDepartment(String message, String currentDepartment, String taskType) {
        if (message.contains("技术") || taskType.equals("web_prototype") || taskType.equals("software_development")) {
            return "tech";
        }
        if (message.contains("财务") || taskType.equals("finance_workflow")) {
            return "finance";
        }
        if (message.contains("法务") || taskType.equals("legal_review")) {
            return "legal";
        }
        if (message.contains("人力") || message.contains("hr") || taskType.equals("hr_workflow")) {
            return "hr";
        }
        if (message.contains("销售")) {
            return "sales";
        }
        if (message.contains("客服") || message.contains("工单")) {
            return "cs";
        }
        if (message.contains("运营") || taskType.equals("data_analysis")) {
            return "ops";
        }
        if (message.contains("行政") || taskType.equals("document_generation")) {
            return "admin";
        }
        return currentDepartment == null || currentDepartment.isBlank() ? "main" : currentDepartment;
    }

    private List<String> detectSupportingDepartments(String message, String primaryDepartment, DialogueDecision.MessageKind kind) {
        List<String> departments = new ArrayList<>();
        addIfMentioned(departments, message, primaryDepartment, "tech", "技术", "开发", "代码");
        addIfMentioned(departments, message, primaryDepartment, "finance", "财务", "预算", "成本", "发票");
        addIfMentioned(departments, message, primaryDepartment, "legal", "法务", "合同", "合规");
        addIfMentioned(departments, message, primaryDepartment, "hr", "人力", "招聘", "绩效");
        addIfMentioned(departments, message, primaryDepartment, "ops", "运营", "数据", "分析");
        if (kind == DialogueDecision.MessageKind.CROSS_DEPARTMENT && departments.isEmpty()) {
            departments.add("main");
        }
        return departments.stream().distinct().toList();
    }

    private void addIfMentioned(List<String> departments, String message, String primaryDepartment, String department, String... keywords) {
        if (department.equals(primaryDepartment)) {
            return;
        }
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                departments.add(department);
                return;
            }
        }
    }

    private List<String> detectDeliverables(String taskType) {
        return switch (taskType) {
            case "web_prototype" -> List.of("index.html", "style.css", "script.js", "运行说明", "验证结果");
            case "software_development" -> List.of("实现代码", "变更说明", "测试或验证结果");
            case "legal_review" -> List.of("风险点清单", "修改建议", "合规结论");
            case "finance_workflow" -> List.of("财务处理建议", "金额/预算核对结果", "风险提示");
            case "hr_workflow" -> List.of("处理建议/评估结论", "制度依据说明", "可追踪记录");
            case "sales_workflow" -> List.of("客户分析结论", "方案建议", "跟进优先级清单");
            case "cs_workflow" -> List.of("客诉分析报告", "处理建议", "客户回复草稿");
            case "admin_workflow" -> List.of("文档/表格", "事务处理记录", "协调结论");
            case "data_analysis" -> List.of("分析结论", "关键指标", "建议动作");
            case "document_generation" -> List.of("文档正文", "摘要", "可编辑文件或内容");
            default -> List.of("任务处理结果", "执行说明");
        };
    }

    private List<String> detectAcceptanceCriteria(String taskType) {
        return switch (taskType) {
            case "web_prototype" -> List.of("页面可直接打开", "小球具有可见跳动动画", "代码结构清晰", "附带运行说明");
            case "software_development" -> List.of("实现满足用户目标", "关键逻辑可验证", "输出变更说明");
            case "legal_review" -> List.of("列出主要风险", "给出可执行修改建议", "明确合规结论");
            case "finance_workflow" -> List.of("金额或预算口径清晰", "风险和审批建议明确", "输出可追踪记录");
            case "hr_workflow" -> List.of("制度依据明确可查", "建议实事求是", "输出可归档记录");
            case "sales_workflow" -> List.of("客户画像清晰", "方案有数据支撑", "优先级排序合理");
            case "cs_workflow" -> List.of("诉求分析到位", "处理建议可执行", "回复模板得体专业");
            case "admin_workflow" -> List.of("文档格式规范", "事务记录完整", "结论可追踪");
            case "data_analysis" -> List.of("指标口径明确", "结论有依据", "给出下一步建议");
            default -> List.of("结果满足用户原始目标", "输出清晰可复核");
        };
    }

    private DepartmentTaskPlan createDepartmentPlan(
            String department,
            String taskType,
            List<String> deliverables,
            List<String> acceptanceCriteria) {
        return switch (department) {
            case "tech" -> DepartmentTaskPlan.of(
                "tech",
                "完成技术实现、验证和交付说明",
                detectTechRoles(taskType),
                detectTechEmployeeCodes(taskType),
                deliverables,
                acceptanceCriteria
            );
            case "finance" -> DepartmentTaskPlan.of(
                "finance",
                "完成财务核对、预算/成本分析和风险提示",
                List.of("budget-admin", "cost-accountant", "auditor"),
                List.of("F04", "F03", "F02"),
                deliverables,
                acceptanceCriteria
            );
            case "legal" -> DepartmentTaskPlan.of(
                "legal",
                "完成合同/合规风险审查和修改建议",
                List.of("contract-reviewer", "compliance"),
                List.of("L01", "L02"),
                deliverables,
                acceptanceCriteria
            );
            case "hr" -> DepartmentTaskPlan.of(
                "hr",
                "完成人力资源相关处理和记录",
                List.of("hr-manager", "recruiter", "performance-admin"),
                List.of("H01", "H02", "H03"),
                deliverables,
                acceptanceCriteria
            );
            case "sales" -> DepartmentTaskPlan.of(
                "sales",
                "完成销售线索分析、方案制定和客户跟进建议",
                List.of("sales-manager", "account-executive", "analyst"),
                List.of("S01", "S02", "S03"),
                deliverables,
                acceptanceCriteria
            );
            case "cs" -> DepartmentTaskPlan.of(
                "cs",
                "完成客诉分析、工单处理和客户回复",
                List.of("cs-manager", "support-agent", "qa-specialist"),
                List.of("C01", "C02", "C03"),
                deliverables,
                acceptanceCriteria
            );
            case "admin" -> DepartmentTaskPlan.of(
                "admin",
                "完成行政事务处理和文档编制",
                List.of("admin-manager", "document-specialist", "coordinator"),
                List.of("A01", "A02", "A03"),
                deliverables,
                acceptanceCriteria
            );
            case "ops" -> DepartmentTaskPlan.of(
                "ops",
                "完成数据分析、运营判断和流程建议",
                List.of("analyst", "operator", "scheduler"),
                List.of("O01", "O02", "O03"),
                deliverables,
                acceptanceCriteria
            );
            default -> DepartmentTaskPlan.of(
                department,
                "完成部门任务处理并输出可复核结果",
                List.of("department-specialist"),
                List.of(),
                deliverables,
                acceptanceCriteria
            );
        };
    }

    private List<String> detectTechRoles(String taskType) {
        if ("web_prototype".equals(taskType)) {
            return List.of("architect", "frontend", "ops-validation");
        }
        return List.of("architect", "developer", "reviewer");
    }

    private List<String> detectTechEmployeeCodes(String taskType) {
        if ("web_prototype".equals(taskType)) {
            return List.of("T02", "T09", "T04");
        }
        return List.of("T02", "T10", "T01");
    }

    private String detectGoal(String taskType, String userMessage) {
        if ("web_prototype".equals(taskType)) {
            return "创建一个满足用户描述的可运行网页原型";
        }
        return userMessage == null || userMessage.isBlank() ? "完成用户提出的任务" : userMessage;
    }

    private boolean isVagueRequest(String message, DialogueDecision decision) {
        if (decision.intent() == null || decision.intent().isBlank()) return true;
        if (message.length() < 5) return true;
        String[] vaguePatterns = {"帮我", "弄一下", "处理", "搞", "看看", "弄个", "做个", "整", "随便"};
        for (String pattern : vaguePatterns) {
            if (message.equals(pattern) || (message.length() <= pattern.length() + 3 && message.startsWith(pattern))) {
                return true;
            }
        }
        return false;
    }

    private List<String> detectClarificationQuestions(String message, String taskType) {
        List<String> questions = new ArrayList<>();
        if ("general_task".equals(taskType)) {
            questions.add("您希望完成什么类型的任务？（如：网页开发、文档生成、数据分析等）");
        }
        if (message.length() < 10) {
            questions.add("能否更详细描述一下您的需求？");
        }
        if (questions.isEmpty()) {
            questions.add("请补充更多细节，以便我为您分配合适的团队。");
        }
        return questions;
    }
}
