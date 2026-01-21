package com.company.qa.service.security;

import com.company.qa.model.dto.SanitizationRequest;
import com.company.qa.model.dto.SanitizationResult;
import com.company.qa.model.dto.ValidationResult;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.security.RateLimiterService.RateLimitResult;
import com.company.qa.service.security.ResponseValidator.ResponseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for security components working together.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest {

    @Autowired
    private DataSanitizerService sanitizerService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ResponseValidator responseValidator;

    @Test
    @DisplayName("Should sanitize input and validate output in complete flow")
    void shouldSanitizeInputAndValidateOutput() {
        // Step 1: Sanitize user input
        String userInput = "Test the login page at admin@example.com with password=Secret123";

        SanitizationRequest sanitizationRequest = SanitizationRequest.builder()
                .content(userInput)
                .userId(UUID.randomUUID())
                .context("test_generation")
                .strictMode(true)
                .build();

        SanitizationResult sanitizationResult = sanitizerService.sanitize(sanitizationRequest);

        assertThat(sanitizationResult.isDataRedacted()).isTrue();
        assertThat(sanitizationResult.getSanitizedContent()).doesNotContain("admin@example.com");
        assertThat(sanitizationResult.getSanitizedContent()).doesNotContain("Secret123");

        // Step 2: Check rate limit
        UUID userId = UUID.randomUUID();
        RateLimitResult rateLimitResult = rateLimiterService.checkRateLimit(userId, UserRole.QA_ENGINEER);

        assertThat(rateLimitResult.allowed()).isTrue();

        // Step 3: Record token usage
        rateLimiterService.recordTokenUsage(userId, 1000);
        long tokenUsage = rateLimiterService.getTokenUsage(userId);
        assertThat(tokenUsage).isGreaterThanOrEqualTo(1000L);

        // Step 4: Validate AI response
        String aiResponse = """
                @Test
                public void testLogin() {
                    driver.findElement(By.id("username")).sendKeys("testuser");
                    driver.findElement(By.id("password")).sendKeys("testpass");
                    driver.findElement(By.id("login")).click();
                }
                """;

        ValidationResult validationResult = responseValidator.validate(aiResponse, ResponseType.TEST_CODE);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.isShouldBlock()).isFalse();
    }

    @Test
    @DisplayName("Should block dangerous response even after successful sanitization")
    void shouldBlockDangerousResponse() {
        // Step 1: Input passes sanitization
        String userInput = "Generate a test for file cleanup";

        SanitizationRequest request = SanitizationRequest.builder()
                .content(userInput)
                .userId(UUID.randomUUID())
                .context("test_generation")
                .build();

        SanitizationResult sanitizationResult = sanitizerService.sanitize(request);
        assertThat(sanitizationResult.isShouldBlock()).isFalse();

        // Step 2: AI generates dangerous code
        String dangerousResponse = """
                @Test
                public void testCleanup() {
                    Runtime.getRuntime().exec("rm -rf /tmp/*");
                }
                """;

        ValidationResult validationResult = responseValidator.validate(dangerousResponse, ResponseType.TEST_CODE);

        // Step 3: Validation should block it
        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.isShouldBlock()).isTrue();
    }

    @Test
    @DisplayName("Should enforce rate limits across multiple requests")
    void shouldEnforceRateLimitsAcrossMultipleRequests() {
        UUID userId = UUID.randomUUID();
        UserRole role = UserRole.DEVELOPER; // 50 requests/hour limit

        // Make 50 requests - all should succeed
        for (int i = 0; i < 50; i++) {
            RateLimitResult result = rateLimiterService.checkRateLimit(userId, role);
            assertThat(result.allowed()).isTrue();
        }

        // 51st request should fail
        RateLimitResult finalResult = rateLimiterService.checkRateLimit(userId, role);
        assertThat(finalResult.allowed()).isFalse();
        assertThat(finalResult.remainingRequests()).isZero();
    }

    @Test
    @DisplayName("Should handle complete security pipeline with blocked secrets")
    void shouldHandleCompletePipelineWithBlockedSecrets() {
        // Input contains AWS keys - should be blocked in strict mode
        String dangerousInput = "Test configuration: AWS_KEY=AKIAIOSFODNN7EXAMPLE";

        SanitizationRequest request = SanitizationRequest.builder()
                .content(dangerousInput)
                .userId(UUID.randomUUID())
                .context("test_generation")
                .strictMode(true)
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.isShouldBlock()).isTrue();
        assertThat(result.getSecretRedactionCount()).isGreaterThan(0);
        assertThat(result.getCriticalIssues()).isNotEmpty();
    }

    @Test
    @DisplayName("Should track costs across multiple operations")
    void shouldTrackCostsAcrossMultipleOperations() {
        UUID userId = UUID.randomUUID();

        // Record multiple costs
        rateLimiterService.recordCost(userId, 1.50);
        rateLimiterService.recordCost(userId, 2.25);
        rateLimiterService.recordCost(userId, 0.75);

        // Total should be sum
        double totalCost = rateLimiterService.getCurrentCost(userId);
        assertThat(totalCost).isGreaterThanOrEqualTo(4.50);
    }
}