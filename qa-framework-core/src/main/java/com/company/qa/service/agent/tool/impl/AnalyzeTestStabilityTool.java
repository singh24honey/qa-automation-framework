package com.company.qa.service.agent.tool.impl;

import com.company.qa.config.FlakyTestConfig;
import com.company.qa.config.PlaywrightProperties;
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
import com.microsoft.playwright.*;
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
            // Extract parameters
            String testIdStr = (String) parameters.get("testId");
            UUID testId = UUID.fromString(testIdStr);

            int runCount = parameters.containsKey("runCount")
                    ? ((Number) parameters.get("runCount")).intValue()
                    : flakyTestConfig.getStabilityCheckRuns();

            // Get test
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new RuntimeException("Test not found: " + testId));

            log.info("Running test {} times: {}", runCount, test.getName());

            // Run test multiple times
            List<Boolean> results = new ArrayList<>();
            List<String> errorMessages = new ArrayList<>();
            List<String> executionIds = new ArrayList<>();
            StringBuilder pattern = new StringBuilder();


            try (Playwright playwright = Playwright.create()) {
                Browser browser =playwrightFactory.createBrowser(PlaywrightProperties.BrowserType.FIREFOX);
               /* Browser browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(true)*/



                for (int i = 0; i < runCount; i++) {
                    // Check cooperative stop flag and thread interrupt before each run.
                    // future.cancel(true) sets the interrupt flag; we also check the
                    // stop flag in AgentOrchestrator for explicit user-initiated stops.
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("🛑 Stop requested — aborting run loop at run {}/{}", i + 1, runCount);
                        Thread.currentThread().interrupt(); // restore flag for BaseAgent to see
                        break;
                    }
                    log.info("  Run {}/{} for test: {}", i + 1, runCount, test.getName());

                    BrowserContext browserContext = browser.newContext();
                    Page page = browserContext.newPage();

                    try {
                        // Parse test steps from content
                        List<TestStep> steps = parseTestSteps(test.getContent());

                        // Execute all steps
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

                        // TargetClosedError means the browser was killed by thread interrupt
                        // (future.cancel(true)). Continuing the loop would just throw the same
                        // error on every subsequent run — break out immediately.
                        if (errMsg.contains("TargetClosedError") || errMsg.contains("Target page, context or browser has been closed")
                                || Thread.currentThread().isInterrupted()) {
                            log.info("🛑 Browser closed due to stop request — aborting remaining runs");
                            results.add(false);
                            errorMessages.add(String.format("Run %d: aborted (stop requested)", i + 1));
                            pattern.append("F");
                            executionIds.add(UUID.randomUUID().toString());
                            try { page.close(); } catch (Exception ignored) {}
                            try { browserContext.close(); } catch (Exception ignored) {}
                            break; // stop the run loop
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

                browser.close();
            }

            // Analyze results
            long passedRuns = results.stream().filter(r -> r).count();
            long failedRuns = results.stream().filter(r -> !r).count();

            // A test is flaky if it has both passes and failures
            boolean isFlaky = passedRuns > 0 && failedRuns > 0;

            // Calculate flakiness score
            double flakinessScore = 0.0;
            if (isFlaky) {
                double failureRate = (double) failedRuns / runCount;
                // Peak flakiness at 50% failure rate
                flakinessScore = 4 * failureRate * (1 - failureRate);
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
                    test.getName(), isFlaky ? "FLAKY" : "STABLE",
                    passedRuns, failedRuns);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("stabilityResult", objectMapper.writeValueAsString(analysisResult));
            result.put("pattern", pattern.toString());
            result.put("isFlaky", isFlaky);
            result.put("passedRuns", passedRuns);
            result.put("failedRuns", failedRuns);

            return result;

        } catch (Exception e) {
            log.error("❌ Stability analysis failed: {}", e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("testId")) {
            return false;
        }

        Object testId = parameters.get("testId");
        if (!(testId instanceof String)) {
            return false;
        }

        try {
            UUID.fromString((String) testId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("testId", "string (required) - UUID of the test to analyze");
        schema.put("runCount", "integer (optional) - Number of times to run test (default: 5)");
        return schema;
    }

    /**
     * Parse test steps from test content.
     */
    private List<TestStep> parseTestSteps(String content) throws Exception {
        // If content is wrapped in {"steps": [...]}
        if (content.trim().startsWith("{")) {
            Map<String, Object> contentMap = objectMapper.readValue(
                    content, new TypeReference<>() {}
            );

            if (contentMap.containsKey("steps")) {
                String stepsJson = objectMapper.writeValueAsString(contentMap.get("steps"));
                return objectMapper.readValue(stepsJson, new TypeReference<>() {});
            }
        }

        // Otherwise assume it's direct array of steps
        return objectMapper.readValue(content, new TypeReference<>() {});
    }
}