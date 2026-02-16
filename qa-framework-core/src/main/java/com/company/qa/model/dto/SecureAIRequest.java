package com.company.qa.model.dto;

import com.company.qa.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to AI Gateway (secure wrapper around AI requests).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureAIRequest {

    /**
     * User making the request
     */
    private UUID userId;

    /**
     * User's role (determines rate limits)
     */
    private UserRole userRole;

    /**
     * Content to send to AI (will be sanitized)
     */
    private String content;

    /**
     * Test framework (for test generation)
     */
    private String framework;

    /**
     * Programming language (for test generation)
     */
    private String language;

    /**
     * Target URL (for test generation)
     */
    private String targetUrl;

    /**
     * Test name (for failure analysis)
     */
    private String testName;

    /**
     * Stack trace (for failure analysis)
     */
    private String stackTrace;

    /**
     * Execution ID (for failure analysis)
     */
    private UUID executionId;

    /**
     * Enable strict mode (blocks on critical secrets)
     */
    @Builder.Default
    private boolean strictMode = true;

    /**
     * Operation type
     */
    private OperationType operationType;



    /**
     * Types of AI operations
     */
    public enum OperationType {
        TEST_GENERATION,
        FAILURE_ANALYSIS,
        FIX_SUGGESTION,
        LOCATOR_DISCOVERY, GENERAL
    }
}