package com.company.qa.controller;

import com.company.qa.service.execution.PlaywrightFactory;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for manual PlaywrightFactory verification.
 *
 * WARNING: This is for testing only. Remove or disable in production.
 *
 * @author QA Framework
 * @since Week 11 Day 2
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/playwright/test")
@RequiredArgsConstructor
public class PlaywrightTestController {

    private final PlaywrightFactory factory;

    /**
     * Check if Playwright is enabled and available.
     *
     * GET /api/v1/playwright/test/status
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", factory.isEnabled());
        response.put("availableBrowsers", factory.getAvailableBrowsers());
        response.put("message", "Playwright is ready");
        return response;
    }

    /**
     * Simple test - navigate to URL and capture screenshot.
     *
     * GET /api/v1/playwright/test/simple?url=https://example.com
     */
    @GetMapping("/simple")
    public Map<String, Object> simpleTest(
            @RequestParam(defaultValue = "https://example.com") String url) {

        log.info("Running simple Playwright test for: {}", url);

        Browser browser = null;
        BrowserContext context = null;

        try {
            // Create browser and context
            browser = factory.createBrowser();
            context = factory.createContext(browser, "manual-test");
            Page page = factory.createPage(context);

            // Navigate
            page.navigate(url);
            page.waitForLoadState();

            // Capture screenshot
            Path screenshot = factory.captureScreenshot(page, "manual-test");

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", page.url());
            response.put("title", page.title());
            response.put("screenshot", screenshot != null ? screenshot.toString() : null);
            response.put("browser", browser.browserType().name());

            log.info("Test completed successfully");
            return response;

        } catch (Exception e) {
            log.error("Test failed", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;

        } finally {
            // Always cleanup
            if (context != null) {
                factory.closeContext(context);
            }
            if (browser != null) {
                factory.closeBrowser(browser);
            }
        }
    }
}