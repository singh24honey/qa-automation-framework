package com.company.qa.service.context;

import com.company.qa.model.entity.JiraStory;
import com.company.qa.service.playwright.ElementRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.company.qa.service.playwright.FrameworkCapabilityService;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Sauce Demo Context Enhancer for Week 15
 *
 * Enhances AI prompts with Sauce Demo specific context:
 * - Element registry locators for known pages
 * - Page-specific guidance
 * - Base URL and credentials
 * - Playwright Java patterns
 *
 * This service bridges JIRA story context with element registry
 * to provide AI with complete information for generating working tests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SauceDemoContextEnhancer {

    private final ElementRegistryService elementRegistryService;
    private final ObjectMapper objectMapper;

    private final FrameworkCapabilityService frameworkCapabilityService;

    /**
     * Mirrors playwright.intent.enabled from PlaywrightContextBuilder.
     * When true, buildPlaywrightPatterns() returns TestIntent JSON examples
     * instead of raw Java code examples.
     */
    @Value("${playwright.intent.enabled:false}")
    private boolean intentEnabled;

    private static final String SAUCE_DEMO_BASE_URL = "https://www.saucedemo.com";
    private static final String SAUCE_DEMO_USERNAME = "standard_user";
    private static final String SAUCE_DEMO_PASSWORD = "secret_sauce";

    /**
     * Enhance JIRA context with Sauce Demo element registry
     *
     * Adds:
     * - Known element locators for pages mentioned in the story
     * - Base URL and authentication details
     * - Playwright Java code patterns
     */
    public String enhanceWithSauceDemoContext(
            String baseContext,
            JiraStory story) {

        StringBuilder enhanced = new StringBuilder(baseContext);

        // Add Sauce Demo specific context header
        enhanced.append("\n\n=== Sauce Demo Application Context ===\n");
        enhanced.append("Base URL: ").append(SAUCE_DEMO_BASE_URL).append("\n");
        enhanced.append("Test Credentials: ")
                .append(SAUCE_DEMO_USERNAME)
                .append(" / ")
                .append(SAUCE_DEMO_PASSWORD)
                .append("\n\n");

        // Add element registry context for relevant pages
        String elementContext = buildElementRegistryContext(story);
        if (!elementContext.isEmpty()) {
            enhanced.append(elementContext);
        }

        // Add Playwright Java patterns
        enhanced.append(buildPlaywrightPatterns());

        log.debug("Enhanced context for {}: {} → {} characters",
                story.getJiraKey(),
                baseContext.length(),
                enhanced.length());

        return enhanced.toString();
    }

    /**
     * Build element registry context based on story content
     *
     * Analyzes story summary/description to determine which pages are involved
     * and includes relevant element locators from the registry.
     */
    private String buildElementRegistryContext(JiraStory story) {
        StringBuilder context = new StringBuilder();

        try {
            // Load element registry
            ClassPathResource resource = new ClassPathResource(
                    "playwright/element-registry-saucedemo.json");

            try (InputStream is = resource.getInputStream()) {
                JsonNode registry = objectMapper.readTree(is);
                JsonNode pages = registry.get("pages");

                // Determine which pages are relevant based on story
                List<String> relevantPages = identifyRelevantPages(story);

                if (!relevantPages.isEmpty()) {
                    context.append("=== Available Page Elements ===\n\n");

                    for (String pageName : relevantPages) {
                        context.append(buildPageElementContext(pages, pageName));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load element registry: {}", e.getMessage());
        }

        return context.toString();
    }

    /**
     * Identify which Sauce Demo pages are relevant based on story content
     */
    private List<String> identifyRelevantPages(JiraStory story) {
        List<String> pages = new ArrayList<>();
        String content = (story.getSummary() + " " +
                story.getDescription() + " " +
                story.getAcceptanceCriteria()).toLowerCase();

        // Map keywords to pages
        if (content.contains("login") || content.contains("log in")) {
            pages.add("LoginPage");
        }
        if (content.contains("product") || content.contains("inventory") ||
                content.contains("add to cart")) {
            pages.add("ProductsPage");
        }
        if (content.contains("cart") || content.contains("shopping cart")) {
            pages.add("CartPage");
        }
        if (content.contains("checkout") || content.contains("shipping")) {
            pages.add("CheckoutInfoPage");
            pages.add("CheckoutOverviewPage");
        }
        if (content.contains("confirmation") || content.contains("order") ||
                content.contains("complete")) {
            pages.add("CheckoutCompletePage");
        }

        log.debug("Identified relevant pages for {}: {}",
                story.getJiraKey(), pages);

        return pages;
    }

    /**
     * Build element context for a specific page
     */
    private String buildPageElementContext(JsonNode pages, String pageName) {
        StringBuilder context = new StringBuilder();

        for (JsonNode page : pages) {
            if (page.get("name").asText().equals(pageName)) {
                context.append("Page: ").append(pageName).append("\n");
                context.append("URL: ").append(page.get("url").asText()).append("\n");
                context.append("Elements:\n");

                JsonNode elements = page.get("elements");
                for (JsonNode element : elements) {
                    String elemName = element.get("name").asText();
                    String description = element.get("description").asText();
                    String primarySelector = element.get("primarySelector").asText();

                    context.append("  - ").append(elemName)
                            .append(": ").append(description).append("\n");
                    context.append("    Locator: ").append(primarySelector).append("\n");
                }

                context.append("\n");
                break;
            }
        }

        return context.toString();
    }

    /**
     * Build code patterns section for AI guidance.
     *
     * Legacy mode: returns raw Java code examples (Playwright API calls).
     * Intent mode: returns TestIntent JSON examples (action/locator/value format).
     *
     * The intent-mode examples reinforce the schema from
     * PlaywrightContextBuilder.buildIntentOutputFormatInstructions() —
     * consistent repetition across both context sections helps AI internalize the format.
     *
     * @since Zero-Hallucination Pipeline (branching added)
     */
    private String buildPlaywrightPatterns() {
        if (intentEnabled) {
            return buildIntentPatterns();
        }
        return buildLegacyPlaywrightPatterns();
    }

    /**
     * Intent-mode patterns: shows TestIntent JSON action/locator/value format
     * using Sauce Demo specific selectors from the element registry.
     *
     * These examples are deliberately concrete and Sauce Demo specific —
     * the AI has seen the element registry locators and these examples
     * show how to reference them in the TestIntent format.
     */
    private String buildIntentPatterns() {
        return """

=== Sauce Demo: TestIntent Step Examples ===

Use these action patterns with the locators from the element registry above:

Navigation:
  {"action": "NAVIGATE", "value": "https://www.saucedemo.com", "description": "Open login page"}

Fill input (use testid= locators from registry):
  {"action": "FILL", "locator": "testid=username", "value": "standard_user"}
  {"action": "FILL", "locator": "testid=password", "value": "secret_sauce"}

Click (use testid= locators from registry):
  {"action": "CLICK", "locator": "testid=login-button"}
  {"action": "CLICK", "locator": "testid=add-to-cart-sauce-labs-backpack"}

Assert URL after navigation:
  {"action": "ASSERT_URL", "value": ".*inventory.*", "description": "Verify products page loaded"}

Assert element text:
  {"action": "ASSERT_TEXT", "locator": "css=.title", "value": "Products"}

Assert element visible:
  {"action": "ASSERT_VISIBLE", "locator": "css=.inventory_list"}
  {"action": "ASSERT_VISIBLE", "locator": "css=.cart_badge"}

Assert element count (e.g., number of products):
  {"action": "ASSERT_COUNT", "locator": "css=.inventory_item", "value": "6"}

Wait for element (use when page needs to settle):
  {"action": "WAIT_FOR_SELECTOR", "locator": "css=.inventory_list"}

With explicit timeout (milliseconds):
  {"action": "CLICK", "locator": "testid=login-button", "timeout": 5000}

Key rules:
- locator field: use strategy=value format (testid=..., css=..., role=...)
- value field: text to fill, URL pattern, expected text, or key name
- description field: optional human-readable comment
- NEVER write Java code in any field
""";
    }

    /**
     * Legacy mode patterns: raw Java code examples.
     * Unchanged from original buildPlaywrightPatterns() implementation.
     * Used when playwright.intent.enabled=false.
     */
    private String buildLegacyPlaywrightPatterns() {
        return """
               
               === Playwright Java Patterns ===
               
               Test Class Structure:
```java
               public class [StoryKey]Test extends BasePlaywrightTest {
                   @Test
                   void test[ScenarioName]() {
                       // Test implementation
                   }
               }
```
               
               Navigation:
```java
               page.navigate("https://www.saucedemo.com");
```
               
               Element Interaction:
```java
               page.locator("[data-test='username']").fill("standard_user");
               page.locator("[data-test='login-button']").click();
```
               
               Assertions:
```java
               import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
               assertThat(page.locator(".title")).hasText("Products");
               assertThat(page.locator(".cart_badge")).isVisible();
```
               
               Use locators from the element registry above when available.
               Prefer data-test attributes over CSS selectors for stability.
               
               """;
    }
}