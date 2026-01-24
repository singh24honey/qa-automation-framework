package com.company.qa.service.analytics;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.*;
import com.company.qa.repository.*;
import com.company.qa.service.quality.TestQualityHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Populates executive dashboard tables from existing operational data.
 * FINAL CORRECTED VERSION - Uses ONLY methods and fields that actually exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutiveDataPopulationService {

    // Existing services
    private final AnalyticsService analyticsService;
    private final TestQualityHistoryService historyService;

    // Week 7 Day 3 repositories
    private final ExecutiveKPICacheRepository kpiCacheRepository;
    private final QualityTrendAnalysisRepository trendAnalysisRepository;
    private final ExecutiveAlertRepository alertRepository;

    // Operational repositories
    private final TestExecutionRepository executionRepository;
    private final TestQualitySnapshotRepository snapshotRepository;
    private final TestFailurePatternRepository patternRepository;

    /**
     * Generate daily KPI cache entry
     * CORRECTED: Uses only fields that actually exist
     */
    @Transactional
    public ExecutiveKPICache generateDailyKPIs() {
        log.info("Generating daily KPI cache");

        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

        // Check if already exists
        Optional<ExecutiveKPICache> existing = kpiCacheRepository
                .findByPeriodTypeAndPeriodStartAndPeriodEnd("DAILY", dayStart, dayEnd);

        if (existing.isPresent()) {
            log.info("KPI cache already exists for today, updating...");
            return updateKPICache(existing.get(), dayStart, dayEnd);
        }

        // Use calculateSuiteHealth() which actually exists
        TestSuiteHealthDTO health = analyticsService.calculateSuiteHealth(dayStart, dayEnd);

        // Use methods we added to repositories
        long totalExecutions = executionRepository.countByStartTimeBetween(dayStart, dayEnd);
        Double avgExecutionTime = executionRepository.findAverageExecutionTime(dayStart, dayEnd);

        // Get flaky test count from snapshot
        Optional<TestQualitySnapshot> todaySnapshot = snapshotRepository
                .findBySnapshotDate(LocalDate.now());
        int flakyCount = todaySnapshot.map(TestQualitySnapshot::getFlakyTests).orElse(0);

        // CORRECTED: Use overallHealthScore (which exists) for quality score
        BigDecimal qualityScore = BigDecimal.valueOf(health.getOverallHealthScore())
                .setScale(2, RoundingMode.HALF_UP);

        // Determine trend direction
        String trendDirection = calculateTrendDirection(health.getAveragePassRate());

        ExecutiveKPICache kpi = ExecutiveKPICache.builder()
                .periodType("DAILY")
                .periodStart(dayStart)
                .periodEnd(dayEnd)
                .overallPassRate(BigDecimal.valueOf(health.getAveragePassRate()))
                .trendDirection(trendDirection)
                .qualityScore(qualityScore)
                .totalExecutions(totalExecutions)
                .avgExecutionTimeMs(avgExecutionTime != null ? avgExecutionTime.longValue() : 0L)
                .flakyTestCount(flakyCount)
                // AI metrics - will be populated when AI features enabled
                .aiAccuracyRate(BigDecimal.ZERO)
                .aiCostTotal(BigDecimal.ZERO)
                .aiCostPerFix(BigDecimal.ZERO)
                .aiSuggestionsAccepted(0)
                .aiSuggestionsRejected(0)
                // Resource metrics
                .peakConcurrentExecutions(calculatePeakConcurrency(dayStart, dayEnd))
                .resourceUtilizationPct(BigDecimal.valueOf(75.0)) // Placeholder
                // Business impact
                .estimatedTimeSavedHours(calculateTimeSaved(totalExecutions, avgExecutionTime))
                .estimatedCostSavedUsd(BigDecimal.ZERO)
                .cacheGeneratedAt(now)
                .isFinal(false)
                .build();

        ExecutiveKPICache saved = kpiCacheRepository.save(kpi);
        log.info("Created daily KPI cache: quality score {}, {} executions",
                qualityScore, totalExecutions);

        return saved;
    }

    /**
     * Update existing KPI cache
     */
    private ExecutiveKPICache updateKPICache(ExecutiveKPICache existing,
                                             Instant dayStart, Instant dayEnd) {
        TestSuiteHealthDTO health = analyticsService.calculateSuiteHealth(dayStart, dayEnd);

        existing.setOverallPassRate(BigDecimal.valueOf(health.getAveragePassRate()));
        // CORRECTED: Use overallHealthScore
        existing.setQualityScore(BigDecimal.valueOf(health.getOverallHealthScore())
                .setScale(2, RoundingMode.HALF_UP));
        existing.setTrendDirection(calculateTrendDirection(health.getAveragePassRate()));
        existing.setCacheGeneratedAt(Instant.now());

        return kpiCacheRepository.save(existing);
    }

    /**
     * Generate quality trend analysis
     * CORRECTED: Uses correct field name for prediction
     */
    @Transactional
    public QualityTrendAnalysis generateTrendAnalysis(LocalDate analysisDate) {
        log.info("Generating trend analysis for {}", analysisDate);

        // Use the method we added
        Optional<QualityTrendAnalysis> existing = trendAnalysisRepository
                .findByAnalysisDateAndSuiteIdIsNull(analysisDate);

        if (existing.isPresent()) {
            log.info("Trend analysis already exists for {}", analysisDate);
            return existing.get();
        }

        // Use the correct method name
        List<TestQualitySnapshot> last7Days = snapshotRepository
                .findBySnapshotDateBetweenOrderBySnapshotDateAsc(
                        analysisDate.minusDays(7), analysisDate);

        List<TestQualitySnapshot> last30Days = snapshotRepository
                .findBySnapshotDateBetweenOrderBySnapshotDateAsc(
                        analysisDate.minusDays(30), analysisDate);

        // Calculate averages
        BigDecimal passRate7d = calculateAveragePassRate(last7Days);
        BigDecimal passRate30d = calculateAveragePassRate(last30Days);

        // Determine trend
        String trend = determineTrend(passRate7d, passRate30d);

        // Calculate volatility
        BigDecimal volatility = calculateVolatility(last7Days);

        // Get failure counts
        int newFailures = countNewFailures(analysisDate);
        int recurringFailures = countRecurringFailures(analysisDate);
        int resolvedFailures = countResolvedFailures(analysisDate);

        // Predict next 7 days
        BigDecimal predicted = predictPassRate(passRate7d, passRate30d);
        BigDecimal confidence = calculatePredictionConfidence(volatility);

        QualityTrendAnalysis analysis = QualityTrendAnalysis.builder()
                .analysisDate(analysisDate)
                .suiteId(null) // Global analysis
                .passRate7dAvg(passRate7d)
                .passRate30dAvg(passRate30d)
                .passRateTrend(trend)
                .passRateVolatility(volatility)
                .flakinessScore(calculateFlakinessScore(last7Days))
                .newFailuresCount(newFailures)
                .recurringFailuresCount(recurringFailures)
                .resolvedFailuresCount(resolvedFailures)
                // CORRECTED: Use correct field name
                .predictedNext7dPassRate(predicted)  // ← FIXED
                .confidenceScore(confidence)
                .createdAt(Instant.now())
                .build();

        QualityTrendAnalysis saved = trendAnalysisRepository.save(analysis);
        log.info("Created trend analysis: trend={}, volatility={}",
                trend, volatility);

        return saved;
    }

    /**
     * Generate alerts based on thresholds
     */
    @Transactional
    public void generateAlerts() {
        log.info("Generating executive alerts");

        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

        // Get current health
        TestSuiteHealthDTO health = analyticsService.calculateSuiteHealth(dayStart, dayEnd);

        // Alert 1: Low pass rate (CRITICAL)
        if (health.getAveragePassRate() < 80.0) {
            createAlertIfNotExists(
                    "QUALITY_DEGRADATION",
                    "CRITICAL",
                    "Overall pass rate dropped below 80%",
                    String.format("Current pass rate: %.2f%%", health.getAveragePassRate())
            );
        }

        // Alert 2: High flaky test count (HIGH)
        if (health.getFlakyTests() > 10) {
            createAlertIfNotExists(
                    "HIGH_FLAKY_TESTS",
                    "HIGH",
                    String.format("%d flaky tests detected", health.getFlakyTests()),
                    "Flaky tests reduce confidence in test suite"
            );
        }

        // Alert 3: High average execution time (MEDIUM)
        Double avgTime = executionRepository.findAverageExecutionTime(dayStart, dayEnd);
        if (avgTime != null && avgTime > 300000) { // 5 minutes
            createAlertIfNotExists(
                    "SLOW_EXECUTION",
                    "MEDIUM",
                    "Average execution time exceeds threshold",
                    String.format("Current average: %.2f seconds", avgTime / 1000.0)
            );
        }

        // Alert 4: High-impact failure patterns (HIGH)
        List<StoredPatternDTO> highImpact = historyService.getHighImpactPatterns(50.0);
        if (!highImpact.isEmpty()) {
            createAlertIfNotExists(
                    "HIGH_IMPACT_PATTERNS",
                    "HIGH",
                    String.format("%d high-impact failure patterns detected", highImpact.size()),
                    "Multiple tests failing with same pattern"
            );
        }

        log.info("Alert generation completed");
    }

    /**
     * Create alert only if it doesn't already exist and is active
     */
    private void createAlertIfNotExists(String alertType, String severity,
                                        String title, String description) {
        // Check if active alert already exists for this type
        List<ExecutiveAlert> existing = alertRepository
                .findByAlertTypeAndStatusOrderByDetectedAtDesc(alertType, "ACTIVE");

        if (!existing.isEmpty()) {
            log.debug("Active alert already exists for type: {}", alertType);
            return;
        }

        ExecutiveAlert alert = ExecutiveAlert.builder()
                .alertType(alertType)
                .severity(severity)
                .status("ACTIVE")
                .title(title)
                .description(description)
                .detectedAt(Instant.now())
                .build();

        alertRepository.save(alert);
        log.info("Created {} alert: {}", severity, title);
    }

    // ========== HELPER METHODS ==========

    private String calculateTrendDirection(double currentPassRate) {
        // Simple comparison with previous day
        Optional<TestQualitySnapshot> yesterday = snapshotRepository
                .findBySnapshotDate(LocalDate.now().minusDays(1));

        if (yesterday.isEmpty()) {
            return "STABLE";
        }

        double previousPassRate = yesterday.get().getAvgPassRate().doubleValue();
        double change = currentPassRate - previousPassRate;

        if (change > 2.0) return "IMPROVING";
        if (change < -2.0) return "DECLINING";
        return "STABLE";
    }

    // ✅ NEW - SINGLE QUERY
    private int calculatePeakConcurrency(Instant start, Instant end) {
        // Get all executions in the period
        List<TestExecution> executions = executionRepository.findByStartTimeBetween(start, end);

        if (executions.isEmpty()) {
            return 0;
        }

        // Build a timeline of start/end events
        Map<Instant, Integer> timeline = new HashMap<>();

        for (TestExecution exec : executions) {
            Instant execStart = exec.getStartTime();
            Instant execEnd = exec.getEndTime() != null ? exec.getEndTime() : Instant.now();

            // Add start event (+1)
            timeline.merge(execStart, 1, Integer::sum);

            // Add end event (-1)
            timeline.merge(execEnd, -1, Integer::sum);
        }

        // Sort timeline and find peak
        int currentConcurrent = 0;
        int maxConcurrent = 0;

        List<Instant> sortedTimes = new ArrayList<>(timeline.keySet());
        sortedTimes.sort(Instant::compareTo);

        for (Instant time : sortedTimes) {
            currentConcurrent += timeline.get(time);
            if (currentConcurrent > maxConcurrent) {
                maxConcurrent = currentConcurrent;
            }
        }

        return maxConcurrent;
    }

    private BigDecimal calculateTimeSaved(long executions, Double avgTime) {
        if (avgTime == null || executions == 0) {
            return BigDecimal.ZERO;
        }

        // Assume manual testing takes 5x longer
        double manualTimeMs = avgTime * 5.0 * executions;
        double automatedTimeMs = avgTime * executions;
        double savedMs = manualTimeMs - automatedTimeMs;
        double savedHours = savedMs / (1000.0 * 60.0 * 60.0);

        return BigDecimal.valueOf(savedHours).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAveragePassRate(List<TestQualitySnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return BigDecimal.valueOf(100.0);
        }

        double avg = snapshots.stream()
                .mapToDouble(s -> s.getAvgPassRate().doubleValue())
                .average()
                .orElse(100.0);

        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private String determineTrend(BigDecimal passRate7d, BigDecimal passRate30d) {
        double diff = passRate7d.subtract(passRate30d).doubleValue();

        if (diff > 2.0) return "IMPROVING";
        if (diff < -2.0) return "DECLINING";
        return "STABLE";
    }

    private BigDecimal calculateVolatility(List<TestQualitySnapshot> snapshots) {
        if (snapshots.size() < 2) {
            return BigDecimal.ZERO;
        }

        double mean = snapshots.stream()
                .mapToDouble(s -> s.getAvgPassRate().doubleValue())
                .average()
                .orElse(0.0);

        double variance = snapshots.stream()
                .mapToDouble(s -> Math.pow(s.getAvgPassRate().doubleValue() - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);

        return BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateFlakinessScore(List<TestQualitySnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double avg = snapshots.stream()
                .mapToDouble(s -> s.getAvgFlakinessScore().doubleValue())
                .average()
                .orElse(0.0);

        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private int countNewFailures(LocalDate date) {
        Instant dayStart = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

        return (int) patternRepository.findByIsResolvedFalseOrderByLastDetectedAtDesc()
                .stream()
                .filter(p -> p.getFirstDetectedAt().isAfter(dayStart) &&
                        p.getFirstDetectedAt().isBefore(dayEnd))
                .count();
    }

    private int countRecurringFailures(LocalDate date) {
        return (int) patternRepository.findByIsResolvedFalseOrderByLastDetectedAtDesc()
                .stream()
                .filter(p -> p.getOccurrences() > 1)
                .count();
    }

    private int countResolvedFailures(LocalDate date) {
        Instant dayStart = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

        return (int) patternRepository.findAll()
                .stream()
                .filter(p -> p.getIsResolved() &&
                        p.getResolvedAt() != null &&
                        p.getResolvedAt().isAfter(dayStart) &&
                        p.getResolvedAt().isBefore(dayEnd))
                .count();
    }

    private BigDecimal predictPassRate(BigDecimal passRate7d, BigDecimal passRate30d) {
        // Simple linear trend prediction
        BigDecimal trend = passRate7d.subtract(passRate30d);
        BigDecimal predicted = passRate7d.add(trend.multiply(BigDecimal.valueOf(0.5)));

        // Cap between 0 and 100
        if (predicted.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        if (predicted.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }

        return predicted.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePredictionConfidence(BigDecimal volatility) {
        // Lower volatility = higher confidence
        // confidence = 100 - (volatility * 2)
        BigDecimal confidence = BigDecimal.valueOf(100)
                .subtract(volatility.multiply(BigDecimal.valueOf(2)));

        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (confidence.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }

        return confidence.setScale(2, RoundingMode.HALF_UP);
    }
}