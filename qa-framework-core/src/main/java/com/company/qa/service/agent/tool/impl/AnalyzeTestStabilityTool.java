package com.company.qa.service.agent.tool.impl;

import com.company.qa.config.FlakyTestConfig;
import com.company.qa.execution.engine.ExecutionResult;
import com.company.qa.model.agent.StabilityAnalysisResult;
import com.company.qa.model.dto.TestStep;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.execution.PlaywrightFactory;
import com.company.qa.service.execution.PlaywrightTestExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent tool to analyze test stability by running it multiple times.
 *
 * Input parameters:
 * - testId: UUID of the test to analyze
 * - runCount: Number of times to run (defaults to config value)
 *
 * Output:
 * - success: true/false
 * - stabilityResult: StabilityAnalysisResult JSON string
 * - error: error message if failed
 *
 * Browser lifecycle:
 *   Browser is created once via PlaywrightFactory (which handles Apple Silicon
 *   --disable-gpu / --no-sandbox args automatically). A fresh BrowserContext+Page
 *   is created per run for isolation, then closed in the finally block.
 *   The Browser itself is closed after all runs complete.
 *
 * @author QA Framework
 * @since Week 16 Day 1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeTestStabilityTool implements AgentTool {

    private final TestRepository testRepository;
    private final PlaywrightTestExecutor playwrightExecutor;
    private final FlakyTestConfig flakyTestConfig;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;
    private final PlaywrightFactory playwrightFactory;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.ANALYZE_TEST_STABILITY;
    }

    @Override
    public String getName() {
        return "Test Stability Analyzer";
    }

    @Override
    public String getDescription() {
        return "Runs a test multiple times to analyze stability and detect flakiness. " +
                "Returns pass/fail pattern and flakiness score. Use this to confirm if a test is flaky.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("🔍 Analyzing test stability: {}", parameters);

        try {
            String testIdStr = (String) parameters.get("testId");
            UUID testId = UUID.fromString(testIdStr);

            int runCount = parameters.containsKey("runCount")
                    ? ((Number) parameters.get("runCount")).intValue()
                    : flakyTestConfig.getStabilityCheckRuns();

            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new RuntimeException("Test not found: " + testId));

            log.info("Running test {} times: {}", runCount, test.getName());

            List<Boolean> results = new ArrayList<>();
            List<String> errorMessages = new ArrayList<>();
            List<String> executionIds = new ArrayList<>();
            StringBuilder pattern = new StringBuilder();

            // PlaywrightFactory.createBrowser() handles all platform-specific args:
            //   - Apple Silicon: --disable-gpu, --no-sandbox
            //   - macOS general: --disable-dev-shm-usage
            // Do NOT call Playwright.create() here — that bypasses those fixes.
            Browser browser = playwrightFactory.createBrowser();
            try {
                for (int i = 0; i < runCount; i++) {
                    // Cooperative cancellation: check interrupt flag before every run.
                    // AgentOrchestrator.stopAgent() calls future.cancel(true) which sets this flag.
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("🛑 Stop requested — aborting run loop at run {}/{}", i + 1, runCount);
                        Thread.currentThread().interrupt(); // restore for BaseAgent
                        break;
                    }

                    log.info("  Run {}/{} for test: {}", i + 1, runCount, test.getName());

                    // Fresh context + page per run = full isolation (separate cookies, auth state)
                    BrowserContext browserContext = browser.newContext();
                    Page page = browserContext.newPage();

                    try {
                        List<TestStep> steps = parseTestSteps(test.getContent());
                        boolean allStepsPassed = true;
                        String executionId = UUID.randomUUID().toString();

                        for (TestStep step : steps) {
                            ExecutionResult result = playwrightExecutor.executeStep(
                                    step, page, executionId
                            );
                            if (!result.isSuccess()) {
                                allStepsPassed = false;
                                errorMessages.add(
                                        String.format("Run %d: %s", i + 1, result.getErrorMessage())
                                );
                                break;
                            }
                        }

                        results.add(allStepsPassed);
                        executionIds.add(executionId);
                        pattern.append(allStepsPassed ? "P" : "F");
                        log.info("    Result: {}", allStepsPassed ? "PASS" : "FAIL");

                    } catch (Exception e) {
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        log.error("    Test execution failed: {}", errMsg);

                        // TargetClosedError means the browser was closed by thread interrupt.
                        // Continuing the loop would throw the same error every iteration — stop.
                        if (errMsg.contains("TargetClosedError")
                                || errMsg.contains("Target page, context or browser has been closed")
                                || Thread.currentThread().isInterrupted()) {
                            log.info("🛑 Browser closed due to stop request — aborting remaining runs");
                            results.add(false);
                            errorMessages.add(String.format("Run %d: aborted (stop requested)", i + 1));
                            pattern.append("F");
                            executionIds.add(UUID.randomUUID().toString());
                            break;
                        }

                        results.add(false);
                        errorMessages.add(String.format("Run %d: %s", i + 1, errMsg));
                        pattern.append("F");
                        executionIds.add(UUID.randomUUID().toString());

                    } finally {
                        try { page.close(); } catch (Exception ignored) {}
                        try { browserContext.close(); } catch (Exception ignored) {}
                    }
                }
            } finally {
                playwrightFactory.closeBrowser(browser);
            }

            long passedRuns = results.stream().filter(r -> r).count();
            long failedRuns  = results.stream().filter(r -> !r).count();
            boolean isFlaky  = passedRuns > 0 && failedRuns > 0;

            double flakinessScore = 0.0;
            if (isFlaky) {
                double failureRate = (double) failedRuns / runCount;
                flakinessScore = 4 * failureRate * (1 - failureRate); // peaks at 50% failure rate
            }

            StabilityAnalysisResult analysisResult = StabilityAnalysisResult.builder()
                    .testId(testId.toString())
                    .testName(test.getName())
                    .totalRuns(runCount)
                    .passedRuns((int) passedRuns)
                    .failedRuns((int) failedRuns)
                    .pattern(pattern.toString())
                    .isFlaky(isFlaky)
                    .flakinessScore(Math.round(flakinessScore * 100.0) / 100.0)
                    .errorMessages(errorMessages)
                    .executionIds(executionIds)
                    .build();

            log.info("✅ Stability analysis complete: {} - {} ({} P, {} F)",
                    test.getName(), isFlaky ? "FLAKY" : "STABLE", passedRuns, failedRuns);

            Map<String, Object> result = new HashMap<>();
            result.put("success",         true);
            result.put("stabilityResult", objectMapper.writeValueAsString(analysisResult));
            result.put("pattern",         pattern.toString());
            result.put("isFlaky",         isFlaky);
            result.put("passedRuns",      passedRuns);
            result.put("failedRuns",      failedRuns);
            return result;

        } catch (Exception e) {
            log.error("❌ Stability analysis failed: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error",   e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("testId")) return false;
        Object testId = parameters.get("testId");
        if (!(testId instanceof String)) return false;
        try { UUID.fromString((String) testId); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("testId",   "string (required) - UUID of the test to analyze");
        schema.put("runCount", "integer (optional) - Number of times to run test (default: 5)");
        return schema;
    }

    private List<TestStep> parseTestSteps(String content) throws Exception {
        String trimmed = content.trim();

        // Unwrap double-encoded strings: "\"{ actual json }\""
        if (trimmed.startsWith("\"")) {
            try {
                String unwrapped = objectMapper.readValue(trimmed, String.class);
                trimmed = unwrapped.trim();
            } catch (Exception ignored) {}
        }

        if (trimmed.startsWith("{")) {
            Map<String, Object> contentMap = objectMapper.readValue(trimmed, new TypeReference<>() {});
            if (contentMap.containsKey("steps")) {
                String stepsJson = objectMapper.writeValueAsString(contentMap.get("steps"));
                return objectMapper.readValue(stepsJson, new TypeReference<>() {});
            }
        }
        return objectMapper.readValue(trimmed, new TypeReference<>() {});
    }
}