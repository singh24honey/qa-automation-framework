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
        // Initialize on first call
        if (getTestIds(context).isEmpty() && context.getCurrentIteration() == 0) {
            initializeAgentState(context);
        }

        List<String> testIds = getTestIds(context);
        int currentTestIndex = getTestIndex(context);

        log.info("📋 Planning - Test {}/{}, Fix attempt {}/{}",
                currentTestIndex, testIds.size(),
                getFixAttemptCount(context), config.getMaxFixAttempts());

        AgentActionType lastAction = getLastActionType(context);
        if (lastAction != AgentActionType.REQUEST_APPROVAL) {
            int consecutiveFailures = countConsecutiveFailures(context, lastAction);
            if (consecutiveFailures >= 3) {
                log.error("❌ Action {} failed {} times consecutively - aborting",
                        lastAction, consecutiveFailures);
                return AgentPlan.builder()
                        .nextAction(AgentActionType.REQUEST_APPROVAL)
                        .actionParameters(Map.of(
                                "testName", "[FAILED - NEEDS MANUAL FIX]",
                                "requestedBy", "FlakyTestAgent",
                                "reason", "Action " + lastAction + " failed " + consecutiveFailures + " times"
                        ))
                        .reasoning("Multiple consecutive failures - flagging for manual review")
                        .confidence(1.0)
                        .requiresApproval(false)
                        .build();
            }
        }

        if (currentTestIndex >= testIds.size()) {
            return planComplete(context);
        }

        UUID testId = UUID.fromString(testIds.get(currentTestIndex));
        Test test = testRepository.findById(testId).orElseThrow();

        // AFTER — analysis actions use full-test window, fix actions use fix-attempt window
        boolean hasAnalyzedStability = hasCompletedAction(context, AgentActionType.ANALYZE_TEST_STABILITY, "test");
        boolean hasAnalyzedFailure   = hasCompletedAction(context, AgentActionType.ANALYZE_FAILURE,        "test");
        boolean hasRecordedPattern   = hasCompletedAction(context, AgentActionType.WRITE_FILE,             "test");
        boolean hasGeneratedFix      = hasCompletedAction(context, AgentActionType.SUGGEST_FIX,            "fix");
        boolean hasAppliedFix        = hasCompletedAction(context, AgentActionType.MODIFY_FILE,            "fix");
        boolean hasVerifiedFix       = hasCompletedAction(context, AgentActionType.EXECUTE_TEST,           "fix");
        boolean hasCreatedBranch     = hasCompletedAction(context, AgentActionType.CREATE_BRANCH,             "test");
        boolean hasCommitted         = hasCompletedAction(context, AgentActionType.COMMIT_CHANGES,             "test");
        boolean hasCreatedApproval   = hasCompletedAction(context, AgentActionType.REQUEST_APPROVAL,             "test");
        boolean hasCreatedPR         = hasCompletedAction(context, AgentActionType.CREATE_PULL_REQUEST,             "test");

        // DAY 1
        if (!hasAnalyzedStability) return planAnalyzeStability(context, test);
        if (!hasAnalyzedFailure)   return planAnalyzeFailure(context, test);
        if (!hasRecordedPattern)   return planRecordPattern(context, test);


        String rootCause = (String) getTestAnalysis(context).get("rootCause");

        if ("LOCATOR_BRITTLENESS".equals(rootCause)) {
            log.info("🔀 Root cause LOCATOR_BRITTLENESS on '{}' — delegating to SelfHealingAgent",
                    test.getName());

            // Extract the first error message so SelfHealingAgent knows what broke
            String errorMessage = extractFirstErrorMessage(context);

            AgentGoal healGoal = AgentGoal.builder()
                    .goalType("FIX_BROKEN_LOCATOR")
                    .successCriteria("Fix broken locator in test: " + test.getName())
                    .parameters(Map.of(
                            "testId",         test.getId().toString(),
                            "errorMessage",   errorMessage != null ? errorMessage : "Element not found",
                            "triggeredBy",    "FlakyTestAgent",
                            "sourceTestName", test.getName()
                    ))
                    .build();

            orchestrator.startAgent(
                    AgentType.SELF_HEALING_TEST_FIXER,
                    healGoal,
                    null,            // system-triggered, no user UUID
                    "FlakyTestAgent"
            );

            log.info("✅ SelfHealingAgent started for test: {} (id: {})", test.getName(), test.getId());

            moveToNextTest(context);      // advance FlakyAgent to the next test
            return plan(context);
        }
        // DAY 2
        int fixAttemptCount = getFixAttemptCount(context);
        if (fixAttemptCount < config.getMaxFixAttempts()) {
            if (!hasGeneratedFix) return planGenerateFix(context, test);
            if (!hasAppliedFix)   return planApplyFix(context, test);
            if (!hasVerifiedFix)  return planVerifyFix(context, test);

            if (isFixVerified(context)) {
                log.info("✅ Fix verified! Proceeding to Git workflow");
            } else {
                log.warn("❌ Fix attempt {} failed, trying next fix", fixAttemptCount);
                fixAttemptCount++;
                setFixAttemptCount(context, fixAttemptCount);
                // Advance the fix-phase window — NOT the full test window
                // so hasAnalyzedStability/Failure/RecordedPattern remain true
                context.putState("fixPhaseStartIteration", context.getCurrentIteration());
                return plan(context);
            }
        } else {
            if (!hasCreatedApproval) return planCreateApprovalForManualReview(context, test);
            moveToNextTest(context);
            return plan(context);
        }

        // GIT WORKFLOW
        if (!hasCreatedBranch)   return planCreateBranch(context, test);
        if (!hasCommitted)       return planCommitChanges(context, test);
        if (!hasCreatedApproval) return planCreateApproval(context, test);
        if (!hasCreatedPR)       return planCreatePullRequest(context, test);

        moveToNextTest(context);
        return plan(context);
    }


    /**
     * Extract the first error message from the stability analysis stored in context,
     * so SelfHealingAgent has a concrete failure string to parse.
     */
    private String extractFirstErrorMessage(AgentContext context) {
        try {
            String stabilityResultJson = (String) getTestAnalysis(context).get("stabilityResult");
            if (stabilityResultJson == null) return null;

            StabilityAnalysisResult result = objectMapper.readValue(
                    stabilityResultJson, StabilityAnalysisResult.class);

            List<String> errors = result.getErrorMessages();
            return (errors != null && !errors.isEmpty()) ? errors.get(0) : null;

        } catch (Exception e) {
            log.warn("Could not extract error message: {}", e.getMessage());
            return null;
        }
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
                updateStateFromActionResult(actionType, result, context);
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
        List<String> ids = getTestIds(context);
        boolean allProcessed = !ids.isEmpty() && getTestIndex(context) >= ids.size();
        if (allProcessed) {
            log.info("✅ Goal achieved: All {} tests processed", ids.size());
        }
        return allProcessed;
    }

    private void initializeAgentState(AgentContext context) {
        log.info("🔧 Initializing FlakyTestAgent state");

        Map<String, Object> goalParams = context.getGoal().getParameters();
        List<String> testIds = new ArrayList<>();

        if (goalParams.containsKey("testId")) {
            testIds.add((String) goalParams.get("testId"));
            log.info("Analyzing specific test: {}", goalParams.get("testId"));
        } else {
            testRepository.findAll().stream()
                    .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                    .forEach(t -> testIds.add(t.getId().toString()));
            log.info("Added {} active tests for analysis", testIds.size());
        }

        context.putState(State.TESTS_TO_ANALYZE,      testIds);
        context.putState(State.CURRENT_TEST_INDEX,     0);
        context.putState(State.CURRENT_TEST_ANALYSIS,  new HashMap<>());
        context.putState(State.FIX_ATTEMPT_COUNT,      0);
        context.putState(State.ORIGINAL_CONTENT,       null);
        context.putState(State.LAST_APPLIED_FIX,       null);
        context.putState(State.FIX_VERIFIED,           false);
        context.putState("fixPhaseStartIteration", null);

    }

    // ========== PLANNING METHODS ==========

    private AgentPlan planAnalyzeStability(AgentContext context, Test test) {
        if (getOriginalContent(context) == null) {
            setOriginalContent(context, test.getContent());
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
        String stabilityResult = (String) getTestAnalysis(context).get("stabilityResult");

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
        String stabilityResult = (String) getTestAnalysis(context).get("stabilityResult");
        String testClassName = (String) getTestAnalysis(context).get("testCaseName");

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
        String stabilityResult = (String) getTestAnalysis(context).get("stabilityResult");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("stabilityResult", stabilityResult);
        parameters.put("testCode", test.getContent());
        parameters.put("attemptNumber", getFixAttemptCount(context) + 1);

        return AgentPlan.builder()
                .nextAction(AgentActionType.SUGGEST_FIX)
                .actionParameters(parameters)
                .reasoning(String.format("Generating fix attempt %d/%d for: %s",
                        getFixAttemptCount(context) + 1, config.getMaxFixAttempts(), test.getName()))
                .confidence(0.7)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planApplyFix(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testId", test.getId().toString());
        parameters.put("fixedTestCode", getLastAppliedFix(context));

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
        int fixAttemptCount = getFixAttemptCount(context);

        // ✅ Resolve the actual draft file path from DraftFileService working directory.
        // Previously hardcoded "tests/" + test.getName() + ".java" which never existed on disk
        // causing GitServiceImpl to throw "Source file not found".
        // The file was written by WriteTestFileTool to playwright-tests/drafts/
        // at generation time. Construct the path the same way DraftFileService resolves it.
        String draftFileName = test.getName().replace(" ", "_") + ".java";
        String draftFilePath = context.getWorkProduct("draftFilePath", String.class);
        if (draftFilePath == null) {
            // Fallback: reconstruct from DraftFileService root config
            // CommitChangesTool will log a warning if file is not found
            draftFilePath = "playwright-tests/drafts/" + draftFileName;
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storyKey",       "FLAKY-" + test.getId().toString().substring(0, 8));
        parameters.put("branchName",     branchName);
        parameters.put("commitMessage",  String.format(
                "Fix flaky test: %s\n\nFixed after %d attempts using AI-generated fix",
                test.getName(), fixAttemptCount + 1));
        parameters.put("filePaths",      List.of(draftFilePath));
        // ✅ Pass aiGeneratedTestId so GitServiceImpl can load the AIGeneratedTest record
        String aiGenId = context.getWorkProduct("aiGeneratedTestId", String.class);
        if (aiGenId != null) {
            parameters.put("aiGeneratedTestId", aiGenId);
        }

        return AgentPlan.builder()
                .nextAction(AgentActionType.COMMIT_CHANGES)
                .actionParameters(parameters)
                .reasoning("Committing flaky test fix from playwright-tests/drafts/")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApproval(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testCode",    test.getContent());
        parameters.put("jiraKey",     "FLAKY-" + test.getId().toString().substring(0, 8));
        parameters.put("testName",    test.getName());
        parameters.put("requestedBy", "FlakyTestAgent");
        // ✅ Git branch already committed — approval is PR review gate, no file sync needed
        parameters.put("requestType", "FLAKY_FIX");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("Flakiness fix committed to Git branch — creating PR review approval")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreateApprovalForManualReview(AgentContext context, Test test) {
        Map<String, Object> parameters = new HashMap<>();
        // ✅ Intentionally sends original content — do NOT change to test.getContent()
        // Content is the original broken version. Writing it to disk would regress the file.
        parameters.put("testCode",    getOriginalContent(context));
        parameters.put("jiraKey",     "FLAKY-MANUAL-" + test.getId().toString().substring(0, 8));
        parameters.put("testName",    test.getName() + " [NEEDS MANUAL REVIEW]");
        parameters.put("requestedBy", "FlakyTestAgent");
        // ✅ No file sync — original content, human must fix manually
        parameters.put("requestType", "FLAKY_MANUAL");

        return AgentPlan.builder()
                .nextAction(AgentActionType.REQUEST_APPROVAL)
                .actionParameters(parameters)
                .reasoning("All fix attempts failed — flagging for manual review")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    private AgentPlan planCreatePullRequest(AgentContext context, Test test) {
        String branchName = context.getWorkProduct("branchName", String.class);
        if (branchName == null) {
            branchName = "fix/flaky-" + test.getName().replaceAll("[^a-zA-Z0-9]", "-");
        }
        int fixAttemptCount = getFixAttemptCount(context);

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
                        "totalAnalyzed", getTestIds(context).size(),
                        "results", getTestAnalysis(context)
                ))
                .reasoning("All tests processed")
                .confidence(1.0)
                .requiresApproval(false)
                .build();
    }

    // ========== STATE MANAGEMENT ==========

    private void updateStateFromActionResult(AgentActionType actionType,
                                             Map<String, Object> result,
                                             AgentContext context) throws JsonProcessingException {
        switch (actionType) {
            case ANALYZE_TEST_STABILITY, ANALYZE_FAILURE -> {
                Map<String, Object> analysis = new HashMap<>(getTestAnalysis(context));
                analysis.putAll(result);
                setTestAnalysis(context, analysis);
            }
            case SUGGEST_FIX -> {
                Object fixed = result.get("fixedTestCode");
                    if (fixed instanceof String s) {
                        setLastAppliedFix(context, s);
                    }else if (fixed != null) {
                        setLastAppliedFix(context, objectMapper.writeValueAsString(fixed));
                    }else {
                        setLastAppliedFix(context, "{\"steps\":[]}");
                    }
            }
            case EXECUTE_TEST -> {
                boolean stable = (boolean) result.getOrDefault("isStable", false);
                setFixVerified(context, stable);
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
        setTestIndex(context, getTestIndex(context) + 1);
        setFixAttemptCount(context, 0);
        setOriginalContent(context, null);
        setLastAppliedFix(context, null);
        setFixVerified(context, false);
        setTestAnalysis(context, new HashMap<>());
        context.putState("fixPhaseStartIteration", null);
        context.putState("currentTestStartIteration", context.getCurrentIteration());
    }

    private boolean hasCompletedAction(AgentContext context, AgentActionType actionType, String scope) {
        if (context.getActionHistory() == null || context.getActionHistory().isEmpty()) return false;

        String stateKey = "fix".equals(scope) ? "fixPhaseStartIteration" : "currentTestStartIteration";
        Integer startIter = context.getState(stateKey, Integer.class);
        if (startIter == null) startIter = 0;

        final int start = startIter;
        return context.getActionHistory().stream()
                .filter(entry -> entry.getIteration() >= start)
                .filter(entry -> entry.getActionType() == actionType)
                .anyMatch(AgentHistoryEntry::isSuccess);
    }

    interface State {
        String TESTS_TO_ANALYZE      = "flaky.testsToAnalyze";      // List<String> (UUID strings)
        String CURRENT_TEST_INDEX    = "flaky.currentTestIndex";     // Integer
        String CURRENT_TEST_ANALYSIS = "flaky.currentTestAnalysis";  // Map<String,Object>
        String FIX_ATTEMPT_COUNT     = "flaky.fixAttemptCount";      // Integer
        String ORIGINAL_CONTENT      = "flaky.originalTestContent";  // String
        String LAST_APPLIED_FIX      = "flaky.lastAppliedFix";       // String
        String FIX_VERIFIED          = "flaky.fixVerified";          // Boolean
    }

    // ── context accessors ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> getTestIds(AgentContext ctx) {
        List<String> ids = ctx.getState(State.TESTS_TO_ANALYZE, List.class);
        return ids != null ? ids : List.of();
    }

    private int getTestIndex(AgentContext ctx) {
        Integer v = ctx.getState(State.CURRENT_TEST_INDEX, Integer.class);
        return v != null ? v : 0;
    }

    private void setTestIndex(AgentContext ctx, int v)      { ctx.putState(State.CURRENT_TEST_INDEX, v); }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTestAnalysis(AgentContext ctx) {
        Map<String, Object> m = ctx.getState(State.CURRENT_TEST_ANALYSIS, Map.class);
        return m != null ? m : new HashMap<>();
    }

    private void setTestAnalysis(AgentContext ctx, Map<String, Object> m) {
        ctx.putState(State.CURRENT_TEST_ANALYSIS, m);
    }

    private int getFixAttemptCount(AgentContext ctx) {
        Integer v = ctx.getState(State.FIX_ATTEMPT_COUNT, Integer.class);
        return v != null ? v : 0;
    }

    private void setFixAttemptCount(AgentContext ctx, int v) { ctx.putState(State.FIX_ATTEMPT_COUNT, v); }

    private String getOriginalContent(AgentContext ctx)      { return ctx.getState(State.ORIGINAL_CONTENT, String.class); }
    private void setOriginalContent(AgentContext ctx, String v) { ctx.putState(State.ORIGINAL_CONTENT, v); }

    private String getLastAppliedFix(AgentContext ctx)       { return ctx.getState(State.LAST_APPLIED_FIX, String.class); }
    private void setLastAppliedFix(AgentContext ctx, String v) { ctx.putState(State.LAST_APPLIED_FIX, v); }

    private boolean isFixVerified(AgentContext ctx) {
        Boolean v = ctx.getState(State.FIX_VERIFIED, Boolean.class);
        return v != null && v;
    }

    private void setFixVerified(AgentContext ctx, boolean v) { ctx.putState(State.FIX_VERIFIED, v); }
}