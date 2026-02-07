package com.company.qa.service.agent;

import com.company.qa.model.agent.AgentConfig;
import com.company.qa.model.agent.AgentGoal;
import com.company.qa.model.agent.AgentResult;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates agent execution lifecycle.
 *
 * Responsibilities:
 * - Start agents asynchronously
 * - Track running agents
 * - Stop agents
 * - Monitor execution progress
 *
 * Used by:
 * - AgentController (REST API)
 * - Scheduled jobs (background agents)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentExecutionService executionService;
    private final Map<AgentType, BaseAgent> agentRegistry = new ConcurrentHashMap<>();

    // Track running agents
    private final Map<UUID, CompletableFuture<AgentResult>> runningAgents = new ConcurrentHashMap<>();

    /**
     * Register an agent implementation.
     *
     * Called by agent implementations at startup via @PostConstruct.
     */
    public void registerAgent(AgentType type, BaseAgent agent) {
        agentRegistry.put(type, agent);
        log.info("🤖 Registered agent: {}", type);
    }

    /**
     * Start agent execution asynchronously.
     *
     * @param agentType Type of agent to execute
     * @param goal Goal for the agent
     * @param config Execution configuration
     * @param triggeredBy User ID who triggered
     * @param triggeredByName User name who triggered
     * @return Execution ID for tracking
     */
    /**
     * Start agent execution asynchronously.
     */
    @Async("agentExecutor")
    public CompletableFuture<AgentResult> startAgent(
            AgentType agentType,
            AgentGoal goal,
            AgentConfig config,
            UUID triggeredBy,
            String triggeredByName) {

        // Get agent implementation
        BaseAgent agent = agentRegistry.get(agentType);
        if (agent == null) {
            throw new IllegalArgumentException("No agent registered for type: " + agentType);
        }

        // Create execution record
        AgentExecution execution = executionService.createExecution(
                agentType, goal, config, triggeredBy, triggeredByName  // ✅ PASS CONFIG
        );
        UUID executionId = execution.getId();

        log.info("🚀 Starting agent: {} - Execution ID: {}", agentType, executionId);

        // Execute agent asynchronously
        CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return agent.execute(goal, config, executionId);
            } catch (Exception e) {
                log.error("Agent execution failed: {}", executionId, e);
                executionService.recordError(executionId, e.getMessage());
                throw new RuntimeException("Agent execution failed", e);
            }
        });



        // Track running agent
        runningAgents.put(executionId, future);

        // Remove from tracking when complete
        future.whenComplete((result, error) -> {
            runningAgents.remove(executionId);
            if (error == null) {
                log.info("✅ Agent completed: {} - Status: {}", executionId, result.getStatus());
            } else {
                log.error("❌ Agent failed: {}", executionId, error);
            }
        });

        return future;
    }

    /**
     * Start agent with default config.
     */
    public CompletableFuture<AgentResult> startAgent(
            AgentType agentType,
            AgentGoal goal,
            UUID triggeredBy,
            String triggeredByName) {

        AgentConfig defaultConfig = AgentConfig.builder()
                .maxIterations(20)
                .maxAICost(5.0)
                .approvalTimeoutSeconds(3600)
                .build();

        return startAgent(agentType, goal, defaultConfig, triggeredBy, triggeredByName);
    }

    /**
     * Stop a running agent.
     *
     * @param executionId Execution ID to stop
     * @return true if stopped, false if not running
     */
    public boolean stopAgent(UUID executionId) {
        CompletableFuture<AgentResult> future = runningAgents.get(executionId);

        if (future == null) {
            log.warn("Cannot stop - agent not running: {}", executionId);
            return false;
        }

        boolean cancelled = future.cancel(true);

        if (cancelled) {
            runningAgents.remove(executionId);
            executionService.updateExecution(
                    executionId,
                    null, // context not available
                    AgentStatus.STOPPED
            );
            log.info("🛑 Stopped agent: {}", executionId);
        }

        return cancelled;
    }

    /**
     * Get execution status.
     */
    public AgentExecution getExecutionStatus(UUID executionId) {
        return executionService.getExecution(executionId);
    }

    /**
     * Check if agent is running.
     */
    public boolean isRunning(UUID executionId) {
        return runningAgents.containsKey(executionId);
    }

    /**
     * Get all running agents.
     */
    public List<AgentExecution> getRunningAgents() {
        return executionService.getRunningExecutions();
    }

    /**
     * Get registered agent types.
     */
    public List<AgentType> getAvailableAgentTypes() {
        return agentRegistry.keySet().stream().toList();
    }
}