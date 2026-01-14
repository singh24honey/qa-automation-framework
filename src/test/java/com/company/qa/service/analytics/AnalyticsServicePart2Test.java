package com.company.qa.service.analytics;

import com.company.qa.model.dto.FailurePatternDTO;
import com.company.qa.model.dto.TestSuiteHealthDTO;
import com.company.qa.model.dto.AnalyticsDashboardDTO;
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
class AnalyticsServicePart2Test {

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
    @DisplayName("Should analyze failure patterns correctly")
    void analyzeFailurePatterns_WithFailures_ReturnsPatterns() {
        // Given
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        UUID testId1 = UUID.randomUUID();
        UUID testId2 = UUID.randomUUID();

        Test test1 = Test.builder()
                .id(testId1)
                .name("Test 1")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .build();

        Test test2 = Test.builder()
                .id(testId2)
                .name("Test 2")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .build();

        List<TestExecution> executions = Arrays.asList(
                TestExecution.builder()
                        .testId(testId1)
                        .status(TestStatus.FAILED)
                        .errorDetails("TimeoutException: Element not found")
                        .browser("CHROME")
                        .startTime(start)
                        .build(),
                TestExecution.builder()
                        .testId(testId2)
                        .status(TestStatus.FAILED)
                        .errorDetails("TimeoutException: Element not found")
                        .browser("FIREFOX")
                        .startTime(start)
                        .build(),
                TestExecution.builder()
                        .testId(testId1)
                        .status(TestStatus.ERROR)
                        .errorDetails("NullPointerException: Object is null")
                        .browser("CHROME")
                        .startTime(start)
                        .build()
        );

        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);
        when(testRepository.findById(testId1)).thenReturn(Optional.of(test1));
        when(testRepository.findById(testId2)).thenReturn(Optional.of(test2));

        // When
        List<FailurePatternDTO> result = analyticsService.analyzeFailurePatterns(start, end);

        // Then
        assertThat(result).hasSize(2);

        // First pattern should be TimeoutException (2 occurrences)
        FailurePatternDTO firstPattern = result.get(0);
        assertThat(firstPattern.getErrorType()).contains("TimeoutException");
        assertThat(firstPattern.getOccurrenceCount()).isEqualTo(2);
        assertThat(firstPattern.getPercentage()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.01));
        assertThat(firstPattern.getAffectedBrowsers()).containsExactlyInAnyOrder("CHROME", "FIREFOX");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should calculate suite health with no tests")
    void calculateSuiteHealth_WithNoTests_ReturnsEmptyHealth() {
        // Given
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        when(testRepository.findAll()).thenReturn(Collections.emptyList());
        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(Collections.emptyList());

        // When
        TestSuiteHealthDTO result = analyticsService.calculateSuiteHealth(start, end);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalTests()).isEqualTo(0);
        assertThat(result.getOverallHealthScore()).isEqualTo(0.0);
        assertThat(result.getTopIssues()).contains("No tests found in suite");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should calculate suite health with healthy tests")
    void calculateSuiteHealth_WithHealthyTests_ReturnsHighScore() {
        // Given
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        List<Test> tests = Arrays.asList(
                Test.builder().id(UUID.randomUUID()).name("Test 1").isActive(true)
                        .framework(TestFramework.SELENIUM).language("json").build(),
                Test.builder().id(UUID.randomUUID()).name("Test 2").isActive(true)
                        .framework(TestFramework.SELENIUM).language("json").build()
        );

        // All executions passed
        List<TestExecution> executions = Arrays.asList(
                TestExecution.builder().testId(tests.get(0).getId())
                        .status(TestStatus.PASSED).duration(1000).startTime(start).build(),
                TestExecution.builder().testId(tests.get(1).getId())
                        .status(TestStatus.PASSED).duration(2000).startTime(start).build(),
                TestExecution.builder().testId(tests.get(0).getId())
                        .status(TestStatus.PASSED).duration(1500).startTime(start).build()
        );

        when(testRepository.findAll()).thenReturn(tests);
        when(testRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return tests.stream().filter(t -> t.getId().equals(id)).findFirst();
        });
        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);

        // When
        TestSuiteHealthDTO result = analyticsService.calculateSuiteHealth(start, end);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalTests()).isEqualTo(2);
        assertThat(result.getActiveTests()).isEqualTo(2);
        assertThat(result.getAveragePassRate()).isEqualTo(100.0);
        assertThat(result.getOverallHealthScore()).isGreaterThan(90.0);
        assertThat(result.getFlakyTests()).isEqualTo(0);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should generate analytics dashboard with all components")
    void getAnalyticsDashboard_WithData_ReturnsCompleteDashboard() {
        // Given
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        List<Test> tests = Arrays.asList(
                Test.builder().id(UUID.randomUUID()).name("Test 1").isActive(true)
                        .framework(TestFramework.SELENIUM).language("json").build()
        );

        List<TestExecution> executions = Arrays.asList(
                TestExecution.builder().testId(tests.get(0).getId())
                        .status(TestStatus.PASSED).duration(1000).startTime(start).build()
        );

        when(testRepository.findAll()).thenReturn(tests);
        when(testRepository.findById(any())).thenReturn(Optional.of(tests.get(0)));
        when(executionRepository.findByStartTimeBetween(start, end)).thenReturn(executions);

        // When
        AnalyticsDashboardDTO result = analyticsService.getAnalyticsDashboard(start, end);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuiteHealth()).isNotNull();
        assertThat(result.getFlakyTests()).isNotNull();
        assertThat(result.getSlowTests()).isNotNull();
        assertThat(result.getCommonFailures()).isNotNull();
        assertThat(result.getTrends()).isNotNull();
        assertThat(result.getGeneratedAt()).isNotNull();
    }
}