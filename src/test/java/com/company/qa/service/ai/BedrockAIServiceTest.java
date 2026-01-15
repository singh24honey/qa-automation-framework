package com.company.qa.service.ai;

import com.company.qa.config.AIConfig;
import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit and Integration tests for BedrockAIService
 *
 * Unit tests (no AWS required):
 * - Provider identification
 * - Service instantiation
 * - Configuration validation
 *
 * Integration tests (AWS required):
 * - Real Bedrock API calls
 * - Test generation
 * - Failure analysis
 * - Fix suggestions
 *
 * To run integration tests, set environment variables:
 * - AWS_ACCESS_KEY_ID=your-access-key
 * - AWS_SECRET_ACCESS_KEY=your-secret-key
 * - AWS_REGION=us-east-1 (optional, defaults to us-east-1)
 */
class BedrockAIServiceTest {

    private AIConfig aiConfig;
    private ObjectMapper objectMapper;
    private BedrockAIService aiService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Configure for testing
        aiConfig = new AIConfig();
        aiConfig.setProvider("bedrock");

        AIConfig.BedrockConfig bedrockConfig = new AIConfig.BedrockConfig();
        bedrockConfig.setEnabled(true);
        bedrockConfig.setRegion(System.getenv("AWS_REGION") != null ?
                System.getenv("AWS_REGION") : "us-east-1");
        bedrockConfig.setModel("anthropic.claude-3-5-sonnet-20240620-v1:0");
        bedrockConfig.setMaxTokens(1000);
        bedrockConfig.setTemperature(0.7);
        bedrockConfig.setTimeout(120); // Add timeout configuration

        // Get credentials from environment
        bedrockConfig.setAccessKeyId(System.getenv("AWS_ACCESS_KEY_ID"));
        bedrockConfig.setSecretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"));

        aiConfig.setBedrock(bedrockConfig);

        aiService = new BedrockAIService(aiConfig, objectMapper);
    }

    // ========== Unit Tests (No AWS Required) ==========

    @Test
    @DisplayName("Should return BEDROCK as provider")
    void getProvider_ReturnsBedrock() {
        // When
        AIProvider provider = aiService.getProvider();

        // Then
        assertThat(provider).isEqualTo(AIProvider.BEDROCK);
    }

    @Test
    @DisplayName("Service should compile and instantiate")
    void service_Compiles() {
        // Given/When/Then
        assertThat(aiService).isNotNull();
        assertThat(aiService.getProvider()).isEqualTo(AIProvider.BEDROCK);
    }

    @Test
    @DisplayName("Should have correct configuration")
    void configuration_IsCorrect() {
        // Then
        assertThat(aiConfig.getBedrock().isEnabled()).isTrue();
        assertThat(aiConfig.getBedrock().getRegion()).isNotEmpty();
        assertThat(aiConfig.getBedrock().getModel()).contains("claude");
        assertThat(aiConfig.getBedrock().getMaxTokens()).isGreaterThan(0);
        assertThat(aiConfig.getBedrock().getTimeout()).isEqualTo(120);
    }

    @Test
    @DisplayName("Should return false for availability when not initialized")
    void isAvailable_WhenNotInitialized_ReturnsFalse() {
        // Given - service not initialized (init() not called)

        // When
        boolean available = aiService.isAvailable();

        // Then
        assertThat(available).isFalse();
    }

    // ========== Integration Tests (AWS Required) ==========

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should initialize and be available with real credentials")
    void isAvailable_WithRealCredentials_ReturnsTrue() {
        // Given
        aiService.init();

        // When
        boolean available = aiService.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should generate test with real Bedrock")
    void generateTest_WithRealBedrock_ReturnsCode() {
        // Given
        aiService.init();

        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test login functionality with username and password")
                .targetUrl("https://example.com/login")
                .framework("SELENIUM")
                .language("java")
                .additionalContext("Use explicit waits for elements")
                .build();

        // When
        AIResponse response = aiService.generateTest(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo(AIProvider.BEDROCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.TEST_GENERATION);
        assertThat(response.getTokensUsed()).isGreaterThan(0);

        // Verify generated content contains expected elements
        String content = response.getContent().toLowerCase();
        assertThat(content).containsAnyOf("selenium", "webdriver", "driver", "test");

        System.out.println("Generated Test Code:");
        System.out.println(response.getContent());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should analyze failure with real Bedrock")
    void analyzeFailure_WithRealBedrock_ReturnsAnalysis() {
        // Given
        aiService.init();

        UUID executionId = UUID.randomUUID();
        FailureAnalysisRequest request = FailureAnalysisRequest.builder()
                .testName("LoginTest.testSuccessfulLogin")
                .errorMessage("NoSuchElementException: Unable to locate element: #login-button")
                .stackTrace("org.openqa.selenium.NoSuchElementException: no such element\n" +
                        "  at LoginTest.testSuccessfulLogin(LoginTest.java:45)\n" +
                        "  at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)")
                .testCode("@Test\npublic void testSuccessfulLogin() {\n" +
                        "  driver.findElement(By.id(\"login-button\")).click();\n" +
                        "}")
                .executionId(executionId)
                .build();

        // When
        AIResponse response = aiService.analyzeFailure(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo(AIProvider.BEDROCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.FAILURE_ANALYSIS);

        // Verify analysis contains expected elements
        String content = response.getContent().toLowerCase();
        assertThat(content).containsAnyOf("element", "locator", "selector", "wait", "cause");

        System.out.println("Failure Analysis:");
        System.out.println(response.getContent());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should suggest fix with real Bedrock")
    void suggestFix_WithRealBedrock_ReturnsSuggestion() {
        // Given
        aiService.init();

        String testCode = """
                @Test
                public void testLogin() {
                    driver.get("https://example.com/login");
                    driver.findElement(By.id("username")).sendKeys("user");
                    driver.findElement(By.id("password")).sendKeys("pass");
                    driver.findElement(By.id("login-btn")).click();
                    assertEquals("Dashboard", driver.getTitle());
                }
                """;

        String errorMessage = "TimeoutException: Expected condition failed: " +
                "waiting for element to be clickable: By.id: login-btn";

        // When
        AIResponse response = aiService.suggestFix(testCode, errorMessage);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo(AIProvider.BEDROCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.FIX_SUGGESTION);

        // Verify suggestion contains expected elements
        String content = response.getContent().toLowerCase();
        assertThat(content).containsAnyOf("wait", "timeout", "explicit", "fix", "suggestion");

        System.out.println("Fix Suggestion:");
        System.out.println(response.getContent());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should execute custom AI request with real Bedrock")
    void execute_WithRealBedrock_ReturnsResponse() {
        // Given
        aiService.init();

        AIRequest request = AIRequest.builder()
                .prompt("Explain in 2-3 sentences why explicit waits are better than implicit waits in Selenium.")
                .taskType(AITaskType.GENERAL)
                .maxTokens(500)
                .temperature(0.5)
                .build();

        // When
        AIResponse response = aiService.execute(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo(AIProvider.BEDROCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.GENERAL);

        // Verify response is relevant
        String content = response.getContent().toLowerCase();
        assertThat(content).containsAnyOf("wait", "explicit", "implicit", "selenium");

        System.out.println("Custom Request Response:");
        System.out.println(response.getContent());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should handle test generation with minimal request")
    void generateTest_MinimalRequest_ReturnsCode() {
        // Given
        aiService.init();

        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Click a button")
                .targetUrl("https://example.com")
                .framework("SELENIUM")
                .language("java")
                .build();

        // When
        AIResponse response = aiService.generateTest(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should handle failure analysis with minimal request")
    void analyzeFailure_MinimalRequest_ReturnsAnalysis() {
        // Given
        aiService.init();

        FailureAnalysisRequest request = FailureAnalysisRequest.builder()
                .testName("SimpleTest")
                .errorMessage("Element not found")
                .build();

        // When
        AIResponse response = aiService.analyzeFailure(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should track token usage")
    void generateTest_TracksTokenUsage() {
        // Given
        aiService.init();

        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Simple navigation test")
                .targetUrl("https://example.com")
                .framework("SELENIUM")
                .language("java")
                .build();

        // When
        AIResponse response = aiService.generateTest(request);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTokensUsed()).isNotNull();
        assertThat(response.getTokensUsed()).isGreaterThan(0);

        System.out.println("Tokens used: " + response.getTokensUsed());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should track response duration")
    void generateTest_TracksDuration() {
        // Given
        aiService.init();

        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Simple click test")
                .targetUrl("https://example.com")
                .framework("SELENIUM")
                .language("java")
                .build();

        // When
        AIResponse response = aiService.generateTest(request);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDurationMs()).isNotNull();
        assertThat(response.getDurationMs()).isGreaterThan(0);

        System.out.println("Response time: " + response.getDurationMs() + "ms");
    }
}