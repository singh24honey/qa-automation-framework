package com.company.qa.service.security;

import com.company.qa.model.dto.SanitizationRequest;
import com.company.qa.model.dto.SanitizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Data Sanitizer Service Tests")
class DataSanitizerServiceTest {

    private DataSanitizerService sanitizerService;
    private PIIDetector piiDetector;
    private SecretScanner secretScanner;

    @BeforeEach
    void setUp() {
        piiDetector = new PIIDetector();
        secretScanner = new SecretScanner();
        sanitizerService = new DataSanitizerService(piiDetector, secretScanner);
    }

    @Test
    @DisplayName("Should sanitize content with PII")
    void shouldSanitizeContentWithPII() {
        SanitizationRequest request = SanitizationRequest.builder()
                .content("Contact me at john@example.com or call 555-123-4567")
                .userId(UUID.randomUUID())
                .context("test_generation")
                .strictMode(true)
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.isDataRedacted()).isTrue();
        assertThat(result.isShouldBlock()).isFalse(); // PII doesn't block in strict mode
        assertThat(result.getSanitizedContent()).contains("[EMAIL_REDACTED]");
        assertThat(result.getSanitizedContent()).contains("[PHONE_REDACTED]");
        assertThat(result.getPiiRedactionCount()).isGreaterThan(0);
        assertThat(result.getTotalRedactionCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should block request with critical secrets in strict mode")
    void shouldBlockRequestWithCriticalSecretsInStrictMode() {
        SanitizationRequest request = SanitizationRequest.builder()
                .content("AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE")
                .userId(UUID.randomUUID())
                .context("test_generation")
                .strictMode(true)
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.isDataRedacted()).isTrue();
        assertThat(result.isShouldBlock()).isTrue();
        assertThat(result.getSanitizedContent()).contains("[AWS_ACCESS_KEY_REDACTED]");
        assertThat(result.getSecretRedactionCount()).isGreaterThan(0);
        assertThat(result.getCriticalIssues()).isNotEmpty();
    }

    @Test
    @DisplayName("Should not block request with critical secrets when strict mode is off")
    void shouldNotBlockWithoutStrictMode() {
        SanitizationRequest request = SanitizationRequest.builder()
                .content("AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE")
                .userId(UUID.randomUUID())
                .context("failure_analysis")
                .strictMode(false)
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.isDataRedacted()).isTrue();
        assertThat(result.isShouldBlock()).isFalse(); // Strict mode off
        assertThat(result.getSanitizedContent()).contains("[AWS_ACCESS_KEY_REDACTED]");
    }

    @Test
    @DisplayName("Should sanitize complex content with multiple sensitive data types")
    void shouldSanitizeComplexContent() {
        String content = """
                Test failed with error:
                Connection failed for user john@example.com
                Database URL: postgresql://admin:MyPassword123@localhost:5432/testdb
                AWS Key: AKIAIOSFODNN7EXAMPLE
                Phone support: 555-123-4567
                SSN in logs: 123-45-6789
                """;

        SanitizationRequest request = SanitizationRequest.builder()
                .content(content)
                .userId(UUID.randomUUID())
                .context("failure_analysis")
                .strictMode(true)
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.isDataRedacted()).isTrue();
        assertThat(result.isShouldBlock()).isTrue(); // Critical secrets found
        assertThat(result.getSanitizedContent())
                .contains("[EMAIL_REDACTED]")
                .contains("[DATABASE_URL_REDACTED]")
                .contains("[AWS_ACCESS_KEY_REDACTED]")
                .contains("[PHONE_REDACTED]")
                .contains("[SSN_REDACTED]");

        assertThat(result.getTotalRedactionCount()).isGreaterThanOrEqualTo(5);
        assertThat(result.getPiiRedactionCount()).isGreaterThan(0);
        assertThat(result.getSecretRedactionCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle empty content")
    void shouldHandleEmptyContent() {
        SanitizationRequest request = SanitizationRequest.builder()
                .content("")
                .userId(UUID.randomUUID())
                .context("test")
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.getSanitizedContent()).isEmpty();
        assertThat(result.isDataRedacted()).isFalse();
        assertThat(result.isShouldBlock()).isFalse();
        assertThat(result.getTotalRedactionCount()).isZero();
    }

    @Test
    @DisplayName("Should handle null content")
    void shouldHandleNullContent() {
        SanitizationRequest request = SanitizationRequest.builder()
                .content(null)
                .userId(UUID.randomUUID())
                .context("test")
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.getSanitizedContent()).isEmpty();
        assertThat(result.isDataRedacted()).isFalse();
        assertThat(result.isShouldBlock()).isFalse();
    }

    @Test
    @DisplayName("Should detect sensitive data presence")
    void shouldDetectSensitiveDataPresence() {
        String withPII = "Email: test@example.com";
        String withSecret = "api_key=abc123def456";
        String clean = "This is clean text";

        assertThat(sanitizerService.containsSensitiveData(withPII)).isTrue();
        assertThat(sanitizerService.containsSensitiveData(withSecret)).isTrue();
        assertThat(sanitizerService.containsSensitiveData(clean)).isFalse();
    }

    @Test
    @DisplayName("Should detect critical data presence")
    void shouldDetectCriticalDataPresence() {
        String withSSN = "SSN: 123-45-6789";
        String withAWSKey = "AKIAIOSFODNN7EXAMPLE";
        String withEmail = "test@example.com"; // Not critical

        assertThat(sanitizerService.containsCriticalData(withSSN)).isTrue();
        assertThat(sanitizerService.containsCriticalData(withAWSKey)).isTrue();
        assertThat(sanitizerService.containsCriticalData(withEmail)).isFalse();
    }

    @Test
    @DisplayName("Should preserve clean content")
    void shouldPreserveCleanContent() {
        String cleanContent = "This is a normal test description without any sensitive data";

        SanitizationRequest request = SanitizationRequest.builder()
                .content(cleanContent)
                .userId(UUID.randomUUID())
                .context("test_generation")
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.getSanitizedContent()).isEqualTo(cleanContent);
        assertThat(result.isDataRedacted()).isFalse();
        assertThat(result.isShouldBlock()).isFalse();
        assertThat(result.getTotalRedactionCount()).isZero();
    }

    @Test
    @DisplayName("Should warn about critical PII")
    void shouldWarnAboutCriticalPII() {
        SanitizationRequest request = SanitizationRequest.builder()
                .content("SSN: 123-45-6789, Credit Card: 4111111111111111")
                .userId(UUID.randomUUID())
                .context("test_generation")
                .strictMode(true)
                .build();

        SanitizationResult result = sanitizerService.sanitize(request);

        assertThat(result.isDataRedacted()).isTrue();
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings().get(0)).containsIgnoringCase("critical pii");
    }
}