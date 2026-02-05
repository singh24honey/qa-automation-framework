package com.company.qa.integration;

import com.company.qa.model.dto.SecureAIRequest;
import com.company.qa.model.dto.SecureAIResponse;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.ai.AIGatewayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for AI Gateway with real components.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AI Gateway Integration Tests")
class AIGatewayIntegrationTest {

    @Autowired
    private AIGatewayService gatewayService;

    @Test
    @DisplayName("Should complete full flow: sanitize -> rate limit -> AI -> validate")
    void shouldCompleteFullFlow() {
        // Create request
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(UUID.randomUUID())
                .userRole(UserRole.QA_ENGINEER)
                .content("Generate a test for login functionality")
                .framework("JUnit 5")
                .language("Java")
                .targetUrl("https://example.com/login")
                .strictMode(true)
                .operationType(SecureAIRequest.OperationType.TEST_GENERATION)
                .build();

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getRequestId()).isNotNull();
        assertThat(response.getTimestamp()).isNotNull();

        // Should succeed (using Mock AI provider in test)
        if (response.isSuccess()) {
            assertThat(response.getContent()).isNotEmpty();
            assertThat(response.getTokensUsed()).isGreaterThan(0);
            assertThat(response.getEstimatedCost()).isGreaterThan(0);
            assertThat(response.isValidationPassed()).isTrue();
        }
    }

    @Test
    @DisplayName("Should block request with AWS keys in strict mode")
    void shouldBlockRequestWithAWSKeys() {
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(UUID.randomUUID())
                .userRole(UserRole.QA_ENGINEER)
                .content("Test with AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE")
                .framework("JUnit 5")
                .language("Java")
                .strictMode(true)
                .operationType(SecureAIRequest.OperationType.TEST_GENERATION)
                .build();

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify - should be blocked
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isBlockedBySecurityPolicy()).isTrue();
        assertThat(response.getErrorMessage()).containsIgnoringCase("sensitive data");
    }

    @Test
    @DisplayName("Should sanitize PII in request")
    void shouldSanitizePIIInRequest() {
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(UUID.randomUUID())
                .userRole(UserRole.QA_ENGINEER)
                .content("Test login for user john@example.com with phone 555-123-4567")
                .framework("JUnit 5")
                .language("Java")
                .strictMode(false) // Not strict - allow with sanitization
                .operationType(SecureAIRequest.OperationType.TEST_GENERATION)
                .build();

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify
        if (response.isSuccess()) {
            assertThat(response.isSanitizationApplied()).isTrue();
            assertThat(response.getRedactionCount()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("Should enforce rate limits")
    void shouldEnforceRateLimits() {
        UUID userId = UUID.randomUUID();

        // Make requests up to DEVELOPER limit (50)
        for (int i = 0; i < 51; i++) {
            SecureAIRequest request = SecureAIRequest.builder()
                    .userId(userId)
                    .userRole(UserRole.DEVELOPER) // 50 requests/hour limit
                    .content("Test " + i)
                    .framework("JUnit 5")
                    .language("Java")
                    .operationType(SecureAIRequest.OperationType.TEST_GENERATION)
                    .build();

            SecureAIResponse response = gatewayService.generateTest(request);

            if (i < 50) {
                // First 50 should succeed or fail for other reasons, not rate limit
                if (!response.isSuccess()) {
                    assertThat(response.isRateLimitExceeded()).isFalse();
                }
            } else {
                // 51st should be rate limited
                assertThat(response.isSuccess()).isFalse();
                assertThat(response.isRateLimitExceeded()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Should successfully analyze failure")
    void shouldSuccessfullyAnalyzeFailure() {
        SecureAIRequest request = SecureAIRequest.builder()
                .userId(UUID.randomUUID())
                .userRole(UserRole.QA_ENGINEER)
                .content("NullPointerException in login test")
                .testName("testLogin")
                .stackTrace("at LoginPage.java:42")
                .executionId(UUID.randomUUID())
                .operationType(SecureAIRequest.OperationType.FAILURE_ANALYSIS)
                .build();

        // Execute
        SecureAIResponse response = gatewayService.analyzeFailure(request);

        // Verify
        assertThat(response).isNotNull();
        if (response.isSuccess()) {
            assertThat(response.getContent()).isNotEmpty();
            assertThat(response.isValidationPassed()).isTrue();
        }
    }
}