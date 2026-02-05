package com.company.qa.model.enums;

/**
 * Types of violations that can occur during validation.
 */
public enum ViolationType {
    /**
     * Response contains PII that should have been redacted
     */
    PII_DETECTED,

    /**
     * Response contains secrets/credentials
     */
    SECRET_DETECTED,

    /**
     * Response contains dangerous code patterns
     */
    DANGEROUS_CODE,

    /**
     * Response doesn't match expected schema/format
     */
    SCHEMA,

    /**
     * Response contains inappropriate content
     */
    CONTENT_SAFETY,

    /**
     * Response exceeds length limits
     */
    LENGTH_VIOLATION,

    /**
     * Response contains blocked domains/URLs
     */
    BLOCKED_DOMAIN,

    /**
     * Response contains non-whitelisted imports
     */
    INVALID_IMPORT
}