package com.livingagent.core.autonomy;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.intervention.InterventionNeuron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.autonomy.TaskMetadataKeys;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConversationOrchestrator.class);

    private final DialogueAnalyzer dialogueAnalyzer;
    private final MainBrainTaskDirector mainBrainTaskDirector;
    private final BrainRegistry brainRegistry;
    private final AutonomyTraceService traceService;
    private final RequirementReadinessEvaluator readinessEvaluator;
    private final MainBrainRequirementClarifier requirementClarifier;
    private final FixedEmployeeDispatcher fixedEmployeeDispatcher;
    private final InterventionNeuron interventionNeuron;

    private static final int MAX_RETRY_COUNT = 1;

    /** 澄清超时时间：30分钟 */
    private static final long CLARIFICATION_TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * P0-2.5: 澄清上下文映射，存储 sessionId → 澄清上下文
     * 当编排结果需要澄清时，保存原编排上下文，用户回答后恢复
     */
    private final ConcurrentHashMap<String, ClarificationContext> pendingClarifications = new ConcurrentHashMap<>();
    private static final String CLARIFICATION_DATA_DIR = "data/clarifications";

    public ConversationOrchestrator(DialogueAnalyzer dialogueAnalyzer,
                                    MainBrainTaskDirector mainBrainTaskDirector,
                                    BrainRegistry brainRegistry,
                                    AutonomyTraceService traceService) {
        this(dialogueAnalyzer, mainBrainTaskDirector, brainRegistry, traceService, null, null, null, null);
    }

    public ConversationOrchestrator(DialogueAnalyzer dialogueAnalyzer,
                                    MainBrainTaskDirector mainBrainTaskDirector,
                                    BrainRegistry brainRegistry,
                                    AutonomyTraceService traceService,
                                    RequirementReadinessEvaluator readinessEvaluator,
                                    MainBrainRequirementClarifier requirementClarifier) {
        this(dialogueAnalyzer, mainBrainTaskDirector, brainRegistry, traceService, readinessEvaluator, requirementClarifier, null, null);
    }

    public ConversationOrchestrator(DialogueAnalyzer dialogueAnalyzer,
                                    MainBrainTaskDirector mainBrainTaskDirector,
                                    BrainRegistry brainRegistry,
                                    AutonomyTraceService traceService,
                                    RequirementReadinessEvaluator readinessEvaluator,
                                    MainBrainRequirementClarifier requirementClarifier,
                                    FixedEmployeeDispatcher fixedEmployeeDispatcher) {
        this(dialogueAnalyzer, mainBrainTaskDirector, brainRegistry, traceService, readinessEvaluator, requirementClarifier, fixedEmployeeDispatcher, null);
    }

    public ConversationOrchestrator(DialogueAnalyzer dialogueAnalyzer,
                                    MainBrainTaskDirector mainBrainTaskDirector,
                                    BrainRegistry brainRegistry,
                                    AutonomyTraceService traceService,
                                    RequirementReadinessEvaluator readinessEvaluator,
                                    MainBrainRequirementClarifier requirementClarifier,
                                    FixedEmployeeDispatcher fixedEmployeeDispatcher,
                                    InterventionNeuron interventionNeuron) {
        this.dialogueAnalyzer = dialogueAnalyzer;
        this.mainBrainTaskDirector = mainBrainTaskDirector;
        this.brainRegistry = brainRegistry;
        this.traceService = traceService;
        this.readinessEvaluator = readinessEvaluator;
        this.requirementClarifier = requirementClarifier;
        this.fixedEmployeeDispatcher = fixedEmployeeDispatcher;
        this.interventionNeuron = interventionNeuron;
        loadClarificationContextsFromDisk();
    }

    public CompletableFuture<OrchestrationResult> orchestrate(
            String message, String userId, String department, String sessionId) {

        String requestId = UUID.randomUUID().toString();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // P0-2.5: 检查是否存在待澄清的上下文，如果有则恢复原编排
                ClarificationContext clarificationCtx = pendingClarifications.get(sessionId);
                if (clarificationCtx != null) {
                    // 检查是否超时（30分钟）
                    if (System.currentTimeMillis() - clarificationCtx.timestamp() > CLARIFICATION_TIMEOUT_MS) {
                        log.info("Clarification context expired for session={}, removing", sessionId);
                        pendingClarifications.remove(sessionId);
                        deleteClarificationFile(sessionId);
                    } else {
                        // 恢复原编排上下文，跳过意图分析直接进入执行
                        log.info("Resuming orchestration after clarification for session={}, originalRequestId={}",
                            sessionId, clarificationCtx.requestId());
                        traceService.recordEvent(AutonomyTraceEvent.of(
                            requestId, "clarification_resumed", "ConversationOrchestrator",
                            "Resuming after clarification, originalRequestId=" + clarificationCtx.requestId(),
                            Map.of("originalRequestId", clarificationCtx.requestId(),
                                   "originalDepartment", clarificationCtx.department() != null ? clarificationCtx.department() : "")
                        ));

                        // 清除映射
                        pendingClarifications.remove(sessionId);
                        deleteClarificationFile(sessionId);

                        // 使用原编排上下文继续执行
                        String effectiveDepartment = clarificationCtx.department() != null ? clarificationCtx.department() : department;
                        return resumeAfterClarification(requestId, clarificationCtx, message, userId, effectiveDepartment, sessionId);
                    }
                }

                // 清理过期的澄清上下文
                cleanupExpiredClarifications();

                DialogueDecision decision = dialogueAnalyzer.analyze(message, userId, department, sessionId);
                IntakeClassification intake = classify(decision);

                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "intake_classified", "DialogueAnalyzer",
                    "Classified message: kind=" + intake.kind() + ", roughIntent=" + intake.roughIntent(),
                    Map.of(
                        "kind", intake.kind().name(),
                        "roughIntent", intake.roughIntent(),
                        "needsMainBrainPlanning", String.valueOf(intake.needsMainBrainPlanning()),
                        "likelyCrossDepartment", String.valueOf(intake.likelyCrossDepartment()),
                        "roughComplexity", String.valueOf(intake.roughComplexity())
                    )
                ));

                // P0-2.4: 检查是否需要人工接管（高风险任务）
                // 风险等级 >= 4 时尝试升级到人工干预
                // 注意：LlmBasedDialogueAnalyzer 已明确指导 LLM 仅对删除生产数据、财务付款等场景给 4-5 级
                if (decision.riskLevel() >= 4) {
                    log.info("High risk detected (riskLevel={}), escalating to human for requestId={}", decision.riskLevel(), requestId);
                    handleEscalateToHuman(requestId, decision, sessionId);
                    traceService.recordEvent(AutonomyTraceEvent.of(
                        requestId, "escalate_to_human", "ConversationOrchestrator",
                        "High risk task escalated to human intervention",
                        Map.of("riskLevel", String.valueOf(decision.riskLevel()),
                               "kind", decision.kind().name(),
                               "intent", decision.intent() != null ? decision.intent() : "")
                    ));
                    // 降级处理：InterventionNeuron 未配置时，记录警告但不阻断流程
                    // 继续走正常编排链路，由大脑自行判断是否执行
                    if (interventionNeuron == null) {
                        log.warn("[P0-2.4] InterventionNeuron not configured, high risk task (riskLevel={}) will proceed without human confirmation for requestId={}",
                            decision.riskLevel(), requestId);
                    } else {
                        return OrchestrationResult.escalateToHuman(requestId, "高风险任务需要人工确认: riskLevel=" + decision.riskLevel());
                    }
                }

                BrainRoutingDecision routingDecision;
                MainBrainTaskPlan mainBrainTaskPlan = null;

                if (intake.needsMainBrainPlanning()) {
                    // P0-6 新增：在规划之前检查需求就绪状态
                    if (readinessEvaluator != null) {
                        RequirementReadinessEvaluator.RequirementReadinessResult readinessResult =
                            readinessEvaluator.evaluate(message, department, sessionId);

                        traceService.recordEvent(AutonomyTraceEvent.of(
                            requestId, "requirement_readiness_evaluated", "RequirementReadinessEvaluator",
                            "Readiness: level=" + readinessResult.level() + ", confidence=" + readinessResult.confidence(),
                            Map.of(
                                "readinessLevel", readinessResult.level().name(),
                                "confidence", String.valueOf(readinessResult.confidence()),
                                "missingElements", String.join(",", readinessResult.missingElements()),
                                "needsClarification", String.valueOf(readinessResult.needsClarification())
                            )
                        ));

                        if (readinessResult.level() == RequirementReadinessEvaluator.ReadinessLevel.INSUFFICIENT) {
                            // 需求不明确，必须先澄清
                            String clarificationMessage;
                            if (requirementClarifier != null) {
                                clarificationMessage = requirementClarifier
                                    .clarify(message, readinessResult, department, sessionId).join();
                            } else {
                                clarificationMessage = "我需要更多信息来帮您完成任务：\n"
                                    + String.join("\n", readinessResult.clarificationQuestions());
                            }
                            log.info("Requirement insufficient, returning clarification for session={}", sessionId);
                            // P0-2.5: 保存澄清上下文，用户回答后可恢复原编排
                            saveClarificationContext(sessionId, requestId, decision, intake, department);
                            return OrchestrationResult.clarification(requestId, clarificationMessage, readinessResult);
                        }

                        if (readinessResult.level() == RequirementReadinessEvaluator.ReadinessLevel.PARTIALLY_SUFFICIENT) {
                            // 需求部分明确，记录警告但继续规划
                            log.info("Requirement partially sufficient (confidence={}), proceeding with planning for session={}",
                                readinessResult.confidence(), sessionId);
                        }
                    }

                    mainBrainTaskPlan = mainBrainTaskDirector
                        .plan(intake, decision, message, userId, sessionId, department)
                        .join();

                    // P0-6 增强：检查 LLM 返回的 requirementStatus
                    if (mainBrainTaskPlan.needsClarification()) {
                        String clarificationMessage = mainBrainTaskPlan.clarificationQuestions() != null && !mainBrainTaskPlan.clarificationQuestions().isEmpty()
                            ? "我需要更多信息来帮您完成任务：\n" + String.join("\n", mainBrainTaskPlan.clarificationQuestions())
                            : "请提供更多关于您需求的信息。";
                        log.info("MainBrain LLM determined requirement needs clarification: status={}, session={}",
                            mainBrainTaskPlan.requirementStatus(), sessionId);
                        // P0-2.5: 保存澄清上下文，用户回答后可恢复原编排
                        saveClarificationContext(sessionId, requestId, decision, intake, department);
                        return OrchestrationResult.clarification(requestId, clarificationMessage, null);
                    }

                    traceService.recordEvent(AutonomyTraceEvent.of(
                        requestId, "main_brain_planned", "MainBrainTaskDirector",
                        "Main brain planned task: type=" + mainBrainTaskPlan.taskType() + ", primary=" + mainBrainTaskPlan.primaryDepartment(),
                        Map.of(
                            TaskMetadataKeys.TASK_TYPE, mainBrainTaskPlan.taskType(),
                            "primaryDepartment", mainBrainTaskPlan.primaryDepartment(),
                            "supportingDepartments", String.join(",", mainBrainTaskPlan.supportingDepartments()),
                            "roughComplexity", String.valueOf(mainBrainTaskPlan.roughComplexity()),
                            "riskLevel", String.valueOf(mainBrainTaskPlan.riskLevel())
                        )
                    ));

                    routingDecision = routeFromMainBrainPlan(mainBrainTaskPlan, department);
                } else {
                    routingDecision = new BrainRoutingDecision(
                        department,
                        decision.primaryBrainId(),
                        decision.supportingDepartments(),
                        decision.intent(),
                        false
                    );
                }

                Optional<Brain> brainOpt = brainRegistry.getByDepartment(routingDecision.primaryDepartment());
                if (brainOpt.isEmpty()) {
                    traceService.recordEvent(AutonomyTraceEvent.of(
                        requestId, "brain_routed", "BrainRouter",
                        "No brain found for department: " + routingDecision.primaryDepartment()
                    ));
                    return OrchestrationResult.error(requestId, "NO_BRAIN", "部门大脑未注册: " + routingDecision.primaryDepartment());
                }

                Brain brain = brainOpt.get();

                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "brain_routed", "BrainRouter",
                    "Routed to brain: " + brain.getId(),
                    Map.of(
                        "primaryBrain", brain.getId(),
                        "primaryDepartment", routingDecision.primaryDepartment(),
                        "routeReason", routingDecision.routeReason(),
                        "reroutedFromRequestedDepartment", String.valueOf(routingDecision.reroutedFromRequestedDepartment())
                    )
                ));

                if (mainBrainTaskPlan != null) {
                    traceService.recordEvent(AutonomyTraceEvent.of(
                        requestId, "department_plan_created", "MainBrainTaskDirector",
                        "Department plan ready for " + routingDecision.primaryDepartment(),
                        buildDepartmentPlanMetadata(mainBrainTaskPlan, routingDecision.primaryDepartment())
                    ));
                }

                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "response_composed", "ConversationOrchestrator",
                    "Ready to process with department brain"
                ));

                return OrchestrationResult.success(requestId, decision, intake, routingDecision, mainBrainTaskPlan, brain);

            } catch (Exception e) {
                log.error("Orchestration failed: {}", e.getMessage(), e);
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "orchestration_failed", "ConversationOrchestrator",
                    "Error: " + e.getMessage()
                ));
                return OrchestrationResult.error(requestId, "SYSTEM_ERROR", "编排失败: " + e.getMessage());
            }
        });
    }

    /**
     * P0-2.5: 保存澄清上下文，当编排需要澄清时调用
     */
    private void saveClarificationContext(String sessionId, String requestId,
                                          DialogueDecision decision, IntakeClassification intake,
                                          String department) {
        ClarificationContext ctx = new ClarificationContext(
            requestId, decision, intake, department, System.currentTimeMillis()
        );
        pendingClarifications.put(sessionId, ctx);
        persistClarificationContext(sessionId, ctx);
        log.info("Saved clarification context for session={}, requestId={}", sessionId, requestId);
    }

    /**
     * P0-2.5: 澄清后恢复编排，跳过意图分析直接进入执行
     * 将用户的澄清回答与原上下文合并，重新走规划→路由→执行流程
     */
    private OrchestrationResult resumeAfterClarification(String requestId, ClarificationContext ctx,
                                                          String clarificationReply, String userId,
                                                          String department, String sessionId) {
        try {
            // 将澄清回答附加到原始意图上下文中
            DialogueDecision originalDecision = ctx.decision();
            IntakeClassification originalIntake = ctx.intake();

            // 重新规划：将用户的澄清回答作为补充信息传入
            MainBrainTaskPlan mainBrainTaskPlan = mainBrainTaskDirector
                .plan(originalIntake, originalDecision, clarificationReply, userId, sessionId, department)
                .join();

            if (mainBrainTaskPlan.needsClarification()) {
                // 仍然需要澄清，再次保存上下文
                String clarificationMessage = mainBrainTaskPlan.clarificationQuestions() != null && !mainBrainTaskPlan.clarificationQuestions().isEmpty()
                    ? "我需要更多信息来帮您完成任务：\n" + String.join("\n", mainBrainTaskPlan.clarificationQuestions())
                    : "请提供更多关于您需求的信息。";
                log.info("Still needs clarification after resume for session={}", sessionId);
                saveClarificationContext(sessionId, requestId, originalDecision, originalIntake, department);
                return OrchestrationResult.clarification(requestId, clarificationMessage, null);
            }

            BrainRoutingDecision routingDecision = routeFromMainBrainPlan(mainBrainTaskPlan, department);

            Optional<Brain> brainOpt = brainRegistry.getByDepartment(routingDecision.primaryDepartment());
            if (brainOpt.isEmpty()) {
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "brain_routed", "BrainRouter",
                    "No brain found for department: " + routingDecision.primaryDepartment()
                ));
                return OrchestrationResult.error(requestId, "NO_BRAIN", "部门大脑未注册: " + routingDecision.primaryDepartment());
            }

            Brain brain = brainOpt.get();

            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "clarification_resumed_brain_routed", "ConversationOrchestrator",
                "Resumed after clarification, routed to brain: " + brain.getId(),
                Map.of(
                    "primaryBrain", brain.getId(),
                    "primaryDepartment", routingDecision.primaryDepartment(),
                    "originalRequestId", ctx.requestId()
                )
            ));

            return OrchestrationResult.success(requestId, originalDecision, originalIntake, routingDecision, mainBrainTaskPlan, brain);

        } catch (Exception e) {
            log.error("Resume after clarification failed for session={}: {}", sessionId, e.getMessage(), e);
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "clarification_resume_failed", "ConversationOrchestrator",
                "Error: " + e.getMessage()
            ));
            return OrchestrationResult.error(requestId, "SYSTEM_ERROR", "澄清恢复编排失败: " + e.getMessage());
        }
    }

    /**
     * P0-2.5: 清理过期的澄清上下文（超过30分钟的条目）
     */
    private void cleanupExpiredClarifications() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ClarificationContext>> it = pendingClarifications.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ClarificationContext> entry = it.next();
            if (now - entry.getValue().timestamp() > CLARIFICATION_TIMEOUT_MS) {
                log.info("Removing expired clarification context for session={}, requestId={}",
                    entry.getKey(), entry.getValue().requestId());
                it.remove();
            }
        }
    }

    /**
     * P0-2.4: 人工接管处理
     * 当编排流程判定需要人工干预时，调用 InterventionNeuron 发起干预请求
     */
    private void handleEscalateToHuman(String requestId, DialogueDecision decision, String sessionId) {
        if (interventionNeuron == null) {
            log.warn("InterventionNeuron not configured, cannot escalate to human for requestId={}", requestId);
            return;
        }

        try {
            com.livingagent.core.channel.ChannelMessage escalationMessage = com.livingagent.core.channel.ChannelMessage.text(
                "channel://dispatch/" + sessionId,
                "ConversationOrchestrator",
                "channel://intervention/request",
                sessionId,
                "干预请求: 高风险任务需要人工确认"
            );
            escalationMessage.addMetadata("operationType", "HUMAN_TAKEOVER");
            escalationMessage.addMetadata("requestId", requestId);
            escalationMessage.addMetadata("riskLevel", decision.riskLevel());
            escalationMessage.addMetadata("intent", decision.intent());
            escalationMessage.addMetadata("kind", decision.kind().name());
            escalationMessage.addMetadata("originalMessage", decision.originalMessage());

            interventionNeuron.onMessage(escalationMessage);
            log.info("Escalation message sent to InterventionNeuron for requestId={}", requestId);
        } catch (Exception e) {
            log.error("Failed to send escalation to InterventionNeuron for requestId={}: {}", requestId, e.getMessage());
        }
    }

    /**
     * P0-2.2/P0-2.3: 重试逻辑闭环 + 换人逻辑
     * 当 ExecutionResultAggregator.AggregationResult 的 needsRetry() 为 true 时，
     * 调用 FixedEmployeeDispatcher.reassign() 重新分派替代员工。
     *
     * @param aggregationResult 聚合结果（含 needsRetry 标记和 retryAssignments）
     * @param mainBrainTaskPlan 原始任务计划
     * @param receipts 已收集的执行回执
     * @param retryCount 当前已重试次数
     * @return 重试编排结果（包含重新分派的员工工作分配）
     */
    public RetryOrchestrationResult retryWithReassignment(
            ExecutionResultAggregator.AggregationResult aggregationResult,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            int retryCount) {

        String retryRequestId = UUID.randomUUID().toString();

        if (aggregationResult == null || !aggregationResult.needsRetry()) {
            return RetryOrchestrationResult.noRetry(retryRequestId, "聚合结果不需要重试");
        }

        if (retryCount >= MAX_RETRY_COUNT) {
            traceService.recordEvent(AutonomyTraceEvent.of(
                retryRequestId, "retry_limit_reached", "ConversationOrchestrator",
                "已达到最大重试次数 " + MAX_RETRY_COUNT + "，停止重试",
                Map.of("retryCount", String.valueOf(retryCount),
                       "maxRetryCount", String.valueOf(MAX_RETRY_COUNT))
            ));
            return RetryOrchestrationResult.noRetry(retryRequestId, "已达到最大重试次数");
        }

        if (fixedEmployeeDispatcher == null) {
            log.warn("FixedEmployeeDispatcher not configured, cannot reassign for retry");
            return RetryOrchestrationResult.noRetry(retryRequestId, "FixedEmployeeDispatcher 未配置");
        }

        // 从回执中提取失败员工代码
        List<String> failedEmployeeCodes = new ArrayList<>();
        if (receipts != null) {
            for (EmployeeExecutionReceipt receipt : receipts) {
                if (receipt.status() == ReceiptStatus.FAILED
                    || receipt.status() == ReceiptStatus.DEGRADED) {
                    if (receipt.employeeCode() != null && !receipt.employeeCode().isBlank()) {
                        failedEmployeeCodes.add(receipt.employeeCode());
                    }
                }
            }
        }
        // 补充 retryAssignments 中指定的员工
        if (aggregationResult.retryAssignments() != null) {
            for (String assignment : aggregationResult.retryAssignments()) {
                if (!failedEmployeeCodes.contains(assignment)) {
                    failedEmployeeCodes.add(assignment);
                }
            }
        }

        if (failedEmployeeCodes.isEmpty()) {
            return RetryOrchestrationResult.noRetry(retryRequestId, "没有失败的员工需要换人");
        }

        traceService.recordEvent(AutonomyTraceEvent.of(
            retryRequestId, "retry_reassignment_started", "ConversationOrchestrator",
            "开始换人重派：失败员工=" + String.join(",", failedEmployeeCodes),
            Map.of("failedEmployeeCodes", String.join(",", failedEmployeeCodes),
                   "retryCount", String.valueOf(retryCount),
                   "overallStatus", aggregationResult.overallStatus())
        ));

        // 调用 FixedEmployeeDispatcher.reassign() 重新分派
        List<EmployeeWorkAssignment> reassignedAssignments =
            fixedEmployeeDispatcher.reassign(mainBrainTaskPlan, failedEmployeeCodes);

        if (reassignedAssignments.isEmpty()) {
            traceService.recordEvent(AutonomyTraceEvent.of(
                retryRequestId, "retry_reassignment_no_alternative", "ConversationOrchestrator",
                "没有可用的替代员工",
                Map.of("failedEmployeeCodes", String.join(",", failedEmployeeCodes))
            ));
            return RetryOrchestrationResult.noRetry(retryRequestId, "没有可用的替代员工");
        }

        traceService.recordEvent(AutonomyTraceEvent.of(
            retryRequestId, "retry_reassignment_completed", "ConversationOrchestrator",
            "换人重派完成：替代员工=" + reassignedAssignments.stream()
                .map(EmployeeWorkAssignment::employeeCode).reduce((a, b) -> a + "," + b).orElse(""),
            Map.of("reassignedCount", String.valueOf(reassignedAssignments.size()),
                   "reassignedEmployeeCodes", reassignedAssignments.stream()
                       .map(EmployeeWorkAssignment::employeeCode).reduce((a, b) -> a + "," + b).orElse(""))
        ));

        return RetryOrchestrationResult.retry(retryRequestId, reassignedAssignments, retryCount + 1);
    }

    private IntakeClassification classify(DialogueDecision decision) {
        boolean needsMainBrainPlanning = switch (decision.kind()) {
            case TASK, PROJECT, APPROVAL, CROSS_DEPARTMENT -> true;
            case CONSULTATION -> false;
            default -> false;
        };
        return new IntakeClassification(
            decision.kind(),
            decision.intent(),
            needsMainBrainPlanning,
            decision.kind() == DialogueDecision.MessageKind.CROSS_DEPARTMENT || !decision.supportingDepartments().isEmpty(),
            decision.complexity()
        );
    }

    private BrainRoutingDecision routeFromMainBrainPlan(MainBrainTaskPlan plan, String requestedDepartment) {
        String primaryDepartment = plan.primaryDepartment() == null || plan.primaryDepartment().isBlank()
            ? requestedDepartment
            : plan.primaryDepartment();
        String primaryBrainId = mapDepartmentToBrain(primaryDepartment);
        boolean rerouted = requestedDepartment != null
            && !requestedDepartment.isBlank()
            && !requestedDepartment.equalsIgnoreCase(primaryDepartment);
        return new BrainRoutingDecision(
            primaryDepartment,
            primaryBrainId,
            plan.supportingDepartments(),
            plan.taskType(),
            rerouted
        );
    }

    private Map<String, Object> buildDepartmentPlanMetadata(MainBrainTaskPlan plan, String department) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("department", department);
        metadata.put(TaskMetadataKeys.TASK_TYPE, plan.taskType());
        metadata.put("deliverables", String.join(",", plan.deliverables()));
        metadata.put("acceptanceCriteria", String.join(" | ", plan.acceptanceCriteria()));
        plan.departmentPlans().stream()
            .filter(it -> it.department().equalsIgnoreCase(department))
            .findFirst()
            .ifPresent(dp -> {
                metadata.put("objective", dp.objective());
                metadata.put("suggestedRoles", String.join(",", dp.suggestedRoles()));
                metadata.put("suggestedEmployeeCodes", String.join(",", dp.suggestedEmployeeCodes()));
            });
        return metadata;
    }

    private String mapDepartmentToBrain(String department) {
        Optional<Brain> match = brainRegistry.getAll().stream()
            .filter(b -> b.getDepartment() != null && b.getDepartment().equalsIgnoreCase(department))
            .findFirst();
        if (match.isPresent()) return match.get().getId();
        String conventionName = department.substring(0, 1).toUpperCase() + department.substring(1).toLowerCase() + "Brain";
        Optional<Brain> conventionMatch = brainRegistry.getAll().stream()
            .filter(b -> b.getId().equalsIgnoreCase(conventionName))
            .findFirst();
        if (conventionMatch.isPresent()) return conventionMatch.get().getId();
        return conventionName;
    }

    public record OrchestrationResult(
        boolean success,
        String requestId,
        DialogueDecision decision,
        IntakeClassification intake,
        BrainRoutingDecision routingDecision,
        MainBrainTaskPlan mainBrainTaskPlan,
        Brain brain,
        String status,
        String reason,
        String clarificationMessage,
        RequirementReadinessEvaluator.RequirementReadinessResult readinessResult
    ) {
        public static OrchestrationResult success(
                String requestId,
                DialogueDecision decision,
                IntakeClassification intake,
                BrainRoutingDecision routingDecision,
                MainBrainTaskPlan mainBrainTaskPlan,
                Brain brain) {
            return new OrchestrationResult(true, requestId, decision, intake, routingDecision, mainBrainTaskPlan, brain, "SUCCESS", null, null, null);
        }

        public static OrchestrationResult error(String requestId, String status, String reason) {
            return new OrchestrationResult(false, requestId, null, null, null, null, null, status, reason, null, null);
        }

        public static OrchestrationResult clarification(String requestId, String clarificationMessage,
                RequirementReadinessEvaluator.RequirementReadinessResult readinessResult) {
            return new OrchestrationResult(true, requestId, null, null, null, null, null, "NEEDS_CLARIFICATION", null, clarificationMessage, readinessResult);
        }

        /** P0-2.4: 人工接管结果 */
        public static OrchestrationResult escalateToHuman(String requestId, String reason) {
            return new OrchestrationResult(true, requestId, null, null, null, null, null, "ESCALATE_TO_HUMAN", reason, null, null);
        }

        public boolean needsClarification() {
            return "NEEDS_CLARIFICATION".equals(status);
        }

        public boolean needsHumanIntervention() {
            return "ESCALATE_TO_HUMAN".equals(status);
        }
    }

    /**
     * P0-2.2/P0-2.3: 重试编排结果
     */
    public record RetryOrchestrationResult(
        boolean shouldRetry,
        String retryRequestId,
        List<EmployeeWorkAssignment> reassignedAssignments,
        int nextRetryCount,
        String reason
    ) {
        public static RetryOrchestrationResult retry(
                String retryRequestId,
                List<EmployeeWorkAssignment> reassignedAssignments,
                int nextRetryCount) {
            return new RetryOrchestrationResult(true, retryRequestId, reassignedAssignments, nextRetryCount, null);
        }

        public static RetryOrchestrationResult noRetry(String retryRequestId, String reason) {
            return new RetryOrchestrationResult(false, retryRequestId, List.of(), 0, reason);
        }
    }

    /**
     * P0-2.5: 澄清上下文，保存原编排状态以便用户回答后恢复
     *
     * @param requestId  原始请求ID
     * @param decision   原始意图分析结果
     * @param intake     原始分类结果
     * @param department 原始部门
     * @param timestamp  保存时间戳，用于超时清理
     */
    record ClarificationContext(
        String requestId,
        DialogueDecision decision,
        IntakeClassification intake,
        String department,
        long timestamp
    ) {}

    private void persistClarificationContext(String sessionId, ClarificationContext ctx) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(CLARIFICATION_DATA_DIR);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(sessionId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), ctx);
            log.debug("Persisted clarification context for session={}", sessionId);
        } catch (Exception e) {
            log.warn("Failed to persist clarification context for session={}: {}", sessionId, e.getMessage());
        }
    }

    private void deleteClarificationFile(String sessionId) {
        try {
            java.nio.file.Path file = java.nio.file.Path.of(CLARIFICATION_DATA_DIR)
                .resolve(sessionId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
            java.nio.file.Files.deleteIfExists(file);
        } catch (Exception e) {
            log.debug("Failed to delete clarification file for session={}: {}", sessionId, e.getMessage());
        }
    }

    private void loadClarificationContextsFromDisk() {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(CLARIFICATION_DATA_DIR);
            if (!java.nio.file.Files.exists(dir)) return;
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            java.util.List<java.nio.file.Path> files = java.nio.file.Files.list(dir)
                .filter(f -> f.toString().endsWith(".json")).toList();
            for (java.nio.file.Path file : files) {
                try {
                    ClarificationContext ctx = mapper.readValue(file.toFile(), ClarificationContext.class);
                    if (ctx != null && ctx.requestId() != null) {
                        String sessionId = file.getFileName().toString().replace(".json", "");
                        if (System.currentTimeMillis() - ctx.timestamp() < CLARIFICATION_TIMEOUT_MS) {
                            pendingClarifications.put(sessionId, ctx);
                        } else {
                            java.nio.file.Files.deleteIfExists(file);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to load clarification file {}: {}", file.getFileName(), e.getMessage());
                }
            }
            log.info("Loaded {} clarification contexts from disk", pendingClarifications.size());
        } catch (Exception e) {
            log.warn("Failed to load clarification contexts: {}", e.getMessage());
        }
    }
}
