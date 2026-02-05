package com.company.qa.execution.engine;

import com.company.qa.execution.context.ExecutionContext;
import com.company.qa.model.dto.TestStep;
import com.company.qa.service.execution.PlaywrightFactory;
import com.company.qa.service.execution.PlaywrightTestExecutor;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Playwright implementation of ExecutionEngine.
 *
 * Follows same pattern as InternalExecutionEngine:
 * - Receives ExecutionContext
 * - Creates browser via PlaywrightFactory
 * - Executes test steps via PlaywrightTestExecutor
 * - Returns ExecutionResult
 * - Cleans up resources
 *
 * Week 11 Day 4 Implementation
 *
 * @author QA Framework Team
 * @since Week 11
 */
@Slf4j
@Component("playwrightExecutionEngine")
@RequiredArgsConstructor
public class PlaywrightExecutionEngine implements ExecutionEngine {

    private final PlaywrightFactory playwrightFactory;
    private final PlaywrightTestExecutor playwrightTestExecutor;

    @Override
    public ExecutionResult execute(ExecutionContext context) throws Exception {
        log.info("Starting Playwright execution: {}", context.getExecutionId());

        Browser browser = null;
        BrowserContext browserContext = null;
        Page page = null;

        long startTime = System.currentTimeMillis();
        List<String> allScreenshotUrls = new ArrayList<>();
        String traceUrl = null;
        boolean allStepsPassed = true;
        String errorMessage = null;

        try {
            // Step 1: Create Playwright browser
            log.debug("Creating browser (executionId: {})", context.getExecutionId());
            browser = playwrightFactory.createBrowser();

            // Step 2: Create browser context with execution ID for trace/video naming
            browserContext = playwrightFactory.createContext(
                    browser,
                    context.getExecutionId().toString()
            );

            // Step 3: Create page
            page = playwrightFactory.createPage(browserContext);

            // Step 4: Execute test steps sequentially
            List<TestStep> steps = context.getTestScript().getSteps();
            log.info("Executing {} test steps", steps.size());

            for (int i = 0; i < steps.size(); i++) {
                TestStep step = steps.get(i);

                log.debug("Executing step {}/{}: action={}",
                        i + 1, steps.size(), step.getAction());

                // Execute single step via PlaywrightTestExecutor
                ExecutionResult stepResult = playwrightTestExecutor.executeStep(
                        step,
                        page,
                        context.getExecutionId().toString()
                );

                // Collect screenshots from step execution
                if (stepResult.getScreenshotUrls() != null &&
                        !stepResult.getScreenshotUrls().isEmpty()) {
                    allScreenshotUrls.addAll(stepResult.getScreenshotUrls());
                }

                // Check if step failed
                if (!stepResult.isSuccess()) {
                    allStepsPassed = false;
                    errorMessage = stepResult.getErrorMessage();
                    log.warn("Step {}/{} failed: {}", i + 1, steps.size(), errorMessage);

                    // FAIL-FAST: Stop execution on first failure
                    break;
                }

                log.debug("Step {}/{} passed", i + 1, steps.size());
            }

            // Step 5: Stop trace recording and get trace file path
            // Note: startTrace is called automatically in createContext if enabled
            if (playwrightFactory.isEnabled()) {
                Path tracePath = playwrightFactory.stopTrace(
                        browserContext,
                        context.getExecutionId().toString()
                );

                if (tracePath != null) {
                    traceUrl = tracePath.toString();
                    log.debug("Trace saved: {}", traceUrl);
                }
            }

            // Step 6: Capture final screenshot (success or failure)
            Path finalScreenshot = playwrightFactory.captureScreenshot(
                    page,
                    context.getExecutionId().toString() + "_final"
            );

            if (finalScreenshot != null) {
                allScreenshotUrls.add(finalScreenshot.toString());
            }

            // Step 7: Calculate duration
            long durationMs = System.currentTimeMillis() - startTime;

            // Step 8: Build ExecutionResult
            ExecutionResult result = ExecutionResult.builder()
                    .success(allStepsPassed)
                    .durationMs(durationMs)
                    .errorMessage(errorMessage)
                    .logUrl(traceUrl)  // Trace path stored in logUrl
                    .screenshotUrls(allScreenshotUrls)
                    .testScript(context.getTestScript())
                    .externalExecutionRef(context.getExecutionId().toString())
                    .build();

            log.info("Playwright execution completed: {} - Success: {} - Duration: {}ms - Screenshots: {}",
                    context.getExecutionId(), allStepsPassed, durationMs, allScreenshotUrls.size());

            return result;

        } catch (Exception e) {
            log.error("Playwright execution engine failure: {}", context.getExecutionId(), e);

            // Stop trace on exception
            if (browserContext != null) {
                try {
                    Path tracePath = playwrightFactory.stopTrace(
                            browserContext,
                            context.getExecutionId().toString()
                    );
                    if (tracePath != null) {
                        traceUrl = tracePath.toString();
                    }
                } catch (Exception traceEx) {
                    log.warn("Failed to save trace on error: {}", traceEx.getMessage());
                }
            }

            // Capture error screenshot
            if (page != null) {
                try {
                    Path errorScreenshot = playwrightFactory.captureScreenshot(
                            page,
                            context.getExecutionId().toString() + "_error"
                    );
                    if (errorScreenshot != null) {
                        allScreenshotUrls.add(errorScreenshot.toString());
                    }
                } catch (Exception screenshotEx) {
                    log.warn("Failed to capture error screenshot: {}", screenshotEx.getMessage());
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;

            // Return failure result
            return ExecutionResult.builder()
                    .success(false)
                    .durationMs(durationMs)
                    .errorMessage("Playwright execution engine failure: " + e.getMessage())
                    .logUrl(traceUrl)
                    .screenshotUrls(allScreenshotUrls)
                    .testScript(context.getTestScript())
                    .externalExecutionRef(context.getExecutionId().toString())
                    .build();

        } finally {
            // Step 9: Always clean up resources in reverse order
            cleanupResources(page, browserContext, browser);
        }
    }

    /**
     * Clean up Playwright resources in reverse order of creation.
     * Called in finally block to ensure cleanup even on exception.
     *
     * Order: Page → BrowserContext → Browser
     */
    private void cleanupResources(Page page, BrowserContext context, Browser browser) {
        // Close page (implicit - closed when context closes)
        if (page != null) {
            try {
                if (!page.isClosed()) {
                    log.debug("Closing page");
                    page.close();
                }
            } catch (Exception e) {
                log.warn("Error closing page: {}", e.getMessage());
            }
        }

        // Close browser context
        if (context != null) {
            playwrightFactory.closeContext(context);
        }

        // Close browser
        if (browser != null) {
            playwrightFactory.closeBrowser(browser);
        }

        log.debug("Playwright resources cleaned up successfully");
    }
}