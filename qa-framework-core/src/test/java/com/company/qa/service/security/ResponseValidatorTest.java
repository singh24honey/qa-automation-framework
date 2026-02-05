package com.company.qa.service.security;

import com.company.qa.model.dto.ValidationResult;
import com.company.qa.model.enums.ViolationType;
import com.company.qa.service.security.ResponseValidator.ResponseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Response Validator Tests")
class ResponseValidatorTest {

    @Mock
    private DataSanitizerService sanitizerService;

    private ResponseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResponseValidator(sanitizerService);
    }

    @Test
    @DisplayName("Should validate safe response as valid")
    void shouldValidateSafeResponse() {
        String safeResponse = "This is a safe test case with no issues";
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(safeResponse, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isTrue();
        assertThat(result.isShouldBlock()).isFalse();
        assertThat(result.getViolations()).isEmpty();
    }

    @Test
    @DisplayName("Should detect PII in response")
    void shouldDetectPIIInResponse() {
        String responseWithPII = "Test case with email test@example.com";
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(true);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(responseWithPII, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isShouldBlock()).isTrue();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.PII_DETECTED);
    }

    @Test
    @DisplayName("Should detect secrets in response")
    void shouldDetectSecretsInResponse() {
        String responseWithSecret = "API Key: AKIAIOSFODNN7EXAMPLE";
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(true);

        ValidationResult result = validator.validate(responseWithSecret, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isShouldBlock()).isTrue();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.SECRET_DETECTED);
    }

    @Test
    @DisplayName("Should detect dangerous code patterns")
    void shouldDetectDangerousCodePatterns() {
        String dangerousCode = "Runtime.getRuntime().exec(\"rm -rf /\")";
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(dangerousCode, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isShouldBlock()).isTrue();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.DANGEROUS_CODE);
    }

    @Test
    @DisplayName("Should detect multiple dangerous patterns")
    void shouldDetectMultipleDangerousPatterns() {
        String code = """
                Runtime.getRuntime().exec("ls");
                System.exit(0);
                Files.delete(path);
                """;
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(code, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isShouldBlock()).isTrue();
        assertThat(result.getViolations())
                .filteredOn(v -> v.getType() == ViolationType.DANGEROUS_CODE)
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Should detect blocked domains")
    void shouldDetectBlockedDomains() {
        String responseWithBlockedDomain = "Check this out: pastebin.com/abc123";
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(responseWithBlockedDomain, ResponseType.SUGGESTION);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.BLOCKED_DOMAIN);
    }

    @Test
    @DisplayName("Should detect invalid imports")
    void shouldDetectInvalidImports() {
        String codeWithInvalidImport = """
                import org.junit.jupiter.api.Test;
                import com.malicious.library.Hack;
                import java.util.List;
                
                public class MyTest {
                    @Test
                    void test() {}
                }
                """;
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(codeWithInvalidImport, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.INVALID_IMPORT);
    }

    @Test
    @DisplayName("Should allow whitelisted imports")
    void shouldAllowWhitelistedImports() {
        String codeWithValidImports = """
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.WebDriver;
                import com.company.qa.model.TestCase;
                import java.util.List;
                import static org.junit.jupiter.api.Assertions.*;
                
                public class MyTest {
                    @Test
                    void test() {}
                }
                """;
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(codeWithValidImports, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations())
                .filteredOn(v -> v.getType() == ViolationType.INVALID_IMPORT)
                .isEmpty();
    }

    @Test
    @DisplayName("Should detect excessive response length")
    void shouldDetectExcessiveResponseLength() {
        String longResponse = "x".repeat(100_001);
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(longResponse, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.LENGTH_VIOLATION);
    }

    @Test
    @DisplayName("Should reject empty response")
    void shouldRejectEmptyResponse() {
        ValidationResult result = validator.validate("", ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.SCHEMA);
    }

    @Test
    @DisplayName("Should reject null response")
    void shouldRejectNullResponse() {
        ValidationResult result = validator.validate(null, ResponseType.TEST_CODE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anyMatch(v -> v.getType() == ViolationType.SCHEMA);
    }

    @Test
    @DisplayName("Should check if response is safe")
    void shouldCheckIfResponseIsSafe() {
        String safeResponse = "Normal test code";
        String unsafeResponse = "Runtime.getRuntime().exec()";

        when(sanitizerService.containsSensitiveData("Normal test code")).thenReturn(false);
        when(sanitizerService.containsCriticalData("Normal test code")).thenReturn(false);
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        assertThat(validator.isResponseSafe(safeResponse)).isTrue();
        assertThat(validator.isResponseSafe(unsafeResponse)).isFalse();
    }

    @Test
    @DisplayName("Should not check dangerous patterns for analysis responses")
    void shouldNotCheckDangerousPatternsForAnalysis() {
        String analysis = "The test failed because Runtime.getRuntime() was called";
        when(sanitizerService.containsSensitiveData(anyString())).thenReturn(false);
        when(sanitizerService.containsCriticalData(anyString())).thenReturn(false);

        ValidationResult result = validator.validate(analysis, ResponseType.ANALYSIS);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations())
                .filteredOn(v -> v.getType() == ViolationType.DANGEROUS_CODE)
                .isEmpty();
    }
}