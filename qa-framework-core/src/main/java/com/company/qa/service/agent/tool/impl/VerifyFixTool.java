package com.company.qa.service.agent.tool.impl;

import com.company.qa.config.FlakyTestConfig;
import com.company.qa.config.PlaywrightProperties;
import com.company.qa.execution.engine.ExecutionResult;
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
 * Agent tool to verify that a fix stabilizes a flaky test.
 *
 * Runs the test multiple times and checks if ALL runs pass.
 *
 * Input parameters:
 * - testId: UUID of the test to verify
 * - runCount: Number of times to run (defaults to config.verificationRuns)
 *
 * Output:
 * - success: true if verification completed
 * - isStable: true if ALL runs passed
 * - totalRuns: Number of runs performed
 * - passedRuns: Number that passed
 * - failedRuns: Number that failed
 * - pattern: Pass/fail pattern (e.g., "PPPPP" = all pass)
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyFixTool implements AgentTool {

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
        return AgentActionType.EXECUTE_TEST;
    }

    @Override
    public String getName() {
        return "Fix Verifier";
    }

    @Override
    public String getDescription() {
        return "Verifies that a fix stabilizes a flaky test by running it multiple times. " +
                "Returns true only if ALL runs pass. Use after applying fix with ApplyFixTool.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("🔍 Verifying fix for test: {}", parameters.get("testId"));

        try {
            // Extract parameters
            String testIdStr = (String) parameters.get("testId");
            UUID testId = UUID.fromString(testIdStr);

            int runCount = parameters.containsKey("runCount")
                    ? ((Number) parameters.get("runCount")).intValue()
                    : flakyTestConfig.getVerificationRuns();

            // Get test
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new RuntimeException("Test not found: " + testId));

            log.info("Verifying test {} times: {}", runCount, test.getName());

            // Run test multiple times
            List<Boolean> results = new ArrayList<>();
            List<String> errorMessages = new ArrayList<>();
            StringBuilder pattern = new StringBuilder();

            try (Playwright playwright = Playwright.create()) {
                Browser browser =playwrightFactory.createBrowser(PlaywrightProperties.BrowserType.FIREFOX);

               /* Browser browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(true)
                );*/

                for (int i = 0; i < runCount; i++) {
                    // Check cooperative stop flag and thread interrupt before each run.
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("🛑 Stop requested — aborting verification loop at run {}/{}", i + 1, runCount);
                        Thread.currentThread().interrupt();
                        break;
                    }
                    log.info("  Verification run {}/{} for: {}", i + 1, runCount, test.getName());

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
                        pattern.append(allStepsPassed ? "P" : "F");

                        log.info("    Result: {}", allStepsPassed ? "✅ PASS" : "❌ FAIL");

                    } catch (Exception e) {
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        log.error("    Test execution failed: {}", errMsg);

                        if (errMsg.contains("TargetClosedError") || errMsg.contains("Target page, context or browser has been closed")
                                || Thread.currentThread().isInterrupted()) {
                            log.info("🛑 Browser closed due to stop request — aborting remaining verification runs");
                            results.add(false);
                            errorMessages.add(String.format("Run %d: aborted (stop requested)", i + 1));
                            pattern.append("F");
                            try { page.close(); } catch (Exception ignored) {}
                            try { browserContext.close(); } catch (Exception ignored) {}
                            break;
                        }

                        results.add(false);
                        errorMessages.add(String.format("Run %d: %s", i + 1, errMsg));
                        pattern.append("F");
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

            // Test is stable ONLY if ALL runs passed
            boolean isStable = failedRuns == 0;

            String verdict = isStable ? "✅ STABLE" : "❌ STILL FLAKY";
            log.info("{} - Verification complete: {} ({} P, {} F)",
                    verdict, test.getName(), passedRuns, failedRuns);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("isStable", isStable);
            result.put("totalRuns", runCount);
            result.put("passedRuns", (int) passedRuns);
            result.put("failedRuns", (int) failedRuns);
            result.put("pattern", pattern.toString());
            result.put("testName", test.getName());
            result.put("errorMessages", errorMessages);

            return result;

        } catch (Exception e) {
            log.error("❌ Verification failed: {}", e.getMessage(), e);

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
        schema.put("testId", "string (required) - UUID of the test to verify");
        schema.put("runCount", "integer (optional) - Number of verification runs (default: 5)");
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