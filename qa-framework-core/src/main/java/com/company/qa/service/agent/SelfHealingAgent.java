package com.company.qa.service.agent;

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
 * SelfHealingAgent - Autonomous agent for fixing broken test locators.
 *
 * WORKFLOW:
 * 1. Analyze test failure to identify broken locator
 * 2. Query Element Registry for alternative locators
 * 3. Try each alternative until one works
 * 4. Update test with working locator
 * 5. Verify fix by running test
 * 6. Update Element Registry (mark old as deprecated, promote new)
 * 7. Git workflow: branch → commit → approval → PR
 *
 * DAY 3 SCOPE:
 * - Registry-based resolution only
 * - No AI fallback yet (that's Day 4)
 * - Update test content
 * - Verify fix
 * - Complete Git workflow
 *
 * @author QA Framework
 * @since Week 16 Day 3
 */
@Slf4j
@Component
public class SelfHealingAgent extends BaseAgent {

    private final TestRepository testRepository;
    private final ObjectMapper objectMapper;
    private final AgentOrchestrator orchestrator;

    // Agent state
    private List<UUID> testsToFix;
    private int currentTestIndex;
    private Map<String, Object> currentFailureAnalysis;
    private List<Map<String, Object>> availableAlternatives;
    private int currentAlternativeIndex;
    private boolean fixVerified;

    public SelfHealingAgent(
            AIGatewayService aiGateway,
            AuditLogService auditService,
            ApprovalRequestService approvalService,
            AIBudgetService budgetService,
            AgentMemoryService memoryService,
            AgentToolRegistry toolRegistry,
            TestRepository testRepository,
            ObjectMapper objectMapper,
            AgentOrchestrator orchestrator) {

        super(aiGateway, auditService, approvalService, budgetService, memoryService, toolRegistry);
        this.testRepository = testRepository;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
    }

    @PostConstruct
    public void register() {
        orchestrator.registerAgent(AgentType.SELF_HEALING_TEST_FIXER, this);
        log.info("🤖 Registered SelfHealingAgent");
    }

    @Override
    protected AgentType getAgentType() {
        return AgentType.SELF_HEALING_TEST_FIXER;
    }

    @Override
    protected AgentPlan plan(AgentContext context) {
        log.info("📋 Planning next action - Test {}/{}, Alternative {}/{}",
                currentTestIndex,
                testsToFix != null ? testsToFix.size() : 0,
                currentAlternativeIndex,
                availableAlternatives != null ? availableAlternatives.size() : 0);

        // Initialize on first call
        if (testsToFix == null) {
            initializeAgentState(context);
        }

        // Check if all tests processed
        if (currentTestIndex >= testsToFix.size()) {
            return planComplete(context);
        }

        // Get current test
        UUID testId = testsToFix.get(currentTestIndex);
        Test test = testRepository.findById(testId).orElseThrow();

        // Check completed actions
        boolean hasExtractedLocator = hasCompletedAction(context, AgentActionType.ANALYZE_FAILURE);
        boolean hasQueriedRegistry = hasCompletedAction(context, AgentActionType.QUERY_ELEMENT_REGISTRY);
        boolean hasAppliedFix = hasCompletedAction(context, AgentActionType.MODIFY_FILE);
        boolean hasVerifiedFix = hasCompletedAction(context, AgentActionType.EXECUTE_TEST);
        boolean hasUpdatedRegistry = hasCompletedAction(context, AgentActionType.UPDATE_ELEMENT_REGISTRY);

        // Git workflow
        boolean hasCreatedBranch = hasCompletedAction(context, AgentActionType.CREATE_BRANCH);
        boolean hasCommitted = hasCompletedAction(context, AgentActionType.COMMIT_CHANGES);
        boolean hasCreatedApproval = hasCompletedAction(context, AgentActionType.REQUEST_APPROVAL);
        boolean hasCreatedPR = hasCompletedAction(context, AgentActionType.CREATE_PULL_REQUEST);

        // STEP 1: Extract broken locator
        if (!hasExtractedLocator) {
            return planExtractBrokenLocator(context, test);
        }

        // STEP 2: Query registry for alternatives
        if (!hasQueriedRegistry) {
            return planQueryRegistry(context, test);
        }

        // Check if we have alternatives to try
        if (availableAlternatives == null || availableAlternatives.isEmpty()) {
            // No alternatives found - create approval for manual review
            if (!hasCreatedApproval) {
                return planCreateApprovalForManualReview(context, test);
            }
            // After approval, move to next test
            moveToNextTest(context);
            return plan(context);
        }

        // STEP 3: Try alternatives
        if (currentAlternativeIndex < availableAlternatives.size()) {

            if (!hasAppliedFix) {
                return planApplyAlternative(context, test);
            }

            if (!hasVerifiedFix) {
                return planVerifyFix(context, test);
            }

            // Check verification result
            if (fixVerified) {
                // Fix worked! Continue to registry update
                log.info("✅ Fix verified! Continuing to registry update and Git workflow");
            } else {
                // Fix didn't work, try next alternative
                log.warn("❌ Alternative {} failed, trying next", currentAlternativeIndex);
                currentAlternativeIndex++;
                // Reset flags for next attempt
                context.putState("currentTestStartIteration", context.getCurrentIteration());
                return plan(context);
            }
        } else {
            // All alternatives tried, none worked
            if (!hasCreatedApproval) {
                return planCreateApprovalForManualReview(context, test);
            }
            moveToNextTest(context);
            return plan(context);
        }

        // STEP 4: Update registry
        if (!hasUpdatedRegistry) {
            return planUpdateRegistry(context, test);
        }

        // STEP 5: Git workflow
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

        // All done for this test
        moveToNextTest(context);
        return plan(context);
    }

    @Override
    protected ActionResult executeAction(
            AgentActionType actionType,
            Map<String, Object> parameters,
            AgentContext context) {

        try {
            log.info("🔧 Executing action: {}", actionType);

            Map<String, Object> result = executeTool(actionType, parameters);
            boolean success = (boolean) result.getOrDefault("success", false);

            if (success) {
                updateStateFromActionResult(actionType, result);
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

    @Override
    protected boolean isGoalAchieved(AgentContext context) {
        boolean allFixed = testsToFix != null && currentTestIndex >= testsToFix.size();

        if (allFixed) {
            log.info("✅ Goal achieved: All {} tests processed", testsToFix.size());
        }

        return allFixed;
    }

    // ========== INITIALIZATION ==========

    private void initializeAgentState(AgentContext context) {
        log.info("🔧 Initializing SelfHealingAgent state");

        testsToFix = new ArrayList<>();
        currentTestIndex = 0;
        currentFailureAnalysis = new HashMap<>();
        availableAlternatives = null;
        currentAlternativeIndex = 0;
        fixVerified = false;

        Map<String, Object> goalParams = context.getGoal().getParameters();

        if (goalParams.containsKey("testId")) {
            String testIdStr = (String) goalParams.get("testId");
            testsToFix.add(UUID.fromString(testIdStr));
            log.info("Fixing specific test: {}", testIdStr);
        } else {
            log.info("No test ID provided in goal parameters");
        }
    }

    // ========== PLANNING METHODS ==========

    private AgentPlan planExtractBrokenLocator(AgentContext context, Test test) {
        // Get failure details from goal parameters
        Map<String, Object> goalParams = context.getGoal().getParameters();
        String errorMessage = (String) goalParams.getOrDefault("errorMessage",
                "Element not found");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("errorMessage", errorMessage);
        parameters.put("testContent", test.getContent());

        return AgentPlan.builder()
                .nextAction(AgentActionType.ANALYZE_FAILURE)
                .actionParameters(parameters)
                .reasoning("Extracting broken locator from test failure")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planQueryRegistry(AgentContext context, Test test) {
        String pageName = (String) currentFailureAnalysis.getOrDefault("pageName", "unknown");
        String elementPurpose = (String) currentFailureAnalysis.getOrDefault("elementPurpose", "unknown");
        String brokenLocator = (String) currentFailureAnalysis.get("brokenLocator");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pageName", pageName);
        parameters.put("elementPurpose", elementPurpose);
        parameters.put("brokenLocator", brokenLocator);

        return AgentPlan.builder()
                .nextAction(AgentActionType.QUERY_ELEMENT_REGISTRY)
                .actionParameters(parameters)
                .reasoning("Querying registry for alternative locators")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private AgentPlan planApplyAlternative(AgentContext context, Test test) {
        Map<String, Object> currentAlternative = availableAlternatives.get(currentAlternativeIndex);
        String newLocator = (String) currentAlternative.get("locator");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("fixedTestCode", buildFixedTestCode(test.getContent(), newLocator));

        return AgentPlan.builder()
                .nextAction(AgentActionType.MODIFY_FILE)
                .actionParameters(parameters)
                .reasoning(String.format("Trying alternative %d/%d: %s",
                        currentAlternativeIndex + 1,
                        availableAlternatives.size(),
                        newLocator))
                .confidence(0.7)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planVerifyFix(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("runCount", 3); // Run 3 times to verify

        return AgentPlan.builder()
                .nextAction(AgentActionType.EXECUTE_TEST)
                .actionParameters(parameters)
                .reasoning("Verifying fix by running test")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private AgentPlan planUpdateRegistry(AgentContext context, Test test) {
        Map<String, Object> currentAlternative = availableAlternatives.get(currentAlternativeIndex);
        String workingLocator = (String) currentAlternative.get("locator");
        String brokenLocator = (String) currentFailureAnalysis.get("brokenLocator");
        String pageName = (String) currentFailureAnalysis.get("pageName");
        String elementName = (String) currentAlternative.get("elementName");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pageName", pageName);
        parameters.put("elementName", elementName);
        parameters.put("workingLocator", workingLocator);
        parameters.put("brokenLocator", brokenLocator);

        return AgentPlan.builder()
                .nextAction(AgentActionType.UPDATE_ELEMENT_REGISTRY)
                .actionParameters(parameters)
                .reasoning("Updating registry with working locator")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateBranch(AgentContext context, Test test) {
        String branchName = "fix/locator-" + test.getName().replaceAll("[^a-zA-Z0-9]", "-");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey", "LOCATOR-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName", branchName);

        return AgentPlan.builder()
                .nextAction(AgentActionType.CREATE_BRANCH)
                .actionParameters(parameters)
                .reasoning("Creating Git branch for locator fix")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCommitChanges(AgentContext context, Test test) {
        String branchName = context.getWorkProduct("branchName", String.class);
        if (branchName == null) {
            branchName = "fix/locator-" + test.getName().replaceAll("[^a-zA-Z0-9]", "-");
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey", "LOCATOR-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName", branchName);
        parameters.put("commitMessage", String.format("Fix broken locator in test: %s", test.getName()));
        parameters.put("filePaths", List.of("tests/" + test.getName() + ".java"));

        return AgentPlan.builder()
                .nextAction(AgentActionType.COMMIT_CHANGES)
                .actionParameters(parameters)
                .reasoning("Committing locator fix")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApproval(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testCode", test.getContent());
        parameters.put("jiraKey", "LOCATOR-" + test.getId().toString().substring(0, 8));
        parameters.put("testName", test.getName());
        parameters.put("requestedBy", "SelfHealingAgent");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("Creating approval request for locator fix")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApprovalForManualReview(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testCode", test.getContent());
        parameters.put("jiraKey", "LOCATOR-MANUAL-" + test.getId().toString().substring(0, 8));
        parameters.put("testName", test.getName() + " [NEEDS MANUAL REVIEW]");
        parameters.put("requestedBy", "SelfHealingAgent");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("No working alternatives found - flagging for manual review")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreatePullRequest(AgentContext context, Test test) {
        String branchName = context.getWorkProduct("branchName", String.class);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey", "LOCATOR-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName", branchName);
        parameters.put("title", "Fix broken locator: " + test.getName());
        parameters.put("description", "Automated locator fix by SelfHealingAgent");

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
                .actionParameters(Map.of("totalFixed", testsToFix.size()))
                .reasoning("All tests processed")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    // ========== STATE MANAGEMENT ==========

    @SuppressWarnings("unchecked")
    private void updateStateFromActionResult(AgentActionType actionType, Map<String, Object> result) {
        switch (actionType) {
            case ANALYZE_FAILURE -> {
                currentFailureAnalysis = new HashMap<>(result);
            }
            case QUERY_ELEMENT_REGISTRY -> {
                availableAlternatives = (List<Map<String, Object>>) result.get("alternatives");
                currentAlternativeIndex = 0;
            }
            case EXECUTE_TEST -> {
                fixVerified = (boolean) result.getOrDefault("isStable", false);
            }
            case CREATE_BRANCH -> {
                String branchName = (String) result.get("branchName");
                if (branchName != null) {
                    // Will be used in commit and PR steps
                    currentFailureAnalysis.put("branchName", branchName);
                }
            }
        }
    }

    private void moveToNextTest(AgentContext context) {
        currentTestIndex++;
        currentFailureAnalysis = new HashMap<>();
        availableAlternatives = null;
        currentAlternativeIndex = 0;
        fixVerified = false;
        context.putState("currentTestStartIteration", context.getCurrentIteration());
    }

    private boolean hasCompletedAction(AgentContext context, AgentActionType actionType) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) {
            return false;
        }

        Integer testStartIteration = context.getState("currentTestStartIteration", Integer.class);
        if (testStartIteration == null) {
            testStartIteration = 0;
        }

        final int startIter = testStartIteration;
        return context.getActionHistory().stream()
                .filter(entry -> entry.getIteration() >= startIter)
                .filter(entry -> entry.getActionType() == actionType)
                .anyMatch(AgentHistoryEntry::isSuccess);
    }

    /**
     * Build fixed test code by replacing broken locator.
     */
    @SuppressWarnings("unchecked")
    private String buildFixedTestCode(String originalContent, String newLocator) {
        try {
            // Parse original content
            Map<String, Object> contentMap = objectMapper.readValue(originalContent, Map.class);
            List<Map<String, Object>> steps = (List<Map<String, Object>>) contentMap.get("steps");

            String brokenLocator = (String) currentFailureAnalysis.get("brokenLocator");

            // Find and replace broken locator in steps
            for (Map<String, Object> step : steps) {
                String locator = (String) step.get("locator");
                if (locator != null && locator.equals(brokenLocator)) {
                    step.put("locator", newLocator);
                    log.info("  Replaced: {} → {}", brokenLocator, newLocator);
                }
            }

            // Convert back to JSON
            return objectMapper.writeValueAsString(contentMap);

        } catch (Exception e) {
            log.error("Failed to build fixed test code: {}", e.getMessage(), e);
            return originalContent; // Return original if parsing fails
        }
    }
}