package com.company.qa.service.analytics;

import com.company.qa.model.dto.FlakyTestDTO;
import com.company.qa.model.dto.PerformanceMetricsDTO;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.*;
import com.company.qa.repository.TestExecutionRepository;
import com.company.qa.repository.TestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private TestExecutionRepository executionRepository;

    @Mock
    private TestRepository testRepository;

    private AnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsServiceImpl(executionRepository, testRepository);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should detect flaky test with 50% pass rate")
    void detectFlakyTests_WithFlakyTest_ReturnsFlaky() {
        // Given
        UUID testId = UUID.randomUUID();
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        Test test = Test.builder()
                .id(testId)
                .name("Flaky Test")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .build();

        // Create 10 executions: 5 passed, 5 failed
        List<TestExecution> executions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            executions.add(TestExecution.builder()
                    .testId(testId)
                    .status(i % 2 == 0 ? TestStatus.PASSED : TestStatus.FAILED)
                    .startTime(start.plus(i, ChronoUnit.DAYS))
                    .build());
        }

        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        // When
        List<FlakyTestDTO> result = analyticsService.detectFlakyTests(start, end);

        // Then
        assertThat(result).hasSize(1);
        FlakyTestDTO flaky = result.get(0);
        assertThat(flaky.getTestName()).isEqualTo("Flaky Test");
        assertThat(flaky.getPassRate()).isEqualTo(50.0);
        assertThat(flaky.getStability()).isEqualTo(TestStability.FLAKY);
        assertThat(flaky.getFlakinessScore()).isGreaterThan(90.0);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not detect stable test as flaky")
    void detectFlakyTests_WithStableTest_ReturnsEmpty() {
        // Given
        UUID testId = UUID.randomUUID();
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        Test test = Test.builder()
                .id(testId)
                .name("Stable Test")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .build();

        // Create 10 executions: all passed
        List<TestExecution> executions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            executions.add(TestExecution.builder()
                    .testId(testId)
                    .status(TestStatus.PASSED)
                    .startTime(start.plus(i, ChronoUnit.DAYS))
                    .build());
        }

        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        // When
        List<FlakyTestDTO> result = analyticsService.detectFlakyTests(start, end);

        // Then
        assertThat(result).isEmpty(); // Stable test not included
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should require minimum 3 executions for flaky detection")
    void detectFlakyTests_WithInsufficientData_ReturnsEmpty() {
        // Given
        UUID testId = UUID.randomUUID();
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        // Only 2 executions
        List<TestExecution> executions = Arrays.asList(
                TestExecution.builder().testId(testId).status(TestStatus.PASSED).startTime(start).build(),
                TestExecution.builder().testId(testId).status(TestStatus.FAILED).startTime(start).build()
        );

        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);

        // When
        List<FlakyTestDTO> result = analyticsService.detectFlakyTests(start, end);

        // Then
        assertThat(result).isEmpty(); // Not enough data
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should calculate performance metrics correctly")
    void analyzePerformance_WithExecutions_ReturnsMetrics() {
        // Given
        UUID testId = UUID.randomUUID();
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        Test test = Test.builder()
                .id(testId)
                .name("Performance Test")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .build();

        List<TestExecution> executions = Arrays.asList(
                TestExecution.builder().testId(testId).duration(1000).startTime(start).status(TestStatus.PASSED).build(),
                TestExecution.builder().testId(testId).duration(2000).startTime(start).status(TestStatus.PASSED).build(),
                TestExecution.builder().testId(testId).duration(3000).startTime(start).status(TestStatus.PASSED).build()
        );

        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        // When
        PerformanceMetricsDTO result = analyticsService.getTestPerformance(testId, start, end);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTestName()).isEqualTo("Performance Test");
        assertThat(result.getAverageDuration()).isEqualTo(2000.0);
        assertThat(result.getMinDuration()).isEqualTo(1000.0);
        assertThat(result.getMaxDuration()).isEqualTo(3000.0);
        assertThat(result.getMedianDuration()).isEqualTo(2000.0);
        assertThat(result.getPerformanceRating()).isEqualTo("FAST");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should detect DEGRADING trend when performance worsens")
    void analyzePerformance_WithWorseningPerformance_ReturnsDegradingTrend() {
        // Given
        UUID testId = UUID.randomUUID();
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        Test test = Test.builder()
                .id(testId)
                .name("Degrading Test")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .build();

        // First 5 executions fast, next 5 slow
        List<TestExecution> executions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            executions.add(TestExecution.builder()
                    .testId(testId)
                    .duration(1000)
                    .startTime(start.plus(i, ChronoUnit.DAYS))
                    .status(TestStatus.PASSED)
                    .build());
        }
        for (int i = 5; i < 10; i++) {
            executions.add(TestExecution.builder()
                    .testId(testId)
                    .duration(5000) // 5x slower
                    .startTime(start.plus(i, ChronoUnit.DAYS))
                    .status(TestStatus.PASSED)
                    .build());
        }

        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        // When
        PerformanceMetricsDTO result = analyticsService.getTestPerformance(testId, start, end);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTrend()).isEqualTo(TrendDirection.DEGRADING);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should handle empty executions gracefully")
    void detectFlakyTests_WithNoExecutions_ReturnsEmpty() {
        // Given
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(Collections.emptyList());

        // When
        List<FlakyTestDTO> result = analyticsService.detectFlakyTests(start, end);

        // Then
        assertThat(result).isEmpty();
    }
}