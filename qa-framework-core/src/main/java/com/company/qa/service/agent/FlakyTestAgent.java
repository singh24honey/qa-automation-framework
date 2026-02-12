package com.company.qa.service.agent;

import com.company.qa.config.FlakyTestConfig;
import com.company.qa.model.agent.*;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.AgentType;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.ai.AIBudgetService;
import com.company.qa.service.ai.AIGatewayService;
import com.company.qa.service.approval.ApprovalRequestService;
import com.company.qa.service.audit.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * FlakyTestAgent - Autonomous agent for detecting and analyzing flaky tests.
 *
 * WORKFLOW:
 * 1. Query analytics for tests with high flakiness scores (or use provided testId)
 * 2. For each flaky test:
 *    a. Run test 5 times to confirm flakiness (ANALYZE_TEST_STABILITY)
 *    b. Analyze failure patterns using AI (ANALYZE_FAILURE)
 *    c. Record pattern for future fix generation (WRITE_FILE - to save pattern)
 * 3. Generate report of all flaky tests analyzed
 *
 * DAY 1: Detection and analysis only
 * DAY 2: Will add fix generation and Git PR creation
 *
 * @author QA Framework
 * @since Week 16 Day 1
 */
@Slf4j
@Component
public class FlakyTestAgent extends BaseAgent {

    private final TestRepository testRepository;
    private final FlakyTestConfig config;
    private final ObjectMapper objectMapper;
    private final AgentOrchestrator orchestrator;

    // Agent state (per execution)
    private List<UUID> testsToAnalyze;
    private int currentTestIndex;
    private Map<String, Object> currentTestAnalysis;

    public FlakyTestAgent(
            AIGatewayService aiGateway,
            AuditLogService auditService,
            ApprovalRequestService approvalService,
            AIBudgetService budgetService,
            AgentMemoryService memoryService,
            AgentToolRegistry toolRegistry,
            TestRepository testRepository,
            FlakyTestConfig config,
            ObjectMapper objectMapper,
            AgentOrchestrator orchestrator) {

        super(aiGateway, auditService, approvalService, budgetService, memoryService, toolRegistry);
        this.testRepository = testRepository;
        this.config = config;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
    }

    /**
     * Register this agent with orchestrator at startup.
     */
    @PostConstruct
    public void register() {
        orchestrator.registerAgent(AgentType.FLAKY_TEST_FIXER, this);
        log.info("🤖 Registered FlakyTestAgent");
    }

    @Override
    protected AgentType getAgentType() {
        return AgentType.FLAKY_TEST_FIXER;
    }

    /**
     * Plan next action based on current context.
     *
     * This is called by BaseAgent in the agent loop.
     */
    @Override
    protected AgentPlan plan(AgentContext context) {
        log.info("📋 Planning next action - Test {}/{} analyzed",
                currentTestIndex, testsToAnalyze != null ? testsToAnalyze.size() : 0);

        // Initialize state on first call
        if (testsToAnalyze == null) {
            initializeAgentState(context);
        }

        // Check if we've processed all tests
        if (currentTestIndex >= testsToAnalyze.size()) {
            log.info("✅ All tests analyzed, completing execution");
            return AgentPlan.builder()
                    .nextAction(AgentActionType.COMPLETE)
                    .actionParameters(Map.of(
                            "totalAnalyzed", testsToAnalyze.size(),
                            "results", currentTestAnalysis != null ? currentTestAnalysis : Map.of()
                    ))
                    .reasoning("All tests have been analyzed for flakiness")
                    .confidence(1.0)
                    .requiresApproval(false)
                    .build();
        }

        // Get next test to analyze
        UUID testId = testsToAnalyze.get(currentTestIndex);
        Test test = testRepository.findById(testId).orElseThrow();

        // Determine next step based on what we've done
        boolean hasAnalyzedStability = hasCompletedAction(context, AgentActionType.ANALYZE_TEST_STABILITY);
        boolean hasAnalyzedFailure = hasCompletedAction(context, AgentActionType.ANALYZE_FAILURE);
        boolean hasRecordedPattern = hasCompletedAction(context, AgentActionType.WRITE_FILE);

        if (!hasAnalyzedStability) {
            // First action: analyze stability
            return planAnalyzeStability(test);
        } else if (!hasAnalyzedFailure) {
            // Second action: analyze failure pattern
            return planAnalyzeFailure(context, test);
        } else if (!hasRecordedPattern) {
            // Third action: record pattern
            return planRecordPattern(context);
        } else {
            // Pattern recorded, move to next test
            currentTestIndex++;
            // Clear action flags for next test
            //context.clearCompletedActions();
            // Recursive call to plan next test
            return plan(context);
        }
    }

    /**
     * Execute a specific action using tools.
     */
    @Override
    protected ActionResult executeAction(
            AgentActionType actionType,
            Map<String, Object> parameters,
            AgentContext context) {

        try {
            log.info("🔧 Executing action: {} with params: {}", actionType, parameters.keySet());

            // Execute using tool registry
            Map<String, Object> result = executeTool(actionType, parameters);

            boolean success = (boolean) result.getOrDefault("success", false);

            // Store result for next action
            if (success) {
                if (actionType == AgentActionType.ANALYZE_TEST_STABILITY) {
                    currentTestAnalysis = result;
                } else if (actionType == AgentActionType.ANALYZE_FAILURE) {
                    // Merge failure analysis into current analysis
                    if (currentTestAnalysis != null) {
                        currentTestAnalysis.putAll(result);
                    }
                }
            }

            return ActionResult.builder()
                    .actionType(actionType)
                    .success(success)
                    .output(result)
                    .errorMessage((String) result.get("error"))
                    .build();

        } catch (Exception e) {
            log.error("❌ Action execution failed: {}", actionType, e);

            return ActionResult.builder()
                    .actionType(actionType)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Check if goal has been achieved.
     */
    @Override
    protected boolean isGoalAchieved(AgentContext context) {
        boolean allAnalyzed = testsToAnalyze != null && currentTestIndex >= testsToAnalyze.size();

        if (allAnalyzed) {
            log.info("✅ Goal achieved: All {} tests analyzed", testsToAnalyze.size());
        }

        return allAnalyzed;
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Initialize agent state from goal parameters.
     */
    private void initializeAgentState(AgentContext context) {
        log.info("🔧 Initializing FlakyTestAgent state");

        testsToAnalyze = new ArrayList<>();
        currentTestIndex = 0;
        currentTestAnalysis = new HashMap<>();

        // Check if specific test ID provided
        Map<String, Object> goalParams = context.getGoal().getParameters();

        if (goalParams.containsKey("testId")) {
            String testIdStr = (String) goalParams.get("testId");
            testsToAnalyze.add(UUID.fromString(testIdStr));
            log.info("Analyzing specific test: {}", testIdStr);
        } else {
            // Otherwise, add all active tests for analysis
            List<Test> allTests = testRepository.findAll();
            for (Test test : allTests) {
                if (Boolean.TRUE.equals(test.getIsActive())) {
                    testsToAnalyze.add(test.getId());
                }
            }
            log.info("Added {} active tests for flakiness analysis", testsToAnalyze.size());
        }
    }

    /**
     * Plan stability analysis action.
     */
    private AgentPlan planAnalyzeStability(Test test) {
        log.info("Planning stability analysis for: {}", test.getName());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("runCount", config.getStabilityCheckRuns());

        return AgentPlan.builder()
                .nextAction(AgentActionType.ANALYZE_TEST_STABILITY)
                .actionParameters(parameters)
                .reasoning("Running test " + config.getStabilityCheckRuns() +
                        " times to detect flakiness pattern for: " + test.getName())
                .expectedOutcome("Pass/fail pattern and flakiness score")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    /**
     * Plan failure analysis action.
     */
    private AgentPlan planAnalyzeFailure(AgentContext context, Test test) {
        log.info("Planning failure pattern analysis for: {}", test.getName());

        // Get stability result from last action
        String stabilityResult = (String) currentTestAnalysis.get("stabilityResult");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("stabilityResult", stabilityResult);
        parameters.put("testCode", test.getContent());

        return AgentPlan.builder()
                .nextAction(AgentActionType.ANALYZE_FAILURE)
                .actionParameters(parameters)
                .reasoning("Using AI to categorize root cause of flakiness for: " + test.getName())
                .expectedOutcome("Root cause category (TIMING, DATA, ENVIRONMENT, or LOCATOR)")
                .confidence(0.8)
                .requiresApproval(false)
                .build();
    }

    /**
     * Plan record pattern action.
     */
    private AgentPlan planRecordPattern(AgentContext context) {
        log.info("Planning to record failure pattern");

        // Get analysis result from last action
        String stabilityResult = (String) currentTestAnalysis.get("stabilityResult");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("stabilityResult", stabilityResult);

        return AgentPlan.builder()
                .nextAction(AgentActionType.WRITE_FILE)  // Using WRITE_FILE for recording
                .actionParameters(parameters)
                .reasoning("Recording failure pattern for future fix generation")
                .expectedOutcome("Pattern saved to database")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    /**
     * Check if action has been completed.
     */
    private boolean hasCompletedAction(AgentContext context, AgentActionType actionType) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) {
            return false;
        }

        // Check most recent actions for this iteration
        return context.getActionHistory().stream()
                .filter(entry -> entry.getActionType() == actionType)
                .anyMatch(AgentHistoryEntry::isSuccess);
    }
}