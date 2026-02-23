package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.intent.IntentActionType;
import com.company.qa.model.intent.IntentTestStep;
import com.company.qa.service.agent.TestContentResolver;
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
    private final TestContentResolver contentResolver;


    // ─── AFTER ───────────────────────────────────────────────────────────────────
// All patterns compiled with DOTALL so .*? crosses newlines in multi-line
// Playwright stack traces. Order matters — most specific first.
    private static final List<Pattern> ERROR_PATTERNS = List.of(

            // ✅ Playwright: "- waiting for locator(".inventory_item:nth-child(1).btn_inventory")"
            // This is the most reliable signal — always present in Playwright timeout errors
            Pattern.compile(
                    "waiting for locator\\([\"']?([^\"')]+)[\"']?\\)",
                    Pattern.DOTALL),

            // Playwright: "waiting for locator('.selector')" — single-quote variant
            Pattern.compile(
                    "waiting for locator\\('([^']+)'\\)",
                    Pattern.DOTALL),

            // Playwright: "Timeout waiting for selector '.selector'"
            Pattern.compile(
                    "Timeout.*?waiting for selector [\"']?([^\"'\\s]+)[\"']?",
                    Pattern.DOTALL),

            // Generic: "Element not found: #selector"
            Pattern.compile(
                    "Element not found: ([^\\s\\n]+)",
                    Pattern.DOTALL),

            // Generic: "Cannot find element with locator: #selector"
            Pattern.compile(
                    "Cannot find element with locator: ([^\\s\\n]+)",
                    Pattern.DOTALL),

            // Generic: Locator "..." not found
            Pattern.compile(
                    "Locator.*?['\"]([^'\"]+)['\"].*?not found",
                    Pattern.DOTALL),

            // Generic: "No element matches selector #selector"
            Pattern.compile(
                    "No element matches selector ([^\\s\\n]+)",
                    Pattern.DOTALL)
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
// ✅ FIXED — knownBrokenLocator short-circuits everything else
            String knownBrokenLocator = (String) parameters.get("knownBrokenLocator");
            String brokenLocator;
            boolean extractedFromContent = false;

            if (knownBrokenLocator != null && !knownBrokenLocator.isBlank()) {
                // Ground truth from VerifyFixTool — no regex, no guessing needed
                brokenLocator = knownBrokenLocator;
                log.info("✅ Using known broken locator from VerifyFixTool: {}", brokenLocator);
            } else {
                // Attempt 1: extract from error message using DOTALL-aware patterns
                // Playwright always emits: "waiting for locator("...")" — this is the ground truth
                brokenLocator = extractLocatorFromError(errorMessage);

                // Attempt 2: UNKNOWN sentinel
                // Do NOT guess from test content — the "last defined step" ≠ "last executed step"
                // A test failing at step 5 has steps 6-17 defined but never run.
                // Returning step 17's locator as "broken" actively misleads the AI.
                // UNKNOWN lets the agent proceed to AI discovery with correct page HTML.
                if (brokenLocator == null) {
                    log.warn("⚠️ Could not extract locator from error message — using UNKNOWN sentinel. " +
                            "Error was: {}", errorMessage != null ? errorMessage.substring(0, Math.min(200, errorMessage.length())) : "null");
                    brokenLocator = "UNKNOWN";
                }
            }
            // Parse locator strategy and value
            LocatorInfo locatorInfo = parseLocator(brokenLocator);

            // Parse test content to find context
            Map<String, Object> testContext = parseTestContext(testContent, brokenLocator);

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

    /**
     * Fallback when error message is too vague to identify the broken locator.
     *
     * <p>Instead of scanning the whole JSON (which always returns the FIRST locator
     * — typically {@code testid=username} — regardless of which step actually failed),
     * this method extracts steps via {@link TestContentResolver} and returns the
     * locator of the LAST step with a non-null locator.
     *
     * <p>The last step is the correct fallback because:
     * <ul>
     *   <li>PlaywrightTestExecutor stops at the first failing step</li>
     *   <li>The last executed step is most likely the one that timed out</li>
     *   <li>This is consistent with {@code parseTestContext}'s no-index fallback</li>
     * </ul>
     *
     * @param testContent INTENT_V1 JSON from {@code Test.content}
     * @return locator string of the last step that has one, or {@code null}
     */
    private String extractLastLocatorFromTestContent(String testContent) {
        try {
            List<IntentTestStep> steps = contentResolver.getSteps(testContent);
            if (steps.isEmpty()) return null;

            // Walk backwards to find the last step that has a locator
            // (navigate, assertUrl, etc. have no locator — skip those)
            for (int i = steps.size() - 1; i >= 0; i--) {
                IntentTestStep step = steps.get(i);
                String locator = step.getLocator();
                if (locator != null && !locator.isBlank() && !"null".equals(locator)) {
                    log.debug("Fallback locator from last step [{}]: action={}, locator={}",
                            i, step.getAction(), locator);
                    return locator;
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract last locator from test content: {}", e.getMessage());
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
    private Map<String, Object> parseTestContext(String testContent,String brokenLocator) {
        Map<String, Object> context = new HashMap<>();

        try {
            List<IntentTestStep> steps = contentResolver.getSteps(testContent);

            if (steps.isEmpty()) {
                context.put("pageName", "unknown");
                context.put("elementPurpose", "unknown");
                context.put("actionType", "unknown");
                return context;
            }

            IntentTestStep failedStep = null;


            // Priority 2: find step by locator match (most reliable for first run)
            // The brokenLocator was extracted from the error message — ground truth
            if (failedStep == null && brokenLocator != null
                    && !brokenLocator.isBlank() && !"UNKNOWN".equals(brokenLocator)) {
                String normalizedBroken = stripLocatorPrefix(brokenLocator);
                for (IntentTestStep step : steps) {
                    if (step.getLocator() != null) {
                        String normalizedStep = stripLocatorPrefix(step.getLocator());
                        if (normalizedStep.equals(normalizedBroken)) {
                            failedStep = step;
                            log.debug("parseTestContext: found failing step by locator match: {} ({})",
                                    step.getAction(), step.getLocator());
                            break;
                        }
                    }
                }
            }

            // Priority 3: UNKNOWN — don't guess from last step
            if (failedStep == null) {
                log.warn("parseTestContext: cannot identify failing step — returning unknown context");
                context.put("pageName", "unknown");
                context.put("elementPurpose", "unknown");
                context.put("actionType", "unknown");
                return context;
            }

            // Infer page name — walk backward, prefer ASSERT_URL over NAVIGATE
            int failedIdx = steps.indexOf(failedStep);
            String pageName = "unknown";

            for (int i = failedIdx - 1; i >= 0; i--) {
                IntentTestStep step = steps.get(i);

                // ASSERT_URL is the most accurate: it's the URL the browser was verified on
                // most recently before the failing step
                if (step.getAction() == IntentActionType.ASSERT_URL && step.getValue() != null) {
                    pageName = inferPageNameFromUrlPattern(step.getValue());
                    if (!"unknown".equals(pageName)) break;
                }

                // NAVIGATE is a fallback — the browser may have redirected since
                if (step.getAction() == IntentActionType.NAVIGATE && step.getValue() != null) {
                    String candidate = inferPageNameFromUrl(step.getValue());
                    if (!"unknown".equals(candidate) && "unknown".equals(pageName)) {
                        pageName = candidate;
                        // Don't break — keep looking for a closer ASSERT_URL
                    }
                }
            }

            String actionType = failedStep.getAction() != null
                    ? failedStep.getAction().name() : "unknown";
            String elementPurpose = inferElementPurpose(actionType, failedStep.getLocator());

            context.put("pageName", pageName);
            context.put("elementPurpose", elementPurpose);
            context.put("actionType", actionType);

        } catch (Exception e) {
            log.warn("Failed to parse test context: {}", e.getMessage());
            context.put("pageName", "unknown");
            context.put("elementPurpose", "unknown");
            context.put("actionType", "unknown");
        }

        return context;
    }

    private String stripLocatorPrefix(String locator) {
        if (locator == null) return "";
        int eq = locator.indexOf('=');
        if (eq > 0 && eq < 12) {
            String prefix = locator.substring(0, eq).toLowerCase();
            if (Set.of("css", "xpath", "testid", "role", "text",
                    "id", "name", "class", "label", "placeholder").contains(prefix)) {
                return locator.substring(eq + 1);
            }
        }
        return locator;
    }

    /**
     * Infer page name from a full URL (NAVIGATE step value).
     */
    private String inferPageNameFromUrl(String url) {
        if (url == null || url.isBlank()) return "unknown";

        // Explicit root domain handling (no path or just "/")
        // "https://www.saucedemo.com" → LoginPage (root of saucedemo = login)
        if (url.matches("https?://[^/]+(/?|/index\\.html?)")) return "LoginPage";

        // Path-based matching (order matters — most specific first)
        if (url.contains("/inventory")) return "InventoryPage";
        if (url.contains("/cart"))      return "CartPage";
        if (url.contains("/checkout"))  return "CheckoutPage";
        if (url.contains("/login"))     return "LoginPage";
        if (url.contains("/dashboard")) return "DashboardPage";
        if (url.contains("/profile"))   return "ProfilePage";
        if (url.contains("/order"))     return "OrderPage";

        // Extract last meaningful path segment
        try {
            String path = url.replaceFirst("https?://[^/]+", ""); // strip scheme+host
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i].replaceAll("\\.html?$", ""); // strip .html
                if (!part.isBlank() && part.length() > 1) {
                    // Capitalize first letter
                    return Character.toUpperCase(part.charAt(0)) + part.substring(1) + "Page";
                }
            }
        } catch (Exception ignored) {}

        return "unknown";
    }

    /**
     * Infer page name from a URL pattern (ASSERT_URL step value like ".*inventory.*").
     * These are regex patterns, not real URLs — extract the meaningful keyword.
     */
    private String inferPageNameFromUrlPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) return "unknown";

        // Extract the meaningful path segment from the regex pattern
        if (pattern.contains("inventory")) return "InventoryPage";
        if (pattern.contains("cart"))      return "CartPage";
        if (pattern.contains("checkout"))  return "CheckoutPage";
        if (pattern.contains("login"))     return "LoginPage";
        if (pattern.contains("dashboard")) return "DashboardPage";
        if (pattern.contains("profile"))   return "ProfilePage";
        if (pattern.contains("order"))     return "OrderPage";
        if (pattern.contains("complete"))  return "OrderConfirmationPage";

        return "unknown";
    }

    private String inferElementPurpose(String action, String locator) {
        if (locator == null) {
            return action != null ? action : "unknown";
        }

        String lower = locator.toLowerCase();

        // ── Authentication ────────────────────────────────────────────────────────
        if (lower.contains("login-button") || lower.contains("login_button")
                || lower.contains("submit") || lower.contains("sign-in")) {
            return "login/submit button";
        }
        if (lower.contains("username") || lower.contains("user-name")
                || lower.contains("user_name") || lower.contains("email")) {
            return "username input";
        }
        if (lower.contains("password")) {
            return "password input";
        }

        // ── Inventory / Product ───────────────────────────────────────────────────
        if (lower.contains("add-to-cart") || lower.contains("add_to_cart")
                || lower.contains("btn_inventory") || lower.contains("btn-inventory")) {
            return "add to cart button";
        }
        if (lower.contains("inventory_item") || lower.contains("inventory-item")) {
            return "inventory/product item";
        }
        if (lower.contains("inventory_list") || lower.contains("inventory-list")) {
            return "products list";
        }
        if (lower.contains("inventory_item_name") || lower.contains("item_name")) {
            return "product name";
        }
        if (lower.contains("inventory_item_price") || lower.contains("item_price")) {
            return "product price";
        }

        // ── Cart ──────────────────────────────────────────────────────────────────
        if (lower.contains("shopping_cart") || lower.contains("shopping-cart")
                || lower.contains("cart_link")) {
            return "cart icon/link";
        }
        if (lower.contains("cart_badge") || lower.contains("cart-badge")) {
            return "cart item count badge";
        }
        if (lower.contains("cart_list") || lower.contains("cart-list")) {
            return "cart items list";
        }
        if (lower.contains("remove") && lower.contains("cart")) {
            return "remove from cart button";
        }

        // ── Checkout ──────────────────────────────────────────────────────────────
        if (lower.contains("checkout_button") || lower.contains("checkout-button")) {
            return "checkout button";
        }
        if (lower.contains("checkout_info") || lower.contains("checkout-info")) {
            return "checkout info form";
        }
        if (lower.contains("checkout_summary") || lower.contains("checkout-summary")) {
            return "checkout order summary";
        }
        if (lower.contains("firstname") || lower.contains("first-name")
                || lower.contains("first_name")) {
            return "first name input";
        }
        if (lower.contains("lastname") || lower.contains("last-name")
                || lower.contains("last_name")) {
            return "last name input";
        }
        if (lower.contains("postalcode") || lower.contains("postal-code")
                || lower.contains("zip")) {
            return "postal code input";
        }
        if (lower.contains("btn_action") || lower.contains("btn-action")) {
            return "primary action button";
        }
        if (lower.contains("btn_primary") || lower.contains("btn-primary")) {
            return "primary button";
        }

        // ── Order Confirmation ────────────────────────────────────────────────────
        if (lower.contains("complete-header") || lower.contains("complete_header")) {
            return "order confirmation header";
        }
        if (lower.contains("complete") || lower.contains("confirmation")) {
            return "order confirmation element";
        }

        // ── Search ────────────────────────────────────────────────────────────────
        if (lower.contains("search")) {
            return "search input";
        }

        // ── Fallback — describe action + locator type ─────────────────────────────
        String locatorHint = lower.startsWith("testid=") ? "element (testid)"
                : lower.startsWith("css=") ? "element (CSS)"
                : lower.startsWith("xpath=") ? "element (XPath)"
                : lower.startsWith("#") ? "element (ID)"
                : "element";

        return action != null ? action + " " + locatorHint : locatorHint;
    }

    /**
     * Helper class for locator info.
     */
    private static class LocatorInfo {
        String strategy;
        String value;
    }
}