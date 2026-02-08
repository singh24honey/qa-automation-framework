package com.company.qa.config;

import com.company.qa.service.agent.tool.AgentToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for agent tools.
 *
 * Tools self-register via @PostConstruct, but this config
 * provides startup validation.
 */
@Configuration
@Slf4j
public class AgentToolConfig {

    /**
     * Validate tool registry at startup.
     */
    @Bean
    public CommandLineRunner validateTools(AgentToolRegistry registry) {
        return args -> {
            int toolCount = registry.getToolCount();
            log.info("🔧 Registered {} agent tools", toolCount);

            if (toolCount == 0) {
                log.warn("⚠️  No agent tools registered! Agents will not be able to execute actions.");
            }

            // Log available tools
            log.info("Available tools:");
            registry.getAllTools().forEach(tool ->
                    log.info("  - {} ({})", tool.getName(), tool.getActionType())
            );
        };
    }
}