package com.livingagent.core.employee.sync;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmployeeStateSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(EmployeeStateSynchronizer.class);

    private final EmployeeService employeeService;
    private final NeuronRegistry neuronRegistry;
    
    private final Map<String, EmployeeStatus> lastKnownEmployeeStatus = new ConcurrentHashMap<>();
    private final Map<String, NeuronState> lastKnownNeuronState = new ConcurrentHashMap<>();

    private static final Map<EmployeeStatus, NeuronState> EMPLOYEE_TO_NEURON_STATE = Map.of(
        EmployeeStatus.ONLINE, NeuronState.RUNNING,
        EmployeeStatus.ACTIVE, NeuronState.RUNNING,
        EmployeeStatus.BUSY, NeuronState.PROCESSING,
        EmployeeStatus.OFFLINE, NeuronState.SUSPENDED,
        EmployeeStatus.AWAY, NeuronState.IDLE,
        EmployeeStatus.DISABLED, NeuronState.STOPPED,
        EmployeeStatus.TERMINATED, NeuronState.STOPPED,
        EmployeeStatus.LEARNING, NeuronState.ACTIVE,
        EmployeeStatus.EVOLVING, NeuronState.ACTIVE
    );

    private static final Map<NeuronState, EmployeeStatus> NEURON_TO_EMPLOYEE_STATE = Map.of(
        NeuronState.RUNNING, EmployeeStatus.ACTIVE,
        NeuronState.ACTIVE, EmployeeStatus.ACTIVE,
        NeuronState.PROCESSING, EmployeeStatus.BUSY,
        NeuronState.IDLE, EmployeeStatus.AWAY,
        NeuronState.SUSPENDED, EmployeeStatus.OFFLINE,
        NeuronState.STOPPED, EmployeeStatus.OFFLINE,
        NeuronState.ERROR, EmployeeStatus.DISABLED,
        NeuronState.INITIALIZING, EmployeeStatus.ACTIVE,
        NeuronState.CREATED, EmployeeStatus.ACTIVE
    );

    public EmployeeStateSynchronizer(EmployeeService employeeService, NeuronRegistry neuronRegistry) {
        this.employeeService = employeeService;
        this.neuronRegistry = neuronRegistry;
    }

    public void syncEmployeeToNeuron(String employeeId) {
        employeeService.getEmployee(employeeId).ifPresent(emp -> {
            if (!emp.isDigital()) {
                return;
            }
            
            DigitalEmployee de = (DigitalEmployee) emp;
            String neuronId = de.getDigitalConfig().getNeuronId();
            
            neuronRegistry.get(neuronId).ifPresent(neuron -> {
                EmployeeStatus currentStatus = emp.getStatus();
                EmployeeStatus lastStatus = lastKnownEmployeeStatus.get(employeeId);
                
                if (currentStatus != lastStatus) {
                    NeuronState targetState = EMPLOYEE_TO_NEURON_STATE.getOrDefault(currentStatus, NeuronState.ACTIVE);
                    neuron.setState(targetState);
                    lastKnownEmployeeStatus.put(employeeId, currentStatus);
                    lastKnownNeuronState.put(neuronId, targetState);
                    
                    log.info("Synced employee {} status {} to neuron state {}", employeeId, currentStatus, targetState);
                }
            });
        });
    }

    public void syncNeuronToEmployee(String neuronId) {
        String employeeId = IdUtils.neuronToEmployeeId(neuronId);
        
        neuronRegistry.get(neuronId).ifPresent(neuron -> {
            employeeService.getEmployee(employeeId).ifPresent(emp -> {
                if (!emp.isDigital()) {
                    return;
                }
                
                DigitalEmployee de = (DigitalEmployee) emp;
                NeuronState currentState = neuron.getState();
                NeuronState lastState = lastKnownNeuronState.get(neuronId);
                
                if (currentState != lastState) {
                    EmployeeStatus targetStatus = NEURON_TO_EMPLOYEE_STATE.getOrDefault(currentState, EmployeeStatus.ACTIVE);
                    de.setStatus(targetStatus);
                    lastKnownNeuronState.put(neuronId, currentState);
                    lastKnownEmployeeStatus.put(employeeId, targetStatus);
                    
                    log.info("Synced neuron {} state {} to employee status {}", neuronId, currentState, targetStatus);
                }
            });
        });
    }

    public void syncAllEmployeesToNeurons() {
        employeeService.listDigitalEmployees().forEach(emp -> {
            if (emp.isDigital()) {
                syncEmployeeToNeuron(emp.getEmployeeId());
            }
        });
    }

    public void syncAllNeuronsToEmployees() {
        neuronRegistry.getAll().forEach(neuron -> {
            syncNeuronToEmployee(neuron.getId());
        });
    }

    @Scheduled(fixedRate = 30000)
    public void periodicSync() {
        log.debug("Running periodic state sync...");
        syncAllNeuronsToEmployees();
    }

    public void forceSync(String employeeId) {
        syncEmployeeToNeuron(employeeId);
        
        employeeService.getEmployee(employeeId).ifPresent(emp -> {
            if (emp.isDigital()) {
                DigitalEmployee de = (DigitalEmployee) emp;
                String neuronId = de.getDigitalConfig().getNeuronId();
                syncNeuronToEmployee(neuronId);
            }
        });
    }

    public StateSyncStatus getSyncStatus(String employeeId) {
        Optional<Employee> empOpt = employeeService.getEmployee(employeeId);
        if (empOpt.isEmpty() || !empOpt.get().isDigital()) {
            return new StateSyncStatus(employeeId, null, null, false, "Not a digital employee");
        }
        
        DigitalEmployee de = (DigitalEmployee) empOpt.get();
        String neuronId = de.getDigitalConfig().getNeuronId();
        
        Optional<Neuron> neuronOpt = neuronRegistry.get(neuronId);
        if (neuronOpt.isEmpty()) {
            return new StateSyncStatus(employeeId, de.getStatus(), null, false, "Neuron not found");
        }
        
        Neuron neuron = neuronOpt.get();
        EmployeeStatus empStatus = de.getStatus();
        NeuronState neuronState = neuron.getState();
        
        boolean inSync = isStatusInSync(empStatus, neuronState);
        String message = inSync ? "States are synchronized" : "States are out of sync";
        
        return new StateSyncStatus(employeeId, empStatus, neuronState, inSync, message);
    }

    private boolean isStatusInSync(EmployeeStatus empStatus, NeuronState neuronState) {
        NeuronState expectedNeuronState = EMPLOYEE_TO_NEURON_STATE.get(empStatus);
        return expectedNeuronState == neuronState;
    }

    public record StateSyncStatus(
        String employeeId,
        EmployeeStatus employeeStatus,
        NeuronState neuronState,
        boolean inSync,
        String message
    ) {}
}
