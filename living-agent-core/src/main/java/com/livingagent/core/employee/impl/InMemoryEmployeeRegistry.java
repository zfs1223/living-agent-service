package com.livingagent.core.employee.impl;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeRegistry;
import com.livingagent.core.employee.EmployeeStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryEmployeeRegistry implements EmployeeRegistry {

    private final Map<String, Employee> employees = new ConcurrentHashMap<>();
    private final Map<String, String> neuronIndex = new ConcurrentHashMap<>();

    @Override
    public void register(Employee employee) {
        employees.put(employee.getEmployeeId(), employee);
        if (employee.isDigital() && employee.getDigitalConfig() != null) {
            neuronIndex.put(employee.getDigitalConfig().getNeuronId(), employee.getEmployeeId());
        }
    }

    @Override
    public Optional<Employee> findById(String employeeId) {
        return Optional.ofNullable(employees.get(employeeId));
    }

    @Override
    public Optional<Employee> findByNeuronId(String neuronId) {
        String employeeId = neuronIndex.get(neuronId);
        return employeeId == null ? Optional.empty() : Optional.ofNullable(employees.get(employeeId));
    }

    @Override
    public List<Employee> findByDepartment(String department) {
        List<Employee> result = new ArrayList<>();
        for (Employee employee : employees.values()) {
            if (department != null && department.equalsIgnoreCase(employee.getDepartment())) {
                result.add(employee);
            }
        }
        return result;
    }

    @Override
    public List<Employee> findByStatus(EmployeeStatus status) {
        List<Employee> result = new ArrayList<>();
        for (Employee employee : employees.values()) {
            if (employee.getStatus() == status) {
                result.add(employee);
            }
        }
        return result;
    }

    @Override
    public List<Employee> findAll() {
        return new ArrayList<>(employees.values());
    }

    @Override
    public void unregister(String employeeId) {
        Employee removed = employees.remove(employeeId);
        if (removed != null && removed.isDigital() && removed.getDigitalConfig() != null) {
            neuronIndex.remove(removed.getDigitalConfig().getNeuronId());
        }
    }

    @Override
    public void activate(String employeeId) {
        findById(employeeId).ifPresent(employee -> {
            if (employee instanceof DigitalEmployee digital) {
                digital.setStatus(EmployeeStatus.ACTIVE);
            }
        });
    }

    @Override
    public void deactivate(String employeeId, String reason) {
        findById(employeeId).ifPresent(employee -> {
            if (employee instanceof DigitalEmployee digital) {
                digital.setStatus(EmployeeStatus.DISABLED);
            }
        });
    }

    @Override
    public void setDormant(String employeeId) {
        findById(employeeId).ifPresent(employee -> {
            if (employee instanceof DigitalEmployee digital) {
                digital.setStatus(EmployeeStatus.OFFLINE);
            }
        });
    }

    @Override
    public void wakeUp(String employeeId) {
        findById(employeeId).ifPresent(employee -> {
            if (employee instanceof DigitalEmployee digital) {
                digital.setStatus(EmployeeStatus.ACTIVE);
            }
        });
    }

    @Override
    public boolean exists(String employeeId) {
        return employees.containsKey(employeeId);
    }
}
