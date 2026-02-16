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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * FlakyTestAgent - Autonomous agent for detecting, analyzing, and fixing flaky tests.
 *
 * COMPLETE WORKFLOW (Days 1-2):
 *
 * Day 1: Detection & Analysis
 * 1. Run test 5 times to confirm flakiness
 * 2. Analyze failure patterns using AI
 * 3. Record pattern to database
 *
 * Day 2: Fix & Verify
 * 4. Generate fix based on root cause (max 3 attempts)
 * 5. Apply fix to test content
 * 6. Verify fix by running test 5 times
 * 7. If stable → Git workflow (branch → commit → approval → PR)
 * 8. If still flaky → Try next fix (up to 3 total attempts)
 * 9. If all fail → Create approval for manual review
 *
 * @author QA Framework
 * @since Week 16
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

    // Day 2: Fix attempt tracking
    private int fixAttemptCount;
    private String originalTestContent;
    private String lastAppliedFix;
    private boolean fixVerified;

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

    @PostConstruct
    public void register() {
        orchestrator.registerAgent(AgentType.FLAKY_TEST_FIXER, this);
        log.info("🤖 Registered FlakyTestAgent");
    }

    @Override
    protected AgentType getAgentType() {
        return AgentType.FLAKY_TEST_FIXER;
    }

    @Override
    protected AgentPlan plan(AgentContext context) {
        log.info("📋 Planning next action - Test {}/{}, Fix attempt {}/{}",
                currentTestIndex,
                testsToAnalyze != null ? testsToAnalyze.size() : 0,
                fixAttemptCount,
                config.getMaxFixAttempts());

        // Initialize state on first call
        if (testsToAnalyze == null) {
            initializeAgentState(context);
        }

        AgentActionType lastAction = getLastActionType(context);
        if (lastAction != AgentActionType.REQUEST_APPROVAL) {
            int consecutiveFailures = countConsecutiveFailures(context, lastAction);

            if (consecutiveFailures >= 3) {
                log.error("❌ Action {} failed {} times consecutively - aborting",
                        lastAction, consecutiveFailures);

                return AgentPlan.builder()
                        .nextAction(AgentActionType.REQUEST_APPROVAL)
                        .actionParameters(Map.of(
                                "testName", " [FAILED - NEEDS MANUAL FIX]",
                                "requestedBy", "FlakyTestAgent",
                                "reason", "Action " + lastAction + " failed " + consecutiveFailures + " times"
                        ))
                        .reasoning("Multiple consecutive failures - flagging for manual review")
                        .confidence(1.0)
                        .requiresApproval(false)
                        .build();
            }
        }
        // Check if we've processed all tests
        if (currentTestIndex >= testsToAnalyze.size()) {
            return planComplete(context);
        }

        // Get current test
        UUID testId = testsToAnalyze.get(currentTestIndex);
        Test test = testRepository.findById(testId).orElseThrow();

        // Check what's been done for current test
        boolean hasAnalyzedStability = hasCompletedAction(context, AgentActionType.ANALYZE_TEST_STABILITY);
        boolean hasAnalyzedFailure = hasCompletedAction(context, AgentActionType.ANALYZE_FAILURE);
        boolean hasRecordedPattern = hasCompletedAction(context, AgentActionType.WRITE_FILE);

        // Day 2: Fix actions
        boolean hasGeneratedFix = hasCompletedAction(context, AgentActionType.SUGGEST_FIX);
        boolean hasAppliedFix = hasCompletedAction(context, AgentActionType.MODIFY_FILE);
        boolean hasVerifiedFix = hasCompletedAction(context, AgentActionType.EXECUTE_TEST);

        // Git workflow actions
        boolean hasCreatedBranch = hasCompletedAction(context, AgentActionType.CREATE_BRANCH);
        boolean hasCommitted = hasCompletedAction(context, AgentActionType.COMMIT_CHANGES);
        boolean hasCreatedApproval = hasCompletedAction(context, AgentActionType.REQUEST_APPROVAL);
        boolean hasCreatedPR = hasCompletedAction(context, AgentActionType.CREATE_PULL_REQUEST);

        // ========== DAY 1: DETECTION & ANALYSIS ==========

        if (!hasAnalyzedStability) {
            return planAnalyzeStability(test);
        }

        if (!hasAnalyzedFailure) {
            return planAnalyzeFailure(context, test);
        }

        if (!hasRecordedPattern) {
            return planRecordPattern(context,test);
        }

        // ========== DAY 2: FIX GENERATION & VERIFICATION ==========

        // Check if we should attempt a fix
        if (fixAttemptCount < config.getMaxFixAttempts()) {

            if (!hasGeneratedFix) {
                return planGenerateFix(context, test);
            }

            if (!hasAppliedFix) {
                return planApplyFix(context, test);
            }

            if (!hasVerifiedFix) {
                return planVerifyFix(context, test);
            }

            // If we reach here, verification is complete
            // Check if fix was successful
            if (fixVerified) {
                log.info("✅ Fix verified! Proceeding to Git workflow");
                // Fall through to Git workflow
            } else {
                // Fix didn't work, try next attempt
                log.warn("❌ Fix attempt {} failed, trying next fix", fixAttemptCount);
                fixAttemptCount++;
                context.putState("currentTestStartIteration", context.getCurrentIteration());
                return plan(context); // Recursive call to try next fix
            }
        } else {
            // All fix attempts exhausted
            if (!hasCreatedApproval) {
                return planCreateApprovalForManualReview(context, test);
            }
            // After creating approval, move to next test
            moveToNextTest(context);
            return plan(context);
        }

        // ========== GIT WORKFLOW (after successful fix) ==========

        if (!hasCreatedBranch) {
            return planCreateBranch(context, test);
        }

        if (!hasCommitted) {
            return planCommitChanges(context, test);
        }

        if (!hasCreatedApproval) {
            return planCreateApproval(context, test);
        }

        if (!hasCreatedPR) {
            return planCreatePullRequest(context, test);
        }

        // All done for this test, move to next
        moveToNextTest(context);
        return plan(context);
    }


    /**
     * ✅ NEW HELPER: Count consecutive failures of same action
     */
    private int countConsecutiveFailures(AgentContext context, AgentActionType actionType) {
        if (context.getActionHistory() == null || actionType == null) {
            return 0;
        }

        int count = 0;
        List<AgentHistoryEntry> history = context.getActionHistory();

        // Count backwards from most recent
        for (int i = history.size() - 1; i >= 0; i--) {
            AgentHistoryEntry entry = history.get(i);

            if (entry.getActionType() != actionType) {
                break;  // Different action type - stop counting
            }

            if (!entry.isSuccess()) {
                count++;
            } else {
                break;  // Success - stop counting
            }
        }

        return count;
    }

    /**
     * ✅ NEW HELPER: Get last action type (same as SelfHealingAgent)
     */
    private AgentActionType getLastActionType(AgentContext context) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) {
            return null;
        }

        List<AgentHistoryEntry> history = context.getActionHistory();
        return history.get(history.size() - 1).getActionType();
    }
    @Override
    protected ActionResult executeAction(
            AgentActionType actionType,
            Map<String, Object> parameters,
            AgentContext context) {

        try {
            log.info("🔧 Executing action: {}", actionType);

            // ✅ NEW: Handle meta-actions that don't need tools
            if (isMetaAction(actionType)) {
                log.info("✅ Meta-action {} completed", actionType);
                return ActionResult.builder()
                        .actionType(actionType)
                        .success(true)
                        .output(parameters)
                        .build();
            }
            // Execute using tool registry
            Map<String, Object> result = executeTool(actionType, parameters);

            boolean success = (boolean) result.getOrDefault("success", false);

            // Update state based on action results
            if (success) {
                updateStateFromActionResult(actionType, result);
                if (actionType == AgentActionType.CREATE_BRANCH) {
                    String branchName = (String) result.get("branchName");
                    if (branchName != null) {
                        context.putWorkProduct("branchName", branchName);
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


    private boolean isMetaAction(AgentActionType actionType) {
        return actionType == AgentActionType.COMPLETE ||
                actionType == AgentActionType.INITIALIZE ||
                actionType == AgentActionType.FINALIZE ||
                actionType == AgentActionType.ABORT;
    }

    @Override
    protected boolean isGoalAchieved(AgentContext context) {
        boolean allAnalyzed = testsToAnalyze != null && currentTestIndex >= testsToAnalyze.size();

        if (allAnalyzed) {
            log.info("✅ Goal achieved: All {} tests processed", testsToAnalyze.size());
        }

        return allAnalyzed;
    }

    // ========== INITIALIZATION ==========

    private void initializeAgentState(AgentContext context) {
        log.info("🔧 Initializing FlakyTestAgent state");

        testsToAnalyze = new ArrayList<>();
        currentTestIndex = 0;
        currentTestAnalysis = new HashMap<>();
        fixAttemptCount = 0;
        originalTestContent = null;
        lastAppliedFix = null;
        fixVerified = false;

        // Check if specific test ID provided
        Map<String, Object> goalParams = context.getGoal().getParameters();

        if (goalParams.containsKey("testId")) {
            String testIdStr = (String) goalParams.get("testId");
            testsToAnalyze.add(UUID.fromString(testIdStr));
            log.info("Analyzing specific test: {}", testIdStr);
        } else {
            // Add all active tests
            List<Test> allTests = testRepository.findAll();
            for (Test test : allTests) {
                if (Boolean.TRUE.equals(test.getIsActive())) {
                    testsToAnalyze.add(test.getId());
                }
            }
            log.info("Added {} active tests for analysis", testsToAnalyze.size());
        }
    }

    // ========== PLANNING METHODS ==========

    private AgentPlan planAnalyzeStability(Test test) {
        // Backup original content before any modifications
        if (originalTestContent == null) {
            originalTestContent = test.getContent();
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("runCount", config.getStabilityCheckRuns());

        return AgentPlan.builder()
                .nextAction(AgentActionType.ANALYZE_TEST_STABILITY)
                .actionParameters(parameters)
                .reasoning("Running test " + config.getStabilityCheckRuns() +
                        " times to detect flakiness for: " + test.getName())
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planAnalyzeFailure(AgentContext context, Test test) {
        String stabilityResult = (String) currentTestAnalysis.get("stabilityResult");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("stabilityResult", stabilityResult);
        parameters.put("testCode", test.getContent());

        return AgentPlan.builder()
                .nextAction(AgentActionType.ANALYZE_FAILURE)
                .actionParameters(parameters)
                .reasoning("Using AI to categorize root cause for: " + test.getName())
                .confidence(0.8)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planRecordPattern(AgentContext context,Test test) {
        String stabilityResult = (String) currentTestAnalysis.get("stabilityResult");
        String testClassName = (String) currentTestAnalysis.get("testCaseName");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("stabilityResult", stabilityResult);
        parameters.put("testCode", test.getContent());
        parameters.put("testClassName", testClassName);

        return AgentPlan.builder()
                .nextAction(AgentActionType.WRITE_FILE)
                .actionParameters(parameters)
                .reasoning("Recording failure pattern for future reference")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planGenerateFix(AgentContext context, Test test) {
        String stabilityResult = (String) currentTestAnalysis.get("stabilityResult");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("stabilityResult", stabilityResult);
        parameters.put("testCode", test.getContent());
        parameters.put("attemptNumber", fixAttemptCount + 1);

        return AgentPlan.builder()
                .nextAction(AgentActionType.SUGGEST_FIX)
                .actionParameters(parameters)
                .reasoning(String.format("Generating fix attempt %d/%d for: %s",
                        fixAttemptCount + 1, config.getMaxFixAttempts(), test.getName()))
                .confidence(0.7)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planApplyFix(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("fixedTestCode", lastAppliedFix);

        return AgentPlan.builder()
                .nextAction(AgentActionType.MODIFY_FILE)
                .actionParameters(parameters)
                .reasoning("Applying fix to test content")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planVerifyFix(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("runCount", config.getVerificationRuns());

        return AgentPlan.builder()
                .nextAction(AgentActionType.EXECUTE_TEST)
                .actionParameters(parameters)
                .reasoning("Verifying fix by running test " + config.getVerificationRuns() + " times")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateBranch(AgentContext context, Test test) {
        String branchName = "fix/flaky-" + test.getName().replaceAll("[^a-zA-Z0-9]", "-");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey", "FLAKY-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName", branchName);

        return AgentPlan.builder()
                .nextAction(AgentActionType.CREATE_BRANCH)
                .actionParameters(parameters)
                .reasoning("Creating Git branch for flaky test fix: " + branchName)
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCommitChanges(AgentContext context, Test test) {
        String branchName = context.getWorkProduct("branchName", String.class);
        if (branchName == null) {
            branchName = "fix/flaky-" + test.getName().replaceAll("[^a-zA-Z0-9]", "-");
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey", "FLAKY-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName", branchName);
        parameters.put("commitMessage", String.format("Fix flaky test: %s\n\nFixed after %d attempts using AI-generated fix",
                test.getName(), fixAttemptCount + 1));
        parameters.put("filePaths", List.of("tests/" + test.getName() + ".java"));

        return AgentPlan.builder()
                .nextAction(AgentActionType.COMMIT_CHANGES)
                .actionParameters(parameters)
                .reasoning("Committing flaky test fix")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApproval(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testCode", test.getContent());
        parameters.put("jiraKey", "FLAKY-" + test.getId().toString().substring(0, 8));
        parameters.put("testName", test.getName());
        parameters.put("requestedBy", "FlakyTestAgent");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("Creating approval request for flaky test fix")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApprovalForManualReview(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testCode", originalTestContent); // Restored original
        parameters.put("jiraKey", "FLAKY-MANUAL-" + test.getId().toString().substring(0, 8));
        parameters.put("testName", test.getName() + " [NEEDS MANUAL REVIEW]");
        parameters.put("requestedBy", "FlakyTestAgent");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("All fix attempts failed - flagging for manual review")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreatePullRequest(AgentContext context, Test test) {
// Get branch name from CREATE_BRANCH action result
        String branchName = context.getWorkProduct("branchName", String.class);
        if (branchName == null) {
            branchName = "fix/flaky-" + test.getName().replaceAll("[^a-zA-Z0-9]", "-");
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey", "FLAKY-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName", branchName);
        parameters.put("title", "Fix flaky test: " + test.getName());
        parameters.put("description", String.format(
                "Automated fix for flaky test\n\nTest: %s\nFixed after %d attempts\n\nPlease review and merge.",
                test.getName(), fixAttemptCount + 1));

        return AgentPlan.builder()
                .nextAction(AgentActionType.CREATE_PULL_REQUEST)
                .actionParameters(parameters)
                .reasoning("Creating pull request for review")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planComplete(AgentContext context) {
        return AgentPlan.builder()
                .nextAction(AgentActionType.COMPLETE)
                .actionParameters(Map.of(
                        "totalAnalyzed", testsToAnalyze.size(),
                        "results", currentTestAnalysis != null ? currentTestAnalysis : Map.of()
                ))
                .reasoning("All tests processed")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    // ========== STATE MANAGEMENT ==========

    private void updateStateFromActionResult(AgentActionType actionType, Map<String, Object> result) throws JsonProcessingException {
        switch (actionType) {
            case ANALYZE_TEST_STABILITY, ANALYZE_FAILURE -> {
                if (currentTestAnalysis == null) {
                    currentTestAnalysis = new HashMap<>();
                }
                currentTestAnalysis.putAll(result);
            }
            case SUGGEST_FIX -> {
                Object fixed = result.get("fixedTestCode");

                lastAppliedFix = objectMapper.writeValueAsString(fixed);
            }
            case EXECUTE_TEST -> {
                // This is verification
                fixVerified = (boolean) result.get("isStable");
            }
        }
    }

    private void moveToNextTest(AgentContext context) {
        currentTestIndex++;
        fixAttemptCount = 0;
        originalTestContent = null;
        lastAppliedFix = null;
        fixVerified = false;
        currentTestAnalysis = new HashMap<>();
        context.putState("currentTestStartIteration", context.getCurrentIteration());
    }

    private boolean hasCompletedAction(AgentContext context, AgentActionType actionType) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) {
            return false;
        }

        // Get iteration when current test started
        Integer testStartIteration = context.getState("currentTestStartIteration", Integer.class);
        if (testStartIteration == null) {
            testStartIteration = 0;
        }

        // Only check actions since current test started
        Integer finalTestStartIteration = testStartIteration;
        return context.getActionHistory().stream()
                .filter(entry -> entry.getIteration() >= finalTestStartIteration)
                .filter(entry -> entry.getActionType() == actionType)
                .anyMatch(AgentHistoryEntry::isSuccess);
    }
}