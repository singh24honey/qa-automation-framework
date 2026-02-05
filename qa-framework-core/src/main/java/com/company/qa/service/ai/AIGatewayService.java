package com.company.qa.service.ai;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
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
import com.company.qa.service.ai.AIUsageTrackingService.AIUsageRequest;

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
    private final AIUsageTrackingService usageTrackingService;

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

            // Step 5: Record usage (existing rate limiter tracking)
            rateLimiterService.recordTokenUsage(request.getUserId(), aiResponse.getTokensUsed());
            double estimatedCost = calculateCost(aiResponse.getTokensUsed());
            rateLimiterService.recordCost(request.getUserId(), estimatedCost);

            // ⭐ NEW Step 5.5: Track AI usage for cost analytics
            long processingTime = System.currentTimeMillis() - startTime;
            trackAIUsage(request, aiResponse, processingTime, true, null);

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

            // ⭐ NEW: Track failed AI usage
            long processingTime = System.currentTimeMillis() - startTime;
            trackAIUsage(request, null, processingTime, false, e.getMessage());

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

    /**
     * Track AI usage for cost analytics.
     * This records every AI request for cost tracking and budget management.
     */
    private void trackAIUsage(SecureAIRequest request, AIResponse aiResponse,
                              long processingTimeMs, boolean success, String errorMessage) {
        try {
            // Extract token counts from AI response
            Integer promptTokens = null;
            Integer completionTokens = null;

            if (aiResponse != null && aiResponse.getTokensUsed() != null) {
                // Try to extract detailed token breakdown
                if (aiResponse.getMetadata() != null) {
                    promptTokens = extractTokenCount(aiResponse.getMetadata(), "promptTokens", "input_tokens");
                    completionTokens = extractTokenCount(aiResponse.getMetadata(), "completionTokens", "output_tokens");
                }

                // Fallback: estimate from total tokens (60% input, 40% output)
                if (promptTokens == null || completionTokens == null) {
                    int totalTokens = aiResponse.getTokensUsed();
                    promptTokens = (int) (totalTokens * 0.6);
                    completionTokens = (int) (totalTokens * 0.4);
                }
            } else {
                // No token info - estimate from content length
                promptTokens = estimateTokens(request.getContent());
                completionTokens = aiResponse != null ? estimateTokens(aiResponse.getContent()) : 0;
            }

            // Determine AI provider (from configuration or aiService)
            AIProvider provider = determineAIProvider();

            // Build usage request
            AIUsageRequest usageRequest = AIUsageRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(request.getUserId())
                    .userName(request.getUserId() != null ? request.getUserId().toString() : "Unknown")                    .userRole(String.valueOf(request.getUserRole()))
                    .provider(provider)
                    .modelName(aiResponse != null && aiResponse.getMetadata() != null
                            ? extractModelName(aiResponse.getMetadata())
                            : "default")
                    .taskType(AITaskType.TEST_GENERATION)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .requestContentLength(request.getContent() != null ? request.getContent().length() : 0)
                    .responseContentLength(aiResponse != null && aiResponse.getContent() != null
                            ? aiResponse.getContent().length()
                            : 0)
                    .processingTimeMs(processingTimeMs)
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();

            // Record usage
            usageTrackingService.recordUsage(usageRequest);

            log.debug("AI usage tracked: provider={}, tokens={}, success={}",
                    provider, promptTokens + completionTokens, success);

        } catch (Exception e) {
            log.error("Failed to track AI usage - continuing with request", e);
            // Don't fail the main request if tracking fails
        }
    }

    /**
     * Extract token count from metadata map.
     */
    private Integer extractTokenCount(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }

        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }

        return null;
    }

    /**
     * Extract model name from metadata.
     */
    private String extractModelName(Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("modelId")) {
            return metadata.get("modelId").toString();
        }
        if (metadata != null && metadata.containsKey("model")) {
            return metadata.get("model").toString();
        }
        return "default";
    }

    /**
     * Estimate token count from text.
     * Rough approximation: 1 token ≈ 4 characters
     */
    private Integer estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    /**
     * Determine which AI provider is being used.
     * This should be configurable or detected from the AI service.
     */
    private AIProvider determineAIProvider() {
        // Check which AI service implementation is active
        String aiServiceClass = aiService.getClass().getSimpleName();

        if (aiServiceClass.contains("Bedrock")) {
            return AIProvider.BEDROCK;
        } else if (aiServiceClass.contains("Ollama")) {
            return AIProvider.OLLAMA;
        } else {
            return AIProvider.MOCK;
        }
    }
}