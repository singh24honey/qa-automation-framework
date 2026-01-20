package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of sanitization operation.
 */
@Data
@NoArgsConstructor

public class SanitizationResult {

    /**
     * Sanitized content (safe to send to AI)
     */
    private String sanitizedContent;

    /**
     * Original content (for audit trail only)
     */
    private String originalContent;

    /**
     * Was any data redacted?
     */
    private boolean dataRedacted = false;

    /**
     * Should the request be blocked (critical secrets found)?
     */
    private boolean shouldBlock = false;

    /**
     * List of redaction types applied
     */
    private List<RedactionType> redactionTypes = new ArrayList<>();

    /**
     * PII items found and redacted
     */
    private int piiRedactionCount = 0;

    /**
     * Secrets found and redacted
     */
    private int secretRedactionCount = 0;

    /**
     * Total redactions
     */
    private int totalRedactionCount = 0;

    /**
     * Warnings (non-blocking issues found)
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * Critical issues (blocking if strictMode=true)
     */
    private List<String> criticalIssues = new ArrayList<>();

    /**
     * Timestamp of sanitization
     */
    private Instant timestamp;

    /**
     * Types of data that can be redacted
     */
    public enum RedactionType {
        EMAIL,
        PHONE_NUMBER,
        SSN,
        CREDIT_CARD,
        API_KEY,
        PASSWORD,
        TOKEN,
        AWS_KEY,
        DATABASE_URL,
        PRIVATE_KEY,
        IP_ADDRESS,
        NAME,
        ADDRESS
    }

    /**
     * Add a redaction type if not already present
     */
    public void addRedactionType(RedactionType type) {
        if (!redactionTypes.contains(type)) {
            redactionTypes.add(type);
        }
    }

    /**
     * Add a warning message
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Add a critical issue
     */
    public void addCriticalIssue(String issue) {
        criticalIssues.add(issue);
    }

    /**
     * Custom builder with fluent API for collections
     */
    @Builder
    public SanitizationResult(
            String sanitizedContent,
            String originalContent,
            boolean dataRedacted,
            boolean shouldBlock,
            List<RedactionType> redactionTypes,
            int piiRedactionCount,
            int secretRedactionCount,
            int totalRedactionCount,
            List<String> warnings,
            List<String> criticalIssues,
            Instant timestamp) {

        this.sanitizedContent = sanitizedContent;
        this.originalContent = originalContent;
        this.dataRedacted = dataRedacted;
        this.shouldBlock = shouldBlock;
        this.redactionTypes = redactionTypes != null ? redactionTypes : new ArrayList<>();
        this.piiRedactionCount = piiRedactionCount;
        this.secretRedactionCount = secretRedactionCount;
        this.totalRedactionCount = totalRedactionCount;
        this.warnings = warnings != null ? warnings : new ArrayList<>();
        this.criticalIssues = criticalIssues != null ? criticalIssues : new ArrayList<>();
        this.timestamp = timestamp;
    }

    /**
     * Custom builder class with helper methods
     */
    public static class SanitizationResultBuilder {

        public SanitizationResultBuilder addRedactionType(RedactionType type) {
            if (this.redactionTypes == null) {
                this.redactionTypes = new ArrayList<>();
            }
            if (!this.redactionTypes.contains(type)) {
                this.redactionTypes.add(type);
            }
            return this;
        }

        public SanitizationResultBuilder addWarning(String warning) {
            if (this.warnings == null) {
                this.warnings = new ArrayList<>();
            }
            this.warnings.add(warning);
            return this;
        }

        public SanitizationResultBuilder addCriticalIssue(String issue) {
            if (this.criticalIssues == null) {
                this.criticalIssues = new ArrayList<>();
            }
            this.criticalIssues.add(issue);
            return this;
        }
    }
}