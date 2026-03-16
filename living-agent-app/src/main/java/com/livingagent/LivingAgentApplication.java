package com.livingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.livingagent.core.database.entity")
@EnableJpaRepositories("com.livingagent.core.database.repository")
public class LivingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LivingAgentApplication.class, args);
    }
}
