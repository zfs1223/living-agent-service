package com.livingagent.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.task.TaskStatus;
import com.livingagent.core.conversation.ConversationStatus;
import com.livingagent.core.conversation.ConversationService;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainOutputContract;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelSubscriber;
import com.livingagent.core.database.entity.DepartmentChatMessageEntity;
import com.livingagent.core.database.entity.DepartmentConversationEntity;
import com.livingagent.core.database.repository.DepartmentChatMessageRepository;
import com.livingagent.core.database.repository.DepartmentConversationRepository;
import com.livingagent.core.database.repository.DepartmentExecutionResultRepository;
import com.livingagent.core.database.entity.DepartmentExecutionResultEntity;
import com.livingagent.core.database.entity.TaskEntity;
import com.livingagent.core.database.repository.TaskRepository;
import com.livingagent.core.approval.ApprovalInstance;
import com.livingagent.core.approval.ApprovalService;
import com.livingagent.core.employee.EmployeeCompensationService;
import com.livingagent.core.runtime.RuntimeEventStore;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.DepartmentAccessService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.work.WorkItemContext;
import com.livingagent.core.work.WorkItemKeyGenerator;
import com.livingagent.gateway.service.WorkspaceContext;
import com.livingagent.gateway.websocket.ConnectionRegistry;
import com.livingagent.gateway.websocket.DepartmentWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class DepartmentChatService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentChatService.class);

    private final UnifiedAuthService authService;
    private final BrainRegistry brainRegistry;
    private final ChannelManager channelManager;
    private final ObjectMapper objectMapper;
    private final DepartmentChatMessageRepository chatMessageRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final FixedEmployeeDispatcher fixedEmployeeDispatcher;
    private final AssignmentPreparationService assignmentPreparationService;
    private final DepartmentExecutionCoordinator departmentExecutionCoordinator;
    private final ArtifactRecordService artifactRecordService;
    private final KnowledgeCaptureService knowledgeCaptureService;
    private final PerformanceCaptureService performanceCaptureService;
    private final MainBrainResponseComposer mainBrainResponseComposer;
    private final ExecutionResultAggregator executionResultAggregator;
    private final EmployeeExecutionReceiptService employeeExecutionReceiptService;
    private final FinalResponseCoordinator finalResponseCoordinator;
    private final AutonomyTraceService traceService;
    private final AssignmentReadinessEvaluator assignmentReadinessEvaluator;
    private final DepartmentWebSocketHandler departmentWebSocketHandler;
    private final MainBrainFinalSummaryService mainBrainFinalSummaryService;
    private final WorkItemContextService workItemContextService;
    private final RuntimeEventStore runtimeEventStore;
    private final ConnectionRegistry connectionRegistry;
    private final TaskRepository taskRepository;
    private final WorkItemKeyGenerator workItemKeyGenerator;
    private final DepartmentConversationRepository conversationRepository;
    private final DepartmentAccessService departmentAccessService;
    private final ConversationService conversationService;
    private final ApprovalService approvalService;
    private final EmployeeCompensationService compensationService;
    private final CrossDepartmentCoordinator crossDepartmentCoordinator;

    // #5/#6: DCS 接入聚合服务 + 自行领取服务
    private final DepartmentAggregationService departmentAggregationService;
    private final DepartmentTodoPool departmentTodoPool;
    private final EmployeeSelfClaimService employeeSelfClaimService;

    /** #5: executionId -> DepartmentAggregationService.AggregationResult 缓存，供最终响应使用 */
    private final Map<String, com.livingagent.core.autonomy.AggregationResult> departmentAggregationResultCache = new ConcurrentHashMap<>();

    private final Map<String, Deque<ChatHistoryEntry>> chatHistory = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> onlineUsers = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();
    private final Map<String, String> conversationToSession = new ConcurrentHashMap<>();

    /** 已触发异步最终响应的 executionId 集合，防止轮询和监听双路径重复触发 */
    private final Set<String> triggeredFinalResponses = ConcurrentHashMap.newKeySet();

    /** executionId -> DepartmentExecutionResult 缓存，供监听路径在 executionResult=null 时回查 */
    private final Map<String, DepartmentExecutionResult> executionResultCache = new ConcurrentHashMap<>();

    /** B-0-1: DB 持久化回写 */
    private final DepartmentExecutionResultRepository executionResultRepository;

    /** P2-4: sessionId -> 活跃的 MainBrainTaskPlan，用于需求冻结/防漂移检查 */
    private final Map<String, MainBrainTaskPlan> activeSessionPlans = new ConcurrentHashMap<>();

    /** P2-4: sessionId -> 活跃计划最后更新时间，用于超时清理 */
    private final Map<String, Instant> activePlanLastUpdated = new ConcurrentHashMap<>();

    /** P2-4: 活跃计划超时时间：30分钟 */
    private static final long ACTIVE_PLAN_TIMEOUT_MS = 30 * 60 * 1000L;

    private static final int MAX_HISTORY_PER_DEPARTMENT = 100;
    private static final long OFFLINE_TIMEOUT_MS = 5 * 60 * 1000;
    private static final int MAX_DB_HISTORY_PER_DEPARTMENT = 500;
    private static final long RECEIPT_WAIT_TIMEOUT_MS = 60_000;
    private static final long RECEIPT_WAIT_POLL_MS = 500;
    /** 等待过程中每隔 15 秒推送进度提示 */
    private static final long WAIT_PROGRESS_INTERVAL_MS = 15_000;
    private static final Path ARTIFACT_ROOT = Path.of(
        System.getProperty("livingagent.artifact.dir", "data/artifacts"));

    public DepartmentChatService(
            UnifiedAuthService authService,
            BrainRegistry brainRegistry,
            ChannelManager channelManager,
            DepartmentChatMessageRepository chatMessageRepository,
            ConversationOrchestrator conversationOrchestrator,
            FixedEmployeeDispatcher fixedEmployeeDispatcher,
            AssignmentPreparationService assignmentPreparationService,
            DepartmentExecutionCoordinator departmentExecutionCoordinator,
            ArtifactRecordService artifactRecordService,
            KnowledgeCaptureService knowledgeCaptureService,
            PerformanceCaptureService performanceCaptureService,
            MainBrainResponseComposer mainBrainResponseComposer,
            ExecutionResultAggregator executionResultAggregator,
            EmployeeExecutionReceiptService employeeExecutionReceiptService,
            FinalResponseCoordinator finalResponseCoordinator,
            AutonomyTraceService traceService,
            AssignmentReadinessEvaluator assignmentReadinessEvaluator,
            @org.springframework.context.annotation.Lazy DepartmentWebSocketHandler departmentWebSocketHandler,
            MainBrainFinalSummaryService mainBrainFinalSummaryService,
            WorkItemContextService workItemContextService,
            RuntimeEventStore runtimeEventStore,
            ConnectionRegistry connectionRegistry,
            TaskRepository taskRepository,
            WorkItemKeyGenerator workItemKeyGenerator,
            DepartmentConversationRepository conversationRepository,
            DepartmentAccessService departmentAccessService,
            ConversationService conversationService,
            ApprovalService approvalService,
            EmployeeCompensationService compensationService,
            CrossDepartmentCoordinator crossDepartmentCoordinator,
            // #5/#6: 新增聚合服务 + 自行领取服务
            DepartmentAggregationService departmentAggregationService,
            DepartmentTodoPool departmentTodoPool,
            EmployeeSelfClaimService employeeSelfClaimService,
            DepartmentExecutionResultRepository executionResultRepository) {
        this.authService = authService;
        this.brainRegistry = brainRegistry;
        this.channelManager = channelManager;
        this.objectMapper = new ObjectMapper();
        this.chatMessageRepository = chatMessageRepository;
        this.conversationOrchestrator = conversationOrchestrator;
        this.fixedEmployeeDispatcher = fixedEmployeeDispatcher;
        this.assignmentPreparationService = assignmentPreparationService;
        this.departmentExecutionCoordinator = departmentExecutionCoordinator;
        this.artifactRecordService = artifactRecordService;
        this.knowledgeCaptureService = knowledgeCaptureService;
        this.performanceCaptureService = performanceCaptureService;
        this.mainBrainResponseComposer = mainBrainResponseComposer;
        this.executionResultAggregator = executionResultAggregator;
        this.employeeExecutionReceiptService = employeeExecutionReceiptService;
        this.finalResponseCoordinator = finalResponseCoordinator;
        this.traceService = traceService;
        this.assignmentReadinessEvaluator = assignmentReadinessEvaluator;
        this.departmentWebSocketHandler = departmentWebSocketHandler;
        this.mainBrainFinalSummaryService = mainBrainFinalSummaryService;
        this.workItemContextService = workItemContextService;
        this.runtimeEventStore = runtimeEventStore;
        this.connectionRegistry = connectionRegistry;
        this.taskRepository = taskRepository;
        this.workItemKeyGenerator = workItemKeyGenerator;
        this.conversationRepository = conversationRepository;
        this.departmentAccessService = departmentAccessService;
        this.conversationService = conversationService;
        this.approvalService = approvalService;
        this.compensationService = compensationService;
        this.crossDepartmentCoordinator = crossDepartmentCoordinator;
        // #5/#6: 新增服务赋值
        this.departmentAggregationService = departmentAggregationService;
        this.departmentTodoPool = departmentTodoPool;
        this.employeeSelfClaimService = employeeSelfClaimService;
        this.executionResultRepository = executionResultRepository;

        employeeExecutionReceiptService.addReceiptListener(this::onReceiptRecorded);
    }

    private void onReceiptRecorded(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult) {
        if (receipt == null) return;

        executionResult = resolveExecutionResult(receipt, executionResult);
        if (executionResult == null) return;

        try {
            List<EmployeeExecutionReceipt> allReceipts = employeeExecutionReceiptService.getReceipts(executionResult.executionId());
            int total = executionResult.dispatchedAssignments() != null ? executionResult.dispatchedAssignments().size() : allReceipts.size();
            int completed = (int) allReceipts.stream().filter(r -> r.status() == ReceiptStatus.COMPLETED).count();
            int failed = (int) allReceipts.stream().filter(r -> r.status() == ReceiptStatus.FAILED).count();

            String sessionId = resolveSessionId(executionResult);

            pushReceiptProgress(sessionId, executionResult, receipt, total, completed, failed);

            // P1-4.2: 回执状态为 NEEDS_APPROVAL 时自动创建审批实例
            if (receipt.status() == ReceiptStatus.NEEDS_APPROVAL) {
                autoCreateApprovalForReceipt(receipt, executionResult);
            }

            // 检查是否所有回执都已收集（含数量校验，防止部分回执到达时误触发）
            int expectedCount = executionResult.dispatchedAssignments() != null
                ? executionResult.dispatchedAssignments().size() : 0;
            if (isExecutionCompleteWithVerification(executionResult.executionId(), expectedCount)) {
                log.info("All receipts collected for executionId={}, triggering async final response", executionResult.executionId());
                performDepartmentAggregation(executionResult);
                triggerFinalResponse(executionResult.executionId(), sessionId);
            }
        } catch (Exception e) {
            log.warn("Failed to push execution progress for receipt: {}", e.getMessage());
        }
    }

    /** Resolve executionResult from cache/DB when listener receives null. */
    private DepartmentExecutionResult resolveExecutionResult(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult) {
        if (executionResult != null) return executionResult;

        executionResult = executionResultCache.get(receipt.executionId());
        if (executionResult == null) {
            executionResult = loadExecutionResultFromDb(receipt.executionId());
        }
        if (executionResult == null) {
            log.debug("No cached executionResult for executionId={}, skipping receipt listener", receipt.executionId());
        }
        return executionResult;
    }

    /** Resolve sessionId from executionResult, falling back to conversationId mapping. */
    private String resolveSessionId(DepartmentExecutionResult executionResult) {
        String sessionId = executionResult.resolveSessionId();
        if ((sessionId == null || sessionId.isBlank()) && executionResult.metadata() != null) {
            Object convIdObj = executionResult.metadata().get("conversationId");
            if (convIdObj != null) {
                sessionId = conversationToSession.get(String.valueOf(convIdObj));
            }
        }
        return sessionId;
    }

    /** Push receipt progress via WebSocket and execution events. */
    private void pushReceiptProgress(String sessionId, DepartmentExecutionResult executionResult,
            EmployeeExecutionReceipt receipt, int total, int completed, int failed) {
        if (sessionId != null && !sessionId.isBlank()) {
            departmentWebSocketHandler.pushExecutionProgress(
                sessionId,
                executionResult.executionId(),
                receipt.status() != null ? receipt.status().getCode() : "",
                total, completed, failed
            );
            pushExecutionEventSafe(sessionId, executionResult.executionId(), "receipt_received", Map.of(
                "receiptStatus", receipt.status() != null ? receipt.status().getCode() : "",
                "employeeCode", receipt.employeeCode() != null ? receipt.employeeCode() : "",
                "completedCount", completed,
                "failedCount", failed,
                "totalCount", total
            ));
        } else {
            log.warn("Cannot push execution progress: sessionId not found in executionResult.metadata for executionId={}",
                executionResult.executionId());
        }
    }

    /** Check execution completeness with defensive count verification for non-JPA implementations. */
    private boolean isExecutionCompleteWithVerification(String executionId, int expectedCount) {
        if (employeeExecutionReceiptService instanceof com.livingagent.core.autonomy.impl.JpaEmployeeExecutionReceiptService jpaSvc) {
            return jpaSvc.isExecutionComplete(executionId, expectedCount);
        }
        boolean complete = employeeExecutionReceiptService.isExecutionComplete(executionId);
        if (complete && expectedCount > 0) {
            List<EmployeeExecutionReceipt> checkReceipts = employeeExecutionReceiptService.getReceipts(executionId);
            if (checkReceipts.size() < expectedCount) {
                log.info("isExecutionComplete returned true but receipt count {}/{} insufficient, deferring",
                    checkReceipts.size(), expectedCount);
                return false;
            }
        }
        return complete;
    }

    /** #5: Invoke department-level aggregation service and cache result. */
    private void performDepartmentAggregation(DepartmentExecutionResult executionResult) {
        if (departmentAggregationService == null) return;

        try {
            String planId = executionResult.executionId();
            String department = executionResult.department();
            String goal = executionResult.metadata() != null
                ? String.valueOf(executionResult.metadata().getOrDefault("goal", ""))
                : "";

            traceService.recordEvent(AutonomyTraceEvent.of(
                planId, "department_aggregation_started", "DepartmentChatService",
                "Starting department-level aggregation",
                Map.of("department", department, "planId", planId)
            ));

            AggregationResult aggregationResult = departmentAggregationService.aggregate(
                department, planId, goal);

            if (aggregationResult.success()) {
                log.info("#5: Department aggregation completed: department={}, quality={}",
                    department, aggregationResult.deliverable() != null
                        ? aggregationResult.deliverable().overallQualityScore() : "N/A");
                departmentAggregationResultCache.put(planId, aggregationResult);
            } else {
                log.warn("#5: Department aggregation incomplete: department={}, issues={}",
                    department, aggregationResult.message());
            }

            traceService.recordEvent(AutonomyTraceEvent.of(
                planId, "department_aggregation_completed", "DepartmentChatService",
                aggregationResult.success() ? "Aggregation successful" : "Aggregation incomplete",
                Map.of("department", department, "success", aggregationResult.success(),
                    "qualityScore", aggregationResult.deliverable() != null
                        ? aggregationResult.deliverable().overallQualityScore() : 0.0)
            ));
        } catch (Exception e) {
            log.warn("#5: Department aggregation failed, falling back to receipt aggregation: {}", e.getMessage());
        }
    }

    /**
     * P1-4.2: 当回执状态为 NEEDS_APPROVAL 时，自动调用审批服务创建审批实例。
     * 审批的 businessId 关联 executionId，审批通过后可自动推进任务状态。
     */
    private void autoCreateApprovalForReceipt(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult) {
        try {
            String executionId = executionResult.executionId();
            String department = executionResult.department();
            String employeeCode = receipt.employeeCode() != null ? receipt.employeeCode() : "unknown";
            String title = "任务审批: " + department + " - " + employeeCode + " 需要审批";
            String description = "执行回执状态为 NEEDS_APPROVAL，需要人工审批后继续。\n"
                + "执行ID: " + executionId + "\n"
                + "员工: " + employeeCode + "\n"
                + "摘要: " + (receipt.summary() != null ? receipt.summary() : "无");

            ApprovalService.CreateApprovalRequest approvalRequest = new ApprovalService.CreateApprovalRequest(
                "default",
                "execution_receipt",
                executionId,
                title,
                description,
                "system:auto"
            );

            ApprovalInstance instance = approvalService.createApproval(approvalRequest);

            // 将审批实例ID记录到执行结果的metadata中
            if (executionResult.metadata() != null && executionResult.metadata() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mutableMeta = new LinkedHashMap<>((Map<String, Object>) executionResult.metadata());
                mutableMeta.put("approvalInstanceId", instance.getInstanceId());
                mutableMeta.put("approvalStatus", instance.getStatus().name());
            }

            log.info("Auto-created approval instance {} for execution {} (receipt NEEDS_APPROVAL from employee {})",
                instance.getInstanceId(), executionId, employeeCode);

            traceService.recordEvent(AutonomyTraceEvent.of(
                executionId, "approval_auto_created", "DepartmentChatService",
                "Auto-created approval instance for NEEDS_APPROVAL receipt",
                Map.of(
                    "approvalInstanceId", instance.getInstanceId(),
                    "executionId", executionId,
                    "employeeCode", employeeCode,
                    "businessType", "execution_receipt"
                )
            ));
        } catch (Exception e) {
            log.warn("Failed to auto-create approval for NEEDS_APPROVAL receipt: executionId={}, error={}",
                executionResult.executionId(), e.getMessage());
        }
    }

    public DepartmentChatResult processDepartmentChat(String department, String message, String authorization) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        
        Optional<AuthSession> sessionOpt = validateSession(authorization);
        if (sessionOpt.isEmpty()) {
            return DepartmentChatResult.error(requestId, department, "UNAUTHORIZED", "请先登录", null);
        }
        
        AuthContext ctx = sessionOpt.get().authContext();
        String employeeId = ctx.getEmployeeId();
        AccessLevel accessLevel = ctx.getAccessLevel();
        
        if (!departmentAccessService.hasDepartmentAccess(ctx, department)) {
            return DepartmentChatResult.error(requestId, department, "FORBIDDEN", "无权访问该部门", null);
        }

        String brainName = Department.mapDepartmentToBrain(department);
        Optional<Brain> brainOpt = brainRegistry.getByDepartment(department);
        
        String resolvedBrain = brainOpt.map(Brain::getId).orElse(brainName);
        
        log.info("Department chat request: requestId={}, dept={}, brain={}, user={}, accessLevel={}",
            requestId, department, resolvedBrain, employeeId, accessLevel);

        if (brainOpt.isEmpty()) {
            return DepartmentChatResult.error(requestId, department, "NO_BRAIN", "部门大脑未注册", resolvedBrain);
        }

        String sessionId = "dept_" + department + "_" + employeeId + "_" + System.currentTimeMillis();
        
        try {
            return processDepartmentBrainAsync(requestId, department, resolvedBrain, brainOpt.get(), message,
                    sessionId, employeeId, ctx.getName(), null, null).join();
        } catch (Exception e) {
            log.error("Department chat failed: requestId={}, dept={}, error={}", requestId, department, e.getMessage(), e);
            return DepartmentChatResult.error(requestId, department, "SYSTEM_ERROR", "处理失败: " + e.getMessage(), resolvedBrain);
        }
    }

    /**
     * 部门文字对话直接进入部门大脑，不经过 AgentService / Qwen3Neuron / chat。
     * 语音链路才需要 ASR/TTS；普通文本只投递给 Department Brain 并等待输出通道响应。
     */
    public CompletableFuture<DepartmentChatResult> processDepartmentBrainAsync(
            String requestId, String department, String resolvedBrain, Brain brain,
            String message, String sessionId, String userId, String userName,
            String clientConversationId, WorkspaceContext workspace) {
        if (brain == null) {
            return CompletableFuture.completedFuture(
                DepartmentChatResult.error(requestId, department, "NO_BRAIN", "部门大脑未注册", resolvedBrain));
        }
        if (brain.getState() != Brain.BrainState.RUNNING) {
            return CompletableFuture.completedFuture(
                DepartmentChatResult.error(requestId, department, "INITIALIZING", "部门大脑仍在初始化", resolvedBrain));
        }

        DepartmentConversationEntity conversation = resolveConversation(clientConversationId, department, userId);
        String conversationId = conversation.getConversationId();

        saveMessage(department, userId, userName, message, "user", conversationId, null, null);

        if (sessionId != null) {
            try {
                connectionRegistry.bindConversation(sessionId, conversationId);
            } catch (Exception e) {
                log.debug("Failed to bind conversation to session: {}", e.getMessage());
            }
        }

        // ====== 新增：传递工作目录信息 ======
        // TODO: 将 workspace 信息传递到 AgentService
        log.info("processDepartmentBrainAsync: dept={}, userId={}, workspace={}",
            department, userId, workspace != null ? workspace.name() : "default");

        // M-NC: 检查当前会话是否有待处理的 NEEDS_CLARIFICATION 任务
        CompletableFuture<DepartmentChatResult> clarificationResult = handlePendingClarifications(
            requestId, department, resolvedBrain, brain, message, sessionId, userId, userName,
            clientConversationId, conversationId);
        if (clarificationResult != null) return clarificationResult;

        // P2-4: 需求冻结检查 — 如果当前 session 有活跃的执行中计划，拒绝重新规划
        CompletableFuture<DepartmentChatResult> frozenResult = checkRequirementFreeze(
            requestId, department, resolvedBrain, sessionId, conversationId);
        if (frozenResult != null) return frozenResult;

        return conversationOrchestrator.orchestrate(message, userId, department, sessionId)
            .thenCompose(orchestrationResult -> processOrchestrationResult(
                requestId, department, resolvedBrain, brain, message, sessionId, userId, userName,
                conversationId, orchestrationResult));
    }

    private DepartmentConversationEntity resolveConversation(String clientConversationId, String department, String userId) {
        if (clientConversationId != null && !clientConversationId.isBlank()) {
            Optional<DepartmentConversationEntity> found = findConversation(clientConversationId);
            if (found.isPresent()) {
                return found.get();
            }
            log.warn("clientConversationId {} not found, creating new conversation for user={}, dept={}",
                clientConversationId, userId, department);
        }
        return findOrCreateConversation(department, userId, null);
    }

    /** M-NC: 检查待处理澄清任务，返回非null表示已处理(需提前返回). */
    private CompletableFuture<DepartmentChatResult> handlePendingClarifications(
            String requestId, String department, String resolvedBrain, Brain brain,
            String message, String sessionId, String userId, String userName,
            String clientConversationId, String conversationId) {
        List<TaskEntity> pendingClarifications = findPendingClarifications(conversationId);
        if (pendingClarifications.isEmpty()) return null;

        TaskEntity clarificationTask = pendingClarifications.get(0);
        int currentRound = clarificationTask.getClarificationRound() != null ? clarificationTask.getClarificationRound() : 0;

        if (currentRound >= 3) {
            clarificationTask.setStatus(TaskStatus.BLOCKED.getDbValue());
            clarificationTask.setReadinessStatus(TaskStatus.BLOCKED.getDbValue());
            clarificationTask.setClarificationAnswer(message);
            clarificationTask.setUpdatedAt(Instant.now());
            taskRepository.save(clarificationTask);

            String escalationMsg = "该任务已多次澄清仍未就绪，建议转交人工处理。";
            saveMessage(department, "brain_" + department, resolvedBrain, escalationMsg, "assistant",
                conversationId, clarificationTask.getTaskKey(), clarificationTask.getExecutionId());
            pushExecutionEventSafe(sessionId, clarificationTask.getExecutionId(), "clarification_escalated", Map.of(
                "requestId", requestId, "rounds", String.valueOf(currentRound), "message", escalationMsg
            ));
            return CompletableFuture.completedFuture(
                DepartmentChatResult.success(requestId, department, resolvedBrain, escalationMsg, null, null, null, null, null));
        }

        clarificationTask.setClarificationAnswer(message);
        clarificationTask.setClarificationRound(currentRound + 1);
        clarificationTask.setUpdatedAt(Instant.now());
        taskRepository.save(clarificationTask);

        String enhancedMessage = buildClarificationContextMessage(clarificationTask, message);
        return processWithClarificationContext(requestId, department, resolvedBrain, brain,
            enhancedMessage, sessionId, userId, userName, clientConversationId,
            conversationId, clarificationTask);
    }

    /** P2-4: 需求冻结检查，返回非null表示需要提前返回冻结消息. */
    private CompletableFuture<DepartmentChatResult> checkRequirementFreeze(
            String requestId, String department, String resolvedBrain,
            String sessionId, String conversationId) {
        if (sessionId == null) return null;

        MainBrainTaskPlan activePlan = activeSessionPlans.get(sessionId);
        if (activePlan == null) return null;

        Instant lastUpdated = activePlanLastUpdated.get(sessionId);
        if (lastUpdated != null && Instant.now().isAfter(lastUpdated.plusMillis(ACTIVE_PLAN_TIMEOUT_MS))) {
            log.info("Active plan timed out for session={}, planId={}, clearing", sessionId, activePlan.planId());
            activeSessionPlans.remove(sessionId);
            activePlanLastUpdated.remove(sessionId);
            return null;
        }

        if (activePlan.isRequirementFrozen()) {
            log.info("Requirement frozen for session={}, planId={}, status={}, rejecting re-planning",
                sessionId, activePlan.planId(), activePlan.requirementStatus());
            String frozenMessage = "当前任务正在执行中（状态: " + activePlan.requirementStatus().name()
                + "），需求已锁定，暂不接受新的任务请求。请等待当前任务完成后再发起新请求。";
            saveMessage(department, "main-brain", resolvedBrain, frozenMessage, "assistant",
                conversationId, null, null);
            pushExecutionEventSafe(sessionId, null, "requirement_frozen", Map.of(
                "requestId", requestId,
                "activePlanId", activePlan.planId() != null ? activePlan.planId() : "",
                "requirementStatus", activePlan.requirementStatus().name(),
                "message", frozenMessage
            ));
            return CompletableFuture.completedFuture(
                DepartmentChatResult.success(requestId, department, resolvedBrain, frozenMessage, null, null, null, null, null));
        }
        return null;
    }

    /** Process orchestration result: handle clarification/human intervention, or proceed to execution. */
    private CompletableFuture<DepartmentChatResult> processOrchestrationResult(
            String requestId, String department, String resolvedBrain, Brain brain,
            String message, String sessionId, String userId, String userName,
            String conversationId, ConversationOrchestrator.OrchestrationResult orchestrationResult) {
        if (!orchestrationResult.success()) {
            return CompletableFuture.completedFuture(
                DepartmentChatResult.error(requestId, department, orchestrationResult.status(), orchestrationResult.reason(), resolvedBrain));
        }

        // P0-6: 处理需求澄清结果
        DepartmentChatResult clarificationResult = handleOrchestrationClarification(
            requestId, department, resolvedBrain, sessionId, conversationId, orchestrationResult);
        if (clarificationResult != null) {
            return CompletableFuture.completedFuture(clarificationResult);
        }

        // P0-2.4: 处理人工接管结果
        DepartmentChatResult escalationResult = handleOrchestrationEscalation(
            requestId, department, resolvedBrain, sessionId, conversationId, orchestrationResult);
        if (escalationResult != null) {
            return CompletableFuture.completedFuture(escalationResult);
        }

        DialogueDecision decision = orchestrationResult.decision();
        BrainRoutingDecision routingDecision = orchestrationResult.routingDecision();
        MainBrainTaskPlan mainBrainTaskPlan = orchestrationResult.mainBrainTaskPlan();
        Brain targetBrain = orchestrationResult.brain();

        // P2-4: 注册活跃计划到 session 映射（需求冻结/防漂移）
        if (mainBrainTaskPlan != null && sessionId != null && mainBrainTaskPlan.isRequirementFrozen()) {
            activeSessionPlans.put(sessionId, mainBrainTaskPlan);
            activePlanLastUpdated.put(sessionId, Instant.now());
            log.info("Registered active plan for session={}, planId={}, status={}",
                sessionId, mainBrainTaskPlan.planId(), mainBrainTaskPlan.requirementStatus());
        }

        String effectiveDepartment = routingDecision != null && routingDecision.primaryDepartment() != null
            ? routingDecision.primaryDepartment()
            : department;
        String effectiveResolvedBrain = targetBrain != null ? targetBrain.getId() : resolvedBrain;

        pushExecutionEventSafe(sessionId, null, "intake_classified", Map.of(
            "requestId", requestId,
            "intent", decision.intent() != null ? decision.intent() : "",
            "kind", decision.kind().name()
        ));

        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "work_plan_created", "DepartmentChatService",
            "Processing with routing department=" + effectiveDepartment + ", intent=" + decision.intent(),
            buildWorkPlanMetadata(decision, routingDecision, mainBrainTaskPlan)
        ));

        // #6: 发布待办到 TodoPool（自行领取前置优化）
        if (departmentTodoPool != null && mainBrainTaskPlan != null) {
            publishDepartmentTodos(requestId, mainBrainTaskPlan, effectiveDepartment, sessionId);
        }

        List<EmployeeWorkAssignment> employeeAssignments = planEmployeeAssignments(
            requestId, mainBrainTaskPlan, effectiveDepartment, sessionId, userId);
        PreparedAssignmentBatch preparedAssignmentBatch = prepareAssignmentBatch(
            requestId, sessionId, mainBrainTaskPlan, effectiveDepartment, employeeAssignments);

        if (mainBrainTaskPlan != null) {
            pushExecutionEventSafe(sessionId, null, "main_brain_planned", Map.of(
                "requestId", requestId,
                "planId", mainBrainTaskPlan.planId() != null ? mainBrainTaskPlan.planId() : "",
                "taskType", mainBrainTaskPlan.taskType() != null ? mainBrainTaskPlan.taskType() : "",
                "goal", mainBrainTaskPlan.goal() != null ? mainBrainTaskPlan.goal() : ""
            ));
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "assignment_planned", "DepartmentChatService",
                "Department execution plan prepared for " + effectiveDepartment,
                buildAssignmentPlanMetadata(mainBrainTaskPlan, effectiveDepartment, employeeAssignments, preparedAssignmentBatch)
            ));
        }

        DepartmentExecutionResult executionResult = coordinateDepartmentExecution(requestId, preparedAssignmentBatch);

        pushReadinessAndExecutionEvents(sessionId, requestId, preparedAssignmentBatch, executionResult);

        if (executionResult != null
                && (TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionResult.status()) || TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status()))) {
            return handleClarificationOrBlocked(
                requestId, effectiveDepartment, effectiveResolvedBrain,
                userId, userName, message, sessionId,
                decision, routingDecision, mainBrainTaskPlan,
                employeeAssignments, preparedAssignmentBatch, executionResult,
                conversationId);
        }

        return processWithBrain(
            requestId,
            effectiveDepartment,
            effectiveResolvedBrain,
            targetBrain != null ? targetBrain : brain,
            message,
            sessionId,
            userId,
            userName,
            decision,
            routingDecision,
            mainBrainTaskPlan,
            employeeAssignments,
            preparedAssignmentBatch,
            executionResult,
            conversationId
        );
    }

    /** P0-6: Handle orchestration clarification result, returning early result or null to continue. */
    private DepartmentChatResult handleOrchestrationClarification(
            String requestId, String department, String resolvedBrain,
            String sessionId, String conversationId,
            ConversationOrchestrator.OrchestrationResult orchestrationResult) {
        if (!orchestrationResult.needsClarification()) return null;

        String clarificationMessage = orchestrationResult.clarificationMessage();
        log.info("Returning clarification message for session={}, requestId={}", sessionId, requestId);

        DepartmentChatMessageEntity clarificationChatMessage = new DepartmentChatMessageEntity();
        clarificationChatMessage.setMessageId(UUID.randomUUID().toString());
        clarificationChatMessage.setConversationId(conversationId);
        clarificationChatMessage.setDepartment(department);
        clarificationChatMessage.setUserId("main-brain");
        clarificationChatMessage.setContent(clarificationMessage);
        clarificationChatMessage.setRole("assistant");
        clarificationChatMessage.setMessageType("clarification");
        clarificationChatMessage.setTimestamp(java.time.Instant.now());
        clarificationChatMessage.setRequestId(requestId);
        chatMessageRepository.save(clarificationChatMessage);

        pushExecutionEventSafe(sessionId, null, "clarification_needed", Map.of(
            "requestId", requestId,
            "message", clarificationMessage,
            "readinessLevel", orchestrationResult.readinessResult() != null ? orchestrationResult.readinessResult().level().name() : "UNKNOWN"
        ));

        return DepartmentChatResult.success(requestId, department, resolvedBrain, clarificationMessage, null, null, null, null, null);
    }

    /** P0-2.4: Handle orchestration human escalation result, returning early result or null to continue. */
    private DepartmentChatResult handleOrchestrationEscalation(
            String requestId, String department, String resolvedBrain,
            String sessionId, String conversationId,
            ConversationOrchestrator.OrchestrationResult orchestrationResult) {
        if (!orchestrationResult.needsHumanIntervention()) return null;

        String escalationMessage = "该任务需要人工确认，已转交人工处理。" + (orchestrationResult.reason() != null ? " 原因: " + orchestrationResult.reason() : "");
        log.info("Escalating to human for session={}, requestId={}, reason={}", sessionId, requestId, orchestrationResult.reason());

        saveMessage(department, "main-brain", resolvedBrain, escalationMessage, "assistant",
            conversationId, null, null);

        pushExecutionEventSafe(sessionId, null, "escalate_to_human", Map.of(
            "requestId", requestId,
            "message", escalationMessage,
            "reason", orchestrationResult.reason() != null ? orchestrationResult.reason() : ""
        ));

        return DepartmentChatResult.success(requestId, department, resolvedBrain, escalationMessage, null, null, null, null, null);
    }

    /** Push readiness_evaluated and execution_started events based on execution state. */
    private void pushReadinessAndExecutionEvents(String sessionId, String requestId,
            PreparedAssignmentBatch preparedAssignmentBatch, DepartmentExecutionResult executionResult) {
        if (preparedAssignmentBatch != null) {
            String readinessStatus = String.valueOf(preparedAssignmentBatch.metadata().getOrDefault("executionReadiness", "UNKNOWN"));
            pushExecutionEventSafe(sessionId, null, "readiness_evaluated", Map.of(
                "requestId", requestId,
                "readinessStatus", readinessStatus,
                "readinessScore", String.valueOf(preparedAssignmentBatch.metadata().getOrDefault("readinessScore", "0"))
            ));
        }

        if (executionResult != null && executionResult.executionId() != null
                && !TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionResult.status())
                && !TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status())) {
            pushExecutionEventSafe(sessionId, executionResult.executionId(), "execution_started", Map.of(
                "requestId", requestId,
                "executionId", executionResult.executionId(),
                "department", executionResult.department(),
                "dispatchedCount", executionResult.dispatchedAssignments().size()
            ));
        }
    }

    private CompletableFuture<DepartmentChatResult> handleClarificationOrBlocked(
            String requestId, String department, String resolvedBrain,
            String userId, String userName, String originalMessage, String sessionId,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            PreparedAssignmentBatch preparedAssignmentBatch,
            DepartmentExecutionResult executionResult,
            String conversationId) {

        boolean isBlocked = TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status());
        List<String> questions = extractClarificationQuestions(preparedAssignmentBatch, executionResult);
        List<String> blockingIssues = extractBlockingIssues(preparedAssignmentBatch, executionResult);

        String taskKey = workItemKeyGenerator.generateTaskKey("_default", userId != null ? userId : "_anon",
            mainBrainTaskPlan != null ? mainBrainTaskPlan.taskType() : "clarification");
        String executionId = workItemKeyGenerator.generateExecutionId(taskKey);

        createClarificationTaskEntity(requestId, department, userId, taskKey, executionId,
            mainBrainTaskPlan, isBlocked, questions, blockingIssues, originalMessage);

        updateConversationContext(conversationId, taskKey, executionId);

        String clarificationMessage;
        if (isBlocked) {
            clarificationMessage = composeBlockedMessage(blockingIssues, mainBrainTaskPlan);
        } else {
            clarificationMessage = composeClarificationMessage(questions, mainBrainTaskPlan);
        }

        saveMessage(department, "brain_" + department, resolvedBrain, clarificationMessage, "assistant",
            conversationId, taskKey, executionId);

        pushExecutionEventSafe(sessionId, executionId,
            isBlocked ? "execution_blocked" : "clarification_requested",
            Map.of(
                "requestId", requestId,
                "taskKey", taskKey,
                "readinessStatus", executionResult.status(),
                isBlocked ? "blockingIssues" : "clarificationQuestions",
                isBlocked ? String.join("; ", blockingIssues) : String.join("; ", questions)
            ));

        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId,
            isBlocked ? "execution_blocked" : "clarification_requested",
            "DepartmentChatService",
            isBlocked
                ? "Execution blocked, returning blocking issues to user"
                : "Execution needs clarification, returning questions to user",
            Map.of(
                "executionId", executionResult.executionId() != null ? executionResult.executionId() : "",
                "readinessStatus", executionResult.status(),
                isBlocked ? "blockingIssues" : "clarificationQuestions",
                isBlocked ? String.join("; ", blockingIssues) : String.join("; ", questions),
                "readinessScore", String.valueOf(preparedAssignmentBatch != null
                    ? preparedAssignmentBatch.metadata().getOrDefault("readinessScore", "0") : "0")
            )
        ));

        if (executionResult.executionId() != null) {
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("requestId", requestId);
            eventData.put("department", department);
            eventData.put("brain", resolvedBrain);
            eventData.put("intent", decision.intent());
            eventData.put("readinessStatus", executionResult.status());
            eventData.put(isBlocked ? "blockingIssues" : "clarificationQuestions",
                isBlocked ? blockingIssues : questions);
            runtimeEventStore.appendConversationEvent(
                "_system", "_system", "_system", executionResult.executionId(),
                isBlocked ? "execution_blocked" : "clarification_requested", eventData);
        }

        try {
            departmentWebSocketHandler.pushExecutionProgress(
                sessionId,
                executionResult.executionId(),
                executionResult.status(),
                0, 0, 0
            );
        } catch (Exception e) {
            log.warn("Failed to push clarification/blocked event via WebSocket: {}", e.getMessage());
        }

        lastActivity.put(department, Instant.now());

        return CompletableFuture.completedFuture(
            DepartmentChatResult.success(
                requestId,
                department,
                resolvedBrain,
                clarificationMessage,
                resolvedBrain,
                isBlocked ? TaskStatus.BLOCKED.getDbValue() : TaskStatus.NEEDS_CLARIFICATION.getDbValue(),
                decision.intent() != null ? decision.intent() : "department_chat",
                resolvedBrain,
                null
            )
        );
    }

    private List<TaskEntity> findPendingClarifications(String conversationId) {
        if (conversationId == null) return List.of();
        try {
            return taskRepository.findByConversationIdAndStatusIn(conversationId,
                List.of(TaskStatus.NEEDS_CLARIFICATION.getDbValue()));
        } catch (Exception e) {
            log.warn("Failed to query pending clarifications for conversationId={}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    private String buildClarificationContextMessage(TaskEntity clarificationTask, String userAnswer) {
        StringBuilder sb = new StringBuilder();
        sb.append("[澄清对话上下文]\n");
        sb.append("原始问题：\n").append(clarificationTask.getClarificationQuestions()).append("\n");
        sb.append("用户回答：").append(userAnswer).append("\n\n");
        sb.append("请基于以上澄清信息重新评估任务执行准备度。\n");
        if (clarificationTask.getDescription() != null) {
            sb.append("原始任务描述：").append(clarificationTask.getDescription());
        }
        return sb.toString();
    }

    private CompletableFuture<DepartmentChatResult> processWithClarificationContext(
            String requestId, String department, String resolvedBrain, Brain brain,
            String enhancedMessage, String sessionId, String userId, String userName,
            String clientConversationId, String conversationId, TaskEntity clarificationTask) {
        return conversationOrchestrator.orchestrate(enhancedMessage, userId, department, sessionId)
            .thenCompose(orchestrationResult -> {
                if (!orchestrationResult.success()) {
                    clarificationTask.setStatus(TaskStatus.NEEDS_CLARIFICATION.getDbValue());
                    clarificationTask.setReadinessStatus(TaskStatus.NEEDS_CLARIFICATION.getDbValue());
                    taskRepository.save(clarificationTask);
                    return CompletableFuture.completedFuture(
                        DepartmentChatResult.error(requestId, department, orchestrationResult.status(), orchestrationResult.reason(), resolvedBrain));
                }

                if (orchestrationResult.needsClarification()) {
                    String clarificationMessage = orchestrationResult.clarificationMessage();
                    saveMessage(department, "main-brain", resolvedBrain, clarificationMessage, "assistant", conversationId, null, null);
                    pushExecutionEventSafe(sessionId, null, "clarification_needed", Map.of(
                        "requestId", requestId, "message", clarificationMessage
                    ));
                    return CompletableFuture.completedFuture(
                        DepartmentChatResult.success(requestId, department, resolvedBrain, clarificationMessage, null, null, null, null, null));
                }

                DialogueDecision decision = orchestrationResult.decision();
                BrainRoutingDecision routingDecision = orchestrationResult.routingDecision();
                MainBrainTaskPlan mainBrainTaskPlan = orchestrationResult.mainBrainTaskPlan();
                Brain targetBrain = orchestrationResult.brain();

                String effectiveDepartment = routingDecision != null && routingDecision.primaryDepartment() != null
                    ? routingDecision.primaryDepartment() : department;
                String effectiveResolvedBrain = targetBrain != null ? targetBrain.getId() : resolvedBrain;

                List<EmployeeWorkAssignment> employeeAssignments = planEmployeeAssignments(
                    requestId, mainBrainTaskPlan, effectiveDepartment, sessionId, userId);
                PreparedAssignmentBatch preparedAssignmentBatch = prepareAssignmentBatch(
                    requestId, sessionId, mainBrainTaskPlan, effectiveDepartment, employeeAssignments);

                DepartmentExecutionResult executionResult = coordinateDepartmentExecution(requestId, preparedAssignmentBatch);

                if (executionResult != null && executionResult.executionId() != null
                    && !TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionResult.status())
                    && !TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status())) {
                    clarificationTask.setStatus(TaskStatus.IN_PROGRESS.getDbValue());
                    clarificationTask.setReadinessStatus("READY");
                    clarificationTask.setExecutionId(executionResult.executionId());
                    clarificationTask.setUpdatedAt(Instant.now());
                    taskRepository.save(clarificationTask);
                    log.info("Clarification task {} resumed as IN_PROGRESS, executionId={}",
                        clarificationTask.getTaskKey(), executionResult.executionId());
                }

                if (executionResult != null
                    && (TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionResult.status())
                        || TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status()))) {
                    return handleClarificationOrBlocked(requestId, effectiveDepartment, effectiveResolvedBrain,
                        userId, userName, enhancedMessage, sessionId, decision, routingDecision,
                        mainBrainTaskPlan, employeeAssignments, preparedAssignmentBatch, executionResult, conversationId);
                }

                return processWithBrain(requestId, effectiveDepartment, effectiveResolvedBrain,
                    targetBrain != null ? targetBrain : brain, enhancedMessage, sessionId,
                    userId, userName, decision, routingDecision, mainBrainTaskPlan,
                    employeeAssignments, preparedAssignmentBatch, executionResult, conversationId);
            });
    }

    @SuppressWarnings("unchecked")
    private List<String> extractClarificationQuestions(PreparedAssignmentBatch batch, DepartmentExecutionResult executionResult) {
        if (batch != null && batch.metadata() != null) {
            Object obj = batch.metadata().get("clarificationQuestions");
            if (obj instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        if (executionResult != null && executionResult.metadata() != null) {
            Object obj = executionResult.metadata().get("clarificationQuestions");
            if (obj instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractBlockingIssues(PreparedAssignmentBatch batch, DepartmentExecutionResult executionResult) {
        if (batch != null && batch.metadata() != null) {
            Object obj = batch.metadata().get("blockingIssues");
            if (obj instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        if (executionResult != null && executionResult.metadata() != null) {
            Object obj = executionResult.metadata().get("blockingIssues");
            if (obj instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    private String composeClarificationMessage(List<String> questions, MainBrainTaskPlan mainBrainTaskPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("我已经完成初步任务分析，但在正式执行前需要你确认以下问题：\n\n");
        for (int i = 0; i < questions.size(); i++) {
            sb.append(i + 1).append(". ").append(questions.get(i)).append("\n");
        }
        sb.append("\n你回复这些问题后，我会继续沿用当前任务规划推进。");
        if (mainBrainTaskPlan != null && mainBrainTaskPlan.goal() != null) {
            sb.append("\n\n当前任务目标：").append(mainBrainTaskPlan.goal());
        }
        return sb.toString();
    }

    private String composeBlockedMessage(List<String> blockingIssues, MainBrainTaskPlan mainBrainTaskPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务执行被阻塞，存在以下关键问题：\n\n");
        for (int i = 0; i < blockingIssues.size(); i++) {
            sb.append(i + 1).append(". ").append(blockingIssues.get(i)).append("\n");
        }
        sb.append("\n请解决以上问题后重新发起请求。");
        if (mainBrainTaskPlan != null && mainBrainTaskPlan.goal() != null) {
            sb.append("\n\n当前任务目标：").append(mainBrainTaskPlan.goal());
        }
        return sb.toString();
    }

    private void createClarificationTaskEntity(
            String requestId, String department, String userId,
            String taskKey, String executionId,
            MainBrainTaskPlan mainBrainTaskPlan,
            boolean isBlocked, List<String> questions, List<String> blockingIssues,
            String originalMessage) {
        try {
            TaskEntity entity = new TaskEntity();
            entity.setTaskId(requestId);
            entity.setTaskType(mainBrainTaskPlan != null ? mainBrainTaskPlan.taskType() : "clarification");
            entity.setDescription(originalMessage != null && originalMessage.length() > 500
                ? originalMessage.substring(0, 500) : originalMessage);
            entity.setPriority(5);
            entity.setStatus(isBlocked ? TaskStatus.BLOCKED.getDbValue() : TaskStatus.NEEDS_CLARIFICATION.getDbValue());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            entity.setUserId(userId);
            entity.setTaskKey(taskKey);
            entity.setExecutionId(executionId);
            entity.setDepartmentCode(department);
            entity.setSourceType("department_chat");
            entity.setReadinessStatus(isBlocked ? TaskStatus.BLOCKED.getDbValue() : TaskStatus.NEEDS_CLARIFICATION.getDbValue());
            entity.setClarificationQuestions(questions != null && !questions.isEmpty()
                ? String.join("\n", questions) : null);
            entity.setBlockingIssues(blockingIssues != null && !blockingIssues.isEmpty()
                ? String.join("\n", blockingIssues) : null);
            entity.setClarificationRequestedAt(Instant.now());
            if (mainBrainTaskPlan != null && mainBrainTaskPlan.planId() != null) {
                entity.setConversationId(mainBrainTaskPlan.planId());
            }
            taskRepository.save(entity);
            log.info("Created clarification TaskEntity: requestId={}, taskKey={}, executionId={}, status={}",
                requestId, taskKey, executionId, entity.getStatus());
        } catch (Exception e) {
            log.error("Failed to create clarification TaskEntity for requestId={}: {}", requestId, e.getMessage());
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "task_entity_create_failed", "DepartmentChatService",
                "Failed to create clarification TaskEntity",
                Map.of("taskKey", taskKey != null ? taskKey : "",
                    "executionId", executionId != null ? executionId : "",
                    "error", e.getMessage() != null ? e.getMessage() : "")
            ));
        }
    }

	private void createNormalTaskEntity(
            String requestId, String department, String userId,
            String taskKey, String executionId,
            MainBrainTaskPlan mainBrainTaskPlan,
            String originalMessage,
            String conversationId) {
        try {
            // 避免重复创建：如果已存在相同 executionId 的 TaskEntity 则跳过
            if (executionId != null && !taskRepository.findByExecutionId(executionId).isEmpty()) {
                log.info("TaskEntity already exists for executionId={}, skipping creation", executionId);
                return;
            }
            TaskEntity entity = new TaskEntity();
            entity.setTaskId(requestId);
            entity.setTaskType(mainBrainTaskPlan != null ? mainBrainTaskPlan.taskType() : "department_chat");
            entity.setDescription(originalMessage != null && originalMessage.length() > 500
                ? originalMessage.substring(0, 500) : originalMessage);
            entity.setPriority(5);
            entity.setStatus(TaskStatus.IN_PROGRESS.getDbValue());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            entity.setUserId(userId);
            entity.setTaskKey(taskKey);
            entity.setExecutionId(executionId);
            entity.setDepartmentCode(department);
            entity.setSourceType("department_chat");
            entity.setReadinessStatus("ready");
            entity.setConversationId(conversationId);
            if (mainBrainTaskPlan != null && mainBrainTaskPlan.planId() != null) {
                entity.setProjectId(mainBrainTaskPlan.planId());
            }
            taskRepository.save(entity);
            log.info("Created normal TaskEntity: requestId={}, taskKey={}, executionId={}, status={}",
                requestId, taskKey, executionId, TaskStatus.IN_PROGRESS.getDbValue());
        } catch (Exception e) {
            log.error("Failed to create normal TaskEntity for requestId={}: {}", requestId, e.getMessage());
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "task_entity_create_failed", "DepartmentChatService",
                "Failed to create normal TaskEntity",
                Map.of("taskKey", taskKey != null ? taskKey : "",
                    "executionId", executionId != null ? executionId : "",
                    "error", e.getMessage() != null ? e.getMessage() : "")
            ));
        }
    }

    private void updateTaskEntityStatus(String executionId, DepartmentExecutionResult responseExecutionResult) {
        try {
            Optional<TaskEntity> taskOpt = taskRepository.findByExecutionId(executionId).stream().findFirst();
            if (taskOpt.isEmpty()) {
                log.debug("No TaskEntity found for executionId={}, skipping status update", executionId);
                return;
            }
            TaskEntity entity = taskOpt.get();
            boolean accepted = isAcceptedCompletion(responseExecutionResult);
            String newStatus = accepted ? TaskStatus.COMPLETED.getDbValue() : TaskStatus.FAILED.getDbValue();
            entity.setStatus(newStatus);
            entity.setUpdatedAt(Instant.now());
            if (TaskStatus.COMPLETED.getDbValue().equalsIgnoreCase(newStatus)) {
                entity.setCompletedAt(Instant.now());
            }
            if (responseExecutionResult != null) {
                entity.setReadinessStatus(responseExecutionResult.status());
            }
            taskRepository.save(entity);
            log.info("Updated TaskEntity status: executionId={}, newStatus={}, readinessStatus={}",
                executionId, newStatus, entity.getReadinessStatus());
        } catch (Exception e) {
            log.error("Failed to update TaskEntity status for executionId={}: {}", executionId, e.getMessage());
            traceService.recordEvent(AutonomyTraceEvent.of(
                executionId, "task_entity_update_failed", "DepartmentChatService",
                "Failed to update TaskEntity status",
                Map.of("executionId", executionId, "error", e.getMessage() != null ? e.getMessage() : "")
            ));
        }
    }

    private CompletableFuture<DepartmentChatResult> processWithBrain(
            String requestId, String department, String resolvedBrain, Brain brain,
            String message, String sessionId, String userId, String userName,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            PreparedAssignmentBatch preparedAssignmentBatch,
            DepartmentExecutionResult executionResult,
            String conversationId) {

        CompletableFuture<DepartmentChatResult> future = new CompletableFuture<>();
        String outputChannel = brain.getPublishChannels().isEmpty()
            ? "channel://output/text"
            : brain.getPublishChannels().get(0);
        String subscriberId = "dept-chat-" + requestId;

        // 用于存储 brain.processWithContract() 返回的契约，供 ChannelMessage 回调使用
        final BrainOutputContract[] contractHolder = new BrainOutputContract[1];

        log.info("processWithBrain: requestId={}, department={}, sessionId={}, outputChannel={}, subscriberId={}, brainState={}",
            requestId, department, sessionId, outputChannel, subscriberId, brain.getState());

        ChannelSubscriber subscriber = new ChannelSubscriber() {
            @Override
            public void onMessage(ChannelMessage response) {
                log.info("dept-chat subscriber received message: subscriberId={}, responseSessionId={}, expectedSessionId={}, match={}",
                    subscriberId, response.getSessionId(), sessionId, sessionId.equals(response.getSessionId()));
                if (!sessionId.equals(response.getSessionId())) {
                    return;
                }

                String responseText = response.getContent();
                if (response.getType() == ChannelMessage.MessageType.ERROR) {
                    future.complete(DepartmentChatResult.error(
                        requestId, department, "SYSTEM_ERROR", responseText, resolvedBrain));
                    return;
                }
                future.complete(processBrainResponse(
                    requestId, department, resolvedBrain, responseText, userId, userName, message, response,
                    decision, routingDecision, mainBrainTaskPlan, employeeAssignments, preparedAssignmentBatch, executionResult,
                    conversationId, contractHolder[0]));
            }

            @Override
            public String getSubscriberId() {
                return subscriberId;
            }
        };

        try {
            if (conversationId != null && !conversationId.isBlank()) {
                injectConversationHistory(brain, sessionId, conversationId);
            }
            subscribeToBrainOutput(outputChannel, subscriber);
            ChannelMessage brainMessage = ChannelMessage.text(
                "channel://department/" + department,
                "gateway://department-chat",
                brain.getSubscribedChannels().isEmpty() ? "channel://" + department + "/tasks" : brain.getSubscribedChannels().get(0),
                sessionId,
                message
            );
            brainMessage.addMetadata("request_id", requestId);
            brainMessage.addMetadata("department", department);
            brainMessage.addMetadata("user_id", userId);
            brainMessage.addMetadata("entrypoint", "department_text_chat");
            brainMessage.addMetadata("message_kind", decision.kind().name());
            brainMessage.addMetadata("intent", decision.intent());
            if (routingDecision != null) {
                brainMessage.addMetadata("primary_department", routingDecision.primaryDepartment());
                brainMessage.addMetadata("primary_brain", routingDecision.primaryBrainId());
                brainMessage.addMetadata("supporting_departments", String.join(",", routingDecision.supportingDepartments()));
                brainMessage.addMetadata("rerouted_from_requested_department", String.valueOf(routingDecision.reroutedFromRequestedDepartment()));
            }
            if (mainBrainTaskPlan != null) {
                brainMessage.addMetadata("main_plan_id", mainBrainTaskPlan.planId());
                brainMessage.addMetadata("task_type", mainBrainTaskPlan.taskType());
                brainMessage.addMetadata("goal", mainBrainTaskPlan.goal());
                brainMessage.addMetadata("deliverables", String.join(",", mainBrainTaskPlan.deliverables()));
                brainMessage.addMetadata("acceptance_criteria", String.join(" | ", mainBrainTaskPlan.acceptanceCriteria()));
                mainBrainTaskPlan.departmentPlans().stream()
                    .filter(plan -> plan.department().equalsIgnoreCase(department))
                    .findFirst()
                    .ifPresent(plan -> {
                        brainMessage.addMetadata("department_objective", plan.objective());
                        brainMessage.addMetadata("suggested_roles", String.join(",", plan.suggestedRoles()));
                        brainMessage.addMetadata("suggested_employee_codes", String.join(",", plan.suggestedEmployeeCodes()));
                    });
            }
            if (preparedAssignmentBatch != null) {
                brainMessage.addMetadata("assignment_batch_id", preparedAssignmentBatch.batchId());
                brainMessage.addMetadata("assignment_batch_status", String.valueOf(preparedAssignmentBatch.metadata().get("executionReadiness")));
            }
            if (executionResult != null && !TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionResult.status()) && !TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status())) {
                brainMessage.addMetadata("execution_id", executionResult.executionId());
                brainMessage.addMetadata("execution_status", executionResult.status());
                brainMessage.addMetadata("execution_dispatch_count", String.valueOf(executionResult.dispatchedAssignments().size()));
                brainMessage.addMetadata("execution_target_channels", executionResult.dispatchedAssignments().stream()
                    .map(EmployeeExecutionDispatch::targetChannel)
                    .collect(Collectors.joining(",")));
            }
            if (employeeAssignments != null && !employeeAssignments.isEmpty()
                    && executionResult != null
                    && !TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionResult.status())
                    && !TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status())) {
                brainMessage.addMetadata("assignment_count", String.valueOf(employeeAssignments.size()));
                brainMessage.addMetadata("assignment_employee_codes", employeeAssignments.stream()
                    .map(EmployeeWorkAssignment::employeeCode)
                    .collect(Collectors.joining(",")));
                brainMessage.addMetadata("assignment_roles", employeeAssignments.stream()
                    .map(EmployeeWorkAssignment::role)
                    .collect(Collectors.joining(",")));
                brainMessage.addMetadata("assignment_neuron_ids", employeeAssignments.stream()
                    .map(EmployeeWorkAssignment::employeeNeuronId)
                    .collect(Collectors.joining(",")));
                brainMessage.addMetadata("assignment_instructions", employeeAssignments.stream()
                    .map(assignment -> assignment.employeeCode() + ":" + assignment.instruction().replace("\n", " "))
                    .collect(Collectors.joining(" || ")));
            }
            // 为正常执行路径创建 TaskEntity，建立 executionId 与 TaskEntity 的关联
            if (executionResult != null && executionResult.executionId() != null
                    && !TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionResult.status())
                    && !TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionResult.status())) {
                String taskKey = workItemKeyGenerator.generateTaskKey(department, userId,
                    mainBrainTaskPlan != null ? mainBrainTaskPlan.taskType() : "department_chat");
                createNormalTaskEntity(requestId, department, userId, taskKey,
                    executionResult.executionId(), mainBrainTaskPlan, message, conversationId);
                updateConversationContext(conversationId, taskKey, executionResult.executionId());
            }

            log.info("Calling brain.processWithContract(): requestId={}, brainId={}, sessionId={}, assignmentCount={}",
                requestId, brain.getId(), sessionId,
                employeeAssignments != null ? employeeAssignments.size() : 0);
            BrainOutputContract contract = brain.processWithContract(brainMessage);
            contractHolder[0] = contract;
            log.info("brain.processWithContract() completed: requestId={}, brainId={}, future.isDone={}, hasContract={}",
                requestId, brain.getId(), future.isDone(), contract != null);

            // 如果大脑返回了 BrainOutputContract 且 ChannelMessage 订阅者尚未完成 future，
            // 利用 contract 的结构化信息直接完成 future（适用于不走 ChannelMessage 回调的场景）
            if (contract != null && !future.isDone()) {
                // 对于 NEEDS_CLARIFICATION/BLOCKED/FAILED 等终态，直接用 contract 完成响应
                if (contract.isTerminal() || contract.needsUserInput()) {
                    String contractResponseText = contract.summary() != null ? contract.summary() : "";
                    future.complete(processBrainResponseWithContract(
                        requestId, department, resolvedBrain, contractResponseText, userId, userName, message,
                        contract, decision, routingDecision, mainBrainTaskPlan, employeeAssignments,
                        preparedAssignmentBatch, executionResult, conversationId));
                }
                // 对于 EXECUTING/READY 等非终态，等待 ChannelMessage 订阅者回调完成
            }
        } catch (Exception e) {
            future.complete(DepartmentChatResult.error(
                requestId, department, "SYSTEM_ERROR", "处理失败: " + e.getMessage(), resolvedBrain));
        }

        // P2-1: 根据 complexity 动态调整超时时间
        // complexity 1-2: 60秒, 3-4: 120秒, 5+: 180秒
        int timeoutSeconds = resolveTimeoutSeconds(decision != null ? decision.complexity() : 3);

        return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .exceptionally(e -> {
                String errorStatus;
                String errorReason;
                if (e instanceof TimeoutException) {
                    errorStatus = "TIMEOUT";
                    errorReason = "部门大脑响应超时";
                } else {
                    errorStatus = "SYSTEM_ERROR";
                    errorReason = "处理失败: " + e.getMessage();
                }
                saveMessage(department, "brain_" + department, resolvedBrain, errorReason, "assistant",
                    conversationId, null, null);
                return DepartmentChatResult.error(requestId, department, errorStatus, errorReason, resolvedBrain);
            })
            .whenComplete((ignored, error) -> unsubscribeFromBrainOutput(outputChannel, subscriberId));
    }

    private DepartmentChatResult processBrainResponse(
            String requestId, String department, String resolvedBrain, String responseText,
            String userId, String userName, String originalMessage, ChannelMessage response,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            PreparedAssignmentBatch preparedAssignmentBatch,
            DepartmentExecutionResult executionResult,
            String conversationId,
            BrainOutputContract contract) {
        if (responseText == null || responseText.isEmpty()) {
            return DepartmentChatResult.error(requestId, department, "NO_RESPONSE", "大脑未返回有效响应", resolvedBrain);
        }

        // 利用 BrainOutputContract 的 status 字段做差异化处理
        DepartmentChatResult contractResult = handleBrainOutputContract(
            requestId, department, resolvedBrain, conversationId, contract, decision, response);
        if (contractResult != null) return contractResult;

        List<EmployeeExecutionReceipt> receipts = collectExecutionReceipts(executionResult);
        String aggregatedResponse = aggregateExecutionResult(
            requestId, department, responseText, mainBrainTaskPlan, executionResult, receipts);

        traceService.recordEvent(AutonomyTraceEvent.ofWithKeys(
            requestId, "execution_receipts_aggregated", "DepartmentChatService",
            "Execution receipts collected and aggregated for response composition",
            Map.of(
                "executionId", executionResult != null && executionResult.executionId() != null ? executionResult.executionId() : "",
                "receiptCount", String.valueOf(receipts != null ? receipts.size() : 0),
                "aggregatedResponseLength", String.valueOf(aggregatedResponse != null ? aggregatedResponse.length() : 0)
            ),
            null,
            executionResult != null ? executionResult.executionId() : null
        ));

        DepartmentExecutionResult responseExecutionResult = enrichExecutionResultWithAggregation(
            executionResult, receipts, aggregatedResponse);

        // P0-2.2/P0-2.3: 重试逻辑闭环 + 换人逻辑
        RetryReassignmentOutcome retryOutcome = attemptRetryReassignment(
            requestId, department, mainBrainTaskPlan, executionResult,
            preparedAssignmentBatch, employeeAssignments, receipts, responseText, aggregatedResponse, responseExecutionResult);
        if (retryOutcome.updatedResponseExecutionResult() != null) {
            responseExecutionResult = retryOutcome.updatedResponseExecutionResult();
        }
        if (retryOutcome.updatedAggregatedResponse() != null) {
            aggregatedResponse = retryOutcome.updatedAggregatedResponse();
        }

        traceService.recordEvent(AutonomyTraceEvent.ofWithKeys(
            requestId, "execution_reviewed", "DepartmentChatService",
            "Execution result reviewed with aggregation enrichment",
            Map.of(
                "executionId", responseExecutionResult != null && responseExecutionResult.executionId() != null ? responseExecutionResult.executionId() : "",
                "reviewedStatus", responseExecutionResult != null ? responseExecutionResult.status() : "UNKNOWN",
                "acceptedCompletion", String.valueOf(responseExecutionResult != null && responseExecutionResult.metadata() != null ? responseExecutionResult.metadata().getOrDefault("acceptedCompletion", false) : false),
                "completionGate", String.valueOf(responseExecutionResult != null && responseExecutionResult.metadata() != null ? responseExecutionResult.metadata().getOrDefault("completionGate", "BLOCKED") : "BLOCKED")
            ),
            null,
            responseExecutionResult != null ? responseExecutionResult.executionId() : null
        ));

        // 最终响应策略与合成
        ComposedResponseOutcome composedOutcome = composeFinalResponse(
            requestId, department, originalMessage, resolvedBrain, response,
            decision, routingDecision, mainBrainTaskPlan, employeeAssignments,
            preparedAssignmentBatch, executionResult, responseExecutionResult, aggregatedResponse);
        String composedResponse = composedOutcome.composedResponse();
        FinalResponseCoordinator.FinalResponseStrategy strategy = composedOutcome.strategy();

        saveMessage(department, "brain_" + department, resolvedBrain, composedResponse, "assistant",
            conversationId,
            executionResult != null ? executionResult.executionId() : null,
            executionResult != null ? executionResult.executionId() : null);

        recordBrainResponseCompletion(requestId, department, resolvedBrain, decision,
            executionResult, responseExecutionResult, strategy, composedResponse, response);

        // 跨部门协调 + 知识/绩效沉淀
        handlePostExecutionActions(requestId, department, decision, mainBrainTaskPlan,
            employeeAssignments, preparedAssignmentBatch, responseExecutionResult,
            executionResult, aggregatedResponse);

        lastActivity.put(department, Instant.now());

        String finalExecutionId = executionResult != null && executionResult.executionId() != null
            ? executionResult.executionId() : "";
        pushExecutionEventSafe(response.getSessionId(), finalExecutionId, "finalized", Map.of(
            "requestId", requestId,
            "department", department,
            "intent", decision.intent() != null ? decision.intent() : "",
            "acceptedCompletion", String.valueOf(isAcceptedCompletion(responseExecutionResult))
        ));

        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "final_response_pushed", "DepartmentChatService",
            "Final response pushed to user, completing the execution loop",
            Map.of(
                "executionId", finalExecutionId,
                "department", department,
                "strategy", strategy != null ? strategy.name() : "UNKNOWN",
                "acceptedCompletion", String.valueOf(isAcceptedCompletion(responseExecutionResult)),
                "responseLength", String.valueOf(composedResponse != null ? composedResponse.length() : 0)
            )
        ));

        // 更新 TaskEntity 状态为 COMPLETED 或 FAILED
        if (executionResult != null && executionResult.executionId() != null) {
            updateTaskEntityStatus(executionResult.executionId(), responseExecutionResult);
        }

        return DepartmentChatResult.success(
            requestId,
            department,
            resolvedBrain,
            composedResponse,
            String.valueOf(response.getMetadata().getOrDefault("model", "department-brain")),
            "SUCCESS",
            decision.intent() != null ? decision.intent() : String.valueOf(response.getMetadata().getOrDefault("intent", "department_chat")),
            resolvedBrain,
            null,
            conversationId
        );
    }

    /** Handle BrainOutputContract status-based dispatch, returning early result or null to continue. */
    private DepartmentChatResult handleBrainOutputContract(
            String requestId, String department, String resolvedBrain, String conversationId,
            BrainOutputContract contract, DialogueDecision decision, ChannelMessage response) {
        if (contract == null) return null;
        log.info("processBrainResponse with BrainOutputContract: requestId={}, status={}, riskLevel={}",
            requestId, contract.status(), contract.riskLevel());

        switch (contract.status()) {
            case NEEDS_CLARIFICATION -> {
                List<String> questions = contract.clarificationQuestions();
                if (questions != null && !questions.isEmpty()) {
                    String clarificationMessage = "我需要更多信息来帮您完成任务：\n" + String.join("\n", questions);
                    saveMessage(department, "brain_" + department, resolvedBrain, clarificationMessage, "assistant",
                        conversationId, contract.taskKey(), contract.executionId());
                    pushExecutionEventSafe(response.getSessionId(), contract.executionId(), "clarification_needed", Map.of(
                        "requestId", requestId,
                        "message", clarificationMessage,
                        "source", "BrainOutputContract"
                    ));
                    return DepartmentChatResult.success(requestId, department, resolvedBrain, clarificationMessage, null,
                        TaskStatus.NEEDS_CLARIFICATION.getDbValue(),
                        decision.intent() != null ? decision.intent() : "department_chat", resolvedBrain, null);
                }
            }
            case BLOCKED -> {
                List<String> issues = contract.blockingIssues();
                String blockedMessage = issues != null && !issues.isEmpty()
                    ? "任务执行被阻塞：\n" + String.join("\n", issues)
                    : contract.summary() != null ? contract.summary() : "任务被阻塞";
                saveMessage(department, "brain_" + department, resolvedBrain, blockedMessage, "assistant",
                    conversationId, contract.taskKey(), contract.executionId());
                return DepartmentChatResult.success(requestId, department, resolvedBrain, blockedMessage, null,
                    TaskStatus.BLOCKED.getDbValue(),
                    decision.intent() != null ? decision.intent() : "department_chat", resolvedBrain, null);
            }
            case FAILED -> {
                String failedMessage = contract.summary() != null ? contract.summary() : "处理失败";
                saveMessage(department, "brain_" + department, resolvedBrain, failedMessage, "assistant",
                    conversationId, contract.taskKey(), contract.executionId());
                return DepartmentChatResult.error(requestId, department, "BRAIN_FAILED", failedMessage, resolvedBrain);
            }
            default -> { /* COMPLETED / EXECUTING / READY: continue */ }
        }
        return null;
    }

    /** Outcome of retry reassignment attempt. */
    private record RetryReassignmentOutcome(
        DepartmentExecutionResult updatedResponseExecutionResult,
        String updatedAggregatedResponse) {
        static RetryReassignmentOutcome noChange() { return new RetryReassignmentOutcome(null, null); }
        static RetryReassignmentOutcome of(DepartmentExecutionResult r, String a) { return new RetryReassignmentOutcome(r, a); }
    }

    /** Attempt retry reassignment when aggregation indicates needsRetry=true. */
    private RetryReassignmentOutcome attemptRetryReassignment(
            String requestId, String department, MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentExecutionResult executionResult, PreparedAssignmentBatch preparedAssignmentBatch,
            List<EmployeeWorkAssignment> employeeAssignments, List<EmployeeExecutionReceipt> receipts,
            String responseText, String aggregatedResponse,
            DepartmentExecutionResult responseExecutionResult) {
        ExecutionResultAggregator.AggregationResult currentAggregation =
            executionResult != null ? aggregationResultCache.get(executionResult.executionId()) : null;
        if (currentAggregation == null || !currentAggregation.needsRetry()
                || mainBrainTaskPlan == null || executionResult == null) {
            return RetryReassignmentOutcome.noChange();
        }
        try {
            ConversationOrchestrator.RetryOrchestrationResult retryResult =
                conversationOrchestrator.retryWithReassignment(
                    currentAggregation, mainBrainTaskPlan, receipts, 0);

            if (!retryResult.shouldRetry() || retryResult.reassignedAssignments().isEmpty()) {
                return RetryReassignmentOutcome.noChange();
            }
            log.info("Retry reassignment triggered for requestId={}, reassigned {} employees",
                requestId, retryResult.reassignedAssignments().size());

            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "retry_reassignment_triggered", "DepartmentChatService",
                "Retry reassignment triggered after aggregation detected failures",
                Map.of(
                    "retryRequestId", retryResult.retryRequestId(),
                    "reassignedCount", String.valueOf(retryResult.reassignedAssignments().size()),
                    "reassignedEmployeeCodes", retryResult.reassignedAssignments().stream()
                        .map(EmployeeWorkAssignment::employeeCode).collect(Collectors.joining(","))
                )
            ));

            return executeRetryReassignment(requestId, department, mainBrainTaskPlan,
                executionResult, preparedAssignmentBatch, receipts, responseText, responseExecutionResult,
                retryResult);
        } catch (Exception e) {
            log.warn("Retry reassignment failed for requestId={}, continuing with original result: {}",
                requestId, e.getMessage());
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "retry_reassignment_failed", "DepartmentChatService",
                "Retry reassignment failed: " + e.getMessage()
            ));
            return RetryReassignmentOutcome.noChange();
        }
    }

    /** Execute the retry reassignment with new assignments. */
    private RetryReassignmentOutcome executeRetryReassignment(
            String requestId, String department, MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentExecutionResult executionResult, PreparedAssignmentBatch preparedAssignmentBatch,
            List<EmployeeExecutionReceipt> receipts, String responseText,
            DepartmentExecutionResult responseExecutionResult,
            ConversationOrchestrator.RetryOrchestrationResult retryResult) {
        List<EmployeeWorkAssignment> reassignedAssignments = retryResult.reassignedAssignments();
        PreparedAssignmentBatch retryBatch = assignmentPreparationService.prepare(
            requestId,
            preparedAssignmentBatch != null ? preparedAssignmentBatch.sessionId() : "",
            department,
            mainBrainTaskPlan,
            mainBrainTaskPlan.departmentPlans().stream()
                .filter(p -> p.department().equalsIgnoreCase(department))
                .findFirst().orElse(null),
            reassignedAssignments
        );

        if (retryBatch == null) return RetryReassignmentOutcome.noChange();

        DepartmentExecutionResult retryExecutionResult =
            departmentExecutionCoordinator.coordinate(retryBatch);

        if (retryExecutionResult != null && retryExecutionResult.executionId() != null) {
            executionResultCache.put(retryExecutionResult.executionId(), retryExecutionResult);
            saveExecutionResultToDb(retryExecutionResult);
        }

        List<EmployeeExecutionReceipt> retryReceipts = collectExecutionReceipts(retryExecutionResult);

        ExecutionResultAggregator.AggregationResult retryAggregation =
            executionResultAggregator.aggregateStructured(
                retryExecutionResult.executionId(), department,
                mainBrainTaskPlan, retryReceipts, responseText);

        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "retry_execution_completed", "DepartmentChatService",
            "Retry execution completed and re-aggregated",
            Map.of(
                "retryExecutionId", retryExecutionResult.executionId(),
                "retryReceiptCount", String.valueOf(retryReceipts.size()),
                "retryOverallStatus", retryAggregation.overallStatus(),
                "retryAccepted", String.valueOf(retryAggregation.accepted()),
                "retryNeedsRetry", String.valueOf(retryAggregation.needsRetry())
            )
        ));

        if (retryAggregation.accepted() || !retryAggregation.needsRetry()) {
            List<EmployeeExecutionReceipt> mergedReceipts = new ArrayList<>(receipts);
            mergedReceipts.addAll(retryReceipts);

            Map<String, Object> retryMetadata = new LinkedHashMap<>(
                executionResult.metadata() != null ? executionResult.metadata() : Map.of());
            retryMetadata.put("retryExecutionId", retryExecutionResult.executionId());
            retryMetadata.put("retryCompletedCount", retryAggregation.completedCount());
            retryMetadata.put("retryFailedCount", retryAggregation.failedCount());
            retryMetadata.put("retryAccepted", retryAggregation.accepted());
            retryMetadata.put("retriedEmployeeCodes", reassignedAssignments.stream()
                .map(EmployeeWorkAssignment::employeeCode).collect(Collectors.joining(",")));
            retryMetadata.put("needsRetry", false);

            DepartmentExecutionResult updatedResult = new DepartmentExecutionResult(
                executionResult.executionId(),
                executionResult.batchId(),
                executionResult.department(),
                executionResult.resolveSessionId(),
                retryAggregation.accepted()
                    ? TaskStatus.COMPLETED.getDbValue()
                    : TaskStatus.PARTIALLY_COMPLETED.getDbValue(),
                executionResult.dispatchedAssignments(),
                retryMetadata
            );

            String updatedAggregated = null;
            if (retryAggregation.summaryForUser() != null
                    && !retryAggregation.summaryForUser().isBlank()) {
                updatedAggregated = retryAggregation.summaryForUser();
            }

            if (retryExecutionResult != null) {
                aggregationResultCache.put(retryExecutionResult.executionId(), retryAggregation);
            }

            pushExecutionEventSafe(
                preparedAssignmentBatch != null ? preparedAssignmentBatch.sessionId() : "",
                retryExecutionResult.executionId(), "retry_completed", Map.of(
                    "requestId", requestId,
                    "retryAccepted", String.valueOf(retryAggregation.accepted()),
                    "reassignedEmployeeCodes", reassignedAssignments.stream()
                        .map(EmployeeWorkAssignment::employeeCode).collect(Collectors.joining(","))
                ));

            return RetryReassignmentOutcome.of(updatedResult, updatedAggregated);
        }
        return RetryReassignmentOutcome.noChange();
    }

    /** Outcome of final response composition. */
    private record ComposedResponseOutcome(String composedResponse, FinalResponseCoordinator.FinalResponseStrategy strategy) {}

    /** Compose the final user-facing response using strategy determination. */
    private ComposedResponseOutcome composeFinalResponse(
            String requestId, String department, String originalMessage, String resolvedBrain,
            ChannelMessage response, DialogueDecision decision,
            BrainRoutingDecision routingDecision, MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            PreparedAssignmentBatch preparedAssignmentBatch,
            DepartmentExecutionResult executionResult,
            DepartmentExecutionResult responseExecutionResult, String aggregatedResponse) {
        String composedResponse = mainBrainResponseComposer.composeUserResponse(
            requestId, department, mainBrainTaskPlan, employeeAssignments, aggregatedResponse, responseExecutionResult);

        FinalResponseCoordinator.FinalResponseStrategy strategy = finalResponseCoordinator.determineStrategy(
            requestId, department, decision, routingDecision, mainBrainTaskPlan, responseExecutionResult);
        if (strategy == FinalResponseCoordinator.FinalResponseStrategy.MAIN_BRAIN_COMPOSE) {
            if (executionResult == null) {
                log.info("Skip main brain final summary for request {} because executionResult is null (single-department routing), using composed response", requestId);
            } else {
                try {
                    ExecutionResultAggregator.AggregationResult aggregationResult = executionResultAggregator.aggregateWithCompensation(
                        executionResult.executionId(), department, mainBrainTaskPlan, collectExecutionReceipts(executionResult), originalMessage, compensationService);
                    MainBrainFinalSummaryService.FinalSummaryResult summaryResult = mainBrainFinalSummaryService.generateSummary(
                        originalMessage,
                        decision,
                        mainBrainTaskPlan,
                        executionResult,
                        aggregationResult,
                        List.of(),
                        "accepted",
                        List.of(),
                        List.of()
                    );
                    if (summaryResult != null && summaryResult.userMessage() != null && !summaryResult.userMessage().isBlank()) {
                        composedResponse = summaryResult.userMessage();
                        log.info("Main brain final summary applied for request {}, source={}", requestId, summaryResult.summarySource());
                    }
                } catch (Exception e) {
                    log.warn("Main brain final summary failed, using composed response: {}", e.getMessage());
                }
            }
        } else if (strategy == FinalResponseCoordinator.FinalResponseStrategy.ESCALATE_TO_HUMAN) {
            composedResponse = "该任务执行结果需要人工审核确认，已转交人工处理。";
            log.info("Execution result escalated to human for request={}, department={}", requestId, department);
            pushExecutionEventSafe(preparedAssignmentBatch != null ? preparedAssignmentBatch.sessionId() : "", null, "escalate_to_human", Map.of(
                "requestId", requestId,
                "message", composedResponse,
                "strategy", "ESCALATE_TO_HUMAN"
            ));
        }

        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "main_brain_finalized", "DepartmentChatService",
            "Final response strategy determined and main brain processing completed",
            Map.of(
                "strategy", strategy != null ? strategy.name() : "UNKNOWN",
                "department", department
            )
        ));

        return new ComposedResponseOutcome(composedResponse, strategy);
    }

    /** Record brain response completion events and traces. */
    private void recordBrainResponseCompletion(String requestId, String department, String resolvedBrain,
            DialogueDecision decision, DepartmentExecutionResult executionResult,
            DepartmentExecutionResult responseExecutionResult,
            FinalResponseCoordinator.FinalResponseStrategy strategy,
            String composedResponse, ChannelMessage response) {
        if (executionResult != null && executionResult.executionId() != null) {
            Map<String, Object> completionEvent = new LinkedHashMap<>();
            completionEvent.put("requestId", requestId);
            completionEvent.put("department", department);
            completionEvent.put("brain", resolvedBrain);
            completionEvent.put("intent", decision.intent());
            completionEvent.put("acceptedCompletion", isAcceptedCompletion(responseExecutionResult));
            runtimeEventStore.appendConversationEvent(
                "_system", "_system", "_system", executionResult.executionId(),
                "brain_response_completed", completionEvent);
        }

        traceService.recordEvent(AutonomyTraceEvent.ofWithKeys(
            requestId, "result_aggregated", "DepartmentChatService",
            "Employee receipts reviewed and execution result aggregated before final response",
            buildResultMetadata(composedResponse, decision, null, null, null, responseExecutionResult),
            null,
            responseExecutionResult != null ? responseExecutionResult.executionId() : null
        ));
    }

    /** Handle cross-department coordination, artifact/knowledge/performance capture, and blocked execution notifications. */
    private void handlePostExecutionActions(String requestId, String department, DialogueDecision decision,
            MainBrainTaskPlan mainBrainTaskPlan, List<EmployeeWorkAssignment> employeeAssignments,
            PreparedAssignmentBatch preparedAssignmentBatch, DepartmentExecutionResult responseExecutionResult,
            DepartmentExecutionResult executionResult, String aggregatedResponse) {
        if (decision.requiresTaskExecution() && isAcceptedCompletion(responseExecutionResult)) {
            // 跨部门协调
            if (crossDepartmentCoordinator != null && mainBrainTaskPlan != null
                    && CrossDepartmentCoordinator.needsCrossDepartmentCoordination(mainBrainTaskPlan)) {
                try {
                    Map<String, DepartmentExecutionResult> deptResults = new LinkedHashMap<>();
                    if (responseExecutionResult != null) {
                        deptResults.put(department, responseExecutionResult);
                    }
                    CrossDepartmentCoordinator.CrossDepartmentResult crossResult =
                        crossDepartmentCoordinator.coordinate(requestId, mainBrainTaskPlan, deptResults);
                    traceService.recordEvent(AutonomyTraceEvent.of(
                        requestId, "cross_department_completed", "CrossDepartmentCoordinator",
                        "Cross-department coordination: status=" + crossResult.overallStatus()
                            + ", departments=" + crossResult.coordinatedDepartments().size(),
                        Map.of(
                            "sessionId", crossResult.sessionId(),
                            "overallStatus", crossResult.overallStatus(),
                            "coordinatedDepartments", String.join(",", crossResult.coordinatedDepartments()),
                            "failedDepartments", String.join(",", crossResult.failedDepartments())
                        )
                    ));
                    if (aggregatedResponse != null && !crossResult.departmentResults().isEmpty()) {
                        aggregatedResponse += "\n\n---\n**跨部门协调结果:**\n" + crossResult.collectSummaries();
                    }
                } catch (Exception e) {
                    log.warn("Cross-department coordination failed for requestId={}: {}", requestId, e.getMessage());
                }
            }

            captureArtifacts(requestId, department,
                preparedAssignmentBatch != null ? preparedAssignmentBatch.sessionId() : null,
                decision, mainBrainTaskPlan, responseExecutionResult, aggregatedResponse);
            captureKnowledge(requestId, department, decision, mainBrainTaskPlan, employeeAssignments, aggregatedResponse);
            capturePerformance(requestId, department, decision, mainBrainTaskPlan, employeeAssignments, responseExecutionResult);
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "knowledge_recorded", "ConversationOrchestrator",
                "Task execution passed receipt review - knowledge can be recorded",
                Map.of(
                    "taskType", mainBrainTaskPlan != null ? mainBrainTaskPlan.taskType() : "unknown",
                    "department", department
                )
            ));
        } else if (decision.requiresTaskExecution() && responseExecutionResult != null) {
            String blockedMessage = "⚠️ 任务执行未通过验收：";
            Object gateObj = responseExecutionResult.metadata() != null ? responseExecutionResult.metadata().get("completionGate") : null;
            if ("BLOCKED".equals(String.valueOf(gateObj))) {
                blockedMessage += "部分员工执行结果未达标或仍在处理中。";
            } else {
                blockedMessage += "执行过程中出现问题，请稍后重试或联系管理员。";
            }
            Object unmetObj = responseExecutionResult.metadata() != null ? responseExecutionResult.metadata().get("unmetCriteria") : null;
            if (unmetObj != null && !unmetObj.toString().isBlank()) {
                blockedMessage += "\n未满足条件: " + unmetObj;
            }
            String sessionForBlocked = preparedAssignmentBatch != null ? preparedAssignmentBatch.sessionId() : null;
            if (sessionForBlocked != null && !sessionForBlocked.isBlank()) {
                pushExecutionEventSafe(sessionForBlocked, responseExecutionResult.executionId(), "execution_blocked", Map.of(
                    "message", blockedMessage,
                    "completionGate", String.valueOf(gateObj)
                ));
            }
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "execution_blocked_user_notified", "DepartmentChatService",
                blockedMessage,
                Map.of("executionId", responseExecutionResult.executionId())
            ));
        } else if (decision.requiresTaskExecution()) {
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "completion_gate_blocked", "ExecutionResultAggregator",
                "Task execution did not pass completion gate; artifacts, knowledge and performance capture skipped",
                buildCompletionGateMetadata(responseExecutionResult)
            ));
        }
    }

    /**
     * NP1-4: 基于 BrainOutputContract 直接构建响应，统一委托到 processBrainResponse。
     * 从 Contract 构建合成 ChannelMessage，消除双通道重复逻辑。
     */
    private DepartmentChatResult processBrainResponseWithContract(
            String requestId, String department, String resolvedBrain, String responseText,
            String userId, String userName, String originalMessage,
            BrainOutputContract contract,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            PreparedAssignmentBatch preparedAssignmentBatch,
            DepartmentExecutionResult executionResult,
            String conversationId) {

        if (contract == null) {
            return DepartmentChatResult.error(requestId, department, "NO_CONTRACT", "大脑未返回结构化输出", resolvedBrain);
        }

        log.info("processBrainResponseWithContract: requestId={}, status={}, riskLevel={}",
            requestId, contract.status(), contract.riskLevel());

        // NP1-4: 从 Contract 构建合成 ChannelMessage，统一走 processBrainResponse
        String syntheticChannel = "channel://output/text";
        if (brainRegistry != null) {
            Optional<Brain> deptBrainOpt = brainRegistry.getByDepartment(department);
            if (deptBrainOpt.isPresent() && !deptBrainOpt.get().getPublishChannels().isEmpty()) {
                syntheticChannel = deptBrainOpt.get().getPublishChannels().get(0);
            }
        }
        ChannelMessage syntheticResponse = ChannelMessage.text(
            syntheticChannel,
            resolvedBrain,
            "channel://department/" + department,
            conversationId != null ? conversationId : requestId,
            responseText != null ? responseText : (contract.summary() != null ? contract.summary() : "")
        );
        syntheticResponse.addMetadata("brain_id", resolvedBrain);
        syntheticResponse.addMetadata("department", department);
        syntheticResponse.addMetadata("synthetic_from_contract", "true");

        return processBrainResponse(
            requestId, department, resolvedBrain, responseText, userId, userName, originalMessage,
            syntheticResponse, decision, routingDecision, mainBrainTaskPlan, employeeAssignments,
            preparedAssignmentBatch, executionResult, conversationId, contract);
    }

    /**
     * P2-1: 根据 complexity 动态调整超时时间。
     * complexity 1-2: 简单任务 60秒
     * complexity 3-4: 中等任务 120秒
     * complexity 5+: 复杂任务 180秒
     */
    private int resolveTimeoutSeconds(int complexity) {
        if (complexity <= 2) return 60;
        if (complexity <= 4) return 120;
        return 180;
    }

    /**
     * #6: 发布待办到部门待办池，支持员工自行领取。
     */
    private void publishDepartmentTodos(String requestId, MainBrainTaskPlan mainPlan,
                                          String department, String sessionId) {
        try {
            List<DepartmentTodoItem> items = new ArrayList<>();

            // 从 MainBrainTaskPlan 的部门计划中提取待办项
            if (mainPlan.departmentPlans() != null) {
                for (DepartmentTaskPlan deptPlan : mainPlan.departmentPlans()) {
                    if (deptPlan.department().equals(department)) {
                        // 为每个建议的员工角色创建待办项
                        for (String role : deptPlan.suggestedRoles()) {
                            String objective = deptPlan.objective() != null ? deptPlan.objective() : mainPlan.goal();
                            // 使用主计划的 requiredTools（ExecutionCapability 是枚举，没有 requiredTools 方法）
                            List<String> requiredTools = mainPlan.requiredTools() != null
                                ? mainPlan.requiredTools()
                                : List.of();
                            String taskType = mainPlan.taskType() != null ? mainPlan.taskType() : "general";

                            DepartmentTodoItem item = new DepartmentTodoItem(
                                "todo-" + UUID.randomUUID().toString().substring(0, 8),
                                department,
                                objective,
                                role + " 任务",  // instruction
                                List.of(role),  // requiredRoles
                                requiredTools,
                                List.of(role),  // requiredCapabilities
                                DepartmentTodoItem.Priority.MEDIUM,
                                requestId,      // planId
                                taskType        // taskType
                            );
                            items.add(item);
                        }
                    }
                }
            }

            if (!items.isEmpty()) {
                departmentTodoPool.publishAll(items);
                log.info("#6: Published {} todo items to pool for department={}, requestId={}",
                    items.size(), department, requestId);

                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "department_todo_published", "DepartmentChatService",
                    "Todo items published to pool",
                    Map.of("department", department, "count", items.size())
                ));

                // 窗口期后兜底指派（2秒）
                scheduleFallbackAssignment(department, items.size(), sessionId, requestId);
            }
        } catch (Exception e) {
            log.warn("#6: Failed to publish todo items: {}", e.getMessage());
        }
    }

    /**
     * #6: 窗口期后兜底指派未领取的待办。
     */
    private void scheduleFallbackAssignment(String department, int expectedCount,
                                              String sessionId, String requestId) {
        if (employeeSelfClaimService == null) return;

        // 2秒后检查未领取的待办，执行兜底指派
        java.util.concurrent.CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS)
            .execute(() -> {
                try {
                    List<TodoClaimResult> results = employeeSelfClaimService.assignUnclaimed(department);
                    if (!results.isEmpty()) {
                        log.info("#6: Fallback assignment for department={}: {} items assigned",
                            department, results.size());

                        traceService.recordEvent(AutonomyTraceEvent.of(
                            requestId, "todo_brain_assigned", "DepartmentChatService",
                            "Fallback assignment completed",
                            Map.of("department", department, "assignedCount", results.size())
                        ));

                        if (sessionId != null) {
                            pushExecutionEventSafe(sessionId, requestId, "todo_assigned", Map.of(
                                "department", department,
                                "assignedCount", results.size()
                            ));
                        }
                    }
                } catch (Exception e) {
                    log.warn("#6: Fallback assignment failed for department={}: {}", department, e.getMessage());
                }
            });
    }

    private Map<String, Object> buildWorkPlanMetadata(
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", decision.kind().name());
        metadata.put("intent", decision.intent());
        metadata.put("complexity", String.valueOf(decision.complexity()));
        metadata.put("riskLevel", String.valueOf(decision.riskLevel()));
        if (routingDecision != null) {
            metadata.put("primaryDepartment", routingDecision.primaryDepartment());
            metadata.put("primaryBrain", routingDecision.primaryBrainId());
            metadata.put("supportingDepartments", String.join(",", routingDecision.supportingDepartments()));
            metadata.put("reroutedFromRequestedDepartment", String.valueOf(routingDecision.reroutedFromRequestedDepartment()));
        }
        if (mainBrainTaskPlan != null) {
            metadata.put("planId", mainBrainTaskPlan.planId());
            metadata.put("taskType", mainBrainTaskPlan.taskType());
            metadata.put("goal", mainBrainTaskPlan.goal());
        }
        return metadata;
    }

    private Map<String, Object> buildAssignmentPlanMetadata(
            MainBrainTaskPlan mainBrainTaskPlan,
            String department,
            List<EmployeeWorkAssignment> employeeAssignments,
            PreparedAssignmentBatch preparedAssignmentBatch) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planId", mainBrainTaskPlan.planId());
        metadata.put("taskType", mainBrainTaskPlan.taskType());
        metadata.put("department", department);
        metadata.put("deliverables", String.join(",", mainBrainTaskPlan.deliverables()));
        metadata.put("acceptanceCriteria", String.join(" | ", mainBrainTaskPlan.acceptanceCriteria()));
        metadata.put("assignmentCount", String.valueOf(employeeAssignments.size()));
        metadata.put("assignmentEmployeeCodes", employeeAssignments.stream()
            .map(EmployeeWorkAssignment::employeeCode)
            .collect(Collectors.joining(",")));
        if (preparedAssignmentBatch != null) {
            metadata.put("assignmentBatchId", preparedAssignmentBatch.batchId());
            metadata.put("executionReadiness", String.valueOf(preparedAssignmentBatch.metadata().get("executionReadiness")));
        }
        mainBrainTaskPlan.departmentPlans().stream()
            .filter(plan -> plan.department().equalsIgnoreCase(department))
            .findFirst()
            .ifPresent(plan -> {
                metadata.put("objective", plan.objective());
                metadata.put("suggestedRoles", String.join(",", plan.suggestedRoles()));
                metadata.put("suggestedEmployeeCodes", String.join(",", plan.suggestedEmployeeCodes()));
            });
        return metadata;
    }

    private List<EmployeeWorkAssignment> planEmployeeAssignments(
            String requestId,
            MainBrainTaskPlan mainBrainTaskPlan,
            String department,
            String sessionId,
            String userId) {
        if (mainBrainTaskPlan == null) {
            return List.of();
        }
        Optional<DepartmentTaskPlan> departmentPlan = mainBrainTaskPlan.departmentPlans().stream()
            .filter(plan -> plan.department().equalsIgnoreCase(department))
            .findFirst();
        if (departmentPlan.isEmpty()) {
            return List.of();
        }
        List<EmployeeWorkAssignment> assignments = fixedEmployeeDispatcher.planAssignments(
            mainBrainTaskPlan,
            departmentPlan.get(),
            sessionId,
            userId
        );
        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "employee_assignment_planned", "FixedEmployeeDispatcher",
            "Fixed employee assignment plan generated",
            Map.of(
                "department", department,
                "assignmentCount", String.valueOf(assignments.size()),
                "employeeCodes", assignments.stream().map(EmployeeWorkAssignment::employeeCode).collect(Collectors.joining(","))
            )
        ));
        return assignments;
    }

    private Map<String, Object> buildSuggestedAssignmentMetadata(
            String department,
            List<EmployeeWorkAssignment> employeeAssignments) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("department", department);
        metadata.put("assignmentCount", String.valueOf(employeeAssignments.size()));
        metadata.put("employeeCodes", employeeAssignments.stream()
            .map(EmployeeWorkAssignment::employeeCode)
            .collect(Collectors.joining(",")));
        metadata.put("employeeNames", employeeAssignments.stream()
            .map(EmployeeWorkAssignment::employeeName)
            .collect(Collectors.joining(",")));
        metadata.put("roles", employeeAssignments.stream()
            .map(EmployeeWorkAssignment::role)
            .collect(Collectors.joining(",")));
        return metadata;
    }

    private Map<String, Object> buildEmployeeSelectionMetadata(EmployeeWorkAssignment assignment) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignmentId", assignment.assignmentId());
        metadata.put("department", assignment.department());
        metadata.put("employeeCode", assignment.employeeCode());
        metadata.put("employeeName", assignment.employeeName());
        metadata.put("employeeNeuronId", assignment.employeeNeuronId());
        metadata.put("role", assignment.role());
        metadata.put("expectedDeliverables", String.join(",", assignment.expectedDeliverables()));
        metadata.put("allowedTools", String.join(",", assignment.allowedTools()));
        return metadata;
    }

    private Map<String, Object> buildResultMetadata(
            String responseText,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            PreparedAssignmentBatch preparedAssignmentBatch,
            DepartmentExecutionResult executionResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", executionResult != null ? executionResult.status() : "SUCCESS");
        metadata.put("responseLength", String.valueOf(responseText != null ? responseText.length() : 0));
        metadata.put("intent", decision.intent());
        metadata.put("kind", decision.kind().name());
        if (routingDecision != null) {
            metadata.put("primaryDepartment", routingDecision.primaryDepartment());
            metadata.put("supportingDepartments", String.join(",", routingDecision.supportingDepartments()));
        }
        if (mainBrainTaskPlan != null) {
            metadata.put("planId", mainBrainTaskPlan.planId());
            metadata.put("taskType", mainBrainTaskPlan.taskType());
        }
        if (preparedAssignmentBatch != null) {
            metadata.put("assignmentBatchId", preparedAssignmentBatch.batchId());
            metadata.put("executionReadiness", String.valueOf(preparedAssignmentBatch.metadata().get("executionReadiness")));
        }
        if (executionResult != null) {
            metadata.put("executionId", executionResult.executionId());
            metadata.put("executionStatus", executionResult.status());
            metadata.put("dispatchedCount", String.valueOf(executionResult.dispatchedAssignments().size()));
            metadata.put("receiptCount", String.valueOf(executionResult.metadata().getOrDefault("receiptCount", "0")));
            metadata.put("acceptedCompletion", String.valueOf(executionResult.metadata().getOrDefault("acceptedCompletion", false)));
            metadata.put("aggregationSource", String.valueOf(executionResult.metadata().getOrDefault("aggregationSource", "none")));
        }
        return metadata;
    }

    private List<EmployeeExecutionReceipt> collectExecutionReceipts(DepartmentExecutionResult executionResult) {
        if (executionResult == null || executionResult.executionId() == null) {
            return List.of();
        }
        long deadline = System.currentTimeMillis() + RECEIPT_WAIT_TIMEOUT_MS;
        long adaptiveDeadline = deadline;
        long lastProgressPushTime = System.currentTimeMillis();
        List<EmployeeExecutionReceipt> receipts = List.of();
        int lastReceiptCount = 0;
        int totalExpected = executionResult.dispatchedAssignments().size();
        // 从 record 字段或 metadata 获取 sessionId
        String sessionId = executionResult.resolveSessionId();
        while (System.currentTimeMillis() <= adaptiveDeadline) {
            try {
                receipts = employeeExecutionReceiptService.getReceipts(executionResult.executionId());
                if (employeeExecutionReceiptService.isExecutionComplete(executionResult.executionId())
                    || receipts.size() >= executionResult.dispatchedAssignments().size()) {
                    return receipts;
                }
                if (receipts.size() > lastReceiptCount) {
                    lastReceiptCount = receipts.size();
                    adaptiveDeadline = Math.min(
                        System.currentTimeMillis() + 15_000,
                        deadline
                    );
                }
                // 定期推送等待进度提示（每 15 秒）
                long now = System.currentTimeMillis();
                if (now - lastProgressPushTime >= WAIT_PROGRESS_INTERVAL_MS && sessionId != null) {
                    int completed = (int) receipts.stream().filter(r -> r.status() == ReceiptStatus.COMPLETED).count();
                    int failed = (int) receipts.stream().filter(r -> r.status() == ReceiptStatus.FAILED).count();
                    int working = totalExpected - completed - failed;
                    String progressMsg = String.format("⏳ 任务执行中... 已完成 %d/%d，正在执行 %d 个", completed, totalExpected, working);
                    pushExecutionEventSafe(sessionId, executionResult.executionId(), "waiting_progress", Map.of(
                        "message", progressMsg,
                        "completedCount", completed,
                        "failedCount", failed,
                        "workingCount", working,
                        "totalCount", totalExpected,
                        "elapsedSeconds", (now - (deadline - RECEIPT_WAIT_TIMEOUT_MS)) / 1000
                    ));
                    lastProgressPushTime = now;
                }
            } catch (Exception e) {
                log.warn("Failed to collect execution receipts: executionId={}, error={}", executionResult.executionId(), e.getMessage());
                return receipts;
            }
            if (executionResult.dispatchedAssignments().isEmpty()) {
                return receipts;
            }
            try {
                Thread.sleep(RECEIPT_WAIT_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return receipts;
            }
        }
        traceService.recordEvent(AutonomyTraceEvent.of(
            executionResult.executionId(), "receipt_wait_timeout", "DepartmentChatService",
            "Timed out waiting for employee execution receipts before final response",
            Map.of(
                "executionId", executionResult.executionId(),
                "receiptCount", String.valueOf(receipts.size()),
                "expectedDispatchCount", String.valueOf(executionResult.dispatchedAssignments().size())
            )
        ));
        return receipts;
    }

    private String aggregateExecutionResult(
            String requestId,
            String department,
            String responseText,
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentExecutionResult executionResult,
            List<EmployeeExecutionReceipt> receipts) {
        if (executionResult == null || receipts == null || receipts.isEmpty()) {
            return responseText;
        }
        try {
            ExecutionResultAggregator.AggregationResult structured = executionResultAggregator.aggregateStructured(
                executionResult.executionId(), department, mainBrainTaskPlan, receipts, responseText);

            aggregationResultCache.put(executionResult.executionId(), structured);

            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "execution_receipts_aggregated", "ExecutionResultAggregator",
                "Execution receipts aggregated with acceptance criteria before response composition",
                Map.of(
                    "executionId", executionResult.executionId(),
                    "receiptCount", String.valueOf(receipts.size()),
                    "aggregationSource", executionResultAggregator.getClass().getSimpleName(),
                    "overallStatus", structured.overallStatus(),
                    "accepted", String.valueOf(structured.accepted()),
                    "qualityScore", String.format("%.2f", structured.qualityScore()),
                    "needsRetry", String.valueOf(structured.needsRetry()),
                    "needsHumanIntervention", String.valueOf(structured.needsHumanIntervention())
                )
            ));

            if (!structured.unmetCriteria().isEmpty()) {
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "unmet_criteria_detected", "ExecutionResultAggregator",
                    "Unmet acceptance criteria detected during aggregation",
                    Map.of(
                        "executionId", executionResult.executionId(),
                        "unmetCriteria", String.join("; ", structured.unmetCriteria())
                    )
                ));
            }

            return structured.summaryForUser() != null && !structured.summaryForUser().isBlank()
                ? structured.summaryForUser() : responseText;
        } catch (Exception e) {
            log.warn("Execution result aggregation failed: requestId={}, executionId={}, error={}",
                requestId, executionResult.executionId(), e.getMessage());
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "execution_receipts_aggregation_failed", "ExecutionResultAggregator",
                "Execution aggregation failed; falling back to department brain response",
                Map.of(
                    "executionId", executionResult.executionId(),
                    "fallbackReason", e.getMessage() != null ? e.getMessage() : "unknown"
                )
            ));
            return responseText;
        }
    }

    private final Map<String, ExecutionResultAggregator.AggregationResult> aggregationResultCache = new ConcurrentHashMap<>();

    private DepartmentExecutionResult enrichExecutionResultWithAggregation(
            DepartmentExecutionResult executionResult,
            List<EmployeeExecutionReceipt> receipts,
            String aggregatedResponse) {
        if (executionResult == null) {
            return null;
        }
        List<EmployeeExecutionReceipt> safeReceipts = receipts != null ? receipts : List.of();
        Map<String, Object> metadata = new LinkedHashMap<>(executionResult.metadata() != null ? executionResult.metadata() : Map.of());

        ExecutionResultAggregator.AggregationResult aggregation = aggregationResultCache.get(executionResult.executionId());

        long acceptedReceipts;
        long failedReceipts;
        long degradedReceipts = safeReceipts.stream().filter(r -> "DEGRADED".equals(r.status())).count();
        boolean acceptedCompletion;
        boolean needsRetry = false;
        boolean needsHumanIntervention = false;
        List<String> unmetCriteria = List.of();

        if (aggregation != null) {
            acceptedReceipts = aggregation.completedCount();
            failedReceipts = aggregation.failedCount();
            acceptedCompletion = aggregation.accepted()
                && !safeReceipts.isEmpty()
                && degradedReceipts == 0;
            needsRetry = aggregation.needsRetry();
            needsHumanIntervention = aggregation.needsHumanIntervention();
            unmetCriteria = aggregation.unmetCriteria();
        } else {
            acceptedReceipts = safeReceipts.stream().filter(this::isAcceptedReceipt).count();
            failedReceipts = safeReceipts.stream().filter(this::isFailedReceipt).count();
            acceptedCompletion = !safeReceipts.isEmpty()
                && acceptedReceipts == safeReceipts.size()
                && degradedReceipts == 0
                && !containsIncompleteStatus(executionResult.status())
                && aggregatedResponse != null
                && !aggregatedResponse.isBlank();
        }

        boolean allDispatchesReported = executionResult.dispatchedAssignments().isEmpty()
            || safeReceipts.stream().map(EmployeeExecutionReceipt::dispatchId).filter(Objects::nonNull).distinct().count()
                >= executionResult.dispatchedAssignments().size();

        String reviewedStatus = determineReviewedStatus(executionResult.status(), safeReceipts, acceptedCompletion, failedReceipts, allDispatchesReported);

        metadata.put("receiptCount", safeReceipts.size());
        metadata.put("acceptedReceiptCount", acceptedReceipts);
        metadata.put("failedReceiptCount", failedReceipts);
        metadata.put("degradedReceiptCount", degradedReceipts);
        metadata.put("allDispatchesReported", allDispatchesReported);
        metadata.put("acceptedCompletion", acceptedCompletion);
        metadata.put("aggregationSource", safeReceipts.isEmpty() ? "none" : executionResultAggregator.getClass().getSimpleName());
        metadata.put("completionGate", acceptedCompletion ? "PASSED" : "BLOCKED");
        metadata.put("needsRetry", needsRetry);
        metadata.put("needsHumanIntervention", needsHumanIntervention);
        if (!unmetCriteria.isEmpty()) {
            metadata.put("unmetCriteria", String.join("; ", unmetCriteria));
        }

        return new DepartmentExecutionResult(
            executionResult.executionId(),
            executionResult.batchId(),
            executionResult.department(),
            executionResult.resolveSessionId(),
            reviewedStatus,
            executionResult.dispatchedAssignments(),
            metadata
        );
    }

    private boolean isAcceptedCompletion(DepartmentExecutionResult executionResult) {
        if (executionResult == null) {
            return false;
        }
        Map<String, Object> meta = executionResult.metadata();
        Object accepted = meta != null ? meta.get("acceptedCompletion") : null;
        boolean acceptedFlag = Boolean.TRUE.equals(accepted) || "true".equalsIgnoreCase(String.valueOf(accepted));
        if (!acceptedFlag) return false;
        Object degraded = meta != null ? meta.get("degradedReceiptCount") : null;
        if (degraded instanceof Number n && n.longValue() > 0) return false;
        Object needsRetry = meta != null ? meta.get("needsRetry") : null;
        if (Boolean.TRUE.equals(needsRetry)) return false;
        Object needsHuman = meta != null ? meta.get("needsHumanIntervention") : null;
        if (Boolean.TRUE.equals(needsHuman)) return false;
        return true;
    }

    private Map<String, Object> buildCompletionGateMetadata(DepartmentExecutionResult executionResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (executionResult == null) {
            metadata.put("reason", "NO_EXECUTION_RESULT");
            return metadata;
        }
        metadata.put("executionId", executionResult.executionId());
        metadata.put("status", executionResult.status());
        metadata.put("receiptCount", String.valueOf(executionResult.metadata().getOrDefault("receiptCount", 0)));
        metadata.put("acceptedReceiptCount", String.valueOf(executionResult.metadata().getOrDefault("acceptedReceiptCount", 0)));
        metadata.put("completionGate", String.valueOf(executionResult.metadata().getOrDefault("completionGate", "BLOCKED")));
        return metadata;
    }

    private String determineReviewedStatus(
            String originalStatus,
            List<EmployeeExecutionReceipt> receipts,
            boolean acceptedCompletion,
            long failedReceipts,
            boolean allDispatchesReported) {
        if (acceptedCompletion) {
            return TaskStatus.COMPLETED.getDbValue();
        }
        if (containsIncompleteStatus(originalStatus)) {
            return originalStatus;
        }
        if (receipts == null || receipts.isEmpty() || !allDispatchesReported) {
            return TaskStatus.WAITING_RECEIPT.getDbValue();
        }
        if (failedReceipts >= receipts.size()) {
            return TaskStatus.FAILED.getDbValue();
        }
        return TaskStatus.PARTIALLY_COMPLETED.getDbValue();
    }

    private boolean isAcceptedReceipt(EmployeeExecutionReceipt receipt) {
        if (receipt == null) {
            return false;
        }
        if (receipt.status() != ReceiptStatus.COMPLETED) {
            return false;
        }
        return receipt.summary() != null && !receipt.summary().isBlank()
            && receipt.summary().trim().length() >= 12;
    }

    private boolean isFailedReceipt(EmployeeExecutionReceipt receipt) {
        if (receipt == null) {
            return true;
        }
        return receipt.status() == ReceiptStatus.FAILED
            || receipt.status() == ReceiptStatus.DEGRADED
            || receipt.status() == ReceiptStatus.NEEDS_RETRY;
    }

    private boolean containsIncompleteStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        return normalized.contains(TaskStatus.BLOCKED.getDbValue().toUpperCase(Locale.ROOT))
            || normalized.contains(TaskStatus.NEEDS_CLARIFICATION.getDbValue().toUpperCase(Locale.ROOT))
            || normalized.contains("DEGRADED")
            || normalized.contains("NEEDS_RETRY")
            || normalized.contains(TaskStatus.FAILED.getDbValue().toUpperCase(Locale.ROOT));
    }

    private PreparedAssignmentBatch prepareAssignmentBatch(
            String requestId,
            String sessionId,
            MainBrainTaskPlan mainBrainTaskPlan,
            String department,
            List<EmployeeWorkAssignment> employeeAssignments) {
        if (mainBrainTaskPlan == null || employeeAssignments == null || employeeAssignments.isEmpty()) {
            return null;
        }
        Optional<DepartmentTaskPlan> departmentPlan = mainBrainTaskPlan.departmentPlans().stream()
            .filter(plan -> plan.department().equalsIgnoreCase(department))
            .findFirst();
        if (departmentPlan.isEmpty()) {
            return null;
        }
        
        AssignmentReadinessEvaluator.ReadinessEvaluation readiness = 
            assignmentReadinessEvaluator.evaluate(mainBrainTaskPlan, departmentPlan.get(), employeeAssignments);
        
        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "readiness_evaluated", "AssignmentReadinessEvaluator",
            "Assignment readiness evaluated: " + readiness.status(),
            Map.of(
                "status", readiness.status().name(),
                "score", String.valueOf(readiness.readinessScore()),
                "blockingIssues", String.join("; ", readiness.blockingIssues()),
                "clarificationQuestions", String.join("; ", readiness.clarificationQuestions())
            )
        ));
        
        if (readiness.status() == AssignmentReadinessEvaluator.ReadinessStatus.BLOCKED) {
            log.warn("Assignment blocked for request {}: {}", requestId, readiness.blockingIssues());
            Map<String, Object> blockedMetadata = new LinkedHashMap<>();
            blockedMetadata.put("executionReadiness", "BLOCKED");
            blockedMetadata.put("blockingIssues", readiness.blockingIssues());
            blockedMetadata.put("readinessScore", readiness.readinessScore());
            return new PreparedAssignmentBatch(
                "blocked-" + requestId, requestId, sessionId, department,
                "blocked", "blocked", employeeAssignments, blockedMetadata
            );
        }
        
        if (readiness.status() == AssignmentReadinessEvaluator.ReadinessStatus.NEEDS_CLARIFICATION) {
            log.info("Assignment needs clarification for request {}: {}", requestId, readiness.clarificationQuestions());
            Map<String, Object> clarificationMetadata = new LinkedHashMap<>();
            clarificationMetadata.put("executionReadiness", "NEEDS_CLARIFICATION");
            clarificationMetadata.put("clarificationQuestions", readiness.clarificationQuestions());
            clarificationMetadata.put("readinessScore", readiness.readinessScore());
            return new PreparedAssignmentBatch(
                "clarification-" + requestId, requestId, sessionId, department,
                "clarification", "clarification", employeeAssignments, clarificationMetadata
            );
        }
        
        PreparedAssignmentBatch batch = assignmentPreparationService.prepare(
            requestId,
            sessionId,
            department,
            mainBrainTaskPlan,
            departmentPlan.get(),
            employeeAssignments
        );
        
        Map<String, Object> enhancedMetadata = new LinkedHashMap<>(batch.metadata());
        enhancedMetadata.put("readinessStatus", readiness.status().name());
        enhancedMetadata.put("readinessScore", readiness.readinessScore());
        
        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "assignment_batch_prepared", "AssignmentPreparationService",
            "Prepared assignment batch for department execution",
            Map.of(
                "batchId", batch.batchId(),
                "department", batch.department(),
                "assignmentCount", String.valueOf(batch.assignments().size()),
                "executionReadiness", String.valueOf(enhancedMetadata.get("executionReadiness")),
                "readinessStatus", readiness.status().name()
            )
        ));
        
        return new PreparedAssignmentBatch(
            batch.batchId(), batch.requestId(), batch.sessionId(), batch.department(),
            batch.taskType(), batch.goal(), batch.assignments(), enhancedMetadata
        );
    }

    private DepartmentExecutionResult coordinateDepartmentExecution(
            String requestId,
            PreparedAssignmentBatch preparedAssignmentBatch) {
        if (preparedAssignmentBatch == null) {
            return null;
        }
        
        String executionReadiness = String.valueOf(preparedAssignmentBatch.metadata().get("executionReadiness"));
        if (TaskStatus.BLOCKED.getDbValue().equalsIgnoreCase(executionReadiness) || TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(executionReadiness)) {
            log.info("Skipping department execution for request {}: readiness={}", requestId, executionReadiness);

            // 当执行被 BLOCKED 或 NEEDS_CLARIFICATION 时，清除冻结状态，允许用户追问重新规划
            String sessionIdFromBatch = preparedAssignmentBatch.sessionId();
            if (sessionIdFromBatch != null) {
                MainBrainTaskPlan removedPlan = activeSessionPlans.remove(sessionIdFromBatch);
                activePlanLastUpdated.remove(sessionIdFromBatch);
                if (removedPlan != null) {
                    log.info("Cleared frozen plan for session={} due to execution readiness={}, allowing re-planning",
                        sessionIdFromBatch, executionReadiness);
                }
            }

            Map<String, Object> skippedMetadata = new LinkedHashMap<>();
            skippedMetadata.put("executionReadiness", executionReadiness);
            skippedMetadata.put("skippedAt", Instant.now().toString());
            return new DepartmentExecutionResult(
                "skipped-" + requestId,
                preparedAssignmentBatch.batchId(),
                preparedAssignmentBatch.department(),
                preparedAssignmentBatch.sessionId(),
                executionReadiness,
                List.of(),
                skippedMetadata
            );
        }
        
        registerReceiptTraceSubscriber(requestId, preparedAssignmentBatch);
        DepartmentExecutionResult result = departmentExecutionCoordinator.coordinate(preparedAssignmentBatch);

        // 缓存 executionResult，供 onReceiptRecorded 监听路径回查
        if (result != null && result.executionId() != null) {
            executionResultCache.put(result.executionId(), result);
            saveExecutionResultToDb(result);

            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("requestId", requestId);
            eventData.put("executionId", result.executionId());
            eventData.put("batchId", result.batchId());
            eventData.put("department", result.department());
            eventData.put("status", result.status());
            eventData.put("dispatchedCount", result.dispatchedAssignments().size());
            runtimeEventStore.appendProjectEvent("_system", requestId, "department_execution_started", eventData);

            connectionRegistry.getSessionIdsByUserId(preparedAssignmentBatch != null ? 
                String.valueOf(preparedAssignmentBatch.metadata().getOrDefault("userId", "")) : "")
                .stream().findFirst()
                .ifPresent(sid -> connectionRegistry.bindExecution(sid, result.executionId()));
        }

        traceService.recordEvent(AutonomyTraceEvent.ofWithKeys(
            requestId, "employee_assigned", "DepartmentExecutionCoordinator",
            "Prepared assignments dispatched to employee task channels",
            Map.of(
                "executionId", result.executionId(),
                "batchId", result.batchId(),
                "department", result.department(),
                "status", result.status(),
                "dispatchedCount", String.valueOf(result.dispatchedAssignments().size())
            ),
            null,
            result.executionId()
        ));
        for (EmployeeExecutionDispatch dispatch : result.dispatchedAssignments()) {
            traceService.recordEvent(AutonomyTraceEvent.ofWithKeys(
                requestId, "employee_execution_started", "DepartmentExecutionCoordinator",
                "Employee task dispatch started for " + dispatch.employeeCode(),
                Map.of(
                    "executionId", result.executionId(),
                    "dispatchId", dispatch.dispatchId(),
                    "assignmentId", dispatch.assignmentId(),
                    "employeeCode", dispatch.employeeCode(),
                    "employeeNeuronId", dispatch.employeeNeuronId(),
                    "targetChannel", dispatch.targetChannel(),
                    "status", dispatch.status()
                ),
                null,
                result.executionId()
            ));
        }
        return result;
    }

    private void registerReceiptTraceSubscriber(String requestId, PreparedAssignmentBatch preparedAssignmentBatch) {
        if (preparedAssignmentBatch == null) {
            return;
        }
        String receiptChannel = "channel://department/" + sanitizePath(preparedAssignmentBatch.department()) + "/execution-receipts";
        String batchId = preparedAssignmentBatch.batchId();
        String subscriberId = "receipt-trace-" + requestId;
        channelManager.subscribe(receiptChannel, new ChannelSubscriber() {
            @Override
            public void onMessage(ChannelMessage message) {
                String messageBatchId = String.valueOf(message.getMetadata().getOrDefault("batch_id", ""));
                if (!batchId.equals(messageBatchId)) {
                    return;
                }
                String executionId = String.valueOf(message.getMetadata().getOrDefault("execution_id", ""));
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "employee_execution_receipt_received", "DepartmentChatService",
                    "Employee execution receipt observed from department receipt channel",
                    Map.of(
                        "executionId", executionId,
                        "batchId", batchId,
                        "dispatchId", String.valueOf(message.getMetadata().getOrDefault("dispatch_id", "")),
                        "employeeCode", String.valueOf(message.getMetadata().getOrDefault("employee_code", "")),
                        "status", String.valueOf(message.getMetadata().getOrDefault("status", "")),
                        "receiptChannel", receiptChannel
                    )
                ));
                if (!executionId.isBlank() && employeeExecutionReceiptService.isExecutionComplete(executionId)) {
                    channelManager.unsubscribe(receiptChannel, subscriberId);
                }
            }

            @Override
            public String getSubscriberId() {
                return subscriberId;
            }
        });
    }

    private void captureArtifacts(
            String requestId, String department, String sessionId,
            DialogueDecision decision, MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentExecutionResult executionResult, String responseText) {
        if (mainBrainTaskPlan == null || executionResult == null) return;

        for (String deliverable : mainBrainTaskPlan.deliverables()) {
            String artifactType = inferArtifactType(deliverable);
            String artifactPath = materializeArtifactFile(
                executionResult.executionId(), department, mainBrainTaskPlan.taskType(), deliverable, responseText);
            ArtifactRecord artifact = ArtifactRecord.of(
                executionResult.executionId(), department,
                "department_brain", "brain_" + department,
                artifactType,
                artifactPath,
                deliverable,
                "任务产物: " + deliverable
            );
            artifactRecordService.recordArtifact(artifact);
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "artifact_recorded", "ArtifactRecordService",
                "Artifact recorded and materialized: " + deliverable,
                Map.of(
                    "artifactId", artifact.artifactId(),
                    "type", artifact.type(),
                    "path", artifact.path(),
                    "name", artifact.name()
                )
            ));
            // 实时推送产物通知到前端
            try {
                departmentWebSocketHandler.sendArtifactMessage(
                    sessionId,
                    artifact.name(),
                    artifact.path(),
                    artifact.type(),
                    Map.of(
                        "artifactId", (Object) artifact.artifactId(),
                        "executionId", (Object) executionResult.executionId(),
                        "title", (Object) deliverable,
                        "description", (Object) ("任务产物: " + deliverable)
                    )
                );
            } catch (Exception e) {
                log.warn("Failed to send artifact notification via WebSocket: {}", e.getMessage());
            }
        }
    }

    private String materializeArtifactFile(
            String executionId, String department, String taskType, String deliverable, String responseText) {
        try {
            Path artifactDir = ARTIFACT_ROOT
                .resolve(sanitizePath(department))
                .resolve(sanitizePath(executionId));
            Files.createDirectories(artifactDir);

            String extension = inferFileExtension(deliverable, taskType);
            Path filePath = artifactDir.resolve(sanitizePath(deliverable) + extension);
            String content = buildArtifactContent(executionId, department, taskType, deliverable, responseText);
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            log.warn("Failed to materialize artifact file: executionId={}, deliverable={}, error={}",
                executionId, deliverable, e.getMessage());
            return "output://" + department + "/" + taskType + "/" + sanitizePath(deliverable);
        }
    }

    private String buildArtifactContent(
            String executionId, String department, String taskType, String deliverable, String responseText) {
        if (isHtmlDeliverable(deliverable, taskType)) {
            return extractCodeBlock(responseText, "html")
                .or(() -> extractFullHtmlDocument(responseText))
                .orElseGet(() -> buildHtmlArtifact(deliverable, responseText));
        }
        return extractCodeBlock(responseText, "markdown")
            .or(() -> extractCodeBlock(responseText, "md"))
            .orElse("# " + deliverable + "\n\n"
                + "- executionId: " + executionId + "\n"
                + "- department: " + department + "\n"
                + "- taskType: " + taskType + "\n\n"
                + "## 执行结果摘要\n\n"
                + (responseText != null ? responseText : ""));
    }

    private Optional<String> extractCodeBlock(String text, String language) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalizedLanguage = language != null ? language.toLowerCase(Locale.ROOT) : "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?is)```\\s*" + java.util.regex.Pattern.quote(normalizedLanguage) + "\\s*\\R(.*?)```"
        );
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String code = matcher.group(1).trim();
            if (!code.isBlank()) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractFullHtmlDocument(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?is)(<!doctype html.*?</html>|<html.*?</html>)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String html = matcher.group(1).trim();
            if (!html.isBlank()) {
                return Optional.of(html);
            }
        }
        return Optional.empty();
    }

    private String buildHtmlArtifact(String deliverable, String responseText) {
        return """
            <!doctype html>
            <html lang=\"zh-CN\">
            <head>
              <meta charset=\"utf-8\" />
              <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
              <title>%s</title>
              <style>
                body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #111827; color: #f9fafb; font-family: system-ui, sans-serif; }
                .ball { width: 96px; height: 96px; border-radius: 50%%; background: #ef4444; animation: bounce 900ms ease-in-out infinite alternate; box-shadow: 0 24px 50px rgba(239,68,68,.45); }
                @keyframes bounce { from { transform: translateY(0); } to { transform: translateY(-180px); } }
                .note { position: fixed; left: 24px; right: 24px; bottom: 24px; color: #9ca3af; white-space: pre-wrap; }
              </style>
            </head>
            <body>
              <div class=\"ball\" aria-label=\"red bouncing ball\"></div>
              <div class=\"note\">%s</div>
            </body>
            </html>
            """.formatted(escapeHtml(deliverable), escapeHtml(responseText != null ? responseText : ""));
    }

    private boolean isHtmlDeliverable(String deliverable, String taskType) {
        String normalized = ((deliverable != null ? deliverable : "") + " " + (taskType != null ? taskType : "")).toLowerCase(Locale.ROOT);
        return normalized.contains("html") || normalized.contains("网页") || normalized.contains("web") || normalized.contains("prototype");
    }

    private String inferFileExtension(String deliverable, String taskType) {
        if (isHtmlDeliverable(deliverable, taskType)) {
            return ".html";
        }
        return ".md";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private void captureKnowledge(
            String requestId, String department,
            DialogueDecision decision, MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments, String responseText) {
        if (mainBrainTaskPlan == null) return;

        List<String> employeeCodes = employeeAssignments != null
            ? employeeAssignments.stream().map(EmployeeWorkAssignment::employeeCode).toList()
            : List.of();

        KnowledgeCaptureResult result = knowledgeCaptureService.captureFromExecution(
            mainBrainTaskPlan.planId(),
            department,
            mainBrainTaskPlan.taskType(),
            mainBrainTaskPlan.goal(),
            responseText != null && responseText.length() > 200 ? responseText.substring(0, 200) : responseText,
            employeeCodes
        );

        if (result.success()) {
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "knowledge_recorded", "KnowledgeCaptureService",
                "Knowledge captured from execution",
                Map.of(
                    "knowledgeKey", result.knowledgeKey(),
                    "layer", result.layer(),
                    "domain", result.domain()
                )
            ));
        }
    }

    private void capturePerformance(
            String requestId, String department,
            DialogueDecision decision, MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            DepartmentExecutionResult executionResult) {
        if (mainBrainTaskPlan == null || employeeAssignments == null || employeeAssignments.isEmpty()) return;

        String executionId = executionResult != null ? executionResult.executionId() : mainBrainTaskPlan.planId();
        List<String> employeeCodes = employeeAssignments.stream()
            .map(EmployeeWorkAssignment::employeeCode)
            .toList();

        List<PerformanceCaptureResult> results = performanceCaptureService.captureFromExecution(
            executionId, department,
            mainBrainTaskPlan.taskType(), mainBrainTaskPlan.goal(),
            employeeCodes,
            executionResult != null ? executionResult.status() : "UNKNOWN"
        );

        for (PerformanceCaptureResult pcr : results) {
            if (pcr.success()) {
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "performance_recorded", "PerformanceCaptureService",
                    "Performance recorded for employee " + pcr.employeeCode(),
                    Map.of(
                        "employeeCode", pcr.employeeCode(),
                        "contributionType", pcr.contributionType()
                    )
                ));
            }
        }
    }

    private String inferArtifactType(String deliverable) {
        String lower = deliverable.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".js") || lower.endsWith(".ts")) return "code";
        if (lower.endsWith(".md") || lower.endsWith(".txt")) return "document";
        if (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml")) return "config";
        return "file";
    }

    private String sanitizePath(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void injectConversationHistory(Brain brain, String sessionId, String conversationId) {
        try {
            List<DepartmentChatMessageEntity> dbMessages =
                chatMessageRepository.findByConversationIdAndDeletedAtIsNullOrderByTimestampAsc(conversationId);
            if (dbMessages.isEmpty()) {
                log.debug("No history messages found for conversationId={}", conversationId);
                return;
            }
            List<com.livingagent.core.provider.Provider.ChatMessage> history = new java.util.ArrayList<>();
            for (DepartmentChatMessageEntity msg : dbMessages) {
                String role = msg.getRole();
                String content = msg.getContent();
                if (content == null || content.isBlank()) continue;
                if ("user".equals(role)) {
                    history.add(com.livingagent.core.provider.Provider.ChatMessage.user(content));
                } else if ("assistant".equals(role)) {
                    history.add(com.livingagent.core.provider.Provider.ChatMessage.assistant(content));
                } else if ("system".equals(role)) {
                    history.add(com.livingagent.core.provider.Provider.ChatMessage.system(content));
                }
            }
            if (!history.isEmpty()) {
                brain.injectSessionHistory(sessionId, history);
                log.info("Injected {} history messages into brain session={} convId={}",
                    history.size(), sessionId, conversationId);
            }
        } catch (Exception e) {
            log.warn("Failed to inject conversation history for convId={}: {}", conversationId, e.getMessage());
        }
    }

    private void subscribeToBrainOutput(String outputChannel, ChannelSubscriber subscriber) {
        channelManager.subscribe(outputChannel, subscriber);
    }

    private void pushExecutionEventSafe(String sessionId, String executionId, String eventType, Map<String, Object> eventData) {
        try {
            departmentWebSocketHandler.pushExecutionEvent(sessionId, executionId, eventType, eventData);
        } catch (Exception e) {
            log.debug("Failed to push execution event: eventType={}, error={}", eventType, e.getMessage());
        }
    }

    private void triggerFinalResponse(String executionId, String sessionId) {
        // 防止轮询路径和监听路径重复触发
        if (!triggeredFinalResponses.add(executionId)) {
            log.debug("Async final response already triggered for executionId={}, skipping", executionId);
            return;
        }
        try {
            List<EmployeeExecutionReceipt> receipts = employeeExecutionReceiptService.getReceipts(executionId);
            if (receipts == null || receipts.isEmpty()) {
                log.warn("No receipts found for async final response: executionId={}", executionId);
                return;
            }

            // #5: 检查是否有聚合结果，使用 DepartmentDeliverable 信息
            com.livingagent.core.autonomy.AggregationResult aggregationResult = departmentAggregationResultCache.remove(executionId);
            com.livingagent.core.autonomy.DepartmentDeliverable deliverable = aggregationResult != null && aggregationResult.success()
                ? aggregationResult.deliverable() : null;

            StringBuilder summary = new StringBuilder("📋 任务执行完成\n\n");

            // #5: 如果有聚合结果，先展示质量评分和交付项
            if (deliverable != null) {
                summary.append("## 部门级聚合结果\n");
                summary.append("- **质量评分**: ").append(String.format("%.2f", deliverable.overallQualityScore())).append("\n");
                if (deliverable.items() != null && !deliverable.items().isEmpty()) {
                    summary.append("- **交付项数量**: ").append(deliverable.items().size()).append("\n");
                    for (var item : deliverable.items()) {
                        summary.append("  - ").append(item.employeeName() != null ? item.employeeName() : item.employeeCode())
                            .append(" (").append(item.reviewPassed() ? "已通过" : "待审查").append(")\n");
                    }
                }
                if (deliverable.issues() != null && !deliverable.issues().isEmpty()) {
                    summary.append("- **发现问题**: ").append(deliverable.issues().size()).append(" 个\n");
                }
                summary.append("\n## 员工执行明细\n");
            }

            int completed = 0;
            int degraded = 0;
            int failed = 0;
            for (EmployeeExecutionReceipt r : receipts) {
                summary.append("- **").append(r.employeeCode() != null ? r.employeeCode() : "未知").append("**: ");
                if (r.status() == ReceiptStatus.COMPLETED) {
                    completed++;
                    summary.append("✅ 完成");
                } else if (r.status() == ReceiptStatus.DEGRADED) {
                    degraded++;
                    summary.append("⚠️ 降级完成（无真实文件产物）");
                } else {
                    failed++;
                    summary.append("❌ 失败");
                }
                if (r.summary() != null && !r.summary().isBlank()) {
                    summary.append(" — ").append(r.summary());
                }
                summary.append("\n");
            }
            summary.append("\n汇总: ").append(completed).append(" 完成, ")
                .append(degraded).append(" 降级, ").append(failed).append(" 失败");

            if (sessionId != null && !sessionId.isBlank()) {
                departmentWebSocketHandler.pushExecutionProgress(
                    sessionId, executionId, "COMPLETED",
                    receipts.size(), completed + degraded, failed
                );
                pushExecutionEventSafe(sessionId, executionId, "async_final_response", Map.of(
                    "completedCount", completed,
                    "degradedCount", degraded,
                    "failedCount", failed,
                    "summary", summary.toString()
                ));
            }

            log.info("Async final response sent for executionId={}: completed={}, degraded={}, failed={}",
                executionId, completed, degraded, failed);

            // 清理缓存，避免内存泄漏
            executionResultCache.remove(executionId);
            try { executionResultRepository.deleteByExecutionId(executionId); } catch (Exception e) { log.warn("B-0-1: failed to delete execution result from DB: {}", e.getMessage()); }

            // P2-4: 清理活跃计划映射，允许 session 发起新请求
            if (sessionId != null) {
                MainBrainTaskPlan removed = activeSessionPlans.remove(sessionId);
                activePlanLastUpdated.remove(sessionId);
                if (removed != null) {
                    log.info("Cleared active plan for session={} after completion, planId={}", sessionId, removed.planId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to trigger async final response for executionId={}: {}", executionId, e.getMessage());
        }
    }

    // ========== B-0-1: DepartmentExecutionResult DB 双写/查询 ==========

    private void saveExecutionResultToDb(DepartmentExecutionResult result) {
        try {
            DepartmentExecutionResultEntity entity = new DepartmentExecutionResultEntity();
            entity.setExecutionId(result.executionId());
            entity.setBatchId(result.batchId());
            entity.setDepartment(result.department());
            entity.setStatus(result.status());
            entity.setDispatchedAssignments(objectMapper.writeValueAsString(result.dispatchedAssignments()));
            entity.setMetadata(result.metadata() != null ? objectMapper.writeValueAsString(result.metadata()) : null);
            entity.setCreatedAt(java.time.Instant.now());
            entity.setUpdatedAt(java.time.Instant.now());
            executionResultRepository.save(entity);
        } catch (Exception e) {
            log.warn("B-0-1: failed to persist execution result to DB (executionId={}): {}", result.executionId(), e.getMessage());
        }
    }

    private DepartmentExecutionResult loadExecutionResultFromDb(String executionId) {
        try {
            var optEntity = executionResultRepository.findByExecutionId(executionId);
            if (optEntity.isPresent()) {
                DepartmentExecutionResultEntity entity = optEntity.get();
                List<EmployeeExecutionDispatch> assignments = null;
                if (entity.getDispatchedAssignments() != null) {
                    assignments = objectMapper.readValue(entity.getDispatchedAssignments(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, EmployeeExecutionDispatch.class));
                }
                Map<String, Object> metadata = null;
                if (entity.getMetadata() != null) {
                    metadata = objectMapper.readValue(entity.getMetadata(), Map.class);
                }
                return new DepartmentExecutionResult(
                    entity.getExecutionId(), entity.getBatchId(), entity.getDepartment(),
                    metadata != null && metadata.get("sessionId") != null ? String.valueOf(metadata.get("sessionId")) : null,
                    entity.getStatus(), assignments, metadata);
            }
        } catch (Exception e) {
            log.warn("B-0-1: failed to load execution result from DB (executionId={}): {}", executionId, e.getMessage());
        }
        return null;
    }

    public void bindSessionToConversation(String sessionId, String conversationId) {
        if (sessionId != null && conversationId != null) {
            conversationToSession.put(conversationId, sessionId);
            log.debug("Bound session {} to conversation {} for receipt routing", sessionId, conversationId);
        }
    }

    public DepartmentConversationEntity findOrCreateConversation(String department, String userId, String tenantId) {
        String title = generateConversationTitle(department);
        try {
            DepartmentConversationEntity conv = conversationService.createConversation(
                userId, department, tenantId, title);
            log.info("Created new conversation: convId={}, dept={}, user={}, title={}",
                conv.getConversationId(), department, userId, title);
            return conv;
        } catch (Exception e) {
            log.warn("Failed to create conversation via ConversationService, falling back: {}", e.getMessage());
            DepartmentConversationEntity conv = new DepartmentConversationEntity();
            conv.setConversationId(com.livingagent.core.util.IdUtils.generateConversationId());
            conv.setConversationKey(workItemKeyGenerator.generateTaskKey(department, userId, "conversation"));
            conv.setTenantId(tenantId);
            conv.setOwnerUserId(userId);
            conv.setDepartmentCode(department);
            conv.setTitle(title);
            conv.setStatus(ConversationStatus.ACTIVE.getDbValue());
            conv.setCreatedAt(Instant.now());
            conv.setUpdatedAt(Instant.now());
            conv.setLastActivityAt(Instant.now());
            conv.setRetentionPolicy("standard");
            conv = conversationRepository.save(conv);
            return conv;
        }
    }

    private String generateConversationTitle(String department) {
        String date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        return "对话 " + date + " - " + department;
    }

    public Optional<DepartmentConversationEntity> findConversation(String conversationId) {
        if (conversationId == null) return Optional.empty();
        try {
            return conversationRepository.findByConversationId(conversationId);
        } catch (Exception e) {
            log.warn("Failed to find conversation {}: {}", conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<DepartmentConversationEntity> listActiveConversations(String userId, String department) {
        try {
            List<String> activeStatuses = ConversationStatus.activeDbValues();
            log.debug("listActiveConversations: userId={}, department={}, activeStatuses={}", userId, department, activeStatuses);
            List<DepartmentConversationEntity> result;
            if (department != null) {
                result = conversationRepository.findByOwnerUserIdAndDepartmentCodeAndStatusInOrderByLastActivityAtDesc(
                    userId, department, activeStatuses);
            } else {
                result = conversationRepository.findByOwnerUserIdAndStatusInOrderByLastActivityAtDesc(
                    userId, activeStatuses);
            }
            log.debug("listActiveConversations result: {} conversations found", result.size());
            return result;
        } catch (Exception e) {
            log.warn("Failed to list conversations: {}", e.getMessage());
            return List.of();
        }
    }

    private void updateConversationLastMessage(String conversationId) {
        try {
            conversationRepository.findByConversationId(conversationId).ifPresent(conv -> {
                conv.setLastMessageAt(Instant.now());
                conv.setLastActivityAt(Instant.now());
                conv.setUpdatedAt(Instant.now());
                conversationRepository.save(conv);
            });
        } catch (Exception e) {
            log.debug("Failed to update conversation last message: {}", e.getMessage());
        }
    }

    public void updateConversationContext(String conversationId, String taskKey, String executionId) {
        try {
            conversationRepository.findByConversationId(conversationId).ifPresent(conv -> {
                if (taskKey != null) conv.setActiveTaskKey(taskKey);
                if (executionId != null) conv.setActiveExecutionId(executionId);
                conv.setLastActivityAt(Instant.now());
                conv.setUpdatedAt(Instant.now());
                conversationRepository.save(conv);
            });
        } catch (Exception e) {
            log.debug("Failed to update conversation context: {}", e.getMessage());
        }
    }

    public void archiveConversation(String conversationId) {
        try {
            conversationRepository.findByConversationId(conversationId).ifPresent(conv -> {
                conv.setStatus(ConversationStatus.ARCHIVED.getDbValue());
                conv.setArchivedAt(Instant.now());
                conv.setUpdatedAt(Instant.now());
                conversationRepository.save(conv);
            });
        } catch (Exception e) {
            log.warn("Failed to archive conversation {}: {}", conversationId, e.getMessage());
        }
    }

    public String resolveTaskKeyForConversation(String conversationId, String userId, String department) {
        if (conversationId == null) return null;
        try {
            Optional<DepartmentConversationEntity> convOpt = conversationRepository.findByConversationId(conversationId);
            if (convOpt.isPresent()) {
                DepartmentConversationEntity conv = convOpt.get();
                if (conv.getActiveTaskKey() != null && !conv.getActiveTaskKey().isBlank()) {
                    Optional<com.livingagent.core.database.entity.TaskEntity> taskOpt =
                        taskRepository.findByTaskKey(conv.getActiveTaskKey());
                    if (taskOpt.isPresent()) {
                        String taskStatus = taskOpt.get().getStatus();
                        if (TaskStatus.PENDING.getDbValue().equalsIgnoreCase(taskStatus) || "CHECKED_OUT".equalsIgnoreCase(taskStatus)
                            || TaskStatus.IN_PROGRESS.getDbValue().equalsIgnoreCase(taskStatus) || TaskStatus.NEEDS_CLARIFICATION.getDbValue().equalsIgnoreCase(taskStatus)
                            || "CLARIFICATION_PENDING".equalsIgnoreCase(taskStatus)) {
                            log.info("Reusing existing taskKey={} for conversationId={} (status={})",
                                conv.getActiveTaskKey(), conversationId, taskStatus);
                            return conv.getActiveTaskKey();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve taskKey for conversation {}: {}", conversationId, e.getMessage());
        }
        return null;
    }

    public void softDeleteConversation(String conversationId) {
        try {
            conversationRepository.findByConversationId(conversationId).ifPresent(conv -> {
                conv.setStatus(ConversationStatus.DELETED.getDbValue());
                conv.setDeletedAt(Instant.now());
                conv.setUpdatedAt(Instant.now());
                conversationRepository.save(conv);
            });
        } catch (Exception e) {
            log.warn("Failed to soft-delete conversation {}: {}", conversationId, e.getMessage());
        }
    }

    public List<ChatHistoryEntry> getConversationHistory(String conversationId, int limit) {
        List<ChatHistoryEntry> entries = new ArrayList<>();
        try {
            List<DepartmentChatMessageEntity> dbHistory =
                chatMessageRepository.findByConversationIdAndDeletedAtIsNullOrderByTimestampAsc(conversationId);
            for (DepartmentChatMessageEntity entity : dbHistory) {
                entries.add(new ChatHistoryEntry(
                    entity.getMessageId(),
                    entity.getDepartment(),
                    entity.getUserId(),
                    entity.getUserName(),
                    entity.getContent(),
                    entity.getRole(),
                    entity.getTimestamp()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to get conversation history for {}: {}", conversationId, e.getMessage());
        }
        if (entries.size() > limit) {
            entries = entries.subList(Math.max(0, entries.size() - limit), entries.size());
        }
        return entries;
    }

    private void unsubscribeFromBrainOutput(String outputChannel, String subscriberId) {
        channelManager.unsubscribe(outputChannel, subscriberId);
    }

    public Optional<Brain> getBrainByDepartment(String department) {
        return brainRegistry.getByDepartment(department);
    }

    private Optional<AuthSession> validateSession(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String sessionId = authorization.substring(7);
        return authService.validateSession(sessionId);
    }


    public ChatHistoryEntry saveMessage(String department, String userId, String userName, 
                                        String content, String role) {
        return saveMessage(department, userId, userName, content, role, null, null, null);
    }

    public ChatHistoryEntry saveMessage(String department, String userId, String userName,
                                        String content, String role,
                                        String conversationId, String taskKey, String executionId) {
        ChatHistoryEntry entry = new ChatHistoryEntry(
            UUID.randomUUID().toString(),
            department,
            userId,
            userName,
            content,
            role,
            Instant.now()
        );

        chatHistory.computeIfAbsent(department, k -> new ConcurrentLinkedDeque<>())
            .addLast(entry);

        Deque<ChatHistoryEntry> history = chatHistory.get(department);
        while (history.size() > MAX_HISTORY_PER_DEPARTMENT) {
            history.removeFirst();
        }

        try {
            DepartmentChatMessageEntity dbEntity = new DepartmentChatMessageEntity();
            dbEntity.setDepartment(department);
            dbEntity.setMessageId(entry.messageId());
            dbEntity.setUserId(userId);
            dbEntity.setUserName(userName);
            dbEntity.setContent(content);
            dbEntity.setRole(role);
            dbEntity.setTimestamp(entry.timestamp());
            dbEntity.setConversationId(conversationId);
            dbEntity.setTaskKey(taskKey);
            dbEntity.setExecutionId(executionId);
            dbEntity.setMessageType(role);
            chatMessageRepository.save(dbEntity);
        } catch (Exception e) {
            log.error("Failed to save chat message to database: dept={}, error={}", department, e.getMessage());
        }

        if (conversationId != null) {
            updateConversationLastMessage(conversationId);
        }

        lastActivity.put(department, Instant.now());

        log.debug("Saved chat message: dept={}, user={}, role={}, convId={}", 
            department, userId, role, conversationId);

        return entry;
    }

    public List<ChatHistoryEntry> getHistory(String department, int limit) {
        return getHistory(department, null, null, null, limit);
    }

    public List<ChatHistoryEntry> getHistory(String department, String userId, Instant start, Instant end, int limit) {
        List<ChatHistoryEntry> entries = new ArrayList<>();
        try {
            List<DepartmentChatMessageEntity> dbHistory;
            if (userId != null && start != null && end != null) {
                dbHistory = chatMessageRepository.findByDepartmentAndUserIdAndTimestampBetweenOrderByTimestampAsc(department, userId, start, end, PageRequest.of(0, limit));
            } else if (userId != null) {
                dbHistory = chatMessageRepository.findByDepartmentAndUserIdOrderByTimestampAsc(department, userId, PageRequest.of(0, limit));
            } else if (start != null && end != null) {
                dbHistory = chatMessageRepository.findByDepartmentAndTimestampBetweenOrderByTimestampAsc(department, start, end, PageRequest.of(0, limit));
            } else {
                dbHistory = chatMessageRepository.findByDepartmentOrderByTimestampDesc(department, PageRequest.of(0, limit));
                Collections.reverse(dbHistory);
            }

            entries = dbHistory.stream()
                    .map(e -> new ChatHistoryEntry(
                            e.getMessageId(),
                            e.getDepartment(),
                            e.getUserId(),
                            e.getUserName(),
                            e.getContent(),
                            e.getRole(),
                            e.getTimestamp()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get history from database, falling back to memory: dept={}, user={}, error={}", department, userId, e.getMessage());
            Deque<ChatHistoryEntry> history = chatHistory.get(department);
            if (history != null) {
                entries = history.stream()
                        .filter(entry -> userId == null || userId.equals(entry.userId()))
                        .filter(entry -> start == null || !entry.timestamp().isBefore(start))
                        .filter(entry -> end == null || !entry.timestamp().isAfter(end))
                        .sorted(Comparator.comparing(ChatHistoryEntry::timestamp))
                        .collect(Collectors.toList());
            }
        }

        if (entries.size() > limit) {
            entries = entries.subList(Math.max(0, entries.size() - limit), entries.size());
        }
        return entries;
    }

    public List<ChatHistoryEntry> getHistorySince(String department, Instant since) {
        return getHistory(department, null, since, Instant.now(), MAX_DB_HISTORY_PER_DEPARTMENT);
    }

    public void userJoined(String department, String userId) {
        onlineUsers.computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
            .add(userId);
        lastActivity.put(department, Instant.now());
        lastActivity.put(department + ":" + userId, Instant.now());

        log.info("User joined department chat: dept={}, user={}", department, userId);
    }

    public void userLeft(String department, String userId) {
        Set<String> users = onlineUsers.get(department);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                onlineUsers.remove(department);
            }
        }
        lastActivity.remove(department + ":" + userId);

        log.info("User left department chat: dept={}, user={}", department, userId);
    }

    public Set<String> getOnlineUsers(String department) {
        return new HashSet<>(onlineUsers.getOrDefault(department, Set.of()));
    }

    public int getOnlineCount(String department) {
        Set<String> users = onlineUsers.get(department);
        return users != null ? users.size() : 0;
    }

    public Map<String, Integer> getAllOnlineCounts() {
        Map<String, Integer> counts = new HashMap<>();
        onlineUsers.forEach((dept, users) -> counts.put(dept, users.size()));
        return counts;
    }

    public Instant getLastActivity(String department) {
        return lastActivity.get(department);
    }

    public void clearHistory(String department) {
        chatHistory.remove(department);
        
        // 清除数据库中的历史
        try {
            List<DepartmentChatMessageEntity> allMessages = chatMessageRepository.findByDepartmentOrderByTimestampDesc(department);
            chatMessageRepository.deleteAll(allMessages);
            log.info("Cleared all chat history for department from database: {}", department);
        } catch (Exception e) {
            log.error("Failed to clear chat history from database: dept={}, error={}", department, e.getMessage());
        }
    }
    
    /**
     * 清理数据库中超过保留期限的旧消息
     */
    public void cleanupOldMessages() {
        try {
            // 保留最近30天的消息
            Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(30));
            // 从数据库查询所有活跃的部门
            List<String> departments = chatMessageRepository.findDistinctDepartments();
            for (String department : departments) {
                chatMessageRepository.deleteByDepartmentAndTimestampBefore(department, cutoff);
            }
            log.info("Cleaned up old chat messages before {} for {} departments", cutoff, departments.size());
        } catch (Exception e) {
            log.error("Failed to cleanup old messages: {}", e.getMessage());
        }
    }

    public void cleanupOfflineUsers() {
        Instant now = Instant.now();
        onlineUsers.forEach((dept, users) -> {
            users.removeIf(userId -> {
                Instant lastSeen = lastActivity.get(dept + ":" + userId);
                if (lastSeen == null) {
                    return true;
                }
                return now.toEpochMilli() - lastSeen.toEpochMilli() > OFFLINE_TIMEOUT_MS;
            });
            if (users.isEmpty()) {
                onlineUsers.remove(dept);
            }
        });
    }

    public boolean canAccessDepartment(String department, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return false;
        }

        AuthContext ctx = sessionOpt.get().authContext();
        return departmentAccessService.hasDepartmentAccess(ctx, department);
    }

    public DepartmentChatSummary getSummary(String department) {
        Deque<ChatHistoryEntry> history = chatHistory.get(department);
        Set<String> users = onlineUsers.get(department);
        Instant activity = lastActivity.get(department);

        return new DepartmentChatSummary(
            department,
            history != null ? history.size() : 0,
            users != null ? users.size() : 0,
            activity
        );
    }

    public List<DepartmentChatSummary> getAllSummaries() {
        Set<String> departments = new HashSet<>();
        departments.addAll(chatHistory.keySet());
        departments.addAll(onlineUsers.keySet());

        return departments.stream()
            .map(this::getSummary)
            .collect(Collectors.toList());
    }

    public record DepartmentChatResult(
        boolean success,
        String requestId,
        String department,
        String brain,
        String text,
        String model,
        String status,
        String reason,
        String intent,
        String neuron,
        String conversationId,
        String executionId
    ) {
        public static DepartmentChatResult success(
                String requestId, String department, String brain,
                String text, String model, String status,
                String intent, String neuron, String reason, String conversationId) {
            return new DepartmentChatResult(true, requestId, department, brain, text, model, status, reason, intent, neuron, conversationId, null);
        }

        public static DepartmentChatResult success(
                String requestId, String department, String brain,
                String text, String model, String status,
                String intent, String neuron, String reason) {
            return new DepartmentChatResult(true, requestId, department, brain, text, model, status, reason, intent, neuron, null, null);
        }

        /** P2-4: 带 executionId 的成功结果 */
        public static DepartmentChatResult success(
                String requestId, String department, String brain,
                String text, String model, String status,
                String intent, String neuron, String reason, String conversationId, String executionId) {
            return new DepartmentChatResult(true, requestId, department, brain, text, model, status, reason, intent, neuron, conversationId, executionId);
        }

        public static DepartmentChatResult error(
                String requestId, String department, String status, String reason, String brain) {
            return new DepartmentChatResult(false, requestId, department, brain, null, null, status, reason, null, null, null, null);
        }
    }

    public record ChatHistoryEntry(
        String messageId,
        String department,
        String userId,
        String userName,
        String content,
        String role,
        Instant timestamp
    ) {
        public String toJson() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return "{}";
            }
        }
    }

    public record DepartmentChatSummary(
        String department,
        int messageCount,
        int onlineCount,
        Instant lastActivity
    ) {}
}
