package com.livingagent.gateway.service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.model.ModelStatus;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.nativelib.AudioNative;
import com.livingagent.core.neuron.chat.ChatNeuronRouter;
import com.livingagent.core.neuron.chat.ChatNeuronRouter.RoutingResult;
import com.livingagent.core.neuron.impl.NeuronCoordinator;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.security.Department;
import com.livingagent.gateway.service.WorkspaceContext;

@Service
public class AgentService {
    
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    
    private final ModelManager modelManager;
    private final NeuronRegistry neuronRegistry;
    private final ChannelManager channelManager;
    private final ChatNeuronRouter chatNeuronRouter;
    private final NeuronCoordinator coordinator;
    private final DepartmentChatService departmentChatService;
    private final BrainRegistry brainRegistry;
    private final ConcurrentHashMap<String, SessionContext> activeSessions;
    private final ConcurrentHashMap<String, AudioNative.Processor> audioProcessors;
    /** Sessions where audio processing is unavailable (Native lib not loaded) */
    private final Set<String> audioDisabledSessions = ConcurrentHashMap.newKeySet();

    /** 挂起会话：断线后暂不销毁，等待重连 */
    private final ConcurrentHashMap<String, SuspendedSession> suspendedSessions;
    /** 挂起会话超时时间（毫秒） */
    private static final long SUSPEND_TIMEOUT_MS = 5 * 60 * 1000;
    /** 清理挂起会话的调度器 */
    private final ScheduledExecutorService cleanupScheduler;

    /** Agent WebSocket Handler 引用，用于推送进度消息 */
    private volatile com.livingagent.gateway.websocket.AgentWebSocketHandler agentWebSocketHandler;

    public AgentService(ModelManager modelManager, NeuronRegistry neuronRegistry,
                        ChannelManager channelManager, ChatNeuronRouter chatNeuronRouter,
                        NeuronCoordinator coordinator,
                        DepartmentChatService departmentChatService,
                        BrainRegistry brainRegistry) {
        this.modelManager = modelManager;
        this.neuronRegistry = neuronRegistry;
        this.channelManager = channelManager;
        this.chatNeuronRouter = chatNeuronRouter;
        this.coordinator = coordinator;
        this.departmentChatService = departmentChatService;
        this.brainRegistry = brainRegistry;
        this.activeSessions = new ConcurrentHashMap<>();
        this.audioProcessors = new ConcurrentHashMap<>();
        this.suspendedSessions = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-suspended-cleanup");
            t.setDaemon(true);
            return t;
        });
        // 每60秒清理超时的挂起会话
        this.cleanupScheduler.scheduleAtFixedRate(
            this::cleanupSuspendedSessions, 60, 60, TimeUnit.SECONDS);
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AgentService shutdown complete");
    }

    /**
     * 注入 AgentWebSocketHandler 引用（避免循环依赖）
     */
    public void setAgentWebSocketHandler(com.livingagent.gateway.websocket.AgentWebSocketHandler handler) {
        this.agentWebSocketHandler = handler;
    }
    
    public void startSession(String sessionId) {
        startSession(sessionId, null);
    }
    
    public void startSession(String sessionId, AccessLevel accessLevel) {
        startSession(sessionId, accessLevel, null);
    }
    
    public void startSession(String sessionId, AccessLevel accessLevel, String departmentId) {
        SessionContext context = activeSessions.computeIfAbsent(sessionId,
            id -> new SessionContext(id, accessLevel, departmentId));
        activeSessions.put(sessionId, context);

        String coordinatorSessionId = coordinator.createSession();
        context.setCoordinatorSessionId(coordinatorSessionId);

        neuronRegistry.get("neuron://chat/qwen3/001").ifPresent(chatNeuron -> {
            coordinator.bindNeuronToSession(coordinatorSessionId, chatNeuron.getId());
            log.info("Bound chat neuron to coordinator session: {}", coordinatorSessionId);
        });

        try {
            AudioNative.Processor audioProcessor = new AudioNative.Processor(16000, 1, 960, true);
            audioProcessors.put(sessionId, audioProcessor);
        } catch (UnsatisfiedLinkError e) {
            log.warn("AudioNative not available, audio processing disabled for session: {}", sessionId);
            audioDisabledSessions.add(sessionId);
        }

        CompletableFuture<Void> sessionReadyFuture = modelManager.createSession(sessionId)
            .orTimeout(20, TimeUnit.SECONDS)
            .thenAccept(session -> {
                context.setModelSession(session);
                log.info("Session started: {}, coordinatorSession={}, accessLevel={}, departmentId={}",
                    sessionId, coordinatorSessionId, accessLevel, departmentId);
            })
            .whenComplete((v, e) -> {
                if (e != null) {
                    log.error("Failed to start session: {}", sessionId, e);
                }
            });
        context.setSessionReadyFuture(sessionReadyFuture);
    }
    
    public void attachUserIdentity(String sessionId, String userId) {
        SessionContext context = activeSessions.get(sessionId);
        if (context != null) {
            context.setUserId(userId);
            log.info("Attached user identity to session {}: {}", sessionId, userId);
        }
    }

    public void endSession(String sessionId) {
        SessionContext context = activeSessions.remove(sessionId);
        if (context != null) {
            String coordinatorSessionId = context.getCoordinatorSessionId();
            if (coordinatorSessionId != null) {
                coordinator.destroySession(coordinatorSessionId);
            }
            modelManager.destroySession(sessionId);
            log.info("Session ended: {}", sessionId);
        }
        
        AudioNative.Processor processor = audioProcessors.remove(sessionId);
        audioDisabledSessions.remove(sessionId);
        if (processor != null) {
            processor.close();
        }
    }

    /**
     * 挂起会话：断线时暂不销毁，保留状态等待重连
     * @return 挂起成功返回 true，会话不存在返回 false
     */
    public boolean suspendSession(String sessionId) {
        SessionContext context = activeSessions.remove(sessionId);
        if (context == null) {
            return false;
        }

        SuspendedSession suspended = new SuspendedSession(
            context,
            audioProcessors.remove(sessionId),
            Instant.now()
        );
        suspendedSessions.put(sessionId, suspended);
        log.info("Session suspended: {}, will expire at {}", sessionId,
            Instant.now().plusMillis(SUSPEND_TIMEOUT_MS));
        return true;
    }

    /**
     * 恢复挂起的会话：重连时恢复之前的状态
     * @return 恢复成功返回 true，无挂起会话返回 false
     */
    public boolean resumeSession(String sessionId) {
        SuspendedSession suspended = suspendedSessions.remove(sessionId);
        if (suspended == null) {
            return false;
        }

        SessionContext context = suspended.context();
        activeSessions.put(sessionId, context);

        if (suspended.audioProcessor() != null) {
            audioProcessors.put(sessionId, suspended.audioProcessor());
        }

        log.info("Session resumed: {}, was suspended for {}ms",
            sessionId, Instant.now().toEpochMilli() - suspended.suspendedAt().toEpochMilli());
        return true;
    }

    /**
     * 检查是否有指定 sessionId 的挂起会话
     */
    public boolean hasSuspendedSession(String sessionId) {
        SuspendedSession suspended = suspendedSessions.get(sessionId);
        if (suspended == null) {
            return false;
        }
        // 检查是否超时
        if (Instant.now().isAfter(suspended.suspendedAt().plusMillis(SUSPEND_TIMEOUT_MS))) {
            return false;
        }
        return true;
    }

    /**
     * 清理超时的挂起会话
     */
    public void cleanupSuspendedSessions() {
        Instant now = Instant.now();
        List<String> expired = new ArrayList<>();

        for (Map.Entry<String, SuspendedSession> entry : suspendedSessions.entrySet()) {
            if (now.isAfter(entry.getValue().suspendedAt().plusMillis(SUSPEND_TIMEOUT_MS))) {
                expired.add(entry.getKey());
            }
        }

        for (String sessionId : expired) {
            SuspendedSession suspended = suspendedSessions.remove(sessionId);
            if (suspended != null) {
                // 真正销毁会话
                SessionContext context = suspended.context();
                String coordinatorSessionId = context.getCoordinatorSessionId();
                if (coordinatorSessionId != null) {
                    coordinator.destroySession(coordinatorSessionId);
                }
                modelManager.destroySession(sessionId);

                AudioNative.Processor processor = suspended.audioProcessor();
                if (processor != null) {
                    processor.close();
                }

                log.info("Suspended session expired and cleaned up: {}", sessionId);
            }
        }

        if (!expired.isEmpty()) {
            log.info("Cleaned up {} expired suspended sessions, remaining: {}",
                expired.size(), suspendedSessions.size());
        }
    }

    /**
     * 获取挂起会话的上下文信息（用于重连时恢复对话历史）
     */
    public List<Map<String, String>> getSuspendedSessionHistory(String sessionId) {
        SuspendedSession suspended = suspendedSessions.get(sessionId);
        if (suspended != null) {
            return suspended.context().getHistory();
        }
        return null;
    }

    /**
     * 推送进度消息到 Agent WebSocket 客户端
     * @param sessionId 会话ID
     * @param stage 阶段标识（如 processing_started, brain_executing, tool_executing, completed）
     * @param message 进度描述
     * @param progress 进度百分比 0-100
     */
    public void pushProgress(String sessionId, String stage, String message, int progress) {
        pushProgress(sessionId, stage, message, progress, null);
    }

    /**
     * 推送进度消息到 Agent WebSocket 客户端（带额外数据）
     */
    public void pushProgress(String sessionId, String stage, String message, int progress, Map<String, Object> extra) {
        if (agentWebSocketHandler != null) {
            try {
                agentWebSocketHandler.sendProgressMessage(sessionId, stage, message, progress, extra);
            } catch (Exception e) {
                log.debug("Failed to push progress: sessionId={}, stage={}, error={}", sessionId, stage, e.getMessage());
            }
        }
    }
    
    public CompletableFuture<Map<String, Object>> processTextAsync(String sessionId, String text, String channel) {
        SessionContext context = activeSessions.get(sessionId);
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }

        if (context.getAccessLevel() == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Access level not initialized"
            ));
        }
        
        context.incrementMessageCount();

        // 推送进度：开始处理
        pushProgress(sessionId, "processing_started", "开始处理请求", 10);

        CompletableFuture<Void> sessionReadyFuture = context.getSessionReadyFuture();
        if (sessionReadyFuture != null) {
            if (!sessionReadyFuture.isDone()) {
                try {
                    sessionReadyFuture.get(8, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException timeoutException) {
                    log.warn("Session {} model initialization timed out before processing", sessionId);
                    return CompletableFuture.completedFuture(Map.of(
                        "type", "initializing",
                        "message", "模型会话仍在初始化，请1-2秒后重试"
                    ));
                } catch (Exception e) {
                    log.error("Session {} model initialization failed", sessionId, e);
                    return CompletableFuture.completedFuture(Map.of(
                        "type", "error",
                        "message", "模型会话初始化失败，请重新进入部门后重试"
                    ));
                }
            }

            if (sessionReadyFuture.isCompletedExceptionally()) {
                return CompletableFuture.completedFuture(Map.of(
                    "type", "error",
                    "message", "模型会话初始化失败，请重新进入部门后重试"
                ));
            }
        }
        
        AccessLevel accessLevel = context.getAccessLevel();
        String departmentId = context.getDepartmentId();
        String userId = context.getUserId();

        Map<String, Object> routingContext = new HashMap<>();
        routingContext.put("channel", channel);
        routingContext.put("accessLevel", accessLevel);
        routingContext.put("departmentId", departmentId);
        routingContext.put("userId", userId);
        routingContext.put("hasUserIdentity", context.hasUserIdentity());

        // 登录后部门通道：走自治编排（ConversationOrchestrator）
        // 未登录公共通道：走 ChatNeuronRouter（闲聊神经元）
        boolean isPublicChannel = channel != null && channel.contains("public");

        if (!isPublicChannel && departmentId != null && !departmentId.isBlank()) {
            // 登录后部门文本对话：走自治编排
            log.info("Session {} department text chat: routing to ConversationOrchestrator via DepartmentChatService, dept={}", sessionId, departmentId);
            pushProgress(sessionId, "brain_executing", "部门大脑处理中", 30);
            return processWithOrchestration(sessionId, text, channel, context);
        }

        // 未登录闲聊：走 ChatNeuronRouter
        RoutingResult routing = chatNeuronRouter.route(sessionId, text, routingContext);
        
        if (!routing.isPermissionGranted()) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "permission_denied",
                "sessionId", sessionId,
                "message", routing.getPermissionDeniedReason(),
                "requiredLevel", getRequiredLevelForIntent(routing.getIntent()),
                "currentLevel", accessLevel.name()
            ));
        }
        
        Neuron targetNeuron = routing.getNeuron();
        if (targetNeuron == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "No available neuron for routing"
            ));
        }
        
        return processWithNeuron(sessionId, text, channel, routing, targetNeuron, context);
    }

    /**
     * 登录后部门文本对话：走自治编排（ConversationOrchestrator）
     * 通过 DepartmentChatService 间接调用，复用完整的编排+执行+回执+总结流程
     */
    private CompletableFuture<Map<String, Object>> processWithOrchestration(
            String sessionId, String text, String channel, SessionContext context) {

        String departmentId = context.getDepartmentId();
        String userId = context.getUserId();
        String requestId = java.util.UUID.randomUUID().toString();
        String resolvedBrain = Department.mapDepartmentToBrain(departmentId);

        pushProgress(sessionId, "brain_executing", "大脑编排处理中: " + resolvedBrain, 40, Map.of("brain", resolvedBrain));

        java.util.Optional<Brain> brainOpt = brainRegistry.getByDepartment(departmentId);
        if (brainOpt.isEmpty()) {
            log.warn("No brain found for department {}, falling back to direct LLM call for session {}", departmentId, sessionId);
            Map<String, Object> routingContext = new HashMap<>();
            routingContext.put("channel", channel);
            routingContext.put("accessLevel", context.getAccessLevel());
            routingContext.put("departmentId", departmentId);
            routingContext.put("userId", userId);
            return processWithBrain(sessionId, text, channel, context, routingContext);
        }

        Brain brain = brainOpt.get();
        if (brain.getState() != Brain.BrainState.RUNNING) {
            log.warn("Brain {} not running (state={}), falling back to direct LLM for session {}", brain.getId(), brain.getState(), sessionId);
            Map<String, Object> routingContext = new HashMap<>();
            routingContext.put("channel", channel);
            routingContext.put("accessLevel", context.getAccessLevel());
            routingContext.put("departmentId", departmentId);
            routingContext.put("userId", userId);
            return processWithBrain(sessionId, text, channel, context, routingContext);
        }

        return departmentChatService.processDepartmentBrainAsync(
                requestId, departmentId, resolvedBrain, brain,
                text, sessionId, userId != null ? userId : "anonymous", null, null, null)
            .thenApply(chatResult -> {
                Map<String, Object> result = new HashMap<>();
                result.put("sessionId", sessionId);
                result.put("accessLevel", context.getAccessLevel().name());

                if (chatResult.success()) {
                    result.put("type", "response");
                    result.put("text", chatResult.text());
                    result.put("model", chatResult.model() != null ? chatResult.model() : resolvedBrain);
                    result.put("intent", chatResult.intent() != null ? chatResult.intent() : "department_chat");
                    result.put("brain", chatResult.brain());
                    result.put("orchestrated", true);

                    context.addHistory("user", text);
                    if (chatResult.text() != null) {
                        context.addHistory("assistant", chatResult.text());
                    }
                } else {
                    result.put("type", "error");
                    result.put("message", chatResult.reason() != null ? chatResult.reason() : "编排处理失败");
                    result.put("status", chatResult.status());
                }

                return result;
            })
            .exceptionally(e -> {
                log.error("Orchestration failed for session {}, falling back to direct LLM: {}", sessionId, e.getMessage());
                Map<String, Object> routingContext = new HashMap<>();
                routingContext.put("channel", channel);
                routingContext.put("accessLevel", context.getAccessLevel());
                routingContext.put("departmentId", departmentId);
                routingContext.put("userId", userId);
                try {
                    return processWithBrain(sessionId, text, channel, context, routingContext).join();
                } catch (Exception fallbackEx) {
                    return Map.of("type", "error", "message", "编排和降级处理均失败: " + fallbackEx.getMessage());
                }
            });
    }

    /**
     * 降级路径：直接调用 LLM，不经过自治编排
     * 用于自治编排失败时的降级，以及语音链路
     */
    private CompletableFuture<Map<String, Object>> processWithBrain(String sessionId, String text,
            String channel, SessionContext context, Map<String, Object> routingContext) {
        
        log.info("Session {} department brain chat: direct LLM call for channel={}, input='{}'", 
            sessionId, channel, text);

        pushProgress(sessionId, "brain_executing", "直接调用大脑处理", 40);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, String>> history = context.getHistory();
                ModelResponse modelResponse = modelManager
                    .processChatWithIntent(sessionId, text, history)
                    .get(120, java.util.concurrent.TimeUnit.SECONDS);
                
                Map<String, Object> result = new HashMap<>();
                result.put("type", "response");
                result.put("sessionId", sessionId);
                result.put("accessLevel", context.getAccessLevel().name());
                result.put("directToBrain", true);
                
                if (modelResponse.isSuccess() && modelResponse.getText() != null && !modelResponse.getText().isEmpty()) {
                    result.put("text", modelResponse.getText());
                    result.put("model", modelResponse.getModel());
                    context.addHistory("user", text);
                    context.addHistory("assistant", modelResponse.getText());
                } else {
                    result.put("text", "抱歉，我暂时无法处理您的请求。请稍后再试。");
                    result.put("model", "fallback-error");
                }
                return result;
            } catch (Exception e) {
                log.error("Department brain LLM call failed for session {}: {}", sessionId, e.getMessage());
                return Map.of(
                    "type", "error",
                    "message", "处理失败: " + e.getMessage()
                );
            }
        });
    }
    
    private CompletableFuture<Map<String, Object>> processWithNeuron(String sessionId, String text,
            String channel, RoutingResult routing, Neuron neuron, SessionContext context) {

        pushProgress(sessionId, "brain_executing", "神经元处理中: " + routing.getTargetNeuron(), 30,
            Map.of("neuron", routing.getTargetNeuron(), "intent", routing.getIntent()));

        String coordinatorSessionId = context.getCoordinatorSessionId();
        if (coordinatorSessionId == null) {
            log.warn("No coordinator session for {}, falling back to direct model call", sessionId);
            return fallbackToModel(sessionId, routing, context);
        }

        Map<String, Object> userContext = new HashMap<>();
        userContext.put("originalSessionId", sessionId);
        userContext.put("accessLevel", routing.getAccessLevel().name());
        userContext.put("departmentId", context.getDepartmentId());
        userContext.put("userId", context.getUserId());
        userContext.put("intent", routing.getIntent());

        log.info("Publishing to coordinator perception channel: session={}, coordinatorSession={}, input='{}'",
            sessionId, coordinatorSessionId, text);

        // Bind target neuron to coordinator session so it receives messages from perception channel
        if (neuron != null) {
            coordinator.bindNeuronToSession(coordinatorSessionId, neuron.getId());
            log.info("Bound target neuron {} to coordinator session {}", neuron.getId(), coordinatorSessionId);
        }

        String responseChannelId = "channel://response/" + coordinatorSessionId;
        final java.util.concurrent.atomic.AtomicReference<ChannelMessage> responseRef =
            new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        log.info("Subscribing response_waiter to channel BEFORE publishing input: {}", responseChannelId);
        channelManager.subscribe(responseChannelId, new com.livingagent.core.channel.ChannelSubscriber() {
            @Override
            public void onMessage(ChannelMessage message) {
                if (message.getType() == ChannelMessage.MessageType.TEXT ||
                    message.getType() == ChannelMessage.MessageType.CONTROL) {
                    log.info("response_waiter received message from {}: {}", message.getSourceNeuronId(), message.getContent());
                    responseRef.set(message);
                    latch.countDown();
                }
            }

            @Override
            public String getSubscriberId() {
                return "response_waiter_" + sessionId;
            }
        });

        coordinator.publishUserInput(coordinatorSessionId, text, userContext);

        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean received = latch.await(120, java.util.concurrent.TimeUnit.SECONDS);

                channelManager.unsubscribe(responseChannelId, "response_waiter_" + sessionId);

                Map<String, Object> result = new HashMap<>();
                result.put("type", "response");
                result.put("sessionId", sessionId);
                result.put("intent", routing.getIntent());
                result.put("neuron", routing.getTargetNeuron());
                result.put("accessLevel", routing.getAccessLevel().name());

                if (received && responseRef.get() != null) {
                    ChannelMessage response = responseRef.get();
                    String responseText = response.getContent();

                    if (responseText == null || responseText.isEmpty()) {
                        log.warn("Session {} received empty response from neuron {}", sessionId, response.getSourceNeuronId());
                        responseText = "抱歉，我暂时无法生成回复。";
                    }

                    result.put("text", responseText);
                    result.put("model", response.getMetadata().getOrDefault("model", "qwen3-0.6b"));
                    result.put("processedBy", response.getSourceNeuronId());
                    result.put("viaChannel", true);

                    context.addHistory("user", routing.getOriginalInput());
                    context.addHistory("assistant", responseText);

                    log.info("Session {} received response from {} via channel: {} chars",
                        sessionId, response.getSourceNeuronId(), responseText.length());
                } else {
                    log.warn("Session {} timeout waiting for neuron response after 120s", sessionId);
                    result.put("text", "抱歉，响应超时了。请稍后再试。");
                    result.put("model", "timeout");
                    result.put("timeout", true);
                }

                return result;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for response: {}", sessionId, e);
                return Map.of(
                    "type", "error",
                    "message", "Processing interrupted"
                );
            } catch (Exception e) {
                log.error("Error waiting for response: {}", sessionId, e);
                return Map.of(
                    "type", "error",
                    "message", "Error processing message: " + e.getMessage()
                );
            }
        });
    }

    private CompletableFuture<Map<String, Object>> fallbackToModel(String sessionId,
            RoutingResult routing, SessionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, String>> history = context.getHistory();
                ModelResponse modelResponse = modelManager
                    .processChatWithIntent(sessionId, routing.getOriginalInput(), history)
                    .get(120, java.util.concurrent.TimeUnit.SECONDS);

                Map<String, Object> result = new HashMap<>();
                result.put("type", "response");
                result.put("sessionId", sessionId);
                result.put("intent", routing.getIntent());
                result.put("neuron", routing.getTargetNeuron());
                result.put("accessLevel", routing.getAccessLevel().name());
                result.put("fallback", true);

                if (modelResponse.isSuccess() && modelResponse.getText() != null && !modelResponse.getText().isEmpty()) {
                    result.put("text", modelResponse.getText());
                    result.put("model", modelResponse.getModel());
                    context.addHistory("user", routing.getOriginalInput());
                    context.addHistory("assistant", modelResponse.getText());
                } else {
                    result.put("text", "抱歉，我暂时无法处理您的请求。请稍后再试。");
                    result.put("model", "fallback-error");
                }
                return result;
            } catch (Exception e) {
                log.error("Fallback model call failed for session {}: {}", sessionId, e.getMessage());
                return Map.of(
                    "type", "error",
                    "message", "处理失败: " + e.getMessage()
                );
            }
        });
    }
    
    private String getRequiredLevelForIntent(String intent) {
        return switch (intent) {
            case "TOOL_CALL" -> "DEPARTMENT";
            case "COMPLEX_TASK" -> "FULL";
            default -> "CHAT_ONLY";
        };
    }
    
    public CompletableFuture<Map<String, Object>> processAudioAsync(String sessionId, String audioData, String format) {
        SessionContext context = activeSessions.get(sessionId);
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }
        
        if (context.getAccessLevel() == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Access level not initialized"
            ));
        }
        
        context.incrementMessageCount();
        
        return modelManager.recognizeSpeech(sessionId, audioData, "sherpa")
            .thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("type", "transcription");
                result.put("sessionId", sessionId);
                
                if (response.isSuccess()) {
                    result.put("text", response.getText());
                    result.put("model", response.getModel());
                } else {
                    result.put("error", response.getError());
                }
                
                return result;
            });
    }
    
    public CompletableFuture<Map<String, Object>> processAudioFullChain(String sessionId, String base64OpusData) {
        SessionContext context = activeSessions.get(sessionId);
        AudioNative.Processor audioProcessor = audioProcessors.get(sessionId);
        
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }
        
        if (audioProcessor == null) {
            String message = audioDisabledSessions.contains(sessionId)
                ? "Audio processing unavailable (Native library not loaded)"
                : "Audio processor not initialized";
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", message
            ));
        }
        
        long startTime = System.currentTimeMillis();
        context.incrementMessageCount();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] opusBytes = Base64.getDecoder().decode(base64OpusData);
                
                byte[] pcmBytes = audioProcessor.decodeOpus(opusBytes);
                if (pcmBytes == null || pcmBytes.length == 0) {
                    return Map.of("type", "error", "message", "Opus decode failed");
                }
                
                Path tempAudioFile = savePcmToWav(pcmBytes, 16000, 1);
                
                ModelResponse asrResponse = modelManager.recognizeSpeech(sessionId, tempAudioFile.toString(), "sherpa").join();
                if (!asrResponse.isSuccess()) {
                    return Map.of("type", "error", "message", "ASR failed: " + asrResponse.getError());
                }
                
                String recognizedText = asrResponse.getText();
                log.info("[{}] ASR: {}", sessionId, recognizedText);
                
                List<Map<String, String>> history = context.getHistory();
                
                ModelResponse chatResponse = modelManager.processChatWithIntent(sessionId, recognizedText, history).join();
                if (!chatResponse.isSuccess()) {
                    return Map.of("type", "error", "message", "Chat failed: " + chatResponse.getError());
                }
                
                String responseText = chatResponse.getText();
                String intent = (String) chatResponse.getData().getOrDefault("intent", "unknown");
                String model = chatResponse.getModel();
                
                log.info("[{}] Chat ({}) intent={}: {}", sessionId, model, intent, responseText);
                
                context.addHistory("user", recognizedText);
                context.addHistory("assistant", responseText);
                
                if (responseText == null || responseText.isEmpty()) {
                    return Map.of("type", "error", "message", "Empty response from LLM");
                }
                
                ModelResponse ttsResponse = modelManager.synthesizeSpeechRaw(sessionId, responseText, "zh", 1.0).join();
                if (!ttsResponse.isSuccess()) {
                    return Map.of("type", "error", "message", "TTS failed: " + ttsResponse.getError());
                }
                
                byte[] ttsPcmBytes = extractPcmFromResponse(ttsResponse);
                if (ttsPcmBytes == null || ttsPcmBytes.length == 0) {
                    return Map.of("type", "error", "message", "TTS produced no audio");
                }
                
                List<byte[]> opusPackets = encodePcmToOpus(audioProcessor, ttsPcmBytes);
                
                String responseBase64 = combineOpusPacketsToBase64(opusPackets);
                
                long latency = System.currentTimeMillis() - startTime;
                
                Map<String, Object> result = new HashMap<>();
                result.put("type", "audio_response");
                result.put("sessionId", sessionId);
                result.put("text", recognizedText);
                result.put("response", responseText);
                result.put("audio", responseBase64);
                result.put("model", model);
                result.put("intent", intent);
                result.put("latency_ms", latency);
                
                Files.deleteIfExists(tempAudioFile);
                
                return result;
                
            } catch (Exception e) {
                log.error("[{}] Full chain processing error", sessionId, e);
                return Map.of("type", "error", "message", "Processing error: " + e.getMessage());
            }
        });
    }
    
    private Path savePcmToWav(byte[] pcmData, int sampleRate, int channels) throws Exception {
        Path tempFile = Files.createTempFile("audio_input_", ".wav");
        
        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            int dataSize = pcmData.length;
            int fileSize = 36 + dataSize;
            
            fos.write("RIFF".getBytes());
            fos.write(intToLittleEndian(fileSize));
            fos.write("WAVE".getBytes());
            fos.write("fmt ".getBytes());
            fos.write(intToLittleEndian(16));
            fos.write(shortToLittleEndian((short) 1));
            fos.write(shortToLittleEndian((short) channels));
            fos.write(intToLittleEndian(sampleRate));
            fos.write(intToLittleEndian(sampleRate * channels * 2));
            fos.write(shortToLittleEndian((short) (channels * 2)));
            fos.write(shortToLittleEndian((short) 16));
            fos.write("data".getBytes());
            fos.write(intToLittleEndian(dataSize));
            fos.write(pcmData);
        }
        
        return tempFile;
    }
    
    private byte[] intToLittleEndian(int value) {
        return new byte[] {
            (byte) (value & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 24) & 0xFF)
        };
    }
    
    private byte[] shortToLittleEndian(short value) {
        return new byte[] {
            (byte) (value & 0xFF),
            (byte) ((value >> 8) & 0xFF)
        };
    }
    
    private byte[] extractPcmFromResponse(ModelResponse response) {
        Object audioData = response.getData().get("audio_data");
        if (audioData instanceof List) {
            @SuppressWarnings("unchecked")
            List<Number> samples = (List<Number>) audioData;
            byte[] pcmBytes = new byte[samples.size() * 2];
            for (int i = 0; i < samples.size(); i++) {
                short sample = samples.get(i).shortValue();
                pcmBytes[i * 2] = (byte) (sample & 0xFF);
                pcmBytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            }
            return pcmBytes;
        }
        return null;
    }
    
    private List<byte[]> encodePcmToOpus(AudioNative.Processor processor, byte[] pcmData) {
        List<byte[]> packets = new ArrayList<>();
        int frameSize = 960 * 2;
        
        for (int i = 0; i + frameSize <= pcmData.length; i += frameSize) {
            byte[] frame = new byte[frameSize];
            System.arraycopy(pcmData, i, frame, 0, frameSize);
            
            byte[] opusPacket = processor.encodePcm(frame);
            if (opusPacket != null && opusPacket.length > 0) {
                packets.add(opusPacket);
            }
        }
        
        return packets;
    }
    
    private String combineOpusPacketsToBase64(List<byte[]> packets) {
        int totalLength = 0;
        for (byte[] packet : packets) {
            totalLength += 4 + packet.length;
        }
        
        byte[] combined = new byte[totalLength];
        int offset = 0;
        for (byte[] packet : packets) {
            combined[offset++] = (byte) ((packet.length >> 24) & 0xFF);
            combined[offset++] = (byte) ((packet.length >> 16) & 0xFF);
            combined[offset++] = (byte) ((packet.length >> 8) & 0xFF);
            combined[offset++] = (byte) (packet.length & 0xFF);
            System.arraycopy(packet, 0, combined, offset, packet.length);
            offset += packet.length;
        }
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("activeSessions", activeSessions.size());
        status.put("neurons", neuronRegistry.getAll().size());
        status.put("channels", channelManager.getAll().size());
        
        try {
            ModelStatus modelStatus = modelManager.getStatus().join();
            status.put("modelsLoaded", modelStatus.getLoadedCount());
            status.put("modelsTotal", modelStatus.getTotalModels());
            status.put("asrAvailable", modelStatus.isAsrAvailable());
            status.put("llmAvailable", modelStatus.isLlmAvailable());
            status.put("ttsAvailable", modelStatus.isTtsAvailable());
        } catch (Exception e) {
            status.put("modelStatusError", e.getMessage());
        }
        
        return status;
    }
    
    public boolean isSessionActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
    
    private static class SessionContext {
        private final String sessionId;
        private final long createdAt;
        private final AccessLevel accessLevel;
        private final String departmentId;
        private volatile String userId;
        private volatile Object modelSession;
        private volatile String coordinatorSessionId;
        private volatile int messageCount;
        private volatile CompletableFuture<Void> sessionReadyFuture;
        private final List<Map<String, String>> history;
        
        public SessionContext(String sessionId) {
            this(sessionId, AccessLevel.CHAT_ONLY, null);
        }
        
        public SessionContext(String sessionId, AccessLevel accessLevel, String departmentId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
            this.accessLevel = accessLevel != null ? accessLevel : AccessLevel.CHAT_ONLY;
            this.departmentId = departmentId;
            this.messageCount = 0;
            this.history = new ArrayList<>();
        }
        
        public void setModelSession(Object modelSession) {
            this.modelSession = modelSession;
        }
        
        public Object getModelSession() {
            return modelSession;
        }
        
        public void incrementMessageCount() {
            this.messageCount++;
        }

        public CompletableFuture<Void> getSessionReadyFuture() {
            return sessionReadyFuture;
        }

        public void setSessionReadyFuture(CompletableFuture<Void> sessionReadyFuture) {
            this.sessionReadyFuture = sessionReadyFuture;
        }
        
        public int getMessageCount() {
            return messageCount;
        }
        
        public AccessLevel getAccessLevel() {
            return accessLevel;
        }
        
        public String getDepartmentId() {
            return departmentId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }

        public boolean hasUserIdentity() {
            return userId != null && !userId.isBlank();
        }

        public String getCoordinatorSessionId() {
            return coordinatorSessionId;
        }

        public void setCoordinatorSessionId(String coordinatorSessionId) {
            this.coordinatorSessionId = coordinatorSessionId;
        }
        
        public List<Map<String, String>> getHistory() {
            return new ArrayList<>(history);
        }
        
        public void addHistory(String role, String content) {
            Map<String, String> turn = new HashMap<>();
            turn.put("role", role);
            turn.put("content", content);
            history.add(turn);
            
            if (history.size() > 10) {
                history.remove(0);
            }
        }
    }

    /**
     * 前台闲聊：直接调用 ModelManager.chatAsync() 走 Qwen3-0.6B 模型。
     * 不需要 session 上下文，无需权限验证。
     */
    public CompletableFuture<String> chatPublic(String message, String userId) {
        return modelManager.chatAsync("qwen3-0.6b", message)
            .exceptionally(ex -> {
                log.error("Public chat failed: userId={}, error={}", userId, ex.getMessage());
                return "抱歉，我暂时无法回复，请稍后再试。";
            });
    }

    /**
     * 前台闲聊音频全链路：ASR → LLM → TTS。
     * 接收 Base64 编码的音频数据，走 processAudioFullChain 处理后返回完整响应。
     */
    public CompletableFuture<Map<String, Object>> chatPublicAudio(String base64AudioData, String userId) {
        // 创建临时 session 用于音频处理
        String tempSessionId = "public-audio-" + userId + "-" + System.currentTimeMillis();

        return modelManager.createSession(tempSessionId)
            .thenCompose(ignored -> processAudioFullChain(tempSessionId, base64AudioData))
            .thenApply(response -> {
                // 清理临时 session
                try { modelManager.destroySession(tempSessionId); } catch (Exception e) {
                    log.debug("Failed to destroy temp session {}: {}", tempSessionId, e.getMessage());
                }
                return response;
            })
            .exceptionally(ex -> {
                log.error("Public audio chat failed: userId={}, error={}", userId, ex.getMessage());
                try { modelManager.destroySession(tempSessionId); } catch (Exception e) {
                    log.debug("Failed to destroy temp session {}: {}", tempSessionId, e.getMessage());
                }
                return Map.of("type", "error", "message", "语音处理失败");
            });
    }

    /**
     * 挂起会话记录：保存断线时的会话上下文和音频处理器
     */
    private record SuspendedSession(
        SessionContext context,
        AudioNative.Processor audioProcessor,
        Instant suspendedAt
    ) {}
}
