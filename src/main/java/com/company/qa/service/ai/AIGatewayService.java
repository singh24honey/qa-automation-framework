package com.company.qa.service.ai;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.approval.ApprovalRequestService;
import com.company.qa.service.audit.AuditLogService;
import com.company.qa.service.security.DataSanitizerService;
import com.company.qa.service.security.RateLimiterService;
import com.company.qa.service.security.RateLimiterService.RateLimitResult;
import com.company.qa.service.security.ResponseValidator;
import com.company.qa.service.security.ResponseValidator.ResponseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Secure AI Gateway Service - SINGLE ENTRY POINT for all AI interactions.
 *
 * This service wraps the existing AIService with security layers:
 * - Rate limiting per user/role
 * - Data sanitization (PII/secret removal)
 * - Response validation
 * - Complete audit logging
 *
 * CRITICAL: All AI requests MUST go through this gateway.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIGatewayService {

    private final AIService aiService;
    private final DataSanitizerService sanitizerService;
    private final RateLimiterService rateLimiterService;
    private final ResponseValidator responseValidator;
    private final AuditLogService auditLogService;
    // Add this field to AIGatewayService
    private final ApprovalRequestService approvalRequestService;

    /**
     * Generate a test through the secure gateway.
     *
     * @param request Secure AI request
     * @return Secure AI response
     */
    public SecureAIResponse generateTest(SecureAIRequest request) {
        log.info("Secure AI Gateway: Test generation request from user {}", request.getUserId());

        UUID requestId = UUID.randomUUID();
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Check rate limits
            RateLimitResult rateLimitResult = rateLimiterService.checkRateLimit(
                    request.getUserId(), request.getUserRole());

            if (!rateLimitResult.allowed()) {
                log.warn("Rate limit exceeded for user {}", request.getUserId());
                auditLogService.logRateLimitExceeded(requestId, request.getUserId(),
                        "test_generation", rateLimitResult.remainingRequests(),
                        rateLimitResult.resetTime());
                return createRateLimitResponse(requestId, rateLimitResult);
            }

            // Step 2: Sanitize input
            SanitizationResult sanitizationResult = sanitizerService.sanitize(
                    SanitizationRequest.builder()
                            .content(request.getContent())
                            .userId(request.getUserId())
                            .context("test_generation")
                            .strictMode(request.isStrictMode())
                            .build());

            if (sanitizationResult.isShouldBlock()) {
                log.warn("Request blocked due to sensitive data for user {}", request.getUserId());
                auditLogService.logBlockedRequest(requestId, request.getUserId(),
                        "test_generation", "Sensitive data detected", sanitizationResult);
                return createBlockedResponse(requestId, "Request contains sensitive data that cannot be processed");
            }

            // Step 3: Call AI service with sanitized content
            TestGenerationRequest aiRequest = TestGenerationRequest.builder()
                    .description(sanitizationResult.getSanitizedContent())
                    .framework(request.getFramework())
                    .language(request.getLanguage())
                    .targetUrl(request.getTargetUrl())
                    .build();

            AIResponse aiResponse = aiService.generateTest(aiRequest);

            // Step 4: Validate response
            ValidationResult validationResult = responseValidator.validate(
                    aiResponse.getContent(), ResponseType.TEST_CODE);

            if (!validationResult.isValid() || validationResult.isShouldBlock()) {
                log.warn("AI response failed validation for user {}", request.getUserId());
                auditLogService.logValidationFailure(requestId, request.getUserId(),
                        "test_generation", validationResult);
                return createValidationFailedResponse(requestId, validationResult);
            }

            // Step 5: Record usage
            rateLimiterService.recordTokenUsage(request.getUserId(), aiResponse.getTokensUsed());
            double estimatedCost = calculateCost(aiResponse.getTokensUsed());
            rateLimiterService.recordCost(request.getUserId(), estimatedCost);

            // Step 6: Log success
            long duration = System.currentTimeMillis() - startTime;
            auditLogService.logSuccessfulRequest(requestId, request.getUserId(),
                    "test_generation", sanitizationResult, aiResponse, duration);

            // Step 7: Return secure response
            return SecureAIResponse.builder()
                    .requestId(requestId)
                    .success(true)
                    .content(aiResponse.getContent())
                    .tokensUsed(aiResponse.getTokensUsed())
                    .estimatedCost(estimatedCost)
                    .sanitizationApplied(sanitizationResult.isDataRedacted())
                    .redactionCount(sanitizationResult.getTotalRedactionCount())
                    .validationPassed(true)
                    .processingTimeMs(duration)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Error in AI Gateway for user {}: {}", request.getUserId(), e.getMessage(), e);
            auditLogService.logError(requestId, request.getUserId(), "test_generation", e.getMessage());
            return createErrorResponse(requestId, "An error occurred processing your request");
        }
    }

    /**
     * Analyze a test failure through the secure gateway.
     *
     * @param request Secure AI request
     * @return Secure AI response
     */
    public SecureAIResponse analyzeFailure(SecureAIRequest request) {
        log.info("Secure AI Gateway: Failure analysis request from user {}", request.getUserId());

        UUID requestId = UUID.randomUUID();
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Check rate limits
            RateLimitResult rateLimitResult = rateLimiterService.checkRateLimit(
                    request.getUserId(), request.getUserRole());

            if (!rateLimitResult.allowed()) {
                auditLogService.logRateLimitExceeded(requestId, request.getUserId(),
                        "failure_analysis", rateLimitResult.remainingRequests(),
                        rateLimitResult.resetTime());
                return createRateLimitResponse(requestId, rateLimitResult);
            }

            // Step 2: Sanitize input (less strict for analysis - we need error details)
            SanitizationResult sanitizationResult = sanitizerService.sanitize(
                    SanitizationRequest.builder()
                            .content(request.getContent())
                            .userId(request.getUserId())
                            .context("failure_analysis")
                            .strictMode(false) // More permissive for error logs
                            .build());

            // Step 3: Call AI service
            FailureAnalysisRequest aiRequest = FailureAnalysisRequest.builder()
                    .testName(request.getTestName())
                    .errorMessage(sanitizationResult.getSanitizedContent())
                    .stackTrace(request.getStackTrace())
                    .executionId(request.getExecutionId())
                    .build();

            AIResponse aiResponse = aiService.analyzeFailure(aiRequest);

            // Step 4: Validate response
            ValidationResult validationResult = responseValidator.validate(
                    aiResponse.getContent(), ResponseType.ANALYSIS);

            if (!validationResult.isValid() || validationResult.isShouldBlock()) {
                auditLogService.logValidationFailure(requestId, request.getUserId(),
                        "failure_analysis", validationResult);
                return createValidationFailedResponse(requestId, validationResult);
            }

            // Step 5: Record usage and log
            rateLimiterService.recordTokenUsage(request.getUserId(), aiResponse.getTokensUsed());
            double estimatedCost = calculateCost(aiResponse.getTokensUsed());
            rateLimiterService.recordCost(request.getUserId(), estimatedCost);

            long duration = System.currentTimeMillis() - startTime;
            auditLogService.logSuccessfulRequest(requestId, request.getUserId(),
                    "failure_analysis", sanitizationResult, aiResponse, duration);

            return SecureAIResponse.builder()
                    .requestId(requestId)
                    .success(true)
                    .content(aiResponse.getContent())
                    .tokensUsed(aiResponse.getTokensUsed())
                    .estimatedCost(estimatedCost)
                    .sanitizationApplied(sanitizationResult.isDataRedacted())
                    .redactionCount(sanitizationResult.getTotalRedactionCount())
                    .validationPassed(true)
                    .processingTimeMs(duration)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Error in AI Gateway: {}", e.getMessage(), e);
            auditLogService.logError(requestId, request.getUserId(), "failure_analysis", e.getMessage());
            return createErrorResponse(requestId, "An error occurred processing your request");
        }
    }

    /**
     * Calculate estimated cost based on tokens used.
     *
     * Using approximate pricing:
     * - Input: $0.003 per 1K tokens
     * - Output: $0.015 per 1K tokens
     * - Assuming 30/70 split
     */
    private double calculateCost(int tokensUsed) {
        double inputCostPer1K = 0.003;
        double outputCostPer1K = 0.015;

        // Assume 30% input, 70% output
        double inputCost = (tokensUsed * 0.3 * inputCostPer1K / 1000);
        double outputCost = (tokensUsed * 0.7 * outputCostPer1K / 1000);

        return inputCost + outputCost;
    }

    /**
     * Create rate limit exceeded response.
     */
    private SecureAIResponse createRateLimitResponse(UUID requestId, RateLimitResult rateLimitResult) {
        return SecureAIResponse.builder()
                .requestId(requestId)
                .success(false)
                .errorMessage(String.format("Rate limit exceeded. You have used %d/%d requests. " +
                                "Limit resets at %s",
                        rateLimitResult.maxRequests() - rateLimitResult.remainingRequests(),
                        rateLimitResult.maxRequests(),
                        rateLimitResult.resetTime()))
                .rateLimitExceeded(true)
                .rateLimitResetTime(rateLimitResult.resetTime())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create blocked response (sensitive data detected).
     */
    private SecureAIResponse createBlockedResponse(UUID requestId, String reason) {
        return SecureAIResponse.builder()
                .requestId(requestId)
                .success(false)
                .errorMessage(reason)
                .blockedBySecurityPolicy(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create validation failed response.
     */
    private SecureAIResponse createValidationFailedResponse(UUID requestId, ValidationResult validationResult) {
        SecureAIResponse response = SecureAIResponse.builder()
                .requestId(requestId)
                .success(false)
                .errorMessage("AI response failed security validation")
                .validationPassed(false)
                .timestamp(Instant.now())
                .build();

        validationResult.getViolations().forEach(v ->
                response.addValidationError(v.getMessage()));

        return response;
    }

    /**
     * Create generic error response.
     */
    private SecureAIResponse createErrorResponse(UUID requestId, String message) {
        return SecureAIResponse.builder()
                .requestId(requestId)
                .success(false)
                .errorMessage(message)
                .timestamp(Instant.now())
                .build();
    }



    /**
     * Generate test with approval workflow.
     * Creates an approval request instead of returning test directly.
     */
    public SecureAIResponse generateTestWithApproval(SecureAIRequest request) {
        log.info("Secure AI Gateway: Test generation with approval workflow for user {}",
                request.getUserId());

        UUID requestId = UUID.randomUUID();
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Check rate limits
            RateLimitResult rateLimitResult = rateLimiterService.checkRateLimit(
                    request.getUserId(), request.getUserRole());

            if (!rateLimitResult.allowed()) {
                log.warn("Rate limit exceeded for user {}", request.getUserId());
                auditLogService.logRateLimitExceeded(requestId, request.getUserId(),
                        "test_generation_approval", rateLimitResult.remainingRequests(),
                        rateLimitResult.resetTime());
                return createRateLimitResponse(requestId, rateLimitResult);
            }

            // Step 2: Sanitize input
            SanitizationResult sanitizationResult = sanitizerService.sanitize(
                    SanitizationRequest.builder()
                            .content(request.getContent())
                            .userId(request.getUserId())
                            .context("test_generation")
                            .strictMode(request.isStrictMode())
                            .build());

            if (sanitizationResult.isShouldBlock()) {
                log.warn("Request blocked due to sensitive data for user {}", request.getUserId());
                auditLogService.logBlockedRequest(requestId, request.getUserId(),
                        "test_generation_approval", "Sensitive data detected", sanitizationResult);
                return createBlockedResponse(requestId,
                        "Request contains sensitive data that cannot be processed");
            }

            // Step 3: Call AI service with sanitized content
            TestGenerationRequest aiRequest = TestGenerationRequest.builder()
                    .description(sanitizationResult.getSanitizedContent())
                    .framework(request.getFramework())
                    .language(request.getLanguage())
                    .targetUrl(request.getTargetUrl())
                    .build();

            AIResponse aiResponse = aiService.generateTest(aiRequest);

            // Step 4: Validate response
            ValidationResult validationResult = responseValidator.validate(
                    aiResponse.getContent(), ResponseType.TEST_CODE);

            if (!validationResult.isValid() || validationResult.isShouldBlock()) {
                log.warn("AI response failed validation for user {}", request.getUserId());
                auditLogService.logValidationFailure(requestId, request.getUserId(),
                        "test_generation_approval", validationResult);
                return createValidationFailedResponse(requestId, validationResult);
            }

            // Step 5: Record usage
            rateLimiterService.recordTokenUsage(request.getUserId(), aiResponse.getTokensUsed());
            double estimatedCost = calculateCost(aiResponse.getTokensUsed());
            rateLimiterService.recordCost(request.getUserId(), estimatedCost);

            // Step 6: Create approval request instead of returning directly
            CreateApprovalRequestDTO approvalRequest = CreateApprovalRequestDTO.builder()
                    .requestType(ApprovalRequestType.TEST_GENERATION)
                    .generatedContent(aiResponse.getContent())
                    .aiResponseMetadata(Map.of(
                            "tokensUsed", aiResponse.getTokensUsed(),
                            "provider", aiResponse.getProvider().name(),
                            "taskType", aiResponse.getTaskType().name(),
                            "estimatedCost", estimatedCost
                    ))
                    .testName(extractTestName(request.getContent()))
                    .testFramework(request.getFramework())
                    .testLanguage(request.getLanguage())
                    .targetUrl(request.getTargetUrl())
                    .requestedById(request.getUserId())
                    .requestedByName(request.getUserRole().name()) // TODO: Get actual user name
                    .requestedByEmail(null) // TODO: Get from user context
                    .autoExecuteOnApproval(false) // Default: manual execution
                    .expirationDays(7)
                    .sanitizationApplied(sanitizationResult.isDataRedacted())
                    .redactionCount(sanitizationResult.getTotalRedactionCount())
                    .build();

            ApprovalRequestDTO approval = approvalRequestService.createApprovalRequest(approvalRequest);

            // Step 7: Log success
            long duration = System.currentTimeMillis() - startTime;
            auditLogService.logSuccessfulRequest(requestId, request.getUserId(),
                    "test_generation_approval", sanitizationResult, aiResponse, duration);

            // Step 8: Return response indicating approval required
            return SecureAIResponse.builder()
                    .requestId(requestId)
                    .success(true)
                    .content("Test generated successfully. Awaiting approval. Approval Request ID: " +
                            approval.getId())
                    .tokensUsed(aiResponse.getTokensUsed())
                    .estimatedCost(estimatedCost)
                    .sanitizationApplied(sanitizationResult.isDataRedacted())
                    .redactionCount(sanitizationResult.getTotalRedactionCount())
                    .validationPassed(true)
                    .processingTimeMs(duration)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Error in AI Gateway: {}", e.getMessage(), e);
            auditLogService.logError(requestId, request.getUserId(),
                    "test_generation_approval", e.getMessage());
            return createErrorResponse(requestId, "An error occurred processing your request");
        }
    }

    private String extractTestName(String content) {
        // Simple extraction - just take first 50 chars
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
}