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
                // waitForLoadState: waits for the page network/DOM to settle.
                // The AI test generator emits this action after navigate steps.
                // Without this case every test run fails immediately after navigation.
                case "waitforloadstate", "wait_for_load_state", "waitforload" ->
                        executeWaitForLoadState(page, step);
                // AI frequently generates these page-load wait variants — all map to waitForLoadState
                case "waitforpageload", "wait_for_page_load", "waitpageload",
                        "waitfornetworkidle", "wait_for_network_idle", "waitfornetwork" ->
                        executeWaitForLoadStateByName(page, step);
                case "asserttext", "verify_text", "assertequals", "assert_text" ->
                        executeVerifyText(page, step);
                case "assertvisible", "verify_visible", "assert_visible", "assertelement" ->
                        executeVerifyVisible(page, step);
                case "screenshot" ->
                        log.debug("screenshot step — skipping (taken on failure)");
                // AI sometimes generates these no-op validation steps — skip gracefully
                case "checknotnull", "assertnotnull", "verify_not_null" ->
                        log.debug("checkNotNull step '{}' — treated as no-op", step.getLocator());
                // 'login' is a high-level concept, not an atomic step — skip with warning
                case "login" ->
                        log.warn("⚠️ 'login' is not a supported atomic action — skipping. " +
                                "Use 'type' for username/password fields and 'click' for the button.");
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
        // If no locator, treat as a sleep: value = milliseconds to wait
        if (step.getLocator() == null || step.getLocator().trim().isEmpty()) {
            int ms = 1000; // default 1s
            if (step.getValue() != null) {
                try { ms = Integer.parseInt(step.getValue().trim()); } catch (NumberFormatException ignored) {}
            } else if (step.getTimeout() != null && step.getTimeout() > 0) {
                ms = step.getTimeout() * 1000;
            }
            log.debug("wait (sleep) {}ms", ms);
            try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return;
        }
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
    /**
     * Waits for the page to finish loading.
     *
     * Playwright's waitForLoadState() blocks until the requested state is reached.
     * Default is "load" (fires when all resources including images are done).
     * "networkidle" waits until no network requests for 500ms — better for SPAs.
     *
     * The step's locator value can specify the state: "networkidle", "domcontentloaded", or "load".
     * Defaults to "load" if unspecified.
     */
    /**
     * Derives the load state from the action name itself rather than the locator value.
     * Used for AI-generated actions like "waitForNetworkIdle" and "waitForPageLoad"
     * where the desired state is encoded in the action name, not the step parameters.
     */
    private void executeWaitForLoadStateByName(Page page, TestStep step) {
        String actionLower = step.getAction().toLowerCase();
        com.microsoft.playwright.options.LoadState state;

        if (actionLower.contains("networkidle") || actionLower.contains("network")) {
            state = com.microsoft.playwright.options.LoadState.NETWORKIDLE;
        } else if (actionLower.contains("domcontent") || actionLower.contains("dom")) {
            state = com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED;
        } else {
            state = com.microsoft.playwright.options.LoadState.LOAD; // default for waitForPageLoad
        }

        int timeoutMs = step.getTimeout() != null && step.getTimeout() > 0
                ? step.getTimeout() * 1000
                : 30_000;

        log.debug("waitForLoadState (from action name): state={}, timeout={}ms", state, timeoutMs);
        page.waitForLoadState(state, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
    }

    private void executeWaitForLoadState(Page page, TestStep step) {
        String stateStr = step.getLocator() != null ? step.getLocator().trim().toLowerCase() : "load";

        com.microsoft.playwright.options.LoadState state;
        switch (stateStr) {
            case "networkidle" -> state = com.microsoft.playwright.options.LoadState.NETWORKIDLE;
            case "domcontentloaded" -> state = com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED;
            default -> state = com.microsoft.playwright.options.LoadState.LOAD;
        }

        int timeoutMs = step.getTimeout() != null && step.getTimeout() > 0
                ? step.getTimeout() * 1000
                : 30_000; // 30s default

        log.debug("waitForLoadState: state={}, timeout={}ms", state, timeoutMs);
        page.waitForLoadState(state, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
    }


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

        if (locatorString.contains("=")) {
            // Format: "type=value" (e.g., "css=#btn", "xpath=//button")
            // BUT: CSS attribute selectors like [data-test='foo'] also contain '='
            // so we only split if the prefix is a known strategy keyword.
            // If it isn't, treat the whole string as a CSS selector.
            String[] parts = locatorString.split("=", 2);
            String candidateStrategy = parts[0].trim();
            try {
                strategy = LocatorStrategy.fromString(candidateStrategy);
                value = parts[1].trim();
            } catch (IllegalArgumentException ignored) {
                // Not a strategy prefix — it's a raw CSS selector (e.g. [data-test='foo'])
                strategy = LocatorStrategy.CSS;
                value = locatorString.trim();
            }
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