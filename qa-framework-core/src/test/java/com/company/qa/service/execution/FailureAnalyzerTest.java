package com.company.qa.service.execution;

import com.company.qa.model.dto.FailureAnalysis;
import com.company.qa.model.enums.FailureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureAnalyzerTest {

    private FailureAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new FailureAnalyzer();
    }

    @Test
    @DisplayName("Should classify TimeoutException correctly")
    void analyze_TimeoutException_ReturnsTimeout() {
        // Given
        Exception exception = new TimeoutException("Element not found within timeout");

        // When
        FailureAnalysis analysis = analyzer.analyze(exception, 1);

        // Then
        assertThat(analysis.getFailureType()).isEqualTo(FailureType.TIMEOUT);
        assertThat(analysis.getShouldRetry()).isTrue();
        assertThat(analysis.getSuggestion()).contains("timeout");
    }

    @Test
    @DisplayName("Should classify NoSuchElementException correctly")
    void analyze_NoSuchElementException_ReturnsElementNotFound() {
        // Given
        Exception exception = new NoSuchElementException("Element not found");

        // When
        FailureAnalysis analysis = analyzer.analyze(exception, 2);

        // Then
        assertThat(analysis.getFailureType()).isEqualTo(FailureType.ELEMENT_NOT_FOUND);
        assertThat(analysis.getShouldRetry()).isTrue();
        assertThat(analysis.getStepNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should classify AssertionError correctly")
    void analyze_AssertionError_ReturnsAssertionFailed() {
        // Given
        Exception exception = new Exception("Expected 'Hello' but got 'Goodbye'");

        // When
        FailureAnalysis analysis = analyzer.analyze(exception, 3);

        // Then
        assertThat(analysis.getFailureType()).isEqualTo(FailureType.ASSERTION_FAILED);
        assertThat(analysis.getShouldRetry()).isFalse();
        assertThat(analysis.getSuggestion()).contains("genuine test failure");
    }

    @Test
    @DisplayName("Should determine if failure is transient")
    void isTransientFailure_TimeoutException_ReturnsTrue() {
        // Given
        Exception exception = new TimeoutException("Timeout");

        // When
        boolean isTransient = analyzer.isTransientFailure(exception);

        // Then
        assertThat(isTransient).isTrue();
    }

    @Test
    @DisplayName("Should determine if failure is permanent")
    void isTransientFailure_AssertionError_ReturnsFalse() {
        // Given
        Exception exception = new Exception("Assertion failed");

        // When
        boolean isTransient = analyzer.isTransientFailure(exception);

        // Then
        assertThat(isTransient).isFalse();
    }
}