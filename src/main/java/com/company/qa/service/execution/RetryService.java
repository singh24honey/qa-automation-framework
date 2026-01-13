package com.company.qa.service.execution;

import com.company.qa.model.dto.FailureAnalysis;
import com.company.qa.model.dto.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryService {

    private final FailureAnalyzer failureAnalyzer;

    public <T> RetryResult<T> executeWithRetry(
            Supplier<T> operation,
            RetryConfig config,
            String operationName) {

        List<FailureAnalysis> failures = new ArrayList<>();
        int attempt = 0;

        while (attempt < config.getMaxAttempts()) {
            attempt++;

            try {
                log.debug("Attempt {}/{} for: {}", attempt, config.getMaxAttempts(), operationName);

                T result = operation.get();

                if (attempt > 1) {
                    log.info("Operation succeeded on attempt {}: {}", attempt, operationName);
                }

                return RetryResult.<T>builder()
                        .success(true)
                        .result(result)
                        .attempts(attempt)
                        .failures(failures)
                        .build();

            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for {}: {}",
                        attempt, config.getMaxAttempts(), operationName, e.getMessage());

                FailureAnalysis analysis = failureAnalyzer.analyze(e, null);
                failures.add(analysis);

                boolean shouldRetry = shouldRetryAfterFailure(analysis, config, attempt);

                if (!shouldRetry || attempt >= config.getMaxAttempts()) {
                    log.error("Operation failed after {} attempts: {}", attempt, operationName);

                    return RetryResult.<T>builder()
                            .success(false)
                            .attempts(attempt)
                            .failures(failures)
                            .lastFailure(analysis)
                            .build();
                }

                // Wait before retry
                if (attempt < config.getMaxAttempts()) {
                    waitBeforeRetry(config.getDelaySeconds(), attempt);
                }
            }
        }

        // Should not reach here, but just in case
        return RetryResult.<T>builder()
                .success(false)
                .attempts(attempt)
                .failures(failures)
                .build();
    }

    private boolean shouldRetryAfterFailure(FailureAnalysis analysis,
                                            RetryConfig config,
                                            int currentAttempt) {

        if (!config.getEnabled()) {
            log.debug("Retry disabled");
            return false;
        }

        if (currentAttempt >= config.getMaxAttempts()) {
            log.debug("Max attempts reached");
            return false;
        }

        // Check if this failure type should be retried
        switch (analysis.getFailureType()) {
            case TIMEOUT:
                return config.getRetryOnTimeout();

            case NETWORK_ERROR:
            case STALE_ELEMENT:
            case ELEMENT_NOT_FOUND:
                return config.getRetryOnNetworkError();

            case ASSERTION_FAILED:
                return config.getRetryOnAssertionFailure();

            case ELEMENT_NOT_INTERACTABLE:
            case INVALID_SELECTOR:
            case APPLICATION_ERROR:
                // Don't retry permanent failures
                return false;

            default:
                // Retry unknown errors conservatively
                return false;
        }
    }

    private void waitBeforeRetry(int delaySeconds, int attempt) {
        // Exponential backoff: delay * attempt
        int waitTime = delaySeconds * attempt;

        log.debug("Waiting {} seconds before retry...", waitTime);

        try {
            Thread.sleep(waitTime * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Wait interrupted");
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class RetryResult<T> {
        private boolean success;
        private T result;
        private int attempts;
        private List<FailureAnalysis> failures;
        private FailureAnalysis lastFailure;
    }
}