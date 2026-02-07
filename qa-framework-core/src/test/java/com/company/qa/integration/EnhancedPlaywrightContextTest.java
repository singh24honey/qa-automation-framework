package com.company.qa.integration;


import com.company.qa.model.entity.JiraStory;
import com.company.qa.service.context.PlaywrightContextBuilder;
import com.company.qa.service.playwright.ElementRegistryService;
import com.company.qa.service.playwright.PageObjectRegistryService;

import com.company.qa.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for enhanced Playwright context with registry integration.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Enhanced Playwright Context Integration Tests")
class EnhancedPlaywrightContextTest extends PostgresIntegrationTest {

    @Autowired
    private PlaywrightContextBuilder contextBuilder;

    @Autowired
    private ElementRegistryService elementRegistry;

    @Autowired
    private PageObjectRegistryService pageObjectRegistry;

    private JiraStory loginStory;

    @BeforeEach
    void setUp() {
        // Ensure registries are loaded
        elementRegistry.loadRegistry();
        pageObjectRegistry.scanPageObjects();

        // Create sample story
        loginStory = new JiraStory();
        loginStory.setJiraKey("PROJ-123");
        loginStory.setSummary("User can login with valid credentials");
        loginStory.setAcceptanceCriteria(
                "Given user is on login page\n" +
                        "When user enters valid email and password\n" +
                        "Then user sees dashboard"
        );
    }

    @Test
    @DisplayName("Should include JIRA story context")
    void buildContext_IncludesJiraStory() {
        // When
        String context = contextBuilder.buildContext(loginStory, null);

        // Then
        assertThat(context).contains("=== JIRA Story Context ===");
        assertThat(context).contains("PROJ-123");
        assertThat(context).contains("User can login");
    }

    @Test
    @DisplayName("Should include Element Registry context")
    void buildContext_IncludesElementRegistry() {
        // When
        String context = contextBuilder.buildContext(loginStory, null);

        // Then
        assertThat(context).contains("=== Available Elements");
        assertThat(context).contains("login");
        assertThat(context).contains("emailInput");
        assertThat(context).contains("page.getByLabel(\"Email\")");

        System.out.println("Element Registry section found in context ✓");
    }

    @Test
    @DisplayName("Should include Page Object Registry context")
    void buildContext_IncludesPageObjectRegistry() {
        // When
        String context = contextBuilder.buildContext(loginStory, null);

        // Then
        assertThat(context).contains("=== Available Page Objects ===");
        assertThat(context).contains("LoginPage");
        assertThat(context).contains("login(String email, String password)");

        System.out.println("Page Object Registry section found in context ✓");
    }

    @Test
    @DisplayName("Should include Playwright guidance")
    void buildContext_IncludesPlaywrightGuidance() {
        // When
        String context = contextBuilder.buildContext(loginStory, null);

        // Then
        assertThat(context).contains("=== Playwright Locator Strategy");
        assertThat(context).contains("Role-based");
        assertThat(context).contains("CHECK ELEMENT REGISTRY FIRST");
        assertThat(context).contains("CHECK PAGE OBJECT REGISTRY");
    }

    @Test
    @DisplayName("Should include output format instructions")
    void buildContext_IncludesOutputFormat() {
        // When
        String context = contextBuilder.buildContext(loginStory, null);

        // Then
        assertThat(context).contains("=== Output Format");
        assertThat(context).contains("testClassName");
        assertThat(context).contains("testClass");
        assertThat(context).contains("usesExistingPages");
        assertThat(context).contains("newPagesNeeded");
    }

    @Test
    @DisplayName("Should extract relevant pages from story")
    void buildContext_ExtractsRelevantPages() {
        // When
        String context = contextBuilder.buildContext(loginStory, null);

        // Then - Should include login page elements since story mentions "login"
        assertThat(context).contains("login (/login)");

        System.out.println("Relevant pages extracted and included ✓");
    }

    @Test
    @DisplayName("Should build context with OpenAPI information")
    void buildContext_WithOpenApi() {
        // Given
        String openApiContext = "POST /api/auth/login - Authenticate user";

        // When
        String context = contextBuilder.buildContext(loginStory, openApiContext);

        // Then
        assertThat(context).contains("=== API Context ===");
        assertThat(context).contains("/api/auth/login");
    }

    @Test
    @DisplayName("Should provide complete context with all sections")
    void buildContext_CompleteSections() {
        // When
        String context = contextBuilder.buildContext(loginStory, null);

        // Then - Verify all major sections present
        assertThat(context)
                .contains("=== JIRA Story Context ===")
                .contains("=== Available Elements")
                .contains("=== Available Page Objects ===")
                .contains("=== Playwright Locator Strategy")
                .contains("=== Output Format");

        // Print context structure
        System.out.println("\n=== CONTEXT STRUCTURE ===");
        System.out.println("Total length: " + context.length() + " characters");
        System.out.println("✓ JIRA Story Context");
        System.out.println("✓ Element Registry");
        System.out.println("✓ Page Object Registry");
        System.out.println("✓ Playwright Guidance");
        System.out.println("✓ Output Format");
    }

    @Test
    @DisplayName("Should handle story without matching pages gracefully")
    void buildContext_NoMatchingPages() {
        // Given
        JiraStory unrelatedStory = new JiraStory();
        unrelatedStory.setJiraKey("PROJ-999");
        unrelatedStory.setSummary("Update settings configuration");
        unrelatedStory.setAcceptanceCriteria("User can update settings");

        // When
        String context = contextBuilder.buildContext(unrelatedStory, null);

        // Then - Should still include all sections, just no specific page elements
        assertThat(context).contains("=== Available Elements");
        assertThat(context).contains("=== Available Page Objects ===");
    }

    @Test
    @DisplayName("Should build basic context as fallback")
    void buildBasicContext_Works() {
        // When
        String context = contextBuilder.buildBasicContext(loginStory, null);

        // Then
        assertThat(context).contains("=== JIRA Story Context ===");
        assertThat(context).contains("=== Playwright Locator Strategy");
        assertThat(context).contains("=== Output Format");

        // Should NOT contain registry sections
        assertThat(context).doesNotContain("=== Available Elements");
        assertThat(context).doesNotContain("=== Available Page Objects");
    }
}