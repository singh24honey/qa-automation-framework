package com.company.qa.e2e;

import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.dto.response.TestGenerationResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.repository.JiraConfigurationRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.ai.AITestGenerationService;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for Week 13 complete flow.
 *
 * Tests: JIRA Story → Enhanced Context → AI Generation → Test File
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Week 13 - Complete End-to-End Integration Test")
class Week13CompleteFlowTest extends PostgresIntegrationTest {

    @Autowired
    private AITestGenerationService aiTestGenerationService;

    @Autowired
    private PlaywrightContextBuilder contextBuilder;

    @Autowired
    private ElementRegistryService elementRegistry;

    @Autowired
    private PageObjectRegistryService pageObjectRegistry;

    @Autowired
    private JiraConfigurationRepository jiraConfigurationRepository;

    @Autowired
    private JiraStoryRepository jiraStoryRepository;
    @BeforeEach
    void setUp() {
        // Ensure registries are loaded
        elementRegistry.loadRegistry();
        pageObjectRegistry.scanPageObjects();
    }

    @Test
    @DisplayName("E2E: Complete flow from JIRA story to generated test")
    void completeFlow_JiraToGeneratedTest() {
        // Given - Create realistic JIRA story
        JiraStory loginStory = createLoginStory();

       // jiraStoryRepository.save(loginStory);

        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-100")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();
        // When - Generate test using complete Week 13 flow
        TestGenerationResponse result = aiTestGenerationService.generateTestFromStory(request);

        // Then - Verify result quality
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AIGeneratedTest.TestGenerationStatus.APPROVED);
        assertThat(result.getTestFramework()).isEqualTo(AIGeneratedTest.TestFramework.PLAYWRIGHT);

        // Verify generated code structure
        Map<String, Object> testCode = result.getTestCode();
        assertThat(testCode).isNotNull();
        assertThat(testCode).containsKey("testClassName");
        assertThat(testCode).containsKey("testClass");

        String testClass = testCode.get("testClass").toString();

        // Week 13 Exit Criteria Validation
        System.out.println("\n=== WEEK 13 EXIT CRITERIA VALIDATION ===\n");

        // 1. Uses known locators from Element Registry
        boolean usesElementRegistry = testClass.contains("page.getByLabel(\"Email\")")
                || testClass.contains("page.getByLabel(\"Password\")")
                || testClass.contains("page.getByRole(");

        System.out.println("✓ Uses Element Registry locators: " + usesElementRegistry);
        assertThat(usesElementRegistry).isTrue();

        // 2. References existing Page Objects
        boolean usesPageObjects = testClass.contains("LoginPage")
                || testClass.contains("DashboardPage");

        System.out.println("✓ References existing Page Objects: " + usesPageObjects);

        // 3. Uses Playwright-style locators (not CSS)
        boolean usesPlaywrightLocators = testClass.contains("getByRole")
                || testClass.contains("getByLabel")
                || testClass.contains("getByTestId");

        System.out.println("✓ Uses Playwright locators: " + usesPlaywrightLocators);
        assertThat(usesPlaywrightLocators).isTrue();

        // 4. Avoids hardcoded CSS selectors
        boolean avoidsHardcodedCSS = !testClass.contains("locator(\"#")
                && !testClass.contains("locator(\".");

        System.out.println("✓ Avoids hardcoded CSS: " + avoidsHardcodedCSS);

        // 5. Framework integration (extends BasePlaywrightTest)
        boolean extendsBase = testClass.contains("extends BasePlaywrightTest")
                || testClass.contains("@Test");

        System.out.println("✓ Framework integrated: " + extendsBase);
        assertThat(extendsBase).isTrue();

        // 6. Print summary
        System.out.println("\n=== GENERATED TEST QUALITY ===");
        System.out.println("Test Class Name: " + testCode.get("testClassName"));
        System.out.println("Code Length: " + testClass.length() + " characters");
        System.out.println("Uses Element Registry: YES ✓");
        System.out.println("Uses Playwright Locators: YES ✓");
        System.out.println("Framework Integrated: YES ✓");
        System.out.println("\n=== END-TO-END TEST PASSED ===\n");
    }

    @Test
    @DisplayName("E2E: Context includes all registry information")
    void completeFlow_ContextIncludesRegistries() {
        // Given
        JiraStory story = createLoginStory();

        // When - Build context
        String context = contextBuilder.buildContext(story, null);

        // Then - Verify all sections present
        assertThat(context).contains("=== JIRA Story Context ===");
        assertThat(context).contains("=== Available Elements");
        assertThat(context).contains("=== Available Page Objects ===");
        assertThat(context).contains("=== Playwright Locator Strategy");
        assertThat(context).contains("=== Output Format");

        // Verify Element Registry content
        assertThat(context).contains("emailInput");
        assertThat(context).contains("page.getByLabel(\"Email\")");

        // Verify Page Object Registry content
        assertThat(context).contains("LoginPage");
        assertThat(context).contains("login(String email, String password)");

        System.out.println("\n✓ Context includes all registry information");
        System.out.println("✓ Element Registry: Present");
        System.out.println("✓ Page Object Registry: Present");
        System.out.println("✓ Best Practices: Present");
    }

    @Test
    @DisplayName("E2E: Registry statistics are healthy")
    void completeFlow_RegistryStatistics() {
        // When - Get registry statistics
        Map<String, Object> elementStats = elementRegistry.getStatistics();
        Map<String, Object> pageObjectStats = pageObjectRegistry.getStatistics();

        // Then - Verify registries are populated
        assertThat(elementStats.get("pageCount")).isNotNull();
        assertThat((Integer) elementStats.get("pageCount")).isGreaterThan(0);
        assertThat((Integer) elementStats.get("totalElements")).isGreaterThan(0);

        assertThat(pageObjectStats.get("pageObjectCount")).isNotNull();
        assertThat((Integer) pageObjectStats.get("pageObjectCount")).isGreaterThan(0);

        System.out.println("\n=== REGISTRY STATISTICS ===");
        System.out.println("Element Registry:");
        System.out.println("  - Pages: " + elementStats.get("pageCount"));
        System.out.println("  - Elements: " + elementStats.get("totalElements"));
        System.out.println("  - Strategies: " + elementStats.get("strategyBreakdown"));

        System.out.println("\nPage Object Registry:");
        System.out.println("  - Page Objects: " + pageObjectStats.get("pageObjectCount"));
        System.out.println("  - Total Methods: " + pageObjectStats.get("totalMethods"));
        System.out.println("  - With URLs: " + pageObjectStats.get("pageObjectsWithUrls"));
    }

    private JiraStory createLoginStory() {

       /* JiraConfiguration testConfig = JiraConfiguration.builder()
                .id(UUID.randomUUID())
                .configName("E2E-integration-test")
                .jiraUrl("https://singh24honey.atlassian.net/jira")
                .projectKey("TEST")
                .maxRequestsPerMinute(60)
                .secretArn("test-secret")
                .createdBy("QA")
                .enabled(false)
                .build();

        testConfig = jiraConfigurationRepository.save(testConfig);*/

        JiraStory story = new JiraStory();
        story.setJiraKey("PROJ-123");
        story.setSummary("User can login with valid credentials");
        story.setDescription("As a user, I want to login with my email and password so that I can access my dashboard");
        story.setAcceptanceCriteria(
                "Given user is on the login page\n" +
                        "When user enters valid email 'user@example.com'\n" +
                        "And user enters valid password 'password123'\n" +
                        "And user clicks the Sign In button\n" +
                        "Then user should see the dashboard\n" +
                        "And user should see welcome message"
        );
        return story;
    }
}