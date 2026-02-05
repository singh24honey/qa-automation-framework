package com.company.qa.service.ai;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAIServiceTest {

    private MockAIService aiService;

    @BeforeEach
    void setUp() {
        aiService = new MockAIService();
    }

    @Test
    @DisplayName("Should return MOCK as provider")
    void getProvider_ReturnsMock() {
        // When
        AIProvider provider = aiService.getProvider();

        // Then
        assertThat(provider).isEqualTo(AIProvider.MOCK);
    }

    @Test
    @DisplayName("Should always be available")
    void isAvailable_ReturnsTrue() {
        // When
        boolean available = aiService.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("Should generate test code")
    void generateTest_WithValidRequest_ReturnsCode() {
        // Given
        TestGenerationRequest request = TestGenerationRequest.builder()
                .description("Login to application")
                .targetUrl("https://example.com")
                .framework("SELENIUM")
                .language("java")
                .build();

        // When
        AIResponse response = aiService.generateTest(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).contains("@Test");
        assertThat(response.getContent()).contains("driver.get");
        assertThat(response.getProvider()).isEqualTo(AIProvider.MOCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.TEST_GENERATION);
        assertThat(response.getTokensUsed()).isGreaterThan(0);
        assertThat(response.getDurationMs()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should analyze failure")
    void analyzeFailure_WithValidRequest_ReturnsAnalysis() {
        // Given
        FailureAnalysisRequest request = FailureAnalysisRequest.builder()
                .testName("LoginTest")
                .errorMessage("Element not found: #loginButton")
                .stackTrace("NoSuchElementException at line 45")
                .build();

        // When
        AIResponse response = aiService.analyzeFailure(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).contains("Failure Analysis");
        assertThat(response.getContent()).contains("Probable Causes");
        assertThat(response.getContent()).contains("Recommendations");
        assertThat(response.getProvider()).isEqualTo(AIProvider.MOCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.FAILURE_ANALYSIS);
    }

    @Test
    @DisplayName("Should suggest fix for timeout error")
    void suggestFix_WithTimeoutError_ReturnsSuggestions() {
        // Given
        String testCode = "driver.findElement(By.id(\"button\")).click();";
        String errorMessage = "TimeoutException: Element not found";

        // When
        AIResponse response = aiService.suggestFix(testCode, errorMessage);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).containsIgnoringCase("timeout");
        assertThat(response.getContent()).contains("Fix Suggestions");
        assertThat(response.getProvider()).isEqualTo(AIProvider.MOCK);
        assertThat(response.getTaskType()).isEqualTo(AITaskType.FIX_SUGGESTION);
    }

    @Test
    @DisplayName("Should execute custom AI request")
    void execute_WithCustomRequest_ReturnsResponse() {
        // Given
        AIRequest request = AIRequest.builder()
                .taskType(AITaskType.CODE_REVIEW)
                .prompt("Review this test code for best practices")
                .build();

        // When
        AIResponse response = aiService.execute(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).contains("Mock AI response");
        assertThat(response.getTaskType()).isEqualTo(AITaskType.CODE_REVIEW);
    }
}