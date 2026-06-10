package com.livingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@SpringBootApplication
@EntityScan({
    "com.livingagent.core.database.entity",
    "com.livingagent.core.employee.entity",
    "com.livingagent.core.autonomous.payout",
    "com.livingagent.core.budget",
    "com.livingagent.core.config",
    "com.livingagent.core.heartbeat",
    "com.livingagent.core.security",
    "com.livingagent.core.security.profile",
    "com.livingagent.core.security.session",
    "com.livingagent.core.security.speaker",
    "com.livingagent.core.model.pool"
})
@EnableJpaRepositories({
    "com.livingagent.core.database.repository",
    "com.livingagent.core.employee.repository",
    "com.livingagent.core.autonomous.payout",
    "com.livingagent.core.budget",
    "com.livingagent.core.config",
    "com.livingagent.core.heartbeat",
    "com.livingagent.core.security.profile",
    "com.livingagent.core.security.session",
    "com.livingagent.core.security.speaker",
    "com.livingagent.core.model.pool"
})
@EnableRedisRepositories(basePackages = { "com.livingagent.core.distributed.redis.repository" })
public class LivingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LivingAgentApplication.class, args);
    }
}
