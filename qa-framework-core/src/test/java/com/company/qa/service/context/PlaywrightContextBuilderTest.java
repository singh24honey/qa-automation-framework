package com.company.qa.service.context;

import com.company.qa.model.dto.ParsedAcceptanceCriteria;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.service.parser.AcceptanceCriteriaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlaywrightContextBuilder.
 *
 * Test Coverage:
 * 1. Full prompt generation with all sections
 * 2. Minimal prompt generation
 * 3. Locator strategy guidance
 * 4. Framework-specific instructions
 * 5. Output format specifications
 * 6. Acceptance criteria handling (Gherkin, numbered, empty)
 * 7. HTML cleaning
 * 8. Edge cases (null values, empty strings)
 *
 * @author QA Framework
 * @since Week 12 Day 1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlaywrightContextBuilder Tests")
class PlaywrightContextBuilderTest {

    @Mock
    private AcceptanceCriteriaParser acParser;

    @InjectMocks
    private PlaywrightContextBuilder contextBuilder;

    private JiraStory sampleStory;
    private ParsedAcceptanceCriteria gherkinAC;
    private ParsedAcceptanceCriteria numberedAC;
    private ParsedAcceptanceCriteria emptyAC;

    @BeforeEach
    void setUp() {
        // Sample JIRA story
        sampleStory = JiraStory.builder()
                .jiraKey("SCRUM-7")
                .summary("Implement user login functionality")
                .description("Users should be able to log in with email and password")
                .acceptanceCriteria("""
                        Scenario: Successful login
                        Given user is on login page
                        When user enters valid credentials
                        Then user is redirected to dashboard
                        """)
                .storyType("Story")
                .status("In Progress")
                .priority("High")
                .labelsString("UI")
                .componentsString("Frontend")
                .assignee("john.doe@example.com")
                .build();

        // Mock parsed acceptance criteria - Gherkin format
        gherkinAC = ParsedAcceptanceCriteria.builder()
                .format(ParsedAcceptanceCriteria.ACFormat.GHERKIN)
                .rawText("""
                        Scenario: Successful login
                        Given user is on login page
                        When user enters valid credentials
                        Then user is redirected to dashboard
                        """)

                //.scenarioCount(1)
                .build();

        // Mock parsed acceptance criteria - Numbered format
        numberedAC = ParsedAcceptanceCriteria.builder()
                .format(ParsedAcceptanceCriteria.ACFormat.NUMBERED_LIST)
                .rawText("""
                        1. User can log in with valid email and password
                        2. Invalid credentials show error message
                        3. Password field is masked
                        """)
                .build();

        // Mock empty acceptance criteria
        emptyAC = ParsedAcceptanceCriteria.builder()
                .format(ParsedAcceptanceCriteria.ACFormat.EMPTY)
                .rawText("")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // FULL PROMPT GENERATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Playwright Prompt Generation")
    class FullPromptGenerationTests {

        @Test
        @DisplayName("Should generate comprehensive prompt with all sections")
        void shouldGenerateComprehensivePrompt() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).isNotNull();
            assertThat(prompt).isNotEmpty();

            // Verify all required sections present
            assertThat(prompt).contains("=== JIRA Story Context ===");
            assertThat(prompt).contains("=== Story Summary ===");
            assertThat(prompt).contains("=== Description ===");
            assertThat(prompt).contains("=== Acceptance Criteria ===");
            assertThat(prompt).contains("=== Additional Context ===");
            assertThat(prompt).contains("=== Playwright Test Generation Instructions ===");
            assertThat(prompt).contains("=== Playwright Locator Strategy Guide ===");
            assertThat(prompt).contains("=== Framework & Technical Context ===");
            assertThat(prompt).contains("=== Required Output Format ===");

            // Verify story details included
            assertThat(prompt).contains("SCRUM-7");
            assertThat(prompt).contains("Implement user login functionality");
            assertThat(prompt).contains("In Progress");
            assertThat(prompt).contains("High");
        }

        @Test
        @DisplayName("Should include user prompt when provided")
        void shouldIncludeUserPromptWhenProvided() {
            // Arrange
            String userPrompt = "Focus on security aspects and edge cases";
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    userPrompt,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).startsWith(userPrompt);
        }

        @Test
        @DisplayName("Should handle story without description")
        void shouldHandleStoryWithoutDescription() {
            // Arrange
            JiraStory storyWithoutDesc = JiraStory.builder()
                    .jiraKey("SCRUM-8")
                    .summary("Simple test")
                    .acceptanceCriteria("AC here")
                    .storyType("Story")
                    .status("To Do")
                    .priority("Medium")
                    .build();

            when(acParser.parse(any())).thenReturn(numberedAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    storyWithoutDesc,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("=== Story Summary ===");
            assertThat(prompt).doesNotContain("=== Description ===");
        }

        @Test
        @DisplayName("Should handle story without acceptance criteria")
        void shouldHandleStoryWithoutAcceptanceCriteria() {
            // Arrange
            JiraStory storyWithoutAC = JiraStory.builder()
                    .jiraKey("SCRUM-9")
                    .summary("Test without AC")
                    .description("Description here")
                    .storyType("Story")
                    .status("To Do")
                    .priority("Low")
                    .build();

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    storyWithoutAC,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("=== Story Summary ===");
            assertThat(prompt).contains("=== Description ===");
            assertThat(prompt).doesNotContain("=== Acceptance Criteria ===");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYWRIGHT-SPECIFIC CONTENT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Playwright-Specific Content")
    class PlaywrightSpecificContentTests {

        @Test
        @DisplayName("Should include role-based locator guidance")
        void shouldIncludeRoleBasedLocatorGuidance() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert - Check for key locator strategies
            assertThat(prompt).contains("getByRole");
            assertThat(prompt).contains("getByLabel");
            assertThat(prompt).contains("getByTestId");
            assertThat(prompt).contains("getByText");
            assertThat(prompt).contains("getByPlaceholder");

            // Should include examples
            assertThat(prompt).contains("AriaRole.BUTTON");
            assertThat(prompt).contains("AriaRole.LINK");
            assertThat(prompt).contains("Page.GetByRoleOptions()");
        }

        @Test
        @DisplayName("Should warn against CSS/XPath selectors")
        void shouldWarnAgainstCssXpathSelectors() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("❌ AVOID");
            assertThat(prompt).containsIgnoringCase("Complex CSS");
            assertThat(prompt).containsIgnoringCase("XPath");
        }

        @Test
        @DisplayName("Should emphasize auto-waiting (no Thread.sleep)")
        void shouldEmphasizeAutoWaiting() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).containsIgnoringCase("auto-wait");
            assertThat(prompt).containsIgnoringCase("NO Thread.sleep");
            assertThat(prompt).containsIgnoringCase("automatically waits");
        }

        @Test
        @DisplayName("Should specify Java (not Gherkin) output")
        void shouldSpecifyJavaOutput() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("single Java test class");
            assertThat(prompt).contains("NOT Gherkin/Cucumber");
            assertThat(prompt).contains("@Test annotation");
            assertThat(prompt).doesNotContain("Feature:");
            assertThat(prompt).doesNotContain("Scenario:");
        }

        @Test
        @DisplayName("Should include Page Object Model guidance")
        void shouldIncludePageObjectModelGuidance() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("Page Object Model");
            assertThat(prompt).contains("BasePage");
            assertThat(prompt).contains("BasePlaywrightTest");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OUTPUT FORMAT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Output Format Specifications")
    class OutputFormatTests {

        @Test
        @DisplayName("Should specify JSON output structure")
        void shouldSpecifyJsonOutputStructure() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("testClassName");
            assertThat(prompt).contains("testClass");
            assertThat(prompt).contains("pageObjects");
            assertThat(prompt).contains("usesExistingPages");
            assertThat(prompt).contains("newPagesNeeded");

            // Should NOT include Cucumber structure
            assertThat(prompt).doesNotContain("featureFile");
            assertThat(prompt).doesNotContain("stepDefinitions");
        }

        @Test
        @DisplayName("Should include example test structure")
        void shouldIncludeExampleTestStructure() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("```java");
            assertThat(prompt).contains("public class LoginTest extends BasePlaywrightTest");
            assertThat(prompt).contains("@Test");
            assertThat(prompt).contains("assertThat(page)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCEPTANCE CRITERIA FORMAT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Acceptance Criteria Format Handling")
    class AcceptanceCriteriaFormatTests {

        @Test
        @DisplayName("Should handle Gherkin format with conversion guidance")
        void shouldHandleGherkinFormat() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("Gherkin format");
            assertThat(prompt).contains("Convert each scenario to a @Test method");
        }

        @Test
        @DisplayName("Should handle numbered list format")
        void shouldHandleNumberedListFormat() {
            // Arrange
            when(acParser.parse(any())).thenReturn(numberedAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("numbered list format");
            assertThat(prompt).contains("Convert each criterion to a @Test method");
        }

        @Test
        @DisplayName("Should handle empty acceptance criteria")
        void shouldHandleEmptyAcceptanceCriteria() {
            // Arrange
            when(acParser.parse(any())).thenReturn(emptyAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("No explicit acceptance criteria");
            assertThat(prompt).contains("Infer test scenarios");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MINIMAL PROMPT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Minimal Prompt Generation")
    class MinimalPromptTests {

        @Test
        @DisplayName("Should generate minimal prompt with essential elements")
        void shouldGenerateMinimalPromptWithEssentials() {
            // Act
            String prompt = contextBuilder.buildMinimalPlaywrightPrompt(
                    sampleStory,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).isNotNull();
            assertThat(prompt).isNotEmpty();
            assertThat(prompt).contains("Implement user login functionality");
            assertThat(prompt).contains("Playwright");
            assertThat(prompt).contains("role-based locators");
        }

        @Test
        @DisplayName("Should include acceptance criteria in minimal prompt if available")
        void shouldIncludeAcceptanceCriteriaInMinimalPrompt() {
            // Act
            String prompt = contextBuilder.buildMinimalPlaywrightPrompt(
                    sampleStory,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("Acceptance Criteria:");
        }

        @Test
        @DisplayName("Should fallback to description if no acceptance criteria")
        void shouldFallbackToDescriptionIfNoAC() {
            // Arrange
            JiraStory storyWithoutAC = JiraStory.builder()
                    .jiraKey("SCRUM-10")
                    .summary("Test story")
                    .description("This is the description")
                    .storyType("Story")
                    .status("To Do")
                    .priority("Medium")
                    .build();

            // Act
            String prompt = contextBuilder.buildMinimalPlaywrightPrompt(
                    storyWithoutAC,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("Description:");
            assertThat(prompt).contains("This is the description");
        }

        @Test
        @DisplayName("Minimal prompt should be significantly shorter than full prompt")
        void minimalPromptShouldBeShorter() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String fullPrompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );
            String minimalPrompt = contextBuilder.buildMinimalPlaywrightPrompt(
                    sampleStory,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(minimalPrompt.length()).isLessThan(fullPrompt.length() / 3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML CLEANING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HTML Cleaning")
    class HtmlCleaningTests {

        @Test
        @DisplayName("Should remove HTML tags")
        void shouldRemoveHtmlTags() {
            // Arrange
            String htmlText = "<p>This is <strong>bold</strong> text</p>";

            // Act
            String cleaned = contextBuilder.cleanJiraHtml(htmlText);

            // Assert
            assertThat(cleaned).isEqualTo("This is bold text");
            assertThat(cleaned).doesNotContain("<p>");
            assertThat(cleaned).doesNotContain("<strong>");
        }

        @Test
        @DisplayName("Should decode HTML entities")
        void shouldDecodeHtmlEntities() {
            // Arrange
            String htmlText = "Price&nbsp;is&nbsp;&lt;$100&gt;&amp;&quot;free&quot;";

            // Act
            String cleaned = contextBuilder.cleanJiraHtml(htmlText);

            // Assert
            assertThat(cleaned).isEqualTo("Price is <$100>&\"free\"");
        }

        @Test
        @DisplayName("Should remove JIRA markup")
        void shouldRemoveJiraMarkup() {
            // Arrange
            String jiraText = "{code}some code{code} and {quote}a quote{quote}";

            // Act
            String cleaned = contextBuilder.cleanJiraHtml(jiraText);

            // Assert
            assertThat(cleaned).isEqualTo("some code and a quote");
            assertThat(cleaned).doesNotContain("{code}");
            assertThat(cleaned).doesNotContain("{quote}");
        }

        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNullInput() {
            // Act
            String cleaned = contextBuilder.cleanJiraHtml(null);

            // Assert
            assertThat(cleaned).isEmpty();
        }

        @Test
        @DisplayName("Should normalize whitespace")
        void shouldNormalizeWhitespace() {
            // Arrange
            String messyText = "Text  with    multiple   spaces\n\n\n\nand newlines";

            // Act
            String cleaned = contextBuilder.cleanJiraHtml(messyText);

            // Assert
            assertThat(cleaned).isEqualTo("Text with multiple spaces\n\nand newlines");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethodTests {

        @Test
        @DisplayName("Should generate prompt summary")
        void shouldGeneratePromptSummary() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Act
            String summary = contextBuilder.getPromptSummary(
                    sampleStory,
                    AIGeneratedTest.TestType.UI,
                    prompt
            );

            // Assert
            assertThat(summary).contains("SCRUM-7");
            assertThat(summary).contains("UI Test");
            assertThat(summary).contains("Playwright");
            assertThat(summary).contains("sections");
            assertThat(summary).contains("chars");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTEGRATION-LIKE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("Should generate prompt for API test type")
        void shouldGeneratePromptForApiTest() {
            // Arrange
            when(acParser.parse(any())).thenReturn(numberedAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.API
            );

            // Assert
            assertThat(prompt).contains("API test");
            assertThat(prompt).contains("Playwright");
        }

        @Test
        @DisplayName("Should handle complex story with all optional fields")
        void shouldHandleComplexStory() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    "Custom user instructions here",
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("Custom user instructions");
            assertThat(prompt).contains("SCRUM-7");
            assertThat(prompt).contains("authentication");
            assertThat(prompt).contains("Frontend");
            assertThat(prompt).contains("john.doe@example.com");
        }

        @Test
        @DisplayName("Should generate different prompts for different test types")
        void shouldGenerateDifferentPromptsForDifferentTestTypes() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String uiPrompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory, null, AIGeneratedTest.TestType.UI
            );
            String apiPrompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory, null, AIGeneratedTest.TestType.API
            );

            // Assert
            assertThat(uiPrompt).contains("UI test");
            assertThat(apiPrompt).contains("API test");
            assertThat(uiPrompt).isNotEqualTo(apiPrompt);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FRAMEWORK CONTEXT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Framework Context")
    class FrameworkContextTests {

        @Test
        @DisplayName("Should include Java 17 and Gradle")
        void shouldIncludeJavaAndGradle() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("Java 17");
            assertThat(prompt).contains("Gradle");
        }

        @Test
        @DisplayName("Should include Playwright library version")
        void shouldIncludePlaywrightVersion() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).contains("com.microsoft.playwright");
            assertThat(prompt).contains("1.41.0");
        }

        @Test
        @DisplayName("Should mention JUnit 5 or TestNG")
        void shouldMentionTestFrameworks() {
            // Arrange
            when(acParser.parse(any())).thenReturn(gherkinAC);

            // Act
            String prompt = contextBuilder.buildPlaywrightTestPrompt(
                    sampleStory,
                    null,
                    AIGeneratedTest.TestType.UI
            );

            // Assert
            assertThat(prompt).containsAnyOf("JUnit 5", "TestNG");
        }
    }
}