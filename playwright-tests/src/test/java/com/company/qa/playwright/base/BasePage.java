package com.company.qa.playwright.base;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for all Playwright Page Object Model classes.
 * Provides common functionality for page interactions.
 *
 * @author QA Framework
 * @since Week 11 - Playwright Integration
 */
@Slf4j
@Getter
public abstract class BasePage {

    protected final Page page;

    public BasePage(Page page) {
        this.page = page;
        log.debug("Initialized {} for URL: {}", this.getClass().getSimpleName(), page.url());
    }

    /**
     * Navigate to the page URL.
     * Override this method in subclasses to define page-specific URLs.
     */
    public abstract void navigate();

    /**
     * Get the expected URL pattern for this page.
     * Used for navigation verification.
     */
    public abstract String getExpectedUrlPattern();

    /**
     * Wait for the page to be fully loaded.
     * Override in subclasses for page-specific load conditions.
     */
    public void waitForPageLoad() {
        page.waitForLoadState();
        log.debug("Page loaded: {}", page.url());
    }

    /**
     * Check if the page is displayed by verifying URL pattern.
     *
     * @return true if current URL matches expected pattern
     */
    public boolean isDisplayed() {
        String currentUrl = page.url();
        String expectedPattern = getExpectedUrlPattern();
        boolean matches = currentUrl.matches(expectedPattern);

        log.debug("URL verification - Current: {}, Expected pattern: {}, Matches: {}",
                currentUrl, expectedPattern, matches);

        return matches;
    }

    /**
     * Take a screenshot and return the path.
     *
     * @param screenshotName name for the screenshot file
     * @return path to the saved screenshot
     */
    public Path takeScreenshot(String screenshotName) {
        Path screenshotPath = Paths.get("test-results/screenshots/" + screenshotName + ".png");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshotPath)
                .setFullPage(true));

        log.info("Screenshot saved: {}", screenshotPath);
        return screenshotPath;
    }

    /**
     * Wait for an element to be visible.
     *
     * @param selector the element selector
     */
    protected void waitForElement(String selector) {
        waitForElement(selector, WaitForSelectorState.VISIBLE);
    }

    /**
     * Wait for an element with specific state.
     *
     * @param selector the element selector
     * @param state the expected state
     */
    protected void waitForElement(String selector, WaitForSelectorState state) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions().setState(state));
        log.debug("Element ready: {} (state: {})", selector, state);
    }

    /**
     * Get the page title.
     *
     * @return current page title
     */
    public String getTitle() {
        return page.title();
    }

    /**
     * Get the current URL.
     *
     * @return current page URL
     */
    public String getCurrentUrl() {
        return page.url();
    }

    /**
     * Refresh the current page.
     */
    public void refresh() {
        log.debug("Refreshing page: {}", page.url());
        page.reload();
    }
}