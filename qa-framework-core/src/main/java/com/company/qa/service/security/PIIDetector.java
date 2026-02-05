package com.company.qa.service.security;

import com.company.qa.model.dto.SanitizationResult.RedactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and redacts Personally Identifiable Information (PII).
 */
@Component
@Slf4j
public class PIIDetector {

    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    // Phone number patterns (US format)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
    );

    // SSN pattern (XXX-XX-XXXX)
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    // Credit card patterns (basic - covers Visa, MasterCard, Amex, Discover)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b"
    );

    // IP Address pattern
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"
    );

    private static final Pattern ALLOWED_TEST_EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@(example|test|sample|dummy)\\.com$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REAL_EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    /**
     * Detect if content contains PII.
     */
    public boolean containsPII(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }


        if (ALLOWED_TEST_EMAIL.matcher(content).find()) {
            return false;

        }
        if (REAL_EMAIL_PATTERN.matcher(content).find()) {
            return true;
        }
        if (PHONE_PATTERN.matcher(content).find() ||
                SSN_PATTERN.matcher(content).find() ||
                CREDIT_CARD_PATTERN.matcher(content).find()) {
            return true;
        }
        return false;

    }



    /**
     * Redact PII from content and return detection results.
     */
    public PIIDetectionResult redactPII(String content, boolean includeIPAddresses) {
        if (content == null || content.isEmpty()) {
            return new PIIDetectionResult(content, new ArrayList<>(), 0);
        }

        String sanitized = content;
        List<RedactionType> redactionTypes = new ArrayList<>();
        int redactionCount = 0;

        // Redact emails
        Matcher emailMatcher = EMAIL_PATTERN.matcher(sanitized);
        if (emailMatcher.find()) {
            sanitized = emailMatcher.replaceAll("[EMAIL_REDACTED]");
            redactionTypes.add(RedactionType.EMAIL);
            redactionCount += countMatches(emailMatcher);
            log.debug("Redacted {} email(s)", redactionCount);
        }

        // Redact phone numbers
        Matcher phoneMatcher = PHONE_PATTERN.matcher(sanitized);
        if (phoneMatcher.find()) {
            sanitized = phoneMatcher.replaceAll("[PHONE_REDACTED]");
            redactionTypes.add(RedactionType.PHONE_NUMBER);
            int phoneCount = countMatches(phoneMatcher);
            redactionCount += phoneCount;
            log.debug("Redacted {} phone number(s)", phoneCount);
        }

        // Redact SSNs
        Matcher ssnMatcher = SSN_PATTERN.matcher(sanitized);
        if (ssnMatcher.find()) {
            sanitized = ssnMatcher.replaceAll("[SSN_REDACTED]");
            redactionTypes.add(RedactionType.SSN);
            int ssnCount = countMatches(ssnMatcher);
            redactionCount += ssnCount;
            log.warn("Redacted {} SSN(s) - CRITICAL PII DETECTED", ssnCount);
        }

        // Redact credit cards
        Matcher ccMatcher = CREDIT_CARD_PATTERN.matcher(sanitized);
        if (ccMatcher.find()) {
            sanitized = ccMatcher.replaceAll("[CREDIT_CARD_REDACTED]");
            redactionTypes.add(RedactionType.CREDIT_CARD);
            int ccCount = countMatches(ccMatcher);
            redactionCount += ccCount;
            log.warn("Redacted {} credit card(s) - CRITICAL PII DETECTED", ccCount);
        }

        // Redact IP addresses (optional - may have legitimate use in logs)
        if (includeIPAddresses) {
            Matcher ipMatcher = IP_ADDRESS_PATTERN.matcher(sanitized);
            if (ipMatcher.find()) {
                sanitized = ipMatcher.replaceAll("[IP_REDACTED]");
                redactionTypes.add(RedactionType.IP_ADDRESS);
                int ipCount = countMatches(ipMatcher);
                redactionCount += ipCount;
                log.debug("Redacted {} IP address(es)", ipCount);
            }
        }

        return new PIIDetectionResult(sanitized, redactionTypes, redactionCount);
    }

    /**
     * Check if content contains critical PII (SSN, Credit Card).
     */
    public boolean containsCriticalPII(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        return SSN_PATTERN.matcher(content).find() ||
                CREDIT_CARD_PATTERN.matcher(content).find();
    }

    /**
     * Count matches in a matcher (by resetting and counting).
     */
    private int countMatches(Matcher matcher) {
        matcher.reset();
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Result of PII detection.
     */
    public record PIIDetectionResult(
            String sanitizedContent,
            List<RedactionType> redactionTypes,
            int redactionCount
    ) {}
}