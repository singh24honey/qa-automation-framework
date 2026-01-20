package com.company.qa.service.security;

import com.company.qa.model.dto.SanitizationResult.RedactionType;
import com.company.qa.service.security.SecretScanner.SecretDetectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Secret Scanner Tests")
class SecretScannerTest {

    private SecretScanner secretScanner;

    @BeforeEach
    void setUp() {
        secretScanner = new SecretScanner();
    }

    @Test
    @DisplayName("Should detect and redact API keys")
    void shouldDetectAndRedactAPIKeys() {
        String content = "api_key=sk_test_1234567890abcdefghij";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[API_KEY_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("sk_test_1234567890abcdefghij");
        assertThat(result.redactionTypes()).contains(RedactionType.API_KEY);
        assertThat(result.redactionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should detect and redact AWS access keys")
    void shouldDetectAndRedactAWSAccessKeys() {
        String content = "AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[AWS_ACCESS_KEY_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(result.redactionTypes()).contains(RedactionType.AWS_KEY);
        assertThat(result.criticalSecretsFound()).isTrue();
    }

    @Test
    @DisplayName("Should detect and redact AWS secret keys")
    void shouldDetectAndRedactAWSSecretKeys() {
        String content = "aws_secret_access_key=\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[AWS_SECRET_KEY_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        assertThat(result.redactionTypes()).contains(RedactionType.AWS_KEY);
        assertThat(result.criticalSecretsFound()).isTrue();
    }

    @Test
    @DisplayName("Should detect and redact passwords")
    void shouldDetectAndRedactPasswords() {
        String content = "password=MySecretPassword123";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[PASSWORD_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("MySecretPassword123");
        assertThat(result.redactionTypes()).contains(RedactionType.PASSWORD);
    }

    @Test
    @DisplayName("Should detect and redact bearer tokens")
    void shouldDetectAndRedactBearerTokens() {
        String content = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[TOKEN_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(result.redactionTypes()).contains(RedactionType.TOKEN);
    }

    @Test
    @DisplayName("Should detect and redact database URLs")
    void shouldDetectAndRedactDatabaseURLs() {
        String content = "DATABASE_URL=postgresql://user:password@localhost:5432/mydb";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[DATABASE_URL_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("user:password@localhost");
        assertThat(result.redactionTypes()).contains(RedactionType.DATABASE_URL);
        assertThat(result.criticalSecretsFound()).isTrue();
    }

    @Test
    @DisplayName("Should detect and redact private keys")
    void shouldDetectAndRedactPrivateKeys() {
        String content = "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA...";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[PRIVATE_KEY_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("BEGIN RSA PRIVATE KEY");
        assertThat(result.redactionTypes()).contains(RedactionType.PRIVATE_KEY);
        assertThat(result.criticalSecretsFound()).isTrue();
    }

    @Test
    @DisplayName("Should detect and redact GitHub tokens")
    void shouldDetectAndRedactGitHubTokens() {
        String content = "GITHUB_TOKEN=ghp_1234567890abcdefghijklmnopqrstuvwxyz";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent()).contains("[GITHUB_TOKEN_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("ghp_1234567890abcdefghijklmnopqrstuvwxyz");
        assertThat(result.redactionTypes()).contains(RedactionType.TOKEN);
    }

    @Test
    @DisplayName("Should detect critical secrets")
    void shouldDetectCriticalSecrets() {
        String awsKey = "AKIAIOSFODNN7EXAMPLE";
        String dbUrl = "postgresql://user:pass@localhost/db";
        String privateKey = "-----BEGIN PRIVATE KEY-----";
        String safeContent = "No secrets here";

        assertThat(secretScanner.containsCriticalSecrets(awsKey)).isTrue();
        assertThat(secretScanner.containsCriticalSecrets(dbUrl)).isTrue();
        assertThat(secretScanner.containsCriticalSecrets(privateKey)).isTrue();
        assertThat(secretScanner.containsCriticalSecrets(safeContent)).isFalse();
    }

    @Test
    @DisplayName("Should detect any secrets")
    void shouldDetectAnySecrets() {
        String apiKey = "api_key=abc123def456ghi789";
        String password = "password=MySecret123";
        String safeContent = "No secrets here";

        assertThat(secretScanner.containsSecrets(apiKey)).isTrue();
        assertThat(secretScanner.containsSecrets(password)).isTrue();
        assertThat(secretScanner.containsSecrets(safeContent)).isFalse();
    }

    @Test
    @DisplayName("Should handle null and empty content")
    void shouldHandleNullAndEmptyContent() {
        assertThat(secretScanner.containsSecrets(null)).isFalse();
        assertThat(secretScanner.containsSecrets("")).isFalse();

        SecretDetectionResult result = secretScanner.redactSecrets("");
        assertThat(result.sanitizedContent()).isEmpty();
        assertThat(result.redactionCount()).isZero();
        assertThat(result.criticalSecretsFound()).isFalse();
    }

    @Test
    @DisplayName("Should handle content with multiple secret types")
    void shouldHandleMultipleSecretTypes() {
        String content = "api_key=abc123 " +
                "AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE " +
                "password=MySecret123 " +
                "postgresql://user:pass@localhost/db";

        SecretDetectionResult result = secretScanner.redactSecrets(content);

        assertThat(result.sanitizedContent())
                .contains("[API_KEY_REDACTED]")
                .contains("[AWS_ACCESS_KEY_REDACTED]")
                .contains("[PASSWORD_REDACTED]")
                .contains("[DATABASE_URL_REDACTED]");

        assertThat(result.redactionTypes()).containsAnyOf(
                RedactionType.API_KEY,
                RedactionType.AWS_KEY,
                RedactionType.PASSWORD,
                RedactionType.DATABASE_URL
        );

        assertThat(result.criticalSecretsFound()).isTrue();
    }
}