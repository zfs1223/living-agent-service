package com.livingagent.core.employee.impl;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeLifecycleService;
import com.livingagent.core.employee.EmployeeOrigin;
import com.livingagent.core.employee.EmployeeRegistry;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmployeeLifecycleServiceImpl implements EmployeeLifecycleService {

    private final EmployeeService employeeService;
    private final EmployeeRegistry employeeRegistry;

    public EmployeeLifecycleServiceImpl(EmployeeService employeeService, EmployeeRegistry employeeRegistry) {
        this.employeeService = employeeService;
        this.employeeRegistry = employeeRegistry;
    }

    @Override
    public Employee create(EmployeeService.EmployeeCreationRequest request) {
        Employee employee = employeeService.createEmployee(request);
        employeeRegistry.register(employee);
        return employee;
    }

    @Override
    public Optional<Employee> get(String employeeId) {
        return employeeRegistry.findById(employeeId);
    }

    @Override
    public List<Employee> listAll() {
        return employeeRegistry.findAll();
    }

    @Override
    public void activate(String employeeId) {
        employeeRegistry.activate(employeeId);
    }

    @Override
    public void dormant(String employeeId) {
        employeeRegistry.setDormant(employeeId);
    }

    @Override
    public void wakeUp(String employeeId) {
        employeeRegistry.wakeUp(employeeId);
    }

    @Override
    public void terminate(String employeeId, String reason) {
        employeeRegistry.deactivate(employeeId, reason);
        employeeService.deleteEmployee(employeeId);
    }

    @Override
    public void archive(String employeeId) {
        Optional<Employee> opt = employeeRegistry.findById(employeeId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        Employee employee = opt.get();
        if (!employee.getStatus().canTransitionTo(EmployeeStatus.ARCHIVED)) {
            throw new IllegalStateException(
                "Cannot archive employee " + employeeId + " from status " + employee.getStatus());
        }
        employeeRegistry.deactivate(employeeId, "归档");
    }

    @Override
    public void reactivate(String employeeId) {
        Optional<Employee> opt = employeeRegistry.findById(employeeId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        Employee employee = opt.get();
        if (!employee.getStatus().canTransitionTo(EmployeeStatus.ACTIVE)) {
            throw new IllegalStateException(
                "Cannot reactivate employee " + employeeId + " from status " + employee.getStatus());
        }
        employeeRegistry.activate(employeeId);
    }

    @Override
    public void bindSkill(String employeeId, String skillName) {
        employeeService.bindSkill(employeeId, skillName);
    }

    @Override
    public void unbindSkill(String employeeId, String skillName) {
        employeeService.unbindSkill(employeeId, skillName);
    }

    @Override
    public void refreshIdleEmployees(Duration maxIdleTime) {
        employeeService.checkAndDormantIdleEmployees();
    }

    @Override
    public Map<EmployeeOrigin, Long> countByOrigin() {
        Map<EmployeeOrigin, Long> counts = new EnumMap<>(EmployeeOrigin.class);
        for (Employee employee : listAll()) {
            counts.merge(employee.getOrigin(), 1L, Long::sum);
        }
        return counts;
    }

    @Override
    public Map<EmployeeStatus, Long> countByStatus() {
        Map<EmployeeStatus, Long> counts = new EnumMap<>(EmployeeStatus.class);
        for (Employee employee : listAll()) {
            counts.merge(employee.getStatus(), 1L, Long::sum);
        }
        return counts;
    }
}
