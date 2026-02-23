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
import com.company.qa.service.playwright.PlaywrightJavaRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.company.qa.service.draft.DraftFileService;

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
 * THREAD SAFETY: This agent is a Spring singleton. All per-execution state is stored
 * in AgentContext.state (persisted to Redis), never in instance fields.
 * Concurrent executions each have their own AgentContext and are fully isolated.
 *
 * @author QA Framework
 * @since Week 16 Day 3 / Phase 1 refactor
 */
@Slf4j
@Component
public class SelfHealingAgent extends BaseAgent {

    // ── Stateless dependencies (safe as instance fields) ──────────────────────
    private final TestRepository testRepository;
    private final ObjectMapper objectMapper;
    private final AgentOrchestrator orchestrator;

    private final DraftFileService draftFileService;
    private final PlaywrightJavaRenderer playwrightJavaRenderer;
    // ── NO instance state fields ───────────────────────────────────────────────
    // All per-execution state lives in AgentContext.state via State keys below.

    public SelfHealingAgent(
            AIGatewayService aiGateway,
            AuditLogService auditService,
            ApprovalRequestService approvalService,
            AIBudgetService budgetService,
            AgentMemoryService memoryService,
            AgentToolRegistry toolRegistry,
            TestRepository testRepository,
            ObjectMapper objectMapper,
            AgentOrchestrator orchestrator, DraftFileService draftFileService, PlaywrightJavaRenderer playwrightJavaRenderer) {

        super(aiGateway, auditService, approvalService, budgetService, memoryService, toolRegistry);
        this.testRepository = testRepository;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
        this.draftFileService = draftFileService;
        this.playwrightJavaRenderer = playwrightJavaRenderer;
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

    // =========================================================================
    // PLAN
    // =========================================================================

    @Override
    protected AgentPlan plan(AgentContext context) {

        // Initialize on very first call
        if (getTestIds(context).isEmpty() && context.getCurrentIteration() == 0) {
            initializeAgentState(context);
        }

        List<String> testIds                       = getTestIds(context);
        int currentTestIndex                       = getTestIndex(context);
        List<Map<String, Object>> alternatives     = getAlternatives(context);
        List<Map<String, Object>> aiSuggestions    = getAiSuggestions(context);
        int currentAltIndex                        = getAltIndex(context);
        int currentAiIdx                           = getAiSuggestionIdx(context);

        log.info("📋 Planning - Test {}/{}, Registry Alt {}/{}, AI Alt {}/{}",
                currentTestIndex, testIds.size(),
                currentAltIndex,  alternatives != null ? alternatives.size() : 0,
                currentAiIdx,     aiSuggestions != null ? aiSuggestions.size() : 0);

        if (currentTestIndex >= testIds.size()) {
            return planComplete(context);
        }

        UUID testId = UUID.fromString(testIds.get(currentTestIndex));
        Test test   = testRepository.findById(testId).orElseThrow();

        AgentActionType lastAction = getLastActionType(context);

        // Git workflow flags
        boolean hasUpdatedRegistry = hasCompletedAction(context, AgentActionType.UPDATE_ELEMENT_REGISTRY);
        boolean hasCreatedBranch   = hasCompletedAction(context, AgentActionType.CREATE_BRANCH);
        boolean hasCommitted       = hasCompletedAction(context, AgentActionType.COMMIT_CHANGES);
        boolean hasCreatedApproval = hasCompletedAction(context, AgentActionType.REQUEST_APPROVAL);
        boolean hasCreatedPR       = hasCompletedAction(context, AgentActionType.CREATE_PULL_REQUEST);

        // ── STEP 1: Extract broken locator ────────────────────────────────────
        if (getFailureAnalysis(context).isEmpty()) {
            return planExtractBrokenLocator(context, test);
        }

        // ── STEP 2: Query registry ────────────────────────────────────────────
        if (alternatives == null) {
            return planQueryRegistry(context, test);
        }

        // ── PHASE 3A: Try registry alternatives ───────────────────────────────
        if (!alternatives.isEmpty() && currentAltIndex < alternatives.size()) {
            if (lastAction == null
                    || lastAction == AgentActionType.QUERY_ELEMENT_REGISTRY
                    || lastAction == AgentActionType.EXECUTE_TEST) {
                return planApplyAlternative(context, test);
            }
            if (lastAction == AgentActionType.MODIFY_FILE) {
                return planVerifyFix(context, test);
            }
        }

        // ── PHASE 3B: AI discovery fallback ───────────────────────────────────
        boolean registryExhausted = alternatives.isEmpty() || currentAltIndex >= alternatives.size();

        if (registryExhausted && !isFixVerified(context)) {

            if (!hasCompletedAction(context, AgentActionType.READ_FILE)) {
                return planCapturePageHtml(context, test);
            }

            if (aiSuggestions == null) {
                return planAiDiscovery(context, test);
            }

            if (aiSuggestions.isEmpty()) {
                log.warn("❌ AI discovery returned no suggestions");
                if (!hasCreatedApproval) return planCreateApprovalForManualReview(context, test);
                moveToNextTest(context);
                return plan(context);
            }

            if (currentAiIdx < aiSuggestions.size()) {
                if (lastAction == null
                        || lastAction == AgentActionType.DISCOVER_LOCATOR
                        || lastAction == AgentActionType.EXECUTE_TEST) {
                    return planApplyAiSuggestion(context, test);
                }
                if (lastAction == AgentActionType.MODIFY_FILE) {
                    return planVerifyFix(context, test);
                }
            } else {
                log.error("❌ Both registry alternatives AND AI suggestions failed!");
                if (!hasCreatedApproval) return planCreateApprovalForManualReview(context, test);
                moveToNextTest(context);
                return plan(context);
            }
        }

        // ── PHASE 4: Git workflow (only reached when fixVerified == true) ─────
        if (!isFixVerified(context)) {
            log.error("❌ Reached Phase 4 but fixVerified=false — should not happen!");
            if (!hasCreatedApproval) return planCreateApprovalForManualReview(context, test);
            moveToNextTest(context);
            return plan(context);
        }

        if (!hasUpdatedRegistry) return planUpdateRegistry(context, test);
        boolean hasRenderedFile = Boolean.TRUE.equals(
                context.getState(State.HAS_RENDERED_FILE, Boolean.class));
        if (!hasRenderedFile)                  return planRenderAndWriteFile(context, test);
        if (!hasCreatedBranch)   return planCreateBranch(context, test);
        if (!hasCommitted)       return planCommitChanges(context, test);
        if (!hasCreatedApproval) return planCreateApproval(context, test);
        if (!hasCreatedPR)       return planCreatePullRequest(context, test);

        moveToNextTest(context);
        return plan(context);
    }

    /**
     * Render the fixed intent JSON to a .java file and save it to the drafts folder.
     * This must happen before CREATE_BRANCH so the commit tool has a real file to reference.
     */
    private AgentPlan planRenderAndWriteFile(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId",       test.getId().toString());
        parameters.put("testClassName",     test.getName());
        parameters.put("testCode",  test.getContent()); // fixed intent JSON from DB

        return AgentPlan.builder()
                .nextAction(AgentActionType.WRITE_FILE)
                .actionParameters(parameters)
                .reasoning("Rendering fixed intent JSON to .java draft file before Git commit")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    // =========================================================================
    // EXECUTE ACTION
    // =========================================================================

    @Override
    protected ActionResult executeAction(
            AgentActionType actionType,
            Map<String, Object> parameters,
            AgentContext context) {

        try {
            log.info("🔧 Executing action: {}", actionType);

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
            } else if (actionType == AgentActionType.EXTRACT_BROKEN_LOCATOR) {
                // Always advance state for this tool so the planner doesn't loop
                log.warn("⚠️ EXTRACT_BROKEN_LOCATOR returned success=false — updating state anyway");
                updateStateFromActionResult(actionType, result, context);
            } else if (actionType == AgentActionType.DISCOVER_LOCATOR) {
                // FIX 3: DISCOVER_LOCATOR returned success=false (should be rare after Fix 1+2,
                // but guard here as a safety net). Force aiSuggestions=[] so the planner
                // hits the aiSuggestions.isEmpty() branch and escalates to manual review
                // instead of looping forever on planAiDiscovery() (aiSuggestions==null → retry).
                log.warn("⚠️ DISCOVER_LOCATOR failed — forcing aiSuggestions=[] to break retry loop");
                setAiSuggestions(context, List.of());
                setAiSuggestionIdx(context, 0);
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
        return actionType == AgentActionType.COMPLETE
                || actionType == AgentActionType.INITIALIZE
                || actionType == AgentActionType.FINALIZE
                || actionType == AgentActionType.ABORT;
    }

    // =========================================================================
    // GOAL CHECK
    // =========================================================================

    @Override
    protected boolean isGoalAchieved(AgentContext context) {
        List<String> ids    = getTestIds(context);
        boolean allProcessed = !ids.isEmpty() && getTestIndex(context) >= ids.size();

        if (allProcessed) {
            int fixed = getSuccessfullyFixed(context);
            log.info("✅ Goal achieved: {}/{} tests successfully fixed", fixed, ids.size());
            context.putWorkProduct("successfullyFixed", fixed);
            context.putWorkProduct("totalTests", ids.size());
        }

        return allProcessed;
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    private void initializeAgentState(AgentContext context) {
        log.info("🔧 Initializing SelfHealingAgent state");

        List<String> testIds = new ArrayList<>();
        Map<String, Object> goalParams = context.getGoal().getParameters();

        if (goalParams.containsKey("testId")) {
            testIds.add((String) goalParams.get("testId"));
            log.info("Fixing specific test: {}", goalParams.get("testId"));
        } else {
            log.info("No test ID provided in goal parameters");
        }

        String errorMessage = (String) goalParams.get("errorMessage");
        if ((errorMessage == null || errorMessage.isBlank()) && !testIds.isEmpty()) {
            try {
                Test test = testRepository.findById(UUID.fromString(testIds.get(0))).orElse(null);
                if (test != null && test.getLastExecutionError() != null) {
                    errorMessage = test.getLastExecutionError();
                    log.info("Loaded lastExecutionError from DB for test {} — length: {}",
                            testIds.get(0), errorMessage.length());
                }
            } catch (Exception e) {
                log.warn("Could not load lastExecutionError from DB: {}", e.getMessage());
            }
        }

        context.putState(State.TESTS_TO_FIX,             testIds);
        context.putState(State.CURRENT_TEST_INDEX,        0);
        context.putState("errorMessage",                  errorMessage);  // available to planExtractBrokenLocator
        context.putState(State.CURRENT_FAILURE_ANALYSIS,  new HashMap<>());
        context.putState(State.AVAILABLE_ALTERNATIVES,    null);
        context.putState(State.CURRENT_ALT_INDEX,         0);
        context.putState(State.AI_SUGGESTIONS,            null);
        context.putState(State.CURRENT_AI_SUGGESTION_IDX, 0);
        context.putState(State.FIX_VERIFIED,              false);
        context.putState(State.ORIGINAL_CONTENT,          null);
        context.putState(State.LAST_APPLIED_FIX,          null);
        context.putState(State.SUCCESSFULLY_FIXED_COUNT,  0);
    }

    // =========================================================================
    // PLANNING METHODS
    // =========================================================================

    private AgentPlan planExtractBrokenLocator(AgentContext context, Test test) {
        String errorMessage = context.getState("errorMessage", String.class);
        if (errorMessage == null) errorMessage = "Element not found";

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("errorMessage", errorMessage);
        parameters.put("testContent",  test.getContent());

        return AgentPlan.builder()
                .nextAction(AgentActionType.EXTRACT_BROKEN_LOCATOR)
                .actionParameters(parameters)
                .reasoning("Extracting broken locator from test failure")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planQueryRegistry(AgentContext context, Test test) {
        Map<String, Object> analysis = getFailureAnalysis(context);
        String pageName       = (String) analysis.getOrDefault("pageName", "unknown");
        String elementPurpose = (String) analysis.getOrDefault("elementPurpose", "unknown");
        String brokenLocator  = (String) analysis.get("brokenLocator");

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
        String original = getOriginalContent(context);
        if (original == null) {
            original = test.getContent();
            setOriginalContent(context, original);
            log.info("📦 Backed up original test content ({} chars)", original.length());
        }

        List<Map<String, Object>> alternatives = getAlternatives(context);
        Map<String, Object> currentAlternative = alternatives.get(getAltIndex(context));
        String newLocator = (String) currentAlternative.get("locator");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("fixedTestCode", buildFixedTestCode(original, newLocator, context));

        return AgentPlan.builder()
                .nextAction(AgentActionType.MODIFY_FILE)
                .actionParameters(parameters)
                .reasoning(String.format("Trying registry alternative %d/%d: %s",
                        getAltIndex(context) + 1, alternatives.size(), newLocator))
                .confidence(0.7)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planVerifyFix(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("runCount", 3);

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
        List<Map<String, Object>> alternatives = getAlternatives(context);
        List<Map<String, Object>> aiSuggestions = getAiSuggestions(context);
        int altIndex = getAltIndex(context);
        int aiIdx    = getAiSuggestionIdx(context);

        String workingLocator;
        String elementName;

        if (alternatives != null && !alternatives.isEmpty() && altIndex < alternatives.size()) {
            Map<String, Object> alt = alternatives.get(altIndex);
            workingLocator = (String) alt.get("locator");
            elementName    = (String) alt.get("elementName");
        } else if (aiSuggestions != null && !aiSuggestions.isEmpty() && aiIdx < aiSuggestions.size()) {
            Map<String, Object> suggestion = aiSuggestions.get(aiIdx);
            workingLocator = (String) suggestion.get("locator");
            elementName    = "AI-discovered-element-" + System.currentTimeMillis();
        } else {
            log.error("❌ planUpdateRegistry called but no working alternative found!");
            workingLocator = getLastAppliedFix(context);
            elementName    = "fallback-element";
        }

        Map<String, Object> analysis = getFailureAnalysis(context);
        String brokenLocator = (String) analysis.get("brokenLocator");
        String pageName      = (String) analysis.get("pageName");

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

        String renderedFilePath = context.getState(State.RENDERED_FILE_PATH, String.class);
        if (renderedFilePath == null) {
            log.warn("⚠️ No rendered file path in state — using fallback path");
            renderedFilePath = "drafts/" + test.getName() + ".java";
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey",      "LOCATOR-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName",    branchName);
        parameters.put("commitMessage", "Fix broken locator in test: " + test.getName());
        parameters.put("filePaths",     List.of(renderedFilePath));

        return AgentPlan.builder()
                .nextAction(AgentActionType.COMMIT_CHANGES)
                .actionParameters(parameters)
                .reasoning("Committing rendered .java file with locator fix")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApproval(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testCode",    test.getContent());
        parameters.put("jiraKey",     "LOCATOR-" + test.getId().toString().substring(0, 8));
        parameters.put("testName",    test.getName());
        parameters.put("requestedBy", "SelfHealingAgent");
        parameters.put("requestType", "SELF_HEALING_FIX");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("Locator fix verified — creating approval request for file promotion")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApprovalForManualReview(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testCode",    test.getContent());
        parameters.put("jiraKey",     "LOCATOR-MANUAL-" + test.getId().toString().substring(0, 8));
        parameters.put("testName",    test.getName() + " [NEEDS MANUAL REVIEW]");
        parameters.put("requestedBy", "SelfHealingAgent");
        parameters.put("requestType", "SELF_HEALING_MANUAL");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("No working alternatives found — flagging for manual review")
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

    private AgentPlan planCapturePageHtml(AgentContext context, Test test) {
        String pageUrl = inferPageUrl(context, test);

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

    private AgentPlan planAiDiscovery(AgentContext context, Test test) {
        Map<String, Object> analysis = getFailureAnalysis(context);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pageHtml",        context.getWorkProduct("pageHtml", String.class));
        parameters.put("brokenLocator",   analysis.get("brokenLocator"));
        parameters.put("elementPurpose",  analysis.get("elementPurpose"));
        parameters.put("pageName",        analysis.get("pageName"));
        parameters.put("actionType",      analysis.get("actionType"));
        String failedStepLocator = context.getWorkProduct("failedStepLocator", String.class);
        if (failedStepLocator != null) {
            parameters.put("originalLocator", failedStepLocator);
        }
        return AgentPlan.builder()
                .nextAction(AgentActionType.DISCOVER_LOCATOR)
                .actionParameters(parameters)
                .reasoning("Using AI to discover new locator from page HTML")
                .confidence(0.8)
                .requiresApproval(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private AgentPlan planApplyAiSuggestion(AgentContext context, Test test) {
        String original = getOriginalContent(context);
        if (original == null) {
            original = test.getContent();
            setOriginalContent(context, original);
            log.info("📦 Backed up original test content ({} chars)", original.length());
        }

        List<Map<String, Object>> aiSuggestions = getAiSuggestions(context);
        int aiIdx = getAiSuggestionIdx(context);
        Map<String, Object> suggestion = aiSuggestions.get(aiIdx);
        String newLocator = (String) suggestion.get("locator");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("fixedTestCode", buildFixedTestCode(original, newLocator, context));

        return AgentPlan.builder()
                .nextAction(AgentActionType.MODIFY_FILE)
                .actionParameters(parameters)
                .reasoning(String.format("Trying AI suggestion %d/%d: %s",
                        aiIdx + 1, aiSuggestions.size(), newLocator))
                .confidence(0.7)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planComplete(AgentContext context) {
        return AgentPlan.builder()
                .nextAction(AgentActionType.COMPLETE)
                .actionParameters(Map.of("totalFixed", getTestIds(context).size()))
                .reasoning("All tests processed")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    // =========================================================================
    // STATE MANAGEMENT
    // =========================================================================

    @SuppressWarnings("unchecked")
    private void updateStateFromActionResult(AgentActionType actionType,
                                             Map<String, Object> result,
                                             AgentContext context) {
        switch (actionType) {

            case EXTRACT_BROKEN_LOCATOR ->
                    setFailureAnalysis(context, new HashMap<>(result));

            case QUERY_ELEMENT_REGISTRY -> {
                setAlternatives(context, (List<Map<String, Object>>) result.get("alternatives"));
                setAltIndex(context, 0);
            }

            case READ_FILE ->
                    context.putWorkProduct("pageHtml", result.get("relevantHtml"));

            case DISCOVER_LOCATOR -> {
                if (result.containsKey("suggestions")) {
                    List<Map<String, Object>> suggestions =
                            (List<Map<String, Object>>) result.get("suggestions");
                    setAiSuggestions(context, suggestions);
                    setAiSuggestionIdx(context, 0);
                    log.info("AI discovered {} locator suggestions", suggestions.size());
                }
            }
            case WRITE_FILE -> {
                String filePath = (String) result.get("filePath");
                if (filePath != null) {
                    context.putState(State.RENDERED_FILE_PATH, filePath);
                    context.putState(State.HAS_RENDERED_FILE, true);
                    log.info("✅ Rendered .java file saved to: {}", filePath);
                } else {
                    log.error("❌ WRITE_FILE succeeded but returned no filePath");
                    context.putState(State.HAS_RENDERED_FILE, true);
                }
            }

            case EXECUTE_TEST -> {
                boolean stable = (boolean) result.getOrDefault("isStable", false);
                setFixVerified(context, stable);

                Object failedIdx = result.get("failedStepIndex");
                if (failedIdx != null) {
                    context.putWorkProduct("failedStepIndex", failedIdx);
                }
                String failedLocator = (String) result.get("failedStepLocator");
                if (failedLocator != null) {
                    context.putWorkProduct("failedStepLocator", failedLocator);
                }
                String failedError = (String) result.get("failedErrorMessage");
                if (failedError != null) {
                    context.putWorkProduct("failedErrorMessage", failedError);
                }

                if (stable) {
                    setSuccessfullyFixed(context, getSuccessfullyFixed(context) + 1);
                    log.info("✅ Fix verified! Successfully fixed {}/{} tests",
                            getSuccessfullyFixed(context), getTestIds(context).size());
                } else {
                    List<Map<String, Object>> alts   = getAlternatives(context);
                    List<Map<String, Object>> aiSugg = getAiSuggestions(context);

                    if (alts != null && !alts.isEmpty() && getAltIndex(context) < alts.size()) {
                        log.warn("❌ Registry alternative {} failed, trying next", getAltIndex(context));
                        setAltIndex(context, getAltIndex(context) + 1);
                    } else if (aiSugg != null && !aiSugg.isEmpty()
                            && getAiSuggestionIdx(context) < aiSugg.size()) {
                        log.warn("❌ AI suggestion {} failed, trying next", getAiSuggestionIdx(context));
                        setAiSuggestionIdx(context, getAiSuggestionIdx(context) + 1);
                    }
                }
            }

            case CREATE_BRANCH -> {
                String branchName = (String) result.get("branchName");
                if (branchName != null) context.putWorkProduct("branchName", branchName);
            }
        }
    }

    private void moveToNextTest(AgentContext context) {

        if (!isFixVerified(context) && getOriginalContent(context) != null
                && getTestIndex(context) < getTestIds(context).size()) {
            UUID testId = UUID.fromString(getTestIds(context).get(getTestIndex(context)));
            testRepository.findById(testId).ifPresent(test -> {
                test.setContent(getOriginalContent(context));
                testRepository.save(test);
                log.info("🔄 Rolled back test content to original for: {}", test.getName());
            });
        }
        if (isFixVerified(context)) {
            setSuccessfullyFixed(context, getSuccessfullyFixed(context) + 1);
            log.info("✅ Test {} fixed ({}/{} total)",
                    getTestIndex(context) + 1,
                    getSuccessfullyFixed(context),
                    getTestIds(context).size());
        } else {
            log.warn("❌ Test {} could not be fixed ({}/{} fixed so far)",
                    getTestIndex(context) + 1,
                    getSuccessfullyFixed(context),
                    getTestIds(context).size());
        }

        setTestIndex(context, getTestIndex(context) + 1);
        setFailureAnalysis(context, new HashMap<>());
        setAlternatives(context, null);
        setAltIndex(context, 0);
        setAiSuggestions(context, null);
        setAiSuggestionIdx(context, 0);
        setFixVerified(context, false);
        setOriginalContent(context, null);
        setLastAppliedFix(context, null);
        context.putState(State.RENDERED_FILE_PATH, null);
        context.putState(State.HAS_RENDERED_FILE, false);
        context.putState("currentTestStartIteration", context.getCurrentIteration());
    }

    private boolean hasCompletedAction(AgentContext context, AgentActionType actionType) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) {
            return false;
        }
        Integer testStartIteration = context.getState("currentTestStartIteration", Integer.class);
        if (testStartIteration == null) testStartIteration = 0;

        final int startIter = testStartIteration;
        return context.getActionHistory().stream()
                .filter(entry -> entry.getIteration() >= startIter)
                .filter(entry -> entry.getActionType() == actionType)
                .anyMatch(AgentHistoryEntry::isSuccess);
    }

    private AgentActionType getLastActionType(AgentContext context) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) return null;
        List<AgentHistoryEntry> history = context.getActionHistory();
        return history.get(history.size() - 1).getActionType();
    }

    private boolean hasActionSince(AgentContext context, AgentActionType actionType, int sinceIteration) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) return false;
        return context.getActionHistory().stream()
                .filter(e -> e.getIteration() >= sinceIteration)
                .filter(e -> e.getActionType() == actionType)
                .anyMatch(AgentHistoryEntry::isSuccess);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    @SuppressWarnings("unchecked")
    private String buildFixedTestCode(String originalContent, String newLocator,
                                      AgentContext context) {
        if (!isValidSelectorString(newLocator)) {
            log.error("❌ Invalid locator format: {} (looks like Java code)", newLocator);
            return originalContent;
        }

        String brokenLocator = (String) getFailureAnalysis(context).get("brokenLocator");
        if (brokenLocator == null || brokenLocator.isBlank() || "UNKNOWN".equals(brokenLocator)) {
            log.warn("⚠️ No brokenLocator in failure analysis — cannot replace");
            return originalContent;
        }

        try {
            Map<String, Object> contentMap = objectMapper.readValue(originalContent, Map.class);
            boolean replaced = false;

            List<Map<String, Object>> scenarios =
                    (List<Map<String, Object>>) contentMap.get("scenarios");

            if (scenarios != null) {
                for (Map<String, Object> scenario : scenarios) {
                    List<Map<String, Object>> steps =
                            (List<Map<String, Object>>) scenario.get("steps");
                    if (steps == null) continue;

                    for (Map<String, Object> step : steps) {
                        String locator = (String) step.get("locator");
                        if (locator != null && locatorsMatch(locator, brokenLocator)) {
                            step.put("locator", newLocator);
                            log.info("  ✅ Replaced: {} → {}", brokenLocator, newLocator);
                            replaced = true;
                        }
                    }
                }
            }

            if (!replaced) {
                List<Map<String, Object>> steps =
                        (List<Map<String, Object>>) contentMap.get("steps");
                if (steps != null) {
                    for (Map<String, Object> step : steps) {
                        String locator = (String) step.get("locator");
                        if (locator != null && locatorsMatch(locator, brokenLocator)) {
                            step.put("locator", newLocator);
                            log.info("  ✅ Replaced: {} → {}", brokenLocator, newLocator);
                            replaced = true;
                        }
                    }
                }
            }

            if (!replaced) {
                log.warn("⚠️ brokenLocator '{}' not found in any step — content unchanged", brokenLocator);
                return originalContent;
            }

            return objectMapper.writeValueAsString(contentMap);

        } catch (Exception e) {
            log.error("Failed to build fixed test code: {}", e.getMessage(), e);
            return originalContent;
        }
    }

    private boolean locatorsMatch(String stored, String extracted) {
        if (stored == null || extracted == null) return false;
        if (stored.equals(extracted)) return true;

        String normalizedStored    = stripLocatorPrefix(stored);
        String normalizedExtracted = stripLocatorPrefix(extracted);

        return normalizedStored.equals(normalizedExtracted);
    }

    private String stripLocatorPrefix(String locator) {
        if (locator == null) return "";
        int eq = locator.indexOf('=');
        if (eq > 0 && eq < 12) {
            String prefix = locator.substring(0, eq).toLowerCase();
            Set<String> KNOWN_PREFIXES = Set.of("css", "xpath", "testid", "role",
                    "text", "id", "name", "class", "label", "placeholder");
            if (KNOWN_PREFIXES.contains(prefix)) {
                return locator.substring(eq + 1);
            }
        }
        return locator;
    }

    private boolean isValidSelectorString(String locator) {
        if (locator == null || locator.trim().isEmpty()) return false;

        String[] javaCodeIndicators = {
                "page.", "AriaRole", "new Page.", "Page.Get",
                ".setName(", "locator(", "getBy", ".click(", "Options()"
        };
        for (String indicator : javaCodeIndicators) {
            if (locator.contains(indicator)) {
                log.error("❌ Locator contains Java code indicator: {}", indicator);
                return false;
            }
        }

        return locator.startsWith("[")
                || locator.startsWith("#")
                || locator.startsWith(".")
                || locator.startsWith("//")
                || locator.matches("^[a-zA-Z][a-zA-Z0-9-]*\\[.+\\]$");
    }

    @SuppressWarnings("unchecked")
    private String inferPageUrl(AgentContext context, Test test) {

        Object pageUrlGoal = context.getGoal().getParameters().get("pageUrl");
        if (pageUrlGoal instanceof String s && !s.isBlank()) return s;

        try {
            Map<String, Object> contentMap = objectMapper.readValue(test.getContent(), Map.class);

            List<Map<String, Object>> scenarios =
                    (List<Map<String, Object>>) contentMap.get("scenarios");

            if (scenarios != null && !scenarios.isEmpty()) {
                List<Map<String, Object>> steps =
                        (List<Map<String, Object>>) scenarios.get(0).get("steps");

                if (steps != null && !steps.isEmpty()) {
                    Integer failedIdx = context.getWorkProduct("failedStepIndex", Integer.class);

                    if (failedIdx != null && failedIdx > 0) {
                        for (int i = Math.min(failedIdx, steps.size() - 1); i >= 0; i--) {
                            Map<String, Object> step = steps.get(i);
                            if ("NAVIGATE".equalsIgnoreCase((String) step.get("action"))) {
                                String url = (String) step.get("value");
                                if (url != null && !url.isBlank()) {
                                    log.info("inferPageUrl: NAVIGATE before failing step {} → {}", failedIdx, url);
                                    return url;
                                }
                            }
                        }
                    }

                    for (Map<String, Object> step : steps) {
                        if ("NAVIGATE".equalsIgnoreCase((String) step.get("action"))) {
                            String url = (String) step.get("value");
                            if (url != null && !url.isBlank()) {
                                log.info("inferPageUrl: using first NAVIGATE step → {}", url);
                                return url;
                            }
                        }
                    }
                }
            }

            String baseUrl = (String) contentMap.get("baseUrl");
            if (baseUrl != null && !baseUrl.isBlank()) {
                log.info("inferPageUrl: using INTENT_V1 baseUrl → {}", baseUrl);
                return baseUrl;
            }

            List<Map<String, Object>> legacySteps =
                    (List<Map<String, Object>>) contentMap.get("steps");
            if (legacySteps != null) {
                for (Map<String, Object> step : legacySteps) {
                    if ("navigate".equalsIgnoreCase((String) step.get("action"))) {
                        String url = (String) step.get("value");
                        if (url != null && !url.isBlank()) return url;
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Could not extract URL from test content: {}", e.getMessage());
        }

        return "https://www.saucedemo.com";
    }

    // =========================================================================
    // CONTEXT ACCESSOR HELPERS
    // =========================================================================

    @SuppressWarnings("unchecked")
    private List<String> getTestIds(AgentContext ctx) {
        List<String> ids = ctx.getState(State.TESTS_TO_FIX, List.class);
        return ids != null ? ids : List.of();
    }

    private int getTestIndex(AgentContext ctx) {
        Integer v = ctx.getState(State.CURRENT_TEST_INDEX, Integer.class);
        return v != null ? v : 0;
    }

    private void setTestIndex(AgentContext ctx, int v) {
        ctx.putState(State.CURRENT_TEST_INDEX, v);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFailureAnalysis(AgentContext ctx) {
        Map<String, Object> m = ctx.getState(State.CURRENT_FAILURE_ANALYSIS, Map.class);
        return m != null ? m : new HashMap<>();
    }

    private void setFailureAnalysis(AgentContext ctx, Map<String, Object> m) {
        ctx.putState(State.CURRENT_FAILURE_ANALYSIS, m);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getAlternatives(AgentContext ctx) {
        return ctx.getState(State.AVAILABLE_ALTERNATIVES, List.class);
    }

    private void setAlternatives(AgentContext ctx, List<Map<String, Object>> v) {
        ctx.putState(State.AVAILABLE_ALTERNATIVES, v);
    }

    private int getAltIndex(AgentContext ctx) {
        Integer v = ctx.getState(State.CURRENT_ALT_INDEX, Integer.class);
        return v != null ? v : 0;
    }

    private void setAltIndex(AgentContext ctx, int v) {
        ctx.putState(State.CURRENT_ALT_INDEX, v);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getAiSuggestions(AgentContext ctx) {
        return ctx.getState(State.AI_SUGGESTIONS, List.class);
    }

    private void setAiSuggestions(AgentContext ctx, List<Map<String, Object>> v) {
        ctx.putState(State.AI_SUGGESTIONS, v);
    }

    private int getAiSuggestionIdx(AgentContext ctx) {
        Integer v = ctx.getState(State.CURRENT_AI_SUGGESTION_IDX, Integer.class);
        return v != null ? v : 0;
    }

    private void setAiSuggestionIdx(AgentContext ctx, int v) {
        ctx.putState(State.CURRENT_AI_SUGGESTION_IDX, v);
    }

    private boolean isFixVerified(AgentContext ctx) {
        Boolean v = ctx.getState(State.FIX_VERIFIED, Boolean.class);
        return v != null && v;
    }

    private void setFixVerified(AgentContext ctx, boolean v) {
        ctx.putState(State.FIX_VERIFIED, v);
    }

    private String getOriginalContent(AgentContext ctx) {
        return ctx.getState(State.ORIGINAL_CONTENT, String.class);
    }

    private void setOriginalContent(AgentContext ctx, String v) {
        ctx.putState(State.ORIGINAL_CONTENT, v);
    }

    private String getLastAppliedFix(AgentContext ctx) {
        return ctx.getState(State.LAST_APPLIED_FIX, String.class);
    }

    private void setLastAppliedFix(AgentContext ctx, String v) {
        ctx.putState(State.LAST_APPLIED_FIX, v);
    }

    private int getSuccessfullyFixed(AgentContext ctx) {
        Integer v = ctx.getState(State.SUCCESSFULLY_FIXED_COUNT, Integer.class);
        return v != null ? v : 0;
    }

    private void setSuccessfullyFixed(AgentContext ctx, int v) {
        ctx.putState(State.SUCCESSFULLY_FIXED_COUNT, v);
    }

    // =========================================================================
    // STATE KEY CONSTANTS
    // =========================================================================

    interface State {
        String TESTS_TO_FIX               = "heal.testsToFix";
        String CURRENT_TEST_INDEX         = "heal.currentTestIndex";
        String CURRENT_FAILURE_ANALYSIS   = "heal.currentFailureAnalysis";
        String AVAILABLE_ALTERNATIVES     = "heal.availableAlternatives";
        String CURRENT_ALT_INDEX          = "heal.currentAltIndex";
        String AI_SUGGESTIONS             = "heal.aiSuggestions";
        String CURRENT_AI_SUGGESTION_IDX  = "heal.currentAiSuggestionIdx";
        String FIX_VERIFIED               = "heal.fixVerified";
        String ORIGINAL_CONTENT           = "heal.originalTestContent";
        String LAST_APPLIED_FIX           = "heal.lastAppliedFix";
        String SUCCESSFULLY_FIXED_COUNT   = "heal.successfullyFixedCount";
        String RENDERED_FILE_PATH         = "heal.renderedFilePath";
        String HAS_RENDERED_FILE          = "heal.hasRenderedFile";
    }
}