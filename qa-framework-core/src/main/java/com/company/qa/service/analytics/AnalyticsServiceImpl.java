package com.company.qa.service.analytics;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStability;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.model.enums.TrendDirection;
import com.company.qa.repository.TestExecutionRepository;
import com.company.qa.repository.TestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final TestExecutionRepository executionRepository;
    private final TestRepository testRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MIN_EXECUTIONS_FOR_ANALYSIS = 3;

    @Override
    public List<FlakyTestDTO> detectFlakyTests(Instant startDate, Instant endDate) {
        log.info("Detecting flaky tests from {} to {}", startDate, endDate);

        List<TestExecution> executions = executionRepository.findByStartTimeBetween(startDate, endDate);

        if (executions.isEmpty()) {
            log.info("No executions found in date range");
            return Collections.emptyList();
        }

        // Group executions by test ID
        Map<UUID, List<TestExecution>> executionsByTest = executions.stream()
                .collect(Collectors.groupingBy(TestExecution::getTestId));

        List<FlakyTestDTO> flakyTests = new ArrayList<>();

        for (Map.Entry<UUID, List<TestExecution>> entry : executionsByTest.entrySet()) {
            UUID testId = entry.getKey();
            List<TestExecution> testExecutions = entry.getValue();

            // Need at least MIN_EXECUTIONS_FOR_ANALYSIS executions to determine flakiness
            if (testExecutions.size() < MIN_EXECUTIONS_FOR_ANALYSIS) {
                log.debug("Test {} has only {} executions, skipping flakiness analysis",
                        testId, testExecutions.size());
                continue;
            }

            FlakyTestDTO flakyTest = analyzeSingleTest(testId, startDate, endDate);

            if (flakyTest == null) {
                continue;
            }

            // Only include if flaky (pass rate between 20% and 95%)
            if (flakyTest.getStability() == TestStability.FLAKY ||
                    flakyTest.getStability() == TestStability.VERY_FLAKY ||
                    flakyTest.getStability() == TestStability.UNRELIABLE) {
                flakyTests.add(flakyTest);
            }
        }

        // Sort by flakiness score (higher = more flaky)
        flakyTests.sort((a, b) -> Double.compare(b.getFlakinessScore(), a.getFlakinessScore()));

        log.info("Found {} flaky tests out of {} analyzed",
                flakyTests.size(), executionsByTest.size());

        return flakyTests;
    }

    @Override
    public FlakyTestDTO analyzeSingleTest(UUID testId, Instant startDate, Instant endDate) {
        log.debug("Analyzing test {} for flakiness", testId);

        Optional<Test> testOpt = testRepository.findById(testId);
        if (testOpt.isEmpty()) {
            log.warn("Test not found: {}", testId);
            return null;
        }

        Test test = testOpt.get();

        List<TestExecution> executions = executionRepository.findByStartTimeBetween(startDate, endDate)
                .stream()
                .filter(e -> e.getTestId().equals(testId))
                .collect(Collectors.toList());

        if (executions.isEmpty()) {
            log.debug("No executions found for test {}", testId);
            return null;
        }

        // Calculate statistics
        int totalExecutions = executions.size();

        long passedCount = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.PASSED)
                .count();

        long failedCount = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.FAILED ||
                        e.getStatus() == TestStatus.ERROR)
                .count();

        double passRate = (passedCount * 100.0) / totalExecutions;

        // Calculate flakiness score (0-100, higher = more flaky)
        // A test that passes/fails 50% of the time is most flaky (score = 100)
        // Perfectly stable (100% or 0%) gets score = 0
        double flakinessScore = 100 - Math.abs(passRate - 50) * 2;

        TestStability stability = determineStability(passRate);

        // Get most recent failure
        Optional<Instant> lastFailure = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.FAILED ||
                        e.getStatus() == TestStatus.ERROR)
                .map(TestExecution::getStartTime)
                .max(Instant::compareTo);

        // Get common errors (top 3)
        List<String> commonErrors = executions.stream()
                .filter(e -> e.getErrorDetails() != null && !e.getErrorDetails().isEmpty())
                .map(TestExecution::getErrorDetails)
                .map(this::extractErrorSummary)
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String recommendation = generateRecommendation(stability, passRate, commonErrors);

        return FlakyTestDTO.builder()
                .testId(testId)
                .testName(test.getName())
                .stability(stability)
                .passRate(passRate)
                .totalExecutions(totalExecutions)
                .passedCount((int) passedCount)
                .failedCount((int) failedCount)
                .lastFailure(lastFailure.orElse(null))
                .commonErrors(commonErrors)
                .flakinessScore(flakinessScore)
                .recommendation(recommendation)
                .build();
    }

    @Override
    public List<PerformanceMetricsDTO> analyzePerformance(Instant startDate, Instant endDate) {
        log.info("Analyzing performance from {} to {}", startDate, endDate);

        List<TestExecution> executions = executionRepository.findByStartTimeBetween(startDate, endDate);

        if (executions.isEmpty()) {
            log.info("No executions found in date range");
            return Collections.emptyList();
        }

        // Group by test ID, filter out executions without duration
        Map<UUID, List<TestExecution>> executionsByTest = executions.stream()
                .filter(e -> e.getDuration() != null && e.getDuration() > 0)
                .collect(Collectors.groupingBy(TestExecution::getTestId));

        List<PerformanceMetricsDTO> performanceMetrics = new ArrayList<>();

        for (Map.Entry<UUID, List<TestExecution>> entry : executionsByTest.entrySet()) {
            UUID testId = entry.getKey();

            PerformanceMetricsDTO metrics = getTestPerformance(testId, startDate, endDate);
            if (metrics != null) {
                performanceMetrics.add(metrics);
            }
        }

        // Sort by average duration (slowest first)
        performanceMetrics.sort((a, b) ->
                Double.compare(b.getAverageDuration(), a.getAverageDuration()));

        log.info("Analyzed performance for {} tests", performanceMetrics.size());

        return performanceMetrics;
    }

    @Override
    public PerformanceMetricsDTO getTestPerformance(UUID testId, Instant startDate, Instant endDate) {
        log.debug("Analyzing performance for test {}", testId);

        Optional<Test> testOpt = testRepository.findById(testId);
        if (testOpt.isEmpty()) {
            log.warn("Test not found: {}", testId);
            return null;
        }

        Test test = testOpt.get();

        List<TestExecution> executions = executionRepository.findByStartTimeBetween(startDate, endDate)
                .stream()
                .filter(e -> e.getTestId().equals(testId))
                .filter(e -> e.getDuration() != null && e.getDuration() > 0)
                .sorted(Comparator.comparing(TestExecution::getStartTime))
                .collect(Collectors.toList());

        if (executions.isEmpty()) {
            log.debug("No executions with duration found for test {}", testId);
            return null;
        }

        // Extract durations
        List<Integer> durations = executions.stream()
                .map(TestExecution::getDuration)
                .collect(Collectors.toList());

        // Calculate statistics
        double avgDuration = durations.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        int minDuration = durations.stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);

        int maxDuration = durations.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        double medianDuration = calculateMedian(durations);
        double stdDev = calculateStandardDeviation(durations, avgDuration);

        // Calculate trend
        TrendDirection trend = calculateTrend(executions);

        // Build duration history
        List<PerformanceMetricsDTO.DurationDataPoint> durationHistory = executions.stream()
                .map(e -> PerformanceMetricsDTO.DurationDataPoint.builder()
                        .date(formatDate(e.getStartTime()))
                        .duration((double) e.getDuration())
                        .status(e.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        String performanceRating = ratePerformance(avgDuration);

        return PerformanceMetricsDTO.builder()
                .testId(testId)
                .testName(test.getName())
                .averageDuration(avgDuration)
                .minDuration((double) minDuration)
                .maxDuration((double) maxDuration)
                .medianDuration(medianDuration)
                .standardDeviation(stdDev)
                .trend(trend)
                .durationHistory(durationHistory)
                .performanceRating(performanceRating)
                .build();
    }

    // ========== Private Helper Methods ==========

    private TestStability determineStability(double passRate) {
        if (passRate >= 95.0) return TestStability.STABLE;
        if (passRate >= 80.0) return TestStability.MOSTLY_STABLE;
        if (passRate >= 50.0) return TestStability.FLAKY;
        if (passRate >= 20.0) return TestStability.VERY_FLAKY;
        return TestStability.UNRELIABLE;
    }

    private String generateRecommendation(TestStability stability, double passRate,
                                          List<String> commonErrors) {
        switch (stability) {
            case VERY_FLAKY:
            case UNRELIABLE:
                return String.format("CRITICAL: Test is highly unreliable (%.1f%% pass rate). " +
                                "Consider rewriting or removing this test. Common errors: %s",
                        passRate,
                        commonErrors.isEmpty() ? "None recorded" :
                                String.join(", ", commonErrors.subList(0, Math.min(2, commonErrors.size()))));

            case FLAKY:
                return String.format("WARNING: Test shows flaky behavior (%.1f%% pass rate). " +
                                "Add explicit waits, stabilize test data, or check for timing issues.",
                        passRate);

            case MOSTLY_STABLE:
                return String.format("Test is mostly stable (%.1f%% pass rate) but could be improved. " +
                        "Monitor for patterns in failures.", passRate);

            default:
                return "Test is stable and reliable.";
        }
    }

    private String extractErrorSummary(String errorDetails) {
        if (errorDetails == null || errorDetails.isEmpty()) {
            return "Unknown Error";
        }

        // Extract first line or first 80 characters
        String firstLine = errorDetails.split("\n")[0];
        if (firstLine.length() > 80) {
            return firstLine.substring(0, 80) + "...";
        }
        return firstLine;
    }

    private double calculateMedian(List<Integer> values) {
        if (values.isEmpty()) {
            return 0.0;
        }

        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();

        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    private double calculateStandardDeviation(List<Integer> values, double mean) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    private TrendDirection calculateTrend(List<TestExecution> executions) {
        if (executions.size() < 5) {
            return TrendDirection.INSUFFICIENT_DATA;
        }

        // Compare first half vs second half average duration
        int midPoint = executions.size() / 2;

        double firstHalfAvg = executions.subList(0, midPoint).stream()
                .filter(e -> e.getDuration() != null)
                .mapToInt(TestExecution::getDuration)
                .average()
                .orElse(0.0);

        double secondHalfAvg = executions.subList(midPoint, executions.size()).stream()
                .filter(e -> e.getDuration() != null)
                .mapToInt(TestExecution::getDuration)
                .average()
                .orElse(0.0);

        if (firstHalfAvg == 0.0) {
            return TrendDirection.INSUFFICIENT_DATA;
        }

        double changePercent = ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100;

        if (changePercent < -10) return TrendDirection.IMPROVING; // Getting faster
        if (changePercent > 10) return TrendDirection.DEGRADING;  // Getting slower
        return TrendDirection.STABLE;
    }

    private String formatDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneId.systemDefault()).format(DATE_FORMATTER);
    }

    private String ratePerformance(double avgDuration) {
        if (avgDuration < 5000) return "FAST";     // < 5 seconds
        if (avgDuration < 30000) return "NORMAL";  // < 30 seconds
        return "SLOW";                              // >= 30 seconds
    }

    // ADD THESE METHODS TO THE END OF AnalyticsServiceImpl class
// (Keep all Part 1 methods, add these below)

    @Override
    public List<FailurePatternDTO> analyzeFailurePatterns(Instant startDate, Instant endDate) {
        log.info("Analyzing failure patterns from {} to {}", startDate, endDate);

        List<TestExecution> failedExecutions = executionRepository.findByStartTimeBetween(startDate, endDate)
                .stream()
                .filter(e -> e.getStatus() == TestStatus.FAILED ||
                        e.getStatus() == TestStatus.ERROR)
                .filter(e -> e.getErrorDetails() != null)
                .collect(Collectors.toList());

        if (failedExecutions.isEmpty()) {
            log.info("No failures found in date range");
            return Collections.emptyList();
        }

        // Group by error type (first line of error)
        Map<String, List<TestExecution>> errorGroups = failedExecutions.stream()
                .collect(Collectors.groupingBy(e -> extractErrorType(e.getErrorDetails())));

        List<FailurePatternDTO> patterns = new ArrayList<>();
        int totalFailures = failedExecutions.size();

        for (Map.Entry<String, List<TestExecution>> entry : errorGroups.entrySet()) {
            String errorType = entry.getKey();
            List<TestExecution> group = entry.getValue();

            int count = group.size();
            double percentage = (count * 100.0) / totalFailures;

            // Get affected test names
            Set<String> affectedTests = group.stream()
                    .map(TestExecution::getTestId)
                    .map(testRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(Test::getName)
                    .collect(Collectors.toSet());

            // Get affected browsers
            Set<String> affectedBrowsers = group.stream()
                    .map(TestExecution::getBrowser)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            String recommendation = generateFailureRecommendation(errorType, affectedBrowsers);

            patterns.add(FailurePatternDTO.builder()
                    .errorType(errorType)
                    .occurrenceCount(count)
                    .percentage(percentage)
                    .affectedTests(new ArrayList<>(affectedTests))
                    .affectedBrowsers(new ArrayList<>(affectedBrowsers))
                    .recommendation(recommendation)
                    .build());
        }

        // Sort by occurrence count (most common first)
        patterns.sort((a, b) -> Integer.compare(b.getOccurrenceCount(), a.getOccurrenceCount()));

        log.info("Found {} distinct failure patterns from {} total failures",
                patterns.size(), totalFailures);

        return patterns;
    }

    @Override
    public TestSuiteHealthDTO calculateSuiteHealth(Instant startDate, Instant endDate) {
        log.info("Calculating test suite health from {} to {}", startDate, endDate);

        List<Test> allTests = testRepository.findAll();
        List<TestExecution> executions = executionRepository.findByStartTimeBetween(startDate, endDate);

        if (allTests.isEmpty()) {
            log.warn("No tests found in database");
            return createEmptySuiteHealth();
        }

        int totalTests = allTests.size();
        int activeTests = (int) allTests.stream()
                .filter(t -> t.getIsActive() != null && t.getIsActive())
                .count();

        // Detect flaky tests
        List<FlakyTestDTO> flakyTests = detectFlakyTests(startDate, endDate);
        int flakyCount = flakyTests.size();

        int stableCount = Math.max(0, activeTests - flakyCount);

        // Calculate average pass rate
        long totalExecutions = executions.size();
        long passedExecutions = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.PASSED)
                .count();

        double averagePassRate = totalExecutions > 0 ?
                (passedExecutions * 100.0) / totalExecutions : 0.0;

        // Calculate average execution time
        double averageExecutionTime = executions.stream()
                .filter(e -> e.getDuration() != null)
                .mapToInt(TestExecution::getDuration)
                .average()
                .orElse(0.0);

        // Calculate overall health score (0-100)
        double healthScore = calculateHealthScore(averagePassRate, flakyCount, totalTests);

        // Determine trend
        TrendDirection trend = calculateSuiteTrend(executions);

        // Generate top issues
        List<String> topIssues = generateTopIssues(flakyTests, executions);

        // Generate recommendations
        List<String> recommendations = generateHealthRecommendations(
                healthScore, flakyCount, averagePassRate);

        return TestSuiteHealthDTO.builder()
                .overallHealthScore(healthScore)
                .totalTests(totalTests)
                .activeTests(activeTests)
                .stableTests(stableCount)
                .flakyTests(flakyCount)
                .trend(trend)
                .averagePassRate(averagePassRate)
                .averageExecutionTime(averageExecutionTime)
                .topIssues(topIssues)
                .recommendations(recommendations)
                .analyzedAt(Instant.now())
                .build();
    }

    @Override
    public AnalyticsDashboardDTO getAnalyticsDashboard(Instant startDate, Instant endDate) {
        log.info("Generating analytics dashboard from {} to {}", startDate, endDate);

        // Get all components
        TestSuiteHealthDTO suiteHealth = calculateSuiteHealth(startDate, endDate);

        List<FlakyTestDTO> flakyTests = detectFlakyTests(startDate, endDate).stream()
                .limit(10)
                .collect(Collectors.toList());

        List<PerformanceMetricsDTO> slowTests = analyzePerformance(startDate, endDate).stream()
                .limit(10)
                .collect(Collectors.toList());

        List<FailurePatternDTO> commonFailures = analyzeFailurePatterns(startDate, endDate).stream()
                .limit(5)
                .collect(Collectors.toList());

        // Calculate trends
        AnalyticsDashboardDTO.TrendAnalysisDTO trends = calculateTrendAnalysis(startDate, endDate);

        return AnalyticsDashboardDTO.builder()
                .suiteHealth(suiteHealth)
                .flakyTests(flakyTests)
                .slowTests(slowTests)
                .commonFailures(commonFailures)
                .trends(trends)
                .generatedAt(Instant.now())
                .build();
    }

// ========== Additional Private Helper Methods for Part 2 ==========

    private String extractErrorType(String errorDetails) {
        if (errorDetails == null || errorDetails.isEmpty()) {
            return "Unknown Error";
        }

        // Extract first line or first 100 characters
        String firstLine = errorDetails.split("\n")[0];
        if (firstLine.length() > 100) {
            return firstLine.substring(0, 100) + "...";
        }
        return firstLine;
    }

    private String generateFailureRecommendation(String errorType, Set<String> affectedBrowsers) {
        if (affectedBrowsers.size() == 1) {
            return String.format("Browser-specific issue detected in %s. " +
                            "Review browser compatibility and element selectors.",
                    affectedBrowsers.iterator().next());
        } else if (errorType.toLowerCase().contains("timeout")) {
            return "Increase timeout values or optimize test execution speed.";
        } else if (errorType.toLowerCase().contains("element") &&
                errorType.toLowerCase().contains("not found")) {
            return "Review element locators and ensure page load waits are sufficient.";
        } else if (errorType.toLowerCase().contains("stale")) {
            return "Add waits before interacting with elements or refresh element references.";
        } else {
            return "Review test implementation and environment configuration.";
        }
    }

    private double calculateHealthScore(double passRate, int flakyCount, int totalTests) {
        if (totalTests == 0) {
            return 0.0;
        }

        // Base score from pass rate (0-70 points)
        double baseScore = (passRate / 100.0) * 70;

        // Deduct points for flaky tests (up to 30 points)
        double flakinessRatio = (double) flakyCount / totalTests;
        double flakinessDeduction = flakinessRatio * 30;

        return Math.max(0, Math.min(100, baseScore + (30 - flakinessDeduction)));
    }

    private TrendDirection calculateSuiteTrend(List<TestExecution> executions) {
        if (executions.size() < 10) {
            return TrendDirection.INSUFFICIENT_DATA;
        }

        // Sort by date
        List<TestExecution> sorted = executions.stream()
                .sorted(Comparator.comparing(TestExecution::getStartTime))
                .collect(Collectors.toList());

        int midPoint = sorted.size() / 2;

        // Compare pass rates
        long firstHalfPassed = sorted.subList(0, midPoint).stream()
                .filter(e -> e.getStatus() == TestStatus.PASSED)
                .count();

        long secondHalfPassed = sorted.subList(midPoint, sorted.size()).stream()
                .filter(e -> e.getStatus() == TestStatus.PASSED)
                .count();

        double firstHalfRate = (firstHalfPassed * 100.0) / midPoint;
        double secondHalfRate = (secondHalfPassed * 100.0) / (sorted.size() - midPoint);

        double change = secondHalfRate - firstHalfRate;

        if (change > 5) return TrendDirection.IMPROVING;
        if (change < -5) return TrendDirection.DEGRADING;
        return TrendDirection.STABLE;
    }

    private List<String> generateTopIssues(List<FlakyTestDTO> flakyTests,
                                           List<TestExecution> executions) {
        List<String> issues = new ArrayList<>();

        if (!flakyTests.isEmpty()) {
            issues.add(String.format("%d flaky tests detected requiring attention",
                    flakyTests.size()));
        }

        long failedCount = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.FAILED ||
                        e.getStatus() == TestStatus.ERROR)
                .count();

        if (failedCount > executions.size() * 0.1) {
            issues.add(String.format("High failure rate: %d failures out of %d executions",
                    failedCount, executions.size()));
        }

        // Check for slow tests
        double avgDuration = executions.stream()
                .filter(e -> e.getDuration() != null)
                .mapToInt(TestExecution::getDuration)
                .average()
                .orElse(0.0);

        if (avgDuration > 60000) { // > 1 minute
            issues.add("Average test execution time exceeds 1 minute");
        }

        if (issues.isEmpty()) {
            issues.add("No critical issues detected");
        }

        return issues;
    }

    private List<String> generateHealthRecommendations(double healthScore,
                                                       int flakyCount,
                                                       double passRate) {
        List<String> recommendations = new ArrayList<>();

        if (healthScore < 60) {
            recommendations.add("URGENT: Suite health is poor. " +
                    "Prioritize fixing flaky tests and improving pass rate.");
        } else if (healthScore < 80) {
            recommendations.add("Suite health needs improvement. " +
                    "Focus on stabilizing flaky tests.");
        }

        if (flakyCount > 0) {
            recommendations.add(String.format("Address %d flaky tests to improve suite reliability",
                    flakyCount));
        }

        if (passRate < 90) {
            recommendations.add("Improve pass rate by fixing failing tests " +
                    "and enhancing test stability");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Suite is healthy. Continue monitoring and maintain test quality.");
        }

        return recommendations;
    }

    private TestSuiteHealthDTO createEmptySuiteHealth() {
        return TestSuiteHealthDTO.builder()
                .overallHealthScore(0.0)
                .totalTests(0)
                .activeTests(0)
                .stableTests(0)
                .flakyTests(0)
                .trend(TrendDirection.INSUFFICIENT_DATA)
                .averagePassRate(0.0)
                .averageExecutionTime(0.0)
                .topIssues(List.of("No tests found in suite"))
                .recommendations(List.of("Create tests to begin analysis"))
                .analyzedAt(Instant.now())
                .build();
    }

    private AnalyticsDashboardDTO.TrendAnalysisDTO calculateTrendAnalysis(Instant startDate,
                                                                          Instant endDate) {
        List<TestExecution> executions = executionRepository.findByStartTimeBetween(startDate, endDate);

        if (executions.isEmpty()) {
            return AnalyticsDashboardDTO.TrendAnalysisDTO.builder()
                    .period("DAILY")
                    .passRateTrend(0.0)
                    .executionTimeTrend(0.0)
                    .totalExecutions(0)
                    .dailyMetrics(Collections.emptyList())
                    .build();
        }

        // Group by date
        Map<String, List<TestExecution>> executionsByDate = executions.stream()
                .collect(Collectors.groupingBy(e -> formatDate(e.getStartTime())));

        List<AnalyticsDashboardDTO.DailyMetric> dailyMetrics = new ArrayList<>();

        for (Map.Entry<String, List<TestExecution>> entry : executionsByDate.entrySet()) {
            List<TestExecution> dayExecutions = entry.getValue();

            long passed = dayExecutions.stream()
                    .filter(e -> e.getStatus() == TestStatus.PASSED)
                    .count();

            double passRate = (passed * 100.0) / dayExecutions.size();

            double avgDuration = dayExecutions.stream()
                    .filter(e -> e.getDuration() != null)
                    .mapToInt(TestExecution::getDuration)
                    .average()
                    .orElse(0.0);

            dailyMetrics.add(AnalyticsDashboardDTO.DailyMetric.builder()
                    .date(entry.getKey())
                    .executions(dayExecutions.size())
                    .passRate(passRate)
                    .avgDuration(avgDuration)
                    .build());
        }

        // Sort by date
        dailyMetrics.sort(Comparator.comparing(AnalyticsDashboardDTO.DailyMetric::getDate));

        // Calculate overall trends
        double passRateTrend = 0.0;
        double executionTimeTrend = 0.0;

        if (dailyMetrics.size() >= 2) {
            double firstPassRate = dailyMetrics.get(0).getPassRate();
            double lastPassRate = dailyMetrics.get(dailyMetrics.size() - 1).getPassRate();
            passRateTrend = lastPassRate - firstPassRate;

            double firstDuration = dailyMetrics.get(0).getAvgDuration();
            double lastDuration = dailyMetrics.get(dailyMetrics.size() - 1).getAvgDuration();

            if (firstDuration > 0) {
                executionTimeTrend = ((lastDuration - firstDuration) / firstDuration) * 100;
            }
        }

        return AnalyticsDashboardDTO.TrendAnalysisDTO.builder()
                .period("DAILY")
                .passRateTrend(passRateTrend)
                .executionTimeTrend(executionTimeTrend)
                .totalExecutions(executions.size())
                .dailyMetrics(dailyMetrics)
                .build();
    }
}