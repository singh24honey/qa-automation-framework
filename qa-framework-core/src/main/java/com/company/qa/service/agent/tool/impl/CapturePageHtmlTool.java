package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.execution.PlaywrightFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to capture page HTML for AI analysis.
 *
 * Navigates to the page where failure occurred and captures full HTML.
 * Returns relevant HTML section for locator discovery.
 *
 * Input parameters:
 * - pageUrl: URL of the page
 * - selectorContext: Optional CSS selector to limit HTML to relevant section
 *
 * Output:
 * - success: true/false
 * - fullHtml: Complete page HTML
 * - relevantHtml: Filtered HTML (if selectorContext provided)
 * - htmlLength: Size of HTML captured
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapturePageHtmlTool implements AgentTool {

    private final PlaywrightFactory playwrightFactory;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.READ_FILE; // Using READ_FILE for page content
    }

    @Override
    public String getName() {
        return "Page HTML Capture Tool";
    }

    @Override
    public String getDescription() {
        return "Captures page HTML for AI analysis. " +
                "Navigates to page and extracts DOM structure. " +
                "Can filter to relevant section using selector.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("📸 Capturing page HTML for AI analysis");

        Browser browser = null;
        BrowserContext context = null;

        try {
            String pageUrl = (String) parameters.get("pageUrl");
            String selectorContext = (String) parameters.getOrDefault("selectorContext", "body");

            log.info("Navigating to: {}", pageUrl);

            // Create browser
            browser = playwrightFactory.createBrowser();
            context = playwrightFactory.createContext(browser, "html-capture");
            Page page = playwrightFactory.createPage(context);

            // Navigate to page
            page.navigate(pageUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Capture full HTML
            String fullHtml = page.content();

            // Capture relevant section if selector provided
            String relevantHtml = fullHtml;
            if (selectorContext != null && !selectorContext.equals("body")) {
                try {
                    ElementHandle element = page.querySelector(selectorContext);
                    if (element != null) {
                        relevantHtml = (String) element.evaluate("el => el.outerHTML");
                    }
                } catch (Exception e) {
                    log.warn("Could not extract section {}, using full HTML", selectorContext);
                }
            }

            // Limit HTML size for AI (max 50KB to keep token cost down)
            if (relevantHtml.length() > 50000) {
                log.warn("HTML too large ({} chars), truncating to 50KB", relevantHtml.length());
                relevantHtml = relevantHtml.substring(0, 50000);
            }

            log.info("✅ Captured {} chars of HTML", relevantHtml.length());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("fullHtml", fullHtml);
            result.put("relevantHtml", relevantHtml);
            result.put("htmlLength", relevantHtml.length());
            result.put("pageUrl", pageUrl);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to capture page HTML: {}", e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;

        } finally {
            if (context != null) {
                playwrightFactory.closeContext(context);
            }
            if (browser != null) {
                playwrightFactory.closeBrowser(browser);
            }
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null && parameters.containsKey("pageUrl");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("pageUrl", "string (required) - URL of the page to capture");
        schema.put("selectorContext", "string (optional) - CSS selector to limit HTML (default: body)");
        return schema;
    }
}