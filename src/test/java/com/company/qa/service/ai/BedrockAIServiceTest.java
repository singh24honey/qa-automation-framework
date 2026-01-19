package com.company.qa.service.ai;

import com.company.qa.config.AIConfig;
import com.company.qa.model.dto.AIResponse;
import com.company.qa.model.dto.FailureAnalysisRequest;
import com.company.qa.model.dto.TestGenerationRequest;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BedrockAIService with Amazon Nova support.
 *
 * Tests run against real AWS Bedrock when credentials are available.
 *
 * Enable with:
 *   export AWS_ACCESS_KEY_ID=your-key
 *   export AWS_SECRET_ACCESS_KEY=your-secret
 *   export BEDROCK_MODEL=us.amazon.nova-micro-v1:0
 *
 * Recommended Models:
 *   - us.amazon.nova-micro-v1:0 (cheapest, good for simple tasks)
 *   - us.amazon.nova-lite-v1:0 (balanced, RECOMMENDED)
 *   - us.amazon.nova-pro-v1:0 (highest quality)
 *   - anthropic.claude-3-sonnet-20240229-v1:0 (if marketplace approved)
 */
@ActiveProfiles("dev")
class BedrockAIServiceTest {

    private AIConfig aiConfig;
    private ObjectMapper objectMapper;
    private BedrockAIService aiService;

    // Default to Nova Lite - balanced cost and quality
    private static final String DEFAULT_MODEL = "us.amazon.nova-2-lite-v1:0";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Configure for testing
        aiConfig = new AIConfig();
        aiConfig.setProvider("bedrock");

        AIConfig.BedrockConfig bedrockConfig = new AIConfig.BedrockConfig();
        bedrockConfig.setEnabled(true);
        bedrockConfig.setRegion(getEnvOrDefault("AWS_REGION", "us-east-1"));

        // Use environment variable for model, or default to Nova Lite
        bedrockConfig.setModel(getEnvOrDefault("BEDROCK_MODEL", DEFAULT_MODEL));
        bedrockConfig.setMaxTokens(2048);
        bedrockConfig.setTemperature(0.7);

        // Get credentials from environment
        bedrockConfig.setAccessKeyId("AKIAWSTSBPYDSFTZSOVC");
        bedrockConfig.setSecretAccessKey("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");

        aiConfig.setBedrock(bedrockConfig);

        aiService = new BedrockAIService(aiConfig, objectMapper);

        // ⚠️ CRITICAL: Manually call init() since @PostConstruct doesn't work in unit tests
        aiService.init();
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

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
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    @DisplayName("Should check availability with real credentials")
    void isAvailable_WithRealCredentials_ReturnsTrue() {
        // When
        boolean available = aiService.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("Should generate test with real Bedrock")
    void generateTest_WithRealBedrock_ReturnsCode() {
        // Given - verify client is initialized
        assertThat(aiService.isAvailable()).isTrue();

        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Test login functionality with username and password")
                .targetUrl("https://example.com/login")
                .framework("SELENIUM")
                .language("java")
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

        System.out.println("=".repeat(80));
        //System.out.println("Model: " + response.getModel());
        System.out.println("Tokens Used: " + response.getTokensUsed());
        System.out.println("Duration: " + response.getDurationMs() + "ms");
        System.out.println("=".repeat(80));
        System.out.println("Generated Test:");
        System.out.println(response.getContent());
        System.out.println("=".repeat(80));
    }

    @Test
    @DisplayName("Should analyze failure with real Bedrock")
     void analyzeFailure_WithRealBedrock_ReturnsAnalysis() {
        // Given
        assertThat(aiService.isAvailable()).isTrue();

        FailureAnalysisRequest request = FailureAnalysisRequest.builder()
                .testName("LoginTest")
                .errorMessage("Element not found: #submit-button")
                .stackTrace("NoSuchElementException at LoginPage.clickSubmit()")
                .testCode("driver.findElement(By.id(\"submit-button\")).click();")
                .build();

        // When
        AIResponse response = aiService.analyzeFailure(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo(AIProvider.BEDROCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.FAILURE_ANALYSIS);

        System.out.println("=".repeat(80));
        System.out.println("Failure Analysis:");
        System.out.println(response.getContent());
        System.out.println("=".repeat(80));
    }
}