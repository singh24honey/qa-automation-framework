package com.company.qa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.company.qa.repository")
@EnableRedisRepositories(basePackages = "none") // Disable Redis repository scanning
public class RepositoryConfig {
    // No additional code needed
}