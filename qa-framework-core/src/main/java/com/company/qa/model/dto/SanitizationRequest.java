package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request for data sanitization before sending to AI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanitizationRequest {

    /**
     * Content to be sanitized (test description, error logs, etc.)
     */
    private String content;

    /**
     * User requesting the sanitization
     */
    private UUID userId;

    /**
     * Context of the request (test_generation, failure_analysis, etc.)
     */
    private String context;

    /**
     * If true, block request if critical secrets found.
     * If false, only redact and log.
     */
    @Builder.Default
    private boolean strictMode = true;

    /**
     * If true, also redact non-critical PII (names, addresses)
     */
    @Builder.Default
    private boolean redactNonCriticalPII = false;
}