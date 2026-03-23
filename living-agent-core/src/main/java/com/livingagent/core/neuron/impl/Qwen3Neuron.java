package com.livingagent.core.neuron.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelMessageQueue;
import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.neuron.chat.ChatIntentClassifier;
import com.livingagent.core.neuron.chat.ChatNeuronConfig;
import com.livingagent.core.tool.Tool;

public class Qwen3Neuron implements Neuron {
    
    private static final Logger log = LoggerFactory.getLogger(Qwen3Neuron.class);
    
    private static final String NEURON_TYPE = "chat";
    private static final String MODEL_ID = "qwen3-0.6b";
    
    private final String neuronId;
    private final ModelManager modelManager;
    private final ChatNeuronConfig config;
    private final ChatIntentClassifier intentClassifier;
    private final List<String> subscribedChannelIds;
    private final List<String> publishChannelIds;
    private final AtomicBoolean running;
    private volatile NeuronState state;
    private Thread executionThread;
    private final Map<String, Object> stateData;
    private final Set<String> skills;
    private final Map<String, List<Map<String, String>>> sessionHistories;
    
    private ChannelMessageQueue inputQueue;
    private Channel outputChannel;
    private NeuronContext context;
    
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong quickResponses = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    
    public Qwen3Neuron(String neuronId, ModelManager modelManager) {
        this(neuronId, modelManager, ChatNeuronConfig.defaultConfig());
    }
    
    public Qwen3Neuron(String neuronId, ModelManager modelManager, ChatNeuronConfig config) {
        this.neuronId = neuronId;
        this.modelManager = modelManager;
        this.config = config;
        this.intentClassifier = new ChatIntentClassifier();
        this.subscribedChannelIds = new ArrayList<>();
        this.publishChannelIds = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.state = NeuronState.CREATED;
        this.stateData = new ConcurrentHashMap<>();
        this.skills = ConcurrentHashMap.newKeySet();
        this.sessionHistories = new ConcurrentHashMap<>();
        
        initDefaultSkills();
    }
    
    private void initDefaultSkills() {
        skills.add("casual-chat");
        skills.add("greeting");
        skills.add("simple-qa");
        skills.add("quick-response");
    }
    
    @Override
    public String getId() {
        return neuronId;
    }
    
    @Override
    public String getName() {
        return "Qwen3Neuron-" + neuronId;
    }
    
    @Override
    public String getDescription() {
        return "Qwen3-0.6B 闲聊神经元 - 快速响应日常对话和简单问题";
    }
    
    @Override
    public String getType() {
        return NEURON_TYPE;
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
        subscribedChannelIds.add(channel.getId());
        log.info("Qwen3Neuron {} subscribed to channel: {}", neuronId, channel.getId());
    }
    
    @Override
    public void publishTo(Channel channel) {
        publishChannelIds.add(channel.getId());
        this.outputChannel = channel;
        log.info("Qwen3Neuron {} will publish to channel: {}", neuronId, channel.getId());
    }
    
    @Override
    public void initialize(NeuronContext context) {
        this.context = context;
        this.inputQueue = context.getQueue();
        this.state = NeuronState.INITIALIZING;
        log.info("Qwen3Neuron {} initialized with config: maxTokens={}, temperature={}", 
            neuronId, config.getMaxTokens(), config.getTemperature());
    }
    
    @Override
    public void start(NeuronContext context) {
        this.context = context;
        this.inputQueue = context.getQueue();
        start();
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            state = NeuronState.ACTIVE;
            executionThread = new Thread(this::executionLoop, "qwen3-neuron-" + neuronId);
            executionThread.setDaemon(true);
            executionThread.start();
            log.info("Qwen3Neuron {} started, ready for quick responses", neuronId);
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            state = NeuronState.STOPPED;
            if (executionThread != null) {
                executionThread.interrupt();
            }
            sessionHistories.clear();
            log.info("Qwen3Neuron {} stopped, stats: total={}, quick={}, avgLatency={}ms", 
                neuronId, totalRequests.get(), quickResponses.get(), 
                totalRequests.get() > 0 ? totalLatencyMs.get() / totalRequests.get() : 0);
        }
    }
    
    @Override
    public void onMessage(ChannelMessage message) {
        if (inputQueue != null) {
            inputQueue.offer(message);
        }
    }
    
    private void executionLoop() {
        log.info("Qwen3Neuron {} execution loop started", neuronId);
        
        while (running.get()) {
            try {
                ChannelMessage message = inputQueue != null ? 
                    inputQueue.poll(1000, java.util.concurrent.TimeUnit.MILLISECONDS) : null;
                
                if (message != null) {
                    processMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in Qwen3Neuron execution loop", e);
                state = NeuronState.ERROR;
            }
        }
        
        log.info("Qwen3Neuron {} execution loop ended", neuronId);
    }
    
    private void processMessage(ChannelMessage message) {
        state = NeuronState.PROCESSING;
        long startTime = System.currentTimeMillis();
        
        try {
            String type = message.getType().name();
            Object payloadObj = message.getPayload();
            String sessionId = message.getSessionId();
            
            log.debug("Qwen3Neuron processing message: type={}, sessionId={}", type, sessionId);
            
            if ("TEXT".equals(type) && payloadObj instanceof String text) {
                handleChatRequest(sessionId, text);
            } else if ("TEXT".equals(type) && payloadObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                String userInput = (String) payload.get("userInput");
                if (userInput != null) {
                    handleChatRequest(sessionId, userInput);
                }
            } else {
                log.warn("Unknown message type: {}", type);
            }
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            totalLatencyMs.addAndGet(latency);
            totalRequests.incrementAndGet();
            state = NeuronState.ACTIVE;
        }
    }
    
    private void handleChatRequest(String sessionId, String userInput) {
        if (config.isEnableIntentClassification()) {
            ChatIntentClassifier.ClassificationResult classification = intentClassifier.classify(userInput);
            
            log.debug("Intent classification: intent={}, confidence={}, reason={}", 
                classification.getIntent(), classification.getConfidence(), classification.getReason());
            
            if (!classification.shouldUseChatNeuron()) {
                log.debug("Input not suitable for chat neuron, intent={}", classification.getIntent());
                publishRoutingSuggestion(sessionId, userInput, classification);
                return;
            }
            
            if (classification.getIntent() == ChatIntentClassifier.ChatIntent.GREETING) {
                handleGreeting(sessionId, userInput);
                return;
            }
        }
        
        handleGenerate(sessionId, userInput);
    }
    
    private void handleGreeting(String sessionId, String userInput) {
        String greeting = generateQuickGreeting(userInput);
        ModelResponse response = ModelResponse.success();
        response.getData().put("text", greeting);
        response.setModel(MODEL_ID);
        publishResponse(sessionId, response);
        quickResponses.incrementAndGet();
    }
    
    private String generateQuickGreeting(String input) {
        String lower = input.toLowerCase().trim();
        
        if (lower.contains("早上好") || lower.contains("早安")) {
            return "早上好！今天有什么我可以帮助您的吗？";
        }
        if (lower.contains("下午好")) {
            return "下午好！有什么我可以为您做的吗？";
        }
        if (lower.contains("晚上好") || lower.contains("晚安")) {
            return "晚上好！祝您有个愉快的夜晚。";
        }
        if (lower.matches(".*在[吗呢].*") || lower.contains("在不在")) {
            return "我在的，有什么可以帮您？";
        }
        
        return "您好！我是您的AI助手，有什么可以帮您的吗？";
    }
    
    private void handleGenerate(String sessionId, String prompt) {
        List<Map<String, String>> history = sessionHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
        
        String fullPrompt = config.buildPromptWithContext(prompt, history);
        
        modelManager.generateText(sessionId, fullPrompt, history)
            .thenAccept(response -> {
                updateHistory(sessionId, prompt, response.getText());
                publishResponse(sessionId, response);
            })
            .exceptionally(e -> {
                log.error("Error generating text for session {}", sessionId, e);
                publishError(sessionId, e.getMessage());
                return null;
            });
    }
    
    private void updateHistory(String sessionId, String userMessage, String assistantResponse) {
        List<Map<String, String>> history = sessionHistories.get(sessionId);
        if (history != null) {
            Map<String, String> userTurn = new HashMap<>();
            userTurn.put("role", "user");
            userTurn.put("content", userMessage);
            history.add(userTurn);
            
            Map<String, String> assistantTurn = new HashMap<>();
            assistantTurn.put("role", "assistant");
            assistantTurn.put("content", assistantResponse);
            history.add(assistantTurn);
            
            while (history.size() > config.getMaxHistoryTurns() * 2) {
                history.remove(0);
            }
        }
    }
    
    private void publishRoutingSuggestion(String sessionId, String userInput, 
            ChatIntentClassifier.ClassificationResult classification) {
        if (outputChannel != null) {
            Map<String, Object> routingInfo = new HashMap<>();
            routingInfo.put("originalInput", userInput);
            routingInfo.put("suggestedNeuron", getSuggestedNeuron(classification.getIntent()));
            routingInfo.put("intent", classification.getIntent().name());
            routingInfo.put("confidence", classification.getConfidence());
            
            ChannelMessage message = new ChannelMessage(
                neuronId, neuronId, outputChannel.getId(), sessionId,
                ChannelMessage.MessageType.CONTROL, routingInfo
            );
            
            outputChannel.publish(message);
            log.debug("Published routing suggestion for session {}", sessionId);
        }
    }
    
    private String getSuggestedNeuron(ChatIntentClassifier.ChatIntent intent) {
        return switch (intent) {
            case TOOL_CALL -> "tool-neuron";
            case COMPLEX_TASK -> "main-brain";
            default -> "chat-neuron";
        };
    }
    
    private void publishResponse(String sessionId, ModelResponse response) {
        if (outputChannel != null) {
            ChannelMessage message = ChannelMessage.text(
                neuronId,
                neuronId,
                outputChannel.getId(),
                sessionId,
                response.getText()
            );
            outputChannel.publish(message);
            log.debug("Qwen3Neuron published response to channel: {}", outputChannel.getId());
        }
    }
    
    private void publishError(String sessionId, String error) {
        if (outputChannel != null) {
            ChannelMessage message = ChannelMessage.error(
                neuronId,
                neuronId,
                outputChannel.getId(),
                sessionId,
                error
            );
            outputChannel.publish(message);
        }
    }
    
    @Override
    public void publish(String channelId, ChannelMessage message) {
        if (context != null) {
            context.publish(channelId, message);
        }
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
        return new ArrayList<>();
    }
    
    @Override
    public List<Channel> getPublishingChannels() {
        return new ArrayList<>();
    }
    
    @Override
    public List<Tool> getTools() {
        return new ArrayList<>();
    }
    
    @Override
    public Set<String> getSkills() {
        return Set.copyOf(skills);
    }
    
    @Override
    public void addSkill(String skillName) {
        skills.add(skillName);
    }
    
    @Override
    public void removeSkill(String skillName) {
        skills.remove(skillName);
    }
    
    @Override
    public boolean hasSkill(String skillName) {
        return skills.contains(skillName);
    }
    
    @Override
    public void autoDiscoverSkills() {
        log.debug("Auto-discovering skills for Qwen3Neuron {}", neuronId);
    }
    
    @Override
    public Map<String, Object> getStateData() {
        Map<String, Object> data = new HashMap<>(stateData);
        data.put("totalRequests", totalRequests.get());
        data.put("quickResponses", quickResponses.get());
        data.put("avgLatencyMs", totalRequests.get() > 0 ? totalLatencyMs.get() / totalRequests.get() : 0);
        data.put("activeSessions", sessionHistories.size());
        return data;
    }
    
    @Override
    public void setStateData(String key, Object value) {
        stateData.put(key, value);
    }
    
    @Override
    public Object getStateData(String key) {
        return stateData.get(key);
    }
    
    public void clearSessionHistory(String sessionId) {
        sessionHistories.remove(sessionId);
        log.debug("Cleared history for session {}", sessionId);
    }
    
    public ChatNeuronConfig getConfig() {
        return config;
    }
    
    public boolean canHandle(String userInput) {
        if (!config.isEnableIntentClassification()) {
            return true;
        }
        return intentClassifier.isQuickResponseCandidate(userInput);
    }
    
    public ChatIntentClassifier.ClassificationResult classifyIntent(String userInput) {
        return intentClassifier.classify(userInput);
    }
}
