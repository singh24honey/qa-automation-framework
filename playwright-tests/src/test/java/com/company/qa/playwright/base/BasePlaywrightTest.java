package com.company.qa.playwright.base;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * Base test class for all Playwright tests.
 * Handles browser lifecycle management.
 *
 * Configuration can be controlled via system properties:
 * - playwright.browser: chromium (default), firefox, or webkit
 * - playwright.headless: true (default) or false
 * - playwright.slowMo: milliseconds to slow down operations (default: 0)
 * - playwright.video: enable video recording (default: false)
 * - playwright.trace: enable tracing (default: false)
 *
 * @author QA Framework
 * @since Week 11 - Playwright Integration
 */
//@Slf4j
public abstract class BasePlaywrightTest {

    // Class-level (shared across all tests in the class)
    protected static Playwright playwright;

    private static final Logger log = LoggerFactory.getLogger(BasePlaywrightTest.class);

    protected static Browser browser;

    // Instance-level (new for each test method)
    protected BrowserContext context;
    protected Page page;

    // Configuration
    private static final String BROWSER_TYPE = System.getProperty("playwright.browser", "chromium");
    private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
    private static final int SLOW_MO = Integer.parseInt(System.getProperty("playwright.slowMo", "0"));
    private static final boolean RECORD_VIDEO = Boolean.parseBoolean(System.getProperty("playwright.video", "false"));
    private static final boolean RECORD_TRACE = Boolean.parseBoolean(System.getProperty("playwright.trace", "false"));

    @BeforeAll
    static void launchBrowser() {
        log.info("Launching Playwright browser: {} (headless: {})", BROWSER_TYPE, HEADLESS);

        playwright = Playwright.create();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(HEADLESS)
                .setSlowMo(SLOW_MO);

        browser = switch (BROWSER_TYPE.toLowerCase()) {
            case "firefox" -> playwright.firefox().launch(launchOptions);
            case "webkit" -> playwright.webkit().launch(launchOptions);
            default -> playwright.chromium().launch(launchOptions);
        };

        log.info("Browser launched successfully: {}", browser.browserType().name());
    }

    @BeforeEach
    void createContextAndPage() {
        log.debug("Creating new browser context and page");

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();

        // Video recording configuration
        if (RECORD_VIDEO) {
            contextOptions.setRecordVideoDir(Paths.get("test-results/videos"));
            log.debug("Video recording enabled");
        }

        // Viewport configuration
        contextOptions.setViewportSize(1920, 1080);

        context = browser.newContext(contextOptions);

        // Enable tracing if configured
        if (RECORD_TRACE) {
            context.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(true));
            log.debug("Tracing enabled");
        }

        page = context.newPage();

        log.debug("Browser context and page created successfully");
    }

    @AfterEach
    void closeContextAndPage() {
        log.debug("Closing browser context and page");

        // Stop tracing and save if enabled
        if (RECORD_TRACE && context != null) {
            String testName = getTestName();
            context.tracing().stop(new Tracing.StopOptions()
                    .setPath(Paths.get("test-results/traces/" + testName + ".zip")));
            log.debug("Trace saved for test: {}", testName);
        }

        if (context != null) {
            context.close();
        }

        log.debug("Browser context closed");
    }

    @AfterAll
    static void closeBrowser() {
        log.info("Closing Playwright browser");

        if (browser != null) {
            browser.close();
        }

        if (playwright != null) {
            playwright.close();
        }

        log.info("Browser and Playwright closed successfully");
    }

    /**
     * Get the current test name.
     * Override in subclasses if needed for custom naming.
     *
     * @return test name for file naming
     */
    protected String getTestName() {
        return this.getClass().getSimpleName() + "_" + System.currentTimeMillis();
    }

    /**
     * Navigate to a URL.
     *
     * @param url the URL to navigate to
     */
    protected void navigateTo(String url) {
        log.debug("Navigating to: {}", url);
        page.navigate(url);
        page.waitForLoadState();
    }
}