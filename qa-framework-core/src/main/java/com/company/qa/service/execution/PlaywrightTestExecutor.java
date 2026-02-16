package com.company.qa.service.execution;

import com.company.qa.config.PlaywrightProperties;
import com.company.qa.execution.engine.ExecutionResult;
import com.company.qa.model.dto.TestStep;
import com.company.qa.model.enums.LocatorStrategy;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes test steps using Playwright.
 * Adapted to work with existing ExecutionResult and TestStep models.
 *
 * @author QA Framework
 * @since Week 11 Day 3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightTestExecutor {

    private final PlaywrightFactory playwrightFactory;
    private final PlaywrightProperties properties;

    /**
     * Execute a single test step.
     *
     * @param step the test step to execute
     * @param page the Playwright page
     * @param executionId unique ID for this test execution
     * @return ExecutionResult with success/failure details
     */
    public ExecutionResult executeStep(TestStep step, Page page, String executionId) {
        Instant startTime = Instant.now();

        log.debug("Executing step: action={}, locator={}",
                step.getAction(), step.getLocator());

        try {
            // Execute based on action type
            String action = step.getAction().toLowerCase();

            switch (action) {
                case "navigate" -> executeNavigate(page, step);
                case "click" -> executeClick(page, step);
                case "type", "sendkeys" -> executeType(page, step);
                case "clear" -> executeClear(page, step);
                case "select" -> executeSelect(page, step);
                case "check" -> executeCheck(page, step);
                case "uncheck" -> executeUncheck(page, step);
                case "wait" -> executeWait(page, step);
                case "asserttext", "verify_text" -> executeVerifyText(page, step);
                case "assertvisible", "verify_visible" -> executeVerifyVisible(page, step);
                default -> throw new IllegalArgumentException(
                        "Unsupported action: " + step.getAction()
                );
            }

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();

            log.info("Step executed successfully in {}ms: {}",
                    durationMs, step.getAction());

            return ExecutionResult.builder()
                    .success(true)
                    .durationMs(durationMs)
                    .errorMessage(null)
                    .screenshotUrls(Collections.emptyList())
                    .logUrl(null)
                    .testScript(null)
                    .externalExecutionRef(executionId)
                    .build();

        } catch (Exception e) {
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();

            log.error("Step execution failed: action={}, locator={}, error={}",
                    step.getAction(), step.getLocator(), e.getMessage());

            // Capture screenshot on failure
            List<String> screenshotUrls = captureFailureScreenshot(page, executionId, step);

            return ExecutionResult.builder()
                    .success(false)
                    .durationMs(durationMs)
                    .errorMessage("Step execution failed: " + e.getMessage())
                    .screenshotUrls(screenshotUrls)
                    .logUrl(null)
                    .testScript(null)
                    .externalExecutionRef(executionId)
                    .build();
        }
    }

    /**
     * Execute NAVIGATE action.
     */
    private void executeNavigate(Page page, TestStep step) {
        String url = step.getValue();
        if (url == null || url.trim().isEmpty()) {
            url = step.getLocator(); // Fallback to locator
        }

        log.debug("Navigating to: {}", url);
        page.navigate(url);
        page.waitForLoadState();
        log.debug("Navigation complete");
    }

    /**
     * Execute CLICK action.
     */
    private void executeClick(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);

        log.debug("Clicking element: {}", step.getLocator());
        locator.click();
        log.debug("Click complete");
    }

    /**
     * Execute TYPE/SENDKEYS action.
     */
    private void executeType(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);
        String text = step.getValue();

        if (text == null) {
            throw new IllegalArgumentException("TYPE action requires value");
        }

        log.debug("Typing text: {} into {}", text, step.getLocator());
        locator.fill(text);
        log.debug("Type complete");
    }

    /**
     * Execute CLEAR action.
     */
    private void executeClear(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);

        log.debug("Clearing element: {}", step.getLocator());
        locator.clear();
        log.debug("Clear complete");
    }

    /**
     * Execute SELECT action.
     */
    private void executeSelect(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);
        String value = step.getValue();

        if (value == null) {
            throw new IllegalArgumentException("SELECT action requires value");
        }

        log.debug("Selecting option: {} in {}", value, step.getLocator());
        locator.selectOption(value);
        log.debug("Select complete");
    }

    /**
     * Execute CHECK action.
     */
    private void executeCheck(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);

        log.debug("Checking checkbox: {}", step.getLocator());
        locator.check();
        log.debug("Check complete");
    }

    /**
     * Execute UNCHECK action.
     */
    private void executeUncheck(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);

        log.debug("Unchecking checkbox: {}", step.getLocator());
        locator.uncheck();
        log.debug("Uncheck complete");
    }

    /**
     * Execute WAIT action.
     */
    private void executeWait(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);

        int timeout = step.getTimeout() != null && step.getTimeout() > 0
                ? step.getTimeout() * 1000  // Convert seconds to milliseconds
                : properties.getTimeout();

        log.debug("Waiting for element: {} (timeout: {}ms)",
                step.getLocator(), timeout);

        locator.waitFor(new Locator.WaitForOptions()
                .setTimeout(timeout)
                .setState(WaitForSelectorState.VISIBLE));

        log.debug("Wait complete");
    }

    /**
     * Execute VERIFY_TEXT/ASSERTTEXT action.
     */
    private void executeVerifyText(Page page, TestStep step) {
        String expectedText = step.getValue();
        if (expectedText == null) {
            throw new IllegalArgumentException("VERIFY_TEXT requires value");
        }

        Locator locator = resolveLocator(page, step);
        String actualText = locator.textContent();

        log.debug("Verifying text: expected='{}', actual='{}'",
                expectedText, actualText);

        if (actualText == null || !actualText.contains(expectedText)) {
            throw new AssertionError(
                    String.format("Text verification failed. Expected: '%s', Actual: '%s'",
                            expectedText, actualText)
            );
        }

        log.debug("Text verification passed");
    }

    /**
     * Execute VERIFY_VISIBLE/ASSERTVISIBLE action.
     */
    private void executeVerifyVisible(Page page, TestStep step) {
        Locator locator = resolveLocator(page, step);

        log.debug("Verifying element visible: {}", step.getLocator());

        if (!locator.isVisible()) {
            throw new AssertionError(
                    "Element is not visible: " + step.getLocator()
            );
        }

        log.debug("Visibility verification passed");
    }

    /**
     * Resolve TestStep locator to Playwright Locator.
     * Parses locator string to determine strategy.
     *
     * Format examples:
     * - "css=#submitBtn" → CSS selector
     * - "xpath=//button" → XPath
     * - "role=button[name='Submit']" → Role-based
     * - "#submitBtn" → CSS (default if no prefix)
     *
     * @param page Playwright page
     * @param step test step
     * @return Playwright Locator
     */
    private Locator resolveLocator(Page page, TestStep step) {
        String locatorString = step.getLocator();

        if (locatorString == null || locatorString.trim().isEmpty()) {
            throw new IllegalArgumentException("Locator cannot be null or empty");
        }

        // Parse locator type and value
        LocatorStrategy strategy;
        String value;

        if (locatorString.matches("^[a-zA-Z]+\\s*=.*")){            // Format: "type=value" (e.g., "css=#btn", "xpath=//button")
            String[] parts = locatorString.split("=", 2);
            strategy = LocatorStrategy.fromString(parts[0].trim());
            value = parts[1].trim();
        } else {
            // Default to CSS if no prefix
            strategy = LocatorStrategy.CSS;
            value = locatorString.trim();
        }

        log.debug("Resolving locator: strategy={}, value={}", strategy, value);

        Locator locator = switch (strategy) {
            case ROLE -> resolveRoleLocator(page, value);
            case LABEL -> page.getByLabel(value);
            case TEXT -> page.getByText(value);
            case TESTID -> page.getByTestId(value);
            case CSS -> page.locator(value);
            case XPATH -> page.locator("xpath=" + value);
            case ID -> page.locator("#" + value);
            case NAME -> page.locator("[name='" + value + "']");
            case CLASS -> page.locator("." + value);
        };

        // Set custom timeout if specified (convert seconds to ms)
        if (step.getTimeout() != null && step.getTimeout() > 0) {
            int timeoutMs = step.getTimeout() * 1000;
            locator.waitFor(new Locator.WaitForOptions()
                    .setTimeout(timeoutMs));
        }

        return locator;
    }

    /**
     * Resolve ROLE locator with name parsing.
     * Format: "button[name='Submit']" or just "button"
     */
    private Locator resolveRoleLocator(Page page, String value) {
        String role;
        String name = null;

        if (value.contains("[name=")) {
            int bracketIndex = value.indexOf('[');
            role = value.substring(0, bracketIndex).trim();

            int nameStart = value.indexOf("'");
            if (nameStart == -1) {
                nameStart = value.indexOf('"');
            }
            int nameEnd = value.lastIndexOf("'");
            if (nameEnd == -1) {
                nameEnd = value.lastIndexOf('"');
            }

            if (nameStart > 0 && nameEnd > nameStart) {
                name = value.substring(nameStart + 1, nameEnd);
            }
        } else {
            role = value.trim();
        }

        AriaRole ariaRole = parseAriaRole(role);

        if (name != null) {
            log.debug("Resolving role locator: role={}, name={}", role, name);
            return page.getByRole(ariaRole, new Page.GetByRoleOptions().setName(name));
        } else {
            log.debug("Resolving role locator: role={}", role);
            return page.getByRole(ariaRole);
        }
    }

    /**
     * Parse string to AriaRole enum.
     */
    private AriaRole parseAriaRole(String role) {
        return switch (role.toLowerCase()) {
            case "button" -> AriaRole.BUTTON;
            case "link" -> AriaRole.LINK;
            case "textbox" -> AriaRole.TEXTBOX;
            case "checkbox" -> AriaRole.CHECKBOX;
            case "radio" -> AriaRole.RADIO;
            case "combobox" -> AriaRole.COMBOBOX;
            case "heading" -> AriaRole.HEADING;
            case "img", "image" -> AriaRole.IMG;
            case "listitem" -> AriaRole.LISTITEM;
            case "list" -> AriaRole.LIST;
            case "table" -> AriaRole.TABLE;
            case "row" -> AriaRole.ROW;
            case "cell" -> AriaRole.CELL;
            default -> throw new IllegalArgumentException(
                    "Unsupported ARIA role: " + role
            );
        };
    }

    /**
     * Capture screenshot on failure.
     * Returns list of screenshot URLs (single item for now).
     *
     * @param page Playwright page
     * @param executionId test execution ID
     * @param step failed test step
     * @return list of screenshot URLs
     */
    private List<String> captureFailureScreenshot(Page page, String executionId, TestStep step) {
        try {
            String screenshotName = String.format(
                    "%s_failure_%s",
                    executionId,
                    step.getAction()
            );

            Path screenshot = playwrightFactory.captureScreenshot(page, screenshotName);

            if (screenshot != null) {
                List<String> urls = new ArrayList<>();
                urls.add(screenshot.toString());
                return urls;
            }

            return Collections.emptyList();

        } catch (Exception e) {
            log.warn("Failed to capture screenshot: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}