package com.company.qa.service.quality;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.*;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.*;
import com.company.qa.service.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Historical quality tracking service
 * INTEGRATES WITH (not duplicates) Week 3 AnalyticsService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestQualityHistoryService {

    // Week 3 Day 4 - Existing service (DO NOT DUPLICATE)
    private final AnalyticsService analyticsService;

    // Week 7 Day 2 - New repositories
    private final TestExecutionHistoryRepository historyRepository;
    private final TestQualitySnapshotRepository snapshotRepository;
    private final TestFailurePatternRepository patternRepository;

    // Existing repositories
    private final TestRepository testRepository;
    private final TestExecutionRepository executionRepository;

    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final int SNAPSHOT_RETENTION_DAYS = 365;
    private static final int PATTERN_RETENTION_DAYS = 180;

    // ========== EXECUTION HISTORY TRACKING ==========

    /**
     * Record execution in history (async - doesn't slow down test execution)
     * Called after each test execution completes
     */
    @Async
    @Transactional
    public void recordExecutionHistory(TestExecution execution) {
        log.debug("Recording execution history for execution: {}", execution.getId());

        try {
            // Get test name
            String testName = testRepository.findById(execution.getTestId())
                    .map(Test::getName)
                    .orElse("Unknown-" + execution.getTestId());

            // Determine failure type if failed
            String failureType = null;
            if (execution.getStatus() == TestStatus.FAILED ||
                    execution.getStatus() == TestStatus.ERROR) {
                failureType = classifyFailureType(execution.getErrorDetails());
            }

            // Create history record
            TestExecutionHistory history = TestExecutionHistory.builder()
                    .executionId(execution.getId())
                    .testName(testName)
                    .status(execution.getStatus().name())
                    .durationMs(execution.getDuration() != null ?
                            execution.getDuration().longValue() : 0L)
                    .failureReason(execution.getErrorDetails())
                    .failureType(failureType)
                    .browser(execution.getBrowser())
                    .environment(execution.getEnvironment())
                    .executedBy(execution.getTriggeredBy())
                    .executedAt(execution.getStartTime() != null ?
                            execution.getStartTime() : Instant.now())
                    .build();

            historyRepository.save(history);

            // If failed, record/update failure pattern
            if (execution.getStatus() == TestStatus.FAILED ||
                    execution.getStatus() == TestStatus.ERROR) {
                recordFailurePattern(testName, failureType,
                        execution.getErrorDetails(),
                        execution.getBrowser());
            }

            log.debug("Execution history recorded successfully");
        } catch (Exception e) {
            log.error("Failed to record execution history", e);
        }
    }

    // ========== DAILY SNAPSHOT CREATION ==========

    /**
     * Create daily quality snapshot
     * Uses Week 3 AnalyticsService for calculations
     * Scheduled to run daily at 1 AM
     */
    @Transactional
    public void createDailySnapshot() {
        LocalDate today = LocalDate.now();

        log.info("Creating daily quality snapshot for {}", today);

        // Check if snapshot already exists
        if (snapshotRepository.existsBySnapshotDate(today)) {
            log.info("Snapshot for {} already exists, skipping", today);
            return;
        }

        try {
            // Define analysis period (last 24 hours)
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(24, ChronoUnit.HOURS);

            // USE Week 3 AnalyticsService for calculations (NO DUPLICATION)
            TestSuiteHealthDTO health = analyticsService.calculateSuiteHealth(startTime, endTime);

            List<FlakyTestDTO> flakyTests = analyticsService.detectFlakyTests(startTime, endTime);

            List<TestExecution> executions = executionRepository.findByStartTimeBetween(startTime, endTime);

            // Calculate averages
            double avgPassRate = health.getAveragePassRate();

            double avgFlakinessScore = flakyTests.isEmpty() ? 0.0 :
                    flakyTests.stream()
                            .mapToDouble(FlakyTestDTO::getFlakinessScore)
                            .average()
                            .orElse(0.0);

            long avgExecutionTime = executions.isEmpty() ? 0L :
                    (long) executions.stream()
                            .filter(e -> e.getDuration() != null)
                            .mapToInt(TestExecution::getDuration)
                            .average()
                            .orElse(0.0);
            //.longValue();

            // Create snapshot
            TestQualitySnapshot snapshot = TestQualitySnapshot.builder()
                    .snapshotDate(today)
                    .totalTests(health.getTotalTests())
                    .activeTests(health.getActiveTests())
                    .stableTests(health.getStableTests())
                    .flakyTests(health.getFlakyTests())
                    .failingTests(health.getTotalTests() - health.getStableTests() - health.getFlakyTests())
                    .avgPassRate(BigDecimal.valueOf(avgPassRate).setScale(2, RoundingMode.HALF_UP))
                    .avgFlakinessScore(BigDecimal.valueOf(avgFlakinessScore).setScale(2, RoundingMode.HALF_UP))
                    .overallHealthScore(BigDecimal.valueOf(health.getOverallHealthScore()).setScale(2, RoundingMode.HALF_UP))
                    .totalExecutions(executions.size())
                    .avgExecutionTimeMs(avgExecutionTime)
                    .build();

            snapshotRepository.save(snapshot);

            log.info("Daily snapshot created successfully: {} tests, {} flaky, health score: {}",
                    snapshot.getTotalTests(), snapshot.getFlakyTests(),
                    snapshot.getOverallHealthScore());

        } catch (Exception e) {
            log.error("Failed to create daily snapshot", e);
            throw new RuntimeException("Snapshot creation failed", e);
        }
    }

    // ========== FAILURE PATTERN MANAGEMENT ==========

    /**
     * Record or update failure pattern
     */
    @Transactional
    public void recordFailurePattern(String testName, String patternType,
                                     String errorDetails, String browser) {
        if (errorDetails == null || errorDetails.isBlank()) {
            return;
        }

        String errorSignature = extractErrorSignature(errorDetails);

        // Find existing pattern
        Optional<TestFailurePattern> existingOpt = patternRepository
                .findByTestNameAndErrorSignature(testName, errorSignature);

        if (existingOpt.isPresent()) {
            // Update existing pattern
            TestFailurePattern existing = existingOpt.get();
            existing.incrementOccurrences();

            // Add browser to affected list if not present
            if (browser != null) {
                List<String> browsers = new ArrayList<>(existing.getAffectedBrowsers() != null
                        ? existing.getAffectedBrowsers() : Collections.emptyList());
                if (!browsers.contains(browser)) browsers.add(browser);
                existing.setAffectedBrowsers(browsers);
            }

            // Recalculate impact score
            existing.setImpactScore(calculateImpactScore(testName, existing.getOccurrences()));

            patternRepository.save(existing);

            log.debug("Updated failure pattern: {} occurrences for test: {}",
                    existing.getOccurrences(), testName);
        } else {
            // Create new pattern
            TestFailurePattern newPattern = TestFailurePattern.builder()
                    .testName(testName)
                    .patternType(patternType != null ? patternType : "UNKNOWN")
                    .errorSignature(errorSignature)
                    .occurrences(1)
                    .firstDetectedAt(Instant.now())
                    .lastDetectedAt(Instant.now())
                    .affectedBrowsers(browser != null ? new ArrayList<>(List.of(browser)) : new ArrayList<>())
                    .impactScore(calculateImpactScore(testName, 1))
                    .build();

            patternRepository.save(newPattern);

            log.info("New failure pattern detected: {} for test: {}", patternType, testName);
        }
    }

    /**
     * Mark pattern as resolved
     */
    @Transactional
    public void resolvePattern(Long patternId, String resolutionNotes) {
        TestFailurePattern pattern = patternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern not found: " + patternId));

        pattern.markResolved(resolutionNotes);
        patternRepository.save(pattern);

        log.info("Pattern {} marked as resolved for test: {}", patternId, pattern.getTestName());
    }

    // ========== QUERY METHODS ==========

    /**
     * Get quality trends for last N days
     */
    @Transactional(readOnly = true)
    public List<QualityTrendDTO> getQualityTrends(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);

        List<TestQualitySnapshot> snapshots = snapshotRepository
                .findRecentSnapshots(startDate);

        return snapshots.stream()
                .map(s -> QualityTrendDTO.builder()
                        .date(s.getSnapshotDate())
                        .totalTests(s.getTotalTests())
                        .flakyTests(s.getFlakyTests())
                        .avgPassRate(s.getAvgPassRate().doubleValue())
                        .overallHealthScore(s.getOverallHealthScore().doubleValue())
                        .totalExecutions(s.getTotalExecutions())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get latest snapshot
     */
    @Transactional(readOnly = true)
    public Optional<TestQualitySnapshot> getLatestSnapshot() {
        List<TestQualitySnapshot> snapshots = snapshotRepository.findLatestSnapshot();
        return snapshots.isEmpty() ? Optional.empty() : Optional.of(snapshots.get(0));
    }

    /**
     * Get stored failure patterns for a test
     */
    @Transactional(readOnly = true)
    public List<StoredPatternDTO> getStoredPatterns(String testName, boolean includeResolved) {
        List<TestFailurePattern> patterns = includeResolved ?
                patternRepository.findByTestNameOrderByOccurrencesDesc(testName) :
                patternRepository.findByTestNameAndIsResolvedFalseOrderByOccurrencesDesc(testName);

        return patterns.stream()
                .map(this::toStoredPatternDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all high-impact patterns
     */
    @Transactional(readOnly = true)
    public List<StoredPatternDTO> getHighImpactPatterns(double minScore) {
        return patternRepository.findHighImpactPatterns(minScore).stream()
                .map(this::toStoredPatternDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all unresolved patterns
     */
    @Transactional(readOnly = true)
    public List<StoredPatternDTO> getAllUnresolvedPatterns() {
        return patternRepository.findByIsResolvedFalseOrderByLastDetectedAtDesc().stream()
                .map(this::toStoredPatternDTO)
                .collect(Collectors.toList());
    }

    // ========== CLEANUP METHODS ==========

    /**
     * Cleanup old execution history
     */
    @Transactional
    public void cleanupOldHistory(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        historyRepository.deleteByExecutedAtBefore(cutoff);
        log.info("Cleaned up execution history older than {} days", retentionDays);
    }

    /**
     * Cleanup old snapshots
     */
    @Transactional
    public void cleanupOldSnapshots(int retentionDays) {
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        snapshotRepository.deleteBySnapshotDateBefore(cutoff);
        log.info("Cleaned up snapshots older than {} days", retentionDays);
    }

    /**
     * Cleanup resolved patterns
     */
    @Transactional
    public void cleanupResolvedPatterns(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        patternRepository.deleteByIsResolvedTrueAndResolvedAtBefore(cutoff);
        log.info("Cleaned up resolved patterns older than {} days", retentionDays);
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Classify failure type from error message
     */
    private String classifyFailureType(String errorDetails) {
        if (errorDetails == null || errorDetails.isBlank()) {
            return "UNKNOWN";
        }

        String lower = errorDetails.toLowerCase();

        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "TIMEOUT";
        } else if (lower.contains("element not found") || lower.contains("no such element")) {
            return "ELEMENT_NOT_FOUND";
        } else if (lower.contains("stale element")) {
            return "STALE_ELEMENT";
        } else if (lower.contains("assertion") || lower.contains("expected")) {
            return "ASSERTION_FAILED";
        } else if (lower.contains("network") || lower.contains("connection")) {
            return "NETWORK_ERROR";
        } else if (lower.contains("null")) {
            return "NULL_POINTER";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Extract error signature (first 200 chars)
     */
    private String extractErrorSignature(String errorDetails) {
        if (errorDetails == null || errorDetails.isBlank()) {
            return "Unknown Error";
        }

        String firstLine = errorDetails.split("\n")[0];
        if (firstLine.length() > 200) {
            return firstLine.substring(0, 200);
        }
        return firstLine;
    }

    /**
     * Calculate impact score based on recent occurrences
     */
    private BigDecimal calculateImpactScore(String testName, int occurrences) {
        // Simple formula: more occurrences = higher impact
        // Max impact = 100 at 10+ occurrences
        double impact = Math.min(100.0, occurrences * 10.0);
        return BigDecimal.valueOf(impact).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Convert entity to DTO
     */
    private StoredPatternDTO toStoredPatternDTO(TestFailurePattern pattern) {
        return StoredPatternDTO.builder()
                .id(pattern.getId())
                .testName(pattern.getTestName())
                .patternType(pattern.getPatternType())
                .errorSignature(pattern.getErrorSignature())
                .occurrences(pattern.getOccurrences())
                .firstDetectedAt(pattern.getFirstDetectedAt())
                .lastDetectedAt(pattern.getLastDetectedAt())
                .isResolved(pattern.getIsResolved())
                .resolvedAt(pattern.getResolvedAt())
                .affectedBrowsers(pattern.getAffectedBrowsers() != null ?
                        pattern.getAffectedBrowsers() : Collections.emptyList())
                .impactScore(pattern.getImpactScore().doubleValue())
                .build();
    }
}