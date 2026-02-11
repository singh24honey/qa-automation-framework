package com.company.qa.config;

import com.company.qa.service.agent.tool.AgentToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Agent tool validation configuration.
 *
 * Validates that tools registered successfully after application startup.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class AgentToolConfig {

    private final AgentToolRegistry toolRegistry;

    /**
     * Validate tools after application is ready.
     *
     * Runs AFTER @PostConstruct, so tools are already registered.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateAgentTools() {
        int toolCount = toolRegistry.getToolCount();

        log.info("═══════════════════════════════════════════");
        log.info("🔧 Agent Tool Registration Summary");
        log.info("═══════════════════════════════════════════");
        log.info("Total tools registered: {}", toolCount);

        if (toolCount == 0) {
            log.error("❌ ERROR: No agent tools registered!");
            log.error("Agents will not be able to execute actions.");
        } else {
            log.info("✅ Tools successfully registered:");
            toolRegistry.getAllTools().forEach(tool ->
                    log.info("   • {} → {}",
                            String.format("%-25s", tool.getActionType()),
                            tool.getName())
            );
        }

        log.info("═══════════════════════════════════════════");
    }
}