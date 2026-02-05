package com.company.qa.service.execution;

import com.company.qa.model.dto.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.TimeoutException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock
    private FailureAnalyzer failureAnalyzer;

    @InjectMocks
    private RetryService retryService;

    private RetryConfig retryConfig;

    @BeforeEach
    void setUp() {
        retryConfig = RetryConfig.builder()
                .enabled(true)
                .maxAttempts(3)
                .delaySeconds(1)
                .retryOnTimeout(true)
                .build();
    }

    @Test
    @DisplayName("Should succeed on first attempt")
    void executeWithRetry_SuccessFirstAttempt() {
        // Given
        AtomicInteger counter = new AtomicInteger(0);

        // When
        RetryService.RetryResult<String> result = retryService.executeWithRetry(
                () -> {
                    counter.incrementAndGet();
                    return "success";
                },
                retryConfig,
                "test-operation"
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("success");
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should retry and succeed on second attempt")
    void executeWithRetry_SuccessAfterRetry() {
        // Given
        AtomicInteger counter = new AtomicInteger(0);

        when(failureAnalyzer.analyze(any(), any()))
                .thenReturn(com.company.qa.model.dto.FailureAnalysis.builder()
                        .failureType(com.company.qa.model.enums.FailureType.TIMEOUT)
                        .shouldRetry(true)
                        .build());

        // When
        RetryService.RetryResult<String> result = retryService.executeWithRetry(
                () -> {
                    int count = counter.incrementAndGet();
                    if (count == 1) {
                        throw new TimeoutException("Timeout on first attempt");
                    }
                    return "success";
                },
                retryConfig,
                "test-operation"
        );

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("success");
        assertThat(result.getAttempts()).isEqualTo(2);
        assertThat(result.getFailures()).hasSize(1);
    }

    @Test
    @DisplayName("Should fail after max retries")
    void executeWithRetry_FailAfterMaxRetries() {
        // Given
        AtomicInteger counter = new AtomicInteger(0);

        when(failureAnalyzer.analyze(any(), any()))
                .thenReturn(com.company.qa.model.dto.FailureAnalysis.builder()
                        .failureType(com.company.qa.model.enums.FailureType.TIMEOUT)
                        .shouldRetry(true)
                        .build());

        // When
        RetryService.RetryResult<String> result = retryService.executeWithRetry(
                () -> {
                    counter.incrementAndGet();
                    throw new TimeoutException("Always fails");
                },
                retryConfig,
                "test-operation"
        );

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttempts()).isEqualTo(3);
        assertThat(result.getFailures()).hasSize(3);
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should not retry when disabled")
    void executeWithRetry_DisabledRetry_NoRetry() {
        // Given
        retryConfig.setEnabled(false);
        AtomicInteger counter = new AtomicInteger(0);

        when(failureAnalyzer.analyze(any(), any()))
                .thenReturn(com.company.qa.model.dto.FailureAnalysis.builder()
                        .failureType(com.company.qa.model.enums.FailureType.TIMEOUT)
                        .shouldRetry(true)
                        .build());

        // When
        RetryService.RetryResult<String> result = retryService.executeWithRetry(
                () -> {
                    counter.incrementAndGet();
                    throw new TimeoutException("Fails");
                },
                retryConfig,
                "test-operation"
        );

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
    }
}