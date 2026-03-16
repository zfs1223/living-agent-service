package com.livingagent.core.neuron.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelMessageQueue;
import com.livingagent.core.evolution.executor.EvolutionExecutor;
import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.model.selector.ToolNeuronModelSelector;
import com.livingagent.core.model.selector.ToolNeuronModelSelector.ModelType;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.neuron.evolution.EvolutionSignalTrigger;
import com.livingagent.core.tool.Tool;

public class BitNetNeuron implements Neuron {
    
    private static final Logger log = LoggerFactory.getLogger(BitNetNeuron.class);
    
    private static final String DEFAULT_MODEL_ID = "qwen3.5-2b";
    private static final String FALLBACK_MODEL_ID = "bitnet-1.58-3b";
    private static final String DEFAULT_BRAIN_DOMAIN = "tech";
    
    private final String neuronId;
    private String modelId;
    private final ModelManager modelManager;
    private final List<String> availableTools;
    private final List<String> subscribedChannelIds;
    private final List<String> publishChannelIds;
    private final List<Channel> subscribedChannels;
    private final List<Channel> publishingChannels;
    private final AtomicBoolean running;
    private NeuronState state;
    private Thread executionThread;
    private final Map<String, Object> stateData;
    private final Set<String> skills;
    
    private ChannelMessageQueue inputQueue;
    private Channel outputChannel;
    private NeuronContext context;
    private EvolutionSignalTrigger evolutionTrigger;
    private ToolNeuronModelSelector modelSelector;
    
    private final AtomicInteger totalToolCalls = new AtomicInteger(0);
    private final AtomicInteger successfulToolCalls = new AtomicInteger(0);
    private final AtomicInteger failedToolCalls = new AtomicInteger(0);
    
    public BitNetNeuron(String neuronId, ModelManager modelManager) {
        this(neuronId, modelManager, null, DEFAULT_BRAIN_DOMAIN);
    }
    
    public BitNetNeuron(String neuronId, ModelManager modelManager, String modelId) {
        this(neuronId, modelManager, modelId, DEFAULT_BRAIN_DOMAIN);
    }
    
    public BitNetNeuron(String neuronId, ModelManager modelManager, String modelId, String brainDomain) {
        this.neuronId = neuronId;
        this.modelManager = modelManager;
        this.availableTools = new ArrayList<>();
        this.subscribedChannelIds = new ArrayList<>();
        this.publishChannelIds = new ArrayList<>();
        this.subscribedChannels = new ArrayList<>();
        this.publishingChannels = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.state = NeuronState.IDLE;
        this.stateData = new HashMap<>();
        this.skills = new HashSet<>();
    }
    
    public void setEvolutionExecutor(EvolutionExecutor evolutionExecutor) {
        this.evolutionTrigger = new EvolutionSignalTrigger(evolutionExecutor, neuronId, getBrainDomain());
    }
    
    public void setEvolutionTrigger(EvolutionSignalTrigger evolutionTrigger) {
        this.evolutionTrigger = evolutionTrigger;
    }
    
    public void setModelSelector(ToolNeuronModelSelector modelSelector) {
        this.modelSelector = modelSelector;
        updateModelFromSelector();
    }
    
    private void updateModelFromSelector() {
        if (modelSelector != null) {
            ModelType selectedModel = modelSelector.selectModel();
            this.modelId = selectedModel.getId();
            log.info("BitNetNeuron {} model auto-selected: {} (memory: {}MB, context: {}K)", 
                neuronId, 
                selectedModel.getDisplayName(),
                selectedModel.getMemoryMB(),
                selectedModel.getContextLength() / 1024);
        }
    }
    
    private String getCurrentModelId() {
        if (modelSelector != null) {
            return modelSelector.getCurrentModel().getId();
        }
        return modelId != null ? modelId : DEFAULT_MODEL_ID;
    }
    
    private String getBrainDomain() {
        if (context != null && context.getBrainDomain() != null) {
            return context.getBrainDomain();
        }
        return DEFAULT_BRAIN_DOMAIN;
    }
    
    public void registerTool(String toolName) {
        if (!availableTools.contains(toolName)) {
            availableTools.add(toolName);
            log.info("BitNetNeuron {} registered tool: {}", neuronId, toolName);
        }
    }
    
    public void registerTools(List<String> toolNames) {
        for (String toolName : toolNames) {
            registerTool(toolName);
        }
    }
    
    @Override
    public String getId() {
        return neuronId;
    }
    
    @Override
    public String getName() {
        return "BitNetNeuron-" + neuronId;
    }
    
    @Override
    public String getDescription() {
        return "BitNet LLM Neuron for tool execution with evolution capability";
    }
    
    @Override
    public String getType() {
        return "llm-tool";
    }
    
    @Override
    public NeuronState getState() {
        return state;
    }
    
    @Override
    public void setState(NeuronState state) {
        this.state = state;
    }
    
    @Override
    public void subscribe(Channel channel) {
        subscribedChannels.add(channel);
        subscribedChannelIds.add(channel.getId());
        log.info("BitNetNeuron {} subscribed to channel: {}", neuronId, channel.getId());
    }
    
    @Override
    public void publishTo(Channel channel) {
        publishingChannels.add(channel);
        publishChannelIds.add(channel.getId());
        this.outputChannel = channel;
        log.info("BitNetNeuron {} will publish to channel: {}", neuronId, channel.getId());
    }
    
    @Override
    public void initialize(NeuronContext context) {
        this.context = context;
        this.inputQueue = context.getQueue();
        log.info("BitNetNeuron {} initialized with {} tools", neuronId, availableTools.size());
    }
    
    @Override
    public void start(NeuronContext context) {
        initialize(context);
        start();
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            state = NeuronState.ACTIVE;
            executionThread = new Thread(this::executionLoop, "bitnet-neuron-" + neuronId);
            executionThread.start();
            log.info("BitNetNeuron {} started", neuronId);
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            state = NeuronState.STOPPED;
            if (executionThread != null) {
                executionThread.interrupt();
            }
            log.info("BitNetNeuron {} stopped", neuronId);
        }
    }
    
    @Override
    public void onMessage(ChannelMessage message) {
        if (inputQueue != null) {
            inputQueue.offer(message);
        }
    }
    
    @Override
    public void publish(String channelId, ChannelMessage message) {
        if (context != null) {
            context.publish(channelId, message);
        }
    }
    
    private void executionLoop() {
        log.info("BitNetNeuron {} execution loop started", neuronId);
        
        while (running.get()) {
            try {
                ChannelMessage message = inputQueue != null ? 
                    inputQueue.poll(java.time.Duration.ofSeconds(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS) : null;

                if (message != null) {
                    processMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in BitNetNeuron execution loop", e);
                state = NeuronState.ERROR;
                recordEvolutionError("execution_loop", e.getMessage());
            }
        }
        
        log.info("BitNetNeuron {} execution loop ended", neuronId);
    }
    
    private void processMessage(ChannelMessage message) {
        state = NeuronState.ACTIVE;

        try {
            Object payloadObj = message.getPayload();
            if (!(payloadObj instanceof Map)) {
                log.warn("Unexpected payload type: {}", payloadObj);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) payloadObj;
            String sessionId = message.getSessionId();

            log.debug("BitNetNeuron processing message for session={}", sessionId);

            if (payload.containsKey("toolName")) {
                handleToolExecution(sessionId, payload);
            } else {
                handleIntentDetection(sessionId, payload);
            }
        } finally {
            state = NeuronState.ACTIVE;
        }
    }
    
    private void handleIntentDetection(String sessionId, Map<String, Object> payload) {
        String userInput = (String) payload.get("userInput");
        
        modelManager.processToolIntent(sessionId, userInput, availableTools)
            .thenAccept(response -> {
                if (response.isSuccess()) {
                    Boolean isToolCall = (Boolean) response.getData().get("tool_call");
                    
                    if (isToolCall != null && isToolCall) {
                        String toolName = (String) response.getData().get("tool");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) response.getData().get("parameters");
                        
                        if (!availableTools.contains(toolName)) {
                            recordCapabilityGap(toolName, "Tool not registered: " + toolName);
                        }
                        
                        publishToolIntent(sessionId, toolName, params, userInput);
                    } else {
                        String textResponse = (String) response.getData().get("response");
                        publishNoToolNeeded(sessionId, textResponse);
                    }
                } else {
                    recordEvolutionError("intent_detection", response.getError());
                    publishError(sessionId, response.getError());
                }
            })
            .exceptionally(e -> {
                log.error("Error detecting intent", e);
                recordEvolutionError("intent_detection_exception", e.getMessage());
                publishError(sessionId, e.getMessage());
                return null;
            });
    }
    
    private void handleToolExecution(String sessionId, Map<String, Object> payload) {
        String toolName = (String) payload.get("toolName");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) payload.get("parameters");
        
        log.info("BitNetNeuron executing tool: {} for session: {}", toolName, sessionId);
        totalToolCalls.incrementAndGet();
        
        modelManager.executeToolCall(sessionId, toolName, params)
        .thenAccept(response -> {
            if (response.isSuccess()) {
                successfulToolCalls.incrementAndGet();
                recordToolSuccess(toolName);
                publishToolResult(sessionId, toolName, response);
            } else {
                failedToolCalls.incrementAndGet();
                recordToolFailure(toolName, response.getError());
                publishToolResult(sessionId, toolName, response);
            }
        })
        .exceptionally(e -> {
            failedToolCalls.incrementAndGet();
            log.error("Error executing tool", e);
            recordToolFailure(toolName, e.getMessage());
            publishError(sessionId, e.getMessage());
            return null;
        });
    }
    
    private void publishToolIntent(String sessionId, String toolName, Map<String, Object> params, String originalInput) {
        if (outputChannel == null && !publishingChannels.isEmpty()) {
            outputChannel = publishingChannels.get(0);
        }
        
        if (outputChannel != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("toolCall", true);
            payload.put("toolName", toolName);
            payload.put("parameters", params);
            payload.put("originalInput", originalInput);

            ChannelMessage message = ChannelMessage.toolCall(
                outputChannel.getId(),
                neuronId,
                outputChannel.getId(),
                sessionId,
                payload
            );
            outputChannel.publish(message);
            
            log.info("BitNetNeuron published tool intent: tool={}, session={}", toolName, sessionId);
        }
    }
    
    private void publishNoToolNeeded(String sessionId, String response) {
        if (outputChannel != null) {
            ChannelMessage message = ChannelMessage.text(
                outputChannel.getId(),
                neuronId,
                outputChannel.getId(),
                sessionId,
                response
            );
            outputChannel.publish(message);
        }
    }
    
    private void publishToolResult(String sessionId, String toolName, ModelResponse result) {
        if (outputChannel != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("toolName", toolName);
            payload.put("result", result.getText());
            payload.put("success", result.isSuccess());

            ChannelMessage message = ChannelMessage.toolCall(
                outputChannel.getId(),
                neuronId,
                outputChannel.getId(),
                sessionId,
                payload
            );
            outputChannel.publish(message);
        }
    }
    
    private void publishError(String sessionId, String error) {
        if (outputChannel != null) {
            ChannelMessage message = ChannelMessage.error(
                outputChannel.getId(),
                neuronId,
                outputChannel.getId(),
                sessionId,
                error
            );
            outputChannel.publish(message);
        }
    }
    
    private void recordEvolutionError(String errorType, String errorMessage) {
        if (evolutionTrigger != null) {
            evolutionTrigger.recordError(errorType, errorMessage);
        }
    }
    
    private void recordToolFailure(String toolName, String errorMessage) {
        if (evolutionTrigger != null) {
            evolutionTrigger.recordToolFailure(toolName, errorMessage);
        }
    }
    
    private void recordToolSuccess(String toolName) {
        if (evolutionTrigger != null) {
            evolutionTrigger.recordToolSuccess(toolName);
        }
    }
    
    private void recordCapabilityGap(String missingCapability, String context) {
        if (evolutionTrigger != null) {
            evolutionTrigger.recordCapabilityGap(missingCapability, context);
        }
    }
    
    public Map<String, Object> getEvolutionStatistics() {
        if (evolutionTrigger != null) {
            return evolutionTrigger.getStatistics();
        }
        return Map.of(
            "neuronId", neuronId,
            "totalToolCalls", totalToolCalls.get(),
            "successfulToolCalls", successfulToolCalls.get(),
            "failedToolCalls", failedToolCalls.get(),
            "evolutionEnabled", false
        );
    }
    
    @Override
    public List<String> getSubscribedChannels() {
        return List.copyOf(subscribedChannelIds);
    }
    
    @Override
    public List<String> getPublishChannels() {
        return List.copyOf(publishChannelIds);
    }
    
    @Override
    public List<Channel> getSubscribedChannelObjects() {
        return List.copyOf(subscribedChannels);
    }
    
    @Override
    public List<Channel> getPublishingChannels() {
        return List.copyOf(publishingChannels);
    }
    
    public List<String> getAvailableTools() {
        return List.copyOf(availableTools);
    }
    
    @Override
    public List<Tool> getTools() {
        return new ArrayList<>();
    }
    
    @Override
    public Set<String> getSkills() {
        return skills;
    }
    
    @Override
    public void addSkill(String skillName) {
        skills.add(skillName);
        log.info("BitNetNeuron {} added skill: {}", neuronId, skillName);
    }
    
    @Override
    public void removeSkill(String skillName) {
        skills.remove(skillName);
        log.info("BitNetNeuron {} removed skill: {}", neuronId, skillName);
    }
    
    @Override
    public boolean hasSkill(String skillName) {
        return skills.contains(skillName);
    }
    
    @Override
    public void autoDiscoverSkills() {
        log.debug("Auto-discovering skills for BitNetNeuron {}", neuronId);
    }
    
    @Override
    public Map<String, Object> getStateData() {
        return stateData;
    }
    
    @Override
    public void setStateData(String key, Object value) {
        stateData.put(key, value);
    }
    
    @Override
    public Object getStateData(String key) {
        return stateData.get(key);
    }
    
    public String getModelId() {
        return modelId;
    }
    
    public void setModelId(String modelId) {
        this.modelId = modelId;
        log.info("BitNetNeuron {} model changed to: {}", neuronId, modelId);
    }
}
