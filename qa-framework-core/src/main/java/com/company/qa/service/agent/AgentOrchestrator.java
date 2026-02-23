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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Stop-requested flags per execution.
     *
     * future.cancel(true) only sets the thread interrupt flag, which Playwright
     * browser loops do not check. This separate AtomicBoolean is checked at the
     * top of the BaseAgent loop on every iteration, giving a clean cooperative
     * shutdown that works even during long-running browser operations.
     */
    private final Map<UUID, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    /**
     * Check if stop has been requested for an execution.
     * Called by BaseAgent at the start of each iteration.
     */
    public boolean isStopRequested(UUID executionId) {
        AtomicBoolean flag = stopFlags.get(executionId);
        return flag != null && flag.get();
    }

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
        }, Executors.newSingleThreadExecutor());



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
     * Creates execution record synchronously then launches agent async.
     *
     * Returns the AgentExecution immediately (before agent completes)
     * so callers can return the execution ID to the client right away.
     *
     * This is the preferred method for the REST controller — it avoids
     * the race condition of querying getRunningAgents() right after start.
     */
    public AgentExecution createAndStartAgent(
            AgentType agentType,
            AgentGoal goal,
            AgentConfig config,
            UUID triggeredBy,
            String triggeredByName) {

        BaseAgent agent = agentRegistry.get(agentType);
        if (agent == null) {
            throw new IllegalArgumentException("No agent registered for type: " + agentType);
        }

        // ✅ Create execution record SYNCHRONOUSLY before async task starts
        AgentExecution execution = executionService.createExecution(
                agentType, goal, config, triggeredBy, triggeredByName
        );
        UUID executionId = execution.getId();

        log.info("🚀 Launching agent async: {} - Execution ID: {}", agentType, executionId);

        // Launch async execution
        CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return agent.execute(goal, config, executionId);
            } catch (Exception e) {
                log.error("Agent execution failed: {}", executionId, e);
                executionService.recordError(executionId, e.getMessage());
                throw new RuntimeException("Agent execution failed", e);
            }
        }, Executors.newSingleThreadExecutor());

        // Register stop flag before adding to runningAgents so stopAgent() can set it
        stopFlags.put(executionId, new AtomicBoolean(false));
        runningAgents.put(executionId, future);

        future.whenComplete((result, error) -> {
            runningAgents.remove(executionId);
            stopFlags.remove(executionId);
            if (error == null) {
                log.info("✅ Agent completed: {} - Status: {}", executionId, result.getStatus());
            } else {
                log.error("❌ Agent failed: {}", executionId, error);
            }
        });

        return execution;
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

        // Step 1: Set cooperative stop flag.
        // BaseAgent checks this at the start of each iteration — this is the primary
        // mechanism. Works even when the agent is blocked inside long Playwright operations
        // because the check happens between browser actions, not inside them.
        AtomicBoolean flag = stopFlags.get(executionId);
        if (flag != null) {
            flag.set(true);
            log.info("🛑 Stop flag set for: {}", executionId);
        } else {
            log.warn("⚠️ No stop flag found for {} — agent may have just completed", executionId);
        }

        // CRITICAL: Do NOT remove the stopFlag here.
        // The agent is likely inside a long-running browser action (e.g. VerifyFixTool
        // running 5 browser sessions). It cannot check the flag until the current action
        // finishes and control returns to BaseAgent's main loop. If we remove the flag now,
        // the agent will see isStopRequested() = false and keep running.
        //
        // The flag is removed in whenComplete(), which fires only after the agent thread
        // actually terminates — ensuring the flag stays alive long enough to be read.

        // Step 2: Also cancel the future + interrupt the thread as a backup.
        // Has no effect inside Playwright I/O but helps for pure-Java blocking calls.
        future.cancel(true);

        // Step 3: Mark execution STOPPED in DB immediately for UI feedback.
        // The agent may still be running its current action, but the DB record
        // is updated now so the frontend shows STOPPED right away.
        runningAgents.remove(executionId);
        executionService.stopExecution(executionId);
        log.info("🛑 Stopped agent (flag set, current action will complete before loop check): {}", executionId);

        return true;
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