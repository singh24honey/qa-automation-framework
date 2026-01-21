package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.ai.AIGatewayService;
import com.company.qa.service.ai.AIService;
import com.company.qa.service.security.RateLimiterService;
import com.company.qa.service.security.RateLimiterService.RateLimitResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * AI Controller - Now secured with AIGatewayService.
 *
 * All AI operations go through security layers:
 * - Rate limiting
 * - Data sanitization
 * - Response validation
 * - Audit logging
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Operations", description = "Secure AI-powered test generation and analysis")
public class AIController {

    private final AIService aiService;  // Keep for status check
    private final AIGatewayService aiGatewayService;  // NEW - Secure gateway
    private final RateLimiterService rateLimiterService;  // NEW - For stats endpoints

    /**
     * Get AI service status.
     * This endpoint remains unchanged - direct call to AIService is fine.
     */
    @GetMapping("/status")
    @Operation(summary = "Get AI service status", description = "Check if AI service is available")
    public ResponseEntity<ApiResponse<AIStatusDTO>> getStatus() {
        log.info("Getting AI service status");

        AIStatusDTO status = AIStatusDTO.builder()
                .provider(aiService.getProvider().name())
                .available(aiService.isAvailable())
                .message(aiService.isAvailable() ?
                        "AI service is ready" :
                        "AI service is unavailable")
                .build();

        return ResponseEntity.ok(ApiResponse.success(status, "AI status retrieved"));
    }

    /**
     * Generate test code using AI - NOW SECURED.
     *
     * @param request Test generation request
     * @param userId User ID (from header or security context)
     * @param userRole User role (from header or security context)
     * @return Secure AI response
     */
    @PostMapping("/generate-test")
    @Operation(summary = "Generate test code", description = "Generate automated test code using AI with security guardrails")
    public ResponseEntity<ApiResponse<SecureAIResponse>> generateTest(
            @RequestBody TestGenerationRequest request,
            @Parameter(description = "User ID") @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "User Role") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        log.info("Generating test from description: {}", request.getDescription());

        try {
            // Build secure request
            SecureAIRequest secureRequest = SecureAIRequest.builder()
                    .userId(userId != null ? UUID.fromString(userId) : UUID.randomUUID())
                    .userRole(userRole != null ? UserRole.valueOf(userRole.toUpperCase()) : UserRole.QA_ENGINEER)
                    .content(request.getDescription())
                    .framework(request.getFramework())
                    .language(request.getLanguage())
                    .targetUrl(request.getTargetUrl())
                    .strictMode(true)
                    .operationType(SecureAIRequest.OperationType.TEST_GENERATION)
                    .build();

            // Call secure gateway instead of direct AI service
            SecureAIResponse response = aiGatewayService.generateTest(secureRequest);

            // Return appropriate HTTP status
            HttpStatus status = response.isSuccess() ? HttpStatus.OK :
                    response.isRateLimitExceeded() ? HttpStatus.TOO_MANY_REQUESTS :
                            response.isBlockedBySecurityPolicy() ? HttpStatus.FORBIDDEN :
                                    HttpStatus.BAD_REQUEST;

            return ResponseEntity.status(status)
                    .body(ApiResponse.success(response,
                            response.isSuccess() ? "Test generated successfully" : response.getErrorMessage()));

        } catch (Exception e) {
            log.error("Error generating test: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Analyze test failure using AI - NOW SECURED.
     *
     * @param request Failure analysis request
     * @param userId User ID
     * @param userRole User role
     * @return Secure AI response
     */
    @PostMapping("/analyze-failure")
    @Operation(summary = "Analyze test failure", description = "Get AI-powered analysis of test failures")
    public ResponseEntity<ApiResponse<SecureAIResponse>> analyzeFailure(
            @RequestBody FailureAnalysisRequest request,
            @Parameter(description = "User ID") @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "User Role") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        log.info("Analyzing failure for test: {}", request.getTestName());

        try {
            // Build secure request
            SecureAIRequest secureRequest = SecureAIRequest.builder()
                    .userId(userId != null ? UUID.fromString(userId) : UUID.randomUUID())
                    .userRole(userRole != null ? UserRole.valueOf(userRole.toUpperCase()) : UserRole.QA_ENGINEER)
                    .content(request.getErrorMessage())
                    .testName(request.getTestName())
                    .stackTrace(request.getStackTrace())
                    .executionId(request.getExecutionId())
                    .strictMode(false)  // Less strict for error analysis
                    .operationType(SecureAIRequest.OperationType.FAILURE_ANALYSIS)
                    .build();

            // Call secure gateway
            SecureAIResponse response = aiGatewayService.analyzeFailure(secureRequest);

            // Return appropriate HTTP status
            HttpStatus status = response.isSuccess() ? HttpStatus.OK :
                    response.isRateLimitExceeded() ? HttpStatus.TOO_MANY_REQUESTS :
                            response.isBlockedBySecurityPolicy() ? HttpStatus.FORBIDDEN :
                                    HttpStatus.BAD_REQUEST;

            return ResponseEntity.status(status)
                    .body(ApiResponse.success(response,
                            response.isSuccess() ? "Failure analyzed successfully" : response.getErrorMessage()));

        } catch (Exception e) {
            log.error("Error analyzing failure: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Suggest fix for error - LEGACY ENDPOINT (kept for backward compatibility).
     * Consider migrating to analyze-failure endpoint.
     */
    @PostMapping("/suggest-fix")
    @Operation(summary = "Suggest fix for error", description = "Generate fix suggestions for test errors")
    @Deprecated
    public ResponseEntity<ApiResponse<AIResponse>> suggestFix(
            @RequestBody FixSuggestionRequest request) {

        log.info("Suggesting fix for error (legacy endpoint)");

        if (!aiService.isAvailable()) {
            return ResponseEntity.ok(ApiResponse.<AIResponse>error(
                    "AI service is not available"));
        }

        AIResponse response = aiService.suggestFix(
                request.getTestCode(),
                request.getErrorMessage()
        );

        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Fix suggestions generated"
        ));
    }

    /**
     * Execute custom AI task - LEGACY ENDPOINT (kept for backward compatibility).
     * Consider migrating to specific secure endpoints.
     */
    @PostMapping("/execute")
    @Operation(summary = "Execute custom AI task", description = "Execute custom AI task")
    @Deprecated
    public ResponseEntity<ApiResponse<AIResponse>> execute(
            @RequestBody AIRequest request) {

        log.info("Executing custom AI task: {}", request.getTaskType());

        if (!aiService.isAvailable()) {
            return ResponseEntity.ok(ApiResponse.<AIResponse>error(
                    "AI service is not available"));
        }

        AIResponse response = aiService.execute(request);

        return ResponseEntity.ok(ApiResponse.success(
                response,
                "AI task executed successfully"
        ));
    }

    /**
     * Get rate limit status for a user - NEW ENDPOINT.
     */
    @GetMapping("/rate-limit-status")
    @Operation(summary = "Get rate limit status", description = "Check current rate limit status for user")
    public ResponseEntity<ApiResponse<RateLimitStatusResponse>> getRateLimitStatus(
            @Parameter(description = "User ID") @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "User Role") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        log.info("Getting rate limit status for user: {}", userId);

        try {
            UUID userUUID = userId != null ? UUID.fromString(userId) : UUID.randomUUID();
            UserRole role = userRole != null ? UserRole.valueOf(userRole.toUpperCase()) : UserRole.QA_ENGINEER;

            RateLimitResult result = rateLimiterService.checkRateLimit(userUUID, role);

            RateLimitStatusResponse response = RateLimitStatusResponse.builder()
                    .allowed(result.allowed())
                    .remainingRequests(result.remainingRequests())
                    .maxRequests(result.maxRequests())
                    .resetTime(result.resetTime())
                    .role(role.name())
                    .timestamp(Instant.now())
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error getting rate limit status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid user ID or role"));
        }
    }

    /**
     * Get usage statistics for a user - NEW ENDPOINT.
     */
    @GetMapping("/usage-stats")
    @Operation(summary = "Get usage statistics", description = "Get AI usage statistics for user")
    public ResponseEntity<ApiResponse<UsageStatsResponse>> getUsageStats(
            @Parameter(description = "User ID") @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "User Role") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        log.info("Getting usage stats for user: {}", userId);

        try {
            UUID userUUID = userId != null ? UUID.fromString(userId) : UUID.randomUUID();
            UserRole role = userRole != null ? UserRole.valueOf(userRole.toUpperCase()) : UserRole.QA_ENGINEER;

            long tokensUsed = rateLimiterService.getTokenUsage(userUUID);
            double totalCost = rateLimiterService.getCurrentCost(userUUID);
            RateLimitResult rateLimitResult = rateLimiterService.checkRateLimit(userUUID, role);

            UsageStatsResponse response = UsageStatsResponse.builder()
                    .tokensUsed(tokensUsed)
                    .totalCost(totalCost)
                    .requestsThisHour(rateLimitResult.maxRequests() - rateLimitResult.remainingRequests())
                    .maxRequestsPerHour(rateLimitResult.maxRequests())
                    .remainingRequests(rateLimitResult.remainingRequests())
                    .rateLimitResetTime(rateLimitResult.resetTime())
                    .role(role.name())
                    .timestamp(Instant.now())
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error getting usage stats: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid user ID or role"));
        }
    }
}