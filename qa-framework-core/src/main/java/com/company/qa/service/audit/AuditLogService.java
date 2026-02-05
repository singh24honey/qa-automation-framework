package com.company.qa.service.audit;

import com.company.qa.model.dto.AIResponse;
import com.company.qa.model.dto.SanitizationResult;
import com.company.qa.model.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit logging service for AI Gateway operations.
 *
 * In production, this would write to:
 * - Database (for compliance)
 * - ELK Stack (for monitoring)
 * - S3 (for long-term storage)
 *
 * For now, we log to application logs with structured format.
 */
@Service
@Slf4j
public class AuditLogService {

    /**
     * Log a successful AI request.
     */
    public void logSuccessfulRequest(
            UUID requestId,
            UUID userId,
            String operationType,
            SanitizationResult sanitizationResult,
            AIResponse aiResponse,
            long durationMs) {

        log.info("AUDIT_SUCCESS | requestId={} | userId={} | operation={} | " +
                        "sanitized={} | redactions={} | tokensUsed={} | durationMs={}",
                requestId,
                userId,
                operationType,
                sanitizationResult.isDataRedacted(),
                sanitizationResult.getTotalRedactionCount(),
                aiResponse.getTokensUsed(),
                durationMs);
    }

    /**
     * Log a blocked request (due to sanitization).
     */
    public void logBlockedRequest(
            UUID requestId,
            UUID userId,
            String operationType,
            String reason,
            SanitizationResult sanitizationResult) {

        log.warn("AUDIT_BLOCKED | requestId={} | userId={} | operation={} | " +
                        "reason={} | criticalIssues={} | redactions={}",
                requestId,
                userId,
                operationType,
                reason,
                sanitizationResult.getCriticalIssues().size(),
                sanitizationResult.getTotalRedactionCount());
    }

    /**
     * Log a validation failure.
     */
    public void logValidationFailure(
            UUID requestId,
            UUID userId,
            String operationType,
            ValidationResult validationResult) {

        log.warn("AUDIT_VALIDATION_FAILED | requestId={} | userId={} | operation={} | " +
                        "violations={} | shouldBlock={}",
                requestId,
                userId,
                operationType,
                validationResult.getViolations().size(),
                validationResult.isShouldBlock());
    }

    /**
     * Log a rate limit violation.
     */
    public void logRateLimitExceeded(
            UUID requestId,
            UUID userId,
            String operationType,
            int remainingRequests,
            Instant resetTime) {

        log.warn("AUDIT_RATE_LIMIT | requestId={} | userId={} | operation={} | " +
                        "remaining={} | resetTime={}",
                requestId,
                userId,
                operationType,
                remainingRequests,
                resetTime);
    }

    /**
     * Log an error in AI processing.
     */
    public void logError(
            UUID requestId,
            UUID userId,
            String operationType,
            String errorMessage) {

        log.error("AUDIT_ERROR | requestId={} | userId={} | operation={} | error={}",
                requestId,
                userId,
                operationType,
                errorMessage);
    }

    /**
     * Log security event (for monitoring).
     */
    public void logSecurityEvent(
            UUID userId,
            String eventType,
            String description) {

        log.warn("AUDIT_SECURITY | userId={} | eventType={} | description={}",
                userId,
                eventType,
                description);
    }
}