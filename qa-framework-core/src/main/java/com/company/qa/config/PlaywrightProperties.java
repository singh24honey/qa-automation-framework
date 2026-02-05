package com.company.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Playwright.
 * Binds to 'playwright' section in application.yml
 *
 * Example:
 * playwright:
 *   enabled: true
 *   browser: chromium
 *   headless: true
 *
 * @author QA Framework
 * @since Week 11 Day 2
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "playwright")
public class PlaywrightProperties {

    /**
     * Enable/disable Playwright framework
     */
    private boolean enabled = true;

    /**
     * Browser type: chromium, firefox, or webkit
     */
    private String browser = "chromium";

    /**
     * Run in headless mode (no GUI)
     */
    private boolean headless = true;

    /**
     * Default timeout in milliseconds
     */
    private int timeout = 30000;

    /**
     * Slow down operations by milliseconds (for debugging)
     */
    private int slowMo = 0;

    /**
     * Record video during execution
     */
    private boolean recordVideo = false;

    /**
     * Record trace for debugging
     */
    private boolean recordTrace = true;

    /**
     * Directory for screenshots
     */
    private String screenshotDir = System.getProperty("user.home") + "/qa-framework/artifacts/screenshots";

    /**
     * Directory for traces
     */
    private String traceDir = System.getProperty("user.home") + "/qa-framework/artifacts/traces";

    /**
     * Directory for videos
     */
    private String videoDir = System.getProperty("user.home") + "/qa-framework/artifacts/videos";

    /**
     * Viewport configuration
     */
    private ViewportConfig viewport = new ViewportConfig();

    /**
     * Accept downloads during test
     */
    private boolean acceptDownloads = true;

    /**
     * Ignore HTTPS errors (useful for testing with self-signed certs)
     */
    private boolean ignoreHttpsErrors = false;

    /**
     * Viewport dimensions
     */
    @Data
    public static class ViewportConfig {
        private int width = 1920;
        private int height = 1080;
    }

    /**
     * Get browser type as enum
     */
    public BrowserType getBrowserType() {
        return BrowserType.valueOf(browser.toUpperCase());
    }

    /**
     * Supported browser types
     */
    public enum BrowserType {
        CHROMIUM,
        FIREFOX,
        WEBKIT
    }
}