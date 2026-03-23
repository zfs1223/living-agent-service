package com.livingagent.gateway.config;

import com.livingagent.core.security.EmployeeService;
import com.livingagent.core.security.auth.FounderService;
import com.livingagent.core.security.auth.FounderService.FounderCheckStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FounderConfig {

    @Bean
    public FounderService founderService(EmployeeService employeeService) {
        return new FounderService(new FounderCheckStrategy() {
            @Override
            public boolean hasAnyEmployee() {
                return employeeService.hasAnyEmployee();
            }

            @Override
            public boolean hasFounder() {
                return employeeService.hasFounder();
            }
        });
    }
}
