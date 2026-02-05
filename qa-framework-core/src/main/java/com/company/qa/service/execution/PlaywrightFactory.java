package com.company.qa.service.execution;

import com.company.qa.config.PlaywrightProperties;
import com.microsoft.playwright.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating and managing Playwright browser instances.
 *
 * Thread-safe. Uses browser contexts for test isolation.
 *
 * Usage:
 * <pre>
 * Browser browser = factory.createBrowser();
 * BrowserContext context = factory.createContext(browser, "test-123");
 * Page page = factory.createPage(context);
 *
 * // ... test actions ...
 *
 * factory.closeContext(context);
 * factory.closeBrowser(browser);
 * </pre>
 *
 * @author QA Framework
 * @since Week 11 Day 2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightFactory {

    private final PlaywrightProperties properties;

    // Singleton Playwright instance (expensive to create)
    private volatile Playwright playwright;

    /**
     * Get or create Playwright instance.
     * Thread-safe singleton pattern.
     *
     * @return Playwright instance
     */
    public synchronized Playwright getPlaywright() {
        if (playwright == null) {
            log.info("Creating Playwright instance");
            playwright = Playwright.create();
            log.info("Playwright instance created successfully");
        }
        return playwright;
    }

    /**
     * Create browser using configured browser type.
     *
     * @return Browser instance
     */
    public Browser createBrowser() {
        return createBrowser(properties.getBrowserType());
    }

    /**
     * Create browser of specific type.
     *
     * @param browserType CHROMIUM, FIREFOX, or WEBKIT
     * @return Browser instance
     */
    public Browser createBrowser(PlaywrightProperties.BrowserType browserType) {
        log.info("Creating {} browser (headless: {}, slowMo: {})",
                browserType, properties.isHeadless(), properties.getSlowMo());

        Playwright pw = getPlaywright();

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(properties.isHeadless())
                .setSlowMo(properties.getSlowMo())
                .setTimeout(properties.getTimeout());

        // Add macOS-specific args to prevent crashes
        List<String> args = new ArrayList<>();

        // Check if running on macOS
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            args.add("--disable-dev-shm-usage");
            args.add("--disable-blink-features=AutomationControlled");

            // For Apple Silicon Macs
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                log.info("Detected Apple Silicon Mac - adding compatibility args");
                args.add("--disable-gpu");
                args.add("--no-sandbox");
            }
        }

        if (!args.isEmpty()) {
            options.setArgs(args);
            log.debug("Browser args: {}", args);
        }

        Browser browser = switch (browserType) {
            case FIREFOX -> pw.firefox().launch(options);
            case WEBKIT -> pw.webkit().launch(options);
            default -> pw.chromium().launch(options);
        };

        log.info("Browser created: {}", browser.browserType().name());
        return browser;
    }
    /**
     * Create browser context with default settings.
     *
     * @param browser the browser to create context in
     * @return BrowserContext instance
     */
    public BrowserContext createContext(Browser browser) {
        return createContext(browser, null);
    }

    /**
     * Create browser context with execution ID for artifacts.
     *
     * A context is like an incognito window - isolated from other contexts.
     *
     * @param browser the browser
     * @param executionId unique ID for this test (used for artifact naming)
     * @return BrowserContext instance
     */
    public BrowserContext createContext(Browser browser, String executionId) {
        log.debug("Creating browser context (executionId: {})", executionId);

        // Configure context options
        Browser.NewContextOptions options = new Browser.NewContextOptions();

        // Set viewport size
        options.setViewportSize(
                properties.getViewport().getWidth(),
                properties.getViewport().getHeight()
        );

        // Video recording (if enabled and has execution ID)
        if (properties.isRecordVideo() && executionId != null) {
            Path videoDir = Paths.get(properties.getVideoDir(), executionId);
            ensureDirectoryExists(videoDir);
            options.setRecordVideoDir(videoDir);
            log.debug("Video recording enabled: {}", videoDir);
        }

        // Other options
        options.setAcceptDownloads(properties.isAcceptDownloads());
        options.setIgnoreHTTPSErrors(properties.isIgnoreHttpsErrors());

        // Create context
        BrowserContext context = browser.newContext(options);

        // Set default timeout
        context.setDefaultTimeout(properties.getTimeout());

        // Start trace recording if enabled
        if (properties.isRecordTrace()) {
            startTrace(context);
        }

        log.debug("Browser context created");
        return context;
    }

    /**
     * Create a new page in the context.
     *
     * @param context the browser context
     * @return Page instance
     */
    public Page createPage(BrowserContext context) {
        log.debug("Creating new page");
        Page page = context.newPage();
        page.setDefaultTimeout(properties.getTimeout());
        log.debug("Page created");
        return page;
    }

    /**
     * Capture screenshot of current page.
     *
     * @param page the page to screenshot
     * @param name filename (without extension)
     * @return Path to saved screenshot, or null if failed
     */
    public Path captureScreenshot(Page page, String name) {
        try {
            // Ensure screenshot directory exists
            Path dir = Paths.get(properties.getScreenshotDir());
            ensureDirectoryExists(dir);

            // Create screenshot path
            Path screenshotPath = dir.resolve(name + ".png");

            // Capture full page screenshot
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshotPath)
                    .setFullPage(true));

            log.info("Screenshot saved: {}", screenshotPath);
            return screenshotPath;

        } catch (Exception e) {
            log.error("Failed to capture screenshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Capture screenshot with execution ID and step number.
     *
     * @param page the page
     * @param executionId test execution ID
     * @param stepNumber step number
     * @return Path to screenshot
     */
    public Path captureScreenshot(Page page, String executionId, int stepNumber) {
        String filename = String.format("%s_step_%d", executionId, stepNumber);
        return captureScreenshot(page, filename);
    }

    /**
     * Start trace recording on context.
     * Traces are useful for debugging test failures.
     *
     * @param context the browser context
     */
    public void startTrace(BrowserContext context) {
        try {
            context.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)   // Include screenshots
                    .setSnapshots(true)     // Include DOM snapshots
                    .setSources(true));     // Include source code

            log.debug("Trace recording started");
        } catch (Exception e) {
            log.warn("Failed to start trace: {}", e.getMessage());
        }
    }

    /**
     * Stop trace recording and save to file.
     *
     * @param context the browser context
     * @param executionId unique ID for trace file
     * @return Path to saved trace file
     */
    public Path stopTrace(BrowserContext context, String executionId) {
        try {
            // Ensure trace directory exists
            Path dir = Paths.get(properties.getTraceDir());
            ensureDirectoryExists(dir);

            // Create trace path
            Path tracePath = dir.resolve(executionId + ".zip");

            // Stop and save trace
            context.tracing().stop(new Tracing.StopOptions()
                    .setPath(tracePath));

            log.info("Trace saved: {}", tracePath);
            return tracePath;

        } catch (Exception e) {
            log.warn("Failed to stop trace: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Close browser context gracefully.
     *
     * @param context the context to close
     */
    public void closeContext(BrowserContext context) {
        if (context != null) {
            try {
                log.debug("Closing browser context");
                context.close();
                log.debug("Browser context closed");
            } catch (Exception e) {
                log.error("Error closing context: {}", e.getMessage());
            }
        }
    }

    /**
     * Close browser gracefully.
     *
     * @param browser the browser to close
     */
    public void closeBrowser(Browser browser) {
        if (browser != null) {
            try {
                log.info("Closing browser");
                browser.close();
                log.info("Browser closed");
            } catch (Exception e) {
                log.error("Error closing browser: {}", e.getMessage());
            }
        }
    }

    /**
     * Cleanup Playwright instance on application shutdown.
     * Automatically called by Spring.
     */
    @PreDestroy
    public void cleanup() {
        if (playwright != null) {
            synchronized (this) {
                if (playwright != null) {
                    log.info("Closing Playwright instance");
                    playwright.close();
                    playwright = null;
                    log.info("Playwright closed");
                }
            }
        }
    }

    /**
     * Check if Playwright is enabled in configuration.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Get list of available browser types.
     *
     * @return array of browser names
     */
    public String[] getAvailableBrowsers() {
        return new String[]{"CHROMIUM", "FIREFOX", "WEBKIT"};
    }

    /**
     * Ensure directory exists, create if needed.
     *
     * @param path directory path
     */
    private void ensureDirectoryExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.debug("Created directory: {}", path);
            }
        } catch (Exception e) {
            log.warn("Failed to create directory {}: {}", path, e.getMessage());
        }
    }
}