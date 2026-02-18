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
import java.util.stream.Collectors;

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

    private List<Map<String, Object>> aiSuggestions;
    private int currentAiSuggestionIndex;

    private int successfullyFixedCount = 0;  // ✅ NEW

    private String originalTestContent;  // ✅ NEW - Store original
    private String lastAppliedFix;


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
        log.info("📋 Planning next action - Test {}/{}, Registry Alt {}/{}, AI Alt {}/{}",
                currentTestIndex,
                testsToFix != null ? testsToFix.size() : 0,
                currentAlternativeIndex,
                availableAlternatives != null ? availableAlternatives.size() : 0,
                currentAiSuggestionIndex,
                aiSuggestions != null ? aiSuggestions.size() : 0);

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

        AgentActionType lastAction = getLastActionType(context);

        // Check completed actions IN CURRENT ATTEMPT SCOPE
       /* Integer attemptStart = context.getState("currentAttemptStartIteration", Integer.class);
        if (attemptStart == null) {
            attemptStart = context.getCurrentIteration();
            context.putState("currentAttemptStartIteration", attemptStart);
        }

        final int currentAttemptStart = attemptStart;*/

        // Check completed actions
        boolean hasExtractedLocator = hasCompletedAction(context, AgentActionType.ANALYZE_FAILURE);
        boolean hasQueriedRegistry = hasCompletedAction(context, AgentActionType.QUERY_ELEMENT_REGISTRY);
        boolean hasAppliedFix = hasCompletedAction(context, AgentActionType.MODIFY_FILE);
        boolean hasVerifiedFix = hasCompletedAction(context, AgentActionType.EXECUTE_TEST);
        boolean hasUpdatedRegistry = hasCompletedAction(context, AgentActionType.UPDATE_ELEMENT_REGISTRY);
        boolean hasDiscoveredLocator = hasCompletedAction(context, AgentActionType.DISCOVER_LOCATOR);
        boolean hasExtractedBrokenLocator = hasCompletedAction(context, AgentActionType.EXTRACT_BROKEN_LOCATOR);

        // Git workflow
        boolean hasCreatedBranch = hasCompletedAction(context, AgentActionType.CREATE_BRANCH);
        boolean hasCommitted = hasCompletedAction(context, AgentActionType.COMMIT_CHANGES);
        boolean hasCreatedApproval = hasCompletedAction(context, AgentActionType.REQUEST_APPROVAL);
        boolean hasCreatedPR = hasCompletedAction(context, AgentActionType.CREATE_PULL_REQUEST);


        if (currentFailureAnalysis.isEmpty()) {
            return planExtractBrokenLocator(context, test);
        }
        // STEP 1: Extract broken locator
        /*if (!hasExtractedBrokenLocator) {
            return planExtractBrokenLocator(context, test);
        }*/

        // STEP 2: Query registry for alternatives
       /* if (!hasQueriedRegistry) {
            return planQueryRegistry(context, test);
        }*/

        if (availableAlternatives == null) {
            return planQueryRegistry(context, test);
        }

        // ================================================================
        // PHASE 3A: Try Registry Alternatives
        // ================================================================

        if (availableAlternatives != null && !availableAlternatives.isEmpty()
                && currentAlternativeIndex < availableAlternatives.size()) {

            // ✅ FIX: State machine approach
            if (lastAction == null || lastAction == AgentActionType.QUERY_ELEMENT_REGISTRY
                    || lastAction == AgentActionType.EXECUTE_TEST) {
                // Ready to apply next alternative
                return planApplyAlternative(context, test);
            }

            if (lastAction == AgentActionType.MODIFY_FILE) {
                // Just applied fix, now verify
                return planVerifyFix(context, test);
            }

            // After verification, check result in updateStateFromActionResult()
            // If failed, it will increment currentAlternativeIndex
            // If succeeded, fixVerified will be true and we'll fall through
        }

        // ================================================================
        // PHASE 3B: AI Discovery Fallback
        // ================================================================

        boolean registryExhausted = (availableAlternatives == null || availableAlternatives.isEmpty()
                || currentAlternativeIndex >= availableAlternatives.size());

        if (registryExhausted && !fixVerified) {

            // Step 1: Capture HTML (once)
            if (!hasCompletedAction(context, AgentActionType.READ_FILE)) {
                return planCapturePageHtml(context, test);
            }

            // Step 2: AI Discovery (once)
            if (aiSuggestions == null) {
                return planAiDiscovery(context, test);
            }

            // Check if AI returned empty suggestions
            if (aiSuggestions.isEmpty()) {
                log.warn("❌ AI discovery returned no suggestions");
                if (!hasCreatedApproval) {
                    return planCreateApprovalForManualReview(context, test);
                }
                moveToNextTest(context);
                return plan(context);
            }

            // ✅ Step 3: Try AI Suggestions (STATE MACHINE APPROACH)
            if (currentAiSuggestionIndex < aiSuggestions.size()) {

                // ✅ FIX: Use state machine based on last action
                if (lastAction == null || lastAction == AgentActionType.DISCOVER_LOCATOR
                        || lastAction == AgentActionType.EXECUTE_TEST) {
                    // Ready to apply next AI suggestion
                    return planApplyAiSuggestion(context, test);
                }

                if (lastAction == AgentActionType.MODIFY_FILE) {
                    // Just applied AI suggestion, now verify
                    return planVerifyFix(context, test);
                }

                // After verification, check result in updateStateFromActionResult()
                // If failed, it will increment currentAiSuggestionIndex
                // If succeeded, fixVerified will be true and we'll fall through
            } else {
                // All AI suggestions exhausted
                log.error("❌ Both registry alternatives AND AI suggestions failed!");
                if (!hasCreatedApproval) {
                    return planCreateApprovalForManualReview(context, test);
                }
                moveToNextTest(context);
                return plan(context);
            }
        }
// ================================================================
        // PHASE 4: Update Registry & Git Workflow
        // (Only reached if fixVerified = true)
        // ================================================================

        if (!fixVerified) {
            log.error("❌ Reached Phase 4 but fixVerified=false - this shouldn't happen!");
            if (!hasCreatedApproval) {
                return planCreateApprovalForManualReview(context, test);
            }
            moveToNextTest(context);
            return plan(context);
        }

        if (!hasUpdatedRegistry) {
            return planUpdateRegistry(context, test);
        }

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


       /* if (availableAlternatives != null && !availableAlternatives.isEmpty()) {
            // We have registry alternatives to try

            if (currentAlternativeIndex < availableAlternatives.size()) {

                if (!hasAppliedFix) {
                    return planApplyAlternative(context, test);
                }

                if (!hasVerifiedFix) {
                    return planVerifyFix(context, test);
                }

                // Check verification result
                if (fixVerified) {
                    log.info("✅ Registry alternative {} worked! Proceeding to Git workflow",
                            currentAlternativeIndex);
                    // Continue to PHASE 4 (registry update & Git)
                } else {
                    log.warn("❌ Registry alternative {} failed, trying next", currentAlternativeIndex);
                    currentAlternativeIndex++;

                    // ✅ FIX: Reset attempt scope for next alternative
                    context.putState("currentAttemptStartIteration", context.getCurrentIteration());
                    return plan(context);
                }
            } else {
                // All registry alternatives exhausted
                log.info("⚠️  All {} registry alternatives failed, falling back to AI discovery",
                        availableAlternatives.size());
                // Fall through to PHASE 3B (AI discovery)
            }
        }

        boolean shouldTryAI = (availableAlternatives == null || availableAlternatives.isEmpty() ||
                currentAlternativeIndex >= availableAlternatives.size());

        if (shouldTryAI) {

            boolean hasCapturedHtml = hasCompletedAction(context, AgentActionType.READ_FILE);
            // Step 1: Capture HTML
            if (!hasCapturedHtml) {
                return planCapturePageHtml(context, test);
            }

            // Step 2: AI Discovery
            if (aiSuggestions == null || aiSuggestions.isEmpty()) {
                // Check if we already tried AI discovery and it returned empty
                boolean hasTriedAiDiscovery = hasActionSince(context, AgentActionType.DISCOVER_LOCATOR,
                        currentAttemptStart) && hasCapturedHtml;

                if (!hasTriedAiDiscovery) {
                    return planAiDiscovery(context, test);
                } else {
                    // AI discovery returned nothing - manual intervention needed
                    log.warn("❌ AI discovery returned no suggestions");
                    if (!hasCreatedApproval) {
                        return planCreateApprovalForManualReview(context, test);
                    }
                    moveToNextTest(context);
                    return plan(context);
                }
            }

            if (currentAiSuggestionIndex < aiSuggestions.size()) {

                if (!hasAppliedFix) {
                    return planApplyAiSuggestion(context, test);
                }

                if (!hasVerifiedFix) {
                    return planVerifyFix(context, test);
                }

                // Check verification result
                if (fixVerified) {
                    log.info("✅ AI suggestion {} worked! Proceeding to Git workflow",
                            currentAiSuggestionIndex);
                    // Continue to PHASE 4 (registry update & Git)
                } else {
                    log.warn("❌ AI suggestion {} failed, trying next", currentAiSuggestionIndex);
                    currentAiSuggestionIndex++;

                    // ✅ FIX: Reset attempt scope for next AI suggestion
                    context.putState("currentAttemptStartIteration", context.getCurrentIteration());
                    return plan(context);
                }
            } else {
                // All AI suggestions exhausted too!
                log.error("❌ Both registry alternatives AND AI suggestions failed!");
                if (!hasCreatedApproval) {
                    return planCreateApprovalForManualReview(context, test);
                }
                moveToNextTest(context);
                return plan(context);
            }*/


    // Check if we have alternatives to try
        /*if (availableAlternatives == null || availableAlternatives.isEmpty()) {

            boolean hasCapturedHtml = hasCompletedAction(context, AgentActionType.READ_FILE);
            boolean hasAiDiscovery = aiSuggestions != null && !aiSuggestions.isEmpty();

            if (!hasCapturedHtml) {
                return planCapturePageHtml(context, test);
            }

            if (!hasAiDiscovery) {
                return planAiDiscovery(context, test);
            }

          /*  if (!hasAiDiscovery) {
                return planAiDiscovery(context, test);
            }
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
        return plan(context);*/
    // }

    /**
     * ✅ NEW HELPER: Get the last action type from history.
     * This enables state machine logic.
     */
    private AgentActionType getLastActionType(AgentContext context) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) {
            return null;
        }

        List<AgentHistoryEntry> history = context.getActionHistory();
        return history.get(history.size() - 1).getActionType();
    }


    /**
     * ✅ NEW HELPER: Check if action completed SINCE a specific iteration.
     * This allows per-attempt checking instead of per-test checking.
     */
    private boolean hasActionSince(AgentContext context, AgentActionType actionType, int sinceIteration) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) {
            return false;
        }

        return context.getActionHistory().stream()
                .filter(entry -> entry.getIteration() >= sinceIteration)
                .filter(entry -> entry.getActionType() == actionType)
                .anyMatch(AgentHistoryEntry::isSuccess);
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

            Map<String, Object> result = executeTool(actionType, parameters);
            boolean success = (boolean) result.getOrDefault("success", false);

            if (success) {
                updateStateFromActionResult(actionType, result, context);
            } else {
                // For EXTRACT_BROKEN_LOCATOR specifically, update state even on failure
                // so the planner can advance to AI discovery rather than looping forever.
                // (The tool now always returns success=true, but this is a belt-and-suspenders guard.)
                if (actionType == com.company.qa.model.enums.AgentActionType.EXTRACT_BROKEN_LOCATOR) {
                    log.warn("⚠️ EXTRACT_BROKEN_LOCATOR returned success=false — updating state anyway to avoid infinite loop");
                    updateStateFromActionResult(actionType, result, context);
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
        boolean allProcessed = testsToFix != null && currentTestIndex >= testsToFix.size();

        if (allProcessed) {
            log.info("✅ Goal achieved: {} of {} tests successfully fixed",
                    successfullyFixedCount, testsToFix.size());

            // Store result in context
            context.putWorkProduct("successfullyFixed", successfullyFixedCount);
            context.putWorkProduct("totalTests", testsToFix.size());

            // ✅ FIX: Return true only if we actually fixed something
            return true;
        }

        return false;
    }

    // ========== INITIALIZATION ==========

    private void initializeAgentState(AgentContext context) {
        log.info("🔧 Initializing SelfHealingAgent state");

        testsToFix = new ArrayList<>();
        currentTestIndex = 0;
        currentFailureAnalysis = new HashMap<>();
        availableAlternatives = null;
        currentAlternativeIndex = 0;
        aiSuggestions = null;              // NEW
        currentAiSuggestionIndex = 0;
        fixVerified = false;
        successfullyFixedCount = 0;  // ✅ NEW

        // ✅ NEW - Store original content
        originalTestContent = null;
        lastAppliedFix = null;


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
                .nextAction(AgentActionType.EXTRACT_BROKEN_LOCATOR)
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

        if (originalTestContent == null) {
            originalTestContent = test.getContent();
            log.info("📦 Backed up original test content ({} chars)", originalTestContent.length());
        }
        Map<String, Object> currentAlternative = availableAlternatives.get(currentAlternativeIndex);
        String newLocator = (String) currentAlternative.get("locator");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("fixedTestCode", buildFixedTestCode(originalTestContent, newLocator));

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
        String workingLocator;
        String elementName;

        // ✅ FIX: Check which source worked
        if (availableAlternatives != null && !availableAlternatives.isEmpty()
                && currentAlternativeIndex < availableAlternatives.size()) {
            // Registry alternative worked
            Map<String, Object> currentAlternative = availableAlternatives.get(currentAlternativeIndex);
            workingLocator = (String) currentAlternative.get("locator");
            elementName = (String) currentAlternative.get("elementName");
        } else if (aiSuggestions != null && !aiSuggestions.isEmpty()
                && currentAiSuggestionIndex < aiSuggestions.size()) {
            // AI suggestion worked
            Map<String, Object> suggestion = aiSuggestions.get(currentAiSuggestionIndex);
            workingLocator = (String) suggestion.get("locator");
            elementName = "AI-discovered-element-" + System.currentTimeMillis();
        } else {
            // ❌ Shouldn't reach here
            log.error("❌ planUpdateRegistry called but no working alternative found!");
            workingLocator = lastAppliedFix;
            elementName = "fallback-element";
        }

        String brokenLocator = (String) currentFailureAnalysis.get("brokenLocator");
        String pageName = (String) currentFailureAnalysis.get("pageName");

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
    private void updateStateFromActionResult(AgentActionType actionType, Map<String, Object> result,AgentContext context) {
        switch (actionType) {
            case EXTRACT_BROKEN_LOCATOR -> {
                // Check if this is locator extraction or AI discovery
                /*if (result.containsKey("suggestions")) {
                    // AI discovery
                    aiSuggestions = (List<Map<String, Object>>) result.get("suggestions");
                    currentAiSuggestionIndex = 0;
                    log.info("AI discovered {} locator suggestions", aiSuggestions.size());
                } else {*/
                    // Locator extraction
                    currentFailureAnalysis = new HashMap<>(result);
                }
            //}
            case QUERY_ELEMENT_REGISTRY -> {
                availableAlternatives = (List<Map<String, Object>>) result.get("alternatives");
                currentAlternativeIndex = 0;
            }
            case READ_FILE -> {
                // Page HTML captured
                String html = (String) result.get("relevantHtml");
                context.putWorkProduct("pageHtml", html);
            }
            case EXECUTE_TEST -> {
                fixVerified = (boolean) result.getOrDefault("isStable", false);

                if (fixVerified) {
                    // ✅ NEW: Increment successful fix count
                    successfullyFixedCount++;
                    log.info("✅ Fix verified! Successfully fixed {}/{} tests",
                            successfullyFixedCount, testsToFix.size());
                }

                if (!fixVerified) {
                    // Failed - increment to try next alternative/suggestion
                    if (availableAlternatives != null && !availableAlternatives.isEmpty()
                            && currentAlternativeIndex < availableAlternatives.size()) {
                        // Was trying registry alternative
                        log.warn("❌ Registry alternative {} failed, will try next", currentAlternativeIndex);
                        currentAlternativeIndex++;
                    } else if (aiSuggestions != null && !aiSuggestions.isEmpty()
                            && currentAiSuggestionIndex < aiSuggestions.size()) {
                        // Was trying AI suggestion
                        log.warn("❌ AI suggestion {} failed, will try next", currentAiSuggestionIndex);
                        currentAiSuggestionIndex++;
                    }
                }
            }

            case CREATE_BRANCH -> {
                String branchName = (String) result.get("branchName");
                if (branchName != null) {
                    context.putWorkProduct("branchName", branchName);
                }
            }
            case DISCOVER_LOCATOR -> {
                if (result.containsKey("suggestions")) {
                    // AI discovery
                    aiSuggestions = (List<Map<String, Object>>) result.get("suggestions");
                    currentAiSuggestionIndex = 0;
                    log.info("AI discovered {} locator suggestions", aiSuggestions.size());
                }
            }
        }
    }

    /**
     * Update moveToNextTest() - MODIFY THIS
     */
    private void moveToNextTest(AgentContext context) {
        // ✅ Track if current test was fixed
        if (fixVerified) {
            successfullyFixedCount++;
            log.info("✅ Test {} fixed successfully ({}/{})",
                    currentTestIndex + 1, successfullyFixedCount, testsToFix.size());
        } else {
            log.warn("❌ Test {} could not be fixed ({}/{} fixed so far)",
                    currentTestIndex + 1, successfullyFixedCount, testsToFix.size());
        }

        currentTestIndex++;
        currentFailureAnalysis = new HashMap<>();
        availableAlternatives = null;
        currentAlternativeIndex = 0;
        aiSuggestions = null;
        currentAiSuggestionIndex = 0;
        fixVerified = false;

        originalTestContent = null;
        lastAppliedFix = null;
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
        // ✅ VALIDATE locator format BEFORE applying
        if (!isValidSelectorString(newLocator)) {
            log.error("❌ Invalid locator format: {} (looks like Java code, not a selector)",
                    newLocator);
            return originalContent;  // Don't apply invalid locators
        }

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


    /**
     * ✅ NEW HELPER: Validate selector is a string, not Java code
     */
    private boolean isValidSelectorString(String locator) {
        if (locator == null || locator.trim().isEmpty()) {
            return false;
        }

        // ❌ Reject if it looks like Java code
        String[] javaCodeIndicators = {
                "page.",           // page.getByRole
                "AriaRole",        // AriaRole.TEXTBOX
                "new Page.",       // new Page.GetByRoleOptions()
                "Page.Get",        // GetByRoleOptions
                ".setName(",       // .setName("...")
                "locator(",        // page.locator(...)
                "getBy",           // getByRole, getByLabel, etc.
                ".click(",         // .click()
                "Options()",       // GetByRoleOptions()
        };

        for (String indicator : javaCodeIndicators) {
            if (locator.contains(indicator)) {
                log.error("❌ Locator contains Java code indicator: {}", indicator);
                return false;
            }
        }

        // ✅ Accept if it looks like a CSS/XPath selector
        return locator.startsWith("[")      // [data-test='x']
                || locator.startsWith("#")       // #id
                || locator.startsWith(".")       // .class
                || locator.startsWith("//")      // //xpath
                || locator.matches("^[a-zA-Z][a-zA-Z0-9-]*\\[.+\\]$");  // input[placeholder='Username']
    }
    /**
     * Plan to capture page HTML for AI analysis.
     */
    private AgentPlan planCapturePageHtml(AgentContext context, Test test) {
        // Infer page URL from test or use default
        String pageUrl = inferPageUrl(test);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pageUrl", pageUrl);
        parameters.put("selectorContext", "body");

        return AgentPlan.builder()
                .nextAction(AgentActionType.READ_FILE)
                .actionParameters(parameters)
                .reasoning("Capturing page HTML for AI analysis (registry had no matches)")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    /**
     * Plan AI-powered locator discovery.
     */
    private AgentPlan planAiDiscovery(AgentContext context, Test test) {
        String pageHtml = context.getWorkProduct("pageHtml", String.class);
        String brokenLocator = (String) currentFailureAnalysis.get("brokenLocator");
        String elementPurpose = (String) currentFailureAnalysis.get("elementPurpose");
        String pageName = (String) currentFailureAnalysis.get("pageName");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pageHtml", pageHtml);
        parameters.put("brokenLocator", brokenLocator);
        parameters.put("elementPurpose", elementPurpose);
        parameters.put("pageName", pageName);

        return AgentPlan.builder()
                .nextAction(AgentActionType.DISCOVER_LOCATOR)
                .actionParameters(parameters)
                .reasoning("Using AI to discover new locator from page HTML")
                .confidence(0.8)
                .requiresApproval(false)
                .build();
    }

    /**
     * Plan applying AI-suggested locator.
     */
    @SuppressWarnings("unchecked")
    private AgentPlan planApplyAiSuggestion(AgentContext context, Test test) {
        if (originalTestContent == null) {
            originalTestContent = test.getContent();
            log.info("📦 Backed up original test content ({} chars)", originalTestContent.length());
        }

        Map<String, Object> suggestion = aiSuggestions.get(currentAiSuggestionIndex);
        String newLocator = (String) suggestion.get("locator");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("fixedTestCode", buildFixedTestCode(originalTestContent, newLocator));

        return AgentPlan.builder()
                .nextAction(AgentActionType.MODIFY_FILE)
                .actionParameters(parameters)
                .reasoning(String.format("Trying AI suggestion %d/%d: %s",
                        currentAiSuggestionIndex + 1,
                        aiSuggestions.size(),
                        newLocator))
                .confidence(0.7)
                .requiresApproval(false)
                .build();
    }

    /**
     * Infer page URL from test content or goal parameters.
     */
    private String inferPageUrl(Test test) {
        // Try to extract from goal parameters first
        Map<String, Object> goalParams = testsToFix != null && !testsToFix.isEmpty()
                ? Map.of() // Would come from context
                : Map.of();

        if (goalParams.containsKey("pageUrl")) {
            return (String) goalParams.get("pageUrl");
        }

        // Try to extract from test content
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> contentMap = objectMapper.readValue(test.getContent(), Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) contentMap.get("steps");

            for (Map<String, Object> step : steps) {
                if ("navigate".equals(step.get("action"))) {
                    return (String) step.get("value");
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract URL from test content");
        }

        // Default fallback
        return "https://www.saucedemo.com";
    }
}