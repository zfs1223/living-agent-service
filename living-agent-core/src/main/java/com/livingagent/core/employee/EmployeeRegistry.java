package com.livingagent.core.employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeRegistry {

    void register(Employee employee);

    Optional<Employee> findById(String employeeId);

    Optional<Employee> findByNeuronId(String neuronId);

    List<Employee> findByDepartment(String department);

    List<Employee> findByStatus(EmployeeStatus status);

    List<Employee> findAll();

    void unregister(String employeeId);

    void activate(String employeeId);

    void deactivate(String employeeId, String reason);

    void setDormant(String employeeId);

    void wakeUp(String employeeId);

    boolean exists(String employeeId);
}
