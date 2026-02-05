package com.company.qa.service.security;

import com.company.qa.model.dto.SanitizationResult.RedactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans for secrets and credentials that should never be sent to AI.
 */
@Component
@Slf4j
public class SecretScanner {

    // Generic API key patterns
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(?:api[_-]?key|apikey|api[_-]?secret)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_\\-]{20,})['\"]?"
    );

    // AWS Access Key pattern
    private static final Pattern AWS_ACCESS_KEY_PATTERN = Pattern.compile(
            "(?:A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}"
    );

    // AWS Secret Key pattern (base64-like, 40 chars)
    private static final Pattern AWS_SECRET_KEY_PATTERN = Pattern.compile(
            "(?i)aws[_-]?secret[_-]?access[_-]?key['\"]?\\s*[:=]\\s*['\"]?([A-Za-z0-9/+=]{40})['\"]?"
    );

    // Generic secret/password patterns
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(?:password|passwd|pwd|secret)\\s*[:=]\\s*['\"]?([^\\s'\"]{8,})['\"]?"
    );

    // Bearer token pattern
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)bearer\\s+([a-zA-Z0-9_\\-\\.]{20,})"
    );

    // Database connection strings
    private static final Pattern DATABASE_URL_PATTERN = Pattern.compile(
            "(?i)(?:jdbc|mongodb|postgresql|mysql)://[^\\s@]+:[^\\s@]+@[^\\s]+"
    );

    // Private key pattern
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN (?:RSA |EC )?PRIVATE KEY-----"
    );

    // GitHub/GitLab tokens
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile(
            "(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,}"
    );

    /**
     * Check if content contains critical secrets that should block the request.
     */
    public boolean containsCriticalSecrets(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        return AWS_ACCESS_KEY_PATTERN.matcher(content).find() ||
                AWS_SECRET_KEY_PATTERN.matcher(content).find() ||
                PRIVATE_KEY_PATTERN.matcher(content).find() ||
                DATABASE_URL_PATTERN.matcher(content).find();
    }

    /**
     * Check if content contains any secrets.
     */
    public boolean containsSecrets(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        return API_KEY_PATTERN.matcher(content).find() ||
                AWS_ACCESS_KEY_PATTERN.matcher(content).find() ||
                PASSWORD_PATTERN.matcher(content).find() ||
                BEARER_TOKEN_PATTERN.matcher(content).find() ||
                DATABASE_URL_PATTERN.matcher(content).find() ||
                PRIVATE_KEY_PATTERN.matcher(content).find() ||
                GITHUB_TOKEN_PATTERN.matcher(content).find();
    }

    /**
     * Redact secrets from content and return detection results.
     */
    public SecretDetectionResult redactSecrets(String content) {
        if (content == null || content.isEmpty()) {
            return new SecretDetectionResult(content, new ArrayList<>(), 0, false);
        }

        String sanitized = content;
        List<RedactionType> redactionTypes = new ArrayList<>();
        int redactionCount = 0;
        boolean criticalSecretsFound = false;

        // Redact API keys
        Matcher apiKeyMatcher = API_KEY_PATTERN.matcher(sanitized);
        if (apiKeyMatcher.find()) {
            sanitized = apiKeyMatcher.replaceAll("$1=[API_KEY_REDACTED]");
            redactionTypes.add(RedactionType.API_KEY);
            redactionCount += countMatches(apiKeyMatcher);
            log.warn("Redacted {} API key(s)", redactionCount);
        }

        // Redact AWS access keys
        Matcher awsAccessMatcher = AWS_ACCESS_KEY_PATTERN.matcher(sanitized);
        if (awsAccessMatcher.find()) {
            sanitized = awsAccessMatcher.replaceAll("[AWS_ACCESS_KEY_REDACTED]");
            redactionTypes.add(RedactionType.AWS_KEY);
            int awsCount = countMatches(awsAccessMatcher);
            redactionCount += awsCount;
            criticalSecretsFound = true;
            log.error("CRITICAL: Redacted {} AWS access key(s)", awsCount);
        }

        // Redact AWS secret keys
        Matcher awsSecretMatcher = AWS_SECRET_KEY_PATTERN.matcher(sanitized);
        if (awsSecretMatcher.find()) {
            sanitized = awsSecretMatcher.replaceAll("$1=[AWS_SECRET_KEY_REDACTED]");
            redactionTypes.add(RedactionType.AWS_KEY);
            int awsSecretCount = countMatches(awsSecretMatcher);
            redactionCount += awsSecretCount;
            criticalSecretsFound = true;
            log.error("CRITICAL: Redacted {} AWS secret key(s)", awsSecretCount);
        }

        // Redact passwords
        Matcher passwordMatcher = PASSWORD_PATTERN.matcher(sanitized);
        if (passwordMatcher.find()) {
            sanitized = passwordMatcher.replaceAll("$1=[PASSWORD_REDACTED]");
            redactionTypes.add(RedactionType.PASSWORD);
            int pwdCount = countMatches(passwordMatcher);
            redactionCount += pwdCount;
            log.warn("Redacted {} password(s)", pwdCount);
        }

        // Redact bearer tokens
        Matcher bearerMatcher = BEARER_TOKEN_PATTERN.matcher(sanitized);
        if (bearerMatcher.find()) {
            sanitized = bearerMatcher.replaceAll("Bearer [TOKEN_REDACTED]");
            redactionTypes.add(RedactionType.TOKEN);
            int tokenCount = countMatches(bearerMatcher);
            redactionCount += tokenCount;
            log.warn("Redacted {} bearer token(s)", tokenCount);
        }

        // Redact database URLs
        Matcher dbMatcher = DATABASE_URL_PATTERN.matcher(sanitized);
        if (dbMatcher.find()) {
            sanitized = dbMatcher.replaceAll("[DATABASE_URL_REDACTED]");
            redactionTypes.add(RedactionType.DATABASE_URL);
            int dbCount = countMatches(dbMatcher);
            redactionCount += dbCount;
            criticalSecretsFound = true;
            log.error("CRITICAL: Redacted {} database URL(s)", dbCount);
        }

        // Redact private keys
        Matcher privateKeyMatcher = PRIVATE_KEY_PATTERN.matcher(sanitized);
        if (privateKeyMatcher.find()) {
            sanitized = privateKeyMatcher.replaceAll("[PRIVATE_KEY_REDACTED]");
            redactionTypes.add(RedactionType.PRIVATE_KEY);
            int pkCount = countMatches(privateKeyMatcher);
            redactionCount += pkCount;
            criticalSecretsFound = true;
            log.error("CRITICAL: Redacted {} private key(s)", pkCount);
        }

        // Redact GitHub tokens
        Matcher githubMatcher = GITHUB_TOKEN_PATTERN.matcher(sanitized);
        if (githubMatcher.find()) {
            sanitized = githubMatcher.replaceAll("[GITHUB_TOKEN_REDACTED]");
            redactionTypes.add(RedactionType.TOKEN);
            int ghCount = countMatches(githubMatcher);
            redactionCount += ghCount;
            log.warn("Redacted {} GitHub token(s)", ghCount);
        }

        return new SecretDetectionResult(sanitized, redactionTypes, redactionCount, criticalSecretsFound);
    }

    /**
     * Count matches in a matcher.
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
     * Result of secret detection.
     */
    public record SecretDetectionResult(
            String sanitizedContent,
            List<RedactionType> redactionTypes,
            int redactionCount,
            boolean criticalSecretsFound
    ) {}
}