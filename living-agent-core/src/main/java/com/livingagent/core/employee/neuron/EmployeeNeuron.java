package com.livingagent.core.employee.neuron;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.neuron.impl.AbstractNeuron;
import com.livingagent.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EmployeeNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(EmployeeNeuron.class);

    private final DigitalEmployee employee;
    private final Brain delegateBrain;
    private BrainContext brainContext;
    private volatile Instant lastActiveAt;

    public EmployeeNeuron(DigitalEmployee employee, Brain delegateBrain, List<Tool> tools) {
        super(
            employee.getDigitalConfig().getNeuronId(),
            employee.getName(),
            employee.getTitle(),
            employee.getDigitalConfig().getSubscribeChannels(),
            employee.getDigitalConfig().getPublishChannels(),
            tools != null ? tools : (delegateBrain != null ? delegateBrain.getTools() : List.of())
        );
        this.employee = employee;
        this.delegateBrain = delegateBrain;
        this.lastActiveAt = Instant.now();
        
        log.info("Created EmployeeNeuron: {} for employee {}", id, employee.getEmployeeId());
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("Starting EmployeeNeuron: {} ({})", id, employee.getTitle());
        
        employee.setStatus(EmployeeStatus.ACTIVE);
        lastActiveAt = Instant.now();
        
        if (delegateBrain != null && context != null) {
            try {
                brainContext = createBrainContext(context);
                delegateBrain.start(brainContext);
                log.info("Delegate brain started for neuron: {}", id);
            } catch (Exception e) {
                log.error("Failed to start delegate brain for neuron: {}", id, e);
            }
        }
        
        for (String skill : employee.getSkills()) {
            addSkill(skill);
        }
    }

    @Override
    protected void doStop() {
        log.info("Stopping EmployeeNeuron: {}", id);
        
        employee.setStatus(EmployeeStatus.OFFLINE);
        
        if (delegateBrain != null) {
            try {
                delegateBrain.stop();
            } catch (Exception e) {
                log.warn("Error stopping delegate brain for neuron: {}", id, e);
            }
        }
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("EmployeeNeuron {} processing message from {}", id, message.getSourceChannelId());
        
        if (employee.getStatus() == EmployeeStatus.OFFLINE) {
            employee.setStatus(EmployeeStatus.ACTIVE);
            setState(NeuronState.RUNNING);
        }
        
        lastActiveAt = Instant.now();
        
        if (delegateBrain != null) {
            try {
                processWithBrain(message);
                employee.recordTask(true);
            } catch (Exception e) {
                log.error("Error processing message with brain for neuron: {}", id, e);
                employee.recordTask(false);
                setState(NeuronState.ERROR);
            }
        } else {
            log.warn("No delegate brain for neuron: {}, message will be ignored", id);
        }
    }

    private void processWithBrain(ChannelMessage message) {
        if (brainContext == null) {
            log.warn("BrainContext not initialized for neuron: {}", id);
            return;
        }
        
        delegateBrain.process(message);
        
        log.debug("EmployeeNeuron {} processed message", id);
    }

    private BrainContext createBrainContext(NeuronContext neuronContext) {
        return BrainContext.builder()
            .brainId(delegateBrain != null ? delegateBrain.getId() : id)
            .department(delegateBrain != null ? delegateBrain.getDepartment() : employee.getDepartmentId())
            .sessionId(neuronContext.getSessionId())
            .channelManager(neuronContext.getChannelManager())
            .skillRegistry(neuronContext.getSkillRegistry())
            .build();
    }

    public DigitalEmployee getEmployee() {
        return employee;
    }

    public Brain getDelegateBrain() {
        return delegateBrain;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void syncStateFromEmployee() {
        EmployeeStatus empStatus = employee.getStatus();
        NeuronState targetState = mapEmployeeStatusToNeuronState(empStatus);
        setState(targetState);
        log.debug("Synced neuron {} state from employee: {} -> {}", id, empStatus, targetState);
    }

    public void syncStateToEmployee() {
        NeuronState neuronState = getState();
        EmployeeStatus targetStatus = mapNeuronStateToEmployeeStatus(neuronState);
        employee.setStatus(targetStatus);
        log.debug("Synced employee {} state from neuron: {} -> {}", employee.getEmployeeId(), neuronState, targetStatus);
    }

    private NeuronState mapEmployeeStatusToNeuronState(EmployeeStatus status) {
        return switch (status) {
            case ONLINE, ACTIVE -> NeuronState.RUNNING;
            case BUSY -> NeuronState.PROCESSING;
            case OFFLINE, AWAY -> NeuronState.SUSPENDED;
            case DISABLED, TERMINATED -> NeuronState.STOPPED;
            case LEARNING, EVOLVING -> NeuronState.ACTIVE;
        };
    }

    private EmployeeStatus mapNeuronStateToEmployeeStatus(NeuronState state) {
        return switch (state) {
            case RUNNING, ACTIVE -> EmployeeStatus.ACTIVE;
            case PROCESSING -> EmployeeStatus.BUSY;
            case IDLE, SUSPENDED -> EmployeeStatus.AWAY;
            case STOPPED -> EmployeeStatus.OFFLINE;
            case ERROR -> EmployeeStatus.DISABLED;
            case INITIALIZING, CREATED -> EmployeeStatus.ACTIVE;
        };
    }

    public static EmployeeNeuron create(DigitalEmployee employee, BrainRegistry brainRegistry, List<Tool> tools) {
        Brain brain = null;
        
        if (brainRegistry != null) {
            String department = employee.getDepartmentId();
            Optional<Brain> brainOpt = brainRegistry.getByDepartment(department);
            if (brainOpt.isEmpty()) {
                brainOpt = brainRegistry.getAll().stream().findFirst();
            }
            brain = brainOpt.orElse(null);
        }
        
        return new EmployeeNeuron(employee, brain, tools);
    }
}
