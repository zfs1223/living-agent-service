package com.livingagent.core.neuron.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.livingagent.core.tool.Tool;

public class Qwen3Neuron implements Neuron {
    
    private static final Logger log = LoggerFactory.getLogger(Qwen3Neuron.class);
    
    private final String neuronId;
    private final String modelId;
    private final ModelManager modelManager;
    private final List<String> subscribedChannelIds;
    private final List<String> publishChannelIds;
    private final AtomicBoolean running;
    private NeuronState state;
    private Thread executionThread;
    private final Map<String, Object> stateData;
    private final Set<String> skills;
    
    private ChannelMessageQueue inputQueue;
    private Channel outputChannel;
    private NeuronContext context;
    
    public Qwen3Neuron(String neuronId, ModelManager modelManager) {
        this.neuronId = neuronId;
        this.modelId = "qwen3-0.6b";
        this.modelManager = modelManager;
        this.subscribedChannelIds = new ArrayList<>();
        this.publishChannelIds = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.state = NeuronState.CREATED;
        this.stateData = new HashMap<>();
        this.skills = new HashSet<>();
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
        return "Qwen3 LLM Neuron for text generation";
    }
    
    @Override
    public String getType() {
        return "llm-chat";
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
        log.info("Qwen3Neuron {} initialized", neuronId);
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
            executionThread.start();
            log.info("Qwen3Neuron {} started", neuronId);
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            state = NeuronState.STOPPED;
            if (executionThread != null) {
                executionThread.interrupt();
            }
            log.info("Qwen3Neuron {} stopped", neuronId);
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
                ChannelMessage message = inputQueue != null ? inputQueue.poll(1000, java.util.concurrent.TimeUnit.MILLISECONDS) : null;
                
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
        state = NeuronState.ACTIVE;
        
        try {
            String type = message.getType().name();
            Object payloadObj = message.getPayload();
            String sessionId = message.getSessionId();
            
            log.debug("Qwen3Neuron processing message: type={}, sessionId={}", type, sessionId);
            
            if ("TEXT".equals(type) && payloadObj instanceof String text) {
                handleGenerate(sessionId, text);
            } else {
                log.warn("Unknown message type: {}", type);
            }
        } finally {
            state = NeuronState.ACTIVE;
        }
    }
    
    private void handleGenerate(String sessionId, String prompt) {
        modelManager.generateText(sessionId, prompt, null)
            .thenAccept(response -> {
                publishResponse(sessionId, response);
            })
            .exceptionally(e -> {
                log.error("Error generating text", e);
                publishError(sessionId, e.getMessage());
                return null;
            });
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
        return skills;
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
}
