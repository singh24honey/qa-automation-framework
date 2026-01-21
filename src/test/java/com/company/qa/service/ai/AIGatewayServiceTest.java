package com.company.qa.service.ai;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.audit.AuditLogService;
import com.company.qa.service.security.DataSanitizerService;
import com.company.qa.service.security.RateLimiterService;
import com.company.qa.service.security.RateLimiterService.RateLimitResult;
import com.company.qa.service.security.ResponseValidator;
import com.company.qa.service.security.ResponseValidator.ResponseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI Gateway Service Tests")
class AIGatewayServiceTest {

    @Mock
    private AIService aiService;

    @Mock
    private DataSanitizerService sanitizerService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private ResponseValidator responseValidator;

    @Mock
    private AuditLogService auditLogService;

    private AIGatewayService gatewayService;

    @BeforeEach
    void setUp() {
        gatewayService = new AIGatewayService(
                aiService,
                sanitizerService,
                rateLimiterService,
                responseValidator,
                auditLogService
        );
    }

    @Test
    @DisplayName("Should successfully generate test when all checks pass")
    void shouldSuccessfullyGenerateTest() {
        // Setup
        SecureAIRequest request = createTestGenerationRequest();

        // Mock rate limit - allowed
        when(rateLimiterService.checkRateLimit(any(UUID.class), any(UserRole.class)))
                .thenReturn(new RateLimitResult(true, 49, 50, Instant.now().plusSeconds(3600)));

        // Mock sanitization - clean
        SanitizationResult sanitizationResult = SanitizationResult.builder()
                .sanitizedContent("Clean test description")
                .originalContent("Clean test description")
                .dataRedacted(false)
                .shouldBlock(false)
                .timestamp(Instant.now())
                .build();
        when(sanitizerService.sanitize(any(SanitizationRequest.class)))
                .thenReturn(sanitizationResult);

        // Mock AI response
        AIResponse aiResponse = AIResponse.builder()
                .content("@Test public void testLogin() {}")
                .tokensUsed(500)
                .build();
        when(aiService.generateTest(any(TestGenerationRequest.class)))
                .thenReturn(aiResponse);

        // Mock validation - valid
        ValidationResult validationResult = ValidationResult.builder()
                .valid(true)
                .shouldBlock(false)
                .build();
        when(responseValidator.validate(anyString(), any(ResponseType.class)))
                .thenReturn(validationResult);

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("@Test public void testLogin() {}");
        assertThat(response.getTokensUsed()).isEqualTo(500);
        assertThat(response.isValidationPassed()).isTrue();
        assertThat(response.isRateLimitExceeded()).isFalse();
        assertThat(response.isBlockedBySecurityPolicy()).isFalse();

        // Verify interactions
        verify(rateLimiterService).checkRateLimit(request.getUserId(), request.getUserRole());
        verify(sanitizerService).sanitize(any(SanitizationRequest.class));
        verify(aiService).generateTest(any(TestGenerationRequest.class));
        verify(responseValidator).validate(anyString(), eq(ResponseType.TEST_CODE));
        verify(rateLimiterService).recordTokenUsage(request.getUserId(), 500);
        verify(rateLimiterService).recordCost(eq(request.getUserId()), anyDouble());
        verify(auditLogService).logSuccessfulRequest(any(), eq(request.getUserId()),
                eq("test_generation"), eq(sanitizationResult), eq(aiResponse), anyLong());
    }

    @Test
    @DisplayName("Should block request when rate limit exceeded")
    void shouldBlockRequestWhenRateLimitExceeded() {
        SecureAIRequest request = createTestGenerationRequest();

        // Mock rate limit - exceeded
        Instant resetTime = Instant.now().plusSeconds(3600);
        when(rateLimiterService.checkRateLimit(any(UUID.class), any(UserRole.class)))
                .thenReturn(new RateLimitResult(false, 0, 50, resetTime));

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isRateLimitExceeded()).isTrue();
        assertThat(response.getRateLimitResetTime()).isEqualTo(resetTime);
        assertThat(response.getErrorMessage()).contains("Rate limit exceeded");

        // Should not call AI service
        verify(aiService, never()).generateTest(any());
        verify(auditLogService).logRateLimitExceeded(any(), eq(request.getUserId()),
                eq("test_generation"), eq(0), eq(resetTime));
    }

    @Test
    @DisplayName("Should block request when sensitive data detected")
    void shouldBlockRequestWhenSensitiveDataDetected() {
        SecureAIRequest request = createTestGenerationRequest();

        // Mock rate limit - allowed
        when(rateLimiterService.checkRateLimit(any(UUID.class), any(UserRole.class)))
                .thenReturn(new RateLimitResult(true, 49, 50, Instant.now().plusSeconds(3600)));

        // Mock sanitization - blocked
        SanitizationResult sanitizationResult = SanitizationResult.builder()
                .sanitizedContent("[REDACTED]")
                .originalContent("AWS_KEY=AKIA...")
                .dataRedacted(true)
                .shouldBlock(true)
                .timestamp(Instant.now())
                .build();
        sanitizationResult.addCriticalIssue("Critical secrets detected");

        when(sanitizerService.sanitize(any(SanitizationRequest.class)))
                .thenReturn(sanitizationResult);

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isBlockedBySecurityPolicy()).isTrue();
        assertThat(response.getErrorMessage()).contains("sensitive data");

        // Should not call AI service
        verify(aiService, never()).generateTest(any());
        verify(auditLogService).logBlockedRequest(any(), eq(request.getUserId()),
                eq("test_generation"), eq("Sensitive data detected"), eq(sanitizationResult));
    }

    @Test
    @DisplayName("Should block response when validation fails")
    void shouldBlockResponseWhenValidationFails() {
        SecureAIRequest request = createTestGenerationRequest();

        // Mock rate limit - allowed
        when(rateLimiterService.checkRateLimit(any(UUID.class), any(UserRole.class)))
                .thenReturn(new RateLimitResult(true, 49, 50, Instant.now().plusSeconds(3600)));

        // Mock sanitization - clean
        SanitizationResult sanitizationResult = SanitizationResult.builder()
                .sanitizedContent("Clean content")
                .dataRedacted(false)
                .shouldBlock(false)
                .timestamp(Instant.now())
                .build();
        when(sanitizerService.sanitize(any(SanitizationRequest.class)))
                .thenReturn(sanitizationResult);

        // Mock AI response with dangerous code
        AIResponse aiResponse = AIResponse.builder()
                .content("Runtime.getRuntime().exec(\"rm -rf /\")")
                .tokensUsed(500)
                .build();
        when(aiService.generateTest(any(TestGenerationRequest.class)))
                .thenReturn(aiResponse);

        // Mock validation - failed
        ValidationResult validationResult = ValidationResult.builder()
                .valid(false)
                .shouldBlock(true)
                .build();
        when(responseValidator.validate(anyString(), any(ResponseType.class)))
                .thenReturn(validationResult);

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isValidationPassed()).isFalse();
        assertThat(response.getErrorMessage()).contains("validation");

        verify(auditLogService).logValidationFailure(any(), eq(request.getUserId()),
                eq("test_generation"), eq(validationResult));
    }

    @Test
    @DisplayName("Should successfully analyze failure")
    void shouldSuccessfullyAnalyzeFailure() {
        // Setup
        SecureAIRequest request = createFailureAnalysisRequest();

        // Mock rate limit - allowed
        when(rateLimiterService.checkRateLimit(any(UUID.class), any(UserRole.class)))
                .thenReturn(new RateLimitResult(true, 49, 50, Instant.now().plusSeconds(3600)));

        // Mock sanitization
        SanitizationResult sanitizationResult = SanitizationResult.builder()
                .sanitizedContent("NullPointerException at line 42")
                .dataRedacted(false)
                .shouldBlock(false)
                .timestamp(Instant.now())
                .build();
        when(sanitizerService.sanitize(any(SanitizationRequest.class)))
                .thenReturn(sanitizationResult);

        // Mock AI response
        AIResponse aiResponse = AIResponse.builder()
                .content("The error is caused by a null check missing...")
                .tokensUsed(300)
                .build();
        when(aiService.analyzeFailure(any(FailureAnalysisRequest.class)))
                .thenReturn(aiResponse);

        // Mock validation
        ValidationResult validationResult = ValidationResult.builder()
                .valid(true)
                .shouldBlock(false)
                .build();
        when(responseValidator.validate(anyString(), any(ResponseType.class)))
                .thenReturn(validationResult);

        // Execute
        SecureAIResponse response = gatewayService.analyzeFailure(request);

        // Verify
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).contains("null check");
        assertThat(response.getTokensUsed()).isEqualTo(300);

        verify(aiService).analyzeFailure(any(FailureAnalysisRequest.class));
        verify(responseValidator).validate(anyString(), eq(ResponseType.ANALYSIS));
    }

    @Test
    @DisplayName("Should record token usage and cost")
    void shouldRecordTokenUsageAndCost() {
        SecureAIRequest request = createTestGenerationRequest();

        setupSuccessfulMocks();

        // Execute
        gatewayService.generateTest(request);

        // Verify
        verify(rateLimiterService).recordTokenUsage(eq(request.getUserId()), eq(500));
        verify(rateLimiterService).recordCost(eq(request.getUserId()), anyDouble());
    }

    @Test
    @DisplayName("Should handle exceptions gracefully")
    void shouldHandleExceptionsGracefully() {
        SecureAIRequest request = createTestGenerationRequest();

        when(rateLimiterService.checkRateLimit(any(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // Execute
        SecureAIResponse response = gatewayService.generateTest(request);

        // Verify
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("error occurred");
        verify(auditLogService).logError(any(), eq(request.getUserId()),
                eq("test_generation"), anyString());
    }

    // Helper methods

    private SecureAIRequest createTestGenerationRequest() {
        return SecureAIRequest.builder()
                .userId(UUID.randomUUID())
                .userRole(UserRole.QA_ENGINEER)
                .content("Generate test for login page")
                .framework("JUnit 5")
                .language("Java")
                .targetUrl("https://example.com/login")
                .strictMode(true)
                .operationType(SecureAIRequest.OperationType.TEST_GENERATION)
                .build();
    }

    private SecureAIRequest createFailureAnalysisRequest() {
        return SecureAIRequest.builder()
                .userId(UUID.randomUUID())
                .userRole(UserRole.QA_ENGINEER)
                .content("NullPointerException in login test")
                .testName("testLogin")
                .stackTrace("at LoginTest.java:42")
                .executionId(UUID.randomUUID())
                .strictMode(true)
                .operationType(SecureAIRequest.OperationType.FAILURE_ANALYSIS)
                .build();
    }

    private void setupSuccessfulMocks() {
        when(rateLimiterService.checkRateLimit(any(UUID.class), any(UserRole.class)))
                .thenReturn(new RateLimitResult(true, 49, 50, Instant.now().plusSeconds(3600)));

        SanitizationResult sanitizationResult = SanitizationResult.builder()
                .sanitizedContent("Clean content")
                .dataRedacted(false)
                .shouldBlock(false)
                .timestamp(Instant.now())
                .build();
        when(sanitizerService.sanitize(any(SanitizationRequest.class)))
                .thenReturn(sanitizationResult);

        AIResponse aiResponse = AIResponse.builder()
                .content("Test code")
                .tokensUsed(500)
                .build();
        when(aiService.generateTest(any(TestGenerationRequest.class)))
                .thenReturn(aiResponse);

        ValidationResult validationResult = ValidationResult.builder()
                .valid(true)
                .shouldBlock(false)
                .build();
        when(responseValidator.validate(anyString(), any(ResponseType.class)))
                .thenReturn(validationResult);
    }
}