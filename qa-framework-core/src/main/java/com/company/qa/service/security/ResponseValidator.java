package com.company.qa.service.security;

import com.company.qa.model.dto.ValidationResult;
import com.company.qa.model.enums.ViolationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates AI responses before they're used by the system.
 *
 * Validation checks:
 * - PII detection in responses
 * - Secret detection in responses
 * - Dangerous code patterns
 * - Response length limits
 * - Content safety
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResponseValidator {

    private final DataSanitizerService sanitizerService;


    // Dangerous code patterns that should never appear in generated tests
    private static final List<Pattern> DANGEROUS_CODE_PATTERNS = List.of(
            Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ProcessBuilder", Pattern.CASE_INSENSITIVE),
            Pattern.compile("System\\.exit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Files\\.delete|FileUtils\\.delete", Pattern.CASE_INSENSITIVE),
            Pattern.compile("setAccessible\\s*\\(\\s*true\\s*\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exec\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("__import__", Pattern.CASE_INSENSITIVE),
            Pattern.compile("os\\.system", Pattern.CASE_INSENSITIVE)
    );

    // Blocked domains that should not appear in responses
    private static final List<String> BLOCKED_DOMAINS = List.of(
            "pastebin.com",
            "hastebin.com",
            "bit.ly",
            "tinyurl.com"
    );

    // Whitelisted imports for Java test code
    private static final List<String> WHITELISTED_IMPORTS = List.of(
            "org.junit",
            "org.testng",
            "org.seleniumhq.selenium",
            "org.openqa.selenium",
            "io.restassured",
            "com.company.qa", // Your framework
            "java.util",
            "java.time",
            "java.io.File",
            "static org.junit",
            "static org.assertj",
            "io.cucumber",
            "com.microsoft",
            "static com.microsoft.playwright.assertions.PlaywrightAssertions.*",
            "com.fasterxml.jackson"
    );

    /**
     * Validate AI response.
     *
     * @param response The AI response to validate
     * @param responseType Type of response (code, analysis, etc.)
     * @return Validation result
     */
    public ValidationResult validate(String response, ResponseType responseType) {
        if (response == null || response.isEmpty()) {
            return ValidationResult.builder()
                    .valid(false)
                    .addViolation(ViolationType.SCHEMA, "Response is empty")
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        boolean shouldBlock = false;

        ValidationResult.ValidationResultBuilder builder = ValidationResult.builder();

        // 1. Check for PII in response
        if (sanitizerService.containsSensitiveData(response)) {
            builder.addViolation(ViolationType.PII_DETECTED,
                    "Response contains PII that should have been redacted");
            shouldBlock = true;
            log.warn("PII detected in AI response");
        }

        // 2. Check for critical secrets
        if (sanitizerService.containsCriticalData(response)) {
            builder.addViolation(ViolationType.SECRET_DETECTED,
                    "Response contains critical secrets");
            shouldBlock = true;
            log.error("CRITICAL: Secrets detected in AI response");
        }

        // 3. Check for dangerous code patterns
        if (responseType == ResponseType.TEST_CODE || responseType == ResponseType.SUGGESTION) {
            for (Pattern pattern : DANGEROUS_CODE_PATTERNS) {
                if (pattern.matcher(response).find()) {
                    builder.addViolation(ViolationType.DANGEROUS_CODE,
                            "Dangerous code pattern detected: " + pattern.pattern());
                    shouldBlock = true;
                    log.error("Dangerous code pattern found: {}", pattern.pattern());
                }
            }
        }

        // 4. Check for blocked domains
        List<String> blockedDomains = checkBlockedDomains(response);
        if (!blockedDomains.isEmpty()) {
            builder.addViolation(ViolationType.BLOCKED_DOMAIN,
                    "Response references blocked domains: " + String.join(", ", blockedDomains));
            warnings.add("Response contains potentially unsafe domains");
        }

        // 5. Check import statements (if code)
        if (responseType == ResponseType.TEST_CODE) {
            List<String> invalidImports = checkImports(response);
            if (!invalidImports.isEmpty()) {
                builder.addViolation(ViolationType.INVALID_IMPORT,
                        "Response contains non-whitelisted imports: " + String.join(", ", invalidImports));
                warnings.add("Review imports before using this code");
            }
        }

        // 6. Check response length (prevent token abuse)
        if (response.length() > 100_000) {
            builder.addViolation(ViolationType.LENGTH_VIOLATION,
                    "Response exceeds maximum length (100K characters)");
            warnings.add("Response exceeds recommended length");
        }

        // Add all warnings
        warnings.forEach(builder::addWarning);

        builder.shouldBlock(shouldBlock);

        ValidationResult result = builder.build();

        log.info("Response validation complete: valid={}, violations={}, warnings={}",
                result.isValid(), result.getViolations().size(), result.getWarnings().size());

        return result;
    }

    /**
     * Quick check if response appears safe.
     *
     * @param response Response to check
     * @return true if safe, false otherwise
     */
    public boolean isResponseSafe(String response) {
        return !sanitizerService.containsSensitiveData(response) &&
                !sanitizerService.containsCriticalData(response) &&
                checkDangerousPatterns(response).isEmpty();
    }

    /**
     * Check for blocked domains in response.
     */
    private List<String> checkBlockedDomains(String response) {
        List<String> found = new ArrayList<>();
        for (String domain : BLOCKED_DOMAINS) {
            if (response.toLowerCase().contains(domain.toLowerCase())) {
                found.add(domain);
            }
        }
        return found;
    }

    /**
     * Check for dangerous patterns in response.
     */
    private List<String> checkDangerousPatterns(String response) {
        List<String> found = new ArrayList<>();
        for (Pattern pattern : DANGEROUS_CODE_PATTERNS) {
            if (pattern.matcher(response).find()) {
                found.add(pattern.pattern());
            }
        }
        return found;
    }

    /**
     * Check import statements in code.
     */
    private List<String> checkImports(String code) {
        List<String> invalidImports = new ArrayList<>();

        // Extract import statements
        Pattern importPattern = Pattern.compile("^\\s*import\\s+([^;]+);", Pattern.MULTILINE);
        java.util.regex.Matcher matcher = importPattern.matcher(code);

        while (matcher.find()) {
            String importStatement = matcher.group(1).trim();

            // Check if import is whitelisted
            boolean whitelisted = false;
            for (String allowed : WHITELISTED_IMPORTS) {
                if (importStatement.startsWith(allowed)) {
                    whitelisted = true;
                    break;
                }
            }

            if (!whitelisted) {
                invalidImports.add(importStatement);
            }
        }

        return invalidImports;
    }

    /**
     * Types of responses that can be validated.
     */
    public enum ResponseType {
        /**
         * Generated test code
         */
        TEST_CODE,

        /**
         * Failure analysis report
         */
        ANALYSIS,

        /**
         * Test improvement suggestion
         */
        SUGGESTION,

        /**
         * General response
         */
        GENERAL
    }
}