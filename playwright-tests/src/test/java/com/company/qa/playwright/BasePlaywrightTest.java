package com.company.qa.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Base class for all AI-generated and hand-written Playwright tests.
 *
 * ALL generated test classes extend this — it is the foundational contract
 * between the Zero-Hallucination renderer and the JUnit 5 runtime.
 *
 * Lifecycle (per class, shared across test methods for performance):
 *   @BeforeAll  → Playwright instance + Browser (heavyweight — created once)
 *   @BeforeEach → BrowserContext + Page (lightweight — fresh per test for isolation)
 *   @AfterEach  → Close context (clears cookies, localStorage, auth state)
 *   @AfterAll   → Close browser + Playwright instance
 *
 * Why class-scoped Playwright/Browser?
 *   Playwright.create() and browser.launch() are expensive (~1-3s each).
 *   BrowserContext is cheap (~50ms) and provides full test isolation.
 *   This matches the official Playwright Java recommendation for JUnit 5.
 *
 * Available fields in subclasses:
 *   - {@code page}    → the active Playwright Page for the current test
 *   - {@code context} → the active BrowserContext (for multi-tab tests)
 *   - {@code browser} → the shared Browser instance (for advanced use)
 *
 * Configuration (reads system properties / env vars with sane defaults):
 *   - PLAYWRIGHT_HEADLESS  → true/false  (default: true)
 *   - PLAYWRIGHT_BROWSER   → chromium/firefox/webkit  (default: chromium)
 *   - PLAYWRIGHT_TIMEOUT   → milliseconds  (default: 30000)
 *   - PLAYWRIGHT_SLOW_MO   → milliseconds  (default: 0)
 *   - PLAYWRIGHT_SCREENSHOT_DIR → path  (default: target/playwright-screenshots)
 *   - PLAYWRIGHT_TRACE_DIR      → path  (default: target/playwright-traces)
 *
 * Screenshot on failure:
 *   Captured automatically in {@code @AfterEach} when a test has failed.
 *   Stored in PLAYWRIGHT_SCREENSHOT_DIR/<testClassName>/<timestamp>.png
 *
 * Usage in generated tests:
 * <pre>
 * public class SCRUM16_LoginTest extends BasePlaywrightTest {
 *
 *     {@literal @}Test
 *     {@literal @}DisplayName("Standard user logs in successfully")
 *     public void testSuccessfulLogin() {
 *         page.navigate("https://www.saucedemo.com");
 *         page.getByTestId("username").fill("standard_user");
 *         page.getByTestId("password").fill("secret_sauce");
 *         page.getByTestId("login-button").click();
 *         assertThat(page).hasURL(java.util.regex.Pattern.compile(".*inventory.*"));
 *     }
 * }
 * </pre>
 *
 * @author QA Framework — Zero-Hallucination Pipeline
 * @since Week 2, Day 3
 */
@Slf4j
public abstract class BasePlaywrightTest {

    // ═══════════════════════════════════════════════════════════════════
    // Class-scoped (shared across all @Test methods in the class)
    // ═══════════════════════════════════════════════════════════════════

    /** Playwright runtime — created once per test class. */
    protected static Playwright playwright;

    /** Browser instance — created once per test class. */
    protected static Browser browser;

    // ═══════════════════════════════════════════════════════════════════
    // Method-scoped (fresh per @Test method for full isolation)
    // ═══════════════════════════════════════════════════════════════════

    /** BrowserContext — fresh per test, provides full isolation. */
    protected BrowserContext context;

    /** Page — the active tab for the current test. */
    protected Page page;

    // ═══════════════════════════════════════════════════════════════════
    // State tracking for screenshot-on-failure
    // ═══════════════════════════════════════════════════════════════════

    /** Track whether the current test has failed so AfterEach can screenshot. */
    private boolean testFailed = false;

    // ═══════════════════════════════════════════════════════════════════
    // Configuration helpers (read env/system properties)
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isHeadless() {
        String val = System.getenv("PLAYWRIGHT_HEADLESS");
        if (val == null) val = System.getProperty("playwright.headless", "true");
        return Boolean.parseBoolean(val);
    }

    private static String browserType() {
        String val = System.getenv("PLAYWRIGHT_BROWSER");
        // Default: firefox — matches PlaywrightProperties.browser default and the rest of the framework.
        // Override via env PLAYWRIGHT_BROWSER=chromium or -Dplaywright.browser=chromium
        if (val == null) val = System.getProperty("playwright.browser", "firefox");
        return val.toLowerCase().trim();
    }

    private static int defaultTimeout() {
        String val = System.getenv("PLAYWRIGHT_TIMEOUT");
        if (val == null) val = System.getProperty("playwright.timeout", "30000");
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 30000; }
    }

    private static int slowMo() {
        String val = System.getenv("PLAYWRIGHT_SLOW_MO");
        if (val == null) val = System.getProperty("playwright.slowMo", "0");
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }

    private static Path screenshotDir() {
        String val = System.getenv("PLAYWRIGHT_SCREENSHOT_DIR");
        if (val == null) val = System.getProperty("playwright.screenshotDir",
                "target/playwright-screenshots");
        return Paths.get(val);
    }

    private static Path traceDir() {
        String val = System.getenv("PLAYWRIGHT_TRACE_DIR");
        if (val == null) val = System.getProperty("playwright.traceDir",
                "target/playwright-traces");
        return Paths.get(val);
    }

    // ═══════════════════════════════════════════════════════════════════
    // @BeforeAll — Playwright + Browser (expensive, once per class)
    // ═══════════════════════════════════════════════════════════════════

    @BeforeAll
    static void launchBrowser() {
        log.info("Launching Playwright [browser={}, headless={}, slowMo={}]",
                browserType(), isHeadless(), slowMo());

        playwright = Playwright.create();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(isHeadless())
                .setSlowMo(slowMo());

        // Mirror the Apple Silicon + macOS args from PlaywrightFactory.createBrowser()
        // so generated tests behave identically to agent-executed tests on your machine.
        List<String> args = new java.util.ArrayList<>();
        String os   = System.getProperty("os.name",  "").toLowerCase();
        String arch = System.getProperty("os.arch",  "").toLowerCase();
        if (os.contains("mac")) {
            args.add("--disable-dev-shm-usage");
            args.add("--disable-blink-features=AutomationControlled");
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                log.info("Detected Apple Silicon — adding --disable-gpu --no-sandbox");
                args.add("--disable-gpu");
                args.add("--no-sandbox");
            }
        }
        if (!args.isEmpty()) {
            launchOptions.setArgs(args);
        }

        browser = switch (browserType()) {
            case "firefox"  -> playwright.firefox().launch(launchOptions);
            case "webkit"   -> playwright.webkit().launch(launchOptions);
            default         -> playwright.firefox().launch(launchOptions);
        };

        log.info("Browser launched: {}", browser.browserType().name());
    }

    // ═══════════════════════════════════════════════════════════════════
    // @BeforeEach — fresh BrowserContext + Page (cheap, per test)
    // ═══════════════════════════════════════════════════════════════════

    @BeforeEach
    void createContextAndPage() {
        testFailed = false;

        // Full-HD viewport by default — matches most CI environments
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setIgnoreHTTPSErrors(false);

        context = browser.newContext(contextOptions);

        // Set default timeout on the context so all locator operations inherit it
        context.setDefaultTimeout(defaultTimeout());

        page = context.newPage();

        log.debug("New BrowserContext + Page created [timeout={}ms]", defaultTimeout());
    }

    // ═══════════════════════════════════════════════════════════════════
    // @AfterEach — screenshot on failure, then close context
    // ═══════════════════════════════════════════════════════════════════

    @AfterEach
    void closeContext(org.junit.jupiter.api.TestInfo testInfo) {
        try {
            if (testFailed && page != null) {
                captureFailureScreenshot(testInfo);
            }
        } catch (Exception e) {
            log.warn("Failed to capture screenshot after test failure: {}", e.getMessage());
        } finally {
            if (context != null) {
                try { context.close(); } catch (Exception e) {
                    log.warn("Error closing BrowserContext: {}", e.getMessage());
                }
            }
            context = null;
            page    = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // @AfterAll — close browser + Playwright
    // ═══════════════════════════════════════════════════════════════════

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            try { browser.close(); } catch (Exception e) {
                log.warn("Error closing browser: {}", e.getMessage());
            }
            browser = null;
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception e) {
                log.warn("Error closing Playwright: {}", e.getMessage());
            }
            playwright = null;
        }
        log.info("Browser and Playwright closed.");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test failure hook — called by subclasses or JUnit extension
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Mark the current test as failed so {@code @AfterEach} captures a screenshot.
     *
     * Generated test classes do not need to call this — the JUnit 5
     * {@link org.junit.jupiter.api.extension.TestWatcher} extension below
     * calls it automatically when any assertion throws.
     *
     * Subclasses may also call this explicitly in a try-catch block.
     */
    protected void markTestFailed() {
        this.testFailed = true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Screenshot utility
    // ═══════════════════════════════════════════════════════════════════

    private void captureFailureScreenshot(org.junit.jupiter.api.TestInfo testInfo) {
        if (page == null) return;

        try {
            String className  = testInfo.getTestClass()
                    .map(Class::getSimpleName)
                    .orElse("UnknownTest");
            String methodName = testInfo.getTestMethod()
                    .map(java.lang.reflect.Method::getName)
                    .orElse("unknownMethod");
            String timestamp  = String.valueOf(Instant.now().toEpochMilli());
            String fileName   = className + "_" + methodName + "_" + timestamp + ".png";

            Path dir = screenshotDir().resolve(className);
            java.nio.file.Files.createDirectories(dir);

            Path screenshotPath = dir.resolve(fileName);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshotPath)
                    .setFullPage(true));

            log.info("📸 Failure screenshot saved: {}", screenshotPath);
        } catch (Exception e) {
            log.warn("Could not save failure screenshot: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // JUnit 5 TestWatcher — auto-marks test failed for screenshot
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Inner extension that auto-marks test failure so screenshots are captured.
     *
     * Registered via {@code @ExtendWith} on this class — subclasses inherit it
     * without any extra annotation. This is the "secret sauce" that makes
     * {@code markTestFailed()} fire automatically on any assertion error.
     */
    @org.junit.jupiter.api.extension.ExtendWith(BasePlaywrightTest.FailureWatcher.class)
    static class FailureWatcher implements org.junit.jupiter.api.extension.TestWatcher {

        @Override
        public void testFailed(org.junit.jupiter.api.extension.ExtensionContext context,
                               Throwable cause) {
            // Retrieve the test instance and mark it failed
            context.getTestInstance()
                    .filter(instance -> instance instanceof BasePlaywrightTest)
                    .map(instance -> (BasePlaywrightTest) instance)
                    .ifPresent(BasePlaywrightTest::markTestFailed);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Convenience navigation helpers (available to all generated tests)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Navigate to URL and wait for network to settle.
     * Use this instead of bare {@code page.navigate()} in generated tests
     * when you need the page fully loaded before continuing.
     */
    protected void navigateAndWait(String url) {
        page.navigate(url);
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        log.debug("Navigated and waited for network idle: {}", url);
    }

    /**
     * Sauce Demo login helper — used by generated tests that need to
     * start from an authenticated state without repeating login steps.
     *
     * @param username Sauce Demo username (e.g. "standard_user")
     * @param password Sauce Demo password (e.g. "secret_sauce")
     */
    protected void sauceDemoLogin(String username, String password) {
        page.navigate("https://www.saucedemo.com");
        page.getByTestId("username").fill(username);
        page.getByTestId("password").fill(password);
        page.getByTestId("login-button").click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        log.debug("Logged into Sauce Demo as: {}", username);
    }

    /**
     * Sauce Demo login with default test credentials.
     */
    protected void sauceDemoLogin() {
        sauceDemoLogin("standard_user", "secret_sauce");
    }
}