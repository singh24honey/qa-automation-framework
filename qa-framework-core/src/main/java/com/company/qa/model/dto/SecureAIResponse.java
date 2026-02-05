package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response from AI Gateway (secure wrapper around AI responses).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureAIResponse {

    /**
     * Unique request ID for tracking
     */
    private UUID requestId;

    /**
     * Was the request successful?
     */
    private boolean success;

    /**
     * AI-generated content (if successful)
     */
    private String content;

    /**
     * Error message (if failed)
     */
    private String errorMessage;

    /**
     * Tokens used in this request
     */
    private int tokensUsed;

    /**
     * Estimated cost for this request (USD)
     */
    private double estimatedCost;

    /**
     * Was sanitization applied to input?
     */
    @Builder.Default
    private boolean sanitizationApplied = false;

    /**
     * Number of redactions made
     */
    private int redactionCount;

    /**
     * Was rate limit exceeded?
     */
    @Builder.Default
    private boolean rateLimitExceeded = false;

    /**
     * When rate limit resets
     */
    private Instant rateLimitResetTime;

    /**
     * Was request blocked by security policy?
     */
    @Builder.Default
    private boolean blockedBySecurityPolicy = false;

    /**
     * Did response pass validation?
     */
    @Builder.Default
    private boolean validationPassed = false;

    /**
     * Validation errors (if any)
     */
    @Builder.Default
    private List<String> validationErrors = new ArrayList<>();

    /**
     * Processing time in milliseconds
     */
    private long processingTimeMs;

    /**
     * Timestamp of response
     */
    private Instant timestamp;

    /**
     * Add a validation error
     */
    public void addValidationError(String error) {
        if (validationErrors == null) {
            validationErrors = new ArrayList<>();
        }
        validationErrors.add(error);
    }
}