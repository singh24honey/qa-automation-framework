package com.company.qa.service.security;

import com.company.qa.model.dto.SanitizationRequest;
import com.company.qa.model.dto.SanitizationResult;
import com.company.qa.service.security.PIIDetector.PIIDetectionResult;
import com.company.qa.service.security.SecretScanner.SecretDetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Main service for sanitizing data before sending to AI providers.
 *
 * This service is the FIRST LINE OF DEFENSE against data leakage.
 *
 * Security Features:
 * - PII detection and redaction
 * - Secret detection and blocking
 * - Audit trail of all sanitization operations
 * - Strict mode for critical operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataSanitizerService {

    private final PIIDetector piiDetector;
    private final SecretScanner secretScanner;

    /**
     * Sanitize content before sending to AI.
     *
     * This is the main entry point for data sanitization.
     */
    public SanitizationResult sanitize(SanitizationRequest request) {
        log.info("Sanitizing content for user {} in context: {}",
                request.getUserId(), request.getContext());

        String content = request.getContent();
        if (content == null || content.isEmpty()) {
            return createEmptyResult(request);
        }

        SanitizationResult.SanitizationResultBuilder resultBuilder = SanitizationResult.builder()
                .originalContent(content)
                .timestamp(Instant.now());

        String sanitized = content;
        int totalRedactionCount = 0;

        // Step 1: Scan for critical secrets
        SecretDetectionResult secretResult = secretScanner.redactSecrets(sanitized);
        sanitized = secretResult.sanitizedContent();
        totalRedactionCount += secretResult.redactionCount();

        if (secretResult.redactionCount() > 0) {
            secretResult.redactionTypes().forEach(resultBuilder::addRedactionType);
            resultBuilder.secretRedactionCount(secretResult.redactionCount());
            resultBuilder.dataRedacted(true);

            if (secretResult.criticalSecretsFound()) {
                String criticalIssue = String.format("Critical secrets detected: %d redaction(s)",
                        secretResult.redactionCount());
                resultBuilder.addCriticalIssue(criticalIssue);

                if (request.isStrictMode()) {
                    resultBuilder.shouldBlock(true);
                    log.error("BLOCKING REQUEST: Critical secrets found for user {} in context: {}",
                            request.getUserId(), request.getContext());
                }
            }
        }

        // Step 2: Scan for PII
        PIIDetectionResult piiResult = piiDetector.redactPII(sanitized, false);
        sanitized = piiResult.sanitizedContent();
        totalRedactionCount += piiResult.redactionCount();

        if (piiResult.redactionCount() > 0) {
            piiResult.redactionTypes().forEach(resultBuilder::addRedactionType);
            resultBuilder.piiRedactionCount(piiResult.redactionCount());
            resultBuilder.dataRedacted(true);

            // Check for critical PII
            if (piiDetector.containsCriticalPII(content)) {
                String warning = String.format("Critical PII detected: %d redaction(s)",
                        piiResult.redactionCount());
                resultBuilder.addWarning(warning);
                log.warn("Critical PII found for user {} in context: {}",
                        request.getUserId(), request.getContext());
            }
        }

        // Step 3: Build final result
        resultBuilder.sanitizedContent(sanitized);
        resultBuilder.totalRedactionCount(totalRedactionCount);

        SanitizationResult result = resultBuilder.build();

        log.info("Sanitization complete: redacted={}, blocked={}, PII={}, Secrets={}",
                result.isDataRedacted(), result.isShouldBlock(),
                result.getPiiRedactionCount(), result.getSecretRedactionCount());

        return result;
    }

    /**
     * Quick check if content contains sensitive data.
     */
    public boolean containsSensitiveData(String content) {
        return piiDetector.containsPII(content) ||
                secretScanner.containsSecrets(content);
    }

    /**
     * Quick check if content contains critical data that should block the request.
     */
    public boolean containsCriticalData(String content) {
        return piiDetector.containsCriticalPII(content) ||
                secretScanner.containsCriticalSecrets(content);
    }

    /**
     * Create an empty result for null/empty content.
     */
    private SanitizationResult createEmptyResult(SanitizationRequest request) {
        return SanitizationResult.builder()
                .originalContent("")
                .sanitizedContent("")
                .dataRedacted(false)
                .shouldBlock(false)
                .timestamp(Instant.now())
                .build();
    }
}