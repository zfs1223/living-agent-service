package com.livingagent.core.autonomy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.employee.*;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface LLMEmployeeCreationService {

    Optional<EmployeeCreationProposal> evaluateCreationNeed(String department, String taskDescription, String workloadContext);

    Employee createFromProposal(EmployeeCreationProposal proposal);

    record EmployeeCreationProposal(
        String department,
        String departmentId,
        String name,
        String code,
        String title,
        String icon,
        List<String> capabilities,
        List<String> skills,
        List<String> tools,
        List<String> roles,
        String justification,
        String neuronRoleSegment
    ) {}
}
