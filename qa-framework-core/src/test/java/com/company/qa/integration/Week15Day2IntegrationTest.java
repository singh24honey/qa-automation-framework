package com.company.qa.integration;

import com.company.qa.model.entity.JiraStory;
import com.company.qa.service.SampleJiraStoryLoader;
import com.company.qa.service.context.JiraContextBuilder;
import com.company.qa.service.context.PlaywrightContextBuilder;
import com.company.qa.service.context.SauceDemoContextEnhancer;
import com.company.qa.service.playwright.ElementRegistryService;
import com.company.qa.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 15 Day 2 Integration Test
 *
 * Validates:
 * 1. Sample JIRA stories load correctly
 * 2. JIRA context builder generates prompts
 * 3. Sauce Demo context enhancer adds element registry
 * 4. Complete context contains all necessary information for AI
 */
@SpringBootTest
@ActiveProfiles({"test"}) // Activate mock Git profile
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Week15Day2IntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private SampleJiraStoryLoader storyLoader;

    @Autowired
    private JiraContextBuilder jiraContextBuilder;

    @Autowired
    private PlaywrightContextBuilder playwrightContextBuilder;

    @Autowired
    private SauceDemoContextEnhancer sauceDemoEnhancer;

    @Autowired
    private ElementRegistryService elementRegistryService;

    @Test
    @Order(1)
    @DisplayName("Test 1: Load sample Sauce Demo JIRA stories")
    void testLoadSampleStories() throws IOException {
        List<JiraStory> stories = storyLoader.loadAllSauceDemoStories();

        assertNotNull(stories, "Stories list should not be null");
        assertFalse(stories.isEmpty(), "Should load at least one story");
        assertEquals(6, stories.size(), "Should load all 6 sample stories");

        // Verify first story structure
        JiraStory firstStory = stories.get(0);
        assertEquals("SAUCE-001", firstStory.getJiraKey());
        assertEquals("User Login to Sauce Demo", firstStory.getSummary());
        assertNotNull(firstStory.getAcceptanceCriteria());
        //assertTrue(firstStory.getLabels().contains("sauce-demo"));

        System.out.println("✅ Test 1 passed: Loaded " + stories.size() + " sample stories");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Get story by JIRA key")
    void testGetStoryByKey() throws IOException {
        Optional<JiraStory> story = storyLoader.getStoryByKey("SAUCE-003");

        assertTrue(story.isPresent(), "Should find SAUCE-003");
        assertEquals("Complete Checkout Process", story.get().getSummary());
        //assertTrue(story.get().getLabels().contains("checkout"));

        System.out.println("✅ Test 2 passed: Retrieved story by key");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Get stories by label")
    void testGetStoriesByLabel() throws IOException {
        List<JiraStory> e2eStories = storyLoader.getStoriesByLabel("e2e");

        assertNotNull(e2eStories);
        assertFalse(e2eStories.isEmpty());
        assertTrue(e2eStories.size() >= 1, "Should have at least one E2E story");

        System.out.println("✅ Test 3 passed: Found " + e2eStories.size() + " E2E stories");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: JIRA context builder generates prompt")
    void testJiraContextBuilder() throws IOException {
        JiraStory story = storyLoader.getStoryByKey("SAUCE-001")
                .orElseThrow();

        String context = jiraContextBuilder.buildStoryTestPrompt(
                story,
                "Generate Playwright Java test"
        );

        assertNotNull(context);
        assertTrue(context.contains("SAUCE-001"), "Should include JIRA key");
        assertTrue(context.contains("User Login"), "Should include summary");
        assertTrue(context.contains("standard_user"), "Should include AC details");
        assertTrue(context.contains("Given I am on"), "Should include AC format");

        System.out.println("✅ Test 4 passed: Generated JIRA context");
        System.out.println("   Context length: " + context.length() + " characters");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Sauce Demo enhancer adds element registry")
    void testSauceDemoEnhancer() throws IOException {
        JiraStory story = storyLoader.getStoryByKey("SAUCE-001")
                .orElseThrow();

        String baseContext = jiraContextBuilder.buildStoryTestPrompt(story, null);
        String enhancedContext = sauceDemoEnhancer.enhanceWithSauceDemoContext(
                baseContext,
                story
        );

        assertNotNull(enhancedContext);
        assertTrue(enhancedContext.length() > baseContext.length(),
                "Enhanced context should be longer");
        assertTrue(enhancedContext.contains("https://www.saucedemo.com"),
                "Should include base URL");
        assertTrue(enhancedContext.contains("standard_user"),
                "Should include test credentials");
        assertTrue(enhancedContext.contains("LoginPage"),
                "Should include LoginPage elements");
        assertTrue(enhancedContext.contains("data-test"),
                "Should include element locators");

        System.out.println("✅ Test 5 passed: Enhanced context with element registry");
        System.out.println("   Base:     " + baseContext.length() + " characters");
        System.out.println("   Enhanced: " + enhancedContext.length() + " characters");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Complete context for shopping cart story")
    void testCompleteContextForCartStory() throws IOException {
        JiraStory story = storyLoader.getStoryByKey("SAUCE-002")
                .orElseThrow();

        String baseContext = jiraContextBuilder.buildStoryTestPrompt(story, null);
        String enhancedContext = sauceDemoEnhancer.enhanceWithSauceDemoContext(
                baseContext,
                story
        );

        // Verify ProductsPage elements included
        assertTrue(enhancedContext.contains("ProductsPage"),
                "Should include ProductsPage for cart story");
        assertTrue(enhancedContext.contains("addToCartBackpack") ||
                        enhancedContext.contains("add-to-cart"),
                "Should include add to cart elements");

        System.out.println("✅ Test 6 passed: Complete context for shopping cart");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Complete context for checkout story")
    void testCompleteContextForCheckoutStory() throws IOException {
        JiraStory story = storyLoader.getStoryByKey("SAUCE-003")
                .orElseThrow();

        String baseContext = jiraContextBuilder.buildStoryTestPrompt(story, null);
        String enhancedContext = sauceDemoEnhancer.enhanceWithSauceDemoContext(
                baseContext,
                story
        );

        // Verify checkout pages included
        assertTrue(enhancedContext.contains("CheckoutInfoPage"),
                "Should include CheckoutInfoPage");
        assertTrue(enhancedContext.contains("CheckoutOverviewPage") ||
                        enhancedContext.contains("CheckoutCompletePage"),
                "Should include checkout flow pages");
        assertTrue(enhancedContext.contains("firstName") ||
                        enhancedContext.contains("first-name"),
                "Should include checkout form fields");

        System.out.println("✅ Test 7 passed: Complete context for checkout");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Verify Playwright patterns in context")
    void testPlaywrightPatternsIncluded() throws IOException {
        JiraStory story = storyLoader.getStoryByKey("SAUCE-001")
                .orElseThrow();

        String context = sauceDemoEnhancer.enhanceWithSauceDemoContext(
                jiraContextBuilder.buildStoryTestPrompt(story, null),
                story
        );

        assertTrue(context.contains("extends BasePlaywrightTest"),
                "Should include test structure");
        assertTrue(context.contains("page.navigate"),
                "Should include navigation pattern");
        assertTrue(context.contains("page.locator"),
                "Should include locator pattern");
        assertTrue(context.contains("assertThat"),
                "Should include assertion pattern");

        System.out.println("✅ Test 8 passed: Playwright patterns included");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Context quality for E2E story")
    void testContextQualityForE2EStory() throws IOException {
        JiraStory story = storyLoader.getStoryByKey("SAUCE-006")
                .orElseThrow();

        String context = sauceDemoEnhancer.enhanceWithSauceDemoContext(
                jiraContextBuilder.buildStoryTestPrompt(story, null),
                story
        );

        // E2E story should include multiple pages
        assertTrue(context.contains("LoginPage"));
        assertTrue(context.contains("ProductsPage"));
        assertTrue(context.contains("CheckoutCompletePage"));

        // Should have comprehensive length
        assertTrue(context.length() > 2000,
                "E2E context should be comprehensive (>2000 chars)");

        System.out.println("✅ Test 9 passed: E2E story context quality validated");
        System.out.println("   Context length: " + context.length() + " characters");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: All components integrated")
    void testAllComponentsIntegrated() throws IOException {
        System.out.println("\n🔄 Testing complete integration...");

        // Step 1: Load story
        JiraStory story = storyLoader.getStoryByKey("SAUCE-001").orElseThrow();
        System.out.println("   ✅ Step 1: Story loaded: " + story.getJiraKey());

        // Step 2: Build JIRA context
        String jiraContext = jiraContextBuilder.buildStoryTestPrompt(story,
                "Generate a Playwright Java test");
        assertTrue(jiraContext.contains("SAUCE-001"));
        System.out.println("   ✅ Step 2: JIRA context built");

        // Step 3: Enhance with Sauce Demo context
        String enhancedContext = sauceDemoEnhancer.enhanceWithSauceDemoContext(
                jiraContext, story);
        assertTrue(enhancedContext.contains("LoginPage"));
        System.out.println("   ✅ Step 3: Context enhanced with element registry");

        // Step 4: Verify context is ready for AI
        assertTrue(enhancedContext.contains("Given I am on"));
        assertTrue(enhancedContext.contains("data-test"));
        assertTrue(enhancedContext.contains("page.locator"));
        assertTrue(enhancedContext.contains("https://www.saucedemo.com"));
        System.out.println("   ✅ Step 4: Context validated for AI consumption");

        System.out.println("\n✅ Test 10 passed: Complete integration validated");
        System.out.println("   Ready for PlaywrightAgent test generation!");
    }
}