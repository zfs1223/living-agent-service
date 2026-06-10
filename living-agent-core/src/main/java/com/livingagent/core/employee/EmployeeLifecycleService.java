package com.livingagent.core.employee;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EmployeeLifecycleService {

    Employee create(EmployeeService.EmployeeCreationRequest request);

    Optional<Employee> get(String employeeId);

    List<Employee> listAll();

    void activate(String employeeId);

    void dormant(String employeeId);

    void wakeUp(String employeeId);

    void terminate(String employeeId, String reason);

    void archive(String employeeId);

    void reactivate(String employeeId);

    void bindSkill(String employeeId, String skillName);

    void unbindSkill(String employeeId, String skillName);

    void refreshIdleEmployees(Duration maxIdleTime);

    Map<EmployeeOrigin, Long> countByOrigin();

    Map<EmployeeStatus, Long> countByStatus();
}
