package com.company.qa.service.agent;

import com.company.qa.model.agent.*;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.service.ai.AIBudgetService;
import com.company.qa.service.ai.AIGatewayService;
import com.company.qa.service.approval.ApprovalRequestService;
import com.company.qa.service.audit.AuditLogService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Abstract base class for all autonomous AI agents.
 *
 * Provides the core agent loop:
 * 1. PLAN: Ask AI what to do next given current context
 * 2. APPROVE: Check if action needs human approval
 * 3. EXECUTE: Run the planned action using tools
 * 4. OBSERVE: Capture results
 * 5. REMEMBER: Update context with results
 * 6. AUDIT: Log action for compliance
 * 7. BUDGET_CHECK: Ensure within cost limits
 * 8. GOAL_CHECK: Is goal achieved?
 * 9. LOOP: Repeat until goal achieved or max iterations
 *
 * Concrete agents (PlaywrightAgent, FlakyAgent, etc.) must implement:
 * - plan(): How to decide next action
 * - executeAction(): How to perform specific actions
 * - isGoalAchieved(): How to know when done
 */
@Slf4j
public abstract class BaseAgent {

    // ========== EXISTING SERVICES (INJECTED) ==========
    // These are NOT duplicated - agents use existing infrastructure

    protected final AIGatewayService aiGateway;
    protected final AuditLogService auditService;
    protected final ApprovalRequestService approvalService;
    protected final AIBudgetService budgetService;

    // ========== NEW AGENT SERVICES ==========
    // These will be created in Day 2

    protected final AgentMemoryService memoryService;

    // ========== CONFIGURATION ==========

    protected final AgentConfig config;

    /**
     * Constructor - all services injected by Spring.
     */
    public BaseAgent(
            AIGatewayService aiGateway,
            AuditLogService auditService,
            ApprovalRequestService approvalService,
            AIBudgetService budgetService,
            AgentMemoryService memoryService,
            AgentConfig config
    ) {
        this.aiGateway = aiGateway;
        this.auditService = auditService;
        this.approvalService = approvalService;
        this.budgetService = budgetService;
        this.memoryService = memoryService;
        this.config = config;
    }

    /**
     * Main execution method - THE AGENT LOOP.
     *
     * This is the core autonomous behavior:
     * - Agent plans next action
     * - Agent executes action
     * - Agent observes result
     * - Agent updates memory
     * - Agent checks if goal achieved
     * - Repeat until done or timeout
     */
    public AgentResult execute(AgentGoal goal) {
        log.info("🤖 Agent starting execution for goal: {}", goal.getGoalType());

        Instant startTime = Instant.now();
        UUID executionId = UUID.randomUUID();

        // Initialize context
        AgentContext context = initializeContext(goal, executionId);

        try {
            // MAIN AGENT LOOP
            while (!shouldStop(context)) {

                context.incrementIteration();
                log.info("🔄 Iteration {}/{}", context.getCurrentIteration(), context.getMaxIterations());

                // STEP 1: PLAN - What should I do next?
                AgentPlan plan = plan(context);
                log.info("📋 Plan: {} - {}", plan.getNextAction(), plan.getReasoning());

                // STEP 2: APPROVE - Does this need human approval?
                if (plan.isRequiresApproval() || config.requiresApproval(plan.getNextAction())) {
                    log.info("⏸️  Action requires approval, waiting...");
                    boolean approved = waitForApproval(plan, context);
                    if (!approved) {
                        return buildResult(executionId, AgentStatus.STOPPED, context, startTime,
                                "User rejected action: " + plan.getNextAction());
                    }
                }

                // STEP 3: BUDGET CHECK - Are we within cost limits?
                if (context.getTotalAICost() > config.getMaxAICost()) {
                    log.warn("💰 Budget exceeded: ${} > ${}", context.getTotalAICost(), config.getMaxAICost());
                    return buildResult(executionId, AgentStatus.BUDGET_EXCEEDED, context, startTime,
                            "Budget limit exceeded");
                }

                // STEP 4: EXECUTE - Run the action
                ActionResult result = executeAction(plan.getNextAction(), plan.getActionParameters(), context);
                log.info("✅ Action {} - Success: {}", plan.getNextAction(), result.isSuccess());

                // STEP 5: OBSERVE & REMEMBER - Store what happened
                updateContextWithResult(context, plan, result);

                // STEP 6: AUDIT - Log for compliance
                auditAction(executionId, plan, result);

                // STEP 7: PERSIST - Save context to Redis
                memoryService.saveContext(executionId, context);

                // STEP 8: GOAL CHECK - Are we done?
                if (isGoalAchieved(context)) {
                    log.info("🎯 Goal achieved!");
                    return buildResult(executionId, AgentStatus.SUCCEEDED, context, startTime, null);
                }

                // STEP 9: FAILURE CHECK - Did critical action fail?
                if (!result.isSuccess() && isCriticalAction(plan.getNextAction())) {
                    log.error("❌ Critical action failed: {}", plan.getNextAction());
                    return buildResult(executionId, AgentStatus.FAILED, context, startTime,
                            "Critical action failed: " + result.getErrorMessage());
                }
            }

            // Max iterations reached
            log.warn("⏱️  Max iterations reached without achieving goal");
            return buildResult(executionId, AgentStatus.TIMEOUT, context, startTime,
                    "Max iterations reached");

        } catch (Exception e) {
            log.error("💥 Agent execution failed with exception", e);
            return buildResult(executionId, AgentStatus.FAILED, context, startTime,
                    "Exception: " + e.getMessage());
        } finally {
            // Cleanup
            memoryService.clearContext(executionId);
        }
    }

    /**
     * Initialize agent context.
     */
    private AgentContext initializeContext(AgentGoal goal, UUID executionId) {
        return AgentContext.builder()
                .goal(goal)
                .currentIteration(0)
                .maxIterations(config.getMaxIterations())
                .actionHistory(new ArrayList<>())
                .workProducts(new HashMap<>())
                .state(new HashMap<>())
                .totalAICost(0.0)
                .startedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Check if agent should stop.
     */
    private boolean shouldStop(AgentContext context) {
        return context.isMaxIterationsReached();
    }

    /**
     * Wait for human approval.
     *
     * Creates an approval request and blocks until approved/rejected.
     */
    private boolean waitForApproval(AgentPlan plan, AgentContext context) {
        // TODO: Implement in Day 3 when we have ApprovalRequest integration
        // For now, just log and return true
        log.warn("⚠️  Approval system not yet integrated, auto-approving");
        return true;
    }

    /**
     * Update context with action result.
     */
    private void updateContextWithResult(AgentContext context, AgentPlan plan, ActionResult result) {
        // Add to history
        AgentHistoryEntry entry = AgentHistoryEntry.builder()
                .iteration(context.getCurrentIteration())
                .actionType(plan.getNextAction())
                .actionInput(plan.getActionParameters())
                .actionOutput(result.getOutput())
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .durationMs(result.getDurationMs())
                .aiCost(result.getAiCost())
                .timestamp(Instant.now())
                .build();

        context.addToHistory(entry);

        // Update total cost
        if (result.getAiCost() != null) {
            context.addAICost(result.getAiCost());
        }
    }

    /**
     * Audit action for compliance.
     */
    private void auditAction(UUID executionId, AgentPlan plan, ActionResult result) {
        // Use existing AuditLogService
        // Implementation will use existing audit infrastructure
        log.debug("Auditing action: {} - Success: {}", plan.getNextAction(), result.isSuccess());
    }

    /**
     * Build final result.
     */
    private AgentResult buildResult(
            UUID executionId,
            AgentStatus status,
            AgentContext context,
            Instant startTime,
            String errorMessage
    ) {
        return AgentResult.builder()
                .executionId(executionId)
                .status(status)
                .goal(context.getGoal())
                .iterationsCompleted(context.getCurrentIteration())
                .outputs(context.getWorkProducts())
                .errorMessage(errorMessage)
                .totalAICost(context.getTotalAICost())
                .totalDuration(Duration.between(startTime, Instant.now()))
                .startedAt(startTime)
                .completedAt(Instant.now())
                .summary(buildSummary(status, context))
                .build();
    }

    /**
     * Build execution summary.
     */
    private String buildSummary(AgentStatus status, AgentContext context) {
        return String.format("Agent %s after %d iterations. Total cost: $%.4f",
                status.name().toLowerCase(),
                context.getCurrentIteration(),
                context.getTotalAICost());
    }

    /**
     * Check if action is critical (failure should stop agent).
     */
    private boolean isCriticalAction(AgentActionType actionType) {
        // Subclasses can override
        return false;
    }

    // ========== ABSTRACT METHODS - MUST BE IMPLEMENTED BY SUBCLASSES ==========

    /**
     * Plan next action based on current context.
     *
     * This is where agent intelligence lives - typically calls AI to decide
     * what to do next given the current situation.
     *
     * @param context Current agent context with history and state
     * @return Plan for next action
     */
    protected abstract AgentPlan plan(AgentContext context);

    /**
     * Execute a specific action.
     *
     * This is where agent uses tools (existing services) to perform work.
     *
     * Examples:
     * - FETCH_JIRA_STORY → call JiraService.getStory()
     * - GENERATE_TEST_CODE → call AIGatewayService.generateTest()
     * - EXECUTE_TEST → call PlaywrightTestExecutor.execute()
     * - COMMIT_CHANGES → call GitService.commit()
     *
     * @param actionType Type of action to execute
     * @param parameters Action parameters
     * @param context Current agent context
     * @return Result of action execution
     */
    protected abstract ActionResult executeAction(
            AgentActionType actionType,
            Map<String, Object> parameters,
            AgentContext context
    );

    /**
     * Check if goal has been achieved.
     *
     * Agent-specific logic to determine success.
     *
     * Examples:
     * - PlaywrightAgent: Test generated AND executed successfully
     * - FlakyAgent: Test passes 5 times in a row
     *
     * @param context Current agent context
     * @return true if goal achieved
     */
    protected abstract boolean isGoalAchieved(AgentContext context);
}