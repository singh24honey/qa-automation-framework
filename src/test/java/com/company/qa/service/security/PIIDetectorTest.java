package com.company.qa.service.security;

import com.company.qa.model.dto.SanitizationResult.RedactionType;
import com.company.qa.service.security.PIIDetector.PIIDetectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PII Detector Tests")
class PIIDetectorTest {

    private PIIDetector piiDetector;

    @BeforeEach
    void setUp() {
        piiDetector = new PIIDetector();
    }

    @Test
    @DisplayName("Should detect and redact email addresses")
    void shouldDetectAndRedactEmails() {
        String content = "Contact me at john.doe@example.com or jane@company.org";

        PIIDetectionResult result = piiDetector.redactPII(content, false);

        assertThat(result.sanitizedContent()).contains("[EMAIL_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("john.doe@example.com");
        assertThat(result.sanitizedContent()).doesNotContain("jane@company.org");
        assertThat(result.redactionTypes()).contains(RedactionType.EMAIL);
        assertThat(result.redactionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should detect and redact phone numbers")
    void shouldDetectAndRedactPhoneNumbers() {
        String content = "Call me at 555-123-4567 or (555) 987-6543";

        PIIDetectionResult result = piiDetector.redactPII(content, false);

        assertThat(result.sanitizedContent()).contains("[PHONE_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("555-123-4567");
        assertThat(result.redactionTypes()).contains(RedactionType.PHONE_NUMBER);
        assertThat(result.redactionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should detect and redact SSN")
    void shouldDetectAndRedactSSN() {
        String content = "My SSN is 123-45-6789";

        PIIDetectionResult result = piiDetector.redactPII(content, false);

        assertThat(result.sanitizedContent()).contains("[SSN_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("123-45-6789");
        assertThat(result.redactionTypes()).contains(RedactionType.SSN);
        assertThat(result.redactionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should detect and redact credit card numbers")
    void shouldDetectAndRedactCreditCards() {
        String content = "Visa: 4111111111111111, Mastercard: 5500000000000004";

        PIIDetectionResult result = piiDetector.redactPII(content, false);

        assertThat(result.sanitizedContent()).contains("[CREDIT_CARD_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("4111111111111111");
        assertThat(result.sanitizedContent()).doesNotContain("5500000000000004");
        assertThat(result.redactionTypes()).contains(RedactionType.CREDIT_CARD);
        assertThat(result.redactionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should detect critical PII (SSN and Credit Card)")
    void shouldDetectCriticalPII() {
        String contentWithSSN = "SSN: 123-45-6789";
        String contentWithCC = "Card: 4111111111111111";
        String contentSafe = "No PII here";

        assertThat(piiDetector.containsCriticalPII(contentWithSSN)).isTrue();
        assertThat(piiDetector.containsCriticalPII(contentWithCC)).isTrue();
        assertThat(piiDetector.containsCriticalPII(contentSafe)).isFalse();
    }

    @Test
    @DisplayName("Should detect any PII")
    void shouldDetectAnyPII() {
        String contentWithEmail = "Email: test@example.com";
        String contentWithPhone = "Phone: 555-1234";
        String contentSafe = "No PII here";

        assertThat(piiDetector.containsPII(contentWithEmail)).isTrue();
        assertThat(piiDetector.containsPII(contentWithPhone)).isTrue();
        assertThat(piiDetector.containsPII(contentSafe)).isFalse();
    }

    @Test
    @DisplayName("Should redact IP addresses when requested")
    void shouldRedactIPAddressesWhenRequested() {
        String content = "Server IP: 192.168.1.100";

        PIIDetectionResult result = piiDetector.redactPII(content, true);

        assertThat(result.sanitizedContent()).contains("[IP_REDACTED]");
        assertThat(result.sanitizedContent()).doesNotContain("192.168.1.100");
        assertThat(result.redactionTypes()).contains(RedactionType.IP_ADDRESS);
    }

    @Test
    @DisplayName("Should not redact IP addresses by default")
    void shouldNotRedactIPAddressesByDefault() {
        String content = "Server IP: 192.168.1.100";

        PIIDetectionResult result = piiDetector.redactPII(content, false);

        assertThat(result.sanitizedContent()).contains("192.168.1.100");
        assertThat(result.redactionTypes()).doesNotContain(RedactionType.IP_ADDRESS);
    }

    @Test
    @DisplayName("Should handle null and empty content")
    void shouldHandleNullAndEmptyContent() {
        assertThat(piiDetector.containsPII(null)).isFalse();
        assertThat(piiDetector.containsPII("")).isFalse();

        PIIDetectionResult result = piiDetector.redactPII("", false);
        assertThat(result.sanitizedContent()).isEmpty();
        assertThat(result.redactionCount()).isZero();
    }

    @Test
    @DisplayName("Should handle content with multiple PII types")
    void shouldHandleMultiplePIITypes() {
        String content = "Contact John at john@example.com or 555-123-4567. " +
                "SSN: 123-45-6789, Card: 4111111111111111";

        PIIDetectionResult result = piiDetector.redactPII(content, false);

        assertThat(result.sanitizedContent())
                .contains("[EMAIL_REDACTED]")
                .contains("[PHONE_REDACTED]")
                .contains("[SSN_REDACTED]")
                .contains("[CREDIT_CARD_REDACTED]");

        assertThat(result.redactionTypes()).contains(
                RedactionType.EMAIL,
                RedactionType.PHONE_NUMBER,
                RedactionType.SSN,
                RedactionType.CREDIT_CARD
        );

        assertThat(result.redactionCount()).isGreaterThanOrEqualTo(4);
    }
}