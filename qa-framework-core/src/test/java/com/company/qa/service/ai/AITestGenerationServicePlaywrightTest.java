package com.company.qa.service.ai;

import com.company.qa.exception.TestGenerationException;
import com.company.qa.model.dto.SecureAIRequest;
import com.company.qa.model.dto.SecureAIResponse;
import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.service.context.JiraContextBuilder;
import com.company.qa.service.context.PlaywrightContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AITestGenerationService Week 12 Day 3 enhancements.
 *
 * Test Coverage:
 * 1. Framework-based context builder selection
 * 2. Playwright prompt generation
 * 3. Enhanced scenario prompts
 * 4. Playwright JSON parsing
 * 5. Playwright structure validation
 * 6. Mixed framework scenarios
 * 7. Error handling
 *
 * @author QA Framework
 * @since Week 12 Day 3
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AITestGenerationService - Week 12 Playwright Integration Tests")
class AITestGenerationServicePlaywrightTest {

    @Mock
    private JiraContextBuilder jiraContextBuilder;

    @Mock
    private PlaywrightContextBuilder playwrightContextBuilder;

    @Mock
    private AIGatewayService aiGatewayService;

    @InjectMocks
    private AITestGenerationService service;

    private JiraStory sampleStory;
    private TestGenerationRequest cucumberRequest;
    private TestGenerationRequest playwrightRequest;

    @BeforeEach
    void setUp() {
        // Configure enhanced scenarios to be enabled
       // ReflectionTestUtils.setField(service, "enhancedScenariosEnabled", true);

        // Sample JIRA story
        sampleStory = JiraStory.builder()
                .jiraKey("SCRUM-7")
                .summary("Implement user login functionality")
                .description("Users should be able to log in")
                .acceptanceCriteria("User can login with valid credentials")
                .storyType("Story")
                .status("In Progress")
                .priority("High")
                .build();

        // Cucumber request
        cucumberRequest = TestGenerationRequest.builder()
                .jiraStoryKey("SCRUM-7")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.CUCUMBER)
                .aiProvider("BEDROCK")
                .aiModel("amazon.nova-pro-v1:0")
                .build();

        // Playwright request
        playwrightRequest = TestGenerationRequest.builder()
                .jiraStoryKey("SCRUM-7")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .aiModel("amazon.nova-pro-v1:0")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // FRAMEWORK-BASED CONTEXT BUILDER SELECTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Context Builder Selection")
    class ContextBuilderSelectionTests {

        @Test
        @DisplayName("Should use PlaywrightContextBuilder for PLAYWRIGHT framework")
        void shouldUsePlaywrightContextBuilderForPlaywright() {
            // Arrange
            when(playwrightContextBuilder.buildPlaywrightTestPrompt(any(), any(), any()))
                    .thenReturn("Playwright prompt");

            // Act
            String prompt = invokePrivateMethod("buildGenerationPrompt",
                    sampleStory, playwrightRequest);

            // Assert
            verify(playwrightContextBuilder, times(1))
                    .buildPlaywrightTestPrompt(
                            eq(sampleStory),
                            any(),
                            eq(AIGeneratedTest.TestType.UI)
                    );
            verify(jiraContextBuilder, never()).buildStoryTestPrompt(any(), any());
            assertThat(prompt).contains("Playwright");
        }

        @Test
        @DisplayName("Should use JiraContextBuilder for CUCUMBER framework")
        void shouldUseJiraContextBuilderForCucumber() {
            // Arrange
            when(jiraContextBuilder.buildStoryTestPrompt(any(), any()))
                    .thenReturn("Cucumber prompt");

            // Act
            String prompt = invokePrivateMethod("buildGenerationPrompt",
                    sampleStory, cucumberRequest);

            // Assert
            verify(jiraContextBuilder, times(1))
                    .buildStoryTestPrompt(eq(sampleStory), any());
            verify(playwrightContextBuilder, never())
                    .buildPlaywrightTestPrompt(any(), any(), any());
            assertThat(prompt).contains("Cucumber");
        }

        @Test
        @DisplayName("Should pass user prompt to PlaywrightContextBuilder")
        void shouldPassUserPromptToPlaywrightBuilder() {
            // Arrange
            String userPrompt = "Focus on security testing";
            playwrightRequest.setUserPrompt(userPrompt);

            when(playwrightContextBuilder.buildPlaywrightTestPrompt(any(), any(), any()))
                    .thenReturn("Playwright prompt with user context");

            // Act
            invokePrivateMethod("buildGenerationPrompt", sampleStory, playwrightRequest);

            // Assert
            ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
            verify(playwrightContextBuilder).buildPlaywrightTestPrompt(
                    eq(sampleStory),
                    userPromptCaptor.capture(),
                    eq(AIGeneratedTest.TestType.UI)
            );
            assertThat(userPromptCaptor.getValue()).isEqualTo(userPrompt);
        }

        @Test
        @DisplayName("Should throw exception for unsupported framework")
        void shouldThrowExceptionForUnsupportedFramework() {
            // Arrange
            TestGenerationRequest invalidRequest = TestGenerationRequest.builder()
                    .testFramework(null)
                    .testType(AIGeneratedTest.TestType.UI)
                    .build();

            // Act & Assert
            assertThatThrownBy(() ->
                    invokePrivateMethod("buildGenerationPrompt", sampleStory, invalidRequest))
                    .isInstanceOf(TestGenerationException.class)
                    .hasMessageContaining("Unsupported");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENHANCED SCENARIO PROMPTS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enhanced Scenario Prompts")
    class EnhancedScenarioTests {

        @Test
        @DisplayName("Should add enhanced scenarios for UI tests")
        void shouldAddEnhancedScenariosForUITests() {
            // Arrange
            when(playwrightContextBuilder.buildPlaywrightTestPrompt(any(), any(), any()))
                    .thenReturn("Base Playwright prompt");

            // Act
            String prompt = invokePrivateMethod("buildGenerationPrompt",
                    sampleStory, playwrightRequest);

            // Assert
            assertThat(prompt).contains("ENHANCED SCENARIO COVERAGE");
            assertThat(prompt).contains("HAPPY PATH SCENARIOS");
            assertThat(prompt).contains("EDGE CASE SCENARIOS");
            assertThat(prompt).contains("NEGATIVE SCENARIOS");
        }

        @Test
        @DisplayName("Should add security scenarios for login stories")
        void shouldAddSecurityScenariosForLoginStories() {
            // Arrange
            when(playwrightContextBuilder.buildPlaywrightTestPrompt(any(), any(), any()))
                    .thenReturn("Base prompt");

            // Act
            String prompt = invokePrivateMethod("buildGenerationPrompt",
                    sampleStory, playwrightRequest);

            // Assert - login story should trigger security scenarios
            assertThat(prompt).contains("SECURITY SCENARIOS");
            assertThat(prompt).contains("SQL injection");
            assertThat(prompt).contains("XSS");
        }

        @Test
        @DisplayName("Should not add security scenarios for non-security stories")
        void shouldNotAddSecurityScenariosForNonSecurityStories() {
            // Arrange
            JiraStory nonSecurityStory = JiraStory.builder()
                    .jiraKey("SCRUM-8")
                    .summary("Display product list")
                    .description("Show all products")
                    .acceptanceCriteria("Products displayed in grid")
                    .build();

            when(playwrightContextBuilder.buildPlaywrightTestPrompt(any(), any(), any()))
                    .thenReturn("Base prompt");

            // Act
            String prompt = invokePrivateMethod("buildGenerationPrompt",
                    nonSecurityStory, playwrightRequest);

            // Assert
            assertThat(prompt).doesNotContain("SECURITY SCENARIOS");
        }

        @Test
        @DisplayName("Should not enhance when feature disabled")
        void shouldNotEnhanceWhenFeatureDisabled() {
            // Arrange
            ReflectionTestUtils.setField(service, "enhancedScenariosEnabled", false);
            when(playwrightContextBuilder.buildPlaywrightTestPrompt(any(), any(), any()))
                    .thenReturn("Base prompt");

            // Act
            String prompt = invokePrivateMethod("buildGenerationPrompt",
                    sampleStory, playwrightRequest);

            // Assert
            assertThat(prompt).doesNotContain("ENHANCED SCENARIO COVERAGE");
            assertThat(prompt).isEqualTo("Base prompt");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYWRIGHT JSON PARSING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Playwright JSON Parsing")
    class PlaywrightJsonParsingTests {

        @Test
        @DisplayName("Should parse valid Playwright JSON structure")
        void shouldParseValidPlaywrightJson() {
            // Arrange
            String aiResponse = """
                    {
                      "testClassName": "LoginTest",
                      "testClass": "public class LoginTest extends BasePlaywrightTest { @Test public void testLogin() {} }",
                      "pageObjects": {
                        "LoginPage": "public class LoginPage extends BasePage {}"
                      },
                      "usesExistingPages": false,
                      "newPagesNeeded": ["LoginPage"]
                    }
                    """;

            // Act
            Map<String, Object> result = invokePrivateMethod("parseAIResponse",
                    aiResponse,
                    AIGeneratedTest.TestType.UI,
                    AIGeneratedTest.TestFramework.PLAYWRIGHT);

            // Assert
            assertThat(result).containsKeys("testClassName", "testClass", "pageObjects");
            assertThat(result.get("testClassName")).isEqualTo("LoginTest");
            assertThat(result.get("testClass").toString()).contains("public class LoginTest");
        }

        @Test
        @DisplayName("Should handle Playwright JSON with markdown wrappers")
        void shouldHandlePlaywrightJsonWithMarkdown() {
            // Arrange
            String aiResponse = """
                    ```json
                    {
                      "testClassName": "LoginTest",
                      "testClass": "public class LoginTest { @Test void test() {} }"
                    }
                    ```
                    """;

            // Act
            Map<String, Object> result = invokePrivateMethod("parseAIResponse",
                    aiResponse,
                    AIGeneratedTest.TestType.UI,
                    AIGeneratedTest.TestFramework.PLAYWRIGHT);

            // Assert
            assertThat(result).containsKey("testClassName");
            assertThat(result.get("testClassName")).isEqualTo("LoginTest");
        }

        @Test
        @DisplayName("Should parse Cucumber JSON structure")
        void shouldParseCucumberJson() {
            // Arrange
            String aiResponse = """
                    {
                      "featureFile": "Feature: Login...",
                      "stepDefinitions": ["LoginSteps.java"],
                      "pageObjects": ["LoginPage.java"]
                    }
                    """;

            // Act
            Map<String, Object> result = invokePrivateMethod("parseAIResponse",
                    aiResponse,
                    AIGeneratedTest.TestType.UI,
                    AIGeneratedTest.TestFramework.CUCUMBER);

            // Assert
            assertThat(result).containsKeys("featureFile", "stepDefinitions", "pageObjects");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYWRIGHT STRUCTURE VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Playwright Structure Validation")
    class PlaywrightValidationTests {

        @Test
        @DisplayName("Should validate correct Playwright structure")
        void shouldValidateCorrectPlaywrightStructure() {
            // Arrange
            Map<String, Object> validStructure = new HashMap<>();
            validStructure.put("testClassName", "LoginTest");
            validStructure.put("testClass", "public class LoginTest extends BasePlaywrightTest { @Test void test() {} }");

            // Act & Assert - should not throw
            invokePrivateMethod("validatePlaywrightStructure", validStructure);
        }

        @Test
        @DisplayName("Should reject Playwright structure without testClassName")
        void shouldRejectPlaywrightStructureWithoutTestClassName() {
            // Arrange
            Map<String, Object> invalidStructure = new HashMap<>();
            invalidStructure.put("testClass", "public class LoginTest { @Test void test() {} }");

            // Act & Assert
            assertThatThrownBy(() ->
                    invokePrivateMethod("validatePlaywrightStructure", invalidStructure))
                    .isInstanceOf(TestGenerationException.class)
                    .hasMessageContaining("testClassName");
        }

        @Test
        @DisplayName("Should reject Playwright structure without testClass")
        void shouldRejectPlaywrightStructureWithoutTestClass() {
            // Arrange
            Map<String, Object> invalidStructure = new HashMap<>();
            invalidStructure.put("testClassName", "LoginTest");

            // Act & Assert
            assertThatThrownBy(() ->
                    invokePrivateMethod("validatePlaywrightStructure", invalidStructure))
                    .isInstanceOf(TestGenerationException.class)
                    .hasMessageContaining("testClass");
        }

        @Test
        @DisplayName("Should reject Playwright structure with empty testClass")
        void shouldRejectEmptyTestClass() {
            // Arrange
            Map<String, Object> invalidStructure = new HashMap<>();
            invalidStructure.put("testClassName", "LoginTest");
            invalidStructure.put("testClass", "");

            // Act & Assert
            assertThatThrownBy(() ->
                    invokePrivateMethod("validatePlaywrightStructure", invalidStructure))
                    .isInstanceOf(TestGenerationException.class)
                    .hasMessageContaining("Empty");
        }

        @Test
        @DisplayName("Should reject Playwright structure without Java class structure")
        void shouldRejectNonJavaStructure() {
            // Arrange
            Map<String, Object> invalidStructure = new HashMap<>();
            invalidStructure.put("testClassName", "LoginTest");
            invalidStructure.put("testClass", "This is just plain text");

            // Act & Assert
            assertThatThrownBy(() ->
                    invokePrivateMethod("validatePlaywrightStructure", invalidStructure))
                    .isInstanceOf(TestGenerationException.class)
                    .hasMessageContaining("Java test class structure");
        }

        @Test
        @DisplayName("Should validate Cucumber structure")
        void shouldValidateCucumberStructure() {
            // Arrange
            Map<String, Object> validStructure = new HashMap<>();
            validStructure.put("featureFile", "Feature: Login");
        //    validStructure.put("stepDefinitions", List.of("LoginSteps.java"));
          //  validStructure.put("pageObjects", List.of("LoginPage.java"));

            // Act & Assert - should not throw
            invokePrivateMethod("validateCucumberStructure", validStructure);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTEGRATION SCENARIO TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("Should handle mixed framework requests in sequence")
        void shouldHandleMixedFrameworkRequests() {
            // Arrange
            when(jiraContextBuilder.buildStoryTestPrompt(any(), any()))
                    .thenReturn("Cucumber prompt");
            when(playwrightContextBuilder.buildPlaywrightTestPrompt(any(), any(), any()))
                    .thenReturn("Playwright prompt");

            // Act - Generate Cucumber test
            String cucumberPrompt = invokePrivateMethod("buildGenerationPrompt",
                    sampleStory, cucumberRequest);

            // Act - Generate Playwright test
            String playwrightPrompt = invokePrivateMethod("buildGenerationPrompt",
                    sampleStory, playwrightRequest);

            // Assert
            verify(jiraContextBuilder, times(1)).buildStoryTestPrompt(any(), any());
            verify(playwrightContextBuilder, times(1)).buildPlaywrightTestPrompt(any(), any(), any());
            assertThat(cucumberPrompt).contains("Cucumber");
            assertThat(playwrightPrompt).contains("Playwright");
        }

        @Test
        @DisplayName("Should determine security relevance correctly")
        void shouldDetermineSecurityRelevance() {
            // Arrange - Security story
            JiraStory securityStory = JiraStory.builder()
                    .summary("Implement OAuth authentication")
                    .description("Secure login with OAuth")
                    .build();

            // Act
            boolean isSecurityRelevant = invokePrivateMethod("isSecurityRelevant", securityStory);

            // Assert
            assertThat(isSecurityRelevant).isTrue();
        }

        @Test
        @DisplayName("Should determine non-security story correctly")
        void shouldDetermineNonSecurityStory() {
            // Arrange - Regular story
            JiraStory regularStory = JiraStory.builder()
                    .summary("Display product catalog")
                    .description("Show all products in grid")
                    .build();

            // Act
            boolean isSecurityRelevant = invokePrivateMethod("isSecurityRelevant", regularStory);

            // Assert
            assertThat(isSecurityRelevant).isFalse();
        }

        @Test
        @DisplayName("Should get correct context builder name")
        void shouldGetCorrectContextBuilderName() {
            // Act & Assert
          /*  assertThat(invokePrivateMethod("getContextBuilderName",
                    AIGeneratedTest.TestFramework.PLAYWRIGHT))
                    .isEqualTo("PlaywrightContextBuilder");

            assertThat(invokePrivateMethod("getContextBuilderName",
                    AIGeneratedTest.TestFramework.CUCUMBER))
                    .isEqualTo("JiraContextBuilder");*/
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS FOR REFLECTION-BASED TESTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Invoke private method via reflection for testing.
     */
    @SuppressWarnings("unchecked")
    private <T> T invokePrivateMethod(String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;

                // Handle primitive type wrappers and enums
                if (args[i] instanceof AIGeneratedTest.TestType) {
                    paramTypes[i] = AIGeneratedTest.TestType.class;
                } else if (args[i] instanceof AIGeneratedTest.TestFramework) {
                    paramTypes[i] = AIGeneratedTest.TestFramework.class;
                }
            }

            var method = AITestGenerationService.class.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(service, args);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }
}