package com.livingagent.core.neuron.impl;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractNeuron implements Neuron {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String id;
    protected final String name;
    protected final String description;
    protected final List<String> subscribedChannels;
    protected final List<String> publishChannels;
    protected final List<Tool> tools;
    protected final Set<String> skills;
    protected final Map<String, Object> stateData;
    
    protected final String department;
    protected final String role;
    protected final String instance;

    protected final AtomicReference<NeuronState> state = new AtomicReference<>(NeuronState.INITIALIZING);
    protected volatile NeuronContext context;
    protected volatile boolean running = false;
    protected final List<Channel> subscribedChannelObjects = new ArrayList<>();
    protected final List<Channel> publishingChannelObjects = new ArrayList<>();

    private static final Set<String> CORE_SKILLS = Set.of(
            "tavily-search",
            "find-skills",
            "proactive-agent",
            "weather"
    );

    protected AbstractNeuron(String id, String name, String description,
                             List<String> subscribedChannels, List<String> publishChannels,
                             List<Tool> tools) {
        this.id = normalizeId(id);
        this.name = name;
        this.description = description;
        this.subscribedChannels = Collections.unmodifiableList(new ArrayList<>(subscribedChannels));
        this.publishChannels = Collections.unmodifiableList(new ArrayList<>(publishChannels));
        this.tools = Collections.unmodifiableList(new ArrayList<>(tools));
        this.skills = ConcurrentHashMap.newKeySet();
        this.skills.addAll(CORE_SKILLS);
        this.stateData = new ConcurrentHashMap<>();
        
        IdUtils.ParsedNeuronId parsed = IdUtils.parseNeuronId(this.id);
        this.department = parsed.getDepartment();
        this.role = parsed.getRole();
        this.instance = parsed.getInstance();
    }
    
    private String normalizeId(String id) {
        if (IdUtils.isNeuronId(id)) {
            return id;
        }
        if (IdUtils.isDigitalEmployeeId(id)) {
            return IdUtils.employeeToNeuronId(id);
        }
        if (id.contains("://")) {
            throw new IllegalArgumentException("Invalid ID format: " + id);
        }
        String[] parts = id.split("/");
        if (parts.length == 3) {
            return IdUtils.generateNeuronId(parts[0], parts[1], parts[2]);
        }
        throw new IllegalArgumentException("Cannot normalize ID: " + id + 
            ". Expected format: department/role/instance or neuron://department/role/instance");
    }

    @Override
    public String getId() { return id; }
    
    public String getDepartment() { return department; }
    
    public String getRole() { return role; }
    
    public String getInstance() { return instance; }
    
    public String getEmployeeId() { return IdUtils.neuronToEmployeeId(id); }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public NeuronState getState() { return state.get(); }

    @Override
    public void setState(NeuronState newState) {
        NeuronState oldState = state.get();
        if (oldState != newState) {
            state.set(newState);
            log.info("Neuron {} state changed: {} -> {}", id, oldState, newState);
        }
    }

    @Override
    public List<String> getSubscribedChannels() { return subscribedChannels; }

    @Override
    public List<String> getPublishChannels() { return publishChannels; }

    @Override
    public List<Channel> getSubscribedChannelObjects() { return Collections.unmodifiableList(subscribedChannelObjects); }
    
    @Override
    public List<Channel> getPublishingChannels() { return Collections.unmodifiableList(publishingChannelObjects); }

    @Override
    public void subscribe(Channel channel) {
        if (channel != null && !subscribedChannelObjects.contains(channel)) {
            subscribedChannelObjects.add(channel);
            channel.subscribe(this.id);
            log.debug("Neuron {} subscribed to channel {}", id, channel.getId());
        }
    }

    @Override
    public void publishTo(Channel channel) {
        if (channel != null && !publishingChannelObjects.contains(channel)) {
            publishingChannelObjects.add(channel);
            log.debug("Neuron {} added publishing channel {}", id, channel.getId());
        }
    }

    @Override
    public String getType() {
        return role != null ? role : "unknown";
    }

    @Override
    public List<Tool> getTools() { return tools; }

    @Override
    public Map<String, Object> getStateData() { return new HashMap<>(stateData); }

    @Override
    public void setStateData(String key, Object value) { stateData.put(key, value); }

    @Override
    public Object getStateData(String key) { return stateData.get(key); }

    @Override
    public void initialize(NeuronContext context) {
        this.context = context;
        setState(NeuronState.ACTIVE);
        log.info("Neuron {} initialized", id);
    }

    @Override
    public Set<String> getSkills() { return Collections.unmodifiableSet(skills); }

    @Override
    public void addSkill(String skillName) {
        if (skillName != null && !skillName.isBlank()) {
            skills.add(skillName);
            log.debug("Neuron {} added skill: {}", id, skillName);
        }
    }

    @Override
    public void removeSkill(String skillName) {
        if (skillName != null && CORE_SKILLS.contains(skillName)) {
            log.warn("Cannot remove core skill: {} from neuron {}", skillName, id);
            return;
        }
        skills.remove(skillName);
        log.debug("Neuron {} removed skill: {}", id, skillName);
    }

    @Override
    public boolean hasSkill(String skillName) {
        return skills.contains(skillName);
    }

    @Override
    public void autoDiscoverSkills() {
        if (context == null || context.getSkillRegistry() == null) {
            log.warn("Neuron {} cannot auto-discover skills: no skill registry available", id);
            return;
        }

        var skillRegistry = context.getSkillRegistry();
        String neuronType = getType();
        
        List<Skill> availableSkills = skillRegistry.getAllSkills();
        for (var skill : availableSkills) {
            String skillCategory = skill.getCategory();
            String targetBrain = skill.getTargetBrain();
            
            boolean shouldAdd = false;
            
            if ("core".equals(skillCategory)) {
                shouldAdd = true;
            } else if (targetBrain != null && targetBrain.equalsIgnoreCase(neuronType)) {
                shouldAdd = true;
            } else if (targetBrain == null || "*".equals(targetBrain)) {
                shouldAdd = true;
            }
            
            if (shouldAdd && !skills.contains(skill.getName())) {
                skills.add(skill.getName());
                log.info("Neuron {} auto-discovered skill: {}", id, skill.getName());
            }
        }
        
        log.info("Neuron {} now has {} skills", id, skills.size());
    }

    @Override
    public void start(NeuronContext context) {
        if (running) {
            log.warn("Neuron {} already running", id);
            return;
        }

        this.context = context;
        state.set(NeuronState.INITIALIZING);
        
        try {
            doStart(context);
            running = true;
            state.set(NeuronState.RUNNING);
            log.info("Neuron {} started", id);
        } catch (Exception e) {
            state.set(NeuronState.ERROR);
            log.error("Failed to start neuron: {}", id, e);
            throw new RuntimeException("Failed to start neuron: " + id, e);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        state.set(NeuronState.STOPPED);
        
        try {
            doStop();
            log.info("Neuron {} stopped", id);
        } catch (Exception e) {
            log.error("Failed to stop neuron: {}", id, e);
        }
    }

    @Override
    public void onMessage(ChannelMessage message) {
        if (!running) {
            log.warn("Neuron {} received message but not running", id);
            return;
        }

        try {
            doProcessMessage(message);
        } catch (Exception e) {
            log.error("Error processing message in neuron: {}", id, e);
            state.set(NeuronState.ERROR);
        }
    }

    @Override
    public void publish(String channelId, ChannelMessage message) {
        if (context != null) {
            if (context.getChannelManager() != null) {
                context.getChannelManager().publish(channelId, message);
            } else if (context.getQueue() != null) {
                context.getQueue().enqueue(message);
            }
            context.publish(channelId, message);
        }
        log.debug("Neuron {} published message to channel {}", id, channelId);
    }

    protected abstract void doStart(NeuronContext context);

    protected abstract void doStop();

    protected abstract void doProcessMessage(ChannelMessage message);
}
