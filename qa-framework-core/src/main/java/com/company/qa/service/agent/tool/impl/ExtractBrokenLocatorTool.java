package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool to extract broken locator information from test failure.
 *
 * Analyzes error messages to identify:
 * - Which locator failed
 * - Which page the failure occurred on
 * - What element was being targeted
 *
 * Input parameters:
 * - errorMessage: Failure error message
 * - testContent: Test code content (JSON steps)
 * - failedStepIndex: Which step failed (optional)
 *
 * Output:
 * - success: true/false
 * - brokenLocator: The locator that failed (e.g., "css=#submit-btn")
 * - locatorStrategy: Strategy used (css, xpath, role, etc.)
 * - locatorValue: Value part of locator
 * - pageName: Inferred page name
 * - elementPurpose: What the element does (e.g., "submit button")
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractBrokenLocatorTool implements AgentTool {

    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;

    // Common error patterns for element not found
    private static final List<Pattern> ERROR_PATTERNS = List.of(
            Pattern.compile("Element not found: ([^\\s]+)"),
            Pattern.compile("Locator.*?(['\"]([^'\"]+)['\"]).*?not found"),
            Pattern.compile("Cannot find element with locator: ([^\\s]+)"),
            Pattern.compile("Timeout.*?waiting for locator ([^\\s]+)"),
            Pattern.compile("No element matches selector ([^\\s]+)")
    );

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.EXTRACT_BROKEN_LOCATOR;
    }

    @Override
    public String getName() {
        return "Broken Locator Extractor";
    }

    @Override
    public String getDescription() {
        return "Extracts broken locator information from test failure. " +
                "Identifies which locator failed and on which page. " +
                "Returns structured data for locator resolution.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("🔍 Extracting broken locator from failure");

        try {
            String errorMessage = (String) parameters.get("errorMessage");
            String testContent = (String) parameters.get("testContent");
            Integer failedStepIndex = parameters.containsKey("failedStepIndex")
                    ? ((Number) parameters.get("failedStepIndex")).intValue()
                    : null;

            // Attempt 1: extract locator from error message using regex patterns
            String brokenLocator = extractLocatorFromError(errorMessage);

            // Attempt 2: if error message is vague (e.g. just "Element not found"),
            // scan test content for the first locator-looking string.
            // This lets the agent proceed to AI discovery rather than looping forever.
            boolean extractedFromContent = false;
            if (brokenLocator == null && testContent != null && !testContent.isBlank()) {
                brokenLocator = extractFirstLocatorFromTestContent(testContent);
                extractedFromContent = (brokenLocator != null);
                if (extractedFromContent) {
                    log.info("⚠️ Error message too vague, using first locator from test content: {}",
                            brokenLocator);
                }
            }

            // Even if we still have nothing, return success=true with a sentinel value
            // so the planner can advance to AI discovery rather than looping.
            if (brokenLocator == null) {
                log.warn("⚠️ No locator found in error or content — proceeding with UNKNOWN sentinel");
                brokenLocator = "UNKNOWN";
                extractedFromContent = false;
            }

            // Parse locator strategy and value
            LocatorInfo locatorInfo = parseLocator(brokenLocator);

            // Parse test content to find context
            Map<String, Object> testContext = parseTestContext(testContent, failedStepIndex);

            // Build result — always return success=true so agent can advance
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("brokenLocator", brokenLocator);
            result.put("locatorStrategy", locatorInfo.strategy);
            result.put("locatorValue", locatorInfo.value);
            result.put("pageName", testContext.get("pageName"));
            result.put("elementPurpose", testContext.get("elementPurpose"));
            result.put("actionType", testContext.get("actionType"));
            result.put("extractedFromContent", extractedFromContent);
            result.put("originalErrorMessage", errorMessage);

            log.info("✅ Extracted broken locator: {} (from {}) on page: {}",
                    brokenLocator,
                    extractedFromContent ? "test content" : "error message",
                    testContext.get("pageName"));

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to extract broken locator: {}", e.getMessage(), e);

            // Even on exception, return success=true with UNKNOWN so agent doesn't loop
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("brokenLocator", "UNKNOWN");
            result.put("locatorStrategy", "unknown");
            result.put("locatorValue", "unknown");
            result.put("extractedFromContent", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Scan test content for the first locator-like string.
     *
     * Called when error message is too vague to identify which locator broke.
     * Looks for common patterns: css=, #id, .class, xpath=, [data-testid=, role=
     */
    private String extractFirstLocatorFromTestContent(String testContent) {
        // Patterns to find locators embedded in test content (JSON or plain)
        List<Pattern> contentPatterns = List.of(
                Pattern.compile("\"locator\"\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("\"selector\"\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("css=([^\\s,\"']+)"),
                Pattern.compile("(#[\\w-]+)"),
                Pattern.compile("(\\.[\\w-]+)"),
                Pattern.compile("xpath=([^\\s,\"']+)"),
                Pattern.compile("\\[data-testid=[\"']([^\"']+)[\"']\\]"),
                Pattern.compile("role=([^\\s,\"']+)")
        );
        for (Pattern p : contentPatterns) {
            Matcher m = p.matcher(testContent);
            if (m.find() && m.groupCount() >= 1) {
                String candidate = m.group(1).trim();
                if (!candidate.isBlank() && candidate.length() > 1) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null &&
                parameters.containsKey("errorMessage") &&
                parameters.containsKey("testContent");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("errorMessage", "string (required) - Test failure error message");
        schema.put("testContent", "string (required) - Test code content (JSON)");
        schema.put("failedStepIndex", "integer (optional) - Index of failed step");
        return schema;
    }

    /**
     * Extract locator from error message using patterns.
     */
    private String extractLocatorFromError(String errorMessage) {
        for (Pattern pattern : ERROR_PATTERNS) {
            Matcher matcher = pattern.matcher(errorMessage);
            if (matcher.find()) {
                // Try to get from first capturing group
                if (matcher.groupCount() >= 1) {
                    String locator = matcher.group(1);
                    // Clean up quotes if present
                    return locator.replaceAll("['\"]", "");
                }
            }
        }
        return null;
    }

    /**
     * Parse locator into strategy and value.
     */
    private LocatorInfo parseLocator(String locator) {
        LocatorInfo info = new LocatorInfo();

        // CSS selector
        if (locator.startsWith("css=") || locator.startsWith("#") || locator.startsWith(".")) {
            info.strategy = "css";
            info.value = locator.replaceFirst("^css=", "");
        }
        // XPath
        else if (locator.startsWith("xpath=") || locator.startsWith("//") || locator.startsWith("(//")) {
            info.strategy = "xpath";
            info.value = locator.replaceFirst("^xpath=", "");
        }
        // Role-based
        else if (locator.startsWith("role=")) {
            info.strategy = "role";
            info.value = locator.substring(5);
        }
        // Test ID
        else if (locator.contains("data-testid") || locator.startsWith("[data-testid")) {
            info.strategy = "testid";
            info.value = locator;
        }
        // Text-based
        else if (locator.startsWith("text=")) {
            info.strategy = "text";
            info.value = locator.substring(5);
        }
        // Placeholder
        else if (locator.startsWith("placeholder=")) {
            info.strategy = "placeholder";
            info.value = locator.substring(12);
        }
        // Label
        else if (locator.startsWith("label=")) {
            info.strategy = "label";
            info.value = locator.substring(6);
        }
        // Default to CSS
        else {
            info.strategy = "css";
            info.value = locator;
        }

        return info;
    }

    /**
     * Parse test content to get context about the broken step.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTestContext(String testContent, Integer failedStepIndex) {
        Map<String, Object> context = new HashMap<>();

        try {
            // Parse test content JSON
            Map<String, Object> contentMap = objectMapper.readValue(testContent, Map.class);
            List<Map<String, Object>> steps = (List<Map<String, Object>>) contentMap.get("steps");

            if (steps == null || steps.isEmpty()) {
                context.put("pageName", "unknown");
                context.put("elementPurpose", "unknown");
                context.put("actionType", "unknown");
                return context;
            }

            // Find failed step or last step
            Map<String, Object> failedStep = failedStepIndex != null && failedStepIndex < steps.size()
                    ? steps.get(failedStepIndex)
                    : steps.get(steps.size() - 1);

            // Infer page name from previous navigation
            String pageName = "unknown";
            for (int i = steps.indexOf(failedStep) - 1; i >= 0; i--) {
                Map<String, Object> step = steps.get(i);
                if ("navigate".equals(step.get("action"))) {
                    String url = (String) step.get("value");
                    pageName = inferPageNameFromUrl(url);
                    break;
                }
            }

            // Get element purpose from action
            String action = (String) failedStep.get("action");
            String locator = (String) failedStep.get("locator");
            String elementPurpose = inferElementPurpose(action, locator);

            context.put("pageName", pageName);
            context.put("elementPurpose", elementPurpose);
            context.put("actionType", action);

        } catch (Exception e) {
            log.warn("Failed to parse test context: {}", e.getMessage());
            context.put("pageName", "unknown");
            context.put("elementPurpose", "unknown");
            context.put("actionType", "unknown");
        }

        return context;
    }

    /**
     * Infer page name from URL.
     */
    private String inferPageNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "unknown";
        }

        // Common patterns
        if (url.contains("/login")) return "LoginPage";
        if (url.contains("/dashboard")) return "DashboardPage";
        if (url.contains("/inventory")) return "InventoryPage";
        if (url.contains("/cart")) return "CartPage";
        if (url.contains("/checkout")) return "CheckoutPage";
        if(url.equals("https://www.saucedemo.com")) return "LoginPage";

        // Try to extract last path segment
        try {
            String[] parts = url.split("/");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (!lastPart.isEmpty() && !lastPart.contains(".")) {
                    return lastPart;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return "unknown";
    }

    /**
     * Infer element purpose from action and locator.
     */
    private String inferElementPurpose(String action, String locator) {
        if (locator == null) {
            return action != null ? action : "unknown";
        }

        String lower = locator.toLowerCase();

        // Common element types
        if (lower.contains("submit") || lower.contains("login-button")) {
            return "submit button";
        }
        if (lower.contains("username") || lower.contains("user-name") || lower.contains("email")) {
            return "username input";
        }
        if (lower.contains("password")) {
            return "password input";
        }
        if (lower.contains("search")) {
            return "search input";
        }
        if (lower.contains("add-to-cart")) {
            return "add to cart button";
        }
        if (lower.contains("checkout")) {
            return "checkout button";
        }

        return action != null ? action + " element" : "unknown element";
    }

    /**
     * Helper class for locator info.
     */
    private static class LocatorInfo {
        String strategy;
        String value;
    }
}