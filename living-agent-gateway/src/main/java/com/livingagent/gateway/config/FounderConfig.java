package com.livingagent.gateway.config;

import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.core.security.auth.FounderService;
import com.livingagent.core.security.auth.FounderService.FounderCheckStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FounderConfig {

    @Bean
    public FounderService founderService(EnterpriseEmployeeRepository employeeRepository) {
        return new FounderService(new FounderCheckStrategy() {
            @Override
            public boolean hasAnyEmployee() {
                return employeeRepository.hasAnyEmployee();
            }

            @Override
            public boolean hasFounder() {
                return employeeRepository.hasFounder();
            }
        });
    }
}
